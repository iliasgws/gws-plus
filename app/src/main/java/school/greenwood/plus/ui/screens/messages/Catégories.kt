package school.greenwood.plus.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import school.greenwood.plus.model.ThemeMessage
import school.greenwood.plus.ui.components.ErrorInline
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.RegistreTheme

/*
 * Les genres (catégories) de message — issue #99. Les deux chemins d'envoi
 * (nouveau fil et réponse) proposent les catégories du serveur (`themes[]` du
 * GET `messages`, ids 8/9/10/11/13) : l'envoi n'en part plus jamais en blanc
 * par inadvertance, et plus rien n'est masqué en silence.
 *
 * Règles (couvertes par CatégoriesMessageTest) :
 * - le serveur propose des catégories → le choix est obligatoire ;
 * - le serveur n'en propose aucune (liste vide, SANS erreur de lecture) →
 *   rien à choisir, l'envoi part avec `theme = ""` faute de mieux ;
 * - catégories indisponibles (échec de lecture, rien en cache) → envoi
 *   bloqué, l'état affiche « Réessayer » au lieu d'un champ disparu.
 */

/** Refus de l'envoi d'un nouveau fil tant qu'aucune catégorie n'est choisie
 *  (null = l'envoi est possible). `chargement` ferme la fenêtre où les
 *  catégories ne sont pas encore arrivées : rien ne part avant. */
internal fun refusCatégorieNouveau(
    themeChoisi: String?,
    themes: List<ThemeMessage>,
    themesEnÉchec: Boolean,
    chargement: Boolean = false,
): String? = when {
    themesEnÉchec && themes.isEmpty() -> "Catégories indisponibles — réessaie"
    themes.isEmpty() && chargement -> "Chargement des catégories…"
    themes.isNotEmpty() && themeChoisi.isNullOrBlank() -> "Choisis une catégorie"
    else -> null
}

/** La catégorie d'une réponse : celle choisie dans le composeur, sinon celle
 *  du fil — jamais vide par inadvertance (bundle : `theme: this.result.theme`). */
internal fun themeDeRéponse(choisi: String?, fil: String?): String =
    choisi?.takeIf { it.isNotBlank() } ?: fil.orEmpty()

/** Refus de l'envoi d'une réponse : un fil sans catégorie doit en recevoir
 *  une au composeur (null = l'envoi est possible). Un fil qui porte déjà sa
 *  catégorie part toujours, même pendant le chargement des autres. */
internal fun refusCatégorieRéponse(
    choisi: String?,
    fil: String?,
    themes: List<ThemeMessage>,
    themesEnÉchec: Boolean,
    chargement: Boolean = false,
): String? = when {
    themeDeRéponse(choisi, fil).isNotBlank() -> null
    themesEnÉchec && themes.isEmpty() -> "Catégories indisponibles — réessaie"
    themes.isEmpty() && chargement -> "Chargement des catégories…"
    themes.isNotEmpty() -> "Choisis une catégorie"
    else -> null
}

/**
 * Bloc « Catégorie » des deux écrans d'envoi : les puces `themes[]`, ou leur
 * état de repli (chargement, échec + Réessayer) — jamais masqué en silence
 * (issue #99). `hint` porte le refus d'envoi du moment, affiché sous les puces.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoixCatégorie(
    themes: List<ThemeMessage>,
    choisi: String?,
    onChoisir: (ThemeMessage) -> Unit,
    chargement: Boolean,
    erreur: Boolean,
    réessayer: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    // L'état du bloc (chargement, échec) a sa propre ligne : le refus qui va
    // avec ne serait qu'un doublon.
    val étatAffiché = themes.isEmpty() && (erreur || chargement)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Catégorie",
            style = MaterialTheme.typography.labelMedium,
            color = RegistreTheme.colors.chalk,
        )
        // Les puces `themes[]` — plus, si besoin, la catégorie déjà portée
        // par le fil mais absente de la liste (label honnête : elle reste
        // visible et l'envoi la conserve).
        if (themes.isNotEmpty() || !choisi.isNullOrBlank()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (choisi != null && themes.none { it.id == choisi }) {
                    PuceThème(label = "Catégorie du fil", choisi = true, onChoisir = {})
                }
                themes.forEach { theme ->
                    PuceThème(
                        label = theme.label,
                        choisi = theme.id == choisi,
                        onChoisir = { onChoisir(theme) },
                    )
                }
            }
        }
        if (themes.isEmpty() && chargement) {
            Text(
                text = "Chargement des catégories…",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
        }
        if (themes.isEmpty() && erreur) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ErrorInline(
                    message = "Catégories indisponibles",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = réessayer) {
                    Text(
                        text = "Réessayer",
                        style = MaterialTheme.typography.labelMedium,
                        color = RegistreTheme.colors.ink,
                    )
                }
            }
        }
        if (themes.isEmpty() && !chargement && !erreur && choisi.isNullOrBlank()) {
            Text(
                text = "Aucune catégorie proposée pour le moment.",
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.chalk,
            )
        }
        if (!étatAffiché) {
            hint?.let { ErrorInline(message = it) }
        }
    }
}

/** Puce de catégorie (issue #10) : sélectionnée en encre pleine, au repos sur
 *  le fond sage. */
@Composable
internal fun PuceThème(label: String, choisi: Boolean, onChoisir: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(AnnotationShape)
            .background(if (choisi) RegistreTheme.colors.ink else RegistreTheme.colors.sage)
            .clickable(onClick = onChoisir)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (choisi) RegistreTheme.colors.page else RegistreTheme.colors.ink,
        )
    }
}
