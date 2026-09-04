plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle")
}

android {
    namespace = "com.example.samehadaku"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    compileOnly("com.lagradost:cloudstream3:pre-release")
}
