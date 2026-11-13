plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.pquest.symbology"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true; all { t ->
        providers.gradleProperty("robolectricRepo").orNull?.let { t.systemProperty("robolectric.dependency.repo.url", it) }
    } } }
}
dependencies {
    api(project(":cot"))
    api("io.github.missioncommand:mil-sym-android:2.12.11")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
