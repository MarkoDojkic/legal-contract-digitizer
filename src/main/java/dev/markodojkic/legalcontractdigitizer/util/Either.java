package dev.markodojkic.legalcontractdigitizer.util;

/**
 * A generic container object that can hold either a left value or a right value, but not both.
 * This is useful for representing a result that can be one of two possible types.
 *
 * @param <L> the type of the left value
 * @param <R> the type of the right value
 */

public record Either<L, R>(L left, R right) {
    public static <L, R> Either<L, R> left(L value) {
        return new Either<>(value, null);
    }

    public static <L, R> Either<L, R> right(R value) {
        return new Either<>(null, value);
    }
}