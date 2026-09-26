import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    android {
        namespace = "school.greenwood.plus.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val jvmCommon = create("jvmCommon") {
            dependsOn(commonMain.get())
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("com.squareup.retrofit2:retrofit:3.0.0")
                implementation("com.squareup.retrofit2:converter-scalars:3.0.0")
                implementation("com.squareup.okhttp3:okhttp:5.5.0")
                implementation("androidx.datastore:datastore-preferences:1.2.1")
                implementation("com.google.code.gson:gson:2.13.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        androidMain.get().dependsOn(jvmCommon)
        jvmMain.get().dependsOn(jvmCommon)
        androidMain.dependencies {
            implementation("androidx.compose.runtime:runtime:1.12.1")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.foundation:foundation:1.12.1")
            implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            implementation("org.jsoup:jsoup:1.22.1")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "school.greenwood.plus.desktop.MainKt"
        from(kotlin.targets["jvm"])
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "gws-plus"
            // jpackage accepts numeric versions; the release asset and Android
            // versionName retain the full beta suffix.
            packageVersion = providers.gradleProperty("gwsVersion").get().substringBefore('-')
            description = "Greenwood School + pour Linux"
            vendor = "Greenwood School +"
        }
    }
}
