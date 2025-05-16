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
 *   Created on Jun 23, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.util.KNIMETimer;
import org.knime.hub.client.sdk.CancelationException;

import com.google.common.util.concurrent.AtomicDouble;

/**
 * Common interface for {@link LeafExecMonitor} and {@link BranchingExecMonitor}, which together form a tree of
 * execution monitors in which the leaves report progress and the branch points aggregate the concurrent updates.
 */
public sealed interface ConcurrentExecMonitor {

    /**
     * @return a cancel checker that can be asked whether the user has requested the whole operation to be canceled
     */
    BooleanSupplier cancelChecker();

    /**
     * Marks the operation as done (100% progress).
     */
    void done();

    /**
     * Checks whether the user has canceled the operation and throws a {@link CancelationException}
     * if they have.
     *
     * @throws CancelationException if the user has canceled the operation
     */
    default void checkCanceled() throws CancelationException {
        if (cancelChecker().getAsBoolean()) {
            throw new CancelationException();
        }
    }

    /**
     * An execution monitor for an operation that is not split into multiple part but reports its own progress.
     *
     * @param cancelChecker used for checking whether the user has aborted the operation
     * @param bytesTransferred
     * @param progress
     */
    record LeafExecMonitor(BooleanSupplier cancelChecker, LongConsumer bytesTransferred,
            DoubleConsumer progress) implements ConcurrentExecMonitor {

        /**
         * Creates a null progress monitor which can be cancelled.
         *
         * @param cancelChecker for checking whether the user has requested cancellation
         * @return {@link LeafExecMonitor}
         */
        static LeafExecMonitor nullExecMonitor(final BooleanSupplier cancelChecker) {
            return new LeafExecMonitor(cancelChecker, l -> {}, p -> {});
        }

        @Override
        public void done() {
            progress.accept(1.0);
        }

        /**
         * Reports the current progress of the operation.
         *
         * @param newProgress new progress, between {@code 0.0} (no progress) and {@code 1.0} (finished)
         */
        public void setProgress(final double newProgress) {
            progress.accept(newProgress);
        }

        /**
         * Increments the number of bytes transferred by the operation by the given amount.
         *
         * @param processed number of additional bytes transferred, should be non-negative
         */
        public void addTransferredBytes(final long processed) {
            bytesTransferred.accept(processed);
        }
    }

    /**
     * An execution monitor for an operation that splits into multiple parts that report independent progress.
     */
    final class BranchingExecMonitor implements ConcurrentExecMonitor {

        /**
         * Creates a null progress monitor which can be cancelled.
         *
         * @param cancelChecker for checking whether the user has requested cancellation
         * @return {@link BranchingExecMonitor}
         */
        public static BranchingExecMonitor nullMonitor(final BooleanSupplier cancelChecker) {
            return new BranchingExecMonitor(cancelChecker, new AtomicLong(), p -> {});
        }

        /**
         * Progress snapshot.
         *
         * @param active list of currently active sub-operations, each represented as a pair of its name and progress
         * @param numDone number of finished tasks (with progress 100%)
         * @param totalProgress progress of the whole operation (between 0 and 1)
         */
        public static record ProgressStatus(List<Pair<String, Double>> active, int numDone, double totalProgress) {}

        /**
         * Representation of the progress of a sub-process.
         * <p><i>Example:</i> A sub-process having uploaded 17.3% of the file {@code /foo/bar/test.txt},
         * which represents 25% of this branching progress would have the following representation:
         * {@code new SubProgress("/foo/bar/test.txt", 0.173, new AtomicDouble(0.25))}
         *
         * @param name description of the sub-process reporting progress
         * @param contribution fraction of the whole progress contributed by the sub-progress
         * @param progress atomic reference holding the current progress of the sub-process (between 0.0 and 1.0)
         */
        private record SubProgress(String name, double contribution, AtomicDouble progress) {}

        private final Deque<SubProgress> m_subProgs = new ConcurrentLinkedDeque<>();
        private final AtomicLong m_bytesTransferred;
        private final DoubleConsumer m_upstreamProgress;
        private final BooleanSupplier m_cancelChecker;
        private double m_reported;

        private BranchingExecMonitor(final BooleanSupplier cancelChecker, final AtomicLong bytesTransferred,
            final DoubleConsumer upstreamProgress) {
            m_cancelChecker = cancelChecker;
            m_bytesTransferred = bytesTransferred;
            m_upstreamProgress = upstreamProgress;
        }

        /**
         * Creates a root execution monitor.
         *
         * @param cancelChecker for checking whether the user has requested cancellation
         * @param upstreamProgress callback for reporting progress
         */
        public BranchingExecMonitor(final BooleanSupplier cancelChecker, final DoubleConsumer upstreamProgress) {
            this(cancelChecker, new AtomicLong(), upstreamProgress);
        }

