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
import school.greenwood.plus.model.QuizDetail
import school.greenwood.plus.model.QuizRésultat
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.model.RéponseJouée
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

/** Filtre par nature de ressource (issue #17) : quiz d'un côté, documents
 *  (tout type non quiz) de l'autre. */
enum class FiltreDocuments(val label: String) {
    Tout("Tout"),
    Quiz("Quiz"),
    Documents("Documents"),
}

/** Une ressource est un quiz si le serveur le dit (`type: "quiz"`) — les
 *  autres types (PDF, vidéos… attendus mais non observés) restent des
 *  documents, type absent compris. */
fun estQuiz(ressource: Ressource): Boolean =
    ressource.type.equals("quiz", ignoreCase = true)

data class DocumentsÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val ressources: List<Ressource> = emptyList(),
    val recherche: String = "",
    val filtre: FiltreDocuments = FiltreDocuments.Tout,
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

    fun choisirFiltre(filtre: FiltreDocuments) {
        _état.update { it.copy(filtre = filtre) }
    }

}

/** Recherche + filtre par nature, appliqués à la liste chargée (testé). */
fun filtrerRessources(
    ressources: List<Ressource>,
    recherche: String,
    filtre: FiltreDocuments,
): List<Ressource> {
    val requête = recherche.trim()
    return ressources
        .filter { r ->
            requête.isEmpty() ||
                r.label.contains(requête, ignoreCase = true) ||
                r.matiere.contains(requête, ignoreCase = true)
        }
        .filter { r ->
            when (filtre) {
                FiltreDocuments.Tout -> true
                FiltreDocuments.Quiz -> estQuiz(r)
                FiltreDocuments.Documents -> !estQuiz(r)
            }
        }
}

/** — Quiz (détail d'un quiz de l'espace documents) ------------------------- */

/** Phase de jeu : départ → question par question → résultat. */
enum class PhaseQuiz { Départ, Jeu, Résultat }

data class QuizÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val quiz: QuizDetail? = null,
    val phase: PhaseQuiz = PhaseQuiz.Départ,
    /** Index de la question en cours (phase Jeu). */
    val indexQuestion: Int = 0,
    /** Décompte restant de la question en cours, en secondes. */
    val secondesRestantes: Int? = null,
    /** Index de la réponse cliquée — fige l'écran le temps du retour visuel. */
    val réponseChoisie: Int? = null,
    /** Bonnes réponses du passage en cours. */
    val scoreLocal: Int = 0,
    /** Tentatives jouées : question → réponse (texte, secondes). */
    val jouées: Map<Int, RéponseJouée> = emptyMap(),
    val résultat: QuizRésultat? = null,
    /** POST d'enregistrement en cours ou raté (score local affiché quand même). */
    val envoiScore: Boolean = false,
    val échecEnvoi: Boolean = false,
)

