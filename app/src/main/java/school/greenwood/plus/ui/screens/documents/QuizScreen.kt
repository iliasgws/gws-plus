package school.greenwood.plus.ui.screens.documents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.QuizDetail
import school.greenwood.plus.model.QuizQuestion
import school.greenwood.plus.ui.PhaseQuiz
import school.greenwood.plus.ui.QuizViewModel
import school.greenwood.plus.ui.QuizÉtat
import school.greenwood.plus.ui.components.EmptyState
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SqueletteQuiz
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Le quiz en question (issue #17). Flow officiel bundle 2.4.14 (page
 * /parent/quiz) : départ → question par question (décompte `temps_reponse`,
 * feedback immédiat — le serveur porte le drapeau correct — puis 2 s
 * d'avance) → POST du tableau de questions enrichi, score affiché. Un échec
 * d'enregistrement ne casse rien : le score local reste affiché.
 *
 * Sortie protégée pendant le jeu (phase Jeu) : geste/retour système, flèche
 * d'en-tête et changement d'onglet (côté coquille, via le signal
 * `container.quizEnJeu`) demandent confirmation — un quiz quitté perd ses
 * réponses, le score n'est pas enregistré.
 */

@Composable
fun QuizScreen(
    container: AppContainer,
    padding: PaddingValues,
    quizId: String,
    retour: () -> Unit,
) {
    val vm: QuizViewModel = viewModel(key = "quiz-$quizId") { QuizViewModel(container, quizId) }
    val état by vm.état.collectAsStateWithLifecycle()

    // Confirmation de sortie pendant la partie : le retour (geste ou bouton
    // système) et la flèche d'en-tête demandent avant d'abandonner.
    var demanderQuitter by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = état.phase == PhaseQuiz.Jeu) { demanderQuitter = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (état.phase == PhaseQuiz.Jeu) demanderQuitter = true else retour()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
            Text(
                text = "Quiz",
                style = MaterialTheme.typography.displayLarge,
                color = RegistreTheme.colors.ink,
            )
        }

        when {
            état.chargement -> SqueletteQuiz()
            état.erreur != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorInline(message = état.erreur ?: "")
                Button(
                    onClick = { vm.charger() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.ink,
                        contentColor = RegistreTheme.colors.page,
                    ),
                    shape = ControlShape,
                ) {
                    Text("Réessayer")
                }
            }
            else -> {
                val quiz = état.quiz ?: return@Column
                when (état.phase) {
                    PhaseQuiz.Départ -> DépartQuiz(quiz, peutJouer = quiz.peutJouer, démarrer = vm::démarrer)
                    PhaseQuiz.Jeu -> JeuQuiz(état, vm)
                    PhaseQuiz.Résultat -> RésultatQuiz(état, quiz, rejouer = vm::démarrer)
                }
            }
        }
    }

    if (demanderQuitter) {
        DialogueQuitterQuiz(
            onContinuer = { demanderQuitter = false },
            onQuitter = {
                demanderQuitter = false
                retour()
            },
        )
    }
}

/**
 * Confirmation avant d'abandonner un quiz en cours — partagée par l'écran
 * (retour système, flèche d'en-tête) et la coquille (changement d'onglet) :
 * un quiz quitté perd ses réponses, le score n'est pas enregistré.
 */
@Composable
internal fun DialogueQuitterQuiz(
    onContinuer: () -> Unit,
    onQuitter: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onContinuer,
        title = { Text("Quitter le quiz ?", color = RegistreTheme.colors.ink) },
        text = {
            Text(
                text = "Le quiz est en cours — vos réponses seront perdues et le " +
                    "score ne sera pas enregistré.",
                color = RegistreTheme.colors.ink,
            )
        },
        confirmButton = {
            TextButton(onClick = onQuitter) {
                Text("Quitter", color = RegistreTheme.colors.ink)
            }
        },
        dismissButton = {
            TextButton(onClick = onContinuer) {
                Text("Continuer le quiz", color = RegistreTheme.colors.ink)
            }
        },
    )
}

