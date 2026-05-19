plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.inputblocker.pctool.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "InputBlockerSetup"
            packageVersion = "0.1.0"
        }
    }
}
