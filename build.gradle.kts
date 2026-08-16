plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()
            
            pom {
                url.set("https://github.com/vibhor-kulshrestha/loggerAnnotation")
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
    }
}
