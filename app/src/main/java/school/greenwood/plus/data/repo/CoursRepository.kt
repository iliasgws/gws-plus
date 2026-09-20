package school.greenwood.plus.data.repo

import school.greenwood.plus.data.api.BotiClient
import school.greenwood.plus.data.cache.CachesSession
import school.greenwood.plus.model.SemaineCours
import java.time.LocalDate

/*
 * Dépôt de l'emploi du temps (GET `cours_v2`).
 *
 * La forme de tête est vérifiée (ENDPOINT-MAP, 2026-09-20) ; les créneaux
 * intérieurs ne le sont pas (sondage : `seances[]` vide). La navigation entre
 * semaines n'a pas de paramètre documenté : on tente `date=<ISO lundi>` — si
 * le serveur l'ignore, la semaine affichée reste celle qu'il renvoie.
 */
class CoursRepository(
    private val client: BotiClient,
    private val caches: CachesSession,
) {

    /** La semaine courante, ou celle visée via `date` (paramètre NON vérifié). */
    suspend fun semaine(date: LocalDate? = null): SemaineCours? {
        val extra = date?.let { mapOf("date" to it.toString()) } ?: emptyMap()
        val rep = client.get("cours_v2", extra)
        val semaine = Normalizers.semaineCours(rep)
        // Seule la semaine courante sert l'ouverture à chaud — les navigations
        // ciblées ne polluent pas le cache (issue #21).
        if (date == null && semaine != null) {
            caches.clé()?.let { clé -> caches.cours.écrire(clé, semaine) }
        }
        return semaine
    }

    /** Dernière semaine connue, pour l'ouverture à chaud (issue #21). */
    suspend fun semaineEnCache(): SemaineCours? {
        val clé = caches.clé() ?: return null
        return caches.cours.lire(clé)
    }
}
