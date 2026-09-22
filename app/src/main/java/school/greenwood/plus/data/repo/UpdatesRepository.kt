package school.greenwood.plus.data.repo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import school.greenwood.plus.BuildConfig
import school.greenwood.plus.MainActivity
import school.greenwood.plus.R
import school.greenwood.plus.data.session.SessionStore
import school.greenwood.plus.util.Fichiers
import java.io.File
import java.util.concurrent.TimeUnit

/*
 * Mises à jour de l'app, sans serveur (issue #46) : l'app interroge l'API
 * GitHub Releases du dépôt public (aucun jeton, aucune donnée personnelle
 * envoyée — l'adresse IP seule, comme tout client HTTP). Contrôle au
 * démarrage (au plus une fois par 12 h), bouton « Vérifier » dans les
 * Paramètres, carte « Mise à jour disponible » sur le Registre,
 * notification locale si la permission est accordée. Le téléchargement
 * passe par le lien direct de la ressource GitHub puis le lecteur
 * d'installation du système — jamais de serveur tiers.
 */

/** Version comparable : « 0.7.1 » et « 0.7.1-beta.1 » (une bêta précède sa
 *  stable). Tolère l'attribut « v » des tags GitHub (« v0.7.1 »). */
data class VersionGws(
    val majeur: Int,
    val mineur: Int,
    val correctif: Int,
    val estBêta: Boolean,
    val numéroBêta: Int,
) : Comparable<VersionGws> {

    override fun compareTo(other: VersionGws): Int = compareValuesBy(
        this, other,
        { it.majeur }, { it.mineur }, { it.correctif },
        // Une stable dépasse toute bêta de mêmes nombres ; bêta N < bêta N+1.
        { if (it.estBêta) -1 else 1 },
        { it.numéroBêta },
    )

    override fun toString(): String =
        "$majeur.$mineur.$correctif" + if (estBêta) "-bêta $numéroBêta" else ""

    companion object {
        private val FORME = Regex(
            """^[vV]?(\d+)\.(\d+)\.(\d+)(?:[-._]?(?:bêta|beta|pre)[-._]?(\d+))?${'$'}""",
        )

        fun parser(texte: String?): VersionGws? {
            if (texte.isNullOrBlank()) return null
            val m = FORME.find(texte.trim()) ?: return null
            val (maj, min, cor, bêta) = m.destructured
            return VersionGws(
                majeur = maj.toInt(),
                mineur = min.toInt(),
                correctif = cor.toInt(),
                estBêta = m.groupValues[4].isNotEmpty(),
                numéroBêta = bêta.toIntOrNull() ?: 0,
            )
        }
    }
}

/** Une publication GitHub pertinente : la version, son canal, les notes
 *  résumées, la page publique et le lien direct de l'APK. */
data class PublicationGws(
    val version: VersionGws,
    val estBêta: Boolean,
    val notes: String?,
    val page: String,
    val apkUrl: String?,
    val nomApk: String?,
)

/** État partagé de la mise à jour — porté par le dépôt, observé par le
 *  Registre (carte) et les Paramètres (contrôle manuel). */
data class MiseÀJourÉtat(
    /** La publication à installer — null si l'app est à jour (ou rien vu). */
    val disponible: PublicationGws? = null,
    /** Un contrôle réseau tourne. */
    val contrôle: Boolean = false,
    /** Un téléchargement tourne ou vient de finir. */
    val téléchargement: TéléchargementMaj = TéléchargementMaj.Inactif,
    /** Suivre les bêtas (préversions) — réglage Paramètres. */
    val canalBêta: Boolean = false,
    /** Dernier contrôle — millis, pour l'affichage « vérifié il y a … ». */
    val dernierContrôle: Long? = null,
    /** Message d'échec du dernier contrôle (bouton « Vérifier » seulement —
     *  le contrôle au démarrage échoue en silence). */
    val erreur: String? = null,
)

