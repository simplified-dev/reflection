package dev.sbs.api.reflection.exception;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.concurrent.ConcurrentMap;

/**
 * {@link ReflectionException ReflectionExceptions} are thrown when the {@link Reflection} class is unable<br>
 * to perform a specific action.
 */
public final class ReflectionException extends SimplifiedException {

    private ReflectionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentMap<String, Object> fields) {
        super(message, cause, enableSuppression, writableStackTrace, fields);
    }

}
