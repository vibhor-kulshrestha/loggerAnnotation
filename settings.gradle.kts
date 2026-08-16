pluginManagement {
    includeBuild("gradle-plugin-build")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "loggerAnnotation"

include(":annotations")
include(":runtime")
include(":compiler-plugin")
include(":sample-android")
