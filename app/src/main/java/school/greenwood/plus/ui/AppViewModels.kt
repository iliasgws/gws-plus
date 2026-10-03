package school.greenwood.plus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import school.greenwood.plus.AppContainer
import school.greenwood.plus.data.ai.RéglagesIA
import school.greenwood.plus.data.api.BotiErreur
import school.greenwood.plus.data.api.CommunErreur
import school.greenwood.plus.data.api.DépassementDébit
import school.greenwood.plus.data.api.JetonRévoqué
import school.greenwood.plus.data.repo.NoticeRequise
import school.greenwood.plus.data.repo.SensSemaine
import school.greenwood.plus.data.repo.RegistreDuJour
import school.greenwood.plus.data.session.SessionState
import school.greenwood.plus.model.BilanAbsences
import school.greenwood.plus.model.CantineJour
import school.greenwood.plus.model.CibleSignalement
import school.greenwood.plus.model.CommandeBoutique
import school.greenwood.plus.model.ContactEcole
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.CorrectionHoraire
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.DevoirSuggéré
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.FicheBibliotheque
import school.greenwood.plus.model.FiltreHoraire
import school.greenwood.plus.model.Mentions
import school.greenwood.plus.model.ProblèmeHoraire
import school.greenwood.plus.model.ProduitBoutique
import school.greenwood.plus.model.ProduitDétail
import school.greenwood.plus.model.RésultatCommande
import school.greenwood.plus.model.RubriqueBoutique
import school.greenwood.plus.data.repo.Normalizers
import school.greenwood.plus.ui.screens.messages.refusCatégorieNouveau
import school.greenwood.plus.ui.screens.messages.refusCatégorieRéponse
import school.greenwood.plus.ui.screens.messages.themeDeRéponse
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.PostDetail
import school.greenwood.plus.model.QuizDetail
import school.greenwood.plus.model.QuizRésultat
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.model.RéponseJouée
import school.greenwood.plus.model.SemaineCours
import school.greenwood.plus.model.SignalementAbus
import school.greenwood.plus.model.TriDevoirs
import school.greenwood.plus.util.dateSaisieVersIso
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
    val derniereActualite: Post? = null,
    /** Un rafraîchissement réseau tourne pendant que le contenu connu reste affiché. */
    val rafraîchissement: Boolean = false,
    /** Déconnexion en cours (issue #101) : l'action reste désactivée jusqu'à
     *  la purge de session — jamais deux appels de suite. */
    val déconnexionEnCours: Boolean = false,
)

class RegistreViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(RegistreÉtat())
    val état: StateFlow<RegistreÉtat> = _état.asStateFlow()

    init {
        viewModelScope.launch {
            appliquerSession(container.session.state.first())
            charger()
        }
        // La session pilote l'écran (issue #101) : la purge — déconnexion ou
        // expiration — vide l'état, aucune donnée du compte précédent ne
        // survit à la bascule vers la connexion ; une reconnexion relance le
        // chargement (ce VM, porté par l'activité, survit à la racine).
        viewModelScope.launch {
            var purgée = false
            container.session.state.collect { s ->
                if (s == null) {
                    if (!purgée) {
                        purgée = true
                        _état.value = RegistreÉtat(chargement = true)
                    }
                } else if (purgée) {
                    purgée = false
                    appliquerSession(s)
                    charger()
                }
            }
        }
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
    }

    /** Adopte la session courante — ou rien du tout si elle a été purgée. */
    private fun appliquerSession(s: SessionState?, eleveChoisi: String? = s?.eleveId) {
        if (s == null) {
            _état.value = RegistreÉtat(chargement = true)
            return
        }
        _état.update { st ->
            st.copy(
                eleve = s.eleves.firstOrNull { it.id == eleveChoisi } ?: s.eleves.firstOrNull(),
                eleves = s.eleves,
            )
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            // Hors session (issue #101) : rien à charger, pas d'erreur à
            // projeter sur un écran qui n'est plus le sien.
            if (container.session.state.first() == null) return@launch
            // issue #21 : préremplissage depuis le cache (lecture silencieuse,
            // jamais d'erreur projetée dans l'UI) — le squelette cède la place
            // aux dernières données connues, puis le réseau rafraîchit en fond.
            if (!force) {
                val enCache = runCatching { container.registre.registreEnCache() }.getOrNull()
                val derniereCache = runCatching { container.nouveautes.listeEnCache()?.let(Normalizers::dernière) }.getOrNull()
                if (enCache != null || derniereCache != null) {
                    _état.update { st ->
                        st.copy(
                            registre = st.registre ?: enCache,
                            derniereActualite = st.derniereActualite ?: derniereCache,
                        )
                    }
                }
            }
            // chargement = rien à montrer (squelette) ; sinon rafraîchissement
            // en fond, le contenu affiché reste en place.
            _état.update {
                it.copy(
                    chargement = it.registre == null,
                    rafraîchissement = it.registre != null,
                )
            }
            try {
                // Les photos de profil stockées à la connexion sont signées
                // et expirent. Renouveler les liens sans masquer le contenu
                // connu ; hors réseau, garder la session et ses caches.
                container.auth.validerSession()?.let { s ->
                    appliquerSession(s, _état.value.eleve?.id ?: s.eleveId)
                }
                if (container.session.state.first() == null) return@launch
                val registre = container.registre.charger()
                val derniere = runCatching { container.nouveautes.dernière() }.getOrNull()
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        registre = registre,
                        derniereActualite = derniere ?: it.derniereActualite,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                // Échec : le contenu connu reste affiché, l'erreur est toujours
                // signalée (bannière non bloquante, issue #21).
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = err.messageUtilisateur)
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = "Le registre n'a pas pu être chargé",
                    )
                }
            }
        }
    }

    /** Geste « tirer vers le bas » de l'accueil (issue #104) : toujours un
     *  chargement réseau — la lecture mémoire ne peut pas répondre à la place
     *  de l'utilisateur. Le contenu connu reste affiché pendant l'opération. */
    fun rafraîchir() = charger(force = true)

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

    /**
     * Déconnexion (issue #101) : `AuthRepository.déconnexion()` — POST
     * `logout` en best effort, purge des caches et de la session, même si
     * le réseau échoue. Portée ViewModel (activité) : l'appel va au bout
     * même si la feuille quitte l'écran en chemin ; la purge fait basculer
     * la racine sur la connexion. Un seul appel à la fois.
     */
    fun déconnexion() {
        if (_état.value.déconnexionEnCours) return
        _état.update { it.copy(déconnexionEnCours = true) }
        viewModelScope.launch {
            runCatching { container.auth.déconnexion() }
            _état.update { it.copy(déconnexionEnCours = false) }
        }
    }

}

/** — Devoirs ------------------------------------------------------------- */
data class DevoirsÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val tous: List<Devoir> = emptyList(),
    val propositionsCommunautaires: List<DevoirSuggéré> = emptyList(),
    val jourChoisi: LocalDate = LocalDate.now(),
    /** Un rafraîchissement réseau tourne pendant que le contenu connu reste affiché. */
    val rafraîchissement: Boolean = false,
)

class DevoirsViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(DevoirsÉtat())
    val état: StateFlow<DevoirsÉtat> = _état.asStateFlow()

    init {
        charger()
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
        // Un devoir vient d'être soumis depuis le détail (issue #68) : la liste
        // se rafraîchit pour que la puce « Travail fait » soit à jour au retour.
        viewModelScope.launch {
            container.devoirsModifiés.collect { id ->
                if (id != null) charger(force = true)
            }
        }
        viewModelScope.launch {
            container.devoirsCommunautairesModifiés.collect { proposition ->
                _état.update { st ->
                    st.copy(propositionsCommunautaires =
                        listOf(proposition) + st.propositionsCommunautaires.filterNot { it.id == proposition.id })
                }
            }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            chargerPropositions()
            // issue #21 : préremplissage depuis le cache (lecture silencieuse),
            // puis rafraîchissement réseau en fond — le contenu connu reste
            // affiché et les listes ne sont jamais vidées entre deux états.
            if (!force) {
                val enCache = runCatching { container.devoirs.listeEnCache() }.getOrNull()
                if (enCache != null) {
                    _état.update { st -> if (st.tous.isEmpty()) st.copy(tous = enCache) else st }
                }
            }
            // chargement = rien à montrer (squelette) ; sinon rafraîchissement
            // en fond, le contenu affiché reste en place.
            _état.update {
                it.copy(
                    chargement = it.tous.isEmpty(),
                    rafraîchissement = it.tous.isNotEmpty(),
                )
            }
            try {
                val tous = container.devoirs.liste()
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        tous = tous,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                // Échec : le contenu connu reste affiché, l'erreur est toujours
                // signalée (bannière non bloquante, issue #21).
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = err.messageUtilisateur)
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = "Devoirs indisponibles pour le moment",
                    )
                }
            }
        }
    }

    private fun chargerPropositions() {
        viewModelScope.launch {
            runCatching { container.communaute.devoirs(limite = 100).éléments }
                .onSuccess { propositions -> _état.update { it.copy(propositionsCommunautaires = propositions) } }
        }
    }

    fun choisirJour(date: LocalDate) {
        _état.update { it.copy(jourChoisi = date) }
    }

    /** Marquage « fait pour moi » (issue #82) : purement local — jamais envoyé
     *  à l'école, réversible d'un clic. La liste affichée suit aussitôt. */
    fun basculerFaitLocal(devoir: Devoir) {
        viewModelScope.launch {
            runCatching { container.devoirs.basculerFaitLocal(devoir.id) }.onSuccess { fait ->
                _état.update { st ->
                    st.copy(tous = st.tous.map {
                        if (it.id == devoir.id) it.copy(faitLocal = fait) else it
                    })
                }
            }
        }
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
    /** Fiches de la Bibliothèque (documents des enseignants, issue #43). */
    val bibliotheque: List<FicheBibliotheque> = emptyList(),
    val recherche: String = "",
    val filtre: FiltreDocuments = FiltreDocuments.Tout,
    /** Un rafraîchissement réseau tourne pendant que le contenu connu reste affiché. */
    val rafraîchissement: Boolean = false,
    /** Fiches en cours de téléchargement — id → en cours (issue #43). */
    val téléchargementsFiche: Map<String, Boolean> = emptyMap(),
)

class DocumentsViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(DocumentsÉtat())
    val état: StateFlow<DocumentsÉtat> = _état.asStateFlow()

    init {
        charger()
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            // issue #21 : préremplissage depuis le cache (lecture silencieuse),
            // puis rafraîchissement réseau en fond — le contenu connu reste
            // affiché et les listes ne sont jamais vidées entre deux états.
            if (!force) {
                val enCache = runCatching { container.documents.ressourcesEnCache() }.getOrNull()
                val fichesEnCache = runCatching { container.documents.bibliothequeEnCache() }.getOrNull()
                _état.update { st ->
                    st.copy(
                        ressources = st.ressources.ifEmpty { enCache ?: st.ressources },
                        bibliotheque = st.bibliotheque.ifEmpty { fichesEnCache ?: st.bibliotheque },
                    )
                }
            }
            // chargement = rien à montrer (squelette) ; sinon rafraîchissement
            // en fond, le contenu affiché reste en place.
            _état.update {
                it.copy(
                    chargement = it.ressources.isEmpty() && it.bibliotheque.isEmpty(),
                    rafraîchissement = it.ressources.isNotEmpty() || it.bibliotheque.isNotEmpty(),
                )
            }
            // Deux sources indépendantes (issue #43) : exercices interactifs
            // (`ressources_v2`) et Bibliothèque des enseignants (`bibliotheque`).
            // Un échec sur l'une n'efface pas l'autre — le contenu connu reste
            // affiché et l'erreur est toujours signalée (issue #21).
            val résultatRessources = async { runCatching { container.documents.ressources() } }
            val résultatFiches = async { runCatching { container.documents.bibliotheque() } }
            val ressources = résultatRessources.await()
            val fiches = résultatFiches.await()
            val erreurs = listOfNotNull(
                messageÉchec(ressources, "Documents indisponibles pour le moment"),
                messageÉchec(fiches, "Bibliothèque indisponible pour le moment"),
            ).distinct()
            _état.update {
                it.copy(
                    chargement = false,
                    rafraîchissement = false,
                    ressources = ressources.getOrDefault(it.ressources),
                    bibliotheque = fiches.getOrDefault(it.bibliotheque),
                    erreur = erreurs.takeIf { liste -> liste.isNotEmpty() }?.joinToString(" · "),
                )
            }
        }
    }

    /** Message utilisateur d'un échec de source — null si la source a réussi. */
    private fun messageÉchec(
        résultat: Result<*>,
        messageGénérique: String,
    ): String? = when (val err = résultat.exceptionOrNull()) {
        null -> null
        is BotiErreur -> err.messageUtilisateur
        else -> messageGénérique
    }

    /**
     * Télécharge une fiche de la Bibliothèque et l'ouvre dans le lecteur du
     * système (issue #43). Deux temps : le détail (`ressource_details`) porte
     * l'URL média signée — la fiche de liste n'a qu'un nom de fichier — puis
     * le téléchargement classique (Fichiers). En échec : onFait(null), l'écran
     * montre l'état d'échec.
     */
    fun téléchargerFiche(fiche: FicheBibliotheque, context: android.content.Context, onFait: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            _état.update { it.copy(téléchargementsFiche = it.téléchargementsFiche + (fiche.id to true)) }
            val fichier = try {
                val détail = container.documents.détailFiche(fiche.id)
                val pièce = détail?.fichiers?.firstOrNull() ?: error("aucune pièce jointe signée")
                school.greenwood.plus.util.Fichiers.télécharger(context, pièce.url, pièce.name)
            } catch (err: Exception) {
                null
            }
            _état.update { it.copy(téléchargementsFiche = it.téléchargementsFiche - fiche.id) }
            onFait(fichier)
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

/** Recherche + filtre pour les fiches de la Bibliothèque (issue #43) : les
 *  fiches sont des documents — visibles sous « Tout » et « Documents »,
 *  masquées sous « Quiz ». */
fun filtrerFiches(
    fiches: List<FicheBibliotheque>,
    recherche: String,
    filtre: FiltreDocuments,
): List<FicheBibliotheque> {
    val requête = recherche.trim()
    return fiches
        .filter { f ->
            requête.isEmpty() ||
                f.titre.contains(requête, ignoreCase = true) ||
                f.matiere.contains(requête, ignoreCase = true)
        }
        .filter { f ->
            when (filtre) {
                FiltreDocuments.Tout -> true
                FiltreDocuments.Quiz -> false
                FiltreDocuments.Documents -> true
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

    /** Le quiz quitte l'écran : plus rien à protéger (signal pour la coquille). */
    override fun onCleared() {
        container.quizEnJeu.value = false
        super.onCleared()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = true, erreur = null) }
            container.quizEnJeu.value = false
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

    /** Démarre (ou rejoue) : remet compteur et réponses à zéro. La partie
     *  protège désormais sa sortie (signal vu par la coquille). */
    fun démarrer() {
        val quiz = _état.value.quiz ?: return
        if (quiz.questions.isEmpty()) return
        container.quizEnJeu.value = true
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
            container.quizEnJeu.value = false
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
    /** Un rafraîchissement réseau tourne pendant que le contenu connu reste affiché. */
    val rafraîchissement: Boolean = false,
)

class MessagesViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(MessagesÉtat())
    val état: StateFlow<MessagesÉtat> = _état.asStateFlow()

    init {
        charger()
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
    }

    /** Recharge la liste. `force` ignore le cache TTL (bouton Réessayer,
     *  issue #14) et saute le préremplissage depuis le cache (issue #21).
     *  En cas d'échec, la liste connue reste affichée et l'erreur est
     *  signalée par une bannière non bloquante — jamais masquée. */
    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            // issue #21 : préremplissage depuis le cache tamponné (lecture
            // silencieuse), puis rafraîchissement réseau en fond.
            if (!force) {
                val enCache = runCatching { container.messages.conversationsEnCache() }.getOrNull()
                if (enCache != null) {
                    _état.update { st ->
                        st.copy(
                            conversations = if (st.conversations.isEmpty()) enCache.conversations else st.conversations,
                            themes = st.themes.ifEmpty { enCache.themes },
                        )
                    }
                }
            }
            // chargement = rien à montrer (squelette) ; sinon rafraîchissement
            // en fond, le contenu affiché reste en place.
            _état.update {
                it.copy(
                    chargement = it.conversations.isEmpty(),
                    rafraîchissement = it.conversations.isNotEmpty(),
                )
            }
            try {
                val page = container.messages.conversations(fraîche = force)
                val contact = runCatching { container.messages.contact() }.getOrNull()
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
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
                rafraîchissement = false,
                conversations = àAfficher,
                themes = it.themes.ifEmpty { enCache?.themes ?: emptyList() },
                // L'erreur est toujours signalée, même avec du contenu à
                // l'écran (bannière non bloquante, issue #21).
                erreur = message,
            )
        }
    }

}

