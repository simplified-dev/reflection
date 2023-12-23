package dev.sbs.api.reflection;

import dev.sbs.api.reflection.accessor.ConstructorAccessor;
import dev.sbs.api.reflection.accessor.FieldAccessor;
import dev.sbs.api.reflection.accessor.MethodAccessor;
import dev.sbs.api.reflection.accessor.ResourceAccessor;
import dev.sbs.api.reflection.exception.ReflectionException;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.helper.ClassUtil;
import dev.sbs.api.util.helper.PrimitiveUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.api.util.helper.SystemUtil;
import dev.sbs.api.util.mutable.tuple.pair.Pair;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Allows for cached access to hidden fields, methods and classes.
 */
@Getter
public class Reflection<T> {

    private static final @NotNull Map<String, Map<Class<?>[], ConstructorAccessor>> CONSTRUCTOR_CACHE = new HashMap<>();
    private static final @NotNull Map<String, Map<Class<?>, Map<Class<?>[], MethodAccessor>>> METHOD_CACHE_CLASS = new HashMap<>();
    private static final @NotNull Map<String, Map<String, Map<Class<?>[], MethodAccessor>>> METHOD_CACHE_NAME = new HashMap<>();
    private static final @NotNull Map<String, Map<Class<?>, FieldAccessor>> FIELD_CACHE_CLASS = new HashMap<>();
    private static final @NotNull Map<String, Map<String, FieldAccessor>> FIELD_CACHE_NAME = new HashMap<>();
    //private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();
    private final @NotNull Class<T> type;

    /**
     * Creates a new reflection instance of {@literal packageName}.{@literal className}.
     *
     * @param packageName The package the {@literal className} belongs to.
     * @param simplifiedName The class name to reflect.
     */
    private Reflection(@NotNull String packageName, @NotNull String simplifiedName) {
        this(String.format("%s.%s", packageName, simplifiedName));
    }

    /**
     * Creates a new reflection instance of {@literal classPath}.
     *
     * @param classPath The fully-qualified class path to reflect.
     */
    @SuppressWarnings("unchecked")
    private Reflection(@NotNull String classPath) {
        try {
            this.type = (Class<T>) Class.forName(classPath);
        } catch (Exception cnfex) {
            throw SimplifiedException.of(ReflectionException.class)
                .withCause(cnfex)
                .build();
        }
    }

    /**
     * Creates a new reflection instance of {@literal clazz}.
     *
     * @param type The class to reflect.
     */
    private Reflection(@NotNull Class<T> type) {
        this.type = PrimitiveUtil.wrap(type);
    }

    public static <T> Reflection<T> of(@NotNull Class<T> clazz) {
        return new Reflection<>(clazz);
    }

    public static <T> Reflection<T> of(@NotNull String packageName, @NotNull String simplifiedName) {
        return new Reflection<>(packageName, simplifiedName);
    }

    /**
     * Gets a constructor with the matching parameter types.
     * <p>
     * The parameter types are automatically checked against assignable types and primitives.
     * <p>
     * Super classes are automatically checked.
     *
     * @param paramTypes The types of parameters to look for.
     * @return The constructor with matching parameter types.
     * @throws ReflectionException When the class or constructor cannot be located.
     */
    public final ConstructorAccessor getConstructor(Class<?>... paramTypes) throws ReflectionException {
        Class<?>[] types = toPrimitiveTypeArray(paramTypes);

        if (CONSTRUCTOR_CACHE.containsKey(this.getName())) {
            Map<Class<?>[], ConstructorAccessor> constructors = CONSTRUCTOR_CACHE.get(this.getName());

            for (Map.Entry<Class<?>[], ConstructorAccessor> entry : constructors.entrySet()) {
                if (Arrays.equals(entry.getKey(), types)) {
                    return entry.getValue();
                }
            }
        } else
            CONSTRUCTOR_CACHE.put(this.getName(), new HashMap<>());

        for (Constructor<?> constructor : this.getType().getDeclaredConstructors()) {
            Class<?>[] constructorTypes = toPrimitiveTypeArray(constructor.getParameterTypes());

            if (isEqualsTypeArray(constructorTypes, types)) {
                constructor.setAccessible(true);
                ConstructorAccessor constructorAccessor = new ConstructorAccessor(this, constructor);
                CONSTRUCTOR_CACHE.get(this.getName()).put(types, constructorAccessor);
                return constructorAccessor;
            }
        }

        throw SimplifiedException.of(ReflectionException.class)
            .withMessage("The constructor matching '%s' was not found!", Arrays.asList(types))
            .build();
    }

