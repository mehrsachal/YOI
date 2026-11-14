plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "app.pquest"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.pquest"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en", "ur")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    // Release signing comes from Gradle properties (never committed): pquestKeystore, pquestKeystorePassword, pquestKeyAlias, pquestKeyPassword.
    val keystore = providers.gradleProperty("pquestKeystore").orNull
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore); storePassword = providers.gradleProperty("pquestKeystorePassword").get()
            keyAlias = providers.gradleProperty("pquestKeyAlias").get(); keyPassword = providers.gradleProperty("pquestKeyPassword").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true // compress native libraries: keeps the APK well under the 60 MB limit
    }
    testOptions { unitTests { isIncludeAndroidResources = true; all { t ->
        providers.gradleProperty("robolectricRepo").orNull?.let { t.systemProperty("robolectric.dependency.repo.url", it) }
    } } }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
dependencies {
    implementation(project(":map"))
    implementation(project(":missions"))
    implementation(project(":mesh"))
    implementation(project(":symbology"))
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.core:core-ktx:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
}
