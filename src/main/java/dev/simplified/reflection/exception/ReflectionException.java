package dev.sbs.api.reflection.exception;

import dev.sbs.api.reflection.Reflection;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the {@link Reflection} layer is unable to locate or invoke
 * classes, fields, methods, or constructors.
 */
public final class ReflectionException extends RuntimeException {

    /**
     * Constructs a new {@code ReflectionException} with the specified cause.
     *
     * @param cause the underlying throwable that caused this exception
     */
    public ReflectionException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code ReflectionException} with the specified detail message.
     *
     * @param message the detail message
     */
    public ReflectionException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ReflectionException} with the specified cause and detail message.
     *
     * @param cause the underlying throwable that caused this exception
     * @param message the detail message
     */
    public ReflectionException(@NotNull Throwable cause, @NotNull String message) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code ReflectionException} with a formatted detail message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public ReflectionException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

    /**
     * Constructs a new {@code ReflectionException} with the specified cause and a formatted detail message.
     *
     * @param cause the underlying throwable that caused this exception
     * @param message the format string
     * @param args the format arguments
     */
    public ReflectionException(@NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args), cause);
    }

}
