plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "app.pquest"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.pquest"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"
        resourceConfigurations += listOf("en", "ur")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
dependencies {
    implementation(project(":map"))
    implementation(project(":missions"))
    implementation(project(":mesh"))
    implementation(project(":symbology"))
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
}
