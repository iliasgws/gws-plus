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
 * Un ViewModel par écran (DESIGN.md §5). État unique par VM, suspend dans
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
)

class MessagesViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(MessagesÉtat())
    val état: StateFlow<MessagesÉtat> = _état.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.conversations.isEmpty()) }
            try {
                val conversations = container.messages.conversations()
                val contact = runCatching { container.messages.contact() }.getOrNull()
                _état.update { it.copy(chargement = false, conversations = conversations, contact = contact) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(chargement = false, erreur = "Messages indisponibles pour le moment") }
            }
        }
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
