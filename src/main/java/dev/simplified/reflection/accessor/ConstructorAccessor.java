package dev.sbs.api.reflection.accessor;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.util.SimplifiedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Grants simpler access to constructor instantialization.
 */
public final class ConstructorAccessor extends ReflectionAccessor<Constructor<?>> {

    public ConstructorAccessor(Reflection<?> reflection, Constructor<?> constructor) {
        super(reflection, constructor);
    }

    /**
     * Gets the constructor associated with this accessor.
     *
     * @return The constructor.
     */
    public @NotNull Constructor<?> getConstructor() {
        return this.getHandle();
    }

    /**
     * Creates a new instance of the current {@link #getType() class type} with given parameters.
     * <p>
     * Super classes are automatically checked.
     *
     * @param args The arguments with matching types to pass to the constructor.
     * @throws ReflectionException When the constructor is passed invalid arguments.
     */
    public @NotNull Object newInstance(@Nullable Object... args) throws ReflectionException {
        try {
            return this.getConstructor().newInstance(args);
        } catch (Exception exception) {
            String arguments = Arrays.stream(args)
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .collect(Collectors.joining(","));

            throw SimplifiedException.of(ReflectionException.class)
                .withMessage("Unable to create new instance of '%s' with arguments [%s].", this.getType(), arguments)
                .withCause(exception)
                .build();
        }
    }

}
