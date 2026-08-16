plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("io.github.vibhor-kulshrestha.autodebug")
}

android {
    namespace = "com.autodebug.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autodebug.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
}
