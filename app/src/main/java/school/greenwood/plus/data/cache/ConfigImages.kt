package school.greenwood.plus.data.cache

import android.content.Context
import android.util.Log
import coil3.EventListener
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import school.greenwood.plus.BuildConfig
import school.greenwood.plus.util.IdentiteMedias

/*
 * Chargeur d'images partagé (issue #108) — des clés de cache stables.
 *
 * `media.boti.education` re-signe chaque URL de média à sa réponse (jeton
 * jetable, ~15–20 min ; docs/api/ENDPOINT-MAP.md) : la même photo arrive avec
 * une URL différente selon l'écran — liste du Registre, Actualités, détail
 * d'un billet — donc une clé de cache prise sur l'URL brute manque le cache à
 * chaque écran et re-télécharge ce qui existe déjà. L'intercepteur ci-dessous
 * réécrit les deux clés (mémoire et disque) sur l'identité stable de la
 * ressource, `IdentiteMedias.clé` (URL sans sa signature) : le cache devient
 * cohérent d'un écran à l'autre sans toucher aux sites d'appel `AsyncImage`.
 * Les tailles de cache restent celles par défaut de Coil (bornées : ~25 Mo de
 * mémoire, 25 % du disque) — rien à régler ici. En développement seulement,
 * un écouteur journalise l'origine de chaque image (source, clé stable,
 * échantillonnage) sous l'étiquette « Médias » : jamais l'URL brute, le jeton
 * signé n'a rien à faire dans les journaux.
 */

/**
 * Réécrit la clé de cache (mémoire + disque) de toute requête dont la donnée
 * est une URL signée de médias, sur l'identité stable de la ressource. Toute
 * autre donnée (fichier, ressource, URL non signée) traverse inchangée.
 */
private class ClesMediasStables : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val requête = chain.request
        val donnée = requête.data
        if (donnée !is String) return chain.proceed()
        val clé = IdentiteMedias.clé(donnée)
        if (clé == donnée) return chain.proceed()
        return chain.withRequest(
            requête.newBuilder()
                .memoryCacheKey(clé)
                .diskCacheKey(clé)
                .build()
        ).proceed()
    }
}

/** Journal de développement : source du résultat, clé stable et
 *  échantillonnage — jamais l'URL brute. Quand la clé n'a pas été réécrite
 *  (URL non signée, forme inconnue), on ne journalise que l'hôte : une URL
 *  signée sous une forme non reconnue ne doit pas fuiter dans logcat. */
private class JournalImages : EventListener() {
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        val donnée = request.data
        val clé = when {
            donnée !is String -> result.diskCacheKey ?: "?"
            IdentiteMedias.clé(donnée) != donnée -> IdentiteMedias.clé(donnée)
            else -> runCatching { java.net.URI(donnée).host ?: "?" }.getOrDefault("?")
        }
        Log.d(
            "Médias",
            "source=${result.dataSource} clé=$clé échantillon=${result.isSampled}"
        )
    }
}

/**
 * Chargeur d'images de l'application, à installer via
 * `SingletonImageLoader.setSafe` au démarrage (issue #108) : clés de cache
 * stables hors URL signée, plus un journal des sources en développement.
 */
fun fabriquerChargeur(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(ClesMediasStables()) }
        .apply { if (BuildConfig.DEBUG) eventListener(JournalImages()) }
        .build()
