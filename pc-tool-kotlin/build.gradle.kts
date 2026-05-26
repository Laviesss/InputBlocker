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
            packageName = "InputBlocker"
            description = "PC Designer for InputBlocker - Configure ghost tap filtering regions"
            vendor = "Laviesss"
            copyright = "Copyright (c) ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Laviesss"
            packageVersion = project.property("VERSION_NAME").toString().let { raw ->
                val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
                // macOS jpackage rejects versions where the first segment is 0
                if (isMacOS && raw.startsWith("0.")) raw.replaceFirst(Regex("^0"), "1")
                else raw
            }
            windows {
                menuGroup = "InputBlocker"
                // Show a desktop shortcut option in the installer
                shortcut = true
                // Show directory chooser during install so users can pick the install path
                dirChooser = true
                // Install per-user (no admin required by default)
                perUserInstall = true
                // Show a console window so we can see crash output / stack traces
                console = true
            }
            macOS {
                bundleID = "com.inputblocker.pctool"
            }
        }
    }
}
