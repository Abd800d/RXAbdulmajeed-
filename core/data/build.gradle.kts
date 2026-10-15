plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.almakhedhi.rxreader.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:matching"))
    implementation(project(":core:files"))
    implementation(project(":core:ai"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    testImplementation(libs.junit)
}
