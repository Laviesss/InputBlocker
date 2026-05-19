plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Common dependencies here
            }
        }
        val androidMain by getting
        val jvmMain by getting
    }
}

android {
    namespace = "com.inputblocker.shared"
    compileSdk = 34
}
