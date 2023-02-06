package dev.sbs.api.reflection.info;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import lombok.Getter;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.NoSuchElementException;

/**
 * Represents a class path resource that can be either a class file or any other resource file
 * loadable from the class path.
 */
public class ResourceInfo {

    @Getter private final File file;
    /**
     * Returns the fully qualified name of the resource. Such as "com/mycomp/foo/bar.txt".
     */
    @Getter private final String resourceName;

    protected final ClassLoader loader;

    static ResourceInfo of(File file, String resourceName, ClassLoader loader) {
        if (resourceName.endsWith(".class")) {
            return new ClassInfo(file, resourceName, loader);
        } else {
            return new ResourceInfo(file, resourceName, loader);
        }
    }

    ResourceInfo(File file, String resourceName, ClassLoader loader) {
        this.file = Preconditions.checkNotNull(file);
        this.resourceName = Preconditions.checkNotNull(resourceName);
        this.loader = Preconditions.checkNotNull(loader);
    }

    /**
     * Returns the url identifying the resource.
     *
     * <p>See {@link ClassLoader#getResource}
     *
     * @throws NoSuchElementException if the resource cannot be loaded through the class loader,
     *                                despite physically existing in the class path.
     */
    public final URL url() {
        URL url = loader.getResource(resourceName);
        if (url == null) {
            throw new NoSuchElementException(resourceName);
        }
        return url;
    }

    /**
     * Returns a {@link ByteSource} view of the resource from which its bytes can be read.
     *
     * @throws NoSuchElementException if the resource cannot be loaded through the class loader,
     *                                despite physically existing in the class path.
     */
    public final ByteSource asByteSource() {
        return Resources.asByteSource(url());
    }

    /**
     * Returns a {@link CharSource} view of the resource from which its bytes can be read as
     * characters decoded with the given {@code charset}.
     *
     * @throws NoSuchElementException if the resource cannot be loaded through the class loader,
     *                                despite physically existing in the class path.
     */
    public final CharSource asCharSource(Charset charset) {
        return Resources.asCharSource(url(), charset);
    }

    @Override
    public int hashCode() {
        return resourceName.hashCode();
    }

    @Override
    public boolean equals(@CheckForNull Object obj) {
        if (obj instanceof ResourceInfo) {
            ResourceInfo that = (ResourceInfo) obj;
            return resourceName.equals(that.resourceName) && loader == that.loader;
        }
        return false;
    }

    @Override
    public String toString() {
        return resourceName;
    }

}