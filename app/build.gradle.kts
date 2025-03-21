import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

fun String.runCommand(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    try {
        project.exec {
            workingDir = currentWorkingDir
            commandLine = this@runCommand.split("\\s".toRegex())
            standardOutput = byteOut
        }
        return String(byteOut.toByteArray()).trim()
    } catch (e: Exception) {
        println("Command failed: $this")
        return ""
    }
}

val gitCommitCount = try {
    val count = "git rev-list --count HEAD".runCommand()
    if (count.isNotEmpty()) count.toInt() else 1
} catch (e: Exception) {
    println("Failed to get git commit count: ${e.message}")
    1 // 默认值
}

val latestTag = try {
    val tag = "git describe --abbrev=0 --tags".runCommand()
    if (tag.isNotEmpty()) tag else "v3.0.3-beta"
} catch (e: Exception) {
    println("Failed to get latest tag: ${e.message}")
    "v3.0.3-beta" // 默认值
}

val commitCountSinceLatestTag = try {
    val count = "git rev-list --count $latestTag..HEAD".runCommand()
    if (count.isNotEmpty()) count else "0"
} catch (e: Exception) {
    println("Failed to get commit count since tag: ${e.message}")
    "0" // 默认值
}

val sdk = 34

android {
    compileSdk = sdk
    buildToolsVersion = "34.0.0"
    ndkVersion = "26.0.10792818"

    defaultConfig {
        applicationId = "gm.tieba.tabswitch"
        minSdk = 28
        targetSdk = sdk
        versionCode = gitCommitCount
        versionName = "3.0.3-beta"
        if (versionName!!.contains("alpha") || versionName!!.contains("beta")) {
            versionNameSuffix = ".$commitCountSinceLatestTag"
        }
        buildConfigField("String", "TARGET_VERSION", "\"12.66.1.0\"")
        buildConfigField("String", "MIN_VERSION", "\"12.53.1.0\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                abiFilters("arm64-v8a")
                arguments("-DANDROID_STL=none")
            }
        }
    }
    applicationVariants.all {
        outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .all { output ->
                output.outputFileName = "TS_${defaultConfig.versionName}${defaultConfig.versionNameSuffix ?: ""}_${name}.apk"
                false
            }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging {
        resources.excludes.addAll(listOf("/META-INF/**", "/kotlin/**", "/okhttp3/**"))
        jniLibs.excludes.addAll(listOf("**/liblog.so", "/lib/x86/**", "/lib/x86_64/**"))
    }
    buildFeatures {
        prefab = true
        buildConfig = true
    }
    lint {
        checkDependencies = true
    }
    namespace = "gm.tieba.tabswitch"
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    api("androidx.annotation:annotation:1.7.1")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("org.luckypray:dexkit:2.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("dev.rikka.ndk.thirdparty:cxx:1.2.0")
}

val adbExecutable: String = androidComponents.sdkComponents.adb.get().asFile.absolutePath

tasks.register("restartTieba") {
    doLast {
        exec {
            commandLine(adbExecutable, "shell", "am", "force-stop", "com.baidu.tieba")
        }
        exec {
            commandLine(adbExecutable, "shell", "am", "start", "$(pm resolve-activity --components com.baidu.tieba)")
        }
    }
}

afterEvaluate {
    tasks.named("installDebug").configure {
        finalizedBy(tasks.named("restartTieba"))
    }
}
