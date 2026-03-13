package dev.sbs.api.reflection.accessor;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.info.ClassInfo;
import dev.sbs.api.reflection.info.LocationInfo;
import dev.sbs.api.reflection.info.ResourceInfo;
import dev.sbs.api.util.Preconditions;
import dev.sbs.api.util.StringUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Scans classpath resources reachable from a given {@link ClassLoader}, providing
 * filtered views over the discovered {@link ResourceInfo} entries.
 * <p>
 * Use {@link Reflection#getResources()} to obtain an instance for the default class loader,
 * then narrow the search with {@link #filterPackage(String)} before calling
 * {@link #getTypesOf(Class)} or {@link #getSubtypesOf(Class)}.
 */
@Getter
public class ResourceAccessor {

    private final @NotNull ClassLoader classLoader;
    private final @NotNull ConcurrentList<ResourceInfo> resources;

    /**
     * Creates a {@link ResourceAccessor} that scans all resources reachable from the given
     * class loader, without any package filter.
     *
     * @param classLoader the class loader to scan
     */
    public ResourceAccessor(@NotNull ClassLoader classLoader) {
        this(classLoader, null);
    }

    /**
     * Creates a {@link ResourceAccessor} that scans all resources reachable from the given
     * class loader, optionally restricting results to a specific package.
     *
     * @param classLoader the class loader to scan
     * @param packageName the dot-separated package name to filter by, or {@code null} for all resources
     */
    public ResourceAccessor(@NotNull ClassLoader classLoader, @Nullable String packageName) {
        this.classLoader = classLoader;
        ConcurrentList<LocationInfo> locations = getLocationsFromClassLoader(classLoader);
        String resourceName = StringUtil.stripToEmpty(packageName).replace(".", "/").toLowerCase();

        // Add all locations to the scanned set so that in a classpath [jar1, jar2], where jar1 has a
        // manifest with Class-Path pointing to jar2, we won't scan jar2 twice.
        ConcurrentSet<File> scanned = locations.stream()
            .map(LocationInfo::getFile)
            .collect(Concurrent.toSet());

        // Scan all locations
        this.resources = locations.stream()
            .flatMap(locationInfo -> locationInfo.scanResources(scanned).stream())
            .filter(resourceInfo -> StringUtil.isEmpty(resourceName) || resourceInfo.getResourceName().startsWith(resourceName))
            .collect(Concurrent.toUnmodifiableList());
    }

    private ResourceAccessor(@NotNull ClassLoader classLoader, @NotNull ConcurrentList<ResourceInfo> resources, @NotNull String packageName) {
        this.classLoader = classLoader;
        String resourceName = StringUtil.stripToEmpty(packageName).replace(".", "/").toLowerCase();

        this.resources = resources.stream()
            .filter(resourceInfo -> resourceInfo.getResourceName().startsWith(resourceName))
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Returns a new {@link ResourceAccessor} restricted to resources within the package of
     * the given class.
     *
     * @param type the class whose package is used as the filter
     * @return a filtered resource accessor
     */
    public @NotNull ResourceAccessor filterPackage(@NotNull Class<?> type) {
        return this.filterPackage(Reflection.getPackageName(type));
    }

    /**
     * Returns a new {@link ResourceAccessor} restricted to resources within the given package.
     *
     * @param packageName the dot-separated package name to filter by
     * @return a filtered resource accessor
     */
    public @NotNull ResourceAccessor filterPackage(@NotNull String packageName) {
        return new ResourceAccessor(this.getClassLoader(), this.getResources(), packageName);
    }

    /**
     * Returns all {@link ClassInfo} entries discovered in the scanned classpath locations.
     *
     * @return an unmodifiable list of class info entries
     */
    public @NotNull ConcurrentList<ClassInfo> getClasses() {
        return this.getResources()
            .stream()
            .filter(ClassInfo.class::isInstance)
            .map(ClassInfo.class::cast)
            .collect(Concurrent.toUnmodifiableList());
    }

    private <T> @NotNull Stream<? extends Class<?>> getClassesOf(@NotNull Class<T> type) {
        return this.getClasses()
            .stream()
            .map(ClassInfo::load)
            .filter(storedType -> !storedType.equals(type))
            .filter(type::isAssignableFrom);
    }

    /**
     * Returns all resources whose resource name starts with the given directory prefix.
     *
     * @param directory the path prefix to filter by (e.g. {@code "com/example/"})
     * @return an unmodifiable list of matching resources
     */
    public @NotNull ConcurrentList<ResourceInfo> getResources(@NotNull String directory) {
        return this.getResources()
            .stream()
            .filter(resourceInfo -> resourceInfo.getResourceName().startsWith(directory))
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Returns all classes that are subtypes of the given type (excluding the type itself).
     *
     * @param <T>  the type
     * @param type the supertype or interface to search for
     * @return an unmodifiable list of assignable subtype classes
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull ConcurrentList<Class<? extends T>> getSubtypesOf(@NotNull Class<T> type) {
        return this.getClassesOf(type)
            .map(storedType -> (Class<? extends T>) storedType)
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Returns all classes that are assignable to the given type (excluding the type itself),
     * cast to {@code Class<T>}.
     *
     * @param <T>  the type
     * @param type the supertype or interface to search for
     * @return an unmodifiable list of matching classes cast to {@code Class<T>}
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull ConcurrentList<Class<T>> getTypesOf(@NotNull Class<T> type) {
        return this.getClassesOf(type)
            .map(storedType -> (Class<T>) storedType)
            .collect(Concurrent.toUnmodifiableList());
    }

    private static @NotNull ConcurrentMap<File, ClassLoader> getClassPathEntries(@NotNull ClassLoader classloader) {
        ConcurrentMap<File, ClassLoader> entries = Concurrent.newMap();

        // Search parent first, since it's the order ClassLoader#loadClass() uses.
        ClassLoader parent = classloader.getParent();
        if (parent != null)
            entries.putAll(getClassPathEntries(parent));

        for (URL url : getClassLoaderUrls(classloader)) {
            if (url.getProtocol().equals("file")) {
                File file = toFile(url);

                if (!entries.containsKey(file))
                    entries.put(file, classloader);
            }
        }

        return entries.toUnmodifiableMap();
    }

    /**
     * Returns the URLs from which the given class loader loads classes and resources.
     * <p>
     * For {@link URLClassLoader} instances, returns the URLs directly. For the system
     * class loader, returns the Java class-path entries. Otherwise returns an empty list.
     *
     * @param classLoader the class loader to inspect
     * @return the list of URLs, unmodifiable
     */
    public static @NotNull ConcurrentList<URL> getClassLoaderUrls(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader)
            return Concurrent.newUnmodifiableList(((URLClassLoader) classLoader).getURLs());

        if (classLoader.equals(ClassLoader.getSystemClassLoader()))
            return Reflection.getJavaClassPath();

        return Concurrent.newUnmodifiableList();
    }

    /**
     * Returns the class path URIs specified by the {@code Class-Path} manifest attribute, according
     * to <a
     * href="http://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Main_Attributes">JAR
     * File Specification</a>. If {@code manifest} is null, it means the jar file has no manifest, and
     * an empty set will be returned.
     */
    public static @NotNull ConcurrentSet<File> getClassPathFromManifest(File jarFile, @NotNull Manifest manifest) {
        ConcurrentSet<File> builder = Concurrent.newSet();
        String classpathAttribute = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH.toString());
        if (classpathAttribute != null) {
            for (String path : StringUtil.split(classpathAttribute, " ")) {
                URL url;
                try {
                    url = getClassPathEntry(jarFile, path);
                } catch (MalformedURLException e) {
                    // Ignore bad entry
                    //logger.warning("Invalid Class-Path entry: " + path);
                    continue;
                }
                if (url.getProtocol().equals("file")) {
                    builder.add(toFile(url));
                }
            }
        }
        return builder.toUnmodifiableSet();
    }
    /**
     * Returns the absolute uri of the Class-Path entry value as specified in <a
     * href="http://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Main_Attributes">JAR
     * File Specification</a>. Even though the specification only talks about relative urls, absolute
     * urls are actually supported too (for example, in Maven surefire plugin).
     */
    private static @NotNull URL getClassPathEntry(File jarFile, String path) throws MalformedURLException {
        return new URL(jarFile.toURI().toURL(), path);
    }

    /**
     * Returns all locations that {@link ClassLoader} and parent loaders load classes and resources
     * from. Callers can {@linkplain LocationInfo#scanResources scan} individual locations selectively
     * or even in parallel.
     */
    private static @NotNull ConcurrentList<LocationInfo> getLocationsFromClassLoader(@NotNull ClassLoader classLoader) {
        ConcurrentList<LocationInfo> locationInfos = Concurrent.newList();

        for (Map.Entry<File, ClassLoader> entry : getClassPathEntries(classLoader).entrySet())
            locationInfos.add(new LocationInfo(entry.getKey(), entry.getValue()));

        return locationInfos.toUnmodifiableList();
    }

    public static @NotNull File toFile(@NotNull URL url) {
        Preconditions.checkArgument(url.getProtocol().equals("file"));

        try {
            return new File(url.toURI()); // Accepts escaped characters like %20.
        } catch (URISyntaxException e) { // URL.toURI() doesn't escape chars.
            return new File(url.getPath()); // Accepts non-escaped chars like space.
        }
    }

}
