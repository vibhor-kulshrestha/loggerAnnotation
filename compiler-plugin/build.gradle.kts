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
