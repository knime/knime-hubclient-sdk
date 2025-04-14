/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Apr 8, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;

/**
 * Output stream which writes the data to a series of chunks of a specified size and calls
 * {@link #chunkFinished(int, byte[])} for each finished chunk. The last file may have any (non-zero) size.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
abstract class ChunkingOutputStream extends OutputStream {

    final long m_maxChunkSize;

    int m_currentChunkNo;

    private @Owning OutputStream m_chunkOutputStream;

    long m_currentChunkSize;

    private final MessageDigest m_digest;

    /**
     * @param chunkSize size size which every output file except for the last one should have
     * @param digest message digest for computing file hashes, may be {@code null}
     */
    protected ChunkingOutputStream(final long chunkSize, final MessageDigest digest) {
        CheckUtils.checkArgument(chunkSize > 0, "Chunk size must be positive, found %d", chunkSize);
        m_maxChunkSize = chunkSize;
        m_digest = digest;
    }

    /**
     * Called whenever an output file has been finished.
     *
     * @param chunkNumber number of the output file (zero-based)
     * @param digest calculated digest if a {@link MessageDigest} was supplied in the constructor,
     *     {@code null} otherwise
     * @throws IOException if processing the file failed
     */
    protected abstract void chunkFinished(int chunkNumber, byte[] digest) throws IOException;

    /**
     * Creates a new chunk.
     *
     * @return {@link OutputStream}
     * @throws IOException if an I/O error occurred during creation of the chunk
     */
    protected abstract @Owning OutputStream createChunk() throws IOException;

    private void ensureOpen() throws IOException {
        if (m_currentChunkNo < 0) {
            throw new IOException("Output stream closed");
        }
    }

    private void ensureChunkOpen() throws IOException {
        ensureOpen();
        if (m_chunkOutputStream == null) {
            m_chunkOutputStream = createChunk();
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
            chunkFinished(m_currentChunkNo,
                chunkOutputStream instanceof DigestOutputStream dos ? dos.getMessageDigest().digest() : null);
        } finally {
            m_chunkOutputStream = null;
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
            final var writeLength = Math.toIntExact(Math.min(available, currentChunkCapacity));
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
