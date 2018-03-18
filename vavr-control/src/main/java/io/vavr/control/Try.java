/* ____  ______________  ________________________  __________
 * \   \/   /      \   \/   /   __/   /      \   \/   /      \
 *  \______/___/\___\______/___/_____/___/\___\______/___/\___\
 *
 * Copyright 2014-2018 Vavr, http://vavr.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vavr.control;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Try control gives us the ability write safe code without focusing on try-catch blocks in the presence of exceptions.
 * <p>
 * The following exceptions are considered to be fatal/non-recoverable:
 * <ul>
 * <li>{@linkplain InterruptedException}</li>
 * <li>{@linkplain LinkageError}</li>
 * <li>{@linkplain ThreadDeath}</li>
 * <li>{@linkplain VirtualMachineError} (e.g. {@linkplain OutOfMemoryError} or {@linkplain StackOverflowError})</li>
 * </ul>
 * <p>
 * <strong>Important note:</strong> Try may re-throw (undeclared) exceptions, e.g. on {@code get()}. From within a
 * dynamic proxy {@link java.lang.reflect.InvocationHandler} this will lead to an
 * {@link java.lang.reflect.UndeclaredThrowableException}. For more information, please read
 * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/reflection/proxy.html">Dynamic Proxy Classes</a>.
 *
 * @param <T> Value type in the case of a success.
 * @author Daniel Dietrich
 */
public abstract class Try<T> implements Iterable<T>, Serializable {

    private static final long serialVersionUID = 1L;

    // sealed
    private Try() {
    }

    /**
     * Calls a {@link Callable} and wraps the result in a {@code Try} instance.
     *
     *
     * @param callable A call that may fail
     * @param <T>      Component type
     * @return {@code Success(supplier.apply())} if no exception occurs, otherwise {@code Failure(throwable)} if an
     * exception occurs calling {@code supplier.apply()}.
     */
    public static <T> Try<T> of(Callable<? extends T> callable) {
        Objects.requireNonNull(callable, "callable is null");
        try {
            return success(callable.call());
        } catch (Throwable t) {
            return failure(t);
        }
    }

    /**
     * Creates a Try of a CheckedRunnable.
     *
     * @param runnable A checked runnable
     * @return {@code Success(null)} if no exception occurs, otherwise {@code Failure(throwable)} if an exception occurs
     * calling {@code runnable.run()}.
     */
    public static Try<Void> run(CheckedRunnable runnable) {
        Objects.requireNonNull(runnable, "runnable is null");
        try {
            runnable.run();
            return success(null); // null represents the absence of an value, i.e. Void
        } catch (Throwable t) {
            return failure(t);
        }
    }

    /**
     * Creates a {@link Success} that contains the given {@code value}. Shortcut for {@code success(value)}.
     *
     * @param value A value.
     * @param <T>   Type of the given {@code value}.
     * @return A new {@code Success}.
     */
    public static <T> Try<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a {@link Failure} that contains the given {@code exception}. Shortcut for {@code failure(exception)}.
     *
     * @param exception An exception.
     * @param <T>       Component type of the {@code Try}.
     * @return A new {@code Failure}.
     */
    public static <T> Try<T> failure(Throwable exception) {
        Objects.requireNonNull(exception, "exception is null");
        if (isFatal(exception)) {
            throw new Error("Fatal error.", exception);
        }
        return new Failure<>(exception);
    }

    /**
     * Returns {@code this} if this is a Failure or this is a Success and the value satisfies the predicate.
     * <p>
     * Returns a new Failure, if this is a Success and the value does not satisfy the Predicate or an exception
     * occurs testing the predicate. The returned Failure wraps a {@link NoSuchElementException} instance.
     *
     * @param predicate A checked predicate
     * @return a {@code Try} instance
     * @throws NullPointerException if {@code predicate} is null
     */
    public Try<T> filter(CheckedPredicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        if (isSuccess()) {
            try {
                final T value = get();
                if (!predicate.test(value)) {
                    return failure(new NoSuchElementException("Predicate does not hold for " + value));
                }
            } catch (Throwable t) {
                return failure(t);
            }
        }
        return this;
    }

    /**
     * FlatMaps the value of a Success or returns a Failure.
     *
     * @param mapper A mapper
     * @param <U>    The new component type
     * @return a {@code Try}
     * @throws NullPointerException if {@code mapper} is null
     */
    @SuppressWarnings("unchecked")
    public <U> Try<U> flatMap(CheckedFunction<? super T, ? extends Try<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (isSuccess()) {
            try {
                return (Try<U>) mapper.apply(get());
            } catch (Throwable t) {
                return failure(t);
            }
        } else {
            return (Failure<U>) this;
        }
    }

    /**
     * Folds either the {@code Failure} or the {@code Success} side of the Try value.
     *
     * @param failureMapper maps the left value if this is a {@code Failure}
     * @param successMapper maps the value if this is a {@code Success}
     * @param <U>           type of the folded value
     * @return A value of type U
     */
    public <U> U fold(Function<? super Throwable, ? extends U> failureMapper, Function<? super T, ? extends U> successMapper) {
        return isSuccess() ? successMapper.apply(get()) : failureMapper.apply(getCause());
    }

    public abstract T get() throws NoSuchElementException;

    public abstract Throwable getCause() throws NoSuchElementException;
    
