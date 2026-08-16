plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    coordinates("io.github.vibhor-kulshrestha", "autodebug-compiler-plugin", "0.1.0")
    pom {
        name.set("AutoDebug Compiler Plugin")
        description.set("Kotlin Compiler Plugin for automated debugging.")
    }
}

group = "io.github.vibhor-kulshrestha"
version = "0.1.0"

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(project(":annotations"))
    compileOnly(project(":runtime"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(project(":annotations"))
    testImplementation(project(":runtime"))
    testImplementation("dev.zacsweers.kctfork:core:0.7.1")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
