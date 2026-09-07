plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle")
}

version = 1

android {
    namespace = "com.example.kuramanime"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val cloudstream by configurations
    val implementation by configurations

    cloudstream("com.lagradost:cloudstream3:pre-release")

    implementation(kotlin("stdlib"))
    implementation("org.jsoup:jsoup:1.18.3")
}
