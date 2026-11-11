// Racine du projet. Versions vérifiées le 2026-09-18 sur les dépôts officiels.
// AGP 9 : Kotlin intégré (pas de plugin kotlin.android) ; les sous-plugins
// compose et serialization restent déclarés à la version de Kotlin.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.2.21" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
}

// Keep Web dependency locks alongside the prototype.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension> {
        lockFileDirectory = file("web/kotlin-js-store/wasm")
    }
}
