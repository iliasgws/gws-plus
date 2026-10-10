package school.greenwood.plus.data.session

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import school.greenwood.plus.data.ai.PresetsFournisseurs
import school.greenwood.plus.data.api.normaliserUrlServeur
import school.greenwood.plus.data.ai.RéglagesIA
import school.greenwood.plus.data.ai.TonIA
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.ParentInfo

private val Context.dataStore by preferencesDataStore(name = "gws_session")

/** État de session persistant (hors mots de passe — jamais stockés). */
data class SessionState(
    val keyToken: String,
    val userId: String,
    val parentId: String,
    val eleveId: String,
    val role: String?,
    val parent: ParentInfo?,
    val eleves: List<Eleve>,
) {
    val isAuthentifie: Boolean get() = keyToken.isNotBlank() && userId.isNotBlank()
}

/** Événements de session émis une fois, consommés par la navigation racine. */
sealed interface SessionEvent {
    data object Expirée : SessionEvent
}

@Serializable
private data class EleveStocke(
    val id: String,
    val nomcomplet: String,
    val prenom: String? = null,
    val nom: String? = null,
    val niveau: String? = null,
    val img: String? = null,
)

private object Clefs {
    val keyToken = stringPreferencesKey("key_token")
    val userId = stringPreferencesKey("user_id")
    val parentId = stringPreferencesKey("parent_id")
    val eleveId = stringPreferencesKey("eleve_id")
    val role = stringPreferencesKey("role")
    val parentNom = stringPreferencesKey("parent_nom")
    val parentImage = stringPreferencesKey("parent_image")
    val eleves = stringPreferencesKey("eleves_json")
    val eleveIndex = intPreferencesKey("eleve_index")
    val retenir = booleanPreferencesKey("retenir")
    val onboardingVu = booleanPreferencesKey("onboarding_vu")

    val ecritureNouveautes = booleanPreferencesKey("ecriture_nouveautes_activee")

    /** Actualisation au retour : minutes d'absence à partir desquelles les
     *  écrans se rafraîchissent au retour au premier plan ; 0 = « jamais ».
     *  Survit à une purge de session (préférence d'app, comme le composeur). */
    val actualisationRetour = intPreferencesKey("actualisation_retour_minutes")
    val banniereRegistre = booleanPreferencesKey("banniere_registre_activee")
    val banniereCours = booleanPreferencesKey("banniere_cours_activee")
    val banniereDevoirs = booleanPreferencesKey("banniere_devoirs_activee")
    val banniereDocuments = booleanPreferencesKey("banniere_documents_activee")
    val banniereActualites = booleanPreferencesKey("banniere_actualites_activee")

    /** Mises à jour (issue #46) : millisecondes du dernier contrôle GitHub,
     *  canal choisi — stable par défaut, bêtas sur option — et dernière
     *  publication vue (JSON) pour retrouver la carte après un redémarrage.
     *  Préférences d'app : survivent à une purge de session. */
    val majDernièreVérification = longPreferencesKey("maj_derniere_verification")
    val majCanalBêta = booleanPreferencesKey("maj_canal_beta")

    /** Chauffage global des caches (prefetch) : toutes les sections se
     *  rafraîchissent en arrière-plan à l'ouverture et à chaque actualisation.
     *  Préférence d'app : survit à une purge de session. */
    val chauffageTout = booleanPreferencesKey("chauffage_cache_active")
    val majPublicationStockée = stringPreferencesKey("maj_publication_stockee")

    /** Composeur IA (issue #56) : activation, fournisseur OpenAI-compatible
     *  (preset ou URL libre), modèle, clé BYOK et ton par défaut. La clé ne
     *  sort jamais de l'app et n'est jamais loguée (F3). Préférences d'app :
     *  survivent à une purge de session. */
    val iaActivé = booleanPreferencesKey("ia_active")
    val iaBase = stringPreferencesKey("ia_base")
    val iaModèle = stringPreferencesKey("ia_modele")
    val iaClé = stringPreferencesKey("ia_cle")
    val iaTon = stringPreferencesKey("ia_ton")

    /** Marquage « fait pour moi » des devoirs (issue #82) : identifiants des
     *  devoirs que l'utilisateur a marqués faits pour lui-même — jamais
     *  envoyés à l'école (JSON, tableau d'ids). Préférence d'app : survit à
     *  une purge de session, comme le composeur. */
    val devoirsFaitLocal = stringPreferencesKey("devoirs_fait_local_json")

