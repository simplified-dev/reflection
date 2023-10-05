package dev.sbs.api.reflection.accessor;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.util.SimplifiedException;

import java.lang.reflect.Field;

/**
 * Grants simpler access to field getting and setting.
 */
public final class FieldAccessor extends ReflectionAccessor<Field> {

    public FieldAccessor(Reflection<?> reflection, Field field) {
        super(reflection, field);
    }

    /**
     * Gets the field associated with this accessor.
     *
     * @return The field.
     */
    public Field getField() {
        return this.getHandle();
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
    public <T> T get() throws ReflectionException {
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
    public <T> T get(Object obj) throws ReflectionException {
        try {
            return (T) this.getField().get(obj);
        } catch (Exception exception) {
            throw SimplifiedException.of(ReflectionException.class)
                .withMessage("Unable to get field '%s' from '%s'.", this.getField(), obj)
                .withCause(exception)
                .build();
        }
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
    public void set(Object value) throws ReflectionException {
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
    public void set(Object obj, Object value) throws ReflectionException {
        try {
            this.getField().set(obj, value);
        } catch (Exception exception) {
            throw SimplifiedException.of(ReflectionException.class)
                .withMessage("Unable to set field '%s' to '%s' in '%s'.", this.getField(), value, obj)
                .withCause(exception)
                .build();
        }
    }

}