/** — Conversation (détail d'un fil) --------------------------------------- */
data class ConversationÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val conversation: Conversation? = null,
    /** Un rafraîchissement réseau tourne pendant que le contenu connu reste affiché. */
    val rafraîchissement: Boolean = false,
    val texte: String = "",
    val pièces: List<java.io.File> = emptyList(),
    val audio: java.io.File? = null,
    val enregistre: Boolean = false,
    /** Envois optimistes : affichés au bas du fil jusqu'à confirmation serveur,
     *  marqués Échec (relance manuelle) si le POST rate. */
    val envois: List<school.greenwood.plus.model.MessageEnvoi> = emptyList(),
    /** Catégories du composeur (serveur themes[]) — issue #99 : la réponse
     *  affiche et permet de changer la catégorie du fil. */
    val themes: List<school.greenwood.plus.model.ThemeMessage> = emptyList(),
    val themesChargement: Boolean = true,
    val themesErreur: Boolean = false,
    /** Catégorie choisie dans le composeur (id) — null = garder celle du fil. */
    val themeChoisi: String? = null,
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
        chargerThemes()
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
    }

    /** Catégories du serveur pour le composeur de réponse (issue #99) —
     *  échec → dernier lot en cache, sinon état d'erreur + « Réessayer ». */
    fun chargerThemes() {
        viewModelScope.launch {
            _état.update { it.copy(themesChargement = true, themesErreur = false) }
            try {
                val page = container.messages.conversations()
                _état.update {
                    it.copy(themesChargement = false, themes = page.themes, themesErreur = false)
                }
            } catch (err: Exception) {
                val enCache = runCatching { container.messages.conversationsEnCache() }.getOrNull()
                val enCacheÉchec = enCache?.themes.orEmpty()
                _état.update {
                    it.copy(
                        themesChargement = false,
                        themes = if (enCacheÉchec.isNotEmpty()) enCacheÉchec else it.themes,
                        themesErreur = enCacheÉchec.isEmpty(),
                    )
                }
            }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            // issue #21 : préremplissage depuis le cache du fil (lecture
            // silencieuse), puis rafraîchissement réseau en fond — le fil
            // connu reste affiché pendant l'appel.
            if (!force) {
                val enCache = runCatching { container.messages.conversationEnCache(conversationId) }.getOrNull()
                if (enCache != null) {
                    _état.update { st -> if (st.conversation == null) st.copy(conversation = enCache) else st }
                }
            }
            // chargement = rien à montrer (squelette) ; sinon rafraîchissement
            // en fond, le contenu affiché reste en place.
            _état.update {
                it.copy(
                    chargement = it.conversation == null,
                    rafraîchissement = it.conversation != null,
                )
            }
            try {
                val conversation = container.messages.conversation(conversationId)
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        conversation = conversation,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                // Échec : le fil connu reste affiché, l'erreur est toujours
                // signalée (bannière non bloquante, issue #21).
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = err.messageUtilisateur)
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = "Messages indisponibles pour le moment",
                    )
                }
            }
        }
    }

    /** — Composeur --------------------------------------------------------- */

    fun modifierTexte(valeur: String) = _état.update { it.copy(texte = valeur) }

    /** Catégorie choisie dans le composeur de réponse (issue #99) — null
     *  rebascule sur celle du fil. */
    fun choisirTheme(id: String?) = _état.update { it.copy(themeChoisi = id) }

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
        // Issue #99 : un fil sans catégorie doit en recevoir une au composeur
        // — jamais de `theme = ""` envoyé à l'aveugle.
        val fil = e.conversation ?: return
        if (refusCatégorieRéponse(
                choisi = e.themeChoisi,
                fil = fil.theme,
                themes = e.themes,
                themesEnÉchec = e.themesErreur,
                chargement = e.themesChargement,
            ) != null
        ) return
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
                    // Issue #99 : catégorie choisie dans le composeur, sinon
                    // celle du fil — lue au moment d'envoyer (relance comprise).
                    theme = themeDeRéponse(_état.value.themeChoisi, conversation.theme),
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
    /** Lecture des catégories en cours / en échec (issue #99) : l'état du
     *  bloc « Catégorie » en découle — jamais masqué en silence. */
    val themesChargement: Boolean = true,
    val themesErreur: Boolean = false,
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
        chargerThemes()
    }

    /** Catégories du serveur (`themes[]` du GET `messages`) — issue #99 :
     *  échec → dernier lot en cache s'il existe, sinon l'état d'erreur
     *  affiche « Réessayer » au lieu d'un bloc qui disparaît. */
    fun chargerThemes() {
        viewModelScope.launch {
            _état.update { it.copy(themesChargement = true, themesErreur = false) }
            try {
                val page = container.messages.conversations()
                _état.update {
                    it.copy(themesChargement = false, themes = page.themes, themesErreur = false)
                }
            } catch (err: Exception) {
                val enCache = runCatching { container.messages.conversationsEnCache() }.getOrNull()
                val enCacheÉchec = enCache?.themes.orEmpty()
                _état.update {
                    it.copy(
                        themesChargement = false,
                        themes = if (enCacheÉchec.isNotEmpty()) enCacheÉchec else it.themes,
                        themesErreur = enCacheÉchec.isEmpty(),
                    )
                }
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
        // Issue #99 : sans catégorie choisie, rien ne part — même si le
        // bouton d'envoi est déjà inactif (filet de sécurité testé).
        refusCatégorieNouveau(
            themeChoisi = e.themeChoisi?.id,
            themes = e.themes,
            themesEnÉchec = e.themesErreur,
            chargement = e.themesChargement,
        )?.let { refus ->
            _état.update { it.copy(erreur = refus) }
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
    /** Un rafraîchissement réseau tourne pendant que le contenu connu reste affiché. */
    val rafraîchissement: Boolean = false,
)

class DemandesViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(DemandesÉtat())
    val état: StateFlow<DemandesÉtat> = _état.asStateFlow()

    init {
        charger()
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            // issue #21 : préremplissage depuis le cache (lecture silencieuse),
            // puis rafraîchissement réseau en fond — le contenu connu reste
            // affiché et les listes ne sont jamais vidées entre deux états.
            if (!force) {
                val enCache = runCatching { container.demandes.listeEnCache() }.getOrNull()
                if (enCache != null) {
                    _état.update { st -> if (st.demandes.isEmpty()) st.copy(demandes = enCache) else st }
                }
            }
            // chargement = rien à montrer (squelette) ; sinon rafraîchissement
            // en fond, le contenu affiché reste en place.
            _état.update {
                it.copy(
                    chargement = it.demandes.isEmpty(),
                    rafraîchissement = it.demandes.isNotEmpty(),
                )
            }
            try {
                val demandes = container.demandes.liste()
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        demandes = demandes,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                // Échec : le contenu connu reste affiché, l'erreur est toujours
                // signalée (bannière non bloquante, issue #21).
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = err.messageUtilisateur)
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = "Demandes indisponibles pour le moment",
                    )
                }
            }
        }
    }

}

/** — Actualités -------------------------------------------------------- */
data class ActualitesÉtat(
    val chargement: Boolean = true,
    val rafraîchissement: Boolean = false,
    val chargementPageSuivante: Boolean = false,
    val erreur: String? = null,
    val liste: List<Post> = emptyList(),
    val finAtteinte: Boolean = false,
)

class ActualitesViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(ActualitesÉtat())
    val état: StateFlow<ActualitesÉtat> = _état.asStateFlow()

    init {
        charger()
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        // rafraîchir() garde la profondeur de pagination (limite = taille chargée),
        // là où charger(force = true) ramènerait la liste aux dix premiers posts.
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { rafraîchir() }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            if (!force) {
                val enCache = runCatching { container.nouveautes.listeEnCache() }.getOrNull()
                if (!enCache.isNullOrEmpty()) {
                    _état.update { st -> if (st.liste.isEmpty()) st.copy(liste = enCache) else st }
                }
            }
            _état.update {
                it.copy(
                    chargement = it.liste.isEmpty(),
                    rafraîchissement = it.liste.isNotEmpty(),
                )
            }
            try {
                val posts = container.nouveautes.liste(départ = 0, limite = 10)
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        liste = posts,
                        finAtteinte = posts.isEmpty(),
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = err.messageUtilisateur)
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = "Les actualités n'ont pas pu être chargées",
                    )
                }
            }
        }
    }

    fun pageSuivante() {
        val courante = _état.value
        if (courante.finAtteinte || courante.chargement || courante.chargementPageSuivante || courante.rafraîchissement) return

        viewModelScope.launch {
            _état.update { it.copy(chargementPageSuivante = true) }
            try {
                val départ = courante.liste.size + 1
                val nouveaux = container.nouveautes.liste(départ = départ, limite = 10)
                _état.update { st ->
                    if (nouveaux.isEmpty()) {
                        st.copy(chargementPageSuivante = false, finAtteinte = true)
                    } else {
                        val fusion = Normalizers.fusionner(st.liste, nouveaux, départ)
                        st.copy(
                            chargementPageSuivante = false,
                            liste = fusion,
                            finAtteinte = nouveaux.size < 10,
                        )
                    }
                }
            } catch (err: Exception) {
                _état.update { it.copy(chargementPageSuivante = false) }
            }
        }
    }

    fun rafraîchir() {
        viewModelScope.launch {
            val tailleActuelle = maxOf(_état.value.liste.size, 10)
            _état.update { it.copy(rafraîchissement = true) }
            try {
                val posts = container.nouveautes.liste(départ = 0, limite = tailleActuelle)
                _état.update {
                    it.copy(
                        rafraîchissement = false,
                        liste = posts,
                        finAtteinte = false,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update { it.copy(rafraîchissement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(rafraîchissement = false, erreur = "Actualisation impossible")
                }
            }
        }
    }
}

/** — Détail d'une actualité --------------------------------------------- */
data class PostDetailÉtat(
    val chargement: Boolean = true,
    val rafraîchissement: Boolean = false,
    val erreur: String? = null,
    val detail: PostDetail? = null,
    val ecritureActivee: Boolean = false,
    val texteCommentaire: String = "",
    val replyToId: String? = null,
    val envoiCommentaire: Boolean = false,
    val erreurEnvoiCommentaire: String? = null,
    val alertMerci: Boolean = false,
)

class PostDetailViewModel(
    private val container: AppContainer,
    private val postId: String,
) : ViewModel() {
    private val _état = MutableStateFlow(PostDetailÉtat())
    val état: StateFlow<PostDetailÉtat> = _état.asStateFlow()

    init {
        viewModelScope.launch {
            container.session.ecritureNouveautesActivée.collect { active ->
                _état.update { it.copy(ecritureActivee = active) }
            }
        }
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger() }
        }
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.detail == null, rafraîchissement = it.detail != null) }
            try {
                val detail = container.nouveautes.détail(postId)
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        detail = detail,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = err.messageUtilisateur)
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = "Impossible de charger le détail de l'actualité",
                    )
                }
            }
        }
    }

    fun majTexteCommentaire(texte: String) {
        _état.update { it.copy(texteCommentaire = texte) }
    }

    fun définirRéponseÀ(replyToId: String?) {
        _état.update { it.copy(replyToId = replyToId) }
    }

    fun envoyerCommentaire() {
        val e = _état.value
        val d = e.detail ?: return
        if (!e.ecritureActivee || !d.peutCommenter || e.texteCommentaire.isBlank() || e.envoiCommentaire) return

        viewModelScope.launch {
            _état.update { it.copy(envoiCommentaire = true, erreurEnvoiCommentaire = null) }
            try {
                val nouveau = container.nouveautes.commenter(
                    postId = postId,
                    texte = e.texteCommentaire.trim(),
                    replyTo = e.replyToId,
                )
                _état.update { st ->
                    val det = st.detail
                    val commentaires = if (det != null && nouveau != null) {
                        det.commentaires + nouveau
                    } else {
                        det?.commentaires ?: emptyList()
                    }
                    st.copy(
                        envoiCommentaire = false,
                        texteCommentaire = "",
                        replyToId = null,
                        detail = det?.copy(commentaires = commentaires),
                    )
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        envoiCommentaire = false,
                        erreurEnvoiCommentaire = "Impossible d'envoyer le commentaire",
                    )
                }
            }
        }
    }

    fun répondreQuestionQuiz(alias: String?, réponse: String) {
        val e = _état.value
        val d = e.detail ?: return
        if (!e.ecritureActivee || alias.isNullOrBlank()) return

        viewModelScope.launch {
            try {
                container.nouveautes.répondreQuestionQuiz(postId, alias, réponse)
                _état.update { st ->
                    val det = st.detail ?: return@update st
                    val questionsMaj = det.questions.map { q ->
                        if (q.alias == alias) q.copy(réponseChoisie = réponse) else q
                    }
                    val toutesRepondues = questionsMaj.isNotEmpty() && questionsMaj.all { it.réponseChoisie != null }
                    st.copy(
                        detail = det.copy(questions = questionsMaj),
                        alertMerci = toutesRepondues,
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun masquerAlerteMerci() {
        _état.update { it.copy(alertMerci = false) }
    }

    fun basculerKillSwitch(actif: Boolean) {
        viewModelScope.launch {
            container.session.définirEcritureNouveautes(actif)
        }
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

/** — Emploi du temps ---------------------------------------------------- */
data class CoursÉtat(
    val chargement: Boolean = true,
    val rafraîchissement: Boolean = false,
    val erreur: String? = null,
    val semaine: SemaineCours? = null,
    /** Jour affiché, 1-based (lundi = 1) — conservé d'une semaine à l'autre. */
    val jourChoisi: Int? = null,
    /** Une navigation ←/→ tourne ; pas un rafraîchissement. */
    val navigation: Boolean = false,
)

class CoursViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(CoursÉtat())
    val état: StateFlow<CoursÉtat> = _état.asStateFlow()

    init {
        charger()
        // Retour après une absence longue : rafraîchir en silence (data/session/Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            if (!force) {
                // Ouverture à chaud : la dernière semaine connue, lecture muette (issue #21).
                val enCache = runCatching { container.cours.semaineEnCache() }.getOrNull()
                if (enCache != null) {
                    _état.update { st ->
                        if (st.semaine == null) {
                            st.copy(semaine = enCache, jourChoisi = st.jourChoisi ?: enCache.jourSélectionné)
                        } else {
                            st
                        }
                    }
                }
            }
            _état.update {
                it.copy(
                    chargement = it.semaine == null,
                    rafraîchissement = it.semaine != null,
                )
            }
            try {
                val semaine = container.cours.semaine()
                _état.update { st ->
                    st.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = null,
                        semaine = semaine,
                        jourChoisi = st.jourChoisi ?: semaine?.jourSélectionné ?: jourParDéfaut(semaine),
                    )
                }
            } catch (err: BotiErreur) {
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = err.messageUtilisateur)
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissement = false,
                        erreur = "Emploi du temps indisponible pour le moment",
                    )
                }
            }
        }
    }

    fun rafraîchir() = charger(force = true)

    fun choisirJour(jour: Int) {
        _état.update { it.copy(jourChoisi = jour) }
    }

    fun semainePrécédente() = naviguer(SensSemaine.Précédente, _état.value.semaine?.semainePrécédente)

    fun semaineSuivante() = naviguer(SensSemaine.Suivante, _état.value.semaine?.semaineSuivante)

    /**
     * Navigation ←/→ : GET `cours_v2?last_week=<ISO lundi>` ou
     * `?next_week=<ISO lundi>` — le serveur attend son propre champ de
     * navigation (sonde 2026-09-22) ; un `date=` générique est ignoré.
     */
    private fun naviguer(sens: SensSemaine, vers: LocalDate?) {
        if (vers == null) return
        val courante = _état.value
        if (courante.navigation || courante.chargement) return

        viewModelScope.launch {
            _état.update { it.copy(navigation = true, erreur = null) }
            try {
                val semaine = container.cours.semaine(sens, vers)
                _état.update { st ->
                    st.copy(
                        navigation = false,
                        erreur = null,
                        semaine = semaine,
                        jourChoisi = st.jourChoisi ?: semaine?.jourSélectionné ?: jourParDéfaut(semaine),
                    )
                }
            } catch (err: BotiErreur) {
                _état.update { it.copy(navigation = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(navigation = false, erreur = "Semaine indisponible") }
            }
        }
    }

    /** selected_day du serveur (1-based), sinon aujourd'hui borné au samedi. */
    private fun jourParDéfaut(semaine: SemaineCours?): Int {
        semaine?.jourSélectionné?.takeIf { it in 1..7 }?.let { return it }
        return LocalDate.now().dayOfWeek.value.coerceIn(1, 6)
    }
}

/** — Paramètres --------------------------------------------------------- */
data class ParamètresÉtat(
    val bannièreRegistreActivée: Boolean = true,
    val bannièreCoursActivée: Boolean = true,
    /** Minutes d'absence déclenchant l'actualisation au retour ; 0 = « jamais ». */
    val minutesRetour: Int = 5,

    /** Serveur communautaire (issue #88) : URL réglée (vide = non configuré),
     *  compte local existant, test de disponibilité et révocation. */
    val urlCommunautaire: String = "",
    val compteCommunautaire: Boolean = false,
    val testEnCours: Boolean = false,
    /** null = pas encore testé. */
    val testRéussi: Boolean? = null,
    val révocationEnCours: Boolean = false,

    /** Déconnexion (issue #101) : second accès, depuis les Paramètres. */
    val déconnexionEnCours: Boolean = false,
)

class ParametresViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(ParamètresÉtat())
    val état: StateFlow<ParamètresÉtat> = _état.asStateFlow()

    init {
        viewModelScope.launch {
            container.session.bannièreRegistreActivée.collect { actif ->
                _état.update { it.copy(bannièreRegistreActivée = actif) }
            }
        }
        viewModelScope.launch {
            container.session.bannièreCoursActivée.collect { actif ->
                _état.update { it.copy(bannièreCoursActivée = actif) }
            }
        }
        viewModelScope.launch {
            container.session.actualisationRetour.collect { minutes ->
                _état.update { it.copy(minutesRetour = minutes) }
            }
        }
        viewModelScope.launch {
            container.session.urlCommunautaire.collect { url ->
                _état.update { it.copy(urlCommunautaire = url, testRéussi = null) }
            }
        }
        viewModelScope.launch {
            container.session.jetonCommunautaire.collect { jeton ->
                _état.update { it.copy(compteCommunautaire = jeton != null) }
            }
        }
    }

    fun choisirDurée(minutes: Int) {
        viewModelScope.launch {
            container.session.définirActualisationRetour(minutes)
        }
    }

    fun définirBannièreRegistre(actif: Boolean) {
        viewModelScope.launch { container.session.définirBannièreRegistre(actif) }
    }

    fun définirBannièreCours(actif: Boolean) {
        viewModelScope.launch { container.session.définirBannièreCours(actif) }
    }

    /** Normalisée à l'écriture (schéma ajouté, « /» de fin retirés). */
    fun définirUrlCommunautaire(url: String) {
        viewModelScope.launch {
            container.session.définirUrlCommunautaire(url)
            _état.update { it.copy(testRéussi = null) }
        }
    }

    /** `GET /health` — silencieux, sans compte ni jeton. */
    fun testerServeurCommunautaire() {
        if (_état.value.testEnCours) return
        viewModelScope.launch {
            _état.update { it.copy(testEnCours = true, testRéussi = null) }
            val ok = runCatching { container.communaute.disponible() }.getOrDefault(false)
            _état.update { it.copy(testEnCours = false, testRéussi = ok) }
        }
    }

    /** `DELETE /compte` + oubli local (jamais bloqué par le réseau). */
    fun révoquerCompteCommunautaire() {
        if (_état.value.révocationEnCours) return
        viewModelScope.launch {
            _état.update { it.copy(révocationEnCours = true) }
            container.communaute.révoquerCompte()
            _état.update { it.copy(révocationEnCours = false) }
        }
    }

    /**
     * Déconnexion (issue #101) — le même chemin que depuis la pilule
     * profil : `AuthRepository.déconnexion()`, une seule fois. Portée de
     * l'écran : les Paramètres peuvent être refermés pendant l'appel
     * réseau, d'où `NonCancellable` — la purge va au bout.
     */
    fun déconnexion() {
        if (_état.value.déconnexionEnCours) return
        _état.update { it.copy(déconnexionEnCours = true) }
        viewModelScope.launch {
            withContext(NonCancellable) { runCatching { container.auth.déconnexion() } }
            _état.update { it.copy(déconnexionEnCours = false) }
        }
    }
}

/** — Composeur IA (issue #56) ------------------------------------------- */

/** Le VM du panneau Paramètres porte les réglages IA : activation, ton
 *  par défaut, fournisseur (preset ou URL libre), modèle et clé BYOK. */
class RéglagesIAViewModel(private val container: AppContainer) : ViewModel() {
    private val _réglages = MutableStateFlow(RéglagesIA())
    val réglages: StateFlow<RéglagesIA> = _réglages.asStateFlow()

    init {
        viewModelScope.launch {
            container.session.réglagesIA.collect { _réglages.value = it }
        }
    }

    fun définir(réglages: RéglagesIA) {
        viewModelScope.launch { container.session.définirRéglagesIA(réglages) }
    }
}

/** — Mises à jour de l'app (issue #46) ----------------------------------- */

/** L'état partagé vit dans UpdatesRepository (le Registre et les Paramètres
 *  observent le même) ; ce VM ne porte que le canal bêta, réglage du
 *  panneau Paramètres. */
class MiseÀJourViewModel(private val container: AppContainer) : ViewModel() {
    private val _canalBêta = MutableStateFlow(false)
    val canalBêta: StateFlow<Boolean> = _canalBêta.asStateFlow()

    init {
        viewModelScope.launch {
            container.misesÀJour.état.collect { _canalBêta.value = it.canalBêta }
        }
    }

    fun définirCanalBêta(actif: Boolean) {
        viewModelScope.launch { container.misesÀJour.définirCanalBêta(actif) }
    }

    fun vérifier() {
        viewModelScope.launch { container.misesÀJour.vérifier(manuel = true) }
    }

    fun mettreÀJour() {
        viewModelScope.launch { container.misesÀJour.mettreÀJour() }
    }

    fun relancerInstallation() {
        container.misesÀJour.relancerInstallation()
    }
}

/** — Boutique de l'école (docs/product/DESIGN.md §4) --------------------- */

data class BoutiqueÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    /** Un rafraîchissement tourne pendant que le contenu connu reste affiché. */
    val rafraîchissementSilencieux: Boolean = false,
    val produits: List<ProduitBoutique> = emptyList(),
    val rubriques: List<RubriqueBoutique> = emptyList(),
    /** Planning cantine — renseigné sur la rubrique « Repas invité ». */
    val cantines: List<CantineJour> = emptyList(),
    /** Réservation de repas en cours, et le succès à confirmer. */
    val envoiRepas: Boolean = false,
    val succèsRepas: RésultatCommande? = null,
    /** Rubrique affichée — « -1 » = Tout (le serveur exige le paramètre). */
    val rubriqueActive: String = "-1",
    val recherche: String = "",
)

class BoutiqueViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(BoutiqueÉtat())
    val état: StateFlow<BoutiqueÉtat> = _état.asStateFlow()

    init {
        charger()
        // Retour après une absence longue : rafraîchir en silence (Veille.kt).
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
        // Une commande passée ailleurs (détail) rafraîchit la liste.
        viewModelScope.launch {
            container.boutique.commandesChangées.collect { if (it > 0) charger(force = true) }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            _état.update {
                it.copy(
                    chargement = it.produits.isEmpty(),
                    rafraîchissementSilencieux = it.produits.isNotEmpty(),
                )
            }
            try {
                val page = container.boutique.catalogue(
                    rubrique = _état.value.rubriqueActive,
                    recherche = "",
                )
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissementSilencieux = false,
                        produits = page.produits,
                        rubriques = page.rubriques,
                        cantines = page.cantines,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissementSilencieux = false,
                        erreur = err.messageUtilisateur,
                    )
                }
            } catch (err: Exception) {
                _état.update {
                    it.copy(
                        chargement = false,
                        rafraîchissementSilencieux = false,
                        erreur = "Boutique indisponible pour le moment",
                    )
                }
            }
        }
    }

    fun choisirRubrique(id: String) {
        if (_état.value.rubriqueActive == id) return
        _état.update { it.copy(rubriqueActive = id, recherche = "") }
        charger(force = true)
    }

    fun modifierRecherche(valeur: String) {
        _état.update { it.copy(recherche = valeur) }
    }

    /** Réserver le repas invité d'un jour du planning (POST vérifié). */
    fun réserverRepas(jour: CantineJour) {
        if (_état.value.envoiRepas) return
        _état.update { it.copy(envoiRepas = true, erreur = null) }
        viewModelScope.launch {
            try {
                val résultat = container.boutique.commanderRepas(jour.id, jour.jourValeur)
                _état.update { it.copy(envoiRepas = false, succèsRepas = résultat) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(envoiRepas = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(envoiRepas = false, erreur = "Réservation impossible pour le moment")
                }
            }
        }
    }

    /** L'alerte de réservation est fermée. */
    fun acquisRepas() {
        _état.update { it.copy(succèsRepas = null) }
    }
}

/** Recherche locale sur le catalogue chargé (testé). */
fun filtrerProduits(produits: List<ProduitBoutique>, recherche: String): List<ProduitBoutique> {
    val requête = recherche.trim()
    if (requête.isEmpty()) return produits
    return produits.filter { it.label.contains(requête, ignoreCase = true) }
}

/**
 * Le prix unitaire affiché au détail : celui de la variante choisie
 * (`amount`) sinon le prix de base du produit (testé).
 */
fun prixUnitaire(produit: ProduitDétail, varianteId: String?): String? =
    varianteId?.let { id ->
        produit.variantes.firstOrNull { it.id == id }?.montant
    } ?: produit.prixRaw

/** — Boutique : détail d'un produit -------------------------------------- */

data class ProduitÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val produit: ProduitDétail? = null,
    val varianteId: String? = null,
    val taille: String? = null,
    val quantité: Int = 1,
    val commentaire: String = "",
    /** Envoi en cours — le bouton « Commander » est gelé. */
    val envoi: Boolean = false,
    /** Succès à confirmer avant de rebasculer en arrière. */
    val succès: RésultatCommande? = null,
)

