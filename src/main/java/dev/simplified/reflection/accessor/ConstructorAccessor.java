package dev.sbs.api.reflection.accessor;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StringUtil;

import java.lang.reflect.Constructor;
import java.util.Objects;

/**
 * Grants simpler access to constructor instantialization.
 */
public final class ConstructorAccessor extends ReflectionAccessor<Constructor<?>> {

	public ConstructorAccessor(Reflection reflection, Constructor<?> constructor) {
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
			String arguments = StringUtil.join(
					Concurrent.newList(args)
							.stream()
							.map(Objects::toString)
							.collect(Concurrent.toList()),
			',');
			throw new ReflectionException(FormatUtil.format("Unable to create new instance of ''{0}'' with arguments [{1}].", this.getClazz(), arguments), exception);
		}
	}

}
