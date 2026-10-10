package school.greenwood.plus.data.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import school.greenwood.plus.data.session.SessionSecrets
import java.io.File

/*
 * Cache de données sur disque (hors-ligne) : la dernière réponse JSON utile
 * de chaque section, scellée avec les secrets de session et portant la clé
 * « userId/eleveId » qui l'a écrite. Le réseau reste la source de vérité —
 * le disque ne sert qu'à ouvrir l'application déjà remplie après un
 * redémarrage hors connexion, et à relire ce qu'on a déjà vu.
 *
 * Garde-fous, identiques à l'instantané du registre (issue #145) :
 * - `noBackupFilesDir` : jamais dans les sauvegardes ni les transferts
 *   Android ;
 * - scellé par `SessionSecrets` (Keystore) : illisible sans la clé ;
 * - clé de session dans l'enveloppe : un autre compte/l'enfant lit null ;
 * - écriture atomique (fichier temporaire puis remplacement) ;
 * - toute lecture qui échoue rend null, jamais une exception ;
 * - purgé avec les caches mémoire, à la connexion comme à la déconnexion ;
 * - jamais écrit quand « Rester connecté » est décoché (issue #140).
 */
class CacheDisque(private val dossier: File) {

    /** Chiffreur injectable pour les tests JVM ; la production utilise le
     *  Keystore (second constructeur interne, même module). */
    private var secrets: SessionSecrets = SessionSecrets()

    internal constructor(dossier: File, secrets: SessionSecrets) : this(dossier) {
        this.secrets = secrets
    }

    constructor(context: Context) : this(File(context.noBackupFilesDir, DOSSIER))

    /** Écriture atomique ; un échec (disque plein, chiffreur) est muet. */
    suspend fun écrire(nom: String, clé: String, données: JsonElement) = withContext(Dispatchers.IO) {
        runCatching {
            val scellé = secrets.seal(
                json.encodeToString(Enveloppe.serializer(), Enveloppe(NOM_VERSION, nom, clé, données)),
            )
            dossier.mkdirs()
            val cible = fichier(nom)
            val temporaire = File(cible.parentFile, cible.name + ".tmp")
            temporaire.writeText(scellé, Charsets.UTF_8)
            if (!temporaire.renameTo(cible)) {
                cible.delete()
                temporaire.renameTo(cible)
            }
        }
        Unit
    }

    /** Dernière entrée pour [nom], seulement si elle appartient à [clé]. */
    suspend fun lire(nom: String, clé: String): JsonElement? = withContext(Dispatchers.IO) {
        val brut = runCatching {
            val cible = fichier(nom)
            if (cible.isFile) cible.readText(Charsets.UTF_8) else null
        }.getOrNull() ?: return@withContext null
        if (brut.isEmpty()) return@withContext null
        val ouvert = secrets.open(brut)
        if (ouvert.isEmpty()) return@withContext null
        runCatching { json.decodeFromString(Enveloppe.serializer(), ouvert) }
            .getOrNull()
            ?.takeIf { it.version == NOM_VERSION && it.nom == nom && it.clé == clé }
            ?.données
    }

    /** Purge totale — appelée avec les caches mémoire (connexion/déconnexion). */
    suspend fun vider() = withContext(Dispatchers.IO) {
        runCatching { dossier.deleteRecursively() }
        Unit
    }

    /** Nom de fichier sûr (le nom logique peut porter des caractères de
     *  paramètres) — la collision éventuelle est tranchée par le `nom` de
     *  l'enveloppe à la lecture. */
    private fun fichier(nom: String): File {
        val propre = nom.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        return File(dossier, "$propre-${Integer.toHexString(nom.hashCode())}.enc")
    }

    @Serializable
    private data class Enveloppe(
        val version: Int,
        val nom: String,
        val clé: String,
        val données: JsonElement,
    )

    private companion object {
        const val DOSSIER = "caches-disque"
        const val NOM_VERSION = 1
        val json = Json { ignoreUnknownKeys = true }
    }
}
