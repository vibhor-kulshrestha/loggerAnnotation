plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.autodebug"
            artifactId = "compiler-plugin"
            version = "0.1.0-SNAPSHOT"
        }
    }
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(project(":annotations"))
    compileOnly(project(":runtime"))
}

kotlin {
    jvmToolchain(17)
}
