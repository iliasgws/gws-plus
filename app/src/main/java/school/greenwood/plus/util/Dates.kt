package school.greenwood.plus.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * Les dates du serveur arrivent prêtes pour l'affichage (« 📥 le 01/09/2026 à
 * 12:47 ») ou en formats techniques hétéroclites selon l'endpoint. On extrait
 * par regex plutôt que d'énumérer des formats : c'est résilient aux emojis,
 * préfixes « Vu le … » et variations du bundle d'origine.
 */

private val SLASH_DATE = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
private val ISO_DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")
private val TIME = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")

/** Première date trouvée, « dd/mm/yyyy » ou « yyyy-mm-dd », sinon null. */
fun extractDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    SLASH_DATE.find(raw)?.let { m ->
        val (d, mth, y) = m.destructured
        return runCatching { LocalDate.of(y.toInt(), mth.toInt(), d.toInt()) }.getOrNull()
    }
    ISO_DATE.find(raw)?.let { m ->
        val (y, mth, d) = m.destructured
        return runCatching { LocalDate.of(y.toInt(), mth.toInt(), d.toInt()) }.getOrNull()
    }
    return null
}

/** Première date + heure trouvées ; minuit quand l'heure manque. */
fun extractDateTime(raw: String?): LocalDateTime? {
    val date = extractDate(raw) ?: return null
    val time = raw?.let { TIME.find(it) }?.destructured
    return if (time != null) {
        val (h, min, s) = time
        val heure = runCatching {
            LocalTime.of(h.toInt(), min.toInt(), s.takeIf { it.isNotEmpty() }?.toInt() ?: 0)
        }.getOrDefault(LocalTime.MIDNIGHT)
        LocalDateTime.of(date, heure)
    } else {
        LocalDateTime.of(date, LocalTime.MIDNIGHT)
    }
}

/** Long form français pour l'en-tête du registre : « jeudi 18 septembre ». */
fun LocalDate.frenchLongDay(): String =
    this.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))

/** Forme courte pour les entrées de flux : « 18 sept. » */
fun LocalDate.frenchShort(): String =
    this.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))

/** Heure en « 12:47 ». */
fun LocalDateTime.frenchTime(): String =
    this.format(DateTimeFormatter.ofPattern("HH:mm"))

/** Forme complète pour les détails : « le 01/09/2026 à 12:47 ». */
fun LocalDateTime.frenchFull(): String =
    this.format(DateTimeFormatter.ofPattern("'le' dd/MM/yyyy 'à' HH:mm", Locale.FRENCH))

/** Date seule pour les listes : « 01/09/2026 ». */
fun LocalDate.frenchNumeric(): String =
    this.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH))

/** Date longue relative lisible dans un choix de système. */
fun LocalDate.mediumLocalized(): String =
    this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.FRENCH))
