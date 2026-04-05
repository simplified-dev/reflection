package dev.sbs.api.reflection;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.api.reflection.accessor.ConstructorAccessor;
import dev.sbs.api.reflection.accessor.FieldAccessor;
import dev.sbs.api.reflection.accessor.MethodAccessor;
import dev.sbs.api.reflection.accessor.ResourceAccessor;
import dev.sbs.api.reflection.builder.BuildFlag;
import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.tuple.pair.Pair;
import dev.sbs.api.util.ArrayUtil;
import dev.sbs.api.util.ClassUtil;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.PrimitiveUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.SystemUtil;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A cached reflection wrapper providing access to fields, methods, constructors, and
 * generic type parameters of a given class.
 *
 * <p>
 * All reflective lookups are backed by per-class caches that eliminate repeated JNI calls
 * to {@code getDeclaredFields()}, {@code getDeclaredMethods()}, and
 * {@code getDeclaredConstructors()}. Each cached member is made accessible once on first
 * load. Higher-level results - such as the full field set across an inheritance hierarchy,
 * {@link BuildFlag @BuildFlag} metadata per builder class, and resolved generic superclass
 * types - are also cached.
 *
 * <p>
 * By default, lookups walk the superclass chain ({@link #isProcessingSuperclass()} is
 * {@code true}). Set it to {@code false} to restrict lookups to the declared class only.
 *
 * @param <R> the reflected class type
 */
@Getter
public class Reflection<R> {

    /** Cache of fully-qualified class path to resolved {@link Class} object. */
    private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = Concurrent.newMap();
    /** Cache of declared fields per class, made accessible on first load. */
    private static final ConcurrentMap<Class<?>, Field[]> DECLARED_FIELDS_CACHE = Concurrent.newMap();
    /** Cache of declared methods per class, made accessible on first load. */
    private static final ConcurrentMap<Class<?>, Method[]> DECLARED_METHODS_CACHE = Concurrent.newMap();
    /** Cache of declared constructors per class, made accessible on first load. */
    private static final ConcurrentMap<Class<?>, Constructor<?>[]> DECLARED_CONSTRUCTORS_CACHE = Concurrent.newMap();
    /** Cache of flattened field accessors across the full superclass chain, per class. */
    private static final ConcurrentMap<Class<?>, ConcurrentSet<FieldAccessor<?>>> ALL_FIELDS_CACHE = Concurrent.newMap();
    /** Cache of flattened method accessors across the full superclass chain, per class. */
    private static final ConcurrentMap<Class<?>, ConcurrentSet<MethodAccessor<?>>> ALL_METHODS_CACHE = Concurrent.newMap();
    /** Cache of {@link BuildFlag @BuildFlag} field metadata per builder class. */
    private static final ConcurrentMap<Class<?>, ConcurrentList<BuildFlagEntry>> BUILD_FLAG_CACHE = Concurrent.newMap();
    /** Cache of resolved generic superclass types, keyed by (class, type argument index). */
    private static final ConcurrentMap<Pair<Class<?>, Integer>, Class<?>> SUPER_CLASS_CACHE = Concurrent.newMap();
    private final @NotNull Class<R> type;
    @Setter private boolean processingSuperclass = true;

    /** Pairs a {@link FieldAccessor} with its {@link BuildFlag} annotation for cached validation. */
    private record BuildFlagEntry(@NotNull FieldAccessor<?> fieldAccessor, @NotNull BuildFlag flag) { }

    /**
     * Returns the declared fields of the given class from cache, loading and making
     * them accessible on first access.
     *
     * @param type the class to retrieve declared fields for
     * @return the cached array of declared fields
     */
    private static Field[] getDeclaredFieldsCached(@NotNull Class<?> type) {
        return DECLARED_FIELDS_CACHE.computeIfAbsent(type, cls -> {
            Field[] fields = cls.getDeclaredFields();

            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                } catch (InaccessibleObjectException ignored) { }
            }

            return fields;
        });
    }

    /**
     * Returns the declared methods of the given class from cache, loading and making
     * them accessible on first access.
     *
     * @param type the class to retrieve declared methods for
     * @return the cached array of declared methods
     */
    private static Method[] getDeclaredMethodsCached(@NotNull Class<?> type) {
        return DECLARED_METHODS_CACHE.computeIfAbsent(type, cls -> {
            Method[] methods = cls.getDeclaredMethods();

            for (Method m : methods) {
                try {
                    m.setAccessible(true);
                } catch (InaccessibleObjectException ignored) { }
            }

            return methods;
        });
    }

    /**
     * Returns the declared constructors of the given class from cache, loading and making
     * them accessible on first access.
     *
     * @param type the class to retrieve declared constructors for
     * @return the cached array of declared constructors
     */
    private static Constructor<?>[] getDeclaredConstructorsCached(@NotNull Class<?> type) {
        return DECLARED_CONSTRUCTORS_CACHE.computeIfAbsent(type, cls -> {
            Constructor<?>[] constructors = cls.getDeclaredConstructors();

            for (Constructor<?> c : constructors) {
                try {
                    c.setAccessible(true);
                } catch (InaccessibleObjectException ignored) { }
            }

            return constructors;
        });
    }

    /**
     * Creates a new reflection instance from a package name and simple class name.
     * The class is resolved via {@link Class#forName(String)} and cached for subsequent
     * lookups.
     *
     * @param packageName the package the class belongs to
     * @param simplifiedName the simple class name to reflect
     * @throws ReflectionException if the class cannot be located
     */
    public Reflection(@NotNull String packageName, @NotNull String simplifiedName) {
        this(String.format("%s.%s", packageName, simplifiedName));
    }

    /**
     * Creates a new reflection instance from a fully-qualified class path. The class
     * is resolved via {@link Class#forName(String)} and cached in {@code CLASS_CACHE}
     * for subsequent lookups.
     *
     * @param classPath the fully-qualified class path to reflect
     * @throws ReflectionException if the class cannot be located
     */
    @SuppressWarnings("unchecked")
    public Reflection(@NotNull String classPath) {
        if (CLASS_CACHE.containsKey(classPath))
            this.type = (Class<R>) CLASS_CACHE.get(classPath);
        else {
            try {
                this.type = (Class<R>) Class.forName(classPath);
                CLASS_CACHE.put(classPath, this.type);
            } catch (Exception cnfex) {
                throw new ReflectionException(cnfex, "Unable to locate class '%s'", classPath);
            }
        }
    }

    /**
     * Creates a new reflection instance from a class reference. Primitive types are
     * automatically wrapped to their boxed equivalents via {@link PrimitiveUtil#wrap(Class)}.
     *
     * @param type the class to reflect
     */
    public Reflection(@NotNull Class<R> type) {
        this.type = PrimitiveUtil.wrap(type);
    }

    /**
     * Returns the first constructor whose parameter types match the given types.
     * Constructors are loaded from a per-class cache. Parameter types are
     * automatically checked against assignable types and primitives.
     *
     * @param paramTypes the parameter types to match against
     * @return a constructor accessor wrapping the matching constructor
     * @throws ReflectionException if no matching constructor is found in this class
     */
    @SuppressWarnings("unchecked")
    public final @NotNull ConstructorAccessor<R> getConstructor(Class<?>... paramTypes) throws ReflectionException {
        Class<?>[] types = toPrimitiveTypeArray(paramTypes);

        for (Constructor<?> constructor : getDeclaredConstructorsCached(this.getType())) {
            Class<?>[] constructorTypes = toPrimitiveTypeArray(constructor.getParameterTypes());

            if (isEqualsTypeArray(constructorTypes, types))
                return new ConstructorAccessor<>(this, (Constructor<R>) constructor);
        }

        throw new ReflectionException("The constructor matching '%s' was not found in '%s'", Arrays.asList(types), this.getType().getName());
    }

    /**
     * Returns the first field whose type matches the given class. Fields are loaded
     * from a per-class cache. The type is automatically checked against assignable
     * types and primitives. Superclasses are walked when
     * {@link #isProcessingSuperclass()} is {@code true}.
     *
     * @param type the field type to match against
     * @return a field accessor wrapping the matching field
     * @throws ReflectionException if no matching field is found in this class or its superclasses
     */
    public final <T> @NotNull FieldAccessor<T> getField(@NotNull Class<T> type) throws ReflectionException {
        Class<?> utype = (type.isPrimitive() ? PrimitiveUtil.wrap(type) : PrimitiveUtil.unwrap(type));

        for (Field field : getDeclaredFieldsCached(this.getType())) {
            if (field.getType().equals(type) || type.isAssignableFrom(field.getType()) || field.getType().equals(utype) || utype.isAssignableFrom(field.getType()))
                return new FieldAccessor<>(this, field);
        }

        if (this.isProcessingSuperclass() && this.getType().getSuperclass() != null)
            return this.getSuperReflection().getField(type);

        throw new ReflectionException("The field with type '%s' was not found in '%s'", type, this.getType().getName());
    }

    /**
     * Returns the first field with a case-sensitive name match. Delegates to
     * {@link #getField(String, boolean) getField(name, true)}.
     *
     * @param name the field name to match
     * @return a field accessor wrapping the matching field
     * @throws ReflectionException if no matching field is found in this class or its superclasses
     */
    public final <T> @NotNull FieldAccessor<T> getField(@NotNull String name) throws ReflectionException {
        return this.getField(name, true);
    }

    /**
     * Returns the first field with a matching name. Fields are loaded from a per-class
     * cache. Superclasses are walked when {@link #isProcessingSuperclass()} is {@code true}.
     *
     * @param name the field name to match
     * @param isCaseSensitive whether to match the name case-sensitively
     * @return a field accessor wrapping the matching field
     * @throws ReflectionException if no matching field is found in this class or its superclasses
     */
    public final <T> @NotNull FieldAccessor<T> getField(@NotNull String name, boolean isCaseSensitive) throws ReflectionException {
        for (Field field : getDeclaredFieldsCached(this.getType())) {
            if (isCaseSensitive ? field.getName().equals(name) : field.getName().equalsIgnoreCase(name))
                return new FieldAccessor<>(this, field);
        }

        if (this.isProcessingSuperclass() && this.getType().getSuperclass() != null)
            return this.getSuperReflection().getField(name);

        throw new ReflectionException("The field '%s' was not found in '%s'", name, this.getType().getName());
    }

    /**
     * Returns all fields of the reflected class. When {@link #isProcessingSuperclass()}
     * is {@code true} (the default), the result includes fields from the entire
     * superclass chain and is cached per class in an unmodifiable set. When
     * {@code false}, returns only the declared fields of this class without caching.
     *
     * <p>
     * Individual field arrays per class are loaded from the declared-fields cache,
     * avoiding repeated JNI calls.
     *
     * @return all field accessors for this class (and its superclasses if enabled)
     * @throws ReflectionException if field introspection fails
     */
    @SuppressWarnings("unchecked")
    public final @NotNull ConcurrentSet<FieldAccessor<?>> getFields() throws ReflectionException {
        if (!this.isProcessingSuperclass()) {
            ConcurrentSet<FieldAccessor<?>> fieldAccessors = Concurrent.newSet();

            for (Field field : getDeclaredFieldsCached(this.getType()))
                fieldAccessors.add(new FieldAccessor<>(this, field));

            return fieldAccessors;
        }

        return ALL_FIELDS_CACHE.computeIfAbsent(this.getType(), cls -> {
            ConcurrentSet<FieldAccessor<?>> fieldAccessors = Concurrent.newSet();
            Class<?> current = cls;

            while (current != null) {
                Reflection<?> ref = (current == cls) ? this : new Reflection<>((Class<Object>) current);

                for (Field field : getDeclaredFieldsCached(current))
                    fieldAccessors.add(new FieldAccessor<>(ref, field));

                current = current.getSuperclass();
            }

            return fieldAccessors.toUnmodifiableSet();
        });
    }

    /**
     * Returns the URLs in the class path specified by the {@code java.class.path}
     * {@linkplain System#getProperty(String) system property}.
     *
     * @return an unmodifiable list of class path URLs
     */
    public static @NotNull ConcurrentList<URL> getJavaClassPath() {
        ConcurrentList<URL> urls = Concurrent.newList();

        for (String entry : Objects.requireNonNull(StringUtil.split(SystemUtil.JAVA_CLASS_PATH, SystemUtil.PATH_SEPARATOR))) {
            try {
                urls.add(Path.of(entry).toAbsolutePath().toUri().toURL());
            } catch (Exception ignore) { }
        }

        return urls.toUnmodifiableList();
    }

    /**
     * Returns the first method whose return type and parameter types match. Methods
     * are loaded from a per-class cache. Types are automatically checked against
     * assignable types and primitives. Superclasses are walked when
     * {@link #isProcessingSuperclass()} is {@code true}.
     *
     * @param type the return type to match against
     * @param paramTypes the parameter types to match against
     * @return a method accessor wrapping the matching method
     * @throws ReflectionException if no matching method is found in this class or its superclasses
     */
    public final <T> @NotNull MethodAccessor<T> getMethod(@NotNull Class<T> type, @Nullable Class<?>... paramTypes) throws ReflectionException {
        Class<?> utype = (type.isPrimitive() ? PrimitiveUtil.wrap(type) : PrimitiveUtil.unwrap(type));
        Class<?>[] types = toPrimitiveTypeArray(paramTypes);

        for (Method method : getDeclaredMethodsCached(this.getType())) {
            Class<?>[] methodTypes = toPrimitiveTypeArray(method.getParameterTypes());
            Class<?> returnType = method.getReturnType();

            if ((returnType.equals(type) || type.isAssignableFrom(returnType) || returnType.equals(utype) || utype.isAssignableFrom(returnType)) && isEqualsTypeArray(methodTypes, types))
                return new MethodAccessor<>(this, method);
        }

        if (this.isProcessingSuperclass() && this.getType().getSuperclass() != null)
            return this.getSuperReflection().getMethod(type, paramTypes);

        throw new ReflectionException("The method with return type '%s' was not found in '%s' with parameters '%s'", type, this.getType().getName(), Arrays.asList(types));
    }

    /**
     * Returns the first method with a case-sensitive name match and matching parameter
     * types. Delegates to {@link #getMethod(String, boolean, Class...) getMethod(name, true, paramTypes)}.
     *
     * @param name the method name to match
     * @param paramTypes the parameter types to match against
     * @return a method accessor wrapping the matching method
     * @throws ReflectionException if no matching method is found in this class or its superclasses
     */
    public final @NotNull MethodAccessor<?> getMethod(@NotNull String name, @Nullable Class<?>... paramTypes) throws ReflectionException {
        return this.getMethod(name, true, paramTypes);
    }

    /**
     * Returns the first method with a matching name and parameter types. Methods are
     * loaded from a per-class cache. Parameter types are automatically checked against
     * assignable types and primitives. Superclasses are walked when
     * {@link #isProcessingSuperclass()} is {@code true}.
     *
     * @param name the method name to match
     * @param isCaseSensitive whether to match the name case-sensitively
     * @param paramTypes the parameter types to match against
     * @return a method accessor wrapping the matching method
     * @throws ReflectionException if no matching method is found in this class or its superclasses
     */
    public final @NotNull MethodAccessor<?> getMethod(@NotNull String name, boolean isCaseSensitive, @Nullable Class<?>... paramTypes) throws ReflectionException {
        Class<?>[] types = toPrimitiveTypeArray(paramTypes);

        for (Method method : getDeclaredMethodsCached(this.getType())) {
            Class<?>[] methodTypes = toPrimitiveTypeArray(method.getParameterTypes());

            if ((isCaseSensitive ? method.getName().equals(name) : method.getName().equalsIgnoreCase(name)) && isEqualsTypeArray(methodTypes, types))
                return new MethodAccessor<>(this, method);
        }

        if (this.isProcessingSuperclass() && this.getType().getSuperclass() != null)
            return this.getSuperReflection().getMethod(name, paramTypes);

        throw new ReflectionException("The method '%s' was not found in '%s' with parameters '%s'", name, this.getType().getName(), Arrays.asList(types));
    }

    /**
     * Returns all methods of the reflected class. When {@link #isProcessingSuperclass()}
     * is {@code true} (the default), the result includes methods from the entire
     * superclass chain and is cached per class in an unmodifiable set. When
     * {@code false}, returns only the declared methods of this class without caching.
     *
     * @return all method accessors for this class (and its superclasses if enabled)
     * @throws ReflectionException if method introspection fails
     */
    @SuppressWarnings("unchecked")
    public final @NotNull ConcurrentSet<MethodAccessor<?>> getMethods() throws ReflectionException {
        if (!this.isProcessingSuperclass()) {
            ConcurrentSet<MethodAccessor<?>> methodAccessors = Concurrent.newSet();

            for (Method method : getDeclaredMethodsCached(this.getType()))
                methodAccessors.add(new MethodAccessor<>(this, method));

            return methodAccessors;
        }

        return ALL_METHODS_CACHE.computeIfAbsent(this.getType(), cls -> {
            ConcurrentSet<MethodAccessor<?>> methodAccessors = Concurrent.newSet();
            Class<?> current = cls;

            while (current != null) {
                Reflection<?> ref = (current == cls) ? this : new Reflection<>((Class<Object>) current);

                for (Method method : getDeclaredMethodsCached(current))
                    methodAccessors.add(new MethodAccessor<>(ref, method));

                current = current.getSuperclass();
            }

            return methodAccessors.toUnmodifiableSet();
        });
    }

    /**
     * Returns the fully-qualified class name (package path and simple class name).
     *
     * @return the fully-qualified class name
     */
    public final @NotNull String getName() {
        return String.format("%s.%s", getPackageName(this.getType()), this.getType().getSimpleName());
    }

    /**
     * Converts a resource filename (e.g. {@code com/example/Foo.class}) to a
     * fully-qualified class name (e.g. {@code com.example.Foo}).
     *
     * @param filename the {@code .class} resource filename to convert
     * @return the corresponding fully-qualified class name
     */
    public static @NotNull String getName(@NotNull String filename) {
        int classNameEnd = filename.length() - ".class".length();
        return filename.substring(0, classNameEnd).replace('/', '.');
    }

    /**
     * Returns the package name of the given class by parsing its name string.
     *
     * <p>
     * Unlike {@link Class#getPackage()}, this method only parses the class name
     * without attempting to define the {@link Package} and hence load files.
     *
     * @param type the class to extract the package name from
     * @return the package name, or an empty string if the class is in the default package
     */
    public static @NotNull String getPackageName(@NotNull Class<?> type) {
        return getPackageName(type.getName());
    }

    /**
     * Returns the package name from a fully-qualified class name string.
     *
     * <p>
     * Unlike {@link Class#getPackage()}, this method only parses the class name
     * without attempting to define the {@link Package} and hence load files.
     *
     * @param classFullName the fully-qualified class name
     * @return the package name, or an empty string if the class is in the default package
     */
    public static @NotNull String getPackageName(@NotNull String classFullName) {
        int lastDot = classFullName.lastIndexOf('.');
        return (lastDot < 0) ? "" : classFullName.substring(0, lastDot);
    }

    /**
     * Returns a {@link ResourceAccessor} that scans all resources reachable from the
     * {@linkplain ClassUtil#getClassLoader() default class loader}.
     *
     * @return a resource accessor for the default class loader
     */
    public static @NotNull ResourceAccessor getResources() {
        return getResources(ClassUtil.getClassLoader());
    }

    /**
     * Returns a {@link ResourceAccessor} that scans all resources reachable from the
     * given class loader.
     *
     * @param classLoader the class loader to scan resources from
     * @return a resource accessor for the given class loader
     */
    public static @NotNull ResourceAccessor getResources(@NotNull ClassLoader classLoader) {
        return new ResourceAccessor(classLoader);
    }

    /**
     * Resolves the first generic type argument of the given object's superclass.
     * Delegates to {@link #getSuperClass(Object, int) getSuperClass(obj, 0)}.
     *
     * @param obj the instance whose class to inspect
     * @param <U> the resolved type argument
     * @return the resolved generic type argument class
     * @throws ReflectionException if no generic type argument is found
     */
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Object obj) {
        return getSuperClass(obj, 0);
    }

    /**
     * Resolves the generic type argument at the given index of the given object's superclass.
     * Delegates to {@link #getSuperClass(Class, int)}.
     *
     * @param obj the instance whose class to inspect
     * @param index the zero-based index of the type argument
     * @param <U> the resolved type argument
     * @return the resolved generic type argument class
     * @throws ReflectionException if no generic type argument is found at the given index
     */
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Object obj, int index) {
        return getSuperClass(obj.getClass(), index);
    }

    /**
     * Resolves the first generic type argument of the given class's superclass.
     * Delegates to {@link #getSuperClass(Class, int) getSuperClass(tClass, 0)}.
     *
     * @param tClass the class to inspect
     * @param <U> the resolved type argument
     * @return the resolved generic type argument class
     * @throws ReflectionException if no generic type argument is found
     */
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Class<?> tClass) {
        return getSuperClass(tClass, 0);
    }

    /**
     * Resolves the generic type argument at the given index of the given class's
     * superclass or generic interfaces. Results are cached per (class, index) pair.
     * Falls back to inspecting generic interfaces if the generic superclass is not
     * parameterized.
     *
     * @param tClass the class to inspect
     * @param index the zero-based index of the type argument
     * @param <U> the resolved type argument
     * @return the resolved generic type argument class
     * @throws ReflectionException if no generic type argument is found at the given index
     */
    @SuppressWarnings("unchecked")
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Class<?> tClass, int index) {
        Class<?> cached = SUPER_CLASS_CACHE.computeIfAbsent(Pair.of(tClass, index), key -> {
            try { // Classes
                ParameterizedType superClass = (ParameterizedType) key.getKey().getGenericSuperclass();
                return (Class<?>) superClass.getActualTypeArguments()[key.getValue()];
            } catch (ClassCastException exception) { // Types
                try {
                    for (Type type : key.getKey().getGenericInterfaces()) {
                        if (type instanceof ParameterizedType superClass)
                            return (Class<?>) superClass.getActualTypeArguments()[key.getValue()];
                    }
                } catch (Exception ignore) {
                }
            }

            throw new ReflectionException("Unable to locate generic class in '%s' at index %s", key.getKey().getSimpleName(), key.getValue());
        });

        return (Class<U>) cached;
    }

    /**
     * Resolves the first generic type argument of the given class's superclass as an
     * array type. Delegates to {@link #getSuperClassArray(Class, int) getSuperClassArray(tClass, 0)}.
     *
     * @param tClass the class to inspect
     * @param <U> the component type of the resolved array
     * @return the resolved generic type argument as an array class
     */
    public static <U> @NotNull Class<U[]> getSuperClassArray(@NotNull Class<?> tClass) {
        return getSuperClassArray(tClass, 0);
    }

    /**
     * Resolves the generic type argument at the given index of the given class's
     * superclass as an array type.
     *
     * @param tClass the class to inspect
     * @param index the zero-based index of the type argument
     * @param <U> the component type of the resolved array
     * @return the resolved generic type argument as an array class
     */
    @SuppressWarnings("unchecked")
    public static <U> @NotNull Class<U[]> getSuperClassArray(@NotNull Class<?> tClass, int index) {
        ParameterizedType superClass = (ParameterizedType) tClass.getGenericSuperclass();
        return (Class<U[]>) Array.newInstance((Class<U>) superClass.getActualTypeArguments()[index], 0).getClass();
    }

    /**
     * Creates a new reflection instance for the immediate superclass. Does not
     * validate that a superclass exists - the caller must check beforehand.
     *
     * @return a reflection instance for the superclass
     */
    @SuppressWarnings("unchecked")
    private @NotNull Reflection<?> getSuperReflection() {
        return new Reflection<>((Class<Object>) this.getType().getSuperclass());
    }

    /**
     * Returns the value of a field whose type matches the given reflection's type.
     * Delegates to {@link #getValue(Class, Object) getValue(reflection.getType(), obj)}.
     *
     * @param reflection the reflection whose type identifies the field to read
     * @param obj the instance to read from, or {@code null} for static fields
     * @return the field value
     * @throws ReflectionException if no matching field is found
     */
    public final @Nullable Object getValue(@NotNull Reflection<?> reflection, @Nullable Object obj) throws ReflectionException {
        return this.getValue(reflection.getType(), obj);
    }

    /**
     * Returns the value of a field whose type matches the given class.
     *
     * @param type the field type to match
     * @param obj the instance to read from, or {@code null} for static fields
     * @param <U> the field value type
     * @return the field value
     * @throws ReflectionException if no matching field is found
     */
    public final <U> @Nullable U getValue(@NotNull Class<U> type, @Nullable Object obj) throws ReflectionException {
        return this.getField(type).get(obj);
    }

    /**
     * Returns the value of a field with a case-sensitive name match. Delegates to
     * {@link #getValue(String, boolean, Object) getValue(name, true, obj)}.
     *
     * @param name the field name to match
     * @param obj the instance to read from, or {@code null} for static fields
     * @return the field value
     * @throws ReflectionException if no matching field is found
     */
    public final @Nullable Object getValue(@NotNull String name, @Nullable Object obj) throws ReflectionException {
        return this.getValue(name, true, obj);
    }

    /**
     * Returns the value of a field with a matching name.
     *
     * @param name the field name to match
     * @param isCaseSensitive whether to match the name case-sensitively
     * @param obj the instance to read from, or {@code null} for static fields
     * @return the field value
     * @throws ReflectionException if no matching field is found
     */
    public final @Nullable Object getValue(@NotNull String name, boolean isCaseSensitive, @Nullable Object obj) throws ReflectionException {
        return this.getField(name, isCaseSensitive).get(obj);
    }

    /**
     * Invokes the method whose return type matches the given reflection's type.
     * Delegates to {@link #invokeMethod(Class, Object, Object...) invokeMethod(reflection.getType(), obj, args)}.
     *
     * @param reflection the reflection whose type identifies the method's return type
     * @param obj the instance to invoke on, or {@code null} for static methods
     * @param args the arguments to pass to the method
     * @return the method's return value
     * @throws ReflectionException if no matching method is found
     */
    public final @Nullable Object invokeMethod(@NotNull Reflection<?> reflection, @Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        return this.invokeMethod(reflection.getType(), obj, args);
    }

    /**
     * Invokes the method whose return type matches the given class.
     *
     * @param type the return type to match
     * @param obj the instance to invoke on, or {@code null} for static methods
     * @param args the arguments to pass to the method
     * @param <U> the return type
     * @return the method's return value
     * @throws ReflectionException if no matching method is found
     */
    public final <U> @Nullable U invokeMethod(@NotNull Class<U> type, @Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        Class<?>[] types = toPrimitiveTypeArray(args);
        return this.getMethod(type, types).invoke(obj, args);
    }

    /**
     * Invokes the method with a case-sensitive name match. Delegates to
     * {@link #invokeMethod(String, boolean, Object, Object...) invokeMethod(name, true, obj, args)}.
     *
     * @param name the method name to match
     * @param obj the instance to invoke on, or {@code null} for static methods
     * @param args the arguments to pass to the method
     * @return the method's return value
     * @throws ReflectionException if no matching method is found
     */
    public final @Nullable Object invokeMethod(@NotNull String name, @Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        return this.invokeMethod(name, true, obj, args);
    }

    /**
     * Invokes the method with a matching name.
     *
     * @param name the method name to match
     * @param isCaseSensitive whether to match the name case-sensitively
     * @param obj the instance to invoke on, or {@code null} for static methods
     * @param args the arguments to pass to the method
     * @return the method's return value
     * @throws ReflectionException if no matching method is found
     */
    public final @Nullable Object invokeMethod(@NotNull String name, boolean isCaseSensitive, @Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        Class<?>[] types = toPrimitiveTypeArray(args);
        return this.getMethod(name, isCaseSensitive, types).invoke(obj, args);
    }

    private static boolean isEqualsTypeArray(Class<?>[] a, Class<?>[] o) {
        if (a.length != o.length) return false;

        for (int i = 0; i < a.length; i++) {
            if (o[i] != null && !a[i].equals(o[i]) && !a[i].isAssignableFrom(o[i]))
                return false;
        }

        return true;
    }

    /**
     * Creates a new instance of the reflected class by locating a constructor whose
     * parameter types match the given arguments. Constructor lookup uses the per-class
     * cache.
     *
     * @param args the arguments to pass to the matching constructor
     * @return a new instance of the reflected class
     * @throws ReflectionException if no matching constructor is found or instantiation fails
     */
    public final @NotNull R newInstance(@Nullable Object... args) throws ReflectionException {
        try {
            Class<?>[] types = toPrimitiveTypeArray(args);
            ConstructorAccessor<R> c = this.getConstructor(types);
            return c.newInstance(args);
        } catch (ReflectionException reflectionException) {
            throw reflectionException;
        } catch (Exception ex) {
            throw new ReflectionException(ex, "Unable to create new instance of '%s' with arguments '%s'", this.getType().getName(), Arrays.asList(args));
        }
    }

    /**
     * Sets the value of a field whose type matches the given class.
     *
     * @param type the field type to match
     * @param obj the instance to write to
     * @param value the new field value
     * @param <T> the field type
     * @throws ReflectionException if no matching field is found or the value is incompatible
     */
    public final <T> void setValue(@NotNull Class<T> type, @NotNull Object obj, @Nullable T value) throws ReflectionException {
        this.getField(type).set(obj, value);
    }

    /**
     * Sets the value of a field with a case-sensitive name match. Delegates to
     * {@link #setValue(String, boolean, Object, Object) setValue(name, true, obj, value)}.
     *
     * @param name the field name to match
     * @param obj the instance to write to
     * @param value the new field value
     * @throws ReflectionException if no matching field is found or the value is incompatible
     */
    public final void setValue(@NotNull String name, @NotNull Object obj, @Nullable Object value) throws ReflectionException {
        this.setValue(name, true, obj, value);
    }

    /**
     * Sets the value of a field with a matching name.
     *
     * @param name the field name to match
     * @param isCaseSensitive whether to match the name case-sensitively
     * @param obj the instance to write to
     * @param value the new field value
     * @throws ReflectionException if no matching field is found or the value is incompatible
     */
    public final void setValue(@NotNull String name, boolean isCaseSensitive, @NotNull Object obj, @Nullable Object value) throws ReflectionException {
        this.getField(name, isCaseSensitive).set(obj, value);
    }

    /**
     * Unwraps each class in the given array to its primitive type equivalent via
     * {@link PrimitiveUtil#unwrap(Class)}, returning a new array.
     *
     * @param types the class array to convert, may be {@code null}
     * @return an array of unwrapped primitive types
     */
    @SuppressWarnings("all")
    public static @NotNull Class<?>[] toPrimitiveTypeArray(@Nullable Class<?> @Nullable [] types) {
        Class<?>[] newTypes = new Class<?>[types != null ? types.length : 0];

        for (int i = 0; i < newTypes.length; i++)
            newTypes[i] = (types[i] != null ? PrimitiveUtil.unwrap(types[i]) : null);

        return newTypes;
    }

    /**
     * Extracts each object's class and unwraps it to its primitive type equivalent via
     * {@link PrimitiveUtil#unwrap(Class)}, returning a new array.
     *
     * @param objects the object array to convert, may be {@code null}
     * @return an array of unwrapped primitive types
     */
    @SuppressWarnings("all")
    public static @NotNull Class<?>[] toPrimitiveTypeArray(@Nullable Object @Nullable [] objects) {
        Class<?>[] newTypes = new Class<?>[objects != null ? objects.length : 0];

        for (int i = 0; i < newTypes.length; i++)
            newTypes[i] = (objects[i] != null ? PrimitiveUtil.unwrap(objects[i].getClass()) : null);

        return newTypes;
    }

    /**
     * Validates an object's fields against their {@link BuildFlag @BuildFlag} annotations.
     * Checks null/empty constraints, regex patterns, and length limits.
     *
     * <p>
     * The list of annotated fields per class is cached on first invocation,
     * so subsequent calls for the same type skip all reflective discovery and
     * only read runtime field values.
     *
     * @param target the object instance to validate
     * @throws ReflectionException if any field violates its {@link BuildFlag} constraints
     */
    @SuppressWarnings("all")
    public static void validateFlags(@NotNull Object target) {
        ConcurrentMap<String, ConcurrentMap<FieldAccessor<?>, Boolean>> invalidRequired = Concurrent.newMap();
        invalidRequired.put("_DEFAULT_", Concurrent.newMap());

        ConcurrentList<BuildFlagEntry> entries = BUILD_FLAG_CACHE.computeIfAbsent(target.getClass(), cls ->
            new Reflection<>(cls)
                .getFields()
                .stream()
                .filter(fieldAccessor -> fieldAccessor.hasAnnotation(BuildFlag.class))
                .map(fieldAccessor -> new BuildFlagEntry(fieldAccessor, fieldAccessor.getAnnotation(BuildFlag.class).orElseThrow()))
                .collect(Concurrent.toUnmodifiableList())
        );

        entries.forEach(entry -> {
            FieldAccessor<?> field = entry.fieldAccessor();
            BuildFlag flag = entry.flag();
            boolean invalid = false;
            Object value = field.get(target);

            // Null
            if (flag.nonNull()) {
                invalid = value == null;

                if (ArrayUtil.isNotEmpty(flag.group())) {
                    for (String group : flag.group()) {
                        if (!invalidRequired.containsKey(group))
                            invalidRequired.put(group, Concurrent.newMap());

                        invalidRequired.get(group).put(field, invalid);
                    }
                }
            }

            // Empty
            if (flag.notEmpty()) {
                if (value != null) {
                    Class<?> fieldType = field.getField().getType();

                    if (CharSequence.class.isAssignableFrom(fieldType))
                        invalid = StringUtil.isEmpty((CharSequence) value);
                    else if (Optional.class.isAssignableFrom(fieldType))
                        invalid = ((Optional<?>) value).isEmpty();
                    else if (Collection.class.isAssignableFrom(fieldType))
                        invalid = ((Collection<?>) value).isEmpty();
                    else if (Map.class.isAssignableFrom(fieldType))
                        invalid = ((Map<?, ?>) value).isEmpty();
                    else if (Object[].class.isAssignableFrom(fieldType))
                        invalid = ArrayUtil.isEmpty((Object[]) value);
                }

                if (ArrayUtil.isNotEmpty(flag.group())) {
                    for (String group : flag.group()) {
                        if (!invalidRequired.containsKey(group))
                            invalidRequired.put(group, Concurrent.newMap());

                        invalidRequired.get(group).put(field, invalid);
                    }
                }
            }

            // Pattern
            if (StringUtil.isNotEmpty(flag.pattern())) {
                if (value != null) {
                    Class<?> fieldType = field.getField().getType();

                    if (CharSequence.class.isAssignableFrom(fieldType)) {
                        CharSequence sequence = (CharSequence) value;
                        invalid = StringUtil.isEmpty(sequence) || !Pattern.compile(flag.pattern()).matcher(sequence).matches();
                    } else if (value instanceof Optional<?> optional) {
                        invalid = optional.map(String::valueOf)
                            .map(str -> !str.matches(flag.pattern()))
                            .orElse(false);
                    } else if (Collection.class.isAssignableFrom(fieldType))
                        invalid = ((Collection<?>) value).size() > flag.limit();

                    if (invalid)
                        throw new ReflectionException("Field '%s' in '%s' does not match pattern '%s' (value: '%s')", field.getField().getName(), target.getClass().getSimpleName(), flag.pattern(), value);
                }
            }

            // Length Limit
            if (flag.limit() >= 0) {
                if (value != null) {
                    Class<?> fieldType = field.getField().getType();
                    int actualLength = -1;

                    if (CharSequence.class.isAssignableFrom(fieldType)) {
                        CharSequence sequence = (CharSequence) value;
                        actualLength = StringUtil.length(sequence);
                        invalid = actualLength > flag.limit();
                    } else if (Collection.class.isAssignableFrom(fieldType)) {
                        Collection<?> collection = (Collection<?>) value;
                        actualLength = collection.size();
                        invalid = actualLength > flag.limit();
                    } else if (value instanceof Optional<?> optional) {
                        final ParameterizedType parameterizedType = (ParameterizedType) field.getField().getGenericType();
                        final Type actualType = parameterizedType.getActualTypeArguments()[0];

                        if (String.class.isAssignableFrom((Class<?>) actualType)) {
                            actualLength = optional.map(String::valueOf).map(StringUtil::length).orElse(0);
                            invalid = actualLength > flag.limit();
                        } else if (Number.class.isAssignableFrom((Class<?>) actualType)) {
                            actualLength = optional.map(String::valueOf).map(NumberUtil::createNumber).map(Number::intValue).orElse(0);
                            invalid = actualLength > flag.limit();
                        }
                    }

                    if (invalid)
                        throw new ReflectionException("Field '%s' in '%s' has length %s, exceeds limit of %s", field.getField().getName(), target.getClass().getSimpleName(), actualLength, flag.limit());
                }
            }
        });

        // Handle Invalid Required
        invalidRequired.getOrDefault("_DEFAULT_", Concurrent.newMap())
            .stream()
            .filterValue(Boolean::booleanValue)
            .findFirst()
            .ifPresentOrElse(pair -> {
                throw new ReflectionException("Field '%s' in '%s' is required and is null/empty", pair.getKey().getField().getName(), target.getClass().getSimpleName());
            }, () -> invalidRequired.stream()
                .filterKey(key -> !key.equals("_DEFAULT_"))
                .filter((key, fields) -> fields.stream().allMatch((field, invalid) -> invalid))
                .findFirst()
                .ifPresent(invalidGroup -> {
                    throw new ReflectionException(
                        "Field group '%s' in '%s' is required and [%s] is null/empty!",
                        invalidGroup.getKey(),
                        target.getClass().getSimpleName(),
                        invalidGroup.getValue()
                            .stream()
                            .filterValue(Boolean::booleanValue)
                            .collapseToSingle((field, invalid) -> field.getField().getName())
                            .collect(Collectors.joining(","))
                    );
                })
            );
    }

}
