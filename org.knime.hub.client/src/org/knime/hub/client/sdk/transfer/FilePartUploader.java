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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.ent.UploadTarget;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.ext.RuntimeDelegate;
import jakarta.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

/**
 * Uploads files or file parts via HTTP requests through an {@link HttpURLConnection}.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public final class FilePartUploader {

    private static final boolean SEND_CONTENT_MD5 = false;

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
         * @throws CouldNotAuthorizeException
         */
        UploadTarget fetch(int partNo) throws IOException, CouldNotAuthorizeException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(FilePartUploader.class);

    /** Maximum number of simultaneous upload connections. */
    private static final int PARALLELISM = 4;

    /** Delegate for reading and writing ETags. */
    private static final HeaderDelegate<EntityTag> ETAG_DELEGATE =
        RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);

    /** Thread pool for part uploads. */
    private static final ExecutorService FILE_PART_UPLOAD_POOL;
    static {
        final var idSupplier = new AtomicInteger();
        final var threadNamePrefix = "KNIME-" + FilePartUploader.class.getSimpleName() + "-";
        FILE_PART_UPLOAD_POOL = new ThreadPoolExecutor(PARALLELISM, PARALLELISM, 10, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(), r -> new Thread(r, threadNamePrefix + idSupplier.getAndIncrement()));
        ((ThreadPoolExecutor)FILE_PART_UPLOAD_POOL).allowCoreThreadTimeOut(true);
    }

    private final Duration m_connectTimeout;

    private final Duration m_readTimeout;

    private final int m_numRetries;

    /**
     * @param connectTimeout connect timeout for connections in milliseconds
     * @param readTimeout read timeout for connections in milliseconds
     * @param numRetries number of retries allowed if an upload fails
     */
    public FilePartUploader(final Duration connectTimeout, final Duration readTimeout, final int numRetries) {
        CheckUtils.checkArgument(numRetries >= 0, "Number of retries must be non-negative, found %d", numRetries);
        m_connectTimeout = connectTimeout;
        m_readTimeout = readTimeout;
        m_numRetries = numRetries;
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
    public Future<Pair<Integer, EntityTag>> uploadTempFile(final String path, final Integer partNum,
            final UploadTargetFetcher targetFetcher, final Path file, final long fileSize, final byte[] md5Hash,
            final LeafExecMonitor monitor) {
        return FILE_PART_UPLOAD_POOL.submit(() -> uploadTempFileJob(path, partNum, targetFetcher, file, fileSize,
            md5Hash == null ? null : Base64.encodeBase64String(md5Hash), monitor));
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
    public Future<Pair<Integer, EntityTag>> uploadDataChunk(final String path, final Integer partNum,
            final UploadTargetFetcher targetFetcher, final byte[] dataChunk, final byte[] md5Hash,
            final LeafExecMonitor monitor) {
        return FILE_PART_UPLOAD_POOL.submit(() -> uploadDataChunkJob(path, partNum, targetFetcher, dataChunk,
            md5Hash == null ? null : Base64.encodeBase64String(md5Hash), monitor));
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
    public Future<Pair<Integer, EntityTag>> uploadDataFilePart(final String path, final Integer partNum,
            final UploadTargetFetcher targetFetcher, final Path file, final long offset, final long length,
            final LeafExecMonitor monitor) {
        return FILE_PART_UPLOAD_POOL.submit( //
            () -> uploadDataFilePartJob(path, partNum, targetFetcher, file, offset, length, monitor));
    }

    private Pair<Integer, EntityTag> uploadTempFileJob(final String path, final Integer partNum,
            final UploadTargetFetcher targetFetcher, final Path file, final long fileSize, final String md5Hash,
            final LeafExecMonitor monitor) throws IOException, CancelationException, CouldNotAuthorizeException {
        try {
            var retriesRemaining = m_numRetries;
            for (var attempt = 0;; attempt++) {
                try {
                    try (final var inputStream = Files.newInputStream(file)) { // NOSONAR
                        return Pair.of(partNum, uploadArtifactPart(path, partNum, attempt, targetFetcher,
                            inputStream, fileSize, md5Hash, monitor));
                    }
                } catch (final IOException e) {
                    if (retriesRemaining > 0) {
                        retriesRemaining--;
                        final var remaining = retriesRemaining;
                        LOGGER.atDebug()
                            .addArgument(() -> "%d of '%s', %d/%d".formatted(partNum, path, remaining, m_numRetries))
                            .setCause(e)
                            .log("Retrying to upload part {} retries left");
                    } else {
                        throw e;
                    }
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private Pair<Integer, EntityTag> uploadDataChunkJob(final String path, final Integer partNum,
        final UploadTargetFetcher targetFetcher, final byte[] dataChunk, final String md5Hash,
        final LeafExecMonitor monitor) throws IOException, CancelationException, CouldNotAuthorizeException {
        final var fileChunkSize = dataChunk.length;
        var retriesRemaining = m_numRetries;
        for (var attempt = 0;; attempt++) {
            try {
                try (final var inputStream = new ByteArrayInputStream(dataChunk)) {
                    return Pair.of(partNum, uploadArtifactPart(path, partNum, attempt, targetFetcher, inputStream,
                        fileChunkSize, md5Hash, monitor));
                }
            } catch (final IOException e) {
                if (retriesRemaining > 0) {
                    retriesRemaining--;
                    final var remaining = retriesRemaining;
                    LOGGER.atDebug()
                        .addArgument(() -> "%d of '%s', %d/%d".formatted(partNum, path, remaining, m_numRetries))
                        .setCause(e).log("Retrying to upload part {} retries left");
                } else {
                    throw e;
                }
            }
        }
    }

    @SuppressWarnings("resource")
    private static @Owning InputStream newFileRangeInputStream(final Path file, final long offset, final long length)
            throws IOException {
        final var raf = new RandomAccessFile(file.toFile(), "r"); // NOSONAR closed by the caller
        raf.seek(offset);
        return new BoundedInputStream(RandomAccessFileInputStream.builder() //
            .setRandomAccessFile(raf) //
            .setCloseOnClose(true) //
            .get(), length);
    }

    private Pair<Integer, EntityTag> uploadDataFilePartJob(final String path, final int partNum,
            final UploadTargetFetcher targetFetcher, final Path dataFile, final long start, final long length,
            final LeafExecMonitor monitor) throws IOException, CancelationException, CouldNotAuthorizeException {
        final String md5Hash = SEND_CONTENT_MD5 ? calculateHash(dataFile, start, length) : null;
        var retriesRemaining = m_numRetries;
        for (var attempt = 0;; attempt++) {
            try (final var inputStream = newFileRangeInputStream(dataFile, start, length)) {
                return Pair.of(partNum, uploadArtifactPart(path, partNum, attempt, targetFetcher, inputStream,
                    length, md5Hash, monitor));
            } catch (final IOException e) {
                if (retriesRemaining > 0) {
                    retriesRemaining--;
                    final var remaining = retriesRemaining;
                    LOGGER.atDebug()
                        .addArgument(() -> "%d of '%s', %d/%d".formatted(partNum, path, remaining, m_numRetries))
                        .setCause(e)
                        .log("Retrying to upload part {} retries left");
                } else {
                    throw e;
                }
            }
        }
    }

    private static String calculateHash(final Path file, final long start, final long length) throws IOException {
        try (final var inStream = newFileRangeInputStream(file, start, length)) {
            return Base64.encodeBase64String(DigestUtils.md5(inStream));
        }
    }

    @SuppressWarnings("unused")
    private HttpURLConnection prepareConnection(final UploadTarget uploadTarget, final long numBytes,
            final String md5Hash) throws IOException {
        final var conn = (HttpURLConnection)URLConnectionFactory.getConnection(uploadTarget.getUrl());
        conn.setRequestMethod(uploadTarget.getMethod());
        conn.setFixedLengthStreamingMode(numBytes);
        conn.setDoOutput(true);
        conn.setConnectTimeout(Math.toIntExact(m_connectTimeout.toMillis()));
        conn.setReadTimeout(Math.toIntExact(m_readTimeout.toMillis()));
        uploadTarget.getHeader().forEach((key, vals) -> vals.forEach(val -> conn.addRequestProperty(key, val)));
        if (SEND_CONTENT_MD5 && md5Hash != null) {
            conn.addRequestProperty("Content-MD5", md5Hash);
        }
        return conn;
    }

    private EntityTag uploadArtifactPart(final String path, final int partNumber, final int attempt, // NOSONAR
            final UploadTargetFetcher targetFetcher, final InputStream artifactStream, final long numBytes,
            final String md5Hash, final LeafExecMonitor monitor)
            throws IOException, CancelationException, CouldNotAuthorizeException {
        LOGGER.atDebug().log("Starting attempt {} of the upload of part {} of '{}' ({} bytes)", //
            attempt + 1, partNumber, path, numBytes);

        // set a very small non-zero value to signal that the part upload job has started
        monitor.setProgress(Double.MIN_VALUE);
        final HttpURLConnection conn = prepareConnection(targetFetcher.fetch(partNumber), numBytes, md5Hash);
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            conn.connect();

            var read = -1;
            var written = 0L;
            try (final var outStream = new BufferedOutputStream(conn.getOutputStream());
                    final var inStream = new BufferedInputStream(artifactStream)) {
                final var buffer = new byte[(int)FileUtils.ONE_MB / 2];
                while ((read = inStream.read(buffer)) >= 0) {
                    monitor.checkCanceled();
                    outStream.write(buffer, 0, read);
                    written += read;
                    monitor.addTransferredBytes(read);
                    monitor.setProgress(0.95 * written / numBytes);
                }
            } catch (final IOException e) {
                LOGGER.atDebug().setCause(e) //
                    .addArgument(partNumber) //
                    .addArgument(path) //
                    .log("Failed to upload part {} of '{}'");
                throw e;
            } catch (final CancelationException e) {
                LOGGER.atDebug().setCause(e) //
                    .addArgument(partNumber)
                    .addArgument(path)
                    .log("Cancelled upload of part {} of '{}'");
                throw e;
            }

            final var status = Response.Status.fromStatusCode(conn.getResponseCode());
            monitor.setProgress(0.97);
            if (status.getFamily() != Family.SUCCESSFUL) {
                final String result = readErrorMessage(conn);
                final var httpStatus = "%d %s".formatted(status.getStatusCode(), status.getReasonPhrase());
                final var message = "Upload of part %d of '%s' unsuccessful (%s)%s" //
                    .formatted(partNumber, path, httpStatus, StringUtils.isBlank(result) ? "" : (": " + result));
                throw HttpExceptionUtils.wrapException(status.getStatusCode(), message);
            }

            try (final var responseStream = conn.getInputStream()) {
                return ETAG_DELEGATE.fromString((conn.getHeaderField(HttpHeaders.ETAG)));
            } finally {
                monitor.setProgress(0.99);
            }
        } finally {
            LOGGER.atDebug() //
                .addArgument(partNumber) //
                .addArgument(path) //
                .log("Ending upload of part {} of '{}'");
            monitor.done();
            conn.disconnect();
        }
    }

    private static String readErrorMessage(final HttpURLConnection conn) throws IOException {
        try (final var errStream = conn.getErrorStream()) {
            return errStream == null ? null : new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
