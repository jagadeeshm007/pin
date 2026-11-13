import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.junit)
    alias(libs.plugins.ktlint)
}

// sherpa-onnx (ADR-0003, ADR-0009): official release AAR, downloaded and hash-checked.
val sherpaOnnxUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-static-link-onnxruntime-2.0.1.aar"
val sherpaOnnxSha256 = "07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575"
val sherpaOnnxAar = layout.buildDirectory.file("third-party/sherpa-onnx-static-link-onnxruntime-2.0.1.aar")

val fetchSherpaOnnx by tasks.registering {
    description = "Downloads the sherpa-onnx AAR and checks its SHA-256 (ADR-0009)."
    val out = sherpaOnnxAar
    val url = sherpaOnnxUrl
    val expected = sherpaOnnxSha256
    outputs.file(out)
    doLast {
        val file = out.get().asFile
        fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").let { md ->
            f.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        if (file.exists() && sha256(file) == expected) return@doLast
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val actual = sha256(tmp)
        if (actual != expected) {
            tmp.delete()
            throw GradleException("sherpa-onnx AAR hash mismatch: expected $expected, got $actual")
        }
        tmp.renameTo(file)
    }
}

// Release signing is wired only when the maintainer provides a keystore path;
// the repository never holds a keystore or a password.
val keystorePath: String? = providers.environmentVariable("PIN_KEYSTORE_PATH").orNull

android {
    namespace = "io.github.jagadeeshm007.pin"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jagadeeshm007.pin"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64 only for now; armeabi-v7a is decided in spike PIN-2.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("PIN_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("PIN_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("PIN_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("proguard-rules.pro"),
            )
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        xmlReport = true
        htmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Native code compressed in the APK: 10.6 MB instead of 25.8 MB (PIN-2).
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    android.set(true)
    version.set(libs.versions.ktlint.get())
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(files(sherpaOnnxAar).builtBy(fetchSherpaOnnx))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
