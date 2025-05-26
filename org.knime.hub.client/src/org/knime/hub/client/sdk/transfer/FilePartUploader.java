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
 *   Created on Jun 8, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.FailureType;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.ent.catalog.UploadTarget;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.EntityTag;

/**
 * Uploads files or file parts via HTTP requests.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public final class FilePartUploader {

    /**
     * Used to request an upload URL for an upload part.
     */
    @FunctionalInterface
    public interface UploadTargetFetcher {

        /**
         * Requests an upload target specification for the download part with the given number.
         *
         * @param partNo part number
         * @return fetched upload target specification
         * @throws IOException if an error occurs while fetching
         * @since 0.1
         */
        UploadTarget fetch(int partNo) throws IOException;
    }

    /**
     * Provides a way of uploading data from an input stream via HTTP in a streaming way (non-chunked).
     */
    @FunctionalInterface
    public interface StreamingUploader {

        /**
         * Perform a streaming HTTP call with the given method (POST or PUT), target, headers and content.
         *
         * @param targetUrl target URL
         * @param httpMethod HTTP verb
         * @param httpHeaders headers to send
         * @param contentStream bytes to send as request body
         * @param contentLength number of bytes in the request body
         * @param writtenBytesAdder consumer for newly written bytes
         * @param cancelChecker cancellation checker
         * @return the ETag sent with a successful response
         *
         * @throws CancelationException if the upload was cancelled
         * @throws IOException if an error occurred during the upload
         */
        EntityTag performUpload(URL targetUrl, String httpMethod, Map<String, List<String>> httpHeaders,
            @Owning InputStream contentStream, long contentLength, LongConsumer writtenBytesAdder,
            final BooleanSupplier cancelChecker) throws CancelationException, IOException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(FilePartUploader.class);

    private static final String CONTENT_MD5_HEADER = "Content-MD5";

    /** Maximum number of simultaneous upload connections. */
    private static final int PARALLELISM = 4;

    /** Thread pool for part uploads. */
    private static final ExecutorService FILE_PART_UPLOAD_POOL;
    static {
        final var idSupplier = new AtomicInteger();
        final var threadNamePrefix = "KNIME-" + FilePartUploader.class.getSimpleName() + "-";
        FILE_PART_UPLOAD_POOL = new ThreadPoolExecutor(PARALLELISM, PARALLELISM, 10, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(), r -> new Thread(r, threadNamePrefix + idSupplier.getAndIncrement()));
        ((ThreadPoolExecutor)FILE_PART_UPLOAD_POOL).allowCoreThreadTimeOut(true);
    }

    private final StreamingUploader m_uploader;

    private final int m_numRetries;

    private final boolean m_sendContentMd5;

    /**
     * @param invocationBuilderSupplier supplier for invocation builders used for single upload requests
     * @param numRetries number of retries allowed if an upload fails
     * @param sendContentMd5 flag indicating whether or not an MD5 checksum should be sent with each part
     */
    FilePartUploader(final StreamingUploader uploader, final int numRetries, final boolean sendContentMd5) {
        CheckUtils.checkArgument(numRetries >= 0, "Number of retries must be non-negative, found %d", numRetries);
        m_uploader = uploader;
        m_numRetries = numRetries;
        m_sendContentMd5 = sendContentMd5;
    }

    /**
     * Uploads a whole temporary file and <i>deletes</i> the file afterwards.
     *
     * @param path name/description of the file to be uploaded (for error and logging messages)
     * @param partNum part number of this part inside the upload
     * @param targetFetcher fetcher for the upload target
     * @param file upload data
     * @param fileSize size of {@code file}
     * @param md5Hash hash of the input data, may be {@code null}
     * @param monitor exec monitor for the upload
     * @return future
     */
    public Future<Result<Pair<Integer, EntityTag>, FailureValue>> uploadTempFile(final String path,
        final Integer partNum, final UploadTargetFetcher targetFetcher, final Path file, final long fileSize,
        final byte[] md5Hash, final LeafExecMonitor monitor) {
        return FILE_PART_UPLOAD_POOL.submit(() -> {
            try {
                final InputStreamSupplier inputSupplier = () -> Files.newInputStream(file);
                return uploadWithRetries(path, partNum, targetFetcher, inputSupplier, fileSize, md5Hash, monitor);
            } finally {
                FileUtils.deleteQuietly(file.toFile());
            }
        });
    }

    /**
     * Uploads a chunk of data.
     *
     * @param path name/description of the file to be uploaded (for error and logging messages)
     * @param partNum part number of this part inside the upload
     * @param targetFetcher fetcher for the upload target
     * @param dataChunk a chunk of the data
     * @param md5Hash hash of the input data, may be {@code null}
     * @param monitor exec monitor for the upload
     * @return future
     */
    public Future<Result<Pair<Integer, EntityTag>, FailureValue>> uploadDataChunk(final String path,
        final Integer partNum, final UploadTargetFetcher targetFetcher, final byte[] dataChunk, final byte[] md5Hash,
        final LeafExecMonitor monitor) {
        return FILE_PART_UPLOAD_POOL.submit(() -> {
            final InputStreamSupplier inputSupplier = () -> new ByteArrayInputStream(dataChunk);
            return uploadWithRetries(path, partNum, targetFetcher, inputSupplier, dataChunk.length, md5Hash, monitor);
        });
    }

    /**
     * Uploads a segment of the given file and <i>does not delete</i> the file afterwards.
     *
     * @param path name/description of the file to be uploaded (for error and logging messages)
     * @param partNum part number of this part inside the upload
     * @param targetFetcher fetcher for the upload target
     * @param file file to upload a part of
     * @param offset start of the part inside the file
     * @param length length of the part inside the file
     * @param monitor exec monitor for the upload
     * @return future
     */
    public Future<Result<Pair<Integer, EntityTag>, FailureValue>> uploadDataFilePart(final String path,
        final Integer partNum, final UploadTargetFetcher targetFetcher, final Path file, final long offset,
        final long length, final LeafExecMonitor monitor) {
        return FILE_PART_UPLOAD_POOL.submit(() -> {
            try {
                final byte[] md5Hash = m_sendContentMd5 ? calculateHash(file, offset, length) : null;
                final InputStreamSupplier inputSupplier = () -> newFileRangeInputStream(file, offset, length);
                return uploadWithRetries(path, partNum, targetFetcher, inputSupplier, length, md5Hash, monitor);
            } catch (final IOException ex) {
                final var detail =
                    "Could not calculate hash for part %d of '%s': %s".formatted(partNum, path, ex.getMessage());
                return Result.failure(FailureValue.fromThrowable(FailureType.UPLOAD_PART_UNREADABLE,
                    "Failed to upload part", List.of(detail), ex));
            }
        });
    }

    private interface InputStreamSupplier {
        @Owning InputStream newInputStream() throws IOException;
    }

    private Result<Pair<Integer, EntityTag>, FailureValue> uploadWithRetries(final String path, final int partNum,
        final UploadTargetFetcher targetFetcher, final InputStreamSupplier inputData, final long numBytes,
        final byte[] md5Hash, final LeafExecMonitor monitor) throws CancelationException {

        // highest amount of written bytes reported across all retries (so that we don't overshoot or go backwards)
        final var bytesReported = new AtomicLong();

        // encode the hash only once
        final String md5Str = md5Hash == null ? null : Base64.encodeBase64String(md5Hash);

        // set a very small non-zero value to signal that the part upload job has started
        monitor.setProgress(Double.MIN_VALUE);

        var retriesRemaining = m_numRetries;
        for (var attempt = 0;; attempt++) {
            monitor.checkCanceled();

            final var bytesWrittenThisTry = new AtomicLong();
            final LongConsumer writtenBytesAdder = written -> {
                final var totalWritten = bytesWrittenThisTry.addAndGet(written);
                final long reported = bytesReported.get();
                final var newBytesWritten = totalWritten - reported;
                if (newBytesWritten > 0) {
                    bytesReported.set(totalWritten);
                    monitor.addTransferredBytes(newBytesWritten);
                    monitor.setProgress(0.99 * totalWritten / numBytes);
                }
            };

            try (final var inputStream = inputData.newInputStream()) {
                final var partUploadResult = uploadArtifactPart(path, partNum, attempt, targetFetcher, inputStream,
                    numBytes, md5Str, writtenBytesAdder, monitor.cancelChecker());
                monitor.done();
                return partUploadResult.map(eTag -> Pair.of(partNum, eTag));
            } catch (final IOException e) {
                if (retriesRemaining > 0) {
                    retriesRemaining--;
                    final var remaining = retriesRemaining;
                    LOGGER.atDebug() //
                        .addArgument(() -> "%d of '%s', %d/%d".formatted(partNum, path, remaining, m_numRetries)) //
                        .setCause(e) //
                        .log("Retrying to upload part {} retries left");
                } else {
                    return retriesExhaustedResult(path, e);
                }
            }
        }
    }

    @SuppressWarnings("resource") // only needed because we can't add external `@Owning` annotations
    private static @Owning InputStream newFileRangeInputStream(final Path file, final long offset, final long length)
            throws IOException {
        final var raf = new RandomAccessFile(file.toFile(), "r");
        raf.seek(offset);
        return new BoundedInputStream(RandomAccessFileInputStream.builder() //
            .setRandomAccessFile(raf) //
            .setCloseOnClose(true) //
            .get(), length);
    }

    private static byte[] calculateHash(final Path file, final long start, final long length) throws IOException {
        try (final var inStream = newFileRangeInputStream(file, start, length)) {
            return DigestUtils.md5(inStream);
        }
    }

    private Result<EntityTag, FailureValue> uploadArtifactPart(final String path, final int partNumber, // NOSONAR
        final int attempt, final UploadTargetFetcher targetFetcher, final @Owning InputStream artifactStream,
        final long numBytes, final String md5Hash, final LongConsumer writtenBytesAdder,
        final BooleanSupplier cancelChecker) throws CancelationException, IOException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.atDebug().log("Starting attempt {} of the upload of part {} of '{}' ({} bytes)", attempt + 1,
                partNumber, path, numBytes);
        }

        try (artifactStream) {
            final var uploadTarget = targetFetcher.fetch(partNumber);
            final var targetUrl = uploadTarget.getUrl();
            final var httpMethod = uploadTarget.getMethod();
            final var httpHeaders = new HashMap<String, List<String>>(uploadTarget.getHeader());

            if (m_sendContentMd5 && md5Hash != null) {
                httpHeaders.put(CONTENT_MD5_HEADER, List.of(md5Hash));
            }
            final var eTag = m_uploader.performUpload(targetUrl, httpMethod, httpHeaders, artifactStream, numBytes,
                writtenBytesAdder, cancelChecker);

            LOGGER.atDebug() //
                .addArgument(partNumber) //
                .addArgument(path) //
                .log("Ending upload of part {} of '{}'");

            return Result.success(eTag);
        } catch (final IOException e) {
            LOGGER.atDebug().setCause(e) //
                .addArgument(partNumber) //
                .addArgument(path) //
                .log("Failed to upload part {} of '{}'");
            throw e;
        } catch (final CancelationException e) {
            LOGGER.atDebug() //
                .addArgument(partNumber) //
                .addArgument(path) //
                .log("Cancelled upload of part {} of '{}'");
            throw e;
        }
    }

    private Result<Pair<Integer, EntityTag>, FailureValue> retriesExhaustedResult(final String path,
        final IOException e) {
        final var detail =
            "Part upload for '%s' failed after %d retries: %s".formatted(path, m_numRetries, e.getMessage());
        return Result.failure(FailureValue.fromThrowable(FailureType.PART_UPLOAD_EXHAUSTED_RETRIES,
            "Failed to upload part", List.of(detail), e));
    }
}
