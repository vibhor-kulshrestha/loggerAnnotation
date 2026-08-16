plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

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
