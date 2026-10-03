package school.greenwood.plus.data.cache

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import school.greenwood.plus.util.CacheAudio
import school.greenwood.plus.util.Fichiers

/*
 * Purge des caches de médias binaires (issue #108) : images Coil, pièces
 * jointes privées, staging d'envoi, voix en cache et registre des
 * téléchargements publics. Tout appartient à la session — la purge part aux
 * mêmes moments que CachesSession.vider() (connexion et déconnexion,
 * AuthRepository) : un compte ne hérite jamais des médias du précédent.
 * Les fichiers de « Downloads/gws-plus » appartiennent à l'élève, eux, on n'y
 * touche pas.
 */
class PurgeMedias(private val context: Context) {

    suspend fun purger() = withContext(Dispatchers.IO) {
        runCatching {
            val chargeur = SingletonImageLoader.get(context)
            chargeur.memoryCache?.clear()
            chargeur.diskCache?.clear()
        }
        runCatching { Fichiers.dossierDocuments(context).deleteRecursively() }
        runCatching { Fichiers.dossierEnvoi(context).deleteRecursively() }
        runCatching { CacheAudio.dossier(context).deleteRecursively() }
        runCatching { Fichiers.oublierRegistrePublic(context) }
    }
}
