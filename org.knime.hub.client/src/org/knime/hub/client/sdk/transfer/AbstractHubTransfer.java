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
 *   Created on Jun 15, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.ObjDoubleConsumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.KNIMETimer;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.Result.Success;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor.ProgressStatus;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.RuntimeDelegate;
import jakarta.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

/**
 * Common functionality for Hub upload and download clients.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
class AbstractHubTransfer {

    /** Media type for a KNIME Workflow. */
    static final MediaType KNIME_WORKFLOW_TYPE = new MediaType("application", "vnd.knime.workflow");

    /** Media type for a KNIME Workflow (KNWF). */
    static final MediaType KNIME_WORKFLOW_TYPE_ZIP = new MediaType("application", "vnd.knime.workflow+zip");

    /** Media type for a KNIME Workflow Group. */
    static final MediaType KNIME_WORKFLOW_GROUP_TYPE = new MediaType("application", "vnd.knime.workflow-group");

    /** Media type for a zipped KNIME Workflow Group (KNAR). */
    static final MediaType KNIME_WORKFLOW_GROUP_TYPE_ZIP = new MediaType("application", "vnd.knime.workflow-group+zip");

    /** Read timeout for expensive operations like {@link #initiateUpload(ItemID, UploadManifest, EntityTag)}. */
    static final Duration SLOW_OPERATION_READ_TIMEOUT = Duration.ofMinutes(15);

    private static final String KNIME_SERVER_NAMESPACE = "knime";

    /** Relation that points to the endpoint for initiating an async upload flow. */
    static final String INITIATE_UPLOAD = "%s:initiate-upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that points to the endpoint for initiating an artifact download. */
    static final String INITIATE_DOWNLOAD = "%s:download-artifact".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items download control. */
    static final String DOWNLOAD = "%s:download".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items upload control. */
    static final String UPLOAD = "%s:upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items edit control. */
    static final String EDIT = "edit";

    static final HeaderDelegate<EntityTag> ETAG_DELEGATE =
        RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);

    /**
     * Handler for {@link Throwable}s thrown by a {@link Future} in
     * {@link #waitForCancellable(Future, BooleanSupplier, ErrorHandler)}.
     *
     * @param <T> result type if the throwable can be handled
     */
    @FunctionalInterface
    interface ErrorHandler<T> {
        /**
         * Handle the throwable thrown by a {@link Future}.
         *
         * @param throwable from the {@link Future}
         * @return result to be returned
         * @throws CancelationException if the process got cancelled
         */
        Result<T, FailureValue> handle(Throwable throwable) throws CancelationException;
    }

    static final int MAX_PATH_LENGTH_IN_MESSAGE = 64;

    static final int PARALLELISM = 4;

    /** Thread pool for item uploads and downloads. */
    static final ExecutorService HUB_ITEM_TRANSFER_POOL;
    static {
        final var idSupplier = new AtomicInteger();
        final var executorService = new ThreadPoolExecutor(PARALLELISM, PARALLELISM, 10, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(), r -> new Thread(r, "KNIME-Hub-Transfer-" + idSupplier.getAndIncrement()));
        executorService.allowCoreThreadTimeOut(true);
        HUB_ITEM_TRANSFER_POOL = executorService;
    }

    final CatalogServiceClient m_catalogClient;
    final Map<String, String> m_clientHeaders;

    /**
     * @param hubClient Hub API client
     * @param additionalHeaders additional headers for up and download
     */
    AbstractHubTransfer(final @NotOwning HubClientAPI hubClient, final Map<String, String> additionalHeaders) {
        m_catalogClient = hubClient.catalog();
        m_clientHeaders = additionalHeaders;
    }

    /**
     * Initiates a multi-part process in which each part may update its progress independently and concurrently.
     *
     * @param progMon overall progress monitor
     * @param beginTaskMessage initial title for the task
     * @param reporter callback which is called whenever the overall progress should be updated
     * @return an execution context that can be split into sub-contexts for the parts
     */
    static BranchingExecMonitor beginMultiProgress(final IProgressMonitor progMon, final String beginTaskMessage,
        final ProgressPoller poller, final ObjDoubleConsumer<ProgressStatus> reporter) {
        final var unitsOfWork = 1_000;
        progMon.beginTask(beginTaskMessage, unitsOfWork);
        final var splitterRef = new AtomicReference<BranchingExecMonitor>();
        final var prevWorked = new AtomicInteger();
        final var statusRef = new AtomicReference<ProgressStatus>();

        final var splitter = new BranchingExecMonitor(progMon::isCanceled, p -> {
            final var status = splitterRef.get().getStatus();
            final var previous = prevWorked.get();
            final var newWorked = (int)(status.totalProgress() * unitsOfWork);
            if (newWorked > previous) {
                progMon.worked(newWorked - previous);
                prevWorked.set(newWorked);
            }
            statusRef.set(status);
        });
        splitterRef.set(splitter);

        // update the progress continually, with a transfer rate window size of 1.5 seconds
        poller.setPollerTask(new Updater(splitter::getBytesTransferred, 1_500) {
            @Override
            protected void update(final double bytesPerSecond) {
                final var status = statusRef.get();
                if (status != null) {
                    reporter.accept(status, bytesPerSecond);
                }
            }
        });
        return splitter;
    }

    private abstract static class Updater implements Runnable {

        private final LongSupplier m_bytesTransferred;
        private final int m_intervalMillis;

        private long m_lastUpdated;
        private long m_bytesWritten;
        private double m_transferRate;

        Updater(final LongSupplier bytesTransferred, final int intervalMillis) {
            m_bytesTransferred = bytesTransferred;
            m_intervalMillis = intervalMillis;
        }

        @Override
        public void run() {
            final var now = System.currentTimeMillis();
            final var millisSinceLastUpdate = now - m_lastUpdated;
            m_lastUpdated = now;

            final var bytesWrittenOld = m_bytesWritten;
            m_bytesWritten = m_bytesTransferred.getAsLong();
            final var newRate = millisSinceLastUpdate <= 0 ? 0
                : (1_000.0 * (m_bytesWritten - bytesWrittenOld) / millisSinceLastUpdate);

            final var retained = Math.exp(-1.0 * millisSinceLastUpdate / m_intervalMillis);
            m_transferRate = retained * m_transferRate + (1 - retained) * newRate;

            update(m_transferRate);
        }

        protected abstract void update(double bytesPerSecond);
    }

    interface ProgressPoller extends AutoCloseable {
        void setPollerTask(Runnable task);

        @Override
        void close();
    }

    static @Owning ProgressPoller startPoller(final Duration interval) {
        final AtomicReference<Runnable> pollerRef = new AtomicReference<>();
        final TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                final var poller = pollerRef.get();
                if (poller != null) {
                    poller.run();
                }
            }
        };

        KNIMETimer.getInstance().schedule(timerTask, 0, interval.toMillis());

        return new ProgressPoller() {
            @Override
            public void setPollerTask(final Runnable task) {
                pollerRef.set(task);
            }

            @Override
            public void close() {
                timerTask.cancel();
            }
        };
    }

    /**
     * Formats a value between 0 and 1 as a percentage padded to two digits before the decimal point.
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code percentage(0.0474)} returns {@code " 4.7%"} where the space is a "Figure Space" U+2007</li>
     *   <li>{@code percentage(0.9731)} returns {@code "97.3%"}</li>
     * </ul>
     *
     * @param value fraction to be represented as a percentage, must be between {@code 0.0} and {@code 1.0}
     * @return formatted string
     */
    static String percentage(final double value) {
        // use the "Figure Space" U+2007 for padding, it's the same width as a digit
        return StringUtils.leftPad("%.1f%%".formatted(100.0 * value), 5, "\u2007");
    }

    /**
     * Shortens a string representing a path to at most {@link #MAX_PATH_LENGTH_IN_MESSAGE} characters, replacing a
     * middle section by {@code "..."} if necessary.
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code shortenedPath("a/short/path")} returns {@code "a/short/path"}</li>
     *   <li>{@code shortenedPath("/this/is/an/extremely-very-tremendously/long/path/with/many/segments/test.txt")}
     *      returns {@code "/this/is/an/extremely-very-trem...th/with/many/segments/test.txt"}</li>
     * </ul>
     *
     * @param path path to be shortened
     * @return shortened string
     */
    static String shortenedPath(final String path) {
        return StringUtils.abbreviateMiddle(path, "...", MAX_PATH_LENGTH_IN_MESSAGE);
    }

    /**
     * Formats a number of bytes as a human-readable string. The space between number and unit is a "Thin Space" U+2009.
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code bytesToHuman(3.0)} returns {@code "3.0 B"}</li>
     *   <li>{@code bytesToHuman(123456789.0)} returns {@code "117.7 MB"}</li>
     *   <li>{@code bytesToHuman(3945873069030.0)} returns {@code "3674.9 GB"}</li>
     * </ul>
     *
     * @param numBytes number of bytes
     * @return formatted string
     */
    static String bytesToHuman(final double numBytes) {
        // use a "Thin Space" U+2009 between number and unit
        if (numBytes >= FileUtils.ONE_GB) {
            return "%.1f\u2009GB".formatted(numBytes / FileUtils.ONE_GB);
        } else if (numBytes >= FileUtils.ONE_MB) {
            return "%.1f\u2009MB".formatted(numBytes / FileUtils.ONE_MB);
        } else if (numBytes >= FileUtils.ONE_KB) {
            return "%.1f\u2009KB".formatted(numBytes / FileUtils.ONE_KB);
        } else {
            return "%.1f\u2009B".formatted(numBytes);
        }
    }

    /**
     * Waits for the completion of the given {@link Future} while checking periodically whether the operation has
     * been cancelled.
     *
     * @param <T> return type of the {@link Future}
     * @param <E> declared exception
     * @param task to throw from the error handler
     * @param cancelChecker for checking whether the user requested cancellation
     * @param errorHandler error handler for handling errors/exceptions in the {@link Future}'s execution
     * @return result
     */
    static <T> Result<T, FailureValue> waitForCancellable(final Future<Result<T, FailureValue>> task,
        final BooleanSupplier cancelChecker, final ErrorHandler<T> errorHandler) throws CancelationException {
        try {
            while (true) {
                if (cancelChecker.getAsBoolean()) {
                    throw new CancelationException();
                }
                try {
                    return task.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) { // NOSONAR
                    // continue waiting until cancelled
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancelationException();
                } catch (final ExecutionException e) { // NOSONAR cause is propagated
                    final Throwable cause = e.getCause();
                    if (cause instanceof CancelationException ce) {
                        throw ce;
                    }
                    return errorHandler.handle(cause);
                }
            }
        } finally {
            task.cancel(true);
        }
    }

    /**
     * Repository item with associated {@code null}-able etag.
     *
     * @param item non-{@code null} repository item
     * @param etag entity tag
     */
    public record TaggedRepositoryItem(RepositoryItem item, EntityTag etag) {
        /**
         * Constructor.
         *
         * @param item non-{@code null} item
         * @param etag {@code null}-able etag
         */
        public TaggedRepositoryItem {
            CheckUtils.checkNotNull(item);
        }
    }

    /**
     * Fetches a repository item from the catalog.
     *
     * @param itemIDOrPath either an item ID or a path to an item in the catalog
     * @param queryParams query parameters, may be {@code null}
     * @param version item version, may be {@code null}
     * @param ifNoneMatch entity tag for the {@code If-None-Match: <ETag>} header, may be null
     * @param ifMatch entity tag for the {@code If-Match: <ETag>} header, may be null
     * @return pair of fetched repository item and corresponding entity tag, or {@link Optional#empty()} if
     *         {@code ifNoneMatch} was non-{@code null} and the HTTP response was {@code 304 Not Modified} or
     *         {@code ifMatch} was non-{@code null} and the HTTP response was {@code 412 Precondition Failed}
     * @throws IOException if an I/O error occurred
     */
    static Result<Optional<TaggedRepositoryItem>, FailureValue> fetchRepositoryItem(
        final CatalogServiceClient catalogClient, final Map<String, String> additionalHeaders,
        final String itemIDOrPath, final Map<String, String> queryParams, final ItemVersion version,
        final EntityTag ifNoneMatch, final EntityTag ifMatch) throws HubFailureIOException {

        Map<String, String> headers = new HashMap<>(additionalHeaders);
        headers.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        if (ifNoneMatch != null) {
            headers.put(HttpHeaders.IF_NONE_MATCH, AbstractHubTransfer.ETAG_DELEGATE.toString(ifNoneMatch));
        }
        if (ifMatch != null) {
            headers.put(HttpHeaders.IF_MATCH, AbstractHubTransfer.ETAG_DELEGATE.toString(ifMatch));
        }

        Map<String, String> nonNullQueryParams = new HashMap<>();
        if (queryParams != null) {
            nonNullQueryParams.putAll(queryParams);
        }

        final var detailsParam = nonNullQueryParams.get("details");
        final var deepParam = Boolean.valueOf(nonNullQueryParams.get("deep"));
        final var spaceDetailsParam = Boolean.valueOf(nonNullQueryParams.get("spaceDetails"));
        final var contribSpacesParam = nonNullQueryParams.get("contribSpaces");

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = itemIDOrPath.startsWith("*")
                ? catalogClient.getRepositoryItemById(itemIDOrPath, detailsParam, deepParam, spaceDetailsParam,
                    contribSpacesParam, version, headers)
                : catalogClient.getRepositoryItemByPath(IPath.forPosix(itemIDOrPath), detailsParam, deepParam,
                    spaceDetailsParam, contribSpacesParam, version, headers);
            if ((ifNoneMatch != null && response.statusCode() == Status.NOT_MODIFIED.getStatusCode())
                || (ifMatch != null && response.statusCode() == Status.PRECONDITION_FAILED.getStatusCode())) {
                return Result.success(Optional.empty());
            }

            return response.result() //
                .map(item -> Optional.of(new TaggedRepositoryItem(item, response.etag().orElse(null))));
        }
    }

    /**
     * Deep-lists the subtree of the repository rooted at the parent item of the item with the given ID.
     *
     * @param itemId item ID of one child of the root item
     * @param cancelChecker for checking cancellation
     * @return pair containing the deep repository item and its entity tag
     * @throws ResourceAccessException
     * @throws CancelationException
     * @throws CouldNotAuthorizeException
     */
    Result<TaggedRepositoryItem, FailureValue> deepListParent(final ItemID itemId, final BooleanSupplier cancelChecker)
        throws CancelationException {
        return runInCommonPool(cancelChecker, () -> { // NOSONAR
            final var pathResult = fetchRepositoryItem(m_catalogClient, m_clientHeaders,
                itemId.id(), Map.of("details", "none"), null, null, null);
            if (!(pathResult instanceof Success<Optional<TaggedRepositoryItem>, ?> pathSuccess)) {
                return pathResult.asFailure();
            }

            final var itemAndETag = pathSuccess.value().orElseThrow(); // no ETag was given, so `empty()` would be a bug
            final var parentPath = IPath.forPosix(itemAndETag.item().getPath()).removeLastSegments(1);
            final var deepResult = fetchRepositoryItem(m_catalogClient, m_clientHeaders,
                parentPath.toString(), Map.of("deep", "true"), null, null, null);
            if (!(deepResult instanceof Success<Optional<TaggedRepositoryItem>, ?> deepSuccess)) {
                return deepResult.asFailure();
            }

            return Result.success(deepSuccess.value().orElseThrow());
        });
    }

    /**
     * Deep-lists the subtree of the repository rooted at the given item ID.
     *
     * @param itemId root item ID
     * @param cancelChecker for checking cancellation
     * @return pair containing the deep repository item and its entity tag if successful
     * @throws CancelationException
     * @throws CouldNotAuthorizeException
     */
    Result<TaggedRepositoryItem, FailureValue> deepListItem(final ItemID itemId,
        final BooleanSupplier cancelChecker) throws CancelationException {
        return runInCommonPool(cancelChecker, () -> { // NOSONAR
            while (true) {
                final var pathResult = fetchRepositoryItem(m_catalogClient, m_clientHeaders,
                    itemId.id(), Map.of("details", "none"), null, null, null);
                if (!(pathResult instanceof Success<Optional<TaggedRepositoryItem>, ?> pathSuccess)) {
                    return pathResult.asFailure();
                }

                final var itemAndETag = pathSuccess.value().orElseThrow();
                final String itemPath = itemAndETag.item().getPath();
                final EntityTag eTag = itemAndETag.etag();
                final var deepResult = fetchRepositoryItem(m_catalogClient, m_clientHeaders,
                    itemPath, Map.of("deep", "true"), null, null, eTag);
                if (!(deepResult instanceof Success<Optional<TaggedRepositoryItem>, ?> deepSuccess)) {
                    return deepResult.asFailure();
                }

                Optional<TaggedRepositoryItem> deep = deepSuccess.value();
                if (deep.isPresent()) {
                    return Result.success(deep.get());
                }
            }
        });
    }

    static void writeStreamToFileWithProgress(@Owning final InputStream in, final long numBytes, final Path outFile,
        final LeafExecMonitor monitor) throws IOException, CancelationException {
        final var bufferSize = (int)(FileUtils.ONE_MB / 2);
        try (final var bufferedInStream = new BufferedInputStream(in, bufferSize);
                final var outStream = Files.newOutputStream(outFile)) {
            final var buffer = new byte[bufferSize];
            long written = 0;
            for (int read; (read = bufferedInStream.read(buffer)) >= 0;) {
                monitor.checkCanceled();
                outStream.write(buffer, 0, read);
                written += read;
                monitor.addTransferredBytes(read);
                if (numBytes >= 0) {
                    monitor.setProgress(Math.min(1.0 * written / numBytes, 1.0));
                }
            }
        }
    }

    @FunctionalInterface
    private interface CommonPoolJob<T> extends Callable<Result<T, FailureValue>> {
        @Override
        Result<T, FailureValue> call() throws CouldNotAuthorizeException, HubFailureIOException;
    }

    private static <T> Result<T, FailureValue> runInCommonPool(final BooleanSupplier cancelChecker,
        final CommonPoolJob<T> job) throws CancelationException {
        return waitForCancellable(ForkJoinPool.commonPool().submit(job), cancelChecker, throwable -> { // NOSONAR
            // exceptions not `instanceof RuntimeException`s are wrapped in `new RuntimeException(thrw)` by the
            // framework, unpack those (and *only* those) here
            var thrw = throwable;
            while (thrw.getClass() == RuntimeException.class && thrw.getCause() != null) {
                thrw = thrw.getCause();
            }

            if (thrw instanceof HubFailureIOException failureEx) {
                return Result.failure(failureEx.getValue());
            } else if (thrw instanceof CouldNotAuthorizeException cnae) {
                return Result.failure(FailureValue.fromAuthFailure(cnae));
            } else {
                return Result
                    .failure(FailureValue.fromUnexpectedThrowable("Unexpected exception: " + thrw.getMessage(), thrw));
            }
        });
    }

    /**
     * Interface for polling calls to Hub.
     *
     * @param <T> type of the responses
     */
    interface PollingCallable<T> {

        /**
         * Hub call that should be made continuusly.
         *
         * @return API response
         * @throws HubFailureIOException if the call failed without a response from Hub
         */
        ApiResponse<T> poll() throws HubFailureIOException;

        /**
         * Whether or not to accept the resunt and stop polling.
         *
         * @param result result of the last polling call
         * @return {@code true} if polling should be stopped, {@code false otherwise}
         */
        boolean accept(T result);
    }

    /**
     * Invokes the given {@link PollingCallable}'s {@link PollingCallable#poll()} continuously until either
     * {@link PollingCallable#accept(Object)} returns {@code true} or until the timeout has run out.
     *
     * @param <T> type of the Hub response
     * @param timeoutMillis timeout, {@code -1} for no timeout
     * @param callable polling callable
     * @return the last response object received from Hub
     * @throws InterruptedException if the thread was interrupted
     */
    static <T> Result<T, FailureValue> poll(final long timeoutMillis, final PollingCallable<T> callable)
            throws InterruptedException {

        final var t0 = System.currentTimeMillis();
        for (var numberOfStatusPolls = 0L;; numberOfStatusPolls++) {
            final T state;
            try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                final var response = callable.poll();
                final Result<T, FailureValue> result = response.result();
                if (!(result instanceof Success<T, ?> success)) {
                    return result;
                }
                state = success.value();
            } catch (final HubFailureIOException ex) { // NOSONAR
                return Result.failure(ex.getValue());
            }

            if (callable.accept(state)) {
                return Result.success(state);
            }

            // Sequence: 200ms, 400ms, 600ms, 800ms and then 1s until the timeout is reached
            Thread.sleep(numberOfStatusPolls < 4 ? (200 * (numberOfStatusPolls + 1)) : 1_000);

            final long elapsed = System.currentTimeMillis() - t0;
            if (timeoutMillis >= 0 && elapsed >= timeoutMillis) {
                return Result.success(state);
            }
        }
    }
}
