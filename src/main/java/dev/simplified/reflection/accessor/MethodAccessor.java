package dev.simplified.reflection.accessor;

import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.exception.ReflectionException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Grants simpler access to method invoking.
 */
@Getter
public final class MethodAccessor<T> implements Accessor<Method> {

    /**
     * Gets the reflection object associated with this accessor.
     */
    private final @NotNull Reflection<?> reflection;

    /**
     * Gets the underlying method handle.
     */
    private final @NotNull Method handle;

    /**
     * Creates a new method accessor.
     *
     * @param reflection the reflection instance that located this method
     * @param method the underlying method (must already be accessible)
     */
    public MethodAccessor(@NotNull Reflection<?> reflection, @NotNull Method method) {
        this.reflection = reflection;
        this.handle = method;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Accessor<?> other)) return false;
        return Objects.equals(this.getType(), other.getType()) && Objects.equals(this.getHandle(), other.getHandle());
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
     * Returns the parameter types declared by this method.
     *
     * @return an array of parameter types
     */
    public @NotNull Class<?>[] getParameterTypes() {
        return this.getHandle().getParameterTypes();
    }

    /**
     * Returns the return type of this method.
     *
     * @return the return type
     */
    public @NotNull Class<?> getReturnType() {
        return this.getHandle().getReturnType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String getName() {
        return this.getHandle().getName();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getType(), this.getHandle());
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
            throw new ReflectionException(exception, "Unable to invoke method '%s' in '%s' with no arguments", this.getMethod(), this.getType());
        }
    }

    /**
     * Gets the value of an invoked method with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param obj Instance of the current class object, null if static field.
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

            throw new ReflectionException(exception, "Unable to invoke method '%s' in '%s' with arguments [%s]", this.getMethod(), this.getType(), arguments.toString());
        }
    }

}