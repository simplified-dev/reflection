package dev.sbs.api.reflection.info;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.SystemUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.NoSuchElementException;

/**
 * Represents a class path resource that can be either a class file or any other resource file
 * loadable from the class path.
 */
@Getter
public class ResourceInfo extends FileInfo {

    /**
     * Returns the fully qualified name of the resource. Such as "com/mycomp/foo/bar.txt".
     */
    private final @NotNull String resourceName;

    public ResourceInfo(@NotNull File file, @NotNull String resourceName, @NotNull ClassLoader classLoader) {
        super(file, classLoader);
        this.resourceName = resourceName;
    }

    static @NotNull ResourceInfo of(@NotNull File file, @NotNull String resourceName, @NotNull ClassLoader loader) {
        if (resourceName.endsWith(".class"))
            return new ClassInfo(file, resourceName, loader);
        else
            return new ResourceInfo(file, resourceName, loader);
    }

    public final @NotNull String getExtension() {
        return StringUtil.getFileExtension(this.getResourceName());
    }

    /**
     * Returns the url identifying the resource.
     *
     * <p>See {@link ClassLoader#getResource}
     *
     * @throws NoSuchElementException if the resource cannot be loaded through the class loader,
     *                                despite physically existing in the class path.
     */
    public final @NotNull URL getUrl() {
        URL url = this.getClassLoader().getResource(this.getResourceName());

        if (url == null)
            throw new NoSuchElementException(this.getResourceName());

        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ResourceInfo that = (ResourceInfo) o;

        return new EqualsBuilder()
            .append(this.getResourceName(), that.getResourceName())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getResourceName())
            .build();
    }

    public byte[] toBytes() {
        return SystemUtil.readResource(this.getResourceName());
    }

    public @Nullable InputStream toStream() {
        return SystemUtil.getResource(this.getResourceName());
    }

    @Override
    public @NotNull String toString() {
        return this.getResourceName();
    }

}