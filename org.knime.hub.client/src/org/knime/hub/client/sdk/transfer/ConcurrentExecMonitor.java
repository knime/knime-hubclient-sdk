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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;

import org.apache.commons.lang3.tuple.Pair;
import org.knime.hub.client.sdk.CancelationException;

import com.google.common.util.concurrent.AtomicDouble;

/**
 * Common interface for {@link LeafExecMonitor} and {@link BranchingExecMonitor}, which together form a tree of
 * execution monitors in which the leaves report progress and the branch points aggregate the concurrent updates.
 */
public sealed interface ConcurrentExecMonitor {

    /** Size of the sliding window used for the transfer rate reporting (e.g. "3.7 MB/sec"). */
    Duration THROUGHPUT_WINDOW_SIZE = Duration.ofSeconds(2);

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
         * @param active list of currently active sub-operations, each represented as a pair of its name and progress
         * @param numDone number of finished tasks (with progress 100%)
         * @param totalProgress progress of the whole operation (between 0 and 1)
         * @param bytesPerSecond current transfer rate in bytes per second
         */
        public static record ProgressStatus(List<Pair<String, Double>> active, int numDone, double totalProgress,
            double bytesPerSecond) {}

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

        /**
         * Representation of the number of bytes transferred up until a certain point in time.
         *
         * @param timestamp point in time at which the snapshot was taken
         * @param numBytesTransferred number of bytes transferred up until {@link #timestamp()}
         */
        private record TransferredBytesSnapshot(long timestamp, long numBytesTransferred) {}

        private final Deque<SubProgress> m_subProgs = new ConcurrentLinkedDeque<>();
        private final Deque<TransferredBytesSnapshot> m_snapshots = new ArrayDeque<>();
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

            final long newTimestamp = System.currentTimeMillis();
            final long newTransferred = m_bytesTransferred.get();
            final double bytesPerSecond;
            synchronized (this) {
                while (m_snapshots.size() > 1
                        && (newTimestamp - m_snapshots.peekFirst().timestamp()) > THROUGHPUT_WINDOW_SIZE.toMillis()) {
                    m_snapshots.removeFirst();
                }
                if (m_snapshots.isEmpty()) {
                    bytesPerSecond = 0;
                } else {
                    final var first = m_snapshots.peekFirst();
                    final long millisecondsDiff = newTimestamp - first.timestamp();
                    final long bytesDiff = newTransferred - first.numBytesTransferred();
                    bytesPerSecond = bytesDiff / (millisecondsDiff / 1000.0);
                }
                m_snapshots.addLast(new TransferredBytesSnapshot(newTimestamp, newTransferred));
            }
            return new ProgressStatus(active, done, totalProgress, bytesPerSecond);
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
}
