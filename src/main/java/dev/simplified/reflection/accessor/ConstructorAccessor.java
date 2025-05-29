package dev.sbs.api.reflection.accessor;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Grants simpler access to constructor instantialization.
 */
public final class ConstructorAccessor<T> extends ReflectionAccessor<Constructor<T>> {

    public ConstructorAccessor(@NotNull Reflection<T> reflection, @NotNull Constructor<T> constructor) {
        super(reflection, constructor);
    }

    /**
     * Gets the constructor associated with this accessor.
     *
     * @return The constructor.
     */
    public @NotNull Constructor<T> getConstructor() {
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
     * Creates a new instance of the current {@link #getType() class type} with given parameters.
     * <p>
     * Super classes are automatically checked.
     *
     * @param args The arguments with matching types to pass to the constructor.
     * @throws ReflectionException When the constructor is passed invalid arguments.
     */
    public @NotNull T newInstance(@Nullable Object... args) throws ReflectionException {
        try {
            return this.getConstructor().newInstance(args);
        } catch (Exception exception) {
            String arguments = Arrays.stream(args)
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .collect(Collectors.joining(","));

            throw new ReflectionException(exception, "Unable to create new instance of '%s' with arguments [%s].", this.getType(), arguments);
        }
    }

}
