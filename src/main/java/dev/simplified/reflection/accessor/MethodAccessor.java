package dev.sbs.api.reflection.accessor;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Grants simpler access to method invoking.
 */
public final class MethodAccessor<T> extends ReflectionAccessor<Method> {

    public MethodAccessor(@NotNull Reflection<?> reflection, @NotNull Method method) {
        super(reflection, method);
    }

    /**
     * Gets the method associated with this accessor.
     *
     * @return The method.
     */
    public @NotNull Method getMethod() {
        return this.getHandle();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getModifiers() {
        return this.getHandle().getModifiers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String getName() {
        return this.getHandle().getName();
    }

    /**
     * Invokes a static method with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the method is not static with no arguments.
     */
    public @Nullable T invoke() throws ReflectionException {
        return this.invoke((Object) null);
    }

    /**
     * Invokes an instance or static method with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param obj Instance of the current class object, null if static field.
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the method is passed an invalid object.
     */
    @SuppressWarnings("unchecked")
    public @Nullable T invoke(@Nullable Object obj) throws ReflectionException {
        try {
            return (T) this.getMethod().invoke(obj);
        } catch (Exception exception) {
            throw new ReflectionException(exception, "Unable to invoke method '%s' in '%s' with no arguments.", this.getMethod(), this.getType());
        }
    }

    /**
     * Invokes a static method with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param args The arguments with matching types to pass to the method.
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the method is not static or is passed invalid arguments.
     */
    public @Nullable T invoke(@Nullable Object... args) throws ReflectionException {
        return this.invoke(null, args);
    }

    /**
     * Gets the value of an invoked method with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param obj  Instance of the current class object, null if static field.
     * @param args The arguments with matching types to pass to the method.
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the method is passed invalid arguments.
     */
    @SuppressWarnings("unchecked")
    public @Nullable T invoke(@Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        try {
            return (T) this.getMethod().invoke(obj, args);
        } catch (Exception exception) {
            StringJoiner arguments = new StringJoiner(",");
            Arrays.stream(args)
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .forEach(arguments::add);

            throw new ReflectionException(exception, "Unable to invoke method '%s' in '%s' with arguments [%s].", this.getMethod(), this.getType(), arguments.toString());
        }
    }

}
