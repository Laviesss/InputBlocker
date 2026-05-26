plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
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
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "InputBlockerSetup"
            packageVersion = project.property("VERSION_NAME").toString().let { raw ->
                val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
                // macOS jpackage rejects versions where the first segment is 0
                if (isMacOS && raw.startsWith("0.")) raw.replaceFirst(Regex("^0"), "1")
                else raw
            }
            macOS {
                bundleID = "com.inputblocker.pctool"
            }
        }
    }
}
