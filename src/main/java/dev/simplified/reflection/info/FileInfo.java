package dev.sbs.api.reflection.info;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

/**
 * Represents a file resource that can be either a class file or any other resource file
 * loadable from the class path.
 */
@RequiredArgsConstructor
public abstract class FileInfo {

    @Getter private final @NotNull File file;
    @Getter(AccessLevel.PROTECTED)
    private final @NotNull ClassLoader classLoader;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileInfo fileInfo = (FileInfo) o;

        return Objects.equals(this.getFile(), fileInfo.getFile())
            && Objects.equals(this.getClassLoader(), fileInfo.getClassLoader());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getFile(), this.getClassLoader());
    }

    @Override
    public @NotNull String toString() {
        return this.getFile().toString();
    }

}