plugins {
    id("com.android.library")
    kotlin("android")
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