sealed interface TéléchargementMaj {
    data object Inactif : TéléchargementMaj
    data object EnCours : TéléchargementMaj
    data class Réussi(val fichier: File) : TéléchargementMaj
    data object Échec : TéléchargementMaj
}

/** Publication persistée (DataStore) pour retrouver la carte « Mise à jour
 *  disponible » après la mort du processus, même si le contrôle réseau est
 *  encore throttlé — la comparaison à la version installée fait foi. */
@Serializable
private data class PublicationStockée(
    val version: String,
    val estBêta: Boolean,
    val notes: String?,
    val page: String,
    val apkUrl: String?,
    val nomApk: String?,
) {
    fun enPublication(): PublicationGws? = PublicationGws(
        version = VersionGws.parser(version) ?: return null,
        estBêta = estBêta,
        notes = notes,
        page = page,
        apkUrl = apkUrl,
        nomApk = nomApk,
    )

    companion object {
        fun de(publication: PublicationGws) = PublicationStockée(
            version = publication.version.let { "${it.majeur}.${it.mineur}.${it.correctif}" +
                if (it.estBêta) "-beta.${it.numéroBêta}" else "" },
            estBêta = publication.estBêta,
            notes = publication.notes,
            page = publication.page,
            apkUrl = publication.apkUrl,
            nomApk = publication.nomApk,
        )
    }
}

