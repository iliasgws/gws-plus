package school.greenwood.plus.data.cache

/** Cache mémoire estampillé par une clé de session : la donnée n'est servie
 *  que si la clé de lecture est identique à celle de l'écriture. Pur Kotlin
 *  (testable sans Android). */
class MemoireSession<T> {

    private val verrou = Any()

    @Volatile
    private var clé: String? = null

    @Volatile
    private var valeur: T? = null

    /** Dernière donnée écrite, mais seulement si [clé] est non nulle, non
     *  blanche et identique à la clé de l'écriture — sinon null. */
    fun lire(clé: String?): T? = synchronized(verrou) {
        if (clé.isNullOrBlank() || clé != this.clé) null else valeur
    }

    /** Estampe la donnée avec la clé de session résolue au moment de l'écriture. */
    fun écrire(clé: String, valeur: T) = synchronized(verrou) {
        this.clé = clé
        this.valeur = valeur
    }

    /** Efface la donnée ET la clé : après un vidage, plus rien n'est servi. */
    fun vider() = synchronized(verrou) {
        clé = null
        valeur = null
    }
}
