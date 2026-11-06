plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ONE place to name a build. For each new build change all three lines below:
//   v2 / 2 / Banana,  v3 / 3 / Cherry, ...  (fruits in A-B-C order, see README)
val appVersion = "v4"
val appVersionCode = 4
val appCodename = "Date"

base {
    archivesName.set("Aksharvel-$appVersion-$appCodename")   // APK file: Aksharvel-v4-Date-debug.apk
}

android {
    namespace = "com.aksharvel.tactile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aksharvel.tactile"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "BUILD_NAME", "\"$appCodename\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        // mp3: SoundPool reads the clips straight from the APK.
        // onnx: the speech model is memory-mapped from assets instead of unpacked.
        noCompress += listOf("mp3", "onnx")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    testImplementation("junit:junit:4.13.2")

    // Offline Marathi speech recognition. Commented out on purpose: the app
    // builds and runs without it, and the speaking tab simply reports that it
    // is not ready. Uncomment once you have added the model to
    // app/src/main/assets/asr/ - see SPEECH_SETUP.md.
    implementation("com.k2-fsa.sherpa:sherpa-onnx:1.10.32")
}
