plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.pquest.map"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true; all { t ->
        providers.gradleProperty("robolectricRepo").orNull?.let { t.systemProperty("robolectric.dependency.repo.url", it) }
    } } }
}
dependencies {
    api(project(":coords"))
    api(project(":security"))
    api("org.maplibre.gl:android-sdk:11.8.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