    /**
     * Consumes the cause if this is a {@link Failure}.
     *
     * <pre>{@code
     * // (does not print anything)
     * Try.success(1).ifFailure(System.out::println);
     *
     * // prints "java.lang.Error"
     * Try.failure(new Error()).ifFailure(System.out::println);
     * }</pre>
     *
     * @param action An exception consumer
     * @return this
     * @throws NullPointerException if {@code action} is null
     */
    public Try<T> ifFailure(Consumer<? super Throwable> action) {
        Objects.requireNonNull(action, "action is null");
        if (isFailure()) {
            action.accept(getCause());
        }
        return this;
    }

    /**
     * Consumes the value if this is a {@link Success}.
     *
     * <pre>{@code
     * // prints "1"
     * Try.success(1).ifSuccess(System.out::println);
     *
     * // (does not print anything)
     * Try.failure(new Error()).ifSuccess(System.out::println);
     * }</pre>
     *
     * @param action A value consumer
     * @return this
     * @throws NullPointerException if {@code action} is null
     */
    public Try<T> ifSuccess(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action is null");
        if (isSuccess()) {
            action.accept(get());
        }
        return this;
    }

    public abstract boolean isFailure();

    public abstract boolean isSuccess();

    /**
     * Runs the given checked function if this is a {@link Success},
     * passing the result of the current expression to it.
     * If this expression is a {@link Failure} then it'll return a new
     * {@link Failure} of type R with the original exception.
     * <p>
     * The main use case is chaining checked functions using method references:
     *
     * <pre>
     * <code>
     * Try.of(() -&gt; 0)
     *    .map(x -&gt; 1 / x); // division by zero
     * </code>
     * </pre>
     *
     * @param <U>    The new component type
     * @param mapper A checked function
     * @return a {@code Try}
     * @throws NullPointerException if {@code mapper} is null
     */
    @SuppressWarnings("unchecked")
    public <U> Try<U> map(CheckedFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (isSuccess()) {
            try {
                return success(mapper.apply(get()));
            } catch (Throwable t) {
                return failure(t);
            }
        } else {
            return (Failure<U>) this;
        }
    }

    /**
     * Maps the cause to a new exception if this is a {@code Failure} or returns this instance if this is a {@code Success}.
     *
     * @param mapper A function that maps the cause of a failure to another exception.
     * @return A new {@code Try} if this is a {@code Failure}, otherwise this.
     */
    public Try<T> mapFailure(CheckedFunction<? super Throwable, ? extends Throwable> mapper) {
        if (isFailure()) {
            try {
                return failure(mapper.apply(getCause()));
            } catch(Throwable t) {
                return failure(t);
            }
        } else {
            return this;
        }
    }

    public T orElse(T other) {
        Objects.requireNonNull(other, "other is null");
        return isSuccess() ? get() : other;
    }

    public T orElseGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return isSuccess() ? get() : supplier.get();
    }

    public T orElseThrow() throws Throwable {
        if (isSuccess()) {
            return get();
        } else {
            throw getCause();
        }
    }

    public <X extends Throwable> T orElseThrow(Function<? super Throwable, ? extends X> exceptionProvider) throws X {
        Objects.requireNonNull(exceptionProvider, "exceptionProvider is null");
        if (isSuccess()) {
            return get();
        } else {
            throw exceptionProvider.apply(getCause());
        }
    }

    @SuppressWarnings("unchecked")
    public <U> Try<U> transform(
            CheckedFunction<? super T, ? extends Try<? extends U>> successMapper,
            CheckedFunction<? super Throwable, ? extends Try<? extends U>> failureMapper
    ) {
        if (isSuccess()) {
            return flatMap(successMapper);
        } else {
            try {
                return (Try<U>) failureMapper.apply(getCause());
            } catch(Throwable t) {
                return failure(t);
            }
        }
    }
    
    @Override
    public Iterator<T> iterator() {
        return isSuccess()
            ? Controls.singletonIterator(((Success<T>) this).get())
            : Collections.emptyIterator();
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(
                iterator(),
                isSuccess() ? 1L : 0L,
                Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED
        );
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();

    private static boolean isFatal(Throwable t) {
        return t instanceof InterruptedException
                || t instanceof LinkageError
                || t instanceof ThreadDeath
                || t instanceof VirtualMachineError;
    }

    private static final class Success<T> extends Try<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        private final T value;

        private Success(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Throwable getCause() throws UnsupportedOperationException {
            throw new UnsupportedOperationException("Success.getCause()");
        }

        @Override
        public boolean isFailure() {
            return false;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj == this) || (
                    obj instanceof Success && Objects.equals(value, ((Success<?>) obj).value)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "Success(" + value + ")";
        }

    }

    private static final class Failure<T> extends Try<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        private final Throwable cause;

        private Failure(Throwable cause) throws Error {
            this.cause = cause;
        }

        @Override
        public T get() throws NoSuchElementException {
            throw new NoSuchElementException("Failure.get()");
        }

        @Override
        public Throwable getCause() {
            return cause;
        }

        @Override
        public boolean isFailure() {
            return true;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
        
        @Override
        public boolean equals(Object obj) {
            return (obj == this) || (
                    obj instanceof Failure && Objects.equals(cause, ((Failure<?>) obj).cause)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(cause);
        }

        @Override
        public String toString() {
            return "Failure(" + cause + ")";
        }

    }

}
