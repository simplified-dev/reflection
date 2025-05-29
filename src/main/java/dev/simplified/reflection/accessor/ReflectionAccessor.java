package dev.sbs.api.reflection.accessor;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Modifier;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
abstract class ReflectionAccessor<T extends AccessibleObject> {

    /**
     * Gets the reflection object associated with this accessor.
     */
    private final @NotNull Reflection<?> reflection;

    /**
     * Gets the instance of type {@link T}.
     */
    private final @NotNull T handle;

    @Override
    public final boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof ReflectionAccessor<?> other)) return false;
        return new EqualsBuilder().append(this.getType(), other.getType()).append(this.getHandle(), other.getHandle()).build();
    }

    public final <A extends Annotation> @NotNull Optional<A> getAnnotation(@NotNull Class<A> annotationClass) {
        return Optional.ofNullable(this.getHandle().isAnnotationPresent(annotationClass) ? this.getHandle().getAnnotation(annotationClass) : null);
    }

    /**
     * Returns the Java language modifiers for the handle represented
     * by this {@code T} object, as an integer.
     *
     * @see Modifier
     */
    public abstract int getModifiers();

    /**
     * Gets the class object associated with this accessor.
     * <p>
     * This object is cached after the first call.
     *
     * @return The class object.
     * @throws ReflectionException When the class cannot be located.
     */
    public final @NotNull Class<?> getType() throws ReflectionException {
        return this.getReflection().getType();
    }

    public final <A extends Annotation> boolean hasAnnotation(@NotNull Class<A> annotationClass) {
        return this.getHandle().isAnnotationPresent(annotationClass);
    }

    @Override
    public final int hashCode() {
        return new HashCodeBuilder().append(this.getType()).append(this.getHandle()).build();
    }

}
