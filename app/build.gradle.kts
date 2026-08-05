import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Load release-signing credentials from a gitignored keystore.properties at the
// project root. Absent on machines that only build debug (release signing is skipped).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.schoolsync.teacher"
    compileSdk = 35

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.schoolsync.teacher"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        vectorDrawables { useSupportLibrary = true }

        // PHP backend URL — used for any REST API endpoints.
        //
        // Dev (WiFi-only, USB-free): use the dev PC's LAN IP. Phone and PC
        //   must be on the same WiFi/router. Update this string when the
        //   PC's IP changes (DHCP) — that's the only maintenance.
        //
        // Production (EC2): replace with the public hostname, e.g.
        //   "https://api.yourdomain.com/Grader/school/".
        //
        // Quick reference:
        //   - same-WiFi LAN access:    http://<PC_LAN_IP>/ZenX/school/
        //   - emulator:                http://10.0.2.2/ZenX/school/
        //   - USB tether (legacy):     localhost:8080 + `adb reverse tcp:8080 tcp:80`
        // Production: ZenXii admin panel + Node backend on Lightsail, served at the
        // host ROOT of https://www.zenxii.com (verified: /attendance/* and /admin_login
        // resolve at root; the legacy /Grader/school/ and /ZenX/school/ subpaths 404).
        // ApiService uses relative paths (attendance/*, staff_attendance/*) -> host root;
        // AuthApi uses leading-slash paths (/auth/*) -> also host root (Node backend).
        buildConfigField("String", "BASE_URL", "\"https://www.zenxii.com/\"")
    }

    buildTypes {
        release {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Pull-to-refresh — Material3's PullToRefreshBox isn't in the
    // 1.2.x BoM we're on, so we use the older Material (Compose
    // Material 2) `pullRefresh` API which coexists with Material3.
    implementation("androidx.compose.material:material")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")

    // Firebase (Firestore-only — RTDB SDK removed in Phase 1 Logical Change 4B)
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Location — FusedLocationProviderClient (GPS staff attendance, Phase 11)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Play Integrity — anti-spoof attestation for GPS staff attendance.
    // Inert until a cloud project number is set (PlayIntegrityTokenProvider) AND
    // the server enables it (config/play_integrity.php).
    implementation("com.google.android.play:integrity:1.4.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Video frame decoding — renders a first-frame thumbnail for the
    // story upload preview (and any video thumbnails).
    implementation("io.coil-kt:coil-video:2.6.0")
    // Video playback (Stories viewer — Round 1a).
    // Media3 = modern ExoPlayer; stable, Compose-friendly via AndroidView.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    // Stories viewer upgrade — disk cache for smooth video re-watch /
    // swipe-back (SimpleCache + CacheDataSource) and the cache DB provider.
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media3:media3-database:1.3.1")

    // Client-side video compression for story uploads — transcodes large
    // clips to ~720p/2Mbps before upload (MediaCodec, Telegram-derived),
    // mirroring WhatsApp/Instagram. JitPack-hosted.
    implementation("com.github.AbedElazizShe:LightCompressor:1.3.3")

    // Lottie animations
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Shimmer effect
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.0")

    // Razorpay checkout
    implementation("com.razorpay:checkout:1.6.38")

    // Unit tests (JVM). Pure-logic homework harness — no Firebase/Android runtime.
    testImplementation("junit:junit:4.13.2")
    // Matches the app's kotlinx-coroutines-core version (1.7.3, via -android).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