    /** Serveur communautaire (issue #88) : jeton du compte local (la
     *  clé vaut le compte — jamais envoyé ailleurs que vers ce serveur),
     *  version de notice servie / acceptée, URL du serveur (réglable
     *  dans les Paramètres, vide = non configuré) et votes locaux par
     *  devoir (JSON, « id » → ±1) affichés en attendant le total serveur.
     *  Préférences d'app : survivent à une purge de session. */
    val commJeton = stringPreferencesKey("communautaire_jeton")
    val commMentionsDemandée = stringPreferencesKey("communautaire_mentions_demandee")
    val commMentionsAcceptée = stringPreferencesKey("communautaire_mentions_acceptee")
    val commUrl = stringPreferencesKey("communautaire_url")
    val commVotes = stringPreferencesKey("communautaire_votes_json")
}

/**
 * Purge des champs de session école (issues #139/#140) : appelée à la
 * déconnexion, à l'expiration de session, et avant une session « ne pas
 * retenir ». Le jeton Boti, l'identité scolaire et l'index d'enfant partent ;
 * les préférences d'app (bannières, IA, mises à jour, composeur…) et le
 * compte communautaire indépendant ne sont JAMAIS touchés ici.
 * Pur JVM (MutablePreferences) — testable sans Context.
 */
internal fun MutablePreferences.purgerSessionÉcole() {
    listOf(
        Clefs.keyToken,
        Clefs.userId,
        Clefs.parentId,
        Clefs.eleveId,
        Clefs.role,
        Clefs.parentNom,
        Clefs.parentImage,
        Clefs.eleves,
        Clefs.eleveIndex,
    ).forEach { remove(it) }
}

/** Écriture persistante d'une session école (jeton déjà scellé). */
internal fun MutablePreferences.écrireSessionÉcole(
    keyTokenScellé: String,
    userId: String,
    parentId: String,
    eleveId: String,
    role: String?,
    parentNom: String?,
    parentImage: String?,
    elevesJson: String,
    retenir: Boolean,
) {
    this[Clefs.keyToken] = keyTokenScellé
    this[Clefs.userId] = userId
    this[Clefs.parentId] = parentId
    this[Clefs.eleveId] = eleveId
    this[Clefs.role] = role ?: ""
    this[Clefs.parentNom] = parentNom ?: ""
    this[Clefs.parentImage] = parentImage ?: ""
    this[Clefs.eleves] = elevesJson
    this[Clefs.eleveIndex] = 0
    this[Clefs.retenir] = retenir
}

class SessionStore(private val context: Context) : CompteCommunautaire {

    private val json = Json { ignoreUnknownKeys = true }
    private val secrets = SessionSecrets()

    /**
     * Session éphémérique (issue #140) : quand « Rester connecté » est
     * décoché, le jeton ne quitte jamais la mémoire du processus — rien
     * n'est écrit sur disque pour une reconnexion automatique, et un
     * redémarrage de l'application impose une nouvelle connexion.
     */
    private val éphémère = MutableStateFlow<SessionState?>(null)

