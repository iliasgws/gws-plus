package school.greenwood.plus.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

class SessionStore(private val context: Context) {

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

        /** Composeur activé (issue #10) — désormais par défaut. L'envoi réel a
         *  été validé en conditions réelles (test du 19/09/2026) ; le réglage
         *  sert d'interrupteur, pas de verrou. Survit à une purge de session. */
        val composeurActivé = booleanPreferencesKey("composeur_active")
    }

    val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)

    val state: Flow<SessionState?> = context.dataStore.data.map { p ->
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

    val eleveIndex: Flow<Int> = context.dataStore.data.map { it[Clefs.eleveIndex] ?: 0 }

    val retenir: Flow<Boolean> = context.dataStore.data.map { it[Clefs.retenir] ?: true }

    val onboardingVu: Flow<Boolean> = context.dataStore.data.map { it[Clefs.onboardingVu] ?: false }

    val composeurActivé: Flow<Boolean> = context.dataStore.data.map { it[Clefs.composeurActivé] ?: true }

    suspend fun définirComposeur(actif: Boolean) {
        context.dataStore.edit { it[Clefs.composeurActivé] = actif }
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
        context.dataStore.edit { p ->
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
        context.dataStore.edit { it[Clefs.eleveIndex] = index }
    }

    suspend fun marquerOnboardingVu() {
        context.dataStore.edit { it[Clefs.onboardingVu] = true }
    }

    /** Purge complète — session tuée ou déconnexion. */
    suspend fun effacer() {
        context.dataStore.edit { p ->
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
