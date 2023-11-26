package dev.sbs.api.reflection.accessor;

import com.google.common.base.Preconditions;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.reflection.info.ClassInfo;
import dev.sbs.api.reflection.info.LocationInfo;
import dev.sbs.api.reflection.info.ResourceInfo;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.helper.StringUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

@Getter
public class ResourceAccessor {

    private final @NotNull ClassLoader classLoader;
    private final @NotNull ConcurrentList<ResourceInfo> resources;

    public ResourceAccessor(@NotNull ClassLoader classLoader) {
        this(classLoader, null);
    }

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
            .collect(Concurrent.toList())
            .toUnmodifiableList();
    }

    private ResourceAccessor(@NotNull ClassLoader classLoader, @NotNull ConcurrentList<ResourceInfo> resources, @NotNull String packageName) {
        this.classLoader = classLoader;
        String resourceName = StringUtil.stripToEmpty(packageName).replace(".", "/").toLowerCase();

        this.resources = resources.stream()
            .filter(resourceInfo -> resourceInfo.getResourceName().startsWith(resourceName))
            .collect(Concurrent.toList())
            .toUnmodifiableList();
    }

    public @NotNull ResourceAccessor filterPackage(@NotNull Class<?> type) {
        return this.filterPackage(Reflection.getPackageName(type));
    }

    public @NotNull ResourceAccessor filterPackage(@NotNull String packageName) {
        return new ResourceAccessor(this.getClassLoader(), this.getResources(), packageName);
    }

    public @NotNull ConcurrentList<ClassInfo> getClasses() {
        return this.getResources()
            .stream()
            .filter(resourceInfo -> resourceInfo instanceof ClassInfo)
            .map(ClassInfo.class::cast)
            .collect(Concurrent.toList())
            .toUnmodifiableList();
    }

    @SuppressWarnings("unchecked")
    public <T> @NotNull ConcurrentList<Class<T>> getSubtypesOf(@NotNull Class<T> type) {
        return this.getClasses()
            .stream()
            .map(ClassInfo::load)
            .filter(storedType -> !storedType.equals(type))
            .filter(type::isAssignableFrom)
            .map(storedType -> (Class<T>) storedType)
            .collect(Concurrent.toList())
            .toUnmodifiableList();
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
    public static @NotNull ConcurrentSet<File> getClassPathFromManifest(File jarFile, @CheckForNull Manifest manifest) {
        if (manifest == null)
            return Concurrent.newSet();

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

    public static @NotNull File toFile(URL url) {
        Preconditions.checkArgument(url.getProtocol().equals("file"));

        try {
            return new File(url.toURI()); // Accepts escaped characters like %20.
        } catch (URISyntaxException e) { // URL.toURI() doesn't escape chars.
            return new File(url.getPath()); // Accepts non-escaped chars like space.
        }
    }

}
