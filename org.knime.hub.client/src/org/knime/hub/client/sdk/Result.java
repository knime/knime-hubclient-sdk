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
package org.knime.hub.client.sdk;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.apache.commons.lang3.function.FailableFunction;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Result of an operation, either a {@link Success} containing a value or a {@link Failure} containing a message
 * and/or a {@link Throwable}.
 *
 * @param <V> type of the result's value
 * @param <E> type of the result's failure
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public sealed interface Result<V, E> {

    /**
     * Creates a successful result containing a result value.
     *
     * @param <V> type of the result's value
     * @param <E> type of the failure (ignored here)
     * @param key key of the result (may be {@code null})
     * @param value value of the result (may be {@code null})
     * @return result denoting a success
     */
    static <V, E> Success<V, E> success(final V value) {
        return new Success<>(value);
    }

    /**
     * Creates a failure result containing information about the failure.
     *
     * @param <V> type of the result's value
     * @param <E> type of the failure
     * @param key key of the result (may be {@code null})
     * @param failure failure object (may be {@code null})
     * @param problemJSON error in RFC9457 standard format (may be {@code null})
     * @param cause cause of the failure (may be {@code null})
     * @return result denoting a failure
     */
    static <V, E> Failure<V, E> failure(final E failure) {
        return new Failure<>(failure);
    }

    /**
     * @return whether or not this result denotes a success
     */
    default boolean successful() {
        return match(v -> true, f -> false);
    }

    /**
     * Converts the result into an optional value by extracting the value if the result is successful.
     *
     * @return extracted value if this result denotes a success, {@link Optional#empty()} otherwise
     */
    default Optional<V> toOptional() {
        return match(Optional::of, failure -> Optional.empty());
    }

    /**
     * Creates a copy of this result in which the given function is applied to the contained value if this result
     * represents a success. If it is instead a failure, only the type changes.
     *
     * @param <W> return type of the mapper function
     * @param mapper function to be applied to the result value
     * @return potentially modified copy of this result
     * @throws T if thrown from the mapper function
     */
    default <W, T extends Throwable> Result<W, E> map(final FailableFunction<V, W, T> mapper) throws T {
        return andThen(mapper.andThen(Result::success));
    }

    /**
     * A collector which partitions the results into successes (storing only the contained values) and failures.
     *
     * @param <V> value type
     * @param <E> failure type
     * @return pair of a list of successes and a list of failures
     */
    static <V, E> Collector<Result<V, E>, ?, Pair<List<V>, List<Failure<Void, E>>>> partitioningCollector() {
        return new Collector<Result<V, E>, Pair<List<V>, List<Failure<Void, E>>>, // NOSONAR
                Pair<List<V>, List<Failure<Void, E>>>>() {
            @Override
            public Supplier<Pair<List<V>, List<Failure<Void, E>>>> supplier() {
                return () -> Pair.of(new ArrayList<>(), new ArrayList<>());
            }

            @Override
            public BiConsumer<Pair<List<V>, List<Failure<Void, E>>>, Result<V, E>> accumulator() {
                return (partial, result) -> {
                    if (result instanceof Success<V, E> success) {
                        partial.getLeft().add(success.value);
                    } else {
                        partial.getRight().add(((Failure<V, E>) result).coerceResultType());
                    }
                };
            }

            @Override
            public BinaryOperator<Pair<List<V>, List<Failure<Void, E>>>> combiner() {
                return (left, right) -> {
                    left.getLeft().addAll(right.getLeft());
                    left.getRight().addAll(right.getRight());
                    return left;
                };
            }

            @Override
            public Function<Pair<List<V>, List<Failure<Void, E>>>, Pair<List<V>, List<Failure<Void, E>>>> finisher() {
                return Function.identity();
            }

            @Override
            public Set<Characteristics> characteristics() {
                return EnumSet.of(Characteristics.IDENTITY_FINISH);
            }
        };
    }

    /**
     * Returns the contained value if this result is successful and throws the {@link Throwable} obtained from the
     * supplied function otherwise.
     *
     * @param errSupplier supplier for custom throwable, called with message and cause of the {@link Failure}
     * @param <T> type of the custom throwable
     * @return contained value if this result is {@link #successful()}
     * @throws T if this result is a {@link Failure}
     */
    default <T extends Throwable> V orElseThrow(final Function<E, T> errSupplier) throws T {
        return match(val -> val, failure -> {
            throw errSupplier.apply(failure);
        });
    }

    /**
     * If this result is successful, this method calls the given function and returns its result. If it is a failure
     * instead, the failure is coerced to the new result type and returned. This allows multiple potentially failing
     * operations to be chained, stopping at the first failure.
     *
     * @param <W> new return value type
     * @param <T> type of the throwable that may be thrown from the given function
     * @param func next step to apply to the value
     * @return result
     * @throws T if thrown from the function
     */
    <W, T extends Throwable> Result<W, E> andThen(final FailableFunction<V, Result<W, E>, T> func) throws T;

    /**
     * Computes a return value from this result by calling one of the two given callbacks depending on whether this
     * result is a {@link Success} or a {@link Failure}.
     *
     * @param <R> return value type
     * @param <T> type of the throwable that may be thrown from the callbacks
     * @param success success callback
     * @param failure failure callback
     * @return value returned by the called callback
     * @throws T if thrown from the callbacks
     */
    <R, T extends Throwable> R match(FailableFunction<V, R, T> success,
        FailableFunction<E, R, T> failure) throws T;

    /**
     * Successful result containing a value.
     *
     * @param <V2> type of the result value
     * @param <E2> type of the failure (ignored here)
     * @param value result value
     */
    public record Success<V2, E2>(V2 value) implements Result<V2, E2> {
        @Override
        public <R, T extends Throwable> R match(final FailableFunction<V2, R, T> success,
            final FailableFunction<E2, R, T> failure) throws T {
            return success.apply(value);
        }

        @Override
        public <W, T extends Throwable> Result<W, E2> andThen(final FailableFunction<V2, Result<W, E2>, T> func)
                throws T {
            return func.apply(value);
        }
    }

    /**
     * Failure result containing information about what failed.
     *
     * @param <V2> type of the result value (ignored here)
     * @param <E2> type of the failure
     * @param value the failure object
     */
    public record Failure<V2, E2>(E2 value) implements Result<V2, E2> {
        @Override
        public <R, T extends Throwable> R match(final FailableFunction<V2, R, T> success,
                final FailableFunction<E2, R, T> failure) throws T {
            return failure.apply(value);
        }

        @Override
        public <W, T extends Throwable> Result<W, E2> andThen(final FailableFunction<V2, Result<W, E2>, T> func) {
            return coerceResultType();
        }

        /**
         * Casts this failure to a failure with a different result type. This is always safe because there is no result.
         *
         * @param <W> new result type
         * @return this failure with changed type
         */
        public <W> Failure<W, E2> coerceResultType() {
            @SuppressWarnings("unchecked")
            final var cast = (Failure<W, E2>)this;
            return cast;
        }
    }

}

