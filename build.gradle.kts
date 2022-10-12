import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
}

group = "me.mavi"
version = "0.2-SNAPSHOT"

defaultTasks("clean", "build")

repositories {
    mavenCentral()
}

kotlin {
    val nativeTarget: KotlinNativeTarget
    val buildForArm64 = project.properties.containsKey("arm")
    val buildForArm32 = project.properties.containsKey("arm32")
    if (buildForArm64 || buildForArm32) {
        nativeTarget = if (buildForArm64) linuxArm64("native") else linuxArm32Hfp("native")
    } else {
        val hostOs = System.getProperty("os.name")
        val isMingwX64 = hostOs.startsWith("Windows")
        nativeTarget = when {
            hostOs == "Mac OS X" -> macosX64("native")
            hostOs == "Linux" -> linuxX64("native")
            isMingwX64 -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
                // bring back once they start supporting linuxArm: https://github.com/Kotlin/kotlinx-cli/issues/89
//                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
            }
        }
        val nativeTest by getting
    }
}
