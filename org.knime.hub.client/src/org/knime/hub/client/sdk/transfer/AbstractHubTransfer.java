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
import org.apache.commons.lang3.function.FailableSupplier;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor.ProgressStatus;

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
         * @throws CanceledExecutionException if the user cancelled
         */
        T handle(Throwable throwable) throws E, CanceledExecutionException;
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

    CatalogServiceClient m_catalogClient;

    /**
     * @param apiClient Hub API client
     * @param additionalHeaders additional headers for up and download
     */
    AbstractHubTransfer(final HubClientAPI apiClient, final Map<String, String> additionalHeaders) {
        m_catalogClient = new CatalogServiceClient(apiClient, additionalHeaders);
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
     * @throws E
     * @throws CanceledExecutionException
     */
    static <T, E extends Exception> T waitForCancellable(final Future<T> task, final BooleanSupplier cancelChecker,
            final ErrorHandler<T, E> errorHandler) throws E, CanceledExecutionException {
        try {
            while (true) {
                try {
                    if (cancelChecker.getAsBoolean()) { // NOSONAR
                        throw new CanceledExecutionException();
                    }
                    return task.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) { // NOSONAR
                    // continue waiting until cancelled
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CanceledExecutionException();
                } catch (final ExecutionException e) { // NOSONAR cause is propagated
                    return errorHandler.handle(e.getCause());
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
     * @throws CanceledExecutionException
     */
    Pair<RepositoryItem, EntityTag> deepListParent(final ItemID itemId, final BooleanSupplier cancelChecker)
            throws ResourceAccessException, CanceledExecutionException {
        return runInCommonPool(cancelChecker, () -> {
            final Pair<RepositoryItem, EntityTag> itemAndETag = m_catalogClient //
                .fetchRepositoryItem(itemId.id(), Map.of("details", "none"), null, null, null).orElseThrow();
            final var parentPath = IPath.forPosix(itemAndETag.getKey().getPath()).removeLastSegments(1);
            return m_catalogClient //
                .fetchRepositoryItem(parentPath.toString(), Map.of("deep", "true"), null, null, null).orElseThrow();
        });
    }

    /**
     * Deep-lists the subtree of the repository rooted at the given item ID.
     *
     * @param itemId root item ID
     * @param cancelChecker for checking cancellation
     * @return pair containing the deep repository item and its entity tag if successful
     * @throws ResourceAccessException
     * @throws CanceledExecutionException
     */
    Optional<Pair<RepositoryItem, EntityTag>> deepListItem(final ItemID itemId, final BooleanSupplier cancelChecker) 
            throws ResourceAccessException, CanceledExecutionException {
        return runInCommonPool(cancelChecker, () -> { // NOSONAR
            while (true) {
                final Pair<RepositoryItem, EntityTag> itemAndETag = m_catalogClient //
                    .fetchRepositoryItem(itemId.id(), Map.of("details", "none"), null, null, null).orElseThrow();
                final RepositoryItem repoItem = itemAndETag.getLeft();
                final EntityTag eTag = itemAndETag.getRight();
                final Optional<Pair<RepositoryItem, EntityTag>> deep = m_catalogClient //
                    .fetchRepositoryItem(repoItem.getPath(), Map.of("deep", "true"), null, null, eTag);
                if (deep.isPresent()) {
                    return deep;
                }
            }
        });
    }

    private static <T> T runInCommonPool(final BooleanSupplier cancelChecker,
            final FailableSupplier<T, ResourceAccessException> job)
            throws ResourceAccessException, CanceledExecutionException {
        return waitForCancellable(ForkJoinPool.commonPool().submit(job::get), cancelChecker, throwable -> {
            if (throwable instanceof RuntimeException rte) {
                throw rte;
            } else if (throwable instanceof CanceledExecutionException cee) {
                throw cee;
            } else {
                // the only declared exception
                throw (ResourceAccessException)throwable;
            }
        });
    }

}
