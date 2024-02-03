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
public final class MethodAccessor extends ReflectionAccessor<Method> {

    public MethodAccessor(Reflection<?> reflection, Method method) {
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
     * Gets the value of an invoked method with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param obj  Instance of the current class object, null if static field.
     * @param args The arguments with matching types to pass to the method.
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the method is passed invalid arguments.
     */
    public @Nullable Object invoke(Object obj, Object... args) throws ReflectionException {
        try {
            return this.getMethod().invoke(obj, args);
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
