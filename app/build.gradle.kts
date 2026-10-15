import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Firebase is optional at build time: the app compiles and runs without
// google-services.json (Firebase features report "not configured").
// When the file exists (local copy or CI secret), the config is wired in.
val googleServicesJson = layout.projectDirectory.file("google-services.json").asFile
var googleWebClientId = ""
if (googleServicesJson.exists()) {
    apply(plugin = "com.google.gms.google-services")
    @Suppress("UNCHECKED_CAST")
    val parsed = JsonSlurper().parse(googleServicesJson) as Map<String, Any>
    val clients = parsed["client"] as List<Map<String, Any>>
    googleWebClientId = clients
        .flatMap { (it["oauth_client"] as List<Map<String, Any>>) }
        .firstOrNull { (it["client_type"] as Number).toInt() == 3 }
        ?.get("client_id") as? String ?: ""
}

android {
    namespace = "com.almakhedhi.rxreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.almakhedhi.rxreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    // Shared debug key (committed on purpose): a stable signature on every
    // machine and in CI, so the Firebase SHA-1 registration keeps working.
    // Release signing (Phase 7) uses a separate, never-committed keystore.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.p12")
            storePassword = "android"
            keyAlias = "rxreader-debug"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:auth"))
    implementation(project(":core:scanner"))
    implementation(project(":core:ai"))
    implementation(project(":core:data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:support"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:drugs"))
    implementation(project(":feature:importexport"))
    implementation(project(":feature:history"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.mlkit.doc.scanner)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
