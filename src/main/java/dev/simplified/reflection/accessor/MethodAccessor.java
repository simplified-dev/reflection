package dev.sbs.api.reflection.accessor;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.util.builder.string.StringBuilder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * Grants simpler access to method invoking.
 */
public final class MethodAccessor extends ReflectionAccessor<Method> {

    public MethodAccessor(Reflection reflection, Method method) {
        super(reflection, method);
    }

    /**
     * Gets the method associated with this accessor.
     *
     * @return The method.
     */
    public Method getMethod() {
        return this.getHandle();
    }

    /**
     * Gets the value of an invoked method with matching {@link #getClazz() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param obj  Instance of the current class object, null if static field.
     * @param args The arguments with matching types to pass to the method.
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the method is passed invalid arguments.
     */
    public Object invoke(Object obj, Object... args) throws ReflectionException {
        try {
            return this.getMethod().invoke(obj, args);
        } catch (Exception exception) {
            StringBuilder arguments = new StringBuilder();
            Arrays.stream(args)
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .forEach(arg -> {
                    arguments.appendSeparator(',');
                    arguments.append(arg);
                });

            throw SimplifiedException.builder(ReflectionException.class)
                .setMessage("Unable to invoke method ''{0}'' in ''{1}'' with arguments [{2}].", this.getMethod(), this.getClazz(), arguments.build())
                .setCause(exception)
                .build();
        }
    }

}
