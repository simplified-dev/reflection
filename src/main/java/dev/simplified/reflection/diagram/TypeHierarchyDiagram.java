package dev.simplified.reflection.diagram;

import dev.simplified.collection.concurrent.Concurrent;
import dev.simplified.collection.concurrent.ConcurrentList;
import dev.simplified.reflection.Reflection;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.LayeringStrategy;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A rendered SVG type-hierarchy diagram styled after IntelliJ IDEA's Darcula UML class diagrams.
 *
 * <p>
 * Instances are produced by {@link DiagramConfig#render()} and wrap the complete SVG markup.
 * The rendering pipeline discovers types via classpath reflection, computes a layered graph
 * layout using Eclipse Layout Kernel (ELK), and renders the result as self-contained SVG with
 * IntelliJ ExpUI dark-mode type icons, orthogonal edge routing, rounded corners, and arc-jump
 * crossings.
 *
 * <p>
 * <b>Edge conventions:</b>
 * <ul>
 *   <li><b>Solid green lines</b> - the first (primary) parent interface of each type</li>
 *   <li><b>Dashed dark-green lines</b> - additional (secondary) parent interfaces</li>
 *   <li><b>Arc jumps</b> - horizontal segments arc over crossing vertical segments to
 *       preserve visual clarity; arcs are rendered as separate overlay paths above all edges</li>
 * </ul>
 *
 * <p>
 * <b>Node rendering:</b> each node is a rounded rectangle containing a 16x16 IntelliJ ExpUI
 * dark-mode type icon (interface, class, enum, record, abstract class, annotation, or exception)
 * followed by the type's simple name with an optional suffix stripped.
 *
 * @see DiagramConfig
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TypeHierarchyDiagram {

    // ======== Darcula Palette ========

    private static final String BOX_FILL   = "#3C3F41";
    private static final String BOX_STROKE = "#515658";
    private static final String TEXT_COLOR  = "#BBBBBB";
    private static final String LINE_COLOR  = "#609350";
    private static final String DASH_LINE   = "#3A5930";

    // ======== Layout Constants ========

    private static final int BOX_H = 26;
    private static final int V_GAP = 36;
    private static final int H_GAP = 14;
    private static final int MARGIN = 24;
    private static final int ARROW_H = 8;
    private static final int ARROW_W = 10;
    private static final int CORNER_R = 4;
    private static final int JUMP_R = 4;

    // ======== Icon/Text Sizing ========

    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 3;
    private static final int PAD_L = 4;
    private static final int PAD_R = 6;
    private static final double CHAR_W = 6.05;

    static {
        // Register ELK's layered algorithm at class-load time.
        LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(new LayeredOptions());
    }

    /**
     * Intermediate representation of a single edge's geometry after ELK layout.
     *
     * @param points the waypoint coordinates (start, bends, end) with arrowhead adjustment applied
     * @param segments the consecutive point pairs forming straight-line segments
     * @param dashed whether this edge represents a secondary (dashed) parent relationship
     * @param tipX the x-coordinate of the arrowhead tip
     * @param tipY the y-coordinate of the arrowhead tip
     */
    private record EdgeRenderData(double[][] points, List<double[]> segments, boolean dashed, double tipX, double tipY) {}

    /** The relative path from a base directory to the output directory. */
    private final @NotNull Path docFilesPath;

    /** The SVG file name (always includes the {@code .svg} extension). */
    private final @NotNull String fileName;

    /** The rendered SVG markup. */
    private final @NotNull String svg;

    /**
     * Writes the SVG to the configured {@code doc-files/} directory under the given base path,
     * creating parent directories as needed.
     *
     * @param basePath the base directory (e.g. {@code Path.of("src/main/java/dev/sbs/discordapi")})
     * @throws IOException if a file write fails
     */
    public void writeTo(@NotNull Path basePath) throws IOException {
        Path dir = basePath.resolve(this.docFilesPath);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(this.fileName), this.svg);
    }

    /** Returns the SVG markup. */
    @Override
    public @NotNull String toString() {
        return this.svg;
    }

    // ======== Engine Entry Point ========

    /**
     * Discovers types, runs ELK layout, and renders the SVG diagram.
     *
     * @param config the diagram configuration
     * @return a new diagram wrapping the rendered SVG
     */
    static @NotNull TypeHierarchyDiagram render(@NotNull DiagramConfig config) {
        ConcurrentList<Class<?>> types = discoverHierarchy(config.getScanPackage(), config.getRoots());
        types.removeIf(cls -> !config.getTypeFilter().test(cls));
        String svgString = buildDiagram(config.getSuffix(), new ArrayList<>(types), config.getLayeringOptions());
        return new TypeHierarchyDiagram(config.getDocFilesPath(), config.getFileName(), svgString);
    }

    // ======== Discovery ========

    @SuppressWarnings("unchecked")
    private static ConcurrentList<Class<?>> discoverHierarchy(Class<?> scanPackage, Class<?>... roots) {
        ConcurrentList<Class<?>> result = Concurrent.newList();
        var accessor = Reflection.getResources().filterPackage(scanPackage);

        for (Class<?> root : roots) {
            if (!result.contains(root))
                result.add(root);

            accessor.getSubtypesOf((Class<Object>) root)
                .stream()
                .filter(cls -> !cls.isMemberClass())
                .filter(cls -> !cls.isEnum())
                .filter(cls -> cls.isInterface() || !Modifier.isAbstract(cls.getModifiers()))
                .filter(cls -> !result.contains(cls))
                .forEach(result::add);
        }

        return result;
    }

    // ======== Layout Engine ========

    private static String buildDiagram(String suffix, List<Class<?>> allTypes,
                                       Map<DiagramConfig.LayeringOption, Object> layeringOptions) {
        int n = allTypes.size();

        // Build edges: first parent = solid, additional parents = dashed
        var solidEdges = new ArrayList<int[]>();
        var dashedEdges = new ArrayList<int[]>();

        for (int i = 0; i < n; i++) {
            Class<?>[] parents = allTypes.get(i).getInterfaces();
            boolean first = true;
            for (Class<?> parent : parents) {
                int pIdx = allTypes.indexOf(parent);
                if (pIdx < 0) continue;
                if (first) {
                    solidEdges.add(new int[]{pIdx, i});
                    first = false;
                } else {
                    dashedEdges.add(new int[]{pIdx, i});
                }
            }
        }

        // Per-node box width (fitted to icon + label text)
        int iconSpace = PAD_L + ICON_SIZE + ICON_GAP;
        int[] nw = new int[n];
        for (int i = 0; i < n; i++) {
            String label = displayName(allTypes.get(i), suffix);
            nw[i] = Math.max(60, iconSpace + (int) (label.length() * CHAR_W) + PAD_R);
        }

        // Build ELK graph
        ElkNode graph = ElkGraphUtil.createGraph();
        graph.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
        graph.setProperty(CoreOptions.DIRECTION, Direction.DOWN);
        graph.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
        graph.setProperty(CoreOptions.SPACING_NODE_NODE, (double) H_GAP);
        graph.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, (double) V_GAP);
        graph.setProperty(CoreOptions.PADDING, new ElkPadding(MARGIN));

        // Apply caller-supplied layering options
        applyLayeringOptions(graph, layeringOptions);

        ElkNode[] elkNodes = new ElkNode[n];
        for (int i = 0; i < n; i++) {
            elkNodes[i] = ElkGraphUtil.createNode(graph);
            elkNodes[i].setWidth(nw[i]);
            elkNodes[i].setHeight(BOX_H);
        }

        // Create ELK edges, tracking which are dashed for rendering
        Set<ElkEdge> dashedEdgeSet = new HashSet<>();
        for (int[] e : solidEdges)
            ElkGraphUtil.createSimpleEdge(elkNodes[e[0]], elkNodes[e[1]]);
        for (int[] e : dashedEdges)
            dashedEdgeSet.add(ElkGraphUtil.createSimpleEdge(elkNodes[e[0]], elkNodes[e[1]]));

        // Run ELK layout
        new RecursiveGraphLayoutEngine().layout(graph, new BasicProgressMonitor());

        // Read computed positions
        double[] nx = new double[n], ny = new double[n];
        for (int i = 0; i < n; i++) {
            nx[i] = elkNodes[i].getX();
            ny[i] = elkNodes[i].getY();
        }
        int svgW = (int) Math.ceil(graph.getWidth());
        int svgH = (int) Math.ceil(graph.getHeight());

        // ======== Render SVG ========
        var svg = new StringBuilder();
        writeHeader(svg, svgW, svgH);

        // Pass 1: Collect edge geometry from ELK layout results
        var edgeData = new ArrayList<EdgeRenderData>();
        for (ElkEdge edge : graph.getContainedEdges()) {
            boolean dashed = dashedEdgeSet.contains(edge);
            ElkEdgeSection section = edge.getSections().getFirst();

            var points = new ArrayList<double[]>();
            points.add(new double[]{section.getStartX(), section.getStartY()});
            for (var bp : section.getBendPoints())
                points.add(new double[]{bp.getX(), bp.getY()});
            points.add(new double[]{section.getEndX(), section.getEndY()});

            // Shorten the last segment to leave room for the arrowhead
            double tipX = section.getEndX();
            double tipY = section.getEndY();
            int arrowH = dashed ? ARROW_H - 2 : ARROW_H;
            double[] last = points.getLast();
            points.set(points.size() - 1, new double[]{last[0], last[1] - arrowH});

            double[][] pts = filterClosePoints(points.toArray(double[][]::new));

            var segments = new ArrayList<double[]>();
            for (int i = 0; i < pts.length - 1; i++)
                segments.add(new double[]{pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]});

            edgeData.add(new EdgeRenderData(pts, segments, dashed, tipX, tipY));
        }

        // Flatten all segments with owner tracking for crossing detection
        var allSegments = new ArrayList<double[]>();
        var segmentOwner = new ArrayList<Integer>();
        for (int e = 0; e < edgeData.size(); e++)
            for (double[] seg : edgeData.get(e).segments()) {
                allSegments.add(seg);
                segmentOwner.add(e);
            }

        var dashedArcs = new ArrayList<double[]>();
        var solidArcs = new ArrayList<double[]>();

        // Pass 2: Render dashed edges first (beneath solid edges)
        for (int e = 0; e < edgeData.size(); e++) {
            if (!edgeData.get(e).dashed()) continue;
            var others = otherSegments(e, allSegments, segmentOwner);
            renderEdge(svg, edgeData.get(e).points(), true, others, dashedArcs);
            var ed = edgeData.get(e);
            arrow(svg, ed.tipX(), ed.tipY(), DASH_LINE, ARROW_H - 2, ARROW_W - 3);
        }

        // Pass 3: Render solid edges on top
        for (int e = 0; e < edgeData.size(); e++) {
            if (edgeData.get(e).dashed()) continue;
            var others = otherSegments(e, allSegments, segmentOwner);
            renderEdge(svg, edgeData.get(e).points(), false, others, solidArcs);
            var ed = edgeData.get(e);
            arrow(svg, ed.tipX(), ed.tipY(), LINE_COLOR, ARROW_H, ARROW_W);
        }

        // Pass 4: Arc overlays above all lines (so they visually "bridge" crossings)
        for (double[] arc : dashedArcs) writeArc(svg, arc, "da");
        for (double[] arc : solidArcs) writeArc(svg, arc, "sa");

        // Pass 5: Node boxes with type icons on top of everything
        for (int i = 0; i < n; i++)
            writeNode(svg, nx[i], ny[i], nw[i], displayName(allTypes.get(i), suffix), allTypes.get(i));

        svg.append("</svg>\n");
        return svg.toString();
    }

    // ======== Layering Option Mapping ========

    private static void applyLayeringOptions(ElkNode graph,
                                             Map<DiagramConfig.LayeringOption, Object> options) {
        for (var entry : options.entrySet()) {
            switch (entry.getKey()) {
                case STRATEGY -> {
                    var strategy = (DiagramConfig.LayeringStrategy) entry.getValue();
                    graph.setProperty(LayeredOptions.LAYERING_STRATEGY,
                        LayeringStrategy.valueOf(strategy.name()));
                }
                case MIN_WIDTH_UPPER_BOUND_ON_WIDTH ->
                    graph.setProperty(LayeredOptions.LAYERING_MIN_WIDTH_UPPER_BOUND_ON_WIDTH,
                        (Integer) entry.getValue());
                case MIN_WIDTH_UPPER_LAYER_ESTIMATION_SCALING_FACTOR ->
                    graph.setProperty(LayeredOptions.LAYERING_MIN_WIDTH_UPPER_LAYER_ESTIMATION_SCALING_FACTOR,
                        (Integer) entry.getValue());
                case NODE_PROMOTION_MAX_ITERATIONS ->
                    graph.setProperty(LayeredOptions.LAYERING_NODE_PROMOTION_MAX_ITERATIONS,
                        (Integer) entry.getValue());
                case COFFMAN_GRAHAM_LAYER_BOUND ->
                    graph.setProperty(LayeredOptions.LAYERING_COFFMAN_GRAHAM_LAYER_BOUND,
                        (Integer) entry.getValue());
            }
        }
    }

    // ======== Edge Rendering ========

    private static void renderEdge(StringBuilder svg, double[][] pts, boolean dashed,
                                   List<double[]> otherSegments, List<double[]> arcCollector) {
        if (pts.length < 2) return;

        var d = new StringBuilder();
        d.append("M%.1f,%.1f".formatted(pts[0][0], pts[0][1]));
        double curX = pts[0][0], curY = pts[0][1];

        for (int i = 1; i < pts.length; i++) {
            double tx = pts[i][0], ty = pts[i][1];

            if (i < pts.length - 1) {
                double[] next = pts[i + 1];
                double dx1 = tx - curX, dy1 = ty - curY;
                double dx2 = next[0] - tx, dy2 = next[1] - ty;
                double len1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
                double len2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
                double r = Math.min(CORNER_R, Math.min(len1 / 2.0, len2 / 2.0));

                double bx = tx - (dx1 / len1) * r;
                double by = ty - (dy1 / len1) * r;
                double ax = tx + (dx2 / len2) * r;
                double ay = ty + (dy2 / len2) * r;

                appendSegmentWithJumps(d, curX, curY, bx, by, otherSegments, arcCollector);
                d.append(" Q%.1f,%.1f %.1f,%.1f".formatted(tx, ty, ax, ay));
                curX = ax;
                curY = ay;
            } else {
                appendSegmentWithJumps(d, curX, curY, tx, ty, otherSegments, arcCollector);
            }
        }

        svg.append("  <path class=\"%s\" d=\"%s\"/>\n".formatted(dashed ? "d" : "e", d));
    }

    private static void appendSegmentWithJumps(StringBuilder d, double x1, double y1, double x2, double y2,
                                               List<double[]> crossSegments,
                                               List<double[]> arcCollector) {
        boolean isHoriz = Math.abs(y1 - y2) < 0.5;

        if (!isHoriz) {
            d.append(" L%.1f,%.1f".formatted(x2, y2));
            return;
        }

        var crossings = new ArrayList<Double>();
        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        for (double[] seg : crossSegments) {
            if (Math.abs(seg[0] - seg[2]) < 0.5) { // vertical segment
                double sx = seg[0];
                double sMinY = Math.min(seg[1], seg[3]), sMaxY = Math.max(seg[1], seg[3]);
                if (sx > minX + 0.5 && sx < maxX - 0.5
                    && y1 > sMinY - 1 && y1 < sMaxY + 1)
                    crossings.add(sx);
            }
        }

        // Deduplicate and sort in travel direction
        var unique = crossings.stream().distinct().sorted().toList();
        if (x2 < x1) unique = unique.reversed();

        boolean right = x2 > x1;
        for (double cross : unique) {
            double rawBefore = right ? cross - JUMP_R : cross + JUMP_R;
            double rawAfter  = right ? cross + JUMP_R : cross - JUMP_R;
            double gapBefore = right ? Math.max(rawBefore, x1) : Math.min(rawBefore, x1);
            double gapAfter  = right ? Math.min(rawAfter, x2) : Math.max(rawAfter, x2);
            d.append(" L%.1f,%.1f".formatted(gapBefore, y1));
            d.append(" M%.1f,%.1f".formatted(gapAfter, y1));
            arcCollector.add(new double[]{right ? rawBefore + 1 : rawBefore - 1, y1, right ? rawAfter - 1 : rawAfter + 1, y1, right ? 1 : 0});
        }

        d.append(" L%.1f,%.1f".formatted(x2, y2));
    }

    private static List<double[]> otherSegments(int edgeIndex, List<double[]> allSegments, List<Integer> owners) {
        var result = new ArrayList<double[]>();
        for (int i = 0; i < allSegments.size(); i++)
            if (owners.get(i) != edgeIndex) result.add(allSegments.get(i));
        return result;
    }

    // ======== Helpers ========

    private static double[][] filterClosePoints(double[][] pts) {
        var filtered = new ArrayList<double[]>();
        filtered.add(pts[0]);
        for (int i = 1; i < pts.length; i++) {
            double[] prev = filtered.getLast();
            double dx = pts[i][0] - prev[0], dy = pts[i][1] - prev[1];
            if (Math.sqrt(dx * dx + dy * dy) >= 0.5)
                filtered.add(pts[i]);
        }
        return filtered.toArray(double[][]::new);
    }

    private static void writeArc(StringBuilder svg, double[] arc, String cls) {
        int r = JUMP_R - 1;
        svg.append("  <path class=\"%s\" d=\"M%.1f,%.1f A%d,%d 0 0,%.0f %.1f,%.1f\"/>\n"
            .formatted(cls, arc[0], arc[1], r, r, arc[4], arc[2], arc[3]));
    }

    private static String displayName(Class<?> cls, String suffix) {
        String name = cls.getSimpleName();
        if (name.length() > suffix.length() && name.endsWith(suffix))
            name = name.substring(0, name.length() - suffix.length());
        return name;
    }

    // ======== SVG Primitives ========

    private static void writeHeader(StringBuilder svg, int w, int h) {
        svg.append("""
            <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
              <style>
                .box { fill: %s; stroke: %s; stroke-width: 1; }
                .lbl { font-family: Consolas, monospace; font-size: 11px; fill: %s; }
                .e   { stroke: %s; stroke-width: 1.2; fill: none; }
                .d   { stroke: %s; stroke-width: 1; fill: none; stroke-dasharray: 5,3; }
                .da  { stroke: %s; stroke-width: 1; fill: none; }
                .sa  { stroke: %s; stroke-width: 1.2; fill: none; }
              </style>
            """.formatted(
            w, h, w, h,
            BOX_FILL, BOX_STROKE, TEXT_COLOR,
            LINE_COLOR, DASH_LINE, DASH_LINE, LINE_COLOR
        ));
    }

    private static void writeNode(StringBuilder svg, double x, double y, double w, String label, Class<?> cls) {
        double cy = y + BOX_H / 2.0;

        svg.append("  <rect class=\"box\" x=\"%.0f\" y=\"%.0f\" width=\"%.0f\" height=\"%d\" rx=\"4\" ry=\"4\"/>\n"
            .formatted(x, y, w, BOX_H));

        double iconX = x + PAD_L;
        double iconY = cy - ICON_SIZE / 2.0;
        writeTypeIcon(svg, iconX, iconY, cls);

        double textX = iconX + ICON_SIZE + ICON_GAP;
        svg.append("  <text class=\"lbl\" x=\"%.1f\" y=\"%.1f\">%s</text>\n"
            .formatted(textX, cy + 4, escape(label)));
    }

    // ======== IntelliJ ExpUI Dark Mode Type Icons ========

    private static final String ICON_INTERFACE = """
            <circle cx="8" cy="8" r="6.5" fill="#253627" stroke="#57965C"/>
            <path fill-rule="evenodd" clip-rule="evenodd" d="M10 4.5V5.5L8.5 5.5V10.5H10V11.5L8.5 11.5H7.5L6 11.5V10.5H7.5V5.5L6 5.5V4.5H7.5H8.5H10Z" fill="#57965C"/>""";

    private static final String ICON_CLASS = """
            <circle cx="8" cy="8" r="6.5" fill="#25324D" stroke="#548AF7"/>
            <path d="M8.133 11.5C9.612 11.5 10.884 10.611 11.208 9.339H10.221C9.902 10.074 9.119 10.606 8.133 10.606C6.779 10.606 5.803 9.518 5.803 8C5.803 6.482 6.779 5.394 8.133 5.394C9.119 5.394 9.902 5.926 10.221 6.661H11.208C10.884 5.39 9.612 4.5 8.133 4.5C6.219 4.5 4.792 5.994 4.792 8C4.792 10.006 6.219 11.5 8.133 11.5Z" fill="#548AF7"/>""";

    private static final String ICON_ENUM = """
            <circle cx="8" cy="8" r="6.5" fill="#2F2936" stroke="#A571E6"/>
            <path d="M5.49 11.5H10.51V10.585H6.48V8.435H10.07V7.515H6.48V5.415H10.41V4.5H5.49V11.5Z" fill="#A571E6"/>""";

    private static final String ICON_RECORD = """
            <circle cx="8" cy="8" r="6.5" fill="#25324D" stroke="#548AF7"/>
            <path d="M7.93 8.405H9.025L10.925 11.5H9.775L7.93 8.405ZM5.7 4.5H8.585C9.048 4.5 9.453 4.587 9.8 4.76C10.15 4.93 10.42 5.172 10.61 5.485C10.8 5.795 10.895 6.158 10.895 6.575C10.895 6.988 10.798 7.353 10.605 7.67C10.412 7.987 10.138 8.232 9.785 8.405C9.432 8.575 9.018 8.66 8.545 8.66H6.7V11.5H5.7V4.5ZM8.56 7.77C8.82 7.77 9.047 7.722 9.24 7.625C9.433 7.528 9.583 7.39 9.69 7.21C9.797 7.03 9.85 6.818 9.85 6.575C9.85 6.335 9.797 6.127 9.69 5.95C9.583 5.77 9.433 5.632 9.24 5.535C9.047 5.438 8.82 5.39 8.56 5.39H6.7V7.77H8.56Z" fill="#548AF7"/>""";

    private static final String ICON_ABSTRACT_CLASS = """
            <path d="M12.95 3.05C15.683 5.784 15.683 10.216 12.95 12.95C10.216 15.683 5.784 15.683 3.05 12.95C0.317 10.216 0.317 5.784 3.05 3.05C5.784 0.317 10.216 0.317 12.95 3.05Z" fill="#25324D"/>
            <path fill-rule="evenodd" clip-rule="evenodd" d="M14.914 6.905L13.926 7.06C13.736 5.851 13.176 4.69 12.243 3.757C11.31 2.824 10.149 2.264 8.94 2.074L9.095 1.086C10.506 1.308 11.862 1.963 12.95 3.05C14.037 4.138 14.692 5.494 14.914 6.905ZM6.905 1.086L7.06 2.074C5.851 2.264 4.69 2.824 3.757 3.757C2.824 4.69 2.264 5.851 2.073 7.06L1.086 6.905C1.308 5.494 1.963 4.138 3.05 3.05C4.138 1.963 5.494 1.308 6.905 1.086ZM1.086 9.095C1.308 10.506 1.963 11.862 3.05 12.95C4.138 14.037 5.494 14.692 6.905 14.914L7.06 13.927C5.851 13.736 4.69 13.176 3.757 12.243C2.824 11.31 2.264 10.149 2.073 8.94L1.086 9.095ZM9.095 14.914L8.94 13.927C10.149 13.736 11.31 13.176 12.243 12.243C13.176 11.31 13.736 10.149 13.926 8.94L14.914 9.095C14.692 10.506 14.037 11.862 12.95 12.95C11.862 14.037 10.506 14.692 9.095 14.914Z" fill="#548AF7"/>
            <path d="M8.133 11.5C9.612 11.5 10.884 10.611 11.208 9.339H10.221C9.902 10.074 9.119 10.606 8.133 10.606C6.779 10.606 5.803 9.518 5.803 8C5.803 6.482 6.779 5.394 8.133 5.394C9.119 5.394 9.902 5.926 10.221 6.661H11.208C10.884 5.39 9.612 4.5 8.133 4.5C6.219 4.5 4.792 5.994 4.792 8C4.792 10.006 6.219 11.5 8.133 11.5Z" fill="#548AF7"/>""";

    private static final String ICON_EXCEPTION = """
            <circle cx="8" cy="8" r="6.5" fill="#3D3223" stroke="#D6AE58"/>
            <path d="M9 4.5L6 8H10L7 11.5" stroke="#D6AE58" stroke-linecap="round" fill="none"/>""";

    private static final String ICON_ANNOTATION = """
            <circle cx="8" cy="8" r="7" fill="#253627"/>
            <path d="M9.73 5.385V6.359H9.7C9.355 5.69 8.716 5.305 7.871 5.305C6.383 5.305 5.264 6.479 5.264 8.04C5.264 9.601 6.383 10.776 7.871 10.776C8.811 10.776 9.588 10.339 10.003 9.654C10.442 10.336 11.26 10.752 12.289 10.752C13.922 10.752 15 9.601 15 7.92C15 4.114 11.854 1 8 1C4.146 1 1 4.186 1 8.08C1 11.886 4.146 15 8 15C9.851 15 11.516 14.236 12.433 13.407L11.709 12.61C10.897 13.335 9.593 13.994 8 13.994C4.701 13.994 2.006 11.331 2.006 8.08C2.006 4.741 4.701 2.006 8 2.006C11.299 2.006 13.994 4.669 13.994 7.92C13.994 9.03 13.326 9.786 12.289 9.786C11.347 9.786 10.655 9.191 10.655 8.322V5.385H9.73ZM7.96 9.891C6.994 9.891 6.27 9.094 6.27 8.04C6.27 6.986 6.994 6.19 7.96 6.19C8.925 6.19 9.649 6.986 9.649 8.04C9.649 9.094 8.925 9.891 7.96 9.891Z" fill="#57965C"/>""";

    private static void writeTypeIcon(StringBuilder svg, double iconX, double iconY, Class<?> cls) {
        String icon;

        if (cls.isAnnotation())          icon = ICON_ANNOTATION;
        else if (cls.isInterface())      icon = ICON_INTERFACE;
        else if (cls.isEnum())           icon = ICON_ENUM;
        else if (cls.isRecord())         icon = ICON_RECORD;
        else if (Throwable.class.isAssignableFrom(cls)) icon = ICON_EXCEPTION;
        else if (Modifier.isAbstract(cls.getModifiers())) icon = ICON_ABSTRACT_CLASS;
        else                             icon = ICON_CLASS;

        svg.append("  <g transform=\"translate(%.1f,%.1f)\">\n".formatted(iconX, iconY));
        svg.append(icon);
        svg.append("\n  </g>\n");
    }

    private static void arrow(StringBuilder svg, double tipX, double tipY, String color, int h, int w) {
        double baseY = tipY - h;
        double hw = w / 2.0;
        svg.append("  <polygon points=\"%.0f,%.0f %.0f,%.0f %.0f,%.0f\" fill=\"%s\"/>\n"
            .formatted(tipX - hw, baseY, tipX + hw, baseY, tipX, tipY, color));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
