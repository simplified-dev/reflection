package dev.sbs.api.reflection.accessor;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * Grants simpler access to field getting and setting.
 */
@Getter
public final class FieldAccessor<T> implements Accessor<Field> {

    /**
     * Gets the reflection object associated with this accessor.
     */
    private final @NotNull Reflection<?> reflection;

    /**
     * Gets the underlying field handle.
     */
    private final @NotNull Field handle;

    /**
     * Creates a new field accessor.
     *
     * @param reflection the reflection instance that located this field
     * @param field      the underlying field (must already be accessible)
     */
    public FieldAccessor(@NotNull Reflection<?> reflection, @NotNull Field field) {
        this.reflection = reflection;
        this.handle = field;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Accessor<?> other)) return false;
        return new EqualsBuilder().append(this.getType(), other.getType()).append(this.getHandle(), other.getHandle()).build();
    }

    /**
     * Gets the value of a field with matching {@link #getType() class type}.
     * <p>
     * This is the same as calling {@link #get(Object) get(null)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @return The field value with matching type.
     * @throws ReflectionException When the static field cannot be located.
     */
    public @Nullable T get() throws ReflectionException {
        return this.get(null);
    }

    /**
     * Gets the value of a field with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param obj Instance of the current class object, null if static field.
     * @return The field value with matching type.
     * @throws ReflectionException When the field cannot be located.
     */
    @SuppressWarnings("unchecked")
    public @Nullable T get(@Nullable Object obj) throws ReflectionException {
        try {
            return (T) this.getField().get(obj);
        } catch (Exception exception) {
            throw new ReflectionException(exception, "Unable to get field '%s' from '%s'.", this.getField(), obj);
        }
    }

    /**
     * Gets the field associated with this accessor.
     *
     * @return The field.
     */
    public @NotNull Field getField() {
        return this.getHandle();
    }

    /**
     * Returns a {@code Type} object that represents the declared type for the field represented by this {@code Field} object.
     *
     * @return The generic field type.
     */
    public @NotNull Type getGenericType() {
        return this.getHandle().getGenericType();
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
     * Sets the value of a field with matching {@link #getType() class type}.
     * <p>
     * This is the same as calling {@link #set(Object, Object) set(null, value)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param value The new value of the field.
     * @throws ReflectionException When the field cannot be located or the value does match the field type.
     */
    public void set(@Nullable T value) throws ReflectionException {
        this.set(null, value);
    }

    /**
     * Sets the value of a field with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param obj   Instance of the current class object, null if static field.
     * @param value The new value of the field.
     * @throws ReflectionException When the field cannot be located or the value does match the field type.
     */
    public void set(@Nullable Object obj, @Nullable T value) throws ReflectionException {
        try {
            this.getField().set(obj, value);
        } catch (Exception exception) {
            throw new ReflectionException(exception, "Unable to set field '%s' to '%s' in '%s'.", this.getField(), value, obj);
        }
    }

}