    init {
        // Re-encrypt credentials saved by older releases without invalidating sessions.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                context.dataStore.edit { p ->
                    listOf(Clefs.keyToken, Clefs.iaClé, Clefs.commJeton).forEach { key ->
                        valeurÀMigrer(p[key]) { clair -> secrets.seal(clair) }?.let { scellé ->
                            p[key] = scellé
                        }
                    }
                }
            }
        }
    }

    val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)

    /** État de session : la session mémoire (« ne pas retenir ») prime sur
     *  la persistée ; déchiffrement déporté sur IO (issue #140). */
    val state: Flow<SessionState?> = combine(
        context.dataStore.data.map { p -> sessionPersistante(p) },
        éphémère,
    ) { persistante, mémoire -> mémoire ?: persistante }
        .flowOn(Dispatchers.IO)

    private fun sessionPersistante(p: androidx.datastore.preferences.core.Preferences): SessionState? {
        val key = secrets.open(p[Clefs.keyToken] ?: "")
        val user = p[Clefs.userId] ?: ""
        if (key.isBlank() || user.isBlank()) return null
        return SessionState(
            keyToken = key,
            userId = user,
            parentId = p[Clefs.parentId] ?: "",
            eleveId = p[Clefs.eleveId] ?: "",
            role = p[Clefs.role],
            parent = p[Clefs.parentNom]?.let {
                ParentInfo(id = p[Clefs.parentId] ?: "", nomComplet = it, image = p[Clefs.parentImage])
            },
            eleves = p[Clefs.eleves]?.let { brut ->
                runCatching {
                    json.decodeFromString<List<EleveStocke>>(brut).map {
                        Eleve(it.id, it.nomcomplet, it.prenom, it.nom, it.niveau, it.img)
                    }
                }.getOrDefault(emptyList())
            } ?: emptyList(),
        )
    }

    val eleveIndex: Flow<Int> = context.dataStore.data.map { it[Clefs.eleveIndex] ?: 0 }

    val retenir: Flow<Boolean> = context.dataStore.data.map { it[Clefs.retenir] ?: true }

    val onboardingVu: Flow<Boolean> = context.dataStore.data.map { it[Clefs.onboardingVu] ?: false }

    val ecritureNouveautesActivée: Flow<Boolean> = context.dataStore.data.map { it[Clefs.ecritureNouveautes] ?: false }

    suspend fun définirEcritureNouveautes(actif: Boolean) {
        context.dataStore.edit { it[Clefs.ecritureNouveautes] = actif }
    }

    /** Minutes d'absence déclenchant l'actualisation au retour (0 = jamais). */
    val actualisationRetour: Flow<Int> = context.dataStore.data.map { it[Clefs.actualisationRetour] ?: 5 }

    suspend fun définirActualisationRetour(minutes: Int) {
        context.dataStore.edit { it[Clefs.actualisationRetour] = minutes }
    }

    /** Préférence d'apparence de l'app, conservée à la déconnexion. */
    val bannièreRegistreActivée: Flow<Boolean> =
        context.dataStore.data.map { it[Clefs.banniereRegistre] ?: true }

    suspend fun définirBannièreRegistre(actif: Boolean) {
        context.dataStore.edit { it[Clefs.banniereRegistre] = actif }
    }

    val bannièreCoursActivée: Flow<Boolean> =
        context.dataStore.data.map { it[Clefs.banniereCours] ?: true }

    suspend fun définirBannièreCours(actif: Boolean) {
        context.dataStore.edit { it[Clefs.banniereCours] = actif }
    }

    val bannièreDevoirsActivée: Flow<Boolean> = context.dataStore.data.map { it[Clefs.banniereDevoirs] ?: true }
    val bannièreDocumentsActivée: Flow<Boolean> = context.dataStore.data.map { it[Clefs.banniereDocuments] ?: true }
    val bannièreActualitésActivée: Flow<Boolean> = context.dataStore.data.map { it[Clefs.banniereActualites] ?: true }

    suspend fun définirBannièreDevoirs(actif: Boolean) {
        context.dataStore.edit { it[Clefs.banniereDevoirs] = actif }
    }

    suspend fun définirBannièreDocuments(actif: Boolean) {
        context.dataStore.edit { it[Clefs.banniereDocuments] = actif }
    }

    suspend fun définirBannièreActualités(actif: Boolean) {
        context.dataStore.edit { it[Clefs.banniereActualites] = actif }
    }

    /** Millisecondes du dernier contrôle de mise à jour (null = jamais). */
    val majDernièreVérification: Flow<Long?> = context.dataStore.data.map { it[Clefs.majDernièreVérification] }

    suspend fun définirMajDernièreVérification(millis: Long) {
        context.dataStore.edit { it[Clefs.majDernièreVérification] = millis }
    }

    /** Chauffage global des caches : activé par défaut ; coupé, chaque écran
     *  ne rafraîchit que ce qu'il affiche. */
    val chauffageToutActivé: Flow<Boolean> =
        context.dataStore.data.map { it[Clefs.chauffageTout] ?: true }

    suspend fun définirChauffageTout(actif: Boolean) {
        context.dataStore.edit { it[Clefs.chauffageTout] = actif }
    }

    /** Canal de mise à jour : stable par défaut, bêtas sur option (issue #46). */
    val majCanalBêta: Flow<Boolean> = context.dataStore.data.map { it[Clefs.majCanalBêta] ?: false }

    suspend fun définirMajCanalBêta(actif: Boolean) {
        context.dataStore.edit { it[Clefs.majCanalBêta] = actif }
    }

    /** Dernière publication vue (JSON de PublicationStockée), pour retrouver
     *  la carte « Mise à jour disponible » après un redémarrage. */
    val majPublicationStockée: Flow<String?> = context.dataStore.data.map { it[Clefs.majPublicationStockée] }

    suspend fun définirMajPublicationStockée(json: String) {
        context.dataStore.edit { it[Clefs.majPublicationStockée] = json }
    }

    /** Réglages du composeur IA (issue #56), observables d'un seul flux. */
    val réglagesIA: Flow<RéglagesIA> = context.dataStore.data.map { p ->
        RéglagesIA(
            actif = p[Clefs.iaActivé] ?: false,
            base = p[Clefs.iaBase] ?: PresetsFournisseurs.first().base,
            modèle = p[Clefs.iaModèle] ?: "",
            clé = secrets.open(p[Clefs.iaClé] ?: ""),
            ton = p[Clefs.iaTon]?.let { nom -> TonIA.entries.firstOrNull { it.name == nom } }
                ?: TonIA.AMICAL,
        )
    }.flowOn(Dispatchers.IO)

    suspend fun définirRéglagesIA(réglages: RéglagesIA) {
        context.dataStore.edit { p ->
            p[Clefs.iaActivé] = réglages.actif
            p[Clefs.iaBase] = réglages.base
            p[Clefs.iaModèle] = réglages.modèle
            p[Clefs.iaClé] = secrets.seal(réglages.clé)
            p[Clefs.iaTon] = réglages.ton.name
        }
    }

    /** Ids des devoirs marqués « fait pour moi » (issue #82) — local
     *  uniquement, jamais envoyés à l'école. */
    val devoirsFaitLocal: Flow<Set<String>> = context.dataStore.data.map { p ->
        p[Clefs.devoirsFaitLocal]?.let { brut ->
            runCatching {
                json.decodeFromString<List<String>>(brut).toSet()
            }.getOrDefault(emptySet())
        } ?: emptySet()
    }

    /** Marque (ou démarque) un devoir « fait pour moi » — réversible d'un
     *  clic, contrairement au fait serveur. */
    suspend fun marquerDevoirFaitLocal(id: String, fait: Boolean) {
        context.dataStore.edit { p ->
            val ids = p[Clefs.devoirsFaitLocal]?.let { brut ->
                runCatching { json.decodeFromString<List<String>>(brut) }.getOrDefault(emptyList())
            } ?: emptyList()
            val nouveau = if (fait) ids + id else ids - id
            p[Clefs.devoirsFaitLocal] = json.encodeToString(nouveau.distinct())
        }
    }

    // — Serveur communautaire (issue #88) ----------------------------------

    /** URL du serveur communautaire (vide = non configuré — réglable dans
     *  les Paramètres), normalisée à l'écriture. */
    val urlCommunautaire: Flow<String> =
        context.dataStore.data.map { it[Clefs.commUrl] ?: "" }

    suspend fun définirUrlCommunautaire(brut: String) {
        context.dataStore.edit { it[Clefs.commUrl] = normaliserUrlServeur(brut) }
    }

    /** Présence d'un compte (jeton) — observée par les Paramètres. */
    val jetonCommunautaire: Flow<String?> =
        context.dataStore.data
            .map { secrets.open(it[Clefs.commJeton] ?: "").takeIf { jeton -> jeton.isNotBlank() } }
            .flowOn(Dispatchers.IO)

    /** Votes locaux ±1 par devoir (affichage immédiat — le total définitif
     *  revient du serveur, jamais incrémenté localement). */
    val votesLocaux: Flow<Map<String, Int>> = context.dataStore.data.map { p ->
        p[Clefs.commVotes]?.let { brut ->
            runCatching { json.decodeFromString<Map<String, Int>>(brut) }.getOrDefault(emptyMap())
        } ?: emptyMap()
    }

    suspend fun noterVoteLocal(id: Long, vote: Int) {
        context.dataStore.edit { p ->
            val votes = p[Clefs.commVotes]?.let { brut ->
                runCatching { json.decodeFromString<Map<String, Int>>(brut) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            p[Clefs.commVotes] = json.encodeToString(votes + (id.toString() to vote))
        }
    }

    private suspend fun oublierVotesLocaux() {
        context.dataStore.edit { it.remove(Clefs.commVotes) }
    }

    // — CompteCommunautaire (APP.md §2) ------------------------------------

    override suspend fun jeton(): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        secrets.open(context.dataStore.data.first()[Clefs.commJeton] ?: "").takeIf { it.isNotBlank() }
    }

    override suspend fun enregistrerJeton(jeton: String, mentionsVersion: String?) {
        context.dataStore.edit { p ->
            p[Clefs.commJeton] = secrets.seal(jeton)
            p[Clefs.commMentionsDemandée] = mentionsVersion ?: ""
            p.remove(Clefs.commMentionsAcceptée)
            p.remove(Clefs.commVotes) // identité neuve : les anciens votes ne sont plus à nous
        }
    }

    override suspend fun mentionsDemandée(): String? =
        context.dataStore.data.first()[Clefs.commMentionsDemandée]?.takeIf { it.isNotBlank() }

    override suspend fun mentionsAcceptée(): String? =
        context.dataStore.data.first()[Clefs.commMentionsAcceptée]?.takeIf { it.isNotBlank() }

    override suspend fun noterVersionServie(version: String) {
        context.dataStore.edit { it[Clefs.commMentionsDemandée] = version }
    }

    override suspend fun accepterNotices(version: String) {
        context.dataStore.edit { it[Clefs.commMentionsAcceptée] = version }
    }

    override suspend fun oublierJeton() {
        context.dataStore.edit { p ->
            p.remove(Clefs.commJeton)
            p.remove(Clefs.commMentionsAcceptée)
            p.remove(Clefs.commVotes)
        }
    }

    override suspend fun oublierTout() {
        context.dataStore.edit { p ->
            p.remove(Clefs.commJeton)
            p.remove(Clefs.commMentionsDemandée)
            p.remove(Clefs.commMentionsAcceptée)
            p.remove(Clefs.commVotes)
        }
    }

    /**
     * Enregistre la session (issues #138/#140). « Rester connecté » coché :
     * jeton scellé sur disque, reconnexion automatique au prochain
     * démarrage. Décoché : RIEN de la session n'est écrit sur disque — la
     * session vit uniquement en mémoire du processus ; un redémarrage
     * d'application (ou un changement d'utilisateur système) impose une
     * nouvelle connexion.
     */
    suspend fun enregistrer(
        keyToken: String,
        userId: String,
        parentId: String,
        eleveId: String,
        role: String?,
        parent: ParentInfo?,
        eleves: List<Eleve>,
        retenir: Boolean,
    ) {
        val elevesJson = json.encodeToString(
            eleves.map {
                EleveStocke(it.id, it.nomComplet, it.prenom, it.nom, it.niveau, it.image)
            },
        )
        if (retenir) {
            // Une éventuelle session mémoire précédente meurt ici.
            éphémère.value = null
            context.dataStore.edit { p ->
                p.écrireSessionÉcole(
                    keyTokenScellé = secrets.seal(keyToken),
                    userId = userId,
                    parentId = parentId,
                    eleveId = eleveId,
                    role = role,
                    parentNom = parent?.nomComplet,
                    parentImage = parent?.image,
                    elevesJson = elevesJson,
                    retenir = true,
                )
            }
        } else {
            // Session éphémère : purge de toute trace persistée (y compris
            // d'une session retenue antérieure), la session reste en mémoire.
            context.dataStore.edit { p ->
                p.purgerSessionÉcole()
                p[Clefs.retenir] = false
            }
            éphémère.value = SessionState(
                keyToken = keyToken,
                userId = userId,
                parentId = parentId,
                eleveId = eleveId,
                role = role,
                parent = parent,
                eleves = eleves,
            )
        }
    }

    suspend fun choisirEleve(index: Int) {
        context.dataStore.edit { it[Clefs.eleveIndex] = index }
    }

    suspend fun marquerOnboardingVu() {
        context.dataStore.edit { it[Clefs.onboardingVu] = true }
    }

    /**
     * Purge complète — session tuée ou déconnexion (issues #139/#140) :
     * la session mémoire meurt en même temps que la persistée ; le compte
     * communautaire indépendant et les préférences d'app sont conservés.
     */
    suspend fun effacer() {
        éphémère.value = null
        context.dataStore.edit { p ->
            p.purgerSessionÉcole()
            p[Clefs.onboardingVu] = true
        }
    }

    suspend fun signalerExpiration() {
        events.emit(SessionEvent.Expirée)
    }
}
