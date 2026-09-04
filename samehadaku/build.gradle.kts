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
    implementation("com.github.recloudstream.cloudstream:library:-SNAPSHOT")
}
