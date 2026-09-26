package school.greenwood.plus.data.session

import kotlinx.coroutines.flow.MutableSharedFlow

/** Veille de l'application : l'activité note son arrêt (onStop) puis sa
 *  reprise (onStart) ; si l'absence a duré au moins la durée réglée dans les
 *  paramètres, la reprise émet un signal que chaque écran chargé écoute pour
 *  rafraîchir en silence — le contenu connu reste affiché, aucun écran n'est
 *  remis à zéro. Pur Kotlin (testable sans Android, horodatages injectés). */
class VeilleSession {

    val retoursPérimés = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Instant du dernier arrêt ; null tant que l'activité n'a jamais été
     *  arrêtée (démarrage à froid) — la première reprise ne déclenche rien. */
    @Volatile
    private var dernierArrêt: Long? = null

    /** L'application passe en arrière-plan : l'instant de l'arrêt est gardé. */
    fun enregistrerArrêt(horodatage: Long = System.currentTimeMillis()) {
        dernierArrêt = horodatage
    }

    /** Retour au premier plan : true si l'absence a dépassé la durée choisie
     *  en minutes (0 = « jamais ») et que le signal a été émis — une seule
     *  fois par reprise, l'arrêt est oublié. */
    fun enregistrerReprise(minutes: Int, horodatage: Long = System.currentTimeMillis()): Boolean {
        val arrêt = dernierArrêt
        dernierArrêt = null
        if (arrêt == null || minutes <= 0 || horodatage - arrêt < minutes * 60_000L) return false
        return retoursPérimés.tryEmit(Unit)
    }
}
