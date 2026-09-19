package school.greenwood.plus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import school.greenwood.plus.AppContainer
import school.greenwood.plus.data.api.BotiErreur
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.model.BilanAbsences
import school.greenwood.plus.model.ContactEcole
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.Ressource
import java.time.LocalDate

/*
 * Un ViewModel par écran (docs/product/DESIGN.md §5). État unique par VM, suspend dans
 * viewModelScope, erreurs en message lisible pour l'écran.
 */

/** — Connexion ---------------------------------------------------------- */
data class ConnexionÉtat(
    val téléphone: String = "",
    val motDePasse: String = "",
    val retenir: Boolean = true,
    val chargement: Boolean = false,
    val erreur: String? = null,
    val rappelEnvoyé: Boolean = false,
)

class ConnexionViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(ConnexionÉtat())
    val état: StateFlow<ConnexionÉtat> = _état.asStateFlow()

    fun modifierTéléphone(valeur: String) = _état.update { it.copy(téléphone = valeur) }
    fun modifierMotDePasse(valeur: String) = _état.update { it.copy(motDePasse = valeur) }
    fun modifierRetenir(valeur: Boolean) = _état.update { it.copy(retenir = valeur) }

    fun seConnecter() {
        val e = _état.value
        if (e.chargement) return
        if (e.téléphone.isBlank() || e.motDePasse.isBlank()) {
            _état.update { it.copy(erreur = "Numéro et mot de passe requis") }
            return
        }
        _état.update { it.copy(chargement = true, erreur = null) }
        viewModelScope.launch {
            try {
                container.auth.connexion(e.téléphone, e.motDePasse, e.retenir)
                // l'état de session bascule l'écran racine
                _état.update { it.copy(chargement = false) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(chargement = false, erreur = "Connexion impossible — réessaie")
                }
            }
        }
    }

    fun demanderRappel() {
        val e = _état.value
        if (e.téléphone.isBlank()) {
            _état.update { it.copy(erreur = "Indique ton numéro pour le rappel") }
            return
        }
        viewModelScope.launch {
            try {
                container.auth.motDePasseOublié(e.téléphone)
                _état.update {
                    it.copy(
                        rappelEnvoyé = true,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update { it.copy(erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(erreur = "Rappel impossible pour le moment") }
            }
        }
    }

}

/** — Registre ------------------------------------------------------------ */
data class RegistreÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val registre: RegistreDuJour? = null,
    val eleve: Eleve? = null,
    val eleves: List<Eleve> = emptyList(),
    val posts: List<Post> = emptyList(),
    val bilans: BilanAbsences? = null,
)

class RegistreViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(RegistreÉtat())
    val état: StateFlow<RegistreÉtat> = _état.asStateFlow()

    init {
        viewModelScope.launch {
            val s = container.session.state.first()
            _état.value = _état.value.copy(
                eleve = s?.eleves?.firstOrNull { it.id == s.eleveId } ?: s?.eleves?.firstOrNull(),
                eleves = s?.eleves ?: emptyList(),
            )
            charger()
        }
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.registre == null, erreur = null) }
            try {
                val registre = container.registre.charger()
                _état.update { it.copy(chargement = false, registre = registre) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(chargement = false, erreur = "Le registre n'a pas pu être chargé")
                }
            }
        }
    }

    fun choisirEleve(eleve: Eleve) {
        viewModelScope.launch {
            val s = container.session.state.first() ?: return@launch
            val index = s.eleves.indexOfFirst { it.id == eleve.id }.takeIf { it >= 0 } ?: 0
            container.session.choisirEleve(index)
            _état.update { it.copy(eleve = eleve) }
            charger()
        }
    }

    fun corpsPost(id: String, onCorps: (String?) -> Unit) {
        viewModelScope.launch {
            onCorps(container.nouveautes.corps(id))
        }
    }

}

/** — Devoirs ------------------------------------------------------------- */
data class DevoirsÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val tous: List<Devoir> = emptyList(),
    val jourChoisi: LocalDate = LocalDate.now(),
)

class DevoirsViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(DevoirsÉtat())
    val état: StateFlow<DevoirsÉtat> = _état.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.tous.isEmpty()) }
            try {
                val tous = container.devoirs.liste()
                _état.update { it.copy(chargement = false, tous = tous) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(chargement = false, erreur = "Devoirs indisponibles pour le moment") }
            }
        }
    }

    fun choisirJour(date: LocalDate) {
        _état.update { it.copy(jourChoisi = date) }
    }

    fun téléchargerPièceJointe(devoir: Devoir, url: String, nom: String, context: android.content.Context, onFait: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            val fichier = try {
                school.greenwood.plus.util.Fichiers.télécharger(context, url, nom)
            } catch (err: Exception) {
                null
            }
            onFait(fichier)
        }
    }

}

/** — Documents ----------------------------------------------------------- */
data class DocumentsÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val ressources: List<Ressource> = emptyList(),
    val recherche: String = "",
)

class DocumentsViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(DocumentsÉtat())
    val état: StateFlow<DocumentsÉtat> = _état.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.ressources.isEmpty()) }
            try {
                val ressources = container.documents.ressources()
                _état.update { it.copy(chargement = false, ressources = ressources) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(chargement = false, erreur = "Documents indisponibles pour le moment") }
            }
        }
    }

    fun modifierRecherche(valeur: String) {
        _état.update { it.copy(recherche = valeur) }
    }

}

/** — Messages ------------------------------------------------------------ */
data class MessagesÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val contact: ContactEcole? = null,
    /** Catégories du composeur (serveur themes[]). */
    val themes: List<school.greenwood.plus.model.ThemeMessage> = emptyList(),
    /** Composeur actif par défaut (issue #10, envoi validé le 19/09/2026). */
    val composeurActivé: Boolean = false,
)

class MessagesViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(MessagesÉtat())
    val état: StateFlow<MessagesÉtat> = _état.asStateFlow()

    init {
        charger()
        viewModelScope.launch {
            container.session.composeurActivé.collect { actif ->
                _état.update { it.copy(composeurActivé = actif) }
            }
        }
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.conversations.isEmpty()) }
            try {
                val page = container.messages.conversations()
                val contact = runCatching { container.messages.contact() }.getOrNull()
                _état.update {
                    it.copy(
                        chargement = false,
                        conversations = page.conversations,
                        themes = page.themes,
                        contact = contact,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(chargement = false, erreur = "Messages indisponibles pour le moment") }
            }
        }
    }

    /** Interrupteur du composeur — persistant (issue #10). */
    fun définirComposeur(actif: Boolean) {
        viewModelScope.launch { container.session.définirComposeur(actif) }
    }

}

/** — Conversation (détail d'un fil) --------------------------------------- */
data class ConversationÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val conversation: Conversation? = null,
    /** Composeur (collecté depuis la session — actif par défaut). */
    val composeurActif: Boolean = false,
    val texte: String = "",
    val pièces: List<java.io.File> = emptyList(),
    val audio: java.io.File? = null,
    val enregistre: Boolean = false,
    /** Envois optimistes : affichés au bas du fil jusqu'à confirmation serveur,
     *  marqués Échec (relance manuelle) si le POST rate. */
    val envois: List<school.greenwood.plus.model.MessageEnvoi> = emptyList(),
)

