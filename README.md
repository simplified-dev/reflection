# Reflection

Cached reflection utility library for Java type inspection and member access. Wraps Java's reflection API with caching, provides typed accessor abstractions for constructors, fields, methods, and resources, and includes a type hierarchy diagram renderer using the ELK layout engine.

> [!IMPORTANT]
> This library is part of the [Simplified-Dev](https://github.com/Simplified-Dev) ecosystem and depends on the `collections` and `utils` modules. All dependencies are resolved automatically via [JitPack](https://jitpack.io/).

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Usage](#usage)
- [API Overview](#api-overview)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Cached reflection** - Wraps `java.lang.reflect` with lookup caching to avoid repeated expensive reflection calls
- **Typed accessors** - Unified `Accessor` abstraction for constructors, fields, methods, and resources with type-safe access
- **Type hierarchy diagrams** - Renders class/interface inheritance trees as graphs using the ELK layout engine
- **Configurable diagram rendering** - `DiagramConfig` controls layout, styling, and filtering for hierarchy visualizations
- **Build flags** - `BuildFlag` directives control which reflection metadata to collect
- **Type information** - `ClassInfo`, `FileInfo`, `LocationInfo`, and `ResourceInfo` provide structured metadata about types and their origins

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [Java](https://adoptium.net/) | **21+** | Required |
| [Gradle](https://gradle.org/) | **9.4+** | Wrapper included (`./gradlew`) |
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

### Usage

```java
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.accessor.FieldAccessor;
import dev.simplified.reflection.accessor.MethodAccessor;

// Inspect a class
Reflection reflection = Reflection.of(MyClass.class);

// Access fields and methods with caching
FieldAccessor field = reflection.field("name");
MethodAccessor method = reflection.method("getName");
```

> [!TIP]
> Use `BuildFlag` to control which metadata is collected during reflection. This lets you skip expensive operations (like scanning resources) when you only need basic type information.

<details>
<summary>Type hierarchy diagrams</summary>

The `diagram` package can render class inheritance hierarchies as graph diagrams using the ELK layout engine:

```java
import dev.simplified.reflection.diagram.TypeHierarchyDiagram;
import dev.simplified.reflection.diagram.DiagramConfig;

TypeHierarchyDiagram diagram = new TypeHierarchyDiagram(DiagramConfig.defaults());
String rendered = diagram.render(MyClass.class);
```

</details>

## API Overview

| Class | Package | Description |
|-------|---------|-------------|
| `Reflection` | `reflection` | Main entry point - cached wrapper around `java.lang.reflect` |
| `Accessor` | `accessor` | Base abstraction for reflective member access |
| `ConstructorAccessor` | `accessor` | Typed accessor for constructors |
| `FieldAccessor` | `accessor` | Typed accessor for fields with get/set operations |
| `MethodAccessor` | `accessor` | Typed accessor for methods with invoke support |
| `ResourceAccessor` | `accessor` | Typed accessor for classpath resources |
| `BuildFlag` | `builder` | Enum of flags controlling which reflection metadata to collect |
| `TypeHierarchyDiagram` | `diagram` | Renders class/interface hierarchies as ELK graph diagrams |
| `DiagramConfig` | `diagram` | Configuration for diagram layout, styling, and filtering |
| `ClassInfo` | `info` | Metadata about a class (name, modifiers, supertypes) |
| `FileInfo` | `info` | Metadata about source file location |
| `LocationInfo` | `info` | Metadata about the JAR or directory a class was loaded from |
| `ResourceInfo` | `info` | Metadata about a classpath resource |
| `ReflectionException` | `exception` | Thrown when a reflective operation fails |

> [!NOTE]
> The ELK diagram rendering pulls in `elk-core`, `elk-graph`, and `elk-layered` at compile time, plus `xtext-xbase-lib` at runtime. These are only needed if you use the `diagram` package.

## Project Structure

```
reflection/
├── src/main/java/dev/simplified/reflection/
│   ├── Reflection.java
│   ├── accessor/
│   │   ├── Accessor.java
│   │   ├── ConstructorAccessor.java
│   │   ├── FieldAccessor.java
│   │   ├── MethodAccessor.java
│   │   └── ResourceAccessor.java
│   ├── builder/
│   │   └── BuildFlag.java
│   ├── diagram/
│   │   ├── DiagramConfig.java
│   │   └── TypeHierarchyDiagram.java
│   ├── exception/
│   │   └── ReflectionException.java
│   └── info/
│       ├── ClassInfo.java
│       ├── FileInfo.java
│       ├── LocationInfo.java
│       └── ResourceInfo.java
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── LICENSE.md
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style guidelines, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0** - see [LICENSE.md](LICENSE.md) for the full text.
