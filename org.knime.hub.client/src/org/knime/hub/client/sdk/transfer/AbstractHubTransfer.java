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
import java.util.Map;
import java.util.Optional;
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
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.transfer.CatalogServiceUtils.TaggedRepositoryItem;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor.ProgressStatus;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;

import jakarta.ws.rs.core.EntityTag;

/**
 * Common functionality for Hub upload and download clients.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
class AbstractHubTransfer {

    /**
     * Handler for {@link Throwable}s thrown by a {@link Future} in
     * {@link #waitForCancellable(Future, BooleanSupplier, ErrorHandler)}.
     *
     * @param <T> result type if the throwable can be handled
     * @param <E> declared exception
     */
    @FunctionalInterface
    interface ErrorHandler<T, E extends Exception> {
        /**
         * Handle the throwable thrown by a {@link Future}.
         *
         * @param throwable from the {@link Future}
         * @return result to be returned
         * @throws E custom declared exception
         * @throws CancelationException if the process got cancelled
         */
        T handle(Throwable throwable) throws E, CancelationException;
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
            final Consumer<ProgressStatus> reporter) {
        final var unitsOfWork = 1_000;
        progMon.beginTask(beginTaskMessage, unitsOfWork);
        final var splitter = new AtomicReference<BranchingExecMonitor>();
        final var prevWorked = new AtomicInteger();
        final DoubleConsumer progress = p -> {
            final var status = splitter.get().getStatus();
            final var previous = prevWorked.get();
            final var newWorked = (int)(status.totalProgress() * unitsOfWork);
            if (newWorked > previous) {
                progMon.worked(newWorked - previous);
                prevWorked.set(newWorked);
            }
            reporter.accept(status);
        };
        splitter.set(new BranchingExecMonitor(progMon::isCanceled, progress));
        return splitter.get();
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
     * @throws E exception which is thrown in case of {@link ExecutionException}
     */
    static <T, E extends Exception> T waitForCancellable(final Future<T> task,
        final BooleanSupplier cancelChecker,
        final ErrorHandler<T, E> errorHandler) throws E, CancelationException {
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
     * Deep-lists the subtree of the repository rooted at the parent item of the item with the given ID.
     *
     * @param itemId item ID of one child of the root item
     * @param cancelChecker for checking cancellation
     * @return pair containing the deep repository item and its entity tag
     * @throws ResourceAccessException
     * @throws CancelationException
     * @throws CouldNotAuthorizeException
     */
    TaggedRepositoryItem deepListParent(final ItemID itemId, final BooleanSupplier cancelChecker)
        throws IOException, CancelationException, CouldNotAuthorizeException {
        return runInCommonPool(cancelChecker, () -> {
            final var itemAndETag = CatalogServiceUtils.fetchRepositoryItem(m_catalogClient, m_clientHeaders,
                itemId.id(), Map.of("details", "none"), null, null, null).orElseThrow();
            final var parentPath = IPath.forPosix(itemAndETag.item().getPath()).removeLastSegments(1);
            return CatalogServiceUtils.fetchRepositoryItem(m_catalogClient, m_clientHeaders, parentPath.toString(),
                Map.of("deep", "true"), null, null, null).orElseThrow();
        });
    }

    /**
     * Deep-lists the subtree of the repository rooted at the given item ID.
     *
     * @param itemId root item ID
     * @param cancelChecker for checking cancellation
     * @return pair containing the deep repository item and its entity tag if successful
     * @throws ResourceAccessException
     * @throws CancelationException
     * @throws CouldNotAuthorizeException
     */
    Optional<TaggedRepositoryItem> deepListItem(final ItemID itemId, final BooleanSupplier cancelChecker)
            throws IOException, CancelationException, CouldNotAuthorizeException {
        return runInCommonPool(cancelChecker, () -> { // NOSONAR
            while (true) {
                final var itemAndETag = CatalogServiceUtils.fetchRepositoryItem(m_catalogClient, m_clientHeaders,
                    itemId.id(), Map.of("details", "none"), null, null, null).orElseThrow();
                final RepositoryItem repoItem = itemAndETag.item();
                final EntityTag eTag = itemAndETag.etag();
                final Optional<TaggedRepositoryItem> deep = CatalogServiceUtils.fetchRepositoryItem(m_catalogClient,
                    m_clientHeaders, repoItem.getPath(), Map.of("deep", "true"), null, null, eTag);
                if (deep.isPresent()) {
                    return deep;
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

    private interface CommonPoolJob<T> {
        T run() throws CouldNotAuthorizeException, IOException;
    }

    @SuppressWarnings("java:S1130") // exceptions are in fact thrown (sneakily)
    private static <T> T runInCommonPool(final BooleanSupplier cancelChecker, final CommonPoolJob<T> job)
            throws CouldNotAuthorizeException, IOException, CancelationException {
        return waitForCancellable(ForkJoinPool.commonPool().submit(job::run),
            cancelChecker, throwable -> { throw ExceptionUtils.asRuntimeException(throwable); });
    }

}
