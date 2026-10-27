// :shared is the app — data, routine logic and Compose UI — for Android and iOS.
// :androidApp wraps it in an Android application and supplies the Android-only
// pieces (alarms, notifications, sharing). Kotlin is built into AGP 9, so
// :androidApp has no kotlin-android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
