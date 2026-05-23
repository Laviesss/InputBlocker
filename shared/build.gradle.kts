plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(17)
    jvm()
    android {
        namespace = "com.inputblocker.shared"
        compileSdk = 34
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
