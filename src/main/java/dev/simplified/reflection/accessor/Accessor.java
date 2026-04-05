package dev.simplified.reflection.accessor;

import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.exception.ReflectionException;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Interface for reflection accessor wrappers.
 * <p>
 * Each accessor provides access to the originating {@link Reflection} instance and the
 * underlying {@link AccessibleObject} handle (field, method, or constructor). Common
 * modifier queries and annotation access are provided as {@code default} methods so that
 * implementations can focus purely on their specific operations.
 *
 * @param <T> the {@link AccessibleObject} type wrapped by this accessor
 */
public interface Accessor<T extends AccessibleObject> {

    /** The originating reflection instance. */
    @NotNull Reflection<?> getReflection();

    /** The underlying {@link AccessibleObject} handle. */
    @NotNull T getHandle();

    /**
     * Returns the annotation of the given type present on this handle, or an empty
     * {@link Optional} if the annotation is not present.
     *
     * @param <A> the annotation type
     * @param annotationClass the annotation class to look for
     * @return an {@link Optional} containing the annotation, or empty if absent
     */
    default <A extends Annotation> @NotNull Optional<A> getAnnotation(@NotNull Class<A> annotationClass) {
        return Optional.ofNullable(this.getHandle().getAnnotation(annotationClass));
    }

    /**
     * The Java language modifiers for this handle, as an integer.
     *
     * @see Modifier
     */
    int getModifiers();

    /** The name of this handle. */
    @NotNull String getName();

    /**
     * Gets the class object associated with this accessor.
     *
     * @return The class object.
     * @throws ReflectionException When the class cannot be located.
     */
    default @NotNull Class<?> getType() throws ReflectionException {
        return this.getReflection().getType();
    }

    /**
     * Returns {@code true} if the given annotation type is present on this handle.
     *
     * @param <A> the annotation type
     * @param annotationClass the annotation class to check for
     * @return {@code true} if the annotation is present; {@code false} otherwise
     */
    default <A extends Annotation> boolean hasAnnotation(@NotNull Class<A> annotationClass) {
        return this.getHandle().isAnnotationPresent(annotationClass);
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code public} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code public} modifier; {@code false} otherwise.
     */
    default boolean isPublic() {
        return Modifier.isPublic(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code private} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code private} modifier; {@code false} otherwise.
     */
    default boolean isPrivate() {
        return Modifier.isPrivate(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code protected} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code protected} modifier; {@code false} otherwise.
     */
    default boolean isProtected() {
        return Modifier.isProtected(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code static} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code static} modifier; {@code false} otherwise.
     */
    default boolean isStatic() {
        return Modifier.isStatic(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code final} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code final} modifier; {@code false} otherwise.
     */
    default boolean isFinal() {
        return Modifier.isFinal(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code synchronized} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code synchronized} modifier; {@code false} otherwise.
     */
    default boolean isSynchronized() {
        return Modifier.isSynchronized(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code volatile} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code volatile} modifier; {@code false} otherwise.
     */
    default boolean isVolatile() {
        return Modifier.isVolatile(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code transient} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code transient} modifier; {@code false} otherwise.
     */
    default boolean isTransient() {
        return Modifier.isTransient(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code native} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code native} modifier; {@code false} otherwise.
     */
    default boolean isNative() {
        return Modifier.isNative(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code interface} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code interface} modifier; {@code false} otherwise.
     */
    default boolean isInterface() {
        return Modifier.isInterface(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code abstract} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code abstract} modifier; {@code false} otherwise.
     */
    default boolean isAbstract() {
        return Modifier.isAbstract(this.getModifiers());
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code strictfp} modifier, {@code false} otherwise.
     *
     * @return {@code true} if {@code mod} includes the
     * {@code strictfp} modifier; {@code false} otherwise.
     */
    default boolean isStrict() {
        return Modifier.isStrict(this.getModifiers());
    }

    default void setAccessible(boolean accessible) {
        this.getHandle().setAccessible(accessible);
    }

}
