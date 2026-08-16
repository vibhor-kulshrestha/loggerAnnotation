plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.1.21")
}

gradlePlugin {
    plugins {
        create("autodebug") {
            id = "com.autodebug"
            implementationClass = "com.autodebug.gradle.AutoDebugGradlePlugin"
        }
    }
}
