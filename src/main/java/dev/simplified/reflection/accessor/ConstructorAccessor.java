package dev.sbs.api.reflection.accessor;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.string.StringBuilder;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;

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
    public Constructor<?> getConstructor() {
        return this.getHandle();
    }

    /**
     * Creates a new instance of the current {@link #getClazz() class type} with given parameters.
     * <p>
     * Super classes are automatically checked.
     *
     * @param args The arguments with matching types to pass to the constructor.
     * @throws ReflectionException When the constructor is passed invalid arguments.
     */
    public Object newInstance(Object... args) throws ReflectionException {
        try {
            return this.getConstructor().newInstance(args);
        } catch (Exception exception) {
            StringBuilder arguments = new StringBuilder();
            Arrays.stream(args)
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .forEach(arg -> {
                    arguments.appendSeparator(',');
                    arguments.append(arg);
                });

            throw SimplifiedException.of(ReflectionException.class)
                .withMessage("Unable to create new instance of ''{0}'' with arguments [{1}].", this.getClazz(), arguments)
                .withCause(exception)
                .build();
        }
    }

}
