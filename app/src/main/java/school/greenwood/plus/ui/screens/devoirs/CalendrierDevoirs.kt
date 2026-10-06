package school.greenwood.plus.ui.screens.devoirs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.GwsPlusTheme
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire

/** Monday-first, whole-week grid; null cells leave adjacent months unambiguous. */
internal fun joursDuMois(mois: YearMonth): List<LocalDate?> {
    val décalage = mois.atDay(1).dayOfWeek.value - 1
    val cellules = ((décalage + mois.lengthOfMonth() + 6) / 7) * 7
    return List(cellules) { index ->
        val jour = index - décalage + 1
        if (jour in 1..mois.lengthOfMonth()) mois.atDay(jour) else null
    }
}

@Composable
internal fun CalendrierDevoirs(
    jourChoisi: LocalDate,
    aujourdhui: LocalDate,
    étatsJours: Map<LocalDate, ÉtatJour>,
    onChoisirJour: (LocalDate) -> Unit,
    moisAffiché: String,
    onChangerMois: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mois = YearMonth.parse(moisAffiché)
    val accent = RegistreTheme.accent
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = RegistreTheme.colors.page) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChangerMois(mois.minusMonths(1).toString()) }) {
                    Icon(Icons.Rounded.ChevronLeft, "Mois précédent", tint = RegistreTheme.colors.ink)
                }
                Text(
                    mois.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = RegistreTheme.colors.ink,
                )
                IconButton(onClick = { onChangerMois(mois.plusMonths(1).toString()) }) {
                    Icon(Icons.Rounded.ChevronRight, "Mois suivant", tint = RegistreTheme.colors.ink)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { lettre ->
                    Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                        Text(lettre, style = MaterialTheme.typography.labelSmall, color = RegistreTheme.colors.chalk)
                    }
                }
            }
            joursDuMois(mois).chunked(7).forEach { semaine ->
                Row(Modifier.fillMaxWidth()) {
                    semaine.forEach { jour ->
                        if (jour == null) {
                            Spacer(Modifier.weight(1f).height(48.dp))
                        } else {
                            val sélectionné = jour == jourChoisi
                            val suivi = étatsJours[jour]
                            val description = when (suivi) {
                                ÉtatJour.Vert -> "Devoirs faits ou échéance passée"
                                ÉtatJour.Orange -> "Fait pour vous, mais pas encore pour l'école"
                                ÉtatJour.Rouge -> "Devoirs à faire"
                                ÉtatJour.Communauté -> "Propositions des familles"
                                null -> "Aucun devoir"
                            }
                            Surface(
                                modifier = Modifier.weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(ControlShape)
                                    .selectable(sélectionné, role = Role.Button, onClick = { onChoisirJour(jour) })
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = jour.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)) +
                                            (if (jour == aujourdhui) ", aujourd'hui" else "") + ", " + description
                                    },
                                shape = ControlShape,
                                color = if (sélectionné) accent.conteneur else RegistreTheme.colors.page,
                                border = if (jour == aujourdhui) BorderStroke(1.dp, accent.teinte) else null,
                            ) {
                                Column(
                                    Modifier.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        jour.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelLarge.tabulaire(),
                                        color = if (sélectionné) accent.surConteneur else RegistreTheme.colors.ink,
                                    )
                                    if (suivi == null) Spacer(Modifier.size(6.dp)) else Box(
                                        Modifier.size(6.dp).clip(CircleShape).background(when (suivi) {
                                            ÉtatJour.Vert -> RegistreTheme.colors.accents.getValue("registre").teinte
                                            ÉtatJour.Orange -> RegistreTheme.colors.signetVif
                                            ÉtatJour.Rouge -> RegistreTheme.colors.redPen
                                            ÉtatJour.Communauté -> accent.teinte
                                        }),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "• Un point = des devoirs",
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = RegistreTheme.colors.chalk,
                )
                TextButton(onClick = {
                    onChangerMois(YearMonth.from(aujourdhui).toString())
                    onChoisirJour(aujourdhui)
                }) { Text("Aujourd'hui", color = accent.teinte) }
            }
            val ailleurs = étatsJours.any { (jour, suivi) ->
                suivi == ÉtatJour.Rouge && YearMonth.from(jour) != mois
            }
            if (ailleurs) Text(
                "Des devoirs restent à faire dans un autre mois.",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = RegistreTheme.colors.redPen,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendrierDevoirsPreview() {
    GwsPlusTheme {
        val jour = LocalDate.of(2026, 10, 5)
        var mois by rememberSaveable { mutableStateOf("2026-10") }
        CalendrierDevoirs(
            jour, jour, mapOf(jour to ÉtatJour.Rouge, jour.plusDays(2) to ÉtatJour.Orange), {},
            moisAffiché = mois, onChangerMois = { mois = it },
        )
    }
}
