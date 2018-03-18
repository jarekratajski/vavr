package io.vavr.control;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;

final class Controls {

    private Controls() {}

    static <T> Iterator<T> singletonIterator(T element) {

        return new Iterator<T>() {

            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public T next() {
                if (!hasNext) {
                    throw new NoSuchElementException();
                }
                hasNext = false;
                return element;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void forEachRemaining(Consumer<? super T> action) {
                Objects.requireNonNull(action, "action is null");
                if (hasNext) {
                    hasNext = false;
                    action.accept(element);
                }
            }
        };
    }

}
