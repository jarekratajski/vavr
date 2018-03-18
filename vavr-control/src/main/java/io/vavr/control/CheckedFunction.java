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

import java.util.Objects;

@FunctionalInterface
public interface CheckedFunction<T, R> {

    static <T> CheckedFunction<T, T> identity() {
        return t -> t;
    }

    R apply(T t) throws Exception;

    default <U> CheckedFunction<T, U> andThen(CheckedFunction<? super R, ? extends U> after) {
        Objects.requireNonNull(after);
        return t -> after.apply(apply(t));
    }

    default <U> CheckedFunction<U, R> compose(CheckedFunction<? super U, ? extends T> before) {
        Objects.requireNonNull(before);
        return u -> apply(before.apply(u));
    }

}