    /**
     * Gets a field with matching type.
     * <p>
     * The type is automatically checked against assignable types and primitives.
     * <p>
     * Super classes are automatically checked.
     *
     * @param type The type to look for.
     * @return The field with matching type.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final @NotNull FieldAccessor getField(@NotNull Class<?> type) throws ReflectionException {
        Class<?> utype = (type.isPrimitive() ? PrimitiveUtil.wrap(type) : PrimitiveUtil.unwrap(type));

        if (FIELD_CACHE_CLASS.containsKey(this.getName())) {
            Map<Class<?>, FieldAccessor> fields = FIELD_CACHE_CLASS.get(this.getName());

            if (fields.containsKey(utype))
                return fields.get(utype);
        } else
            FIELD_CACHE_CLASS.put(this.getName(), new HashMap<>());

        for (Field field : this.getType().getDeclaredFields()) {
            if (field.getType().equals(type) || type.isAssignableFrom(field.getType()) || field.getType().equals(utype) || utype.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                FieldAccessor fieldAccessor = new FieldAccessor(this, field);
                FIELD_CACHE_CLASS.get(this.getName()).put(type, fieldAccessor);
                return fieldAccessor;
            }
        }

        if (this.getType().getSuperclass() != null)
            return this.getSuperReflection().getField(type);

        throw SimplifiedException.of(ReflectionException.class)
            .withMessage("The field with type '%s' was not found!", type)
            .build();
    }

    /**
     * Gets a field with identically matching name.
     * <p>
     * This is the same as calling {@link #getField(String, boolean) getField(name, true)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name The field name to look for.
     * @return The field with identically matching name.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final @NotNull FieldAccessor getField(@NotNull String name) throws ReflectionException {
        return this.getField(name, true);
    }

    /**
     * Gets a field with matching name.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name            The field name to look for.
     * @param isCaseSensitive Whether or not to check case-sensitively.
     * @return The field with matching name.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final @NotNull FieldAccessor getField(@NotNull String name, boolean isCaseSensitive) throws ReflectionException {
        if (FIELD_CACHE_NAME.containsKey(this.getName())) {
            Map<String, FieldAccessor> fields = FIELD_CACHE_NAME.get(this.getName());

            if (fields.containsKey(name))
                return fields.get(name);
        } else
            FIELD_CACHE_NAME.put(this.getName(), new HashMap<>());

        for (Field field : this.getType().getDeclaredFields()) {
            if (isCaseSensitive ? field.getName().equals(name) : field.getName().equalsIgnoreCase(name)) {
                field.setAccessible(true);
                FieldAccessor fieldAccessor = new FieldAccessor(this, field);
                FIELD_CACHE_NAME.get(this.getName()).put(name, fieldAccessor);
                return fieldAccessor;
            }
        }

        if (this.getType().getSuperclass() != null)
            return this.getSuperReflection().getField(name);

        throw SimplifiedException.of(ReflectionException.class)
            .withMessage("The field '%s' was not found!", name)
            .build();
    }

    /**
     * Gets all fields of the given {@link #getType()}.
     * <br>
     * Super classes are automatically checked.
     *
     * @return All fields.
     * @throws ReflectionException When the class or fields cannot be located.
     */
    public final @NotNull ConcurrentSet<FieldAccessor> getFields() throws ReflectionException {
        ConcurrentSet<FieldAccessor> fieldAccessors = Concurrent.newSet();

        if (!FIELD_CACHE_CLASS.containsKey(this.getName()))
            FIELD_CACHE_CLASS.put(this.getName(), Concurrent.newMap());

        for (Field field : this.getType().getDeclaredFields()) {
            field.setAccessible(true);
            FieldAccessor fieldAccessor = new FieldAccessor(this, field);
            FIELD_CACHE_CLASS.get(this.getName()).put(field.getType(), fieldAccessor);
            fieldAccessors.add(fieldAccessor);
        }

        if (this.getType().getSuperclass() != null)
            fieldAccessors.addAll(this.getSuperReflection().getFields());

        return fieldAccessors;
    }

