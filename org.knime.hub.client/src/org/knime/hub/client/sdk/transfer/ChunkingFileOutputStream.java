/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Jun 4, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;

/**
 * Output stream which writes the data to a series of files of a specified size and calls
 * {@link #chunkFinished(int, Path, long, byte[])} for each finished file. The last file may have any (non-zero) size.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public abstract class ChunkingFileOutputStream extends OutputStream {

    private final long m_maxChunkSize;

    private int m_currentChunkNo;

    private Path m_currentChunk;

    private @Owning OutputStream m_chunkOutputStream;

    private long m_currentChunkSize;

    private final MessageDigest m_digest;

    /**
     * @param chunkSize size size which every output file except for the last one should have
     * @param digest message digest for computing file hashes, may be {@code null}
     */
    protected ChunkingFileOutputStream(final long chunkSize, final MessageDigest digest) {
        CheckUtils.checkArgument(chunkSize > 0, "Chunk size must be positive, found %d", chunkSize);
        m_maxChunkSize = chunkSize;
        m_digest = digest;
    }

    /**
     * Creates the next file to write to.
     *
     * @return new output file
     * @throws IOException if file creation failed
     */
    public abstract Path newOutputFile() throws IOException;

    /**
     * Called whenever an output file has been finished.
     *
     * @param chunkNumber number of the output file (zero-based)
     * @param chunk path to the output file
     * @param size size of the output file
     * @param digest calculated digest if a {@link MessageDigest} was supplied in the constructor,
     *     {@code null} otherwise
     * @throws IOException if processing the file failed
     */
    public abstract void chunkFinished(int chunkNumber, Path chunk, long size, byte[] digest) throws IOException;

    private void ensureOpen() throws IOException {
        if (m_currentChunkNo < 0) {
            throw new IOException("Output stream closed");
        }
    }

    private void ensureChunkOpen() throws IOException {
        ensureOpen();
        if (m_chunkOutputStream == null) {
            m_currentChunk = newOutputFile();
            m_chunkOutputStream = new BufferedOutputStream(Files.newOutputStream(m_currentChunk));
            if (m_digest != null) {
                m_digest.reset();
                m_chunkOutputStream = new DigestOutputStream(m_chunkOutputStream, m_digest);
            }
            m_currentChunkSize = 0;
        }
    }

    private void finishChunk(final @Owning OutputStream chunkOutputStream) throws IOException {
        try {
            chunkOutputStream.close();
            chunkFinished(m_currentChunkNo, m_currentChunk, m_currentChunkSize,
                chunkOutputStream instanceof DigestOutputStream dos ? dos.getMessageDigest().digest() : null);
        } finally {
            m_chunkOutputStream = null;
            m_currentChunk = null;
            m_currentChunkNo++;
            m_currentChunkSize = -1;
        }
    }

    @Override
    public void write(final int b) throws IOException {
        ensureChunkOpen();
        m_chunkOutputStream.write(b);
        m_currentChunkSize++;
        if (m_currentChunkSize == m_maxChunkSize) {
            finishChunk(m_chunkOutputStream);
        }
    }

    @Override
    public void write(final byte[] array, final int fromIncl, final int length) throws IOException {
        final var toExcl = fromIncl + length;
        int current = fromIncl;
        while (current < toExcl) {
            ensureChunkOpen();
            final var available = toExcl - current;
            final var currentChunkCapacity = m_maxChunkSize - m_currentChunkSize;
            final var writeLength = (int)Math.min(available, currentChunkCapacity);
            m_chunkOutputStream.write(array, current, writeLength);
            m_currentChunkSize += writeLength;
            if (m_currentChunkSize == m_maxChunkSize) {
                finishChunk(m_chunkOutputStream);
            }
            current += writeLength;
        }
    }

    @Override
    public void flush() throws IOException {
        if (m_chunkOutputStream != null) {
            m_chunkOutputStream.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (m_chunkOutputStream != null) {
            finishChunk(m_chunkOutputStream);
        }
        m_currentChunkNo = -1;
    }
}
