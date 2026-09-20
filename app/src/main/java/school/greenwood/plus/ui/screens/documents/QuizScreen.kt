package school.greenwood.plus.ui.screens.documents

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import school.greenwood.plus.ui.components.GlassSurface
import school.greenwood.plus.ui.components.GwsBouton
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.LiquidIconButton
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SqueletteQuiz
import school.greenwood.plus.ui.components.givre
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Le quiz en question (issue #17). Flow officiel bundle 2.4.14 (page
 * /parent/quiz) : départ → question par question (décompte `temps_reponse`,
 * feedback immédiat — le serveur porte le drapeau correct — puis 2 s
 * d'avance) → POST du tableau de questions enrichi, score affiché. Un échec
 * d'enregistrement ne casse rien : le score local reste affiché.
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

    // La liste défile sous la barre flottante (Shell y a ajouté sa hauteur).
    val bas = padding.calculateBottomPadding()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.padding(
                top = padding.calculateTopPadding() + 8.dp,
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidIconButton(
                onClick = retour,
                surfaceColor = givre(),
            ) {
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
            état.chargement -> Box(Modifier.fillMaxSize().padding(bottom = bas)) {
                SqueletteQuiz()
            }
            état.erreur != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = bas),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorInline(message = état.erreur ?: "")
                GwsBouton(
                    texte = "Réessayer",
                    onClick = { vm.charger() },
                )
            }
            else -> {
                val quiz = état.quiz ?: return@Column
                when (état.phase) {
                    PhaseQuiz.Départ -> DépartQuiz(
                        quiz,
                        peutJouer = quiz.peutJouer,
                        bas = bas,
                        démarrer = vm::démarrer,
                    )
                    PhaseQuiz.Jeu -> JeuQuiz(état, vm, bas = bas)
                    PhaseQuiz.Résultat -> RésultatQuiz(état, quiz, bas = bas, rejouer = vm::démarrer)
                }
            }
        }
    }
}

/** Carte de départ : visuel, matières, volume, bouton « Démarrer le quiz ». */
@Composable
private fun DépartQuiz(
    quiz: QuizDetail,
    peutJouer: Boolean,
    bas: Dp,
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
            .padding(horizontal = 16.dp)
            .padding(bottom = bas),
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
                    quiz.matiere?.takeIf { it.isNotBlank() }?.let { Puce(it) }
                    quiz.niveau?.takeIf { it.isNotBlank() }?.let { Puce(it) }
                }
                Text(
                    text = duréeQuiz(quiz),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RegistreTheme.colors.chalk,
                )
                GwsBouton(
                    texte = "Démarrer le quiz",
                    onClick = démarrer,
                )
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
private fun JeuQuiz(état: QuizÉtat, vm: QuizViewModel, bas: Dp) {
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
            .padding(horizontal = 16.dp)
            .padding(bottom = bas),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Question ${état.indexQuestion + 1} sur $total",
                style = MaterialTheme.typography.labelMedium,
                color = RegistreTheme.colors.chalk,
                modifier = Modifier.weight(1f),
            )
            état.secondesRestantes?.let { restantes ->
                Puce(horloge(restantes))
            }
        }
        LinearProgressIndicator(
            progress = { état.indexQuestion / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = RegistreTheme.colors.ink,
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
 * Une réponse : verre calme avant le choix ; au retour visuel, la réponse
 * jouée passe à sage (bonne) ou stylo rouge (mauvaise) avec son picto —
 * mêmes sémantiques que le bundle (correct / wrong), palette du registre.
 * Les teintes pleines restent pleines : un verdict se lit, il ne se voit
 * pas à travers. Les autres réponses se figent le temps du retour (bundle : pause).
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
    val cadre = Modifier
        .fillMaxWidth()
        .clickable(enabled = !jouée, onClick = onClick)
    if (marquée) {
        Surface(
            shape = PageShape,
            color = if (correcte) RegistreTheme.colors.sage else RegistreTheme.colors.redPen,
            modifier = cadre,
        ) {
            ContenuRéponse(texte = texte, marquée = true, correcte = correcte)
        }
    } else {
        GlassSurface(
            shape = PageShape,
            modifier = cadre,
        ) {
            ContenuRéponse(texte = texte, marquée = false, correcte = false)
        }
    }
}

@Composable
private fun ContenuRéponse(texte: String, marquée: Boolean, correcte: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (marquée) {
            Icon(
                imageVector = if (correcte) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                contentDescription = if (correcte) "Bonne réponse" else "Mauvaise réponse",
                tint = if (correcte) RegistreTheme.colors.ink else RegistreTheme.colors.page,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = texte,
            style = MaterialTheme.typography.bodyMedium,
            color = if (marquée && !correcte) RegistreTheme.colors.page else RegistreTheme.colors.ink,
        )
    }
}

/** Fin du quiz : score (serveur si confirmé, local sinon) + rejouer. */
@Composable
private fun RésultatQuiz(
    état: QuizÉtat,
    quiz: QuizDetail,
    bas: Dp,
    rejouer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = bas),
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
                    GwsBouton(
                        texte = "Rejouer",
                        onClick = rejouer,
                    )
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
