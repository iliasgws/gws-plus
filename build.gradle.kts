// Racine du projet. Versions vérifiées le 2026-09-18 sur les dépôts officiels.
// AGP 9 : Kotlin intégré (pas de plugin kotlin.android) ; les sous-plugins
// compose et serialization restent déclarés à la version de Kotlin.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