class ProduitViewModel(
    private val container: AppContainer,
    private val produitId: String,
    /** Mode modification : la commande à reprendre (préremplissage serveur). */
    private val commandeId: String? = null,
) : ViewModel() {
    private val _état = MutableStateFlow(ProduitÉtat())
    val état: StateFlow<ProduitÉtat> = _état.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.produit == null, erreur = null) }
            try {
                val produit = container.boutique.détail(produitId, commandeId)
                _état.update { st ->
                    st.copy(
                        chargement = false,
                        produit = produit,
                        varianteId = null,
                        taille = produit.prérempli?.taille ?: st.taille,
                        quantité = produit.prérempli?.quantité ?: st.quantité,
                        commentaire = produit.prérempli?.commentaire ?: st.commentaire,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(chargement = false, erreur = "Produit indisponible pour le moment")
                }
            }
        }
    }

    fun choisirVariante(id: String) {
        _état.update { st ->
            if (st.produit?.variantes?.none { it.id == id } == true) st
            else st.copy(varianteId = id, taille = st.produit?.variantes?.firstOrNull { it.id == id }?.label)
        }
    }

    /** ±[delta] sur la quantité, borné 1..stock connu (99 sinon). */
    fun modifierQuantité(delta: Int) {
        _état.update { st ->
            val plafond = st.produit?.variantes
                ?.firstOrNull { it.id == st.varianteId }?.stock?.coerceAtLeast(1) ?: 99
            st.copy(quantité = (st.quantité + delta).coerceIn(1, plafond))
        }
    }

    fun modifierCommentaire(valeur: String) {
        _état.update { it.copy(commentaire = valeur) }
    }

    /** Le prix unitaire courant : variante sinon produit (testé). */
    fun prix(): String? = _état.value.produit?.let { prixUnitaire(it, _état.value.varianteId) }

    fun commander() {
        val st = _état.value
        if (st.envoi || st.produit == null) return
        _état.update { it.copy(envoi = true, erreur = null) }
        viewModelScope.launch {
            try {
                val variante = st.produit.variantes.firstOrNull { it.id == st.varianteId }
                val résultat = container.boutique.commander(
                    produitId = produitId,
                    varianteId = st.varianteId,
                    taille = st.taille ?: variante?.label,
                    quantité = st.quantité,
                    commentaire = st.commentaire,
                    prix = prix(),
                    commandeId = commandeId,
                )
                _état.update { it.copy(envoi = false, succès = résultat) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(envoi = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(envoi = false, erreur = "Commande impossible pour le moment")
                }
            }
        }
    }

    /** Le dialogue de succès est fermé → retour à l'écran d'où l'on venait. */
    fun acquis() {
        _état.update { it.copy(succès = null) }
    }
}

/** — Boutique : historique des commandes --------------------------------- */

data class HistoriqueÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val commandes: List<CommandeBoutique> = emptyList(),
    /** Suppression en cours : l'id de la commande, ou « commande/article ». */
    val suppression: String? = null,
)

class HistoriqueViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(HistoriqueÉtat())
    val état: StateFlow<HistoriqueÉtat> = _état.asStateFlow()

    init {
        charger()
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
        viewModelScope.launch {
            container.boutique.commandesChangées.collect { if (it > 0) charger(force = true) }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            _état.update {
                it.copy(
                    chargement = it.commandes.isEmpty(),
                    erreur = if (force) it.erreur else null,
                )
            }
            try {
                val commandes = container.boutique.historique()
                _état.update {
                    it.copy(chargement = false, commandes = commandes, erreur = null)
                }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(chargement = false, erreur = "Historique indisponible pour le moment")
                }
            }
        }
    }

    fun supprimer(commandeId: String, articleId: String?) {
        if (_état.value.suppression != null) return
        _état.update { it.copy(suppression = "$commandeId/${articleId ?: ""}") }
        viewModelScope.launch {
            try {
                container.boutique.supprimer(commandeId, articleId)
            } catch (err: BotiErreur) {
                _état.update { it.copy(erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(erreur = "Suppression impossible pour le moment") }
            } finally {
                _état.update { it.copy(suppression = null) }
            }
            // Recharge quoi qu'il arrive : le serveur fait foi.
            charger(force = true)
        }
    }
}

/** — Boutique : réservation du repas invité ------------------------------- */

/** L'état de l'écran Repas invité : le planning et une réservation en cours. */
data class RepasÉtat(
    val chargement: Boolean = true,
    val erreur: String? = null,
    val jours: List<CantineJour> = emptyList(),
    val envoi: Boolean = false,
    val succès: RésultatCommande? = null,
)

class RepasViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(RepasÉtat())
    val état: StateFlow<RepasÉtat> = _état.asStateFlow()

    init {
        charger()
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
        viewModelScope.launch {
            container.boutique.commandesChangées.collect { if (it > 0) charger(force = true) }
        }
    }

    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            _état.update { it.copy(chargement = it.jours.isEmpty()) }
            try {
                val page = container.boutique.catalogue(rubrique = "2", recherche = "")
                _état.update {
                    it.copy(
                        chargement = false,
                        jours = page.cantines,
                        erreur = null,
                    )
                }
            } catch (err: BotiErreur) {
                _état.update { it.copy(chargement = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(chargement = false, erreur = "Planning indisponible pour le moment")
                }
            }
        }
    }

    fun réserver(jour: CantineJour) {
        if (_état.value.envoi) return
        _état.update { it.copy(envoi = true, erreur = null) }
        viewModelScope.launch {
            try {
                val résultat = container.boutique.commanderRepas(jour.id, jour.jourValeur)
                _état.update { it.copy(envoi = false, succès = résultat) }
            } catch (err: BotiErreur) {
                _état.update { it.copy(envoi = false, erreur = err.messageUtilisateur) }
            } catch (err: Exception) {
                _état.update { it.copy(envoi = false, erreur = "Réservation impossible pour le moment") }
            }
        }
    }

    fun acquis() {
        _état.update { it.copy(succès = null) }
    }
}

/** — Serveur communautaire (issue #88) ------------------------------------ */

/** Les onglets de la section communautaire. */
enum class OngletCommunautaire(val libellé: String) {
    Devoirs("Devoirs"),
    EmploiDuTemps("Emploi du temps"),
    Abus("Abus"),
}

/** La liste paginée qui doit charger sa page suivante. */
enum class CiblePage { Devoirs, Problèmes, Corrections }

/** La fenêtre de dialogue ouverte — null = aucune. */
sealed interface DialogueCommunautaire {

    /** Notice légale : acceptée avant la première écriture, réaffichée quand
     *  la version du serveur change (APP.md §2) — non refermable au retour. */
    data class Notice(val mentions: Mentions) : DialogueCommunautaire

    data class CréerDevoir(
        val matière: String = "",
        val contenu: String = "",
        val dateRemise: String = "",
        val fichiers: List<java.io.File> = emptyList(),
        val erreur: String? = null,
    ) : DialogueCommunautaire

    data class CréerProblème(
        val description: String = "",
        val date: String = "",
        val erreur: String? = null,
    ) : DialogueCommunautaire

    data class CréerCorrection(
        val description: String = "",
        val date: String = "",
        val problèmeId: Long? = null,
        val erreur: String? = null,
    ) : DialogueCommunautaire

    data class Signaler(
        val cible: CibleSignalement,
        val cibleId: Long,
        val raison: String = "",
        val erreur: String? = null,
    ) : DialogueCommunautaire

    data class Supprimer(
        val cible: CibleSignalement,
        val cibleId: Long,
        val intitulé: String,
        val erreur: String? = null,
    ) : DialogueCommunautaire

    /** Message d'erreur dans le dialogue ouvert (réponse du serveur). */
    fun avecErreur(message: String): DialogueCommunautaire = when (this) {
        is Notice -> this
        is CréerDevoir -> copy(erreur = message)
        is CréerProblème -> copy(erreur = message)
        is CréerCorrection -> copy(erreur = message)
        is Signaler -> copy(erreur = message)
        is Supprimer -> copy(erreur = message)
    }
}

data class CommunauteÉtat(
    val onglet: OngletCommunautaire = OngletCommunautaire.Devoirs,
    /** Rien à montrer : squelette de premier chargement. */
    val chargement: Boolean = true,
    /** Contenu connu affiché pendant un rafraîchissement silencieux. */
    val rafraîchissement: Boolean = false,
    val chargementSuite: Boolean = false,
    val envoi: Boolean = false,
    /** Échec de LECTURE — bandeau « Réessayer » (issue #21). */
    val erreur: String? = null,
    /** Échec d'ÉCRITURE — bandeau sans relance (réessayer = renvoyer le formulaire). */
    val échec: String? = null,
    /** Information passagère (succès) — disparaît à l'action suivante. */
    val message: String? = null,
    val devoirs: List<DevoirSuggéré> = emptyList(),
    val totalDevoirs: Int? = null,
    val problèmes: List<ProblèmeHoraire> = emptyList(),
    val totalProblèmes: Int? = null,
    val corrections: List<CorrectionHoraire> = emptyList(),
    val totalCorrections: Int? = null,
    val abus: List<SignalementAbus> = emptyList(),
    /** Votes locaux ±1 par devoir : affichage immédiat ; le total définitif
     *  est toujours celui que renvoie le serveur (jamais incrémenté). */
    val votes: Map<String, Int> = emptyMap(),
    /** Notre empreinte d'auteur (null sans compte) : masque nos propres
     *  votes et affiche « Supprimer » sur notre contenu. */
    val monAuteurId: String? = null,
    val tri: TriDevoirs = TriDevoirs.Votes,
    val filtre: FiltreHoraire = FiltreHoraire.Tous,
    /** « cible:id » déjà signalés (409 ou liste publique des signalements). */
    val signalés: Set<String> = emptySet(),
    val noticeChargement: Boolean = false,
    val dialogue: DialogueCommunautaire? = null,
    /** Secondes restantes avant reprise automatique après un 429. */
    val attenteDébit: Int? = null,
)

/**
 * Le VM de la section communautaire (issue #88) : trois onglets de listes
 * publiques, écritures à jeton, et tout le cycle de vie du compte — la
 * notice avant la première écriture, un seul recomplément sur 401, une
 * attente automatique sur 429. Les lectures sont toujours des rafraîchissements
 * de première page complets (APP.md §7 : `depuis` ne voit ni votes ni
 * suppressions).
 */
