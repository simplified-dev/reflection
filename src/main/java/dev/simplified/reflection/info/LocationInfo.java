package dev.simplified.reflection.info;

import dev.simplified.collection.concurrent.Concurrent;
import dev.simplified.collection.concurrent.ConcurrentSet;
import dev.simplified.reflection.accessor.ResourceAccessor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Represents a single location (a directory or a jar file) in the class path and is responsible
 * for scanning resources from this location.
 */
@Getter
public class LocationInfo extends FileInfo {

    public LocationInfo(@NotNull File file, @NotNull ClassLoader classloader) {
        super(file, classloader);
    }

    /**
     * Scans this location and returns all scanned resources.
     */
    public @NotNull ConcurrentSet<ResourceInfo> scanResources() {
        return scanResources(Concurrent.newSet());
    }

    /**
     * Scans this location and returns all scanned resources.
     *
     * <p>This file and jar files from "Class-Path" entry in the scanned manifest files will be
     * added to {@code scannedFiles}.
     *
     * <p>A file will be scanned at most once even if specified multiple times by one or multiple
     * jar files' "Class-Path" manifest entries. Particularly, if a jar file from the "Class-Path"
     * manifest entry is already in {@code scannedFiles}, either because it was scanned earlier, or
     * it was intentionally added to the set by the caller, it will not be scanned again.
     *
     * <p>Note that when you call {@code location.scanResources(scannedFiles)}, the location will
     * always be scanned even if {@code scannedFiles} already contains it.
     */
    public @NotNull ConcurrentSet<ResourceInfo> scanResources(@NotNull ConcurrentSet<File> scannedFiles) {
        ConcurrentSet<ResourceInfo> builder = Concurrent.newSet();
        scannedFiles.add(this.getFile());
        this.scan(this.getFile(), scannedFiles, builder);
        return builder.toUnmodifiableSet();
    }

    private void scan(@NotNull File file, @NotNull ConcurrentSet<File> scannedUris, @NotNull ConcurrentSet<ResourceInfo> builder) {
        try {
            if (!file.exists())
                return;
        } catch (SecurityException e) {
            //logger.warning("Cannot access " + file + ": " + e);
            return;
        }

        if (file.isDirectory())
            this.scanDirectory(file, builder);
        else
            this.scanJar(file, scannedUris, builder);
    }

    private void scanJar(@NotNull File file, @NotNull ConcurrentSet<File> scannedUris, @NotNull ConcurrentSet<ResourceInfo> builder) {
        JarFile jarFile;

        try {
            jarFile = new JarFile(file);
        } catch (IOException e) {
            // Not a jar file
            return;
        }

        try {
            for (File path : ResourceAccessor.getClassPathFromManifest(file, jarFile.getManifest())) {
                // Only scan each file once independent of the classloader that file might be associated with.
                if (scannedUris.add(path.getCanonicalFile())) {
                    this.scan(path, scannedUris, builder);
                }
            }
            this.scanJarFile(jarFile, builder);
        } catch (Exception ignore) {
        } finally {
            try {
                jarFile.close();
            } catch (IOException ignored) { // similar to try-with-resources, but don't fail scanning
            }
        }
    }

    private void scanJarFile(@NotNull JarFile file, @NotNull ConcurrentSet<ResourceInfo> builder) {
        Enumeration<JarEntry> entries = file.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            if (entry.isDirectory() || entry.getName().equals(JarFile.MANIFEST_NAME))
                continue;

            builder.add(ResourceInfo.of(new File(file.getName()), entry.getName(), this.getClassLoader()));
        }
    }

    private void scanDirectory(@NotNull File directory, @NotNull ConcurrentSet<ResourceInfo> builder) {
        try {
            ConcurrentSet<File> currentPath = Concurrent.newSet();
            currentPath.add(directory.getCanonicalFile());
            scanDirectory(directory, "", currentPath, builder);
        } catch (IOException ignore) { }
    }

    /**
     * Recursively scan the given directory, adding resources for each file encountered. Symlinks
     * which have already been traversed in the current tree path will be skipped to eliminate
     * cycles; otherwise symlinks are traversed.
     *
     * @param directory the root of the directory to scan
     * @param packagePrefix resource path prefix inside {@code classloader} for any files found
     *     under {@code directory}
     * @param currentPath canonical files already visited in the current directory tree path, for
     *     cycle elimination
     */
    private void scanDirectory(@NotNull File directory, @NotNull String packagePrefix, @NotNull ConcurrentSet<File> currentPath, @NotNull ConcurrentSet<ResourceInfo> builder) throws IOException {
        File[] files = directory.listFiles();

        if (files == null) {
            // IO error, just skip the directory
            return;
        }

        for (File f : files) {
            String name = f.getName();

            if (f.isDirectory()) {
                File deref = f.getCanonicalFile();

                if (currentPath.add(deref)) {
                    scanDirectory(deref, packagePrefix + name + "/", currentPath, builder);
                    currentPath.remove(deref);
                }
            } else {
                String resourceName = packagePrefix + name;

                if (!resourceName.equals(JarFile.MANIFEST_NAME))
                    builder.add(ResourceInfo.of(f, resourceName, this.getClassLoader()));
            }
        }
    }

}