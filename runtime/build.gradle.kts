plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    coordinates("io.github.vibhor-kulshrestha", "autodebug-runtime", "0.1.0")
    pom {
        name.set("AutoDebug Runtime")
        description.set("Runtime library for automated debugging.")
    }
}

group = "io.github.vibhor-kulshrestha"
version = "0.1.0"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
