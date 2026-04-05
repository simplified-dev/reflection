# reflection

Cached reflection utility library for type inspection, member access, and hierarchy diagrams.

## Package Structure
- `dev.simplified.reflection` - Reflection (main cached wrapper)
- `dev.simplified.reflection.accessor` - Accessor (base), ConstructorAccessor, FieldAccessor, MethodAccessor, ResourceAccessor
- `dev.simplified.reflection.builder` - BuildFlag (reflection directives)
- `dev.simplified.reflection.diagram` - DiagramConfig, TypeHierarchyDiagram (ELK rendering)
- `dev.simplified.reflection.exception` - ReflectionException
- `dev.simplified.reflection.info` - ClassInfo, FileInfo, LocationInfo, ResourceInfo

## Key Classes
- `Reflection` - main entry point: cached wrapper around `java.lang.reflect`
- `Accessor` - base abstraction for typed reflective member access
- `TypeHierarchyDiagram` - renders class/interface hierarchies via ELK layout engine
- `BuildFlag` - enum controlling which metadata to collect

## Dependencies
- `com.github.simplified-dev:collections:master-SNAPSHOT`
- `com.github.simplified-dev:utils:master-SNAPSHOT`
- JetBrains annotations, Lombok, Log4j2
- ELK: elk-core, elk-graph, elk-layered (+ xtext-xbase-lib runtime)

## Build
```bash
./gradlew build
./gradlew test
```

## Info
- Java 21, Gradle (Kotlin DSL)
- Group: `dev.simplified`, artifact: `reflection`, version: `1.0.0`
- 14 source files, 6 packages, no tests