        /**
         * Creates a leaf monitor as a child of this context.
         *
         * @param name name of the sub-operation
         * @param contribution fraction of the progress of this monitor contributed by the sub-operation
         * @return context for the sub-operation
         */
        public LeafExecMonitor createLeafChild(final String name, final double contribution) {
            return new LeafExecMonitor(m_cancelChecker, m_bytesTransferred::addAndGet,
                createSubProgress(name, contribution));
        }

        /**
         * Creates a branching monitor as a child of this context.
         *
         * @param name name of the sub-operation
         * @param contribution fraction of the progress of this monitor contributed by the sub-operation
         * @return branching context for the sub-operation
         */
        public BranchingExecMonitor createBranchingChild(final String name, final double contribution) {
            return new BranchingExecMonitor(m_cancelChecker, m_bytesTransferred, createSubProgress(name, contribution));
        }

        @Override
        public void done() {
            m_subProgs.forEach(sub -> sub.progress.set(1.0));
            reportProgressUpstream(1.0);
        }

        /**
         * The number of bytes transferred until the current moment.
         *
         * @return number of bytes transferred in total
         */
        public long getBytesTransferred() {
            return m_bytesTransferred.get();
        }

        /**
         * Computes the current progress of the operation.
         *
         * @return status of the operation and its sub-operations
         */
        public ProgressStatus getStatus() {
            final List<Pair<String, Double>> active = new ArrayList<>();
            double totalProgress = 0;
            var done = 0;
            for (final var subProg : m_subProgs) {
                final var progress = subProg.progress.get();
                totalProgress += subProg.contribution() * progress;
                if (progress >= 1) {
                    done++;
                } else if (progress > 0) {
                    active.add(Pair.of(subProg.name(), progress));
                }
            }

            return new ProgressStatus(active, done, totalProgress);
        }

        @Override
        public BooleanSupplier cancelChecker() {
            return m_cancelChecker;
        }

        private DoubleConsumer createSubProgress(final String name, final double contribution) {
            final var subProgress = new SubProgress(name, contribution, new AtomicDouble());
            m_subProgs.add(subProgress);
            return partProgress -> { // NOSONAR
                subProgress.progress.set(partProgress);
                // there has been an update, always report at least a tiny bit of progress upstream
                reportProgressUpstream(Math.max(Double.MIN_VALUE, m_subProgs.stream() //
                    .mapToDouble(sub -> sub.progress.get() * sub.contribution()) //
                    .sum()));
            };
        }

        private void reportProgressUpstream(final double newProgress) {
            synchronized (m_upstreamProgress) {
                if (m_reported <= 0 || (m_reported < 1 && newProgress >= 1)
                        || (newProgress - m_reported) >= 0.0005) {
                    // don't report progress larger than 100% (imprecision, improper use)
                    m_reported = Math.min(newProgress, 1.0);
                    m_upstreamProgress.accept(m_reported);
                }
            }
        }
    }

    /**
     * Starts a polling task that calls a provided {@link Runnable} with the given interval and stops when
     * {@link ProgressPoller#close()} is called.
     *
     * @param interval duration between two invocations of the provided task
     * @return the closeable poller
     */
    static @Owning ProgressPoller startProgressPoller(final Duration interval) {
        final AtomicReference<Runnable> taskRef = new AtomicReference<>();
        final TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                final var task = taskRef.get();
                if (task != null) {
                    task.run();
                }
            }
        };
        KNIMETimer.getInstance().schedule(timerTask, 0, interval.toMillis());
        return new ProgressPoller(timerTask, taskRef);
    }

    /**
     * Progress poller that is invoked by the {@link KNIMETimer} until {@link #close()} is called.
     */
    final class ProgressPoller implements AutoCloseable {

        private final TimerTask m_timerTask;
        private final AtomicReference<Runnable> m_taskRef;

        ProgressPoller(final TimerTask timerTask, final AtomicReference<Runnable> taskRef) {
            m_timerTask = timerTask;
            m_taskRef = taskRef;
        }

        /**
         * Sets or replaces the task to be invoked periodically.
         *
         * @param task new task, may be {@code null}
         */
        public void setPollerTask(final Runnable task) {
            m_taskRef.set(task);
        }

        @Override
        public void close() {
            m_timerTask.cancel();
        }
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
     * Shortens a string representing a path to at most a given number of characters, replacing a middle section by
     * {@code "..."} if necessary.
     * <b>Examples:</b>
     * <ul>
     * <li>{@code shortenedPath("a/short/path")} returns {@code "a/short/path"}</li>
     * <li>{@code shortenedPath("/this/is/an/extremely-very-tremendously/long/path/with/many/segments/test.txt")}
     * returns {@code "/this/is/an/extremely-very-trem...th/with/many/segments/test.txt"}</li>
     * </ul>
     *
     * @param path path to be shortened
     * @param maxLength maximum result length
     * @return shortened string
     */
    static String shortenedPath(final String path, final int maxLength) {
        return StringUtils.abbreviateMiddle(path, "...", maxLength);
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
}
