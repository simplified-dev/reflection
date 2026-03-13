package dev.sbs.api.reflection.accessor;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Grants simpler access to constructor instantiation.
 */
@Getter
public final class ConstructorAccessor<T> implements Accessor<Constructor<T>> {

    /**
     * Gets the reflection object associated with this accessor.
     */
    private final @NotNull Reflection<T> reflection;

    /**
     * Gets the underlying constructor handle.
     */
    private final @NotNull Constructor<T> handle;

    /**
     * Creates a new constructor accessor.
     *
     * @param reflection  the reflection instance that located this constructor
     * @param constructor the underlying constructor (must already be accessible)
     */
    public ConstructorAccessor(@NotNull Reflection<T> reflection, @NotNull Constructor<T> constructor) {
        this.reflection = reflection;
        this.handle = constructor;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Accessor<?> other)) return false;
        return new EqualsBuilder().append(this.getType(), other.getType()).append(this.getHandle(), other.getHandle()).build();
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

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(this.getType()).append(this.getHandle()).build();
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
            StringJoiner arguments = new StringJoiner(",");
            Arrays.stream(args)
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .forEach(arguments::add);

            throw new ReflectionException(exception, "Unable to create new instance of '%s' with arguments [%s].", this.getType(), arguments);
        }
    }

}