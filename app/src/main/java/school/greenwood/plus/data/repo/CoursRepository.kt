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
 * semaines renvoie au serveur son propre champ : `last_week=`/`next_week=`
 * avec la valeur ISO qu'il a publiée — vérifié en sonde 2026-09-22 (un
 * `date=` générique est ignoré silencieusement ; l'app officielle fait de
 * même, chunk 6109.js : `prev(e)` → `last_week:e`, `next(e)` → `next_week:e`).
 */
class CoursRepository(
    private val client: BotiClient,
    private val caches: CachesSession,
) {

    /** La semaine courante, ou celle visée via le champ de navigation du serveur. */
    suspend fun semaine(sens: SensSemaine? = null, lundi: LocalDate? = null): SemaineCours? {
        val extra = if (sens != null && lundi != null) mapOf(sens.champ to lundi.toString()) else emptyMap()
        val rep = client.get("cours_v2", extra)
        val semaine = Normalizers.semaineCours(rep)
        // Seule la semaine courante sert l'ouverture à chaud — les navigations
        // ciblées ne polluent pas le cache (issue #21).
        if (sens == null && semaine != null) {
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

/** Sens de navigation ←/→ : le serveur attend son propre nom de champ. */
enum class SensSemaine(val champ: String) {
    Précédente("last_week"),
    Suivante("next_week"),
}