class ConversationViewModel(
    private val container: AppContainer,
    private val conversationId: String,
) : ViewModel() {
    private val _état = MutableStateFlow(ConversationÉtat())
    val état: StateFlow<ConversationÉtat> = _état.asStateFlow()

    private var enregistreur: school.greenwood.plus.util.EnregistreurAudio? = null

    init {
        charger()
        viewModelScope.launch {
            container.session.composeurActivé.collect { actif ->
                _état.update { it.copy(composeurActif = actif) }
            }
        }
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.conversation == null, erreur = null) }
            try {
                val conversation = container.messages.conversation(conversationId)
                _état.update { it.copy(chargement = false, conversation = conversation) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(chargement = false, erreur = "Messages indisponibles pour le moment") }
            }
        }
    }

    /** — Composeur --------------------------------------------------------- */

    fun modifierTexte(valeur: String) = _état.update { it.copy(texte = valeur) }

    fun retirerPièce(fichier: java.io.File) =
        _état.update { it.copy(pièces = it.pièces - fichier) }

    fun ajouterPièces(context: android.content.Context, uris: List<android.net.Uri>) {
        viewModelScope.launch {
            val copiées = uris.mapNotNull { school.greenwood.plus.util.Fichiers.copierDepuisSaf(context, it) }
            _état.update { it.copy(pièces = it.pièces + copiées) }
        }
    }

    fun démarrerEnregistrement(context: android.content.Context): Boolean {
        val rec = enregistreur ?: school.greenwood.plus.util.EnregistreurAudio(context).also { enregistreur = it }
        val ok = rec.démarrer()
        if (ok) _état.update { it.copy(enregistre = true) }
        return ok
    }

    fun arrêterEnregistrement() {
        val fichier = enregistreur?.arrêter()
        _état.update { it.copy(enregistre = false, audio = fichier) }
    }

    fun annulerEnregistrement() {
        enregistreur?.annuler()
        _état.update { it.copy(enregistre = false, audio = null) }
    }

    fun retirerAudio() = _état.update { it.copy(audio = null) }

    /** Envoi : push optimiste au bas du fil, puis remplacement par la version
     *  serveur (`conversation[index] = _.message` du bundle). Un envoi à la
     *  fois ; en échec l'élément reste affiché, marqué, relançable. */
    fun envoyer() {
        val e = _état.value
        if (e.envois.any { it.statut == school.greenwood.plus.model.MessageEnvoi.Statut.EnCours }) return
        val texte = e.texte.trim()
        if (texte.isEmpty() && e.pièces.isEmpty() && e.audio == null) return
        val envoi = school.greenwood.plus.model.MessageEnvoi(
            texte = texte,
            pièces = e.pièces,
            audio = e.audio,
        )
        _état.update { it.copy(texte = "", pièces = emptyList(), audio = null, envois = it.envois + envoi) }
        lancerEnvoi(envoi)
    }

    fun relancer(envoi: school.greenwood.plus.model.MessageEnvoi) {
        _état.update { st ->
            st.copy(
                envois = st.envois.map {
                    if (it == envoi) it.copy(statut = school.greenwood.plus.model.MessageEnvoi.Statut.EnCours) else it
                },
            )
        }
        lancerEnvoi(envoi)
    }

    private fun lancerEnvoi(envoi: school.greenwood.plus.model.MessageEnvoi) {
        viewModelScope.launch {
            try {
                val conversation = _état.value.conversation ?: return@launch
                val servi = container.messages.envoyerRéponse(
                    conversation = conversation,
                    texte = envoi.texte,
                    pièces = envoi.pièces,
                    audio = envoi.audio,
                )
                _état.update { st ->
                    val fil = servi?.let { m ->
                        conversation.copy(messages = conversation.messages + m)
                    } ?: conversation
                    st.copy(envois = st.envois - envoi, conversation = fil)
                }
                if (servi == null) charger() // réponse sans .message : refetch
            } catch (err: Exception) {
                _état.update { st ->
                    st.copy(
                        envois = st.envois.map {
                            if (it == envoi) {
                                it.copy(statut = school.greenwood.plus.model.MessageEnvoi.Statut.Échec)
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onCleared() {
        enregistreur?.annuler()
        super.onCleared()
    }

    fun téléchargerPièce(
        pièce: school.greenwood.plus.model.Attachment,
        context: android.content.Context,
        onFait: (java.io.File?) -> Unit,
    ) {
        viewModelScope.launch {
            val fichier = try {
                school.greenwood.plus.util.Fichiers.télécharger(context, pièce.url, pièce.name)
            } catch (err: Exception) {
                null
            }
            onFait(fichier)
        }
    }
}

/** — Nouveau message (fil vierge vers l'administration) -------------------- */
data class NouveauMessageÉtat(
    val sujet: String = "",
    val texte: String = "",
    val themeChoisi: school.greenwood.plus.model.ThemeMessage? = null,
    val themes: List<school.greenwood.plus.model.ThemeMessage> = emptyList(),
    val pièces: List<java.io.File> = emptyList(),
    val audio: java.io.File? = null,
    val enregistre: Boolean = false,
    val envoi: Boolean = false,
    val erreur: String? = null,
    val envoyé: Boolean = false,
)

class NouveauMessageViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(NouveauMessageÉtat())
    val état: StateFlow<NouveauMessageÉtat> = _état.asStateFlow()

    private var enregistreur: school.greenwood.plus.util.EnregistreurAudio? = null

    init {
        viewModelScope.launch {
            try {
                val page = container.messages.conversations()
                _état.update { it.copy(themes = page.themes) }
            } catch (err: Exception) {
                // Les catégories manquantes ne bloquent pas l'envoi : champ
                // libre. Le fil s'ouvre quand même.
            }
        }
    }

    fun modifierSujet(valeur: String) = _état.update { it.copy(sujet = valeur) }
    fun modifierTexte(valeur: String) = _état.update { it.copy(texte = valeur) }
    fun choisirTheme(theme: school.greenwood.plus.model.ThemeMessage?) =
        _état.update { it.copy(themeChoisi = theme) }

    fun ajouterPièces(context: android.content.Context, uris: List<android.net.Uri>) {
        viewModelScope.launch {
            val refusées = mutableListOf<String>()
            val copiées = uris.mapNotNull { uri ->
                val fichier = school.greenwood.plus.util.Fichiers.copierDepuisSaf(context, uri)
                    ?: return@mapNotNull null
                if (school.greenwood.plus.util.Fichiers.dépasseLimite1Mo(fichier)) {
                    fichier.delete()
                    refusées.add(fichier.name)
                    null
                } else {
                    fichier
                }
            }
            _état.update {
                it.copy(
                    pièces = it.pièces + copiées,
                    erreur = refusées.takeIf { r -> r.isNotEmpty() }?.joinToString(
                        prefix = "Pièce trop lourde (1 Mo max) : ",
                        separator = ", ",
                    ),
                )
            }
        }
    }

    fun retirerPièce(fichier: java.io.File) = _état.update { it.copy(pièces = it.pièces - fichier) }

    fun retirerAudio() = _état.update { it.copy(audio = null) }

    fun démarrerEnregistrement(context: android.content.Context): Boolean {
        val rec = enregistreur ?: school.greenwood.plus.util.EnregistreurAudio(context).also { enregistreur = it }
        val ok = rec.démarrer()
        if (ok) _état.update { it.copy(enregistre = true) }
        return ok
    }

    fun arrêterEnregistrement() {
        val fichier = enregistreur?.arrêter()
        _état.update { it.copy(enregistre = false, audio = fichier) }
    }

    fun annulerEnregistrement() {
        enregistreur?.annuler()
        _état.update { it.copy(enregistre = false, audio = null) }
    }

    fun envoyer() {
        val e = _état.value
        if (e.envoi) return
        if (e.sujet.isBlank() || e.texte.isBlank()) {
            _état.update { it.copy(erreur = "Sujet et message requis") }
            return
        }
        _état.update { it.copy(envoi = true, erreur = null) }
        viewModelScope.launch {
            try {
                container.messages.envoyerNouveau(
                    sujet = e.sujet.trim(),
                    texte = e.texte.trim(),
                    theme = e.themeChoisi?.id ?: "",
                    pièces = e.pièces,
                    audio = e.audio,
                )
                _état.update { it.copy(envoi = false, envoyé = true) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(envoi = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(envoi = false, erreur = "Envoi impossible — réessaie") }
            }
        }
    }

    override fun onCleared() {
        enregistreur?.annuler()
        super.onCleared()
    }
}

/** — Demandes ------------------------------------------------------------ */
data class DemandesÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val demandes: List<Demande> = emptyList(),
)

class DemandesViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(DemandesÉtat())
    val état: StateFlow<DemandesÉtat> = _état.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.demandes.isEmpty()) }
            try {
                val demandes = container.demandes.liste()
                _état.update { it.copy(chargement = false, demandes = demandes) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(chargement = false, erreur = "Demandes indisponibles pour le moment") }
            }
        }
    }

}
