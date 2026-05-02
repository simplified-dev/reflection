plugins {
    id("java-library")
}

group = "dev.simplified"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    // Simplified Libraries
    api("com.github.simplified-dev:collections") { version { strictly("a5f41e0") } }
    api("com.github.simplified-dev:utils") { version { strictly("5c6c96a") } }

    // JetBrains Annotations
    api(libs.annotations)

    // Logging
    api(libs.log4j2.api)

    // ELK (type-hierarchy diagram rendering)
    implementation(libs.elk.core)
    implementation(libs.elk.graph)
    implementation(libs.elk.layered)
    runtimeOnly(libs.xtext.xbase.lib)

    // Lombok Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
