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
 *   Mar 25, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Output stream which writes the data to a series of chunks of a specified size and calls
 * {@link #chunkFinished(int, byte[], long, byte[])} for each finished chunk.
 * The last chunk may have any (non-zero) size.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
abstract class ChunkingByteOutputStream extends ChunkingOutputStream {

    private Supplier<byte[]> m_currentChunk;

    /**
     * @param chunkSize size size which every output file except for the last one should have
     * @param digest message digest for computing file hashes, may be {@code null}
     */
    protected ChunkingByteOutputStream(final int chunkSize, final MessageDigest digest) {
        super(chunkSize, digest);
    }

    /**
     * Called whenever an output file has been finished.
     *
     * @param chunkNumber number of the output file (zero-based)
     * @param chunk path to the output file
     * @param digest calculated digest if a {@link MessageDigest} was supplied in the constructor,
     *     {@code null} otherwise
     * @throws IOException if processing the file failed
     */
    protected abstract void chunkFinished(int chunkNumber, byte[] chunk, byte[] digest) throws IOException;

    @Override
    protected void chunkFinished(final int chunkNumber, final byte[] digest) throws IOException {
        chunkFinished(chunkNumber, m_currentChunk.get(), digest);
        m_currentChunk = null;
    }

    @Override
    protected OutputStream createChunk() throws IOException {
        final var baos = new ByteArrayOutputStream(Math.toIntExact(m_maxChunkSize));
        m_currentChunk = baos::toByteArray;
        return baos;
    }
}
