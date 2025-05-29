package dev.sbs.api.reflection.exception;

import dev.sbs.api.reflection.Reflection;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ReflectionException ReflectionExceptions} are thrown when the {@link Reflection} class is unable<br>
 * to perform a specific action.
 */
public final class ReflectionException extends RuntimeException {

    public ReflectionException(@NotNull Throwable cause) {
        super(cause);
    }

    public ReflectionException(@NotNull String message) {
        super(message);
    }

    public ReflectionException(@NotNull Throwable cause, @NotNull String message) {
        super(message, cause);
    }

    public ReflectionException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

    public ReflectionException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args), cause);
    }

}
