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
 *   6 Oct 2025 (Manuel Hotz, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.hub.client.sdk.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code ChunkingOutputStream}.
 *
 * @author Manuel Hotz, KNIME GmbH, Konstanz, Germany
 */
final class ChunkingOutputStreamTest {

    /**
     * Tests that not calling bytes correctly creates one empty chunk.
     *
     * @throws IOException
     */
    @SuppressWarnings({"resource", "static-method"})
    @Test
    void testEmptyChunk() throws IOException {
        final var mockOutputStream = mock(OutputStream.class);

        final var err = new AssertionError("No data should be written to the stream in empty file case");
        doThrow(err).when(mockOutputStream).write(anyInt());
        doThrow(err).when(mockOutputStream).write(any(byte[].class));
        doThrow(err).when(mockOutputStream).write(any(byte[].class), anyInt(), anyInt());

        final var createChunkCallCount = new AtomicInteger(0);
        final var chunkFinishedCallCount = new AtomicInteger(0);
        final var chunkNumber = new AtomicInteger(-1);

        try (var cos = new ChunkingOutputStream(10, null) {
            @Override
            protected OutputStream createChunk() throws IOException {
                createChunkCallCount.incrementAndGet();
                return mockOutputStream;
            }

            @Override
            protected void chunkFinished(final int chunkNum, final byte[] digest) throws IOException {
                // assert that chunk number is always 0 here
                assertEquals(0, chunkNum, "Expected first (and only) chunk number to be 0");
                assertNull(digest, "Did not expect digest for empty file");
                chunkFinishedCallCount.incrementAndGet();
                chunkNumber.set(chunkNum);
            }
        }) {
            // Write nothing - this tests the empty file case
        }

        assertEquals(1, createChunkCallCount.get(), "createChunk should be called exactly once for empty file");
        assertEquals(1, chunkFinishedCallCount.get(), "chunkFinished should be called exactly once for empty file");
        assertEquals(0, chunkNumber.get(), "chunkFinished should be called with chunk number 0 for empty file");

        verify(mockOutputStream).close();
    }

    /**
     * Test that ends writing exactly on the chunk boundary.
     *
     * @throws IOException
     */
    @SuppressWarnings("static-method")
    @Test
    void testWriteOneChunk() throws IOException {
        final var mockOutputStream = mock(OutputStream.class);

        final var createChunkCallCount = new AtomicInteger(0);
        final var chunkFinishedCallCount = new AtomicInteger(0);
        final var lastChunkNumber = new AtomicInteger(-1);

        try (var cos = new ChunkingOutputStream(10, null) {
            @Override
            protected OutputStream createChunk() throws IOException {
                createChunkCallCount.incrementAndGet();
                return mockOutputStream;
            }

            @Override
            protected void chunkFinished(final int chunkNum, final byte[] digest) throws IOException {
                chunkFinishedCallCount.incrementAndGet();
                lastChunkNumber.set(chunkNum);
            }
        }) {
            cos.write(new byte[8]);
            cos.write(0);
            cos.write(0);
            cos.flush();
        }

        assertEquals(1, createChunkCallCount.get(), "createChunk should be called exactly once for 10 bytes");
        assertEquals(1, chunkFinishedCallCount.get(), "chunkFinished should be called exactly once for 10 bytes");
        assertEquals(0, lastChunkNumber.get(), "last chunk number should be 0 for 10 bytes");
        verify(mockOutputStream).close();
    }

    /**
     * Test that ends writing in the middle of a chunk.
     *
     * @throws IOException
     */
    @SuppressWarnings("static-method")
    @Test
    void testTwoAndAHalfChunks() throws IOException {
        final var mockOutputStream = mock(OutputStream.class);

        final var createChunkCallCount = new AtomicInteger(0);
        final var chunkFinishedCallCount = new AtomicInteger(0);
        final var lastChunkNumber = new AtomicInteger(-1);

        try (var cos = new ChunkingOutputStream(10, null) {
            @Override
            protected OutputStream createChunk() throws IOException {
                createChunkCallCount.incrementAndGet();
                return mockOutputStream;
            }

            @Override
            protected void chunkFinished(final int chunkNum, final byte[] digest) throws IOException {
                chunkFinishedCallCount.incrementAndGet();
                lastChunkNumber.set(chunkNum);
            }
        }) {
            // create three chunks with flush in between
            cos.write(new byte[8]);
            cos.write(0);
            cos.write(new byte[6]);
            cos.write(new byte[4]);
            cos.write(0); // this should hit the chunk limit
            cos.write(new byte[5]);
            cos.flush();
        }

        assertEquals(3, createChunkCallCount.get(), "createChunk should be called exactly three times for 25 bytes");
        assertEquals(3, chunkFinishedCallCount.get(), "chunkFinished should be called exactly three times for 25 bytes");
        assertEquals(2, lastChunkNumber.get(), "last chunk number should be 2 for 25 bytes");
        verify(mockOutputStream, times(3)).close();
    }

}