class CommunauteViewModel(private val container: AppContainer) : ViewModel() {
    private val _état = MutableStateFlow(CommunauteÉtat())
    val état: StateFlow<CommunauteÉtat> = _état.asStateFlow()

    /** L'écriture en attente : relue après acceptation de la notice ou la fin
     *  du temps de débit — jamais deux écritures en parallèle. */
    private var enAttente: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch {
            container.session.votesLocaux.collect { votes -> _état.update { it.copy(votes = votes) } }
        }
        charger()
        viewModelScope.launch {
            container.veille.retoursPérimés.collect { charger(force = true) }
        }
    }

    // — Lectures -----------------------------------------------------------

    /** Rafraîchissement complet de l'onglet courant (première page). */
    fun charger(force: Boolean = false) {
        viewModelScope.launch {
            _état.update { st ->
                st.copy(
                    chargement = !force && st.vide(st.onglet),
                    rafraîchissement = force && !st.vide(st.onglet),
                    erreur = if (force) null else st.erreur,
                )
            }
            try {
                val monId = container.communaute.monAuteurId()
                when (_état.value.onglet) {
                    OngletCommunautaire.Devoirs -> {
                        val page = container.communaute.devoirs(tri = _état.value.tri)
                        _état.update {
                            it.copy(devoirs = page.éléments, totalDevoirs = page.total, monAuteurId = monId)
                        }
                    }
                    OngletCommunautaire.EmploiDuTemps -> {
                        val problèmes = container.communaute.problèmes(filtre = _état.value.filtre)
                        val corrections = container.communaute.corrections()
                        _état.update {
                            it.copy(
                                problèmes = problèmes.éléments,
                                totalProblèmes = problèmes.total,
                                corrections = corrections.éléments,
                                totalCorrections = corrections.total,
                                monAuteurId = monId,
                            )
                        }
                    }
                    OngletCommunautaire.Abus -> {
                        val abus = container.communaute.signalements()
                        _état.update { st ->
                            st.copy(
                                abus = abus,
                                monAuteurId = monId,
                                signalés = st.signalés + abus.map { s -> "${s.cible}:${s.cibleId}" },
                            )
                        }
                    }
                }
                _état.update { it.copy(chargement = false, rafraîchissement = false, erreur = null) }
            } catch (err: Exception) {
                _état.update {
                    it.copy(chargement = false, rafraîchissement = false, erreur = messageDe(err))
                }
            }
        }
    }

    /** Page suivante d'une liste paginée — `offset = taille affichée`. */
    fun chargerSuite(cible: CiblePage) {
        val st = _état.value
        val offset = when (cible) {
            CiblePage.Devoirs -> st.devoirs.size
            CiblePage.Problèmes -> st.problèmes.size
            CiblePage.Corrections -> st.corrections.size
        }
        val total = st.total(cible) ?: return
        if (st.chargementSuite || st.envoi || offset >= total) return

        viewModelScope.launch {
            _état.update { it.copy(chargementSuite = true) }
            try {
                when (cible) {
                    CiblePage.Devoirs -> {
                        val page = container.communaute.devoirs(tri = st.tri, offset = offset)
                        _état.update { it.copy(devoirs = it.devoirs + page.éléments, totalDevoirs = page.total) }
                    }
                    CiblePage.Problèmes -> {
                        val page = container.communaute.problèmes(filtre = st.filtre, offset = offset)
                        _état.update { it.copy(problèmes = it.problèmes + page.éléments, totalProblèmes = page.total) }
                    }
                    CiblePage.Corrections -> {
                        val page = container.communaute.corrections(offset = offset)
                        _état.update { it.copy(corrections = it.corrections + page.éléments, totalCorrections = page.total) }
                    }
                }
                _état.update { it.copy(chargementSuite = false, erreur = null) }
            } catch (err: Exception) {
                _état.update { it.copy(chargementSuite = false, échec = messageDe(err)) }
            }
        }
    }

    fun choisirOnglet(onglet: OngletCommunautaire) {
        if (onglet == _état.value.onglet) return
        _état.update { it.copy(onglet = onglet, erreur = null, échec = null, message = null) }
        charger(force = true)
    }

    fun choisirTri(tri: TriDevoirs) {
        if (tri == _état.value.tri) return
        _état.update { it.copy(tri = tri) }
        charger(force = true)
    }

    fun choisirFiltre(filtre: FiltreHoraire) {
        if (filtre == _état.value.filtre) return
        _état.update { it.copy(filtre = filtre) }
        charger(force = true)
    }

    /** Bandeaux cliquables : acquittés d'un geste. */
    fun acquitter() {
        _état.update { it.copy(message = null, échec = null) }
    }

    // — Écritures ----------------------------------------------------------

    /** Vote ±1 : un second vote REMPLACE le premier (le serveur refuse 0).
     *  Rejouer le même vote = pas d'écriture du tout. */
    fun voter(id: Long, sens: Int) {
        if (sens != 1 && sens != -1) return
        val st = _état.value
        if (st.votes[id.toString()] == sens) return
        lancer {
            val misAJour = container.communaute.voter(id, sens)
            container.session.noterVoteLocal(id, sens)
            _état.update { état ->
                état.copy(devoirs = état.devoirs.map { d -> if (d.id == id) misAJour else d })
            }
        }
    }

    fun ouvrirCréationDevoir() {
        _état.update { it.copy(dialogue = DialogueCommunautaire.CréerDevoir()) }
    }

    fun ouvrirCréationProblème() {
        _état.update { it.copy(dialogue = DialogueCommunautaire.CréerProblème()) }
    }

    /** Correction rattachée éventuellement à un problème (date pré-remplie). */
    fun ouvrirCréationCorrection(problèmeId: Long? = null, date: String = "") {
        _état.update { it.copy(dialogue = DialogueCommunautaire.CréerCorrection(problèmeId = problèmeId, date = date)) }
    }

    fun ouvrirSignalement(cible: CibleSignalement, cibleId: Long) {
        _état.update { it.copy(dialogue = DialogueCommunautaire.Signaler(cible, cibleId)) }
    }

    fun ouvrirSuppression(cible: CibleSignalement, cibleId: Long, intitulé: String) {
        _état.update { it.copy(dialogue = DialogueCommunautaire.Supprimer(cible, cibleId, intitulé)) }
    }

    fun fermerDialogue() {
        if (_état.value.envoi) return
        (_état.value.dialogue as? DialogueCommunautaire.CréerDevoir)?.fichiers?.forEach { it.delete() }
        _état.update { it.copy(dialogue = null) }
    }

    fun saisirDevoir(matière: String, contenu: String, dateRemise: String) {
        _état.update { st ->
            val d = st.dialogue as? DialogueCommunautaire.CréerDevoir ?: return@update st
            st.copy(dialogue = d.copy(matière = matière, contenu = contenu, dateRemise = dateRemise, erreur = null))
        }
    }

    fun ajouterFichiersDevoir(context: android.content.Context, uris: List<android.net.Uri>) {
        viewModelScope.launch {
            val fichiers = uris.mapNotNull { uri -> school.greenwood.plus.util.Fichiers.copierDepuisSaf(context, uri) }
            _état.update { st ->
                val d = st.dialogue as? DialogueCommunautaire.CréerDevoir ?: return@update st
                val acceptés = fichiers.filter { it.length() <= 5L * 1024 * 1024 }
                val refusés = fichiers.size - acceptés.size
                st.copy(dialogue = d.copy(
                    fichiers = d.fichiers + acceptés,
                    erreur = if (refusés > 0) "Chaque fichier doit peser 5 Mo maximum." else null,
                ))
            }
        }
    }

    fun retirerFichierDevoir(fichier: java.io.File) {
        fichier.delete()
        _état.update { st ->
            val d = st.dialogue as? DialogueCommunautaire.CréerDevoir ?: return@update st
            st.copy(dialogue = d.copy(fichiers = d.fichiers - fichier))
        }
    }

    fun saisirProblème(description: String, date: String) {
        _état.update { st ->
            val d = st.dialogue as? DialogueCommunautaire.CréerProblème ?: return@update st
            st.copy(dialogue = d.copy(description = description, date = date, erreur = null))
        }
    }

    fun saisirCorrection(description: String, date: String) {
        _état.update { st ->
            val d = st.dialogue as? DialogueCommunautaire.CréerCorrection ?: return@update st
            st.copy(dialogue = d.copy(description = description, date = date, erreur = null))
        }
    }

    fun saisirSignalement(raison: String) {
        _état.update { st ->
            val d = st.dialogue as? DialogueCommunautaire.Signaler ?: return@update st
            st.copy(dialogue = d.copy(raison = raison, erreur = null))
        }
    }

    /** Valide et envoie le contenu du dialogue ouvert (Notice gérée à part). */
    fun envoyer() {
        when (val d = _état.value.dialogue) {
            is DialogueCommunautaire.CréerDevoir -> {
                if (d.matière.isBlank() || d.contenu.isBlank()) {
                    marquerErreurDialogue("Renseigne la matière et le contenu.")
                    return
                }
                val date = d.dateRemise.trim().takeIf { it.isNotBlank() }?.let {
                    dateSaisieVersIso(it) ?: run {
                        marquerErreurDialogue("Date invalide — JJ/MM/AAAA ou AAAA-MM-JJ.")
                        return
                    }
                }
                val créateur = container.communaute
                lancer {
                    val créé = créateur.créerDevoir(d.matière, d.contenu, date, d.fichiers)
                    d.fichiers.forEach { it.delete() }
                    container.devoirsCommunautairesModifiés.tryEmit(créé)
                    _état.update { st ->
                        st.copy(
                            devoirs = listOf(créé) + st.devoirs,
                            totalDevoirs = (st.totalDevoirs ?: 0) + 1,
                            message = "Devoir proposé aux familles",
                        )
                    }
                }
            }
            is DialogueCommunautaire.CréerProblème -> {
                if (d.description.isBlank() || d.date.isBlank()) {
                    marquerErreurDialogue("Décris le problème et indique la date.")
                    return
                }
                val date = dateSaisieVersIso(d.date) ?: run {
                    marquerErreurDialogue("Date invalide — JJ/MM/AAAA ou AAAA-MM-JJ.")
                    return
                }
                val créateur = container.communaute
                lancer {
                    val créé = créateur.créerProblème(d.description, date)
                    _état.update { st ->
                        st.copy(
                            problèmes = listOf(créé) + st.problèmes,
                            totalProblèmes = (st.totalProblèmes ?: 0) + 1,
                            message = "Problème signalé",
                        )
                    }
                }
            }
            is DialogueCommunautaire.CréerCorrection -> {
                if (d.description.isBlank() || d.date.isBlank()) {
                    marquerErreurDialogue("Décris la correction et indique la date.")
                    return
                }
                val date = dateSaisieVersIso(d.date) ?: run {
                    marquerErreurDialogue("Date invalide — JJ/MM/AAAA ou AAAA-MM-JJ.")
                    return
                }
                val créateur = container.communaute
                lancer {
                    val créé = créateur.créerCorrection(d.problèmeId, d.description, date)
                    _état.update { st ->
                        st.copy(
                            corrections = listOf(créé) + st.corrections,
                            totalCorrections = (st.totalCorrections ?: 0) + 1,
                            message = "Correction proposée",
                        )
                    }
                }
            }
            is DialogueCommunautaire.Signaler -> {
                if (d.raison.isBlank()) {
                    marquerErreurDialogue("Explique le motif du signalement.")
                    return
                }
                val dépôt = container.communaute
                lancer {
                    dépôt.signaler(d.cible, d.cibleId, d.raison)
                    _état.update { st ->
                        st.copy(
                            signalés = st.signalés + "${d.cible.param}:${d.cibleId}",
                            message = "Signalement envoyé à la modération",
                        )
                    }
                }
            }
            else -> Unit // Notice et Supprimer ont leur propre geste.
        }
    }

    /** Confirmation de suppression (dialogue de confirmation). */
    fun confirmerSuppression() {
        val d = _état.value.dialogue as? DialogueCommunautaire.Supprimer ?: return
        val dépôt = container.communaute
        lancer {
            when (d.cible) {
                CibleSignalement.Devoir -> {
                    dépôt.supprimerDevoir(d.cibleId)
                    _état.update { st ->
                        st.copy(
                            devoirs = st.devoirs.filterNot { it.id == d.cibleId },
                            totalDevoirs = st.totalDevoirs?.minus(1)?.coerceAtLeast(0),
                            message = "Devoir supprimé",
                        )
                    }
                }
                CibleSignalement.Problème -> {
                    dépôt.supprimerProblème(d.cibleId)
                    _état.update { st ->
                        st.copy(
                            problèmes = st.problèmes.filterNot { it.id == d.cibleId },
                            totalProblèmes = st.totalProblèmes?.minus(1)?.coerceAtLeast(0),
                            message = "Signalement supprimé",
                        )
                    }
                }
                CibleSignalement.Correction -> {
                    dépôt.supprimerCorrection(d.cibleId)
                    _état.update { st ->
                        st.copy(
                            corrections = st.corrections.filterNot { it.id == d.cibleId },
                            totalCorrections = st.totalCorrections?.minus(1)?.coerceAtLeast(0),
                            message = "Correction supprimée",
                        )
                    }
                }
            }
        }
    }

    // — Notice et cycle de vie du compte -----------------------------------

    fun accepterNotice() {
        viewModelScope.launch {
            container.communaute.accepterNotice()
            _état.update { it.copy(dialogue = null) }
            reprendre()
        }
    }

    /** Refus : le compte est révoqué côté serveur et oublié localement —
     *  la prochaine écriture en recrée un, avec une nouvelle notice. */
    fun refuserNotice() {
        viewModelScope.launch {
            enAttente = null
            container.communaute.révoquerCompte()
            _état.update { it.copy(dialogue = null, message = "Compte communautaire supprimé") }
        }
    }

    // — Machine d'état des écritures ---------------------------------------

    /** Mémorise l'écriture et la lance : `exécuter` s'occupe de tout. */
    private fun lancer(action: suspend () -> Unit) {
        if (_état.value.envoi || _état.value.attenteDébit != null) return
        enAttente = action
        viewModelScope.launch { exécuter() }
    }

    /** Relance l'écriture mémorisée (après notice acceptée ou débit écoulé). */
    private fun reprendre() {
        if (enAttente == null || _état.value.envoi) return
        viewModelScope.launch { exécuter() }
    }

    private suspend fun exécuter() {
        val action = enAttente ?: return
        _état.update { it.copy(envoi = true, erreur = null, échec = null, message = null) }
        try {
            action()
            enAttente = null
            // Le dialogue se ferme au succès ; un message de confirmation
            // posé par l'action lui-même survit (copie non destructive).
            _état.update { it.copy(envoi = false, dialogue = null) }
        } catch (err: NoticeRequise) {
            // Pas encore acceptée : on la lit et on garde l'action en veille.
            _état.update { it.copy(envoi = false) }
            afficherNotice()
        } catch (err: DépassementDébit) {
            _état.update { it.copy(envoi = false) }
            partirEnAttenteDébit(err.attenteSecondes)
        } catch (err: JetonRévoqué) {
            // Deuxième 401 d'affilée (le dépôt en a déjà recréé un) : on jette
            // la session et on invite à réessayer — jamais de boucle.
            enAttente = null
            container.session.oublierJeton()
            _état.update {
                it.copy(envoi = false, dialogue = null, échec = "Session expirée — réessaie dans un instant")
            }
        } catch (err: Exception) {
            enAttente = null
            val message = messageDe(err)
            _état.update { st ->
                st.copy(
                    envoi = false,
                    dialogue = st.dialogue?.avecErreur(message),
                    échec = if (st.dialogue == null) message else null,
                )
            }
        }
    }

    private suspend fun afficherNotice() {
        _état.update { it.copy(noticeChargement = true) }
        try {
            val mentions = container.communaute.mentions()
            _état.update {
                it.copy(noticeChargement = false, dialogue = DialogueCommunautaire.Notice(mentions))
            }
        } catch (err: Exception) {
            enAttente = null
            _état.update { it.copy(noticeChargement = false, échec = messageDe(err)) }
        }
    }

    /** Compte à rebours du 429 puis reprise automatique de l'écriture. */
    private fun partirEnAttenteDébit(secondes: Long?) {
        var reste = secondes ?: 30L
        reste = reste.coerceIn(1, 120)
        _état.update { it.copy(attenteDébit = reste.toInt()) }
        viewModelScope.launch {
            while (reste > 0) {
                delay(1_000)
                reste--
                _état.update { it.copy(attenteDébit = reste.toInt()) }
            }
            _état.update { it.copy(attenteDébit = null) }
            reprendre()
        }
    }

    private fun marquerErreurDialogue(message: String) {
        _état.update { st -> st.copy(dialogue = st.dialogue?.avecErreur(message)) }
    }

    /** Message lisible selon le type d'échec — le texte du serveur prime
     *  (il est déjà en français et déjà explicite). */
    private fun messageDe(err: Exception): String = when (err) {
        is CommunErreur -> err.message
        is java.io.IOException -> "Connexion impossible — vérifie le réseau"
        else -> "Le serveur communautaire ne répond pas"
    }
}

private fun CommunauteÉtat.vide(onglet: OngletCommunautaire): Boolean = when (onglet) {
    OngletCommunautaire.Devoirs -> devoirs.isEmpty()
    OngletCommunautaire.EmploiDuTemps -> problèmes.isEmpty() && corrections.isEmpty()
    OngletCommunautaire.Abus -> abus.isEmpty()
}

private fun CommunauteÉtat.total(cible: CiblePage): Int? = when (cible) {
    CiblePage.Devoirs -> totalDevoirs
    CiblePage.Problèmes -> totalProblèmes
    CiblePage.Corrections -> totalCorrections
}
