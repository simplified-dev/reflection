package dev.sbs.api.reflection.accessor;


import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.reflection.Reflection;

abstract class ReflectionAccessor<T> {

	private final Reflection reflection;

	public ReflectionAccessor(Reflection reflection) {
		this.reflection = reflection;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public final boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof ReflectionAccessor))
			return false;
		else {
			ReflectionAccessor other = (ReflectionAccessor)obj;
			return this.getClazz().equals(other.getClazz()) && this.getHandle().equals(other.getHandle());
		}
	}

	/**
	 * Gets the class object associated with this accessor.
	 * <p>
	 * This object is cached after the first call.
	 *
	 * @return The class object.
	 * @throws ReflectionException When the class cannot be located.
	 */
	public final Class<?> getClazz() throws ReflectionException {
		return this.getReflection().getClazz();
	}

	protected abstract T getHandle();

	/**
	 * Gets the reflection object associated with this accessor.
	 */
	public final Reflection getReflection() {
		return this.reflection;
	}

	@Override
	public final int hashCode() {
		return this.getClazz().hashCode() + this.getHandle().hashCode();
	}

}