    /**
     * Returns the URLs in the class path specified by the {@code java.class.path}
     * {@link System#getProperty system property}.
     */
    public static @NotNull ConcurrentList<URL> getJavaClassPath() {
        ConcurrentList<URL> urls = Concurrent.newList();

        for (String entry : Objects.requireNonNull(StringUtil.split(SystemUtil.JAVA_CLASS_PATH, SystemUtil.PATH_SEPARATOR))) {
            try {
                try {
                    urls.add(new File(entry).toURI().toURL());
                } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
                    urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
                }
            } catch (MalformedURLException ignore) { }
        }

        return urls.toUnmodifiableList();
    }

    /**
     * Gets a method with matching return type and parameter types.
     * <p>
     * The return type and parameter types are automatically checked against assignable types and primitives.
     * <p>
     * Super classes are automatically checked.
     *
     * @param type       The return type to look for.
     * @param paramTypes The types of parameters to look for.
     * @return The field with matching return type and parameter types.
     * @throws ReflectionException When the class or method cannot be located.
     */
    public final @NotNull MethodAccessor getMethod(@NotNull Class<?> type, @Nullable Class<?>... paramTypes) throws ReflectionException {
        Class<?> utype = (type.isPrimitive() ? PrimitiveUtil.wrap(type) : PrimitiveUtil.unwrap(type));
        Class<?>[] types = toPrimitiveTypeArray(paramTypes);

        if (METHOD_CACHE_CLASS.containsKey(this.getName())) {
            Map<Class<?>, Map<Class<?>[], MethodAccessor>> methods = METHOD_CACHE_CLASS.get(this.getName());

            if (methods.containsKey(type)) {
                Map<Class<?>[], MethodAccessor> returnTypeMethods = methods.get(type);

                for (Map.Entry<Class<?>[], MethodAccessor> entry : returnTypeMethods.entrySet()) {
                    if (Arrays.equals(entry.getKey(), types)) {
                        return entry.getValue();
                    }
                }
            } else
                METHOD_CACHE_CLASS.get(this.getName()).put(type, new HashMap<>());
        } else {
            METHOD_CACHE_CLASS.put(this.getName(), new HashMap<>());
            METHOD_CACHE_CLASS.get(this.getName()).put(type, new HashMap<>());
        }

        for (Method method : this.getType().getDeclaredMethods()) {
            Class<?>[] methodTypes = toPrimitiveTypeArray(method.getParameterTypes());
            Class<?> returnType = method.getReturnType();

            if ((returnType.equals(type) || type.isAssignableFrom(returnType) || returnType.equals(utype) || utype.isAssignableFrom(returnType)) && isEqualsTypeArray(methodTypes, types)) {
                method.setAccessible(true);
                MethodAccessor methodAccessor = new MethodAccessor(this, method);
                METHOD_CACHE_CLASS.get(this.getName()).get(type).put(types, methodAccessor);
                return methodAccessor;
            }
        }

        if (this.getType().getSuperclass() != null)
            return this.getSuperReflection().getMethod(type, paramTypes);

        throw SimplifiedException.of(ReflectionException.class)
            .withMessage("The method with return type '%s' was not found with parameters ['%s']!", type, Arrays.asList(types))
            .build();
    }

    /**
     * Gets a field with identically matching name and parameter types.
     * <p>
     * The parameter types are automatically checked against assignable types and primitives.
     * <p>
     * This is the same as calling {@link #getMethod(String, boolean, Class...) getMethod(name, true, paramTypes)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name       The method name to look for.
     * @param paramTypes The types of parameters to look for.
     * @return The method with matching name and parameter types.
     * @throws ReflectionException When the class or method cannot be located.
     */
    public final @NotNull MethodAccessor getMethod(@NotNull String name, @Nullable Class<?>... paramTypes) throws ReflectionException {
        return this.getMethod(name, true, paramTypes);
    }

    /**
     * Gets a field with matching name and parameter types.
     * <p>
     * The parameter types are automatically checked against assignable types and primitives.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name            The method name to look for.
     * @param isCaseSensitive Whether to check case-sensitively.
     * @param paramTypes      The types of parameters to look for.
     * @return The method with matching name and parameter types.
     * @throws ReflectionException When the class or method cannot be located.
     */
    public final @NotNull MethodAccessor getMethod(@NotNull String name, boolean isCaseSensitive, @Nullable Class<?>... paramTypes) throws ReflectionException {
        Class<?>[] types = toPrimitiveTypeArray(paramTypes);

        if (METHOD_CACHE_NAME.containsKey(this.getName())) {
            Map<String, Map<Class<?>[], MethodAccessor>> methods = METHOD_CACHE_NAME.get(this.getName());

            if (methods.containsKey(name)) {
                Map<Class<?>[], MethodAccessor> nameMethods = methods.get(name);

                for (Map.Entry<Class<?>[], MethodAccessor> entry : nameMethods.entrySet()) {
                    if (Arrays.equals(entry.getKey(), types))
                        return entry.getValue();
                }
            } else
                METHOD_CACHE_NAME.get(this.getName()).put(name, new HashMap<>());
        } else {
            METHOD_CACHE_NAME.put(this.getName(), new HashMap<>());
            METHOD_CACHE_NAME.get(this.getName()).put(name, new HashMap<>());
        }

        for (Method method : this.getType().getDeclaredMethods()) {
            Class<?>[] methodTypes = toPrimitiveTypeArray(method.getParameterTypes());

            if ((isCaseSensitive ? method.getName().equals(name) : method.getName().equalsIgnoreCase(name)) && isEqualsTypeArray(methodTypes, types)) {
                method.setAccessible(true);
                MethodAccessor methodAccessor = new MethodAccessor(this, method);
                METHOD_CACHE_NAME.get(this.getName()).get(name).put(types, methodAccessor);
                return methodAccessor;
            }
        }

        if (this.getType().getSuperclass() != null)
            return this.getSuperReflection().getMethod(name, paramTypes);

        throw SimplifiedException.of(ReflectionException.class)
            .withMessage("The method ''%s'' was not found with parameters ''%s''.", name, Arrays.asList(types))
            .build();
    }

    /**
     * Gets the fully-qualified class path (includes package path and class name).
     *
     * @return The fully-qualified class path.
     */
    public final @NotNull String getName() {
        return String.format("%s.%s", getPackageName(this.getType()), this.getType().getSimpleName());
    }

    public static @NotNull String getName(@NotNull String filename) {
        int classNameEnd = filename.length() - ".class".length();
        return filename.substring(0, classNameEnd).replace('/', '.');
    }

    /**
     * Returns the package name of {@code clazz}.
     * <br><br>
     * Unlike {@link Class#getPackage}, this method only parses the class name, without
     * attempting to define the {@link Package} and hence load files.
     */
    public static @NotNull String getPackageName(@NotNull Class<?> type) {
        return getPackageName(type.getName());
    }

    /**
     * Returns the package name of {@code classFullName}.
     * <br><br>
     * Unlike {@link Class#getPackage}, this method only parses the class name, without
     * attempting to define the {@link Package} and hence load files.
     */
    public static @NotNull String getPackageName(@NotNull String classFullName) {
        int lastDot = classFullName.lastIndexOf('.');
        return (lastDot < 0) ? "" : classFullName.substring(0, lastDot);
    }

    public static @NotNull ResourceAccessor getResources() {
        return getResources(ClassUtil.getClassLoader());
    }

    public static @NotNull ResourceAccessor getResources(@NotNull ClassLoader classLoader) {
        return new ResourceAccessor(classLoader);
    }

    /**
     * Gets the generic class specified at index 0.
     *
     * @param obj the instance to check for generics
     * @param <U> the type to return as
     * @return the generic superclass
     */
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Object obj) {
        return getSuperClass(obj, 0);
    }

    /**
     * Gets the generic class specified at the specified index.
     *
     * @param obj   the instance to check for generics
     * @param index the index to check for generics
     * @param <U>   the type to return as
     * @return the generic superclass
     */
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Object obj, int index) {
        return getSuperClass(obj.getClass(), index);
    }

    /**
     * Gets the generic class specified at index 0.
     *
     * @param tClass the class to check for generics
     * @param <U>    the type to return as
     * @return the generic superclass
     */
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Class<?> tClass) {
        return getSuperClass(tClass, 0);
    }

    /**
     * Gets the generic class specified at the specified index.
     *
     * @param tClass the class to check for generics
     * @param index  the index to check for generics
     * @param <U>    the type to return as
     * @return the generic superclass
     */
    @SuppressWarnings("unchecked")
    public static <U> @NotNull Class<U> getSuperClass(@NotNull Class<?> tClass, int index) {
        try { // Classes
            ParameterizedType superClass = (ParameterizedType) tClass.getGenericSuperclass();
            return (Class<U>) superClass.getActualTypeArguments()[index];
        } catch (ClassCastException exception) { // Types
            try {
                for (Type type : tClass.getGenericInterfaces()) {
                    if (type instanceof ParameterizedType superClass)
                        return (Class<U>) superClass.getActualTypeArguments()[index];
                }
            } catch (Exception ignore) {
            }
        }

        throw SimplifiedException.of(ReflectionException.class)
            .withMessage("Unable to locate generic class in '%s' at index %s!", tClass.getSimpleName(), index)
            .build();
    }

    /**
     * Gets a generic class array specified at index 0.
     *
     * @param tClass the class to check for generics
     * @param <U>    the type to return as
     * @return the generic superclass array
     */
    public static <U> @NotNull Class<U[]> getSuperClassArray(@NotNull Class<?> tClass) {
        return getSuperClassArray(tClass, 0);
    }

    /**
     * Gets a generic class array specified at the specified index.
     *
     * @param tClass the class to check for generics
     * @param index  the index to check for generics
     * @param <U>    the type to return as
     * @return the generic superclass array
     */
    @SuppressWarnings("unchecked")
    public static <U> @NotNull Class<U[]> getSuperClassArray(@NotNull Class<?> tClass, int index) {
        ParameterizedType superClass = (ParameterizedType) tClass.getGenericSuperclass();
        return (Class<U[]>) Array.newInstance((Class<U>) superClass.getActualTypeArguments()[index], 0).getClass();
    }

    /**
     * Gets a new reflection object of the superclass.
     * <p>
     * This does not check if the superclass is just a {@link Class}.
     *
     * @return The reflected superclass.
     * @throws ReflectionException When the class or superclass cannot be located.
     */
    private @NotNull Reflection<?> getSuperReflection() throws ReflectionException {
        Class<?> superClass = this.getType().getSuperclass();
        String packageName = getPackageName(superClass.getName());
        String className = superClass.getSimpleName();
        return of(packageName, className);
    }

    /**
     * Gets the value of a field with matching {@link #getType() class type}.
     * <p>
     * The field type is automatically checked against assignable types and primitives.
     * <p>
     * This is the same as calling {@link #getValue(Class, Object) getValue(reflection.getClazz(), obj)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param reflection The reflection object housing the field's class type to look for.
     * @param obj        Instance of the current class object, null if static field.
     * @return The field value with matching type.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final @Nullable Object getValue(@NotNull Reflection<?> reflection, @Nullable Object obj) throws ReflectionException {
        return this.getValue(reflection.getType(), obj);
    }

    /**
     * Gets the value of a field with matching {@link #getType() class type}.
     * <p>
     * The field type is automatically checked against assignable types and primitives.
     * <p>
     * Super classes are automatically checked.
     *
     * @param type The field type to look for.
     * @param obj  Instance of the current class object, null if static field.
     * @return The field value with matching type.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final <U> @Nullable U getValue(@NotNull Class<U> type, @Nullable Object obj) throws ReflectionException {
        return this.getField(type).get(obj);
    }

    /**
     * Gets the value of a field with matching {@link #getType() class type}.
     * <p>
     * This is the same as calling {@link #getValue(String, boolean, Object) getValue(name, true, obj)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name The field name to look for.
     * @param obj  Instance of the current class object, null if static field.
     * @return The field value with identically matching name.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final @Nullable Object getValue(@NotNull String name, @Nullable Object obj) throws ReflectionException {
        return this.getValue(name, true, obj);
    }

    /**
     * Gets the value of a field with matching {@link #getType() class type}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name            The field name to look for.
     * @param isCaseSensitive Whether or not to check case-sensitively.
     * @param obj             Instance of the current class object, null if static field.
     * @return The field value with matching name.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final @Nullable Object getValue(@NotNull String name, boolean isCaseSensitive, @Nullable Object obj) throws ReflectionException {
        return this.getField(name, isCaseSensitive).get(obj);
    }

    /**
     * Gets the value of an invoked method with matching {@link #getType()} class type}.
     * <p>
     * The method's return type is automatically checked against assignable types and primitives.
     * <p>
     * This is the same as calling {@link #invokeMethod(Class, Object, Object...) invokeMethod(reflection.getClazz(), obj, args)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param reflection The reflection object housing the field's class type.
     * @param obj        Instance of the current class object, null if static field.
     * @param args       The arguments with matching types to pass to the method.
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the class or method with matching arguments cannot be located.
     */
    public final @Nullable Object invokeMethod(@NotNull Reflection<?> reflection, @Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        return this.invokeMethod(reflection.getType(), obj, args);
    }

    /**
     * Gets the value of an invoked method with matching {@link #getType()} class type}.
     * <p>
     * The method's return type is automatically checked against assignable types and primitives.
     * <p>
     * Super classes are automatically checked.
     *
     * @param type The return type to look for.
     * @param obj  Instance of the current class object, null if static field.
     * @param args The arguments with matching types to pass to the method.
     * @return The invoked method value with matching return type.
     * @throws ReflectionException When the class or method with matching arguments cannot be located.
     */
    @SuppressWarnings("unchecked")
    public final <U> @Nullable U invokeMethod(@NotNull Class<U> type, @Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        Class<?>[] types = toPrimitiveTypeArray(args);
        return (U) this.getMethod(type, types).invoke(obj, args);
    }

    /**
     * Gets the value of an invoked method with identically matching name.
     * <p>
     * This is the same as calling {@link #invokeMethod(String, boolean, Object, Object...) getValue(name, true, obj, args)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name The field name to look for.
     * @param obj  Instance of the current class object, null if static field.
     * @param args The arguments with matching types to pass to the method.
     * @return The invoked method value with identically matching name.
     * @throws ReflectionException When the class or method with matching arguments cannot be located.
     */
    public final @Nullable Object invokeMethod(@NotNull String name, @Nullable Object obj, @Nullable Object... args) throws ReflectionException {
        return this.invokeMethod(name, true, obj, args);
    }

    /**
     * Gets the value of an invoked method with identically matching name.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name            The field name to look for.
     * @param isCaseSensitive Whether or not to check case-sensitively.
     * @param obj             Instance of the current class object, null if static field.
     * @param args            The arguments with matching types to pass to the method.
     * @return The invoked method value with identically matching name.
     * @throws ReflectionException When the class or method with matching arguments cannot be located.
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
     * Creates a new instance of the current {@link #getType() class type} with given parameters.
     * <p>
     * Super classes are automatically checked.
     *
     * @param args The arguments with matching types to pass to the constructor.
     * @throws ReflectionException When the class or constructor with matching arguments cannot be located.
     */
    @SuppressWarnings("unchecked")
    public final @NotNull T newInstance(@Nullable Object... args) throws ReflectionException {
        try {
            Class<?>[] types = toPrimitiveTypeArray(args);
            ConstructorAccessor c = this.getConstructor(types);
            return (T) c.newInstance(args);
        } catch (ReflectionException reflectionException) {
            throw reflectionException;
        } catch (Exception ex) {
            throw SimplifiedException.of(ReflectionException.class)
                .withCause(ex)
                .build();
        }
    }

    /**
     * Sets the value of a field with matching {@link #getType() class type}.
     * <p>
     * The field type is automatically checked against assignable types and primitives.
     * <p>
     * This is the same as calling {@link #setValue(Class, Object, Object) setValue(reflection.getClazz(), obj, value)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param reflection The reflection object housing the field's class type to look for.
     * @param obj        Instance of the current class object, null if static field.
     * @param value      The new value of the field.
     * @throws ReflectionException When the class or field cannot be located.
     */
    public final void setValue(@NotNull Reflection<?> reflection, @NotNull Object obj, @Nullable Object value) throws ReflectionException {
        this.setValue(reflection.getType(), obj, value);
    }

    /**
     * Sets the value of a field with matching {@link #getType() class type}.
     * <p>
     * The field type is automatically checked against assignable types and primitives.
     * <p>
     * Super classes are automatically checked.
     *
     * @param type  The field type to look for.
     * @param obj   Instance of the current class object, null if static field.
     * @param value The new value of the field.
     * @throws ReflectionException When the class or field cannot be located or the value does match the field type.
     */
    public final void setValue(@NotNull Class<?> type, @NotNull Object obj, @Nullable Object value) throws ReflectionException {
        this.getField(type).set(obj, value);
    }

    /**
     * Sets the value of a field with identically matching name.
     * <p>
     * This is the same as calling {@link #setValue(String, boolean, Object, Object) setValue(name, true, obj, value)}.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name  The field name to look for.
     * @param obj   Instance of the current class object, null if static field.
     * @param value The new value of the field.
     * @throws ReflectionException When the class or field cannot be located or the value does match the field type.
     */
    public final void setValue(@NotNull String name, @NotNull Object obj, @Nullable Object value) throws ReflectionException {
        this.setValue(name, true, obj, value);
    }

    /**
     * Sets the value of a field with matching name.
     * <p>
     * Super classes are automatically checked.
     *
     * @param name            The field name to look for.
     * @param isCaseSensitive Whether to check case-sensitively.
     * @param obj             Instance of the current class object, null if static field.
     * @param value           The new value of the field.
     * @throws ReflectionException When the class or field cannot be located or the value does match the field type.
     */
    public final void setValue(@NotNull String name, boolean isCaseSensitive, @NotNull Object obj, @Nullable Object value) throws ReflectionException {
        this.getField(name, isCaseSensitive).set(obj, value);
    }

    /**
     * Converts any primitive classes in the given classes to their primitive types.
     *
     * @param types The classes to convert.
     * @return Converted class types.
     */
    @SuppressWarnings("all")
    public static @NotNull Class<?>[] toPrimitiveTypeArray(Class<?>[] types) {
        Class<?>[] newTypes = new Class<?>[types != null ? types.length : 0];

        for (int i = 0; i < newTypes.length; i++)
            newTypes[i] = (types[i] != null ? PrimitiveUtil.unwrap(types[i]) : null);

        return newTypes;
    }

    /**
     * Converts any primitive classes in the given objects to their primitive types.
     *
     * @param objects The objects to convert.
     * @return Converted class types.
     */
    @SuppressWarnings("all")
    public static @NotNull Class<?>[] toPrimitiveTypeArray(Object[] objects) {
        Class<?>[] newTypes = new Class<?>[objects != null ? objects.length : 0];

        for (int i = 0; i < newTypes.length; i++)
            newTypes[i] = (objects[i] != null ? PrimitiveUtil.unwrap(objects[i].getClass()) : null);

        return newTypes;
    }

    public static <T extends Builder<?>> void validateFlags(@NotNull T builder) {
        ConcurrentMap<String, ConcurrentMap<FieldAccessor, Boolean>> invalidRequired = Concurrent.newMap();

        Reflection.of(builder.getClass())
            .getFields()
            .stream()
            .filter(fieldAccessor -> fieldAccessor.hasAnnotation(BuildFlag.class))
            .map(fieldAccessor -> Pair.of(fieldAccessor, fieldAccessor.getAnnotation(BuildFlag.class).orElseThrow()))
            .forEach(fieldPair -> {
                FieldAccessor field = fieldPair.getLeft();
                BuildFlag flag = fieldPair.getRight();
                boolean invalid = false;

                // Null/Empty
                if (flag.required()) {
                    Object value = field.get(builder);

                    if (value == null)
                        invalid = true;
                    else {
                        Class<?> fieldType = field.getField().getType();

                        if (CharSequence.class.isAssignableFrom(fieldType))
                            invalid = StringUtil.isEmpty((CharSequence) value);
                        else if (Optional.class.isAssignableFrom(fieldType))
                            invalid = ((Optional<?>) value).isEmpty();
                        else if (Collection.class.isAssignableFrom(fieldType))
                            invalid = ((Collection<?>) value).isEmpty();
                        else if (Map.class.isAssignableFrom(fieldType))
                            invalid = ((Map<?, ?>) value).isEmpty();
                    }

                    String invalidKey = Optional.ofNullable(StringUtil.stripToNull(flag.requireGroup())).orElse("_DEFAULT_");

                    if (!invalidRequired.containsKey(invalidKey))
                        invalidRequired.put(invalidKey, Concurrent.newMap());

                    invalidRequired.get(invalidKey).put(field, invalid);
                }

                // Pattern
                if (StringUtil.isNotEmpty(flag.pattern())) {
                    Object value = field.get(builder);

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

                        if (invalid) {
                            throw SimplifiedException.of(ReflectionException.class)
                                .withMessage("Field '%s' does not match pattern '%s'!", field.getField().getName(), flag.pattern())
                                .build();
                        }
                    }
                }

                // Length Limit
                if (flag.limit() >= 0) {
                    Object value = field.get(builder);

                    if (value != null) {
                        Class<?> fieldType = field.getField().getType();

                        if (CharSequence.class.isAssignableFrom(fieldType)) {
                            CharSequence sequence = (CharSequence) value;
                            invalid = StringUtil.length(sequence) > flag.limit();
                        } else if (value instanceof Optional<?> optional) {
                            final ParameterizedType parameterizedType = (ParameterizedType) field.getField().getGenericType();
                            final Type actualType = parameterizedType.getActualTypeArguments()[0];

                            if (String.class.isAssignableFrom((Class<?>) actualType)) {
                                invalid = optional.map(String::valueOf)
                                    .map(StringUtil::length)
                                    .map(length -> length > flag.limit())
                                    .orElse(false);
                            }
                        }

                        if (invalid) {
                            throw SimplifiedException.of(ReflectionException.class)
                                .withMessage("Field '%s' does not have length of '%s' or lower!", field.getField().getName(), flag.limit())
                                .build();
                        }
                    }
                }
            });

        // Handle Invalid Required
        invalidRequired.getOrDefault("_DEFAULT_", Concurrent.newMap())
            .stream()
            .filterValue(Boolean::booleanValue)
            .findFirst()
            .ifPresentOrElse(pair -> {
                throw SimplifiedException.of(ReflectionException.class)
                    .withMessage("Field '%s' is required and is null/empty!", pair.getKey().getField().getName())
                    .build();
            }, () -> invalidRequired.stream()
                .filterKey(key -> !key.equals("_DEFAULT_"))
                .filter((key, fields) -> fields.stream().allMatch((field, invalid) -> invalid))
                .findFirst()
                .ifPresent(invalidGroup -> {
                    throw SimplifiedException.of(ReflectionException.class)
                        .withMessage(
                            "Field group '%s' is required and [%s] is null/empty!",
                            invalidGroup.getKey(),
                            invalidGroup.getValue()
                                .stream()
                                .filterValue(Boolean::booleanValue)
                                .map((field, invalid) -> field.getField().getName())
                                .collect(Collectors.joining(","))
                        )
                        .build();
                })
            );
    }

}
