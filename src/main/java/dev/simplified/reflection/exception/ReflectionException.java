package dev.sbs.api.reflection.exception;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.mutable.triple.Triple;

/**
 * {@link ReflectionException ReflectionExceptions} are thrown when the {@link Reflection} class is unable<br>
 * to perform a specific action.
 */
public final class ReflectionException extends SimplifiedException {

    private ReflectionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
