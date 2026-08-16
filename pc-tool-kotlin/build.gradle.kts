import java.util.Calendar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}

kotlin {
    jvm()
    sourceSets {
        jvmMain {
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
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            val winFormat = project.findProperty("winFormat")?.toString()?.lowercase() ?: "both"
            val windowsTargets = when (winFormat) {
                "exe" -> listOf(TargetFormat.Exe)
                "msi" -> listOf(TargetFormat.Msi)
                else  -> listOf(TargetFormat.Exe, TargetFormat.Msi)
            }

            val hasFakeroot = try {
                ProcessBuilder("which", "fakeroot").start().waitFor() == 0
            } catch (_: Exception) { false }

            val otherTargets = mutableListOf<TargetFormat>()
            if (hasFakeroot) {
                otherTargets.add(TargetFormat.Deb)
            }
            otherTargets.add(TargetFormat.Dmg)

            targetFormats(*(windowsTargets + otherTargets).toTypedArray())
            packageName = "InputBlocker"
            description = "PC Designer for InputBlocker - Configure ghost tap filtering regions"
            vendor = "Laviesss"
            copyright = "Copyright (c) ${Calendar.getInstance().get(Calendar.YEAR)} Laviesss"
            packageVersion = (project.findProperty("VERSION_NAME")?.toString() ?: "0.1.0").let { raw ->
                val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
                // macOS jpackage rejects versions where the first segment is 0
                if (isMacOS && raw.startsWith("0.")) raw.replaceFirst(Regex("^0"), "1")
                else raw
            }
            windows {
                menuGroup = "InputBlocker"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                console = false
            }
            macOS {
                bundleID = "com.inputblocker.pctool"
            }
        }
    }
}
