plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(project(":annotations"))
}

kotlin {
    jvmToolchain(17)
}
