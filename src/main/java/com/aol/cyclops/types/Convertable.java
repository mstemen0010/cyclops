
package com.aol.cyclops.types;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import cyclops.async.Future;

import cyclops.function.Fn0;
import lombok.Value;

/**
 * Interface that represents a single value that can be converted into a List, Stream or Optional
 * 
 * @author johnmcclean
 *
 * @param <T> Type of this convertable
 */
public interface Convertable<T> extends Iterable<T>, Fn0<T>, Visitable<T> {

    /**
     * Collect the contents of the monad wrapped by this AnyM into supplied collector
     * A mutable reduction operation equivalent to Stream#collect
     *
     * <pre>
     * {@code
     *      AnyM<Integer> monad1 = AnyM.fromStream(Stream.of(1,2,3));
     *      AnyM<Integer> monad2 = AnyM.fromOptional(Optional.of(1));
     *
     *      List<Integer> list1 = monad1.collect(Collectors.toList());
     *      List<Integer> list2 = monad2.collect(Collectors.toList());
     *
     * }
     * </pre>
     *
     *
     * @param collector JDK collector to perform mutable reduction
     * @return Reduced value
     */
    default <R, A> R collect(Collector<? super T, A, R> collector){
        return this.toStream().collect(collector);
    }
    /* Present is executed and it's return value returned if the value is both present, otherwise absent is called and its return value returned
     * 
     * (non-Javadoc)
     * @see com.aol.cyclops.types.Visitable#visit(java.util.function.Function, java.util.function.Supplier)
     */
    @Override
    default <R> R visit(final Function<? super T, ? extends R> present, final Supplier<? extends R> absent) {

        if (isPresent()) {
            try {
                final T value = get();
                if (value != null)
                    return present.apply(value);
                return absent.get();
            } catch (final NoSuchElementException e) {
                return absent.get();
            }
        }
        return absent.get();
    }

    /**
     * @return True if value exists and is non-null
     */
    default boolean isPresent() {
        try {
            final T value = get();
            if (value != null)
                return true;
            return false;
        } catch (final NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Construct a Convertable from a Supplier
     * 
     * @param supplier That returns the convertable value
     * @return Convertable
     */
    public static <T> Convertable<T> fromSupplier(final Supplier<T> supplier) {
        return new SupplierToConvertable<>(
                                           supplier);
    }

    @Value
    public static class SupplierToConvertable<T> implements Convertable<T> {
        private final Supplier<T> delegate;

        @Override
        public T get() {
            return delegate.get();
        }
    }

    /**
     * @return Contained value, maybe null
     */
    @Override
    public T get();

    /**
     * An equivalent operation to {@link java.util.Optional#orElseGet(Supplier)} 
     * Returns a value generated by the provided Supplier if this Convertable is empty or
     * contains a null value.
     * 
     * @param value Supplier to generate value if this convertable is empty
     * @return Value contained in this convertable or the value returned by the supplier if this convertable is empty
     */
    default T orElseGet(final Supplier<? extends T> value) {
        return toOptional().orElseGet(value);

    }

    /**
     * @return Optional that wraps contained value, Optional.empty if value is null
     */
    default Optional<T> toOptional() {

        return visit(p -> Optional.ofNullable(p), () -> Optional.empty());
    }

    /**
     * @return Stream containing value returned by get(), Empty Stream if null
     */
    default Stream<T> toStream() {
        return Stream.of(toOptional())
                     .filter(Optional::isPresent)
                     .map(Optional::get);
    }


    /**Get the contained value or else the provided alternative
     * 
     * @param value
     * @return the value of this convertable (if not empty) or else the specified value
     */
    default T orElse(final T value) {
        return toOptional().orElse(value);
    }

    /**
     * Get the contained value or throw an exception if null
     * 
     * @param ex Supplier that returns an exception if this value is empty
     * @return Value of this value if present
     * @throws X Exception type returned by provided Supplier
     */
    default <X extends Throwable> T orElseThrow(final Supplier<? extends X> ex) throws X {
        return toOptional().orElseThrow(ex);
    }



    /* An Iterator over the list returned from toList()
     * 
     *  (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    @Override
    default Iterator<T> iterator() {
        final Optional<T> opt = toOptional();
        if (opt.isPresent())
            return Arrays.asList(get()).iterator();
        return Arrays.<T>asList().iterator();

    }

    /**
     * @return This convertable converted to a Future
     */
    default Future<T> toFutureW() {
        return Future.of(toCompletableFuture());
    }

    /**
     * @return This convertable converted to a Future asyncrhonously
     */
    default Future<T> toFutureWAsync() {
        return Future.of(toCompletableFutureAsync());
    }

    /**
     * This convertable converted to a Future asyncrhonously using the supplied Executor
     * 
     * @param ex Executor to execute the conversion on
     * @return  This convertable converted to a Future asyncrhonously
     */
    default Future<T> toFutureWAsync(final Executor ex) {
        return Future.of(toCompletableFutureAsync(ex));
    }

    /**
     * @return A CompletableFuture, populated immediately by a call to get
     */
    default CompletableFuture<T> toCompletableFuture() {
        try {
            return CompletableFuture.completedFuture(get());
        } catch (final Throwable t) {
            final CompletableFuture<T> res = new CompletableFuture<>();
            res.completeExceptionally(t);
            return res;
        }
    }

    /**
     * @return A CompletableFuture populated asynchronously on the Common ForkJoinPool by calling get
     */
    default CompletableFuture<T> toCompletableFutureAsync() {
        return CompletableFuture.supplyAsync(this);
    }

    /**
     * @param exec Executor to asyncrhonously populate the CompletableFuture
     * @return  A CompletableFuture populated asynchronously on the supplied Executor by calling get
     */
    default CompletableFuture<T> toCompletableFutureAsync(final Executor exec) {
        return CompletableFuture.supplyAsync(this, exec);
    }
}
