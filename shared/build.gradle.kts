import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Renders every screen to PNG on the JVM, for checking the UI without a device:
 * `./gradlew :shared:desktopTest -Pdiaroutine.screenshots` writes shared/build/screenshots.
 * Off by default, so normal builds carry no desktop target.
 */
val screenshots = providers.gradleProperty("diaroutine.screenshots").isPresent

/**
 * The whole app — the routine's data, the timeline and adherence logic, and every
 * screen — written once for Android and iOS. The platforms supply only what they
 * own: where the database file lives, alarms and notifications, and sharing.
 */
kotlin {
    android {
        namespace = "com.diaroutine.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // Compose resources (the Outfit font) are packaged as Android assets.
        androidResources { enable = true }
        // commonTest runs on the JVM too: `./gradlew :shared:testAndroidHostTest`.
        withHostTest { isReturnDefaultValues = true }
    }

    if (screenshots) jvm("desktop")

    // iosX64 is the simulator on an Intel Mac; see libs.versions.toml.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        // Room's database constructor is an expect object that KSP actualizes per platform.
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.coroutines.FlowPreview",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        if (screenshots) {
            getByName("desktopTest").dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

dependencies {
    listOf("kspAndroid", "kspIosX64", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.room.compiler)
    }
    if (screenshots) add("kspDesktop", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    packageOfResClass = "com.diaroutine.resources"
}