/** Carte de départ : visuel, matières, volume, bouton « Démarrer le quiz ». */
@Composable
private fun DépartQuiz(
    quiz: QuizDetail,
    peutJouer: Boolean,
    démarrer: () -> Unit,
) {
    if (!peutJouer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                titre = "Quiz indisponible",
                message = "Ce quiz ne peut pas être lancé pour le moment.",
            )
        }
        return
    }
    if (quiz.questions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                titre = "Quiz vide",
                message = "Ce quiz n'a aucune question pour l'instant.",
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        GwsCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                quiz.image?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(PageShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    text = quiz.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = RegistreTheme.colors.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quiz.matiere?.takeIf { it.isNotBlank() }?.let { Puce(it, accent = RegistreTheme.accent) }
                    quiz.niveau?.takeIf { it.isNotBlank() }?.let { Puce(it, accent = RegistreTheme.accent) }
                }
                Text(
                    text = duréeQuiz(quiz),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.chalk,
                )
                Button(
                    onClick = démarrer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegistreTheme.colors.ink,
                        contentColor = RegistreTheme.colors.page,
                    ),
                    shape = ControlShape,
                ) {
                    Text("Démarrer le quiz")
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = "Une bonne réponse s'affiche aussitôt — le score est " +
                "enregistré à la fin du quiz.",
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.chalk,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** « 5 questions · 5 min » — la durée serveur arrive en « 05:00 ». */
internal fun duréeQuiz(quiz: QuizDetail): String {
    val minutes = quiz.minutes?.takeIf { it.isNotBlank() }?.let { brute ->
        val parties = brute.split(":")
        val n = parties.firstOrNull()?.trim()?.toIntOrNull()
        if (n != null && n > 0) "$n min" else brute
    }
    return buildString {
        append(quiz.nbQuestions)
        append(if (quiz.nbQuestions == 1) " question" else " questions")
        minutes?.let { append(" · ").append(it) }
    }
}

/** Le quiz joué : question courante, décompte, réponses. */
@Composable
private fun JeuQuiz(état: QuizÉtat, vm: QuizViewModel) {
    val quiz = état.quiz ?: return
    val question = quiz.questions.getOrNull(état.indexQuestion) ?: return

    // Décompte de la question — figé dès qu'une réponse est jouée, repart à
    // zéro à chaque question (bundle : initTimer). À zéro : sans réponse.
    // Question sans `temps_reponse` : pas de décompte, on attend le choix.
    LaunchedEffect(état.indexQuestion, état.réponseChoisie, état.phase) {
        if (état.phase == PhaseQuiz.Jeu && état.réponseChoisie == null &&
            état.secondesRestantes != null
        ) {
            while ((état.secondesRestantes ?: 0) > 0) {
                delay(1000)
                vm.tickHorloge()
            }
            vm.tempsÉcoulé()
        }
    }
    // Retour visuel 2 s puis avance (bundle : setTimeout(…, 2e3)).
    LaunchedEffect(état.réponseChoisie) {
        if (état.réponseChoisie != null) {
            delay(2000)
            vm.avancer()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EnTêteQuestion(état, quiz.nbQuestions)
        GwsCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = question.texte,
                    style = MaterialTheme.typography.titleMedium,
                    color = RegistreTheme.colors.ink,
                )
                question.image?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(PageShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        question.reponses.forEachIndexed { index, réponse ->
            CarteRéponse(
                texte = réponse.texte,
                jouée = état.réponseChoisie != null,
                choisie = état.réponseChoisie == index,
                correcte = réponse.correcte,
                onClick = { vm.répondre(index) },
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

/** « Question 2 sur 5 », barre de progression et décompte mm:ss. */
@Composable
private fun EnTêteQuestion(état: QuizÉtat, total: Int) {
    val accent = RegistreTheme.accent
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Question ${état.indexQuestion + 1} sur $total",
                style = MaterialTheme.typography.labelMedium.tabulaire(),
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.weight(1f),
            )
            état.secondesRestantes?.let { restantes ->
                Puce(horloge(restantes), accent = accent)
            }
        }
        LinearProgressIndicator(
            progress = { état.indexQuestion / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = accent.teinte,
            trackColor = RegistreTheme.colors.sage,
        )
    }
}

/** mm:ss, l'horloge du bundle (getSecondsAsDigitalClock). */
internal fun horloge(secondes: Int): String {
    val s = secondes.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/**
 * Une réponse : page calme avant le choix ; au retour visuel, la réponse
 * jouée passe à sage (bonne) ou stylo rouge (mauvaise) avec son picto —
 * mêmes sémantiques que le bundle (correct / wrong), palette du registre.
 * Les autres réponses se figent le temps du retour (bundle : pause).
 */
@Composable
private fun CarteRéponse(
    texte: String,
    jouée: Boolean,
    choisie: Boolean,
    correcte: Boolean,
    onClick: () -> Unit,
) {
    val marquée = jouée && choisie
    val accent = RegistreTheme.accent
    val vert = RegistreTheme.colors.accents["registre"]
    Surface(
        shape = ControlShape,
        color = when {
            marquée && correcte -> vert?.conteneur ?: RegistreTheme.colors.sage
            marquée -> RegistreTheme.colors.redPen
            else -> RegistreTheme.colors.page
        },
        border = if (marquée) null else BorderStroke(1.dp, RegistreTheme.colors.sage),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !jouée, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (marquée) {
                Icon(
                    imageVector = if (correcte) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = if (correcte) "Bonne réponse" else "Mauvaise réponse",
                    tint = if (correcte) (vert?.teinte ?: RegistreTheme.colors.ink) else RegistreTheme.colors.page,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = texte,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    marquée && correcte -> (vert?.surConteneur ?: RegistreTheme.colors.ink)
                    marquée -> RegistreTheme.colors.page
                    else -> RegistreTheme.colors.ink
                },
            )
        }
    }
}

/** Fin du quiz : score (serveur si confirmé, local sinon) + rejouer. */
@Composable
private fun RésultatQuiz(
    état: QuizÉtat,
    quiz: QuizDetail,
    rejouer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GwsCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Terminé !",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RegistreTheme.colors.ink,
                )
                Text(
                    text = quiz.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.chalk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(2.dp))
                val résultat = état.résultat
                when {
                    // Score confirmé par le serveur, tel que renvoyé.
                    résultat?.score != null -> {
                        LigneScore("Score", résultat.score)
                        résultat.temps?.let { LigneScore("Temps", it) }
                    }
                    else -> {
                        LigneScore(
                            "Score",
                            "${état.scoreLocal} sur ${quiz.nbQuestions}",
                        )
                    }
                }
                when {
                    état.envoiScore -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            color = RegistreTheme.colors.ink,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Enregistrement du score…",
                            style = MaterialTheme.typography.bodySmall,
                            color = RegistreTheme.colors.chalk,
                        )
                    }
                    état.échecEnvoi -> ErrorInline(
                        message = "Score non enregistré par le serveur — " +
                            "il reste affiché ici.",
                    )
                }
                val peutRejouer = état.résultat?.peutRejouer ?: quiz.peutRejouer
                if (peutRejouer) {
                    Button(
                        onClick = rejouer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegistreTheme.colors.ink,
                            contentColor = RegistreTheme.colors.page,
                        ),
                        shape = ControlShape,
                    ) {
                        Text("Rejouer")
                    }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun LigneScore(label: String, valeur: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = RegistreTheme.colors.chalk,
        )
        Text(
            text = valeur,
            style = MaterialTheme.typography.titleLarge,
            color = RegistreTheme.colors.ink,
        )
    }
}
