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
import java.security.MessageDigest;

import org.eclipse.jdt.annotation.Owning;

/**
 * Output stream which writes the data to a series of files of a specified size and calls
 * {@link #chunkFinished(int, Path, long, byte[])} for each finished file. The last file may have any (non-zero) size.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public abstract class ChunkingFileOutputStream extends ChunkingOutputStream {

    private Path m_currentChunk;

    /**
     * @param chunkSize size size which every output file except for the last one should have
     * @param digest message digest for computing file hashes, may be {@code null}
     */
    protected ChunkingFileOutputStream(final long chunkSize, final MessageDigest digest) {
        super(chunkSize, digest);
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

    @Override
    public void chunkFinished(final int chunkNumber, final byte[] digest) throws IOException {
        chunkFinished(chunkNumber, m_currentChunk, m_currentChunkSize, digest);
        m_currentChunk = null;
    }

    @Override
    public @Owning OutputStream createChunk() throws IOException {
        m_currentChunk = newOutputFile();
        return new BufferedOutputStream(Files.newOutputStream(m_currentChunk));
    }

}