class QuizViewModel(
    private val container: AppContainer,
    private val quizId: String,
) : ViewModel() {
    private val _état = MutableStateFlow(QuizÉtat())
    val état: StateFlow<QuizÉtat> = _état.asStateFlow()

    /** Questions telles que reçues du GET — l'objet POST `questions` est le
     *  tableau d'origine sérialisé, `answer` mis à jour à chaque réponse
     *  (bundle : `questions: JSON.stringify(this._result.data.questions)`). */
    private var questionsBrutes: kotlinx.serialization.json.JsonArray? = null

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = true, erreur = null) }
            try {
                val chargé = container.documents.quiz(quizId)
                questionsBrutes = chargé.questionsBrutes
                _état.update { it.copy(chargement = false, quiz = chargé.détail) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(chargement = false, erreur = "Quiz indisponible pour le moment")
                }
            }
        }
    }

    /** Démarre (ou rejoue) : remet compteur et réponses à zéro. */
    fun démarrer() {
        val quiz = _état.value.quiz ?: return
        if (quiz.questions.isEmpty()) return
        _état.update {
            it.copy(
                phase = PhaseQuiz.Jeu,
                indexQuestion = 0,
                secondesRestantes = quiz.questions.first().tempsReponse,
                réponseChoisie = null,
                scoreLocal = 0,
                jouées = emptyMap(),
                résultat = null,
                envoiScore = false,
                échecEnvoi = false,
            )
        }
    }

    /** Répondre : feedback immédiat (le serveur porte le drapeau correct),
     *  2 s d'avance automatique — fidèle au bundle (`setTimeout(…, 2e3)`). */
    fun répondre(index: Int) {
        val e = _état.value
        val question = e.quiz?.questions?.getOrNull(e.indexQuestion) ?: return
        if (e.réponseChoisie != null || e.phase != PhaseQuiz.Jeu) return
        val réponse = question.reponses.getOrNull(index) ?: return
        val secondes = question.tempsReponse?.let { total ->
            total - (e.secondesRestantes ?: total)
        } ?: 0
        _état.update {
            it.copy(
                réponseChoisie = index,
                scoreLocal = it.scoreLocal + if (réponse.correcte) 1 else 0,
                jouées = it.jouées + (it.indexQuestion to RéponseJouée(réponse.texte, secondes)),
            )
        }
    }

    /** Temps écoulé sans réponse : la question part sans choix (bundle :
     *  `answerQuestion(null, null)` — `answered` vaut alors le temps total). */
    fun tempsÉcoulé() {
        val e = _état.value
        val question = e.quiz?.questions?.getOrNull(e.indexQuestion) ?: return
        if (e.réponseChoisie != null || e.phase != PhaseQuiz.Jeu) return
        _état.update {
            it.copy(
                réponseChoisie = -1,
                jouées = it.jouées + (
                    it.indexQuestion to RéponseJouée(
                        "",
                        question.tempsReponse ?: 0,
                    )
                    ),
            )
        }
    }

    /** Une seconde de décompte (horloge de l'écran, une par seconde). */
    fun tickHorloge() = _état.update {
        it.copy(secondesRestantes = (it.secondesRestantes ?: 0) - 1)
    }

    /** Avance après le retour visuel ; au bout du quiz, enregistre. */
    fun avancer() {
        val e = _état.value
        if (e.phase != PhaseQuiz.Jeu) return
        val quiz = e.quiz ?: return
        val suivant = e.indexQuestion + 1
        if (suivant < quiz.questions.size) {
            _état.update {
                it.copy(
                    indexQuestion = suivant,
                    secondesRestantes = quiz.questions[suivant].tempsReponse,
                    réponseChoisie = null,
                )
            }
        } else {
            _état.update { it.copy(phase = PhaseQuiz.Résultat, secondesRestantes = null) }
            enregistrer()
        }
    }

    /** POST `quiz` — les champs du bundle ; un échec n'empêche pas l'affichage
     *  du score (le bundle officiel ignore lui-même l'erreur), signalé discret. */
    private fun enregistrer() {
        val e = _état.value
        val quiz = e.quiz ?: return
        val brutes = questionsBrutes ?: return
        val jouées = e.jouées
        _état.update { it.copy(envoiScore = true, échecEnvoi = false) }
        viewModelScope.launch {
            try {
                val résultat = container.documents.envoyerRésultatQuiz(quiz, brutes, jouées)
                _état.update { it.copy(envoiScore = false, résultat = résultat) }
            } catch (err: Exception) {
                _état.update { it.copy(envoiScore = false, échecEnvoi = true) }
            }
        }
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

    /** Recharge la liste. `force` ignore le cache TTL (bouton Réessayer,
     *  issue #14). En cas d'échec serveur, une liste en cache reste affichée
     *  plutôt qu'un mur d'erreur. */
    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.conversations.isEmpty()) }
            try {
                val page = container.messages.conversations(fraîche = force)
                val contact = runCatching { container.messages.contact() }.getOrNull()
                _état.update {
                    it.copy(
                        chargement = false,
                        conversations = page.conversations,
                        themes = page.themes,
                        contact = contact,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                _échec(err.messageUtilisateur)
            } catch (err: Exception) {
                _échec("Messages indisponibles pour le moment")
            }
        }
    }

    private suspend fun _échec(message: String) {
        val enCache = runCatching { container.messages.conversationsEnCache() }.getOrNull()
        _état.update {
            val àAfficher = if (it.conversations.isEmpty()) enCache?.conversations ?: emptyList() else it.conversations
            it.copy(
                chargement = false,
                conversations = àAfficher,
                themes = it.themes.ifEmpty { enCache?.themes ?: emptyList() },
                // Pas de mur d'erreur s'il reste du contenu à montrer.
                erreur = message.takeIf { _ -> àAfficher.isEmpty() },
            )
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
