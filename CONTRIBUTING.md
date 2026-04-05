# Contributing

Thank you for your interest in contributing! This document covers everything you need to get started.

## Table of Contents

- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Setup](#development-setup)
- [Making Changes](#making-changes)
  - [Branching Strategy](#branching-strategy)
  - [Code Style](#code-style)
  - [Commit Messages](#commit-messages)
  - [Running Tests](#running-tests)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)
- [Project Architecture](#project-architecture)
- [Legal](#legal)

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [Java (JDK)](https://adoptium.net/) | **21+** | Must be a full JDK, not a JRE |
| [Gradle](https://gradle.org/) | **9.4+** | Wrapper included - no global install needed |
| [Git](https://git-scm.com/) | **2.x+** | For cloning and version control |

### Development Setup

1. **Fork** the repository on GitHub.

2. **Clone** your fork locally:

   ```bash
   git clone https://github.com/<your-username>/<repo>.git
   cd <repo>
   ```

3. **Build** the project to verify your environment:

   ```bash
   ./gradlew build
   ```

   > On Windows, use `gradlew.bat build` instead.

4. **Open** the project in your IDE. IntelliJ IDEA is recommended - it will automatically detect the Gradle project and configure itself. Make sure the Lombok plugin is installed and annotation processing is enabled.

## Making Changes

### Branching Strategy

All development is based on the `master` branch.

- Create a feature branch from `master` for your changes:

  ```bash
  git checkout -b feature/your-feature master
  ```

- Use descriptive branch names: `feature/add-xyz`, `fix/null-pointer-in-abc`, `refactor/simplify-def`.

### Code Style

- Follow standard Java conventions (Oracle Code Conventions).
- Use [Lombok](https://projectlombok.org/) annotations where appropriate to reduce boilerplate.
- Use [JetBrains annotations](https://www.jetbrains.com/help/idea/annotating-source-code.html) (`@NotNull`, `@Nullable`) for nullability.
- Target **Java 21** - use modern language features (records, sealed classes, pattern matching) where they improve clarity.
- Keep methods focused and classes cohesive. Prefer small, well-named methods over large ones.

### Commit Messages

Write commit messages in **imperative mood** (e.g., "Add support for X" not "Added support for X").

- Keep the subject line under 72 characters.
- Use the body to explain **why**, not just **what**.
- Reference issue numbers where applicable (e.g., `Fixes #12`).

```
Add validation for negative exponents

The expression evaluator did not handle negative exponents in
power operations, causing incorrect results. This adds a check
in the operator evaluation step.

Fixes #42
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "dev.simplified.SomeTest"

# Full build (compile + test + checks)
./gradlew build
```

## Submitting a Pull Request

1. **Push** your branch to your fork:

   ```bash
   git push origin feature/your-feature
   ```

2. **Open a Pull Request** against the `master` branch of the upstream repository.

3. In the PR description:
   - Summarize the changes and motivation.
   - Reference any related issues.
   - Note any breaking changes.

4. **Respond to feedback** - maintainers may request changes before merging.

## Reporting Issues

- Use [GitHub Issues](../../issues) to report bugs or request features.
- For bugs, include: steps to reproduce, expected behavior, actual behavior, and Java/Gradle versions.
- For feature requests, describe the use case and any proposed API surface.

## Project Architecture

This module is part of the [Simplified-Dev](https://github.com/Simplified-Dev) library ecosystem. Each module is a standalone Gradle project published via JitPack. Modules may depend on sibling modules (`collections`, `utils`) through JitPack coordinates.

The source follows a standard Gradle layout:

```
src/main/java/    - Production source code
src/test/java/    - Test source code (JUnit 5)
build.gradle.kts  - Build configuration
settings.gradle.kts - Project settings
```

## Legal

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE.md), the same license that covers this project.
