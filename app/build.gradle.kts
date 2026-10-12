plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.sndmartvendor.vmrtxz"
    minSdk = 24
    targetSdk = 36
    // Play Store requires a higher versionCode for every uploaded release.
    versionCode = 6
    versionName = "1.0.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Release signing comes from the upload keystore. CI passes it via
    // -Pandroid.injected.signing.*; local builds can use KEYSTORE_PATH, STORE_PASSWORD,
    // KEY_ALIAS and KEY_PASSWORD. Without them the release build is left unsigned
    // (never signed with a debug key).
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      val storePass = System.getenv("STORE_PASSWORD")
      if (keystorePath != null && storePass != null && file(keystorePath).exists()) {
        storeFile = file(keystorePath)
        storePassword = storePass
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: storePass
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseSigning = signingConfigs.getByName("release")
      if (releaseSigning.storeFile != null) {
        signingConfig = releaseSigning
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Secrets Gradle Plugin reads .env (falls back to .env.example) and exposes the values
// as BuildConfig fields and manifest placeholders (e.g. GOOGLE_MAPS_API_KEY).
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.messaging)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)
  implementation(libs.play.app.update)
  implementation(libs.retrofit)
  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.moshi.kotlin.codegen)
}
