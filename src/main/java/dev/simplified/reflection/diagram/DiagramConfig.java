package dev.simplified.reflection.diagram;

import dev.simplified.collection.concurrent.Concurrent;
import dev.simplified.collection.concurrent.ConcurrentMap;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Immutable configuration for producing a {@link TypeHierarchyDiagram}.
 *
 * <p>
 * Use {@link #builder()} to obtain a {@link Builder}, configure the diagram parameters,
 * then call {@link Builder#build()} to create the config. Call {@link #render()} on the
 * resulting config to discover types, run ELK layout, and produce the SVG.
 *
 * @see TypeHierarchyDiagram
 * @see Builder
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DiagramConfig {

    /** The relative path from a base directory to the output directory. */
    private final @NotNull Path docFilesPath;

    /** The SVG file name (always includes the {@code .svg} extension). */
    private final @NotNull String fileName;

    /** The suffix stripped from type simple names for display. */
    private final @NotNull String suffix;

    /** The class whose package is scanned for subtypes. */
    private final @NotNull Class<?> scanPackage;

    /** The root types whose hierarchies are discovered and included as nodes. */
    private final @NotNull Class<?>[] roots;

    /** The predicate that filters discovered types before layout. */
    private final @NotNull Predicate<Class<?>> typeFilter;

    /** The ELK layering options applied to the layout algorithm. */
    private final @NotNull ConcurrentMap<LayeringOption, Object> layeringOptions;

    /**
     * Discovers types, runs ELK layout, and renders the SVG diagram from this configuration.
     *
     * @return the rendered diagram
     */
    public @NotNull TypeHierarchyDiagram render() {
        return TypeHierarchyDiagram.render(this);
    }

    /**
     * Returns a new {@link Builder} with default values.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new {@link Builder} pre-filled with the values from the given config.
     *
     * @param config the config to copy values from
     * @return a pre-filled builder
     */
    public static @NotNull Builder from(@NotNull DiagramConfig config) {
        return builder()
            .withDocFilesPath(config.getDocFilesPath())
            .withFileName(config.getFileName())
            .withSuffix(config.getSuffix())
            .withScanPackage(config.getScanPackage())
            .withRoots(config.getRoots())
            .withTypeFilter(config.getTypeFilter())
            .withLayeringOptions(config.getLayeringOptions());
    }

    /**
     * Returns a new {@link Builder} pre-filled with this config's values for modification.
     *
     * @return a pre-filled builder
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * Mutable builder for {@link DiagramConfig}.
     *
     * <p>
     * At minimum, callers must set {@link #withDocFilesPath}, {@link #withFileName},
     * {@link #withSuffix}, {@link #withScanPackage}, and {@link #withRoots}. The optional
     * {@link #withTypeFilter} and {@link #withLayeringOption} methods allow fine-grained
     * control over which types appear and how ELK arranges them.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {

        @BuildFlag(nonNull = true)
        private Path docFilesPath;

        @BuildFlag(nonNull = true)
        private String fileName;

        @BuildFlag(nonNull = true)
        private String suffix;

        @BuildFlag(nonNull = true)
        private Class<?> scanPackage;

        @BuildFlag(nonNull = true, notEmpty = true)
        private Class<?>[] roots;

        @BuildFlag(nonNull = true)
        private Predicate<Class<?>> typeFilter = cls -> true;

        @BuildFlag(nonNull = true)
        private ConcurrentMap<LayeringOption, Object> layeringOptions = Concurrent.newMap();

        /**
         * Sets the relative path from a base directory to the {@code doc-files/} output directory.
         *
         * @param docFilesPath the relative path (e.g. {@code Path.of("context/doc-files")})
         * @return this builder for chaining
         */
        public @NotNull Builder withDocFilesPath(@NotNull Path docFilesPath) {
            this.docFilesPath = docFilesPath;
            return this;
        }

        /**
         * Sets the output SVG file name. If the name does not end with {@code ".svg"}, the
         * extension is appended automatically.
         *
         * @param fileName the file name (e.g. {@code "ContextDiagram"} or {@code "ContextDiagram.svg"})
         * @return this builder for chaining
         */
        public @NotNull Builder withFileName(@NotNull String fileName) {
            this.fileName = fileName.endsWith(".svg") ? fileName : fileName + ".svg";
            return this;
        }

        /**
         * Sets the suffix stripped from type simple names for display.
         *
         * @param suffix the suffix to strip (e.g. {@code "Context"}, {@code "Component"})
         * @return this builder for chaining
         */
        public @NotNull Builder withSuffix(@NotNull String suffix) {
            this.suffix = suffix;
            return this;
        }

        /**
         * Sets the class whose package is scanned for subtypes.
         *
         * @param scanPackage a class in the root package to scan
         * @return this builder for chaining
         */
        public @NotNull Builder withScanPackage(@NotNull Class<?> scanPackage) {
            this.scanPackage = scanPackage;
            return this;
        }

        /**
         * Sets the root types whose hierarchies are discovered and included as nodes.
         *
         * @param roots one or more root types
         * @return this builder for chaining
         */
        public @NotNull Builder withRoots(@NotNull Class<?>... roots) {
            this.roots = roots;
            return this;
        }

        /**
         * Sets a predicate that filters discovered types before layout. Types for which the
         * predicate returns {@code false} are excluded from the diagram.
         *
         * @param typeFilter the filter predicate
         * @return this builder for chaining
         */
        public @NotNull Builder withTypeFilter(@NotNull Predicate<Class<?>> typeFilter) {
            this.typeFilter = typeFilter;
            return this;
        }

        /**
         * Sets a single ELK layering option that controls the layout algorithm's behavior.
         *
         * @param option the layering option key
         * @param value the option value (must match the option's {@link LayeringOption#getValueType()})
         * @return this builder for chaining
         */
        public @NotNull Builder withLayeringOption(@NotNull LayeringOption option, @NotNull Object value) {
            this.layeringOptions.put(option, value);
            return this;
        }

        /**
         * Replaces all layering options with the given map.
         *
         * @param layeringOptions the layering options map
         * @return this builder for chaining
         */
        @NotNull Builder withLayeringOptions(@NotNull ConcurrentMap<LayeringOption, Object> layeringOptions) {
            this.layeringOptions = Concurrent.newMap(layeringOptions);
            return this;
        }

        public @NotNull DiagramConfig build() {
            Reflection.validateFlags(this);

            return new DiagramConfig(
                this.docFilesPath,
                this.fileName,
                this.suffix,
                this.scanPackage,
                this.roots,
                this.typeFilter,
                this.layeringOptions
            );
        }
    }

    /**
     * Layering strategy for the ELK layout algorithm.
     *
     * @see LayeringOption#STRATEGY
     */
    @Getter
    @RequiredArgsConstructor
    public enum LayeringStrategy {

        NETWORK_SIMPLEX,
        LONGEST_PATH,
        LONGEST_PATH_SOURCE,
        COFFMAN_GRAHAM,
        INTERACTIVE,
        STRETCH_WIDTH,
        MIN_WIDTH,
        BF_MODEL_ORDER,
        DF_MODEL_ORDER

    }

    /**
     * Layering-specific property keys mirroring ELK's layered-algorithm layering properties.
     */
    @Getter
    @RequiredArgsConstructor
    public enum LayeringOption {

        /** The layering strategy to use. */
        STRATEGY(LayeringStrategy.class),
        /** Upper bound on width for the {@link LayeringStrategy#MIN_WIDTH} strategy. */
        MIN_WIDTH_UPPER_BOUND_ON_WIDTH(Integer.class),
        /** Scaling factor for upper layer estimation in the {@link LayeringStrategy#MIN_WIDTH} strategy. */
        MIN_WIDTH_UPPER_LAYER_ESTIMATION_SCALING_FACTOR(Integer.class),
        /** Maximum iterations for node promotion. */
        NODE_PROMOTION_MAX_ITERATIONS(Integer.class),
        /** Layer bound for the {@link LayeringStrategy#COFFMAN_GRAHAM} strategy. */
        COFFMAN_GRAHAM_LAYER_BOUND(Integer.class);

        /** The expected value type for this option. */
        private final @NotNull Class<?> valueType;

    }

}