class UpdatesRepository(
    private val contexte: Context,
    private val session: SessionStore,
) {

    companion object {
        /** Dépôt public — la seule dépendance externe de l'app avec l'école. */
        const val DÉPÔT = "iliasgws/gws-plus"
        private const val API_RELEASES = "https://api.github.com/repos/$DÉPÔT/releases?per_page=10"

        /** La page publique des publications — aussi ouverte par les
         *  Paramètres depuis la ligne « Version installée ». */
        const val PAGE_RELEASES = "https://github.com/$DÉPÔT/releases"

        /** Contrôle au démarrage : au plus une fois par 12 h (issue #46). */
        private const val INTERVALLE_MILLIS = 12L * 60 * 60 * 1000

        private const val CANAL_ID = "mises-a-jour"

        private val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private val json = Json { ignoreUnknownKeys = true }

        /** Version installée — comparée à la dernière publication. */
        val versionActuelle: VersionGws? = VersionGws.parser(BuildConfig.VERSION_NAME)

        /** La publication à proposer : la version la plus haute parmi les
         *  bêtas (canal bêta, opt-in) ou parmi les stables (canal stable).
         *  L'ordre de la liste ne fait pas foi — GitHub trie par date de
         *  création, une stable peut y suivre sa propre bêta. */
        internal fun choisirPublication(
            publications: List<PublicationGws>,
            canalBêta: Boolean,
        ): PublicationGws? {
            val candidates = if (canalBêta) publications else publications.filterNot { it.estBêta }
            return candidates.maxByOrNull { it.version }
        }

        /** Lit le corps JSON de l'API Releases → publications lisibles (ordre
         *  du serveur conservé : la plus récente d'abord). */
        internal fun publicationsDuCorps(corps: String): List<PublicationGws> =
            json.parseToJsonElement(corps).jsonArray
                .mapNotNull { (it as? JsonObject)?.let(::publication) }

        private fun publication(o: JsonObject): PublicationGws? {
            val tag = o["tag_name"]?.let { it.jsonPrimitive.contentOrNull } ?: return null
            val version = VersionGws.parser(tag) ?: return null
            val bêta = o["prerelease"]?.let { it.jsonPrimitive.booleanOrNull } ?: version.estBêta
            val page = o["html_url"]?.let { it.jsonPrimitive.contentOrNull } ?: PAGE_RELEASES
            val notes = o["body"]?.let { it.jsonPrimitive.contentOrNull }
            val assets = o["assets"] as? JsonArray ?: JsonArray(emptyList())
            val pièce = assets
                .mapNotNull { (it as? JsonObject)?.let(::asset) }
                .firstOrNull { it.second.endsWith(".apk", ignoreCase = true) }
            return PublicationGws(
                version = version,
                estBêta = bêta,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                page = page,
                apkUrl = pièce?.first,
                nomApk = pièce?.second,
            )
        }

        /** Seul l'APK signé du projet intéresse (« GWS-v….apk ») — les .idsig
         *  et pièces tierces sont ignorés. */
        private fun asset(o: JsonObject): Pair<String, String>? {
            val nom = o["name"]?.let { it.jsonPrimitive.contentOrNull } ?: return null
            val url = o["browser_download_url"]?.let { it.jsonPrimitive.contentOrNull } ?: return null
            if (!nom.startsWith("GWS-") || !nom.endsWith(".apk")) return null
            return url to nom
        }
    }

    private val _état = MutableStateFlow(MiseÀJourÉtat())
    val état: StateFlow<MiseÀJourÉtat> = _état.asStateFlow()

    /** Démarrage de l'app : reprend la publication connue (persistée), puis
     *  vérifie seulement si le dernier contrôle date de plus de 12 h.
     *  Échec silencieux — on retentera à la prochaine ouverture. */
    suspend fun vérifierAuBesoin() {
        val bêta = session.majCanalBêta.first()
        // Carte retrouvée depuis le DataStore — la comparaison à la version
        // installée décide de l'affichage (l'app peut avoir été mise à jour
        // pendant l'absence du processus).
        session.majPublicationStockée.first()?.let { brut ->
            runCatching { json.decodeFromString<PublicationStockée>(brut).enPublication() }.getOrNull()
        }?.let { pub ->
            if (versionActuelle != null && pub.version > versionActuelle) {
                _état.update { it.copy(disponible = pub, canalBêta = bêta) }
            }
        }
        val dernier = session.majDernièreVérification.first()
        if (dernier != null && System.currentTimeMillis() - dernier < INTERVALLE_MILLIS) return
        vérifier(manuel = false)
    }

    /** Contrôle explicite (Paramètres) — les erreurs y sont affichées. */
    suspend fun vérifier(manuel: Boolean = true) {
        if (_état.value.téléchargement is TéléchargementMaj.EnCours) return
        val bêta = session.majCanalBêta.first()
        _état.update { it.copy(contrôle = true, canalBêta = bêta, erreur = null) }
        try {
            val publications = withContext(Dispatchers.IO) { récupérerPublications() }
            val courante = versionActuelle
            val pertinente = choisirPublication(publications, bêta)
            val plusRécente = pertinente != null && courante != null && pertinente.version > courante
            _état.update {
                it.copy(
                    contrôle = false,
                    disponible = pertinente.takeIf { plusRécente },
                    dernierContrôle = System.currentTimeMillis(),
                    erreur = null,
                )
            }
            val maintenant = System.currentTimeMillis()
            session.définirMajDernièreVérification(maintenant)
            pertinente?.let { pub ->
                session.définirMajPublicationStockée(
                    json.encodeToString(PublicationStockée.de(pub)),
                )
            }
            if (plusRécente && pertinente != null) notifierSiAutorisé(pertinente)
        } catch (err: Exception) {
            _état.update {
                it.copy(
                    contrôle = false,
                    dernierContrôle = System.currentTimeMillis(),
                    erreur = if (manuel) "Vérification impossible pour le moment" else null,
                )
            }
            if (!manuel) return // silence au démarrage, retenté à la prochaine ouverture
            session.définirMajDernièreVérification(System.currentTimeMillis())
        }
    }

    suspend fun définirCanalBêta(actif: Boolean) {
        session.définirMajCanalBêta(actif)
        _état.update { it.copy(canalBêta = actif) }
        // Le canal peut faire apparaître ou disparaître la publication.
        vérifier(manuel = false)
    }

    /** Télécharge l'APK depuis le lien direct GitHub et prépare le fichier. */
    suspend fun télécharger(): File? {
        val publication = _état.value.disponible ?: return null
        val url = publication.apkUrl ?: return null
        _état.update { it.copy(téléchargement = TéléchargementMaj.EnCours) }
        return try {
            val fichier = Fichiers.télécharger(
                contexte,
                url,
                publication.nomApk ?: "GWS-${publication.version}.apk",
            )
            _état.update { it.copy(téléchargement = TéléchargementMaj.Réussi(fichier)) }
            fichier
        } catch (err: Exception) {
            _état.update { it.copy(téléchargement = TéléchargementMaj.Échec) }
            null
        }
    }

    fun remettreÀZéroTéléchargement() {
        _état.update { it.copy(téléchargement = TéléchargementMaj.Inactif) }
    }

    /** Le clic unique (issue #46) : autorisation « apps inconnues » vérifiée,
     *  téléchargement, puis installateur du système. Retourne false quand
     *  l'autorisation manque (le réglage système est ouvert à la place) ou
     *  si le téléchargement échoue — l'état porte l'erreur. */
    suspend fun mettreÀJour(): Boolean {
        if (!peutInstaller()) {
            ouvrirRéglageInstallation()
            return false
        }
        val fichier = télécharger() ?: return false
        return lancerInstallation(fichier)
    }

    /** Relance l'installateur sur un APK déjà téléchargé (bouton « Installer »
     *  après un retour depuis l'installateur). */
    fun relancerInstallation(): Boolean {
        val fichier = (_état.value.téléchargement as? TéléchargementMaj.Réussi)?.fichier
            ?: return false
        return lancerInstallation(fichier)
    }

    private fun lancerInstallation(fichier: File): Boolean = runCatching {
        contexte.startActivity(Fichiers.intentionInstaller(contexte, fichier))
        true
    }.getOrDefault(false)

    /** L'autorisation « installer des apps inconnues » pour GWS+ — accordée
     *  une fois, depuis le réglage système ouvert par [ouvrirRéglageInstallation]. */
    fun peutInstaller(): Boolean = contexte.packageManager.canRequestPackageInstalls()

    fun ouvrirRéglageInstallation() {
        runCatching {
            contexte.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${contexte.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // — GitHub ------------------------------------------------------------

    private fun récupérerPublications(): List<PublicationGws> {
        val req = Request.Builder()
            .url(API_RELEASES)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GWSPlus/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("GitHub ${resp.code}")
            return publicationsDuCorps(resp.body.string())
        }
    }

    // — Notification locale ------------------------------------------------

    /** Permission de notification — implicite sous Android 13, demandée
     *  depuis les Paramètres au-delà (issue #46). */
    fun notificationsAccordées(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                contexte,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    /** Notification locale quand une mise à jour apparaît — seulement si la
     *  permission est déjà accordée (demandée dans les Paramètres). */
    private fun notifierSiAutorisé(publication: PublicationGws) {
        if (!notificationsAccordées()) return
        val gestionnaire = contexte.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            gestionnaire.createNotificationChannel(
                NotificationChannel(
                    CANAL_ID,
                    "Mises à jour de GWS+",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val ouvrirApp = PendingIntent.getActivity(
            contexte,
            0,
            Intent(contexte, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(contexte, CANAL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Mise à jour disponible — ${publication.version}")
            .setContentText("GWS+ ${publication.version} est en ligne. Touchez pour mettre à jour.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(publication.notes?.prendreRésumé() ?: "Touchez pour ouvrir GWS+ et mettre à jour."),
            )
            .setContentIntent(ouvrirApp)
            .setAutoCancel(true)
            .build()
        runCatching { gestionnaire.notify(publication.version.hashCode(), notification) }
    }
}

/** Résumé court des notes de publication pour la notification. */
fun String.prendreRésumé(limite: Int = 220): String =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("SHA-256") }
        .joinToString(" ")
        .take(limite)
        .let { if (it.length == limite) it.substringBeforeLast(' ') + "…" else it }
