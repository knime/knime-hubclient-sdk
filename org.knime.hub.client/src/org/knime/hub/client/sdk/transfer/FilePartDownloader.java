/* ------------------------------------------------------------------------
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
 *   Created on Mar 19, 2026 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.annotation.Owning;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.FailureType;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Downloads a single file in parallel using HTTP byte-range requests (RFC 7233).
 * <p>
 * Each part issues a {@code GET} with a {@code Range: bytes=<offset>-<end>} header and writes the response body
 * directly into the correct offset of a pre-allocated output {@link FileChannel}, so no post-assembly step is needed.
 * </p>
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
final class FilePartDownloader {

    private static final String RANGE = "Range";
    private static final String CONTENT_RANGE = "Content-Range";

    private static final Logger LOGGER = LoggerFactory.getLogger(FilePartDownloader.class);

    /** Minimum file size for parallel download to be worthwhile (16 MB). */
    static final long MIN_PARALLEL_SIZE = 16 * FileUtils.ONE_MB;

    /** Minimum size of an individual part (4 MB). */
    private static final long MIN_PART_SIZE = 4 * FileUtils.ONE_MB;

    /** Maximum number of parallel download connections per file. */
    private static final int PARALLELISM = 4;

    /** Buffer size for reading from the HTTP response stream (512 KB). */
    private static final int BUFFER_SIZE = (int)(FileUtils.ONE_MB / 2);

    /** Dedicated thread pool for parallel part downloads. */
    private static final ExecutorService FILE_PART_DOWNLOAD_POOL;
    static {
        final var idSupplier = new AtomicInteger();
        final var pool = new ThreadPoolExecutor(PARALLELISM, PARALLELISM, 10, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(),
            r -> new Thread(r, "KNIME-" + FilePartDownloader.class.getSimpleName() + "-" + idSupplier.getAndIncrement()));
        pool.allowCoreThreadTimeOut(true);
        FILE_PART_DOWNLOAD_POOL = pool;
    }

    /**
     * Result of a byte-range capability probe against a pre-signed download URL.
     *
     * @param supported {@code true} if the server responded with {@code 206 Partial Content}
     * @param totalSize total file size in bytes as reported by {@code Content-Range}, or {@code -1} if unknown
     */
    record RangeCapability(boolean supported, long totalSize) {}

    private final @Owning ApiClient m_apiClient;

    FilePartDownloader(final ApiClient apiClient) {
        m_apiClient = apiClient;
    }

    /**
     * Probes whether the server at {@code downloadUrl} supports byte-range requests by issuing a
     * {@code GET Range: bytes=0-0} request and inspecting the response status.
     *
     * @param downloadUrl pre-signed download URL to probe
     * @return range capability descriptor; never throws — on any unexpected response returns
     *         {@code RangeCapability(false, -1)}
     */
    RangeCapability probeRanges(final URL downloadUrl) {
        try (final var response = m_apiClient.nonApiInvocationBuilder(downloadUrl.toString()) //
                .header(RANGE, "bytes=0-0") //
                .get()) {
            if (response.getStatus() != Status.PARTIAL_CONTENT.getStatusCode()) {
                LOGGER.atDebug() //
                    .addArgument(response.getStatus()) //
                    .addArgument(downloadUrl) //
                    .log("Server responded with {} (not 206) for Range probe on '{}' — falling back to sequential download");
                return new RangeCapability(false, -1);
            }
            final long totalSize = parseTotalSizeFromContentRange(
                response.getHeaderString(CONTENT_RANGE));
            LOGGER.atDebug() //
                .addArgument(totalSize) //
                .addArgument(downloadUrl) //
                .log("Range requests supported for '{}'; total size: {} bytes");
            return new RangeCapability(true, totalSize);
        } catch (final Exception ex) {
            LOGGER.atDebug() //
                .setCause(ex) //
                .addArgument(downloadUrl) //
                .log("Range probe for '{}' failed — falling back to sequential download");
            return new RangeCapability(false, -1);
        }
    }

    /**
     * Downloads {@code totalSize} bytes from {@code downloadUrl} in parallel and writes them into {@code outputFile}.
     * <p>
     * The file is pre-allocated and each part writes directly to its byte offset, so the output is complete once all
     * futures return successfully.
     * </p>
     *
     * @param downloadUrl pre-signed URL that supports byte ranges
     * @param totalSize exact size of the artifact in bytes
     * @param outputFile destination file (will be created / overwritten)
     * @param monitor progress monitor for the whole download
     * @param cancelChecker cancellation check
     * @return {@link Result#success(Object) success} on completion, or a failure describing the first part error
     * @throws CancelationException if the download was cancelled
     * @throws IOException if the output file could not be created or written
     */
    Result<Void, FailureValue> downloadInParallel(final URL downloadUrl, final long totalSize, final Path outputFile,
            final LeafExecMonitor monitor, final BooleanSupplier cancelChecker)
            throws CancelationException, IOException {

        final int numParts = computeNumParts(totalSize);
        LOGGER.atDebug() //
            .addArgument(numParts) //
            .addArgument(totalSize) //
            .addArgument(downloadUrl) //
            .log("Downloading '{}' ({} bytes) in {} parts");

        // Pre-allocate the output file so all parts can write independently.
        try (final var channel = FileChannel.open(outputFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // Write a single zero byte at the last position to set the file length.
            channel.write(ByteBuffer.wrap(new byte[1]), totalSize - 1);
        }

        final var totalWritten = new AtomicLong();
        final long partSize = (totalSize + numParts - 1) / numParts; // ceiling division

        // Submit all parts.
        final List<Future<Result<Void, FailureValue>>> futures = new ArrayList<>(numParts);
        for (var i = 0; i < numParts; i++) {
            final long offset = i * partSize;
            final long length = Math.min(partSize, totalSize - offset);
            futures.add(FILE_PART_DOWNLOAD_POOL.submit(
                () -> downloadPart(downloadUrl, offset, length, outputFile, totalSize, totalWritten, monitor)));
        }

        // Await all parts; cancel siblings on any failure or cancellation.
        Result<Void, FailureValue> firstFailure = null;
        for (final var future : futures) {
            if (firstFailure != null || cancelChecker.getAsBoolean()) {
                future.cancel(true);
                continue;
            }
            final Result<Void, FailureValue> partResult =
                AbstractHubTransfer.waitForCancellable(future, cancelChecker, throwable -> {
                    if (throwable instanceof CancelationException ce) {
                        throw ce;
                    }
                    return Result.failure(FailureValue.fromUnexpectedThrowable("Failed to download part",
                        List.of("Unexpected exception: " + throwable.getMessage()), throwable));
                });
            if (partResult instanceof Result.Failure<?, FailureValue> failure) {
                firstFailure = failure.asFailure();
            }
        }

        if (firstFailure != null) {
            return firstFailure;
        }
        if (cancelChecker.getAsBoolean()) {
            throw new CancelationException();
        }
        return Result.success(null);
    }

    private Result<Void, FailureValue> downloadPart(final URL downloadUrl, final long offset, final long length,
            final Path outputFile, final long totalSize, final AtomicLong totalWritten, final LeafExecMonitor monitor) {
        final var rangeHeader = "bytes=%d-%d".formatted(offset, offset + length - 1);
        LOGGER.atDebug() //
            .addArgument(rangeHeader) //
            .addArgument(downloadUrl) //
            .log("Downloading range '{}' from '{}'");

        try (final var response = m_apiClient.nonApiInvocationBuilder(downloadUrl.toString()) //
                .header(RANGE, rangeHeader) //
                .get()) {

            if (response.getStatus() != Status.PARTIAL_CONTENT.getStatusCode()) {
                return Result.failure(FailureValue.withDetails(FailureType.DOWNLOAD_ITEM_FAILED,
                    "Parallel download part failed",
                    "Expected 206 Partial Content for range '%s' but got %d".formatted(rangeHeader,
                        response.getStatus())));
            }

            return writePartToFile(response, offset, length, outputFile, totalSize, totalWritten, monitor);
        }
    }

    private static Result<Void, FailureValue> writePartToFile(final Response response, final long offset,
            final long length, final Path outputFile, final long totalSize, final AtomicLong totalWritten,
            final LeafExecMonitor monitor) {
        try (final var channel = FileChannel.open(outputFile, StandardOpenOption.WRITE);
                final var in = response.readEntity(InputStream.class)) {
            final var buffer = new byte[BUFFER_SIZE];
            var position = offset;
            var remaining = length;
            while (remaining > 0) {
                final int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                var buf = ByteBuffer.wrap(buffer, 0, read);
                while (buf.hasRemaining()) {
                    position += channel.write(buf, position);
                }
                remaining -= read;
                monitor.addTransferredBytes(read);
                final long written = totalWritten.addAndGet(read);
                monitor.setProgress(Math.min(1.0 * written / totalSize, 1.0));
            }
            return Result.success(null);
        } catch (final IOException ex) {
            return Result.failure(FailureValue.fromUnexpectedThrowable("Failed to download part",
                List.of("I/O error writing range at offset %d: %s".formatted(offset, ex.getMessage())), ex));
        }
    }

    /**
     * Computes how many parts to split the download into, between 2 and {@value #PARALLELISM}, ensuring each part is
     * at least {@value #MIN_PART_SIZE} bytes.
     */
    private static int computeNumParts(final long totalSize) {
        return (int)Math.max(2, Math.min(PARALLELISM, totalSize / MIN_PART_SIZE));
    }

    /**
     * Parses the total size from a {@code Content-Range: bytes 0-0/<totalSize>} header value.
     *
     * @return total size, or {@code -1} if the header is absent or malformed
     */
    private static long parseTotalSizeFromContentRange(final String contentRange) {
        if (contentRange == null) {
            return -1;
        }
        // Format: "bytes <range>/<total>" — total may be "*" if unknown
        final int slashIndex = contentRange.lastIndexOf('/');
        if (slashIndex < 0) {
            return -1;
        }
        final var totalStr = contentRange.substring(slashIndex + 1).trim();
        if ("*".equals(totalStr)) {
            return -1;
        }
        try {
            return Long.parseLong(totalStr);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }
}
