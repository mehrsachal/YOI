plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.pquest.security"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api("net.zetetic:sqlcipher-android:4.6.1")
    api("androidx.sqlite:sqlite:2.4.0")
}
