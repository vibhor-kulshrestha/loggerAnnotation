plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    signing
    id("com.vanniktech.maven.publish") version "0.28.0"
}

group = "io.github.vibhor-kulshrestha"
version = "0.1.2"

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
            id = "io.github.vibhor-kulshrestha.autodebug"
            implementationClass = "com.autodebug.gradle.AutoDebugGradlePlugin"
        }
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    // Explicitly matches your new GitHub verified group ID
    coordinates("io.github.vibhor-kulshrestha", "autodebug-plugin", "0.1.2")

    pom {
        name.set("AutoDebug Gradle Plugin")
        description.set("A custom gradle plugin for automated debugging.")
        url.set("https://github.com")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://apache.org")
            }
        }
        developers {
            developer {
                id.set("vibhor-kulshrestha")
                name.set("Vibhor Kulshrestha")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/vibhor-kulshrestha/loggerAnnotation.git")
            developerConnection.set("scm:git:ssh://github.com/vibhor-kulshrestha/loggerAnnotation.git")
            url.set("https://github.com/vibhor-kulshrestha/loggerAnnotation")
        }
    }
}
