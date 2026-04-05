package dev.simplified.reflection.info;

import dev.simplified.reflection.Reflection;
import dev.simplified.util.RegexUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@Getter
public class ClassInfo extends ResourceInfo {

    private final @NotNull String className;

    public ClassInfo(@NotNull File file, @NotNull String resourceName, @NotNull ClassLoader loader) {
        super(file, resourceName, loader);
        this.className = Reflection.getName(resourceName);
    }

    /**
     * Returns the package name of the class, without attempting to load the class.
     *
     * <p>Behaves similarly to {@code class.getPackage().}{@link Package#getName() getName()} but
     * does not require the class (or package) to be loaded.
     *
     * <p>But note that this method may behave differently for a class in the default package: For
     * such classes, this method always returns an empty string. But under some version of Java,
     * {@code class.getPackage().getName()} produces a {@code NullPointerException} because {@code
     * class.getPackage()} returns {@code null}.
     */
    public String getPackageName() {
        return Reflection.getPackageName(this.getClassName());
    }

    /**
     * Returns the simple name of the underlying class as given in the source code.
     *
     * <p>Behaves similarly to {@link Class#getSimpleName()} but does not require the class to be
     * loaded.
     *
     * @apiNote This class uses heuristics to identify the simple name.
     */
    public String getSimpleName() {
        int lastDollarSign = this.getClassName().lastIndexOf('$');

        if (lastDollarSign != -1) {
            String innerClassName = this.getClassName().substring(lastDollarSign + 1);
            // local and anonymous classes are prefixed with number (1,2,3...), anonymous classes are
            // entirely numeric whereas local classes have the user supplied name as a suffix

            return RegexUtil.replaceFirst(innerClassName, "^[0-9]+", "");
        }

        String packageName = this.getPackageName();
        if (packageName.isEmpty())
            return this.getClassName();

        // Since this is a top level class, its simple name is always the part after package name.
        return this.getClassName().substring(packageName.length() + 1);
    }

    /**
     * Returns true if the class name "looks to be" top level (not nested), that is, it includes no
     * '$' in the name. This method may return false for a top-level class that's intentionally
     * named with the '$' character. If this is a concern, you could use {@link #load} and then
     * check on the loaded {@link Class} object instead.
     */
    public boolean isTopLevel() {
        return this.getClassName().indexOf('$') == -1;
    }

    /**
     * Loads (but doesn't link or initialize) the class.
     *
     * @throws LinkageError when there were errors in loading classes that this class depends on.
     *                      For example, {@link NoClassDefFoundError}.
     */
    public @NotNull Class<?> load() {
        try {
            return this.getClassLoader().loadClass(this.getClassName());
        } catch (ClassNotFoundException e) {
            // Shouldn't happen, since the class name is read from the class path.
            throw new IllegalStateException(e);
        }
    }

    @Override
    public @NotNull String toString() {
        return this.getClassName();
    }

}
