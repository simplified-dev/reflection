# Reflection

Cached reflection utility library for Java type inspection and member access. Wraps `java.lang.reflect` with per-class caching to eliminate repeated JNI lookups, provides typed accessor abstractions for constructors, fields, methods, and classpath resources, and includes a type hierarchy diagram renderer that produces IntelliJ Darcula-styled SVG diagrams using the ELK layout engine.

> [!IMPORTANT]
> This library is under active development. APIs may change between releases
> until a stable `1.0.0` release is published.

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [Usage](#usage)
  - [Inspecting a Class](#inspecting-a-class)
  - [Generic Superclass Resolution](#generic-superclass-resolution)
  - [Classpath Scanning](#classpath-scanning)
  - [Builder Validation](#builder-validation)
  - [Type Hierarchy Diagrams](#type-hierarchy-diagrams)
- [Architecture](#architecture)
  - [Package Overview](#package-overview)
  - [Project Structure](#project-structure)
- [Dependencies](#dependencies)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Per-class caching** - Separate caches for declared fields, methods, constructors, superclass-chain accessors, resolved generic types, and `BuildFlag` metadata, all populated on first access and reused on subsequent lookups
- **Typed accessors** - `FieldAccessor` (get/set with generic type), `MethodAccessor` (invoke with return type), `ConstructorAccessor` (newInstance), and `ResourceAccessor` (classpath scanning) with annotation inspection and modifier predicates
- **Superclass chain walking** - Configurable traversal of the full inheritance hierarchy for field and method discovery, with results cached per class
- **Generic type resolution** - Resolves parameterized superclass type arguments at runtime (e.g. `Client<UserApi>` resolves `UserApi` from `Reflection.getSuperClass(this)`)
- **Classpath scanning** - `ResourceAccessor` scans classloader URLs, JAR manifests, and directory trees with package filtering, symlink cycle detection, and type discovery via `getSubtypesOf()`/`getTypesOf()`
- **Builder validation** - `@BuildFlag` annotation validates builder fields at build time for null checks, emptiness, regex patterns, length limits, and mutually exclusive groups
- **Type hierarchy diagrams** - Renders class/interface inheritance trees as self-contained SVG diagrams with IntelliJ Darcula styling, orthogonal edge routing, arc-jump crossings, and type icons (interface, class, enum, record, abstract, annotation, exception)
- **Configurable diagram layout** - `DiagramConfig` controls ELK layering strategy, type filtering, name suffix stripping, and output path

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [Java](https://adoptium.net/) | **21+** | Required (LTS recommended) |
| [Gradle](https://gradle.org/) | **9.4+** | Or use the included `./gradlew` wrapper |
| [Git](https://git-scm.com/) | 2.x+ | For cloning the repository |

### Installation

Add the JitPack repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.simplified-dev:reflection:master-SNAPSHOT")
}
```

<details>
<summary>Gradle (Groovy)</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.simplified-dev:reflection:master-SNAPSHOT'
}
```

</details>

<details>
<summary>Maven</summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.simplified-dev</groupId>
    <artifactId>reflection</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

</details>

> [!NOTE]
> This library depends on other Simplified-Dev modules (`collections`, `utils`)
> which are resolved from JitPack automatically.

## Usage

### Inspecting a Class

```java
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.accessor.FieldAccessor;
import dev.simplified.reflection.accessor.MethodAccessor;

// Create a reflection instance (results are cached per class)
Reflection<MyClass> reflection = new Reflection<>(MyClass.class);

// Access fields by name or type
FieldAccessor<String> nameField = reflection.getField("name");
String value = nameField.get(instance);
nameField.set(instance, "new value");

// Access methods
MethodAccessor<String> getter = reflection.getMethod("getName");
String name = getter.invoke(instance);

// Construct new instances
MyClass obj = reflection.newInstance("arg1", 42);

// Walk superclass chain (enabled by default)
ConcurrentSet<FieldAccessor<?>> allFields = reflection.getFields();

// Or restrict to declared fields only
reflection.setProcessingSuperclass(false);
ConcurrentSet<FieldAccessor<?>> declaredOnly = reflection.getFields();
```

### Generic Superclass Resolution

Resolve parameterized type arguments from a class's generic superclass at runtime:

```java
// Given: class UserClient extends Client<UserApi>
Class<UserApi> apiType = Reflection.getSuperClass(userClient);

// Resolve the Nth type argument
Class<?> secondArg = Reflection.getSuperClass(SomeClass.class, 1);
```

### Classpath Scanning

Discover classes and resources on the classpath:

```java
import dev.simplified.reflection.accessor.ResourceAccessor;

// Scan the default classloader
ResourceAccessor resources = Reflection.getResources();

// Filter to a specific package
ResourceAccessor filtered = resources.filterPackage(MyApp.class);

// Find all implementations of an interface
ConcurrentList<Class<? extends Plugin>> plugins = filtered.getSubtypesOf(Plugin.class);

// Find all assignable types (including the type itself)
ConcurrentList<Class<JpaModel>> models = filtered.getTypesOf(JpaModel.class);
```

### Builder Validation

Annotate builder fields with `@BuildFlag` for automatic validation:

```java
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.reflection.Reflection;

public class Config {

    public static class Builder {

        @BuildFlag(nonNull = true)
        private String host;

        @BuildFlag(notEmpty = true)
        private List<String> endpoints;

        @BuildFlag(pattern = "^[a-z]+$")
        private String name;

        @BuildFlag(limit = 100)
        private String description;

        @BuildFlag(nonNull = true, group = "auth")
        private String apiKey;

        @BuildFlag(nonNull = true, group = "auth")
        private String token;  // mutually exclusive with apiKey

        public Config build() {
            Reflection.validateFlags(this);  // validates all @BuildFlag constraints
            return new Config(this);
        }
    }
}
```

### Type Hierarchy Diagrams

Render class/interface hierarchies as SVG diagrams:

```java
import dev.simplified.reflection.diagram.DiagramConfig;

DiagramConfig config = DiagramConfig.builder()
    .withScanPackage(MyApp.class)
    .withRoots(BaseInterface.class)
    .withFileName("hierarchy")
    .withSuffix("Impl")           // strip suffix from displayed names
    .build();

TypeHierarchyDiagram diagram = config.render();
diagram.writeTo(Path.of("docs"));  // writes SVG to docs/doc-files/hierarchy.svg
```

> [!TIP]
> The ELK diagram rendering pulls in `elk-core`, `elk-graph`, and `elk-layered`
> at compile time, plus `xtext-xbase-lib` at runtime. These are only needed if
> you use the `diagram` package.

## Architecture

### Package Overview

| Package | Description |
|---------|-------------|
| `dev.simplified.reflection` | `Reflection` - cached wrapper around `java.lang.reflect` with field/method/constructor caching, superclass chain walking, generic type resolution, classpath resource access, and `@BuildFlag` validation |
| `dev.simplified.reflection.accessor` | Typed accessor abstractions - `Accessor` (base interface with annotation and modifier utilities), `FieldAccessor` (get/set), `MethodAccessor` (invoke), `ConstructorAccessor` (newInstance), `ResourceAccessor` (classpath scanning with package filtering and type discovery) |
| `dev.simplified.reflection.builder` | `@BuildFlag` annotation for declarative builder field validation (nonNull, notEmpty, pattern, limit, group) |
| `dev.simplified.reflection.diagram` | `DiagramConfig` (builder-based layout configuration with ELK layering strategies) and `TypeHierarchyDiagram` (self-contained SVG renderer with Darcula styling, orthogonal routing, and type icons) |
| `dev.simplified.reflection.exception` | `ReflectionException` - unchecked exception with formatted message constructors |
| `dev.simplified.reflection.info` | `FileInfo` (base), `ResourceInfo` (classpath resource metadata with byte/stream access), `ClassInfo` (class name parsing without loading), `LocationInfo` (JAR/directory scanner with symlink detection) |

### Project Structure

```
src/main/java/dev/simplified/reflection/
├── Reflection.java
├── accessor/
│   ├── Accessor.java
│   ├── ConstructorAccessor.java
│   ├── FieldAccessor.java
│   ├── MethodAccessor.java
│   └── ResourceAccessor.java
├── builder/
│   └── BuildFlag.java
├── diagram/
│   ├── DiagramConfig.java
│   └── TypeHierarchyDiagram.java
├── exception/
│   └── ReflectionException.java
└── info/
    ├── ClassInfo.java
    ├── FileInfo.java
    ├── LocationInfo.java
    └── ResourceInfo.java
```

## Dependencies

| Dependency | Version | Scope |
|------------|---------|-------|
| [ELK Core](https://www.eclipse.org/elk/) | 0.11.0 | Implementation |
| [ELK Graph](https://www.eclipse.org/elk/) | 0.11.0 | Implementation |
| [ELK Layered](https://www.eclipse.org/elk/) | 0.11.0 | Implementation |
| [Xtext Xbase Lib](https://www.eclipse.org/xtext/) | 2.37.0 | Runtime |
| [Log4j2](https://logging.apache.org/log4j/) | 2.25.3 | API |
| [JetBrains Annotations](https://github.com/JetBrains/java-annotations) | 26.0.2 | API |
| [Lombok](https://projectlombok.org/) | 1.18.36 | Compile-only |
| [collections](https://github.com/Simplified-Dev/collections) | master-SNAPSHOT | API (Simplified-Dev) |
| [utils](https://github.com/Simplified-Dev/utils) | master-SNAPSHOT | API (Simplified-Dev) |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style
guidelines, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0** - see
[LICENSE.md](LICENSE.md) for the full text.
