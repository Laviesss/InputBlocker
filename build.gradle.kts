// Load local.properties for local development (git-ignored, no hardcoded versions)
val localProperties = java.util.Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localFile.inputStream().use { localProperties.load(it) }
}

allprojects {
    localProperties.forEach { (key, value) ->
        extra.set(key.toString(), value.toString())
    }
}

plugins {
    kotlin("multiplatform") version "2.3.21" apply false
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.compose") version "1.11.0" apply false
    kotlin("plugin.compose") version "2.3.21" apply false
}

val processedModuleDir = layout.buildDirectory.dir("intermediates/module")

tasks.register<Copy>("prepareModule") {
    group = "build"
    description = "Process module templates injecting version properties"

    from("module")
    filesMatching(listOf("module.prop", "update.json")) {
        filteringCharset = "UTF-8"
        filter { line ->
            val vName = project.property("VERSION_NAME")
            val vCode = project.property("VERSION_CODE")
            line
                .replace("{{VERSION_NAME}}", vName.toString())
                .replace("{{VERSION_CODE}}", vCode.toString())
        }
    }
    into(processedModuleDir)
}

tasks.register<Zip>("buildModule") {
    group = "build"
    description = "Builds the Android APK and packs it into a flashable root module ZIP"
    
    dependsOn("buildAndroid", "prepareModule")
    
    from(processedModuleDir) {
        into("/")
    }
    
    // Place renamed APK in common/ for the installer check
    from("android-app/app/build/outputs/apk/release/") {
        include("app-release.apk", "app-release-unsigned.apk")
        rename { "InputBlocker.apk" }
        into("common")
    }
    // Also place in system/app so the overlay auto-installs on boot
    from("android-app/app/build/outputs/apk/release/") {
        include("app-release.apk", "app-release-unsigned.apk")
        rename { "InputBlocker.apk" }
        into("system/app/InputBlocker")
    }
    
    archiveFileName.set("inputblocker.zip")
    destinationDirectory.set(file("build/distributions"))
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds the entire ecosystem: APK, PC Tool (EXE/MSI), and Module ZIP"
    dependsOn("buildAndroid", "buildPC", "buildModule")
}

tasks.register("buildAndroid") {
    group = "build"
    description = "Builds the Android APK"
    dependsOn(":android-app:app:assembleRelease")
}

tasks.register("buildPC") {
    group = "build"
    description = "Builds the PC Tool (Windows EXE/MSI, Linux DEB, macOS DMG)"
    dependsOn(":pc-tool-kotlin:packageDistributionForCurrentOS")
}

tasks.register("buildShared") {
    group = "build"
    description = "Builds the Shared KMP library"
    dependsOn(":shared:assemble")
}
