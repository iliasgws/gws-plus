package school.greenwood.plus.data.session

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import school.greenwood.plus.data.ai.PresetsFournisseurs
import school.greenwood.plus.data.ai.RéglagesIA
import school.greenwood.plus.data.ai.TonIA
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.ParentInfo

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

class SessionStore(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true }

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

        /** Mises à jour (issue #46) : millisecondes du dernier contrôle GitHub,
         *  canal choisi — stable par défaut, bêtas sur option — et dernière
         *  publication vue (JSON) pour retrouver la carte après un redémarrage.
         *  Préférences d'app : survivent à une purge de session. */
        val majDernièreVérification = longPreferencesKey("maj_derniere_verification")
        val majCanalBêta = booleanPreferencesKey("maj_canal_beta")
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
    }

    val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)

    val state: Flow<SessionState?> = dataStore.data.map { p ->
        val key = p[Clefs.keyToken] ?: ""
        val user = p[Clefs.userId] ?: ""
        if (key.isBlank() || user.isBlank()) return@map null
        SessionState(
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

    val eleveIndex: Flow<Int> = dataStore.data.map { it[Clefs.eleveIndex] ?: 0 }

    val retenir: Flow<Boolean> = dataStore.data.map { it[Clefs.retenir] ?: true }

    val onboardingVu: Flow<Boolean> = dataStore.data.map { it[Clefs.onboardingVu] ?: false }

    val ecritureNouveautesActivée: Flow<Boolean> = dataStore.data.map { it[Clefs.ecritureNouveautes] ?: false }

    suspend fun définirEcritureNouveautes(actif: Boolean) {
        dataStore.edit { it[Clefs.ecritureNouveautes] = actif }
    }

    /** Minutes d'absence déclenchant l'actualisation au retour (0 = jamais). */
    val actualisationRetour: Flow<Int> = dataStore.data.map { it[Clefs.actualisationRetour] ?: 5 }

    suspend fun définirActualisationRetour(minutes: Int) {
        dataStore.edit { it[Clefs.actualisationRetour] = minutes }
    }

    /** Millisecondes du dernier contrôle de mise à jour (null = jamais). */
    val majDernièreVérification: Flow<Long?> = dataStore.data.map { it[Clefs.majDernièreVérification] }

    suspend fun définirMajDernièreVérification(millis: Long) {
        dataStore.edit { it[Clefs.majDernièreVérification] = millis }
    }

    /** Canal de mise à jour : stable par défaut, bêtas sur option (issue #46). */
    val majCanalBêta: Flow<Boolean> = dataStore.data.map { it[Clefs.majCanalBêta] ?: false }

    suspend fun définirMajCanalBêta(actif: Boolean) {
        dataStore.edit { it[Clefs.majCanalBêta] = actif }
    }

    /** Dernière publication vue (JSON de PublicationStockée), pour retrouver
     *  la carte « Mise à jour disponible » après un redémarrage. */
    val majPublicationStockée: Flow<String?> = dataStore.data.map { it[Clefs.majPublicationStockée] }

    suspend fun définirMajPublicationStockée(json: String) {
        dataStore.edit { it[Clefs.majPublicationStockée] = json }
    }

    /** Réglages du composeur IA (issue #56), observables d'un seul flux. */
    val réglagesIA: Flow<RéglagesIA> = dataStore.data.map { p ->
        RéglagesIA(
            actif = p[Clefs.iaActivé] ?: false,
            base = p[Clefs.iaBase] ?: PresetsFournisseurs.first().base,
            modèle = p[Clefs.iaModèle] ?: "",
            clé = p[Clefs.iaClé] ?: "",
            ton = p[Clefs.iaTon]?.let { nom -> TonIA.entries.firstOrNull { it.name == nom } }
                ?: TonIA.AMICAL,
        )
    }

    suspend fun définirRéglagesIA(réglages: RéglagesIA) {
        dataStore.edit { p ->
            p[Clefs.iaActivé] = réglages.actif
            p[Clefs.iaBase] = réglages.base
            p[Clefs.iaModèle] = réglages.modèle
            p[Clefs.iaClé] = réglages.clé
            p[Clefs.iaTon] = réglages.ton.name
        }
    }

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
        dataStore.edit { p ->
            p[Clefs.keyToken] = keyToken
            p[Clefs.userId] = userId
            p[Clefs.parentId] = parentId
            p[Clefs.eleveId] = eleveId
            p[Clefs.role] = role ?: ""
            p[Clefs.parentNom] = parent?.nomComplet ?: ""
            p[Clefs.parentImage] = parent?.image ?: ""
            p[Clefs.eleves] = json.encodeToString(
                eleves.map {
                    EleveStocke(it.id, it.nomComplet, it.prenom, it.nom, it.niveau, it.image)
                },
            )
            p[Clefs.retenir] = retenir
        }
    }

    suspend fun choisirEleve(index: Int) {
        dataStore.edit { it[Clefs.eleveIndex] = index }
    }

    /** Change the pupil used by API requests in the desktop client. */
    suspend fun sélectionnerÉlève(index: Int) {
        val élève = state.first()?.eleves?.getOrNull(index) ?: return
        dataStore.edit {
            it[Clefs.eleveIndex] = index
            it[Clefs.eleveId] = élève.id
        }
    }

    suspend fun marquerOnboardingVu() {
        dataStore.edit { it[Clefs.onboardingVu] = true }
    }

    /** Purge complète — session tuée ou déconnexion. */
    suspend fun effacer() {
        dataStore.edit { p ->
            p.remove(Clefs.keyToken)
            p.remove(Clefs.userId)
            p.remove(Clefs.parentId)
            p.remove(Clefs.eleveId)
            p.remove(Clefs.role)
            p.remove(Clefs.parentNom)
            p.remove(Clefs.parentImage)
            p.remove(Clefs.eleves)
            p[Clefs.onboardingVu] = true
        }
    }

    suspend fun signalerExpiration() {
        events.emit(SessionEvent.Expirée)
    }
}
