plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    coordinates("io.github.vibhor-kulshrestha", "autodebug-annotations", "0.1.2")
    pom {
        name.set("AutoDebug Annotations")
        description.set("Annotations for automated debugging.")
    }
}

group = "io.github.vibhor-kulshrestha"
version = "0.1.2"

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
