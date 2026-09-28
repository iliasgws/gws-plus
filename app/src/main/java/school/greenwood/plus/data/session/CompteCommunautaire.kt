package school.greenwood.plus.data.session

/*
 * Contrat de stockage du compte communautaire (issue #88, APP.md §2 du dépôt
 * gws-community-server). Le jeton EST le compte : aucun nom, aucun mot de
 * passe, jamais logué ni affiché (docs/security/SECURITY-NOTES.md, F3). Il
 * vit dans les préférences de l'app — un seul compte par installation — et
 * survit à une purge de session.
 *
 * La notice légale suit deux versions : celle que le serveur sert (demandée)
 * et celle que l'utilisateur a acceptée (acceptée) ; tant qu'elles diffèrent,
 * la notice réaffiche avant la prochaine écriture.
 */
interface CompteCommunautaire {

    /** Jeton courant, null si aucun compte (à créer à la première écriture). */
    suspend fun jeton(): String?

    /** Nouveau jeton (POST /compte) : mémorise la version de notice servie et
     *  repart sans acceptation — un compte neuf doit revoir la notice. */
    suspend fun enregistrerJeton(jeton: String, mentionsVersion: String?)

    /** Dernière version de notice vue sur le serveur (GET /mentions ou POST /compte). */
    suspend fun mentionsDemandée(): String?

    /** Version de notice explicitement acceptée par l'utilisateur (null = jamais). */
    suspend fun mentionsAcceptée(): String?

    /** Le serveur sert une version de notice : à comparer à l'acceptée. */
    suspend fun noterVersionServie(version: String)

    /** L'utilisateur accepte la version [version] de la notice. */
    suspend fun accepterNotices(version: String)

    /** Oublier le jeton (révocation ou 401) : la prochaine écriture en recrée
     *  un, et la notice devra être revalidée. Les votes locaux suivent
     *  l'identité — ils sont oubliés avec elle. */
    suspend fun oublierJeton()

    /** Oublier le compte ENTIER (refus de la notice, révocation depuis les
     *  Paramètres) : jeton, notices et votes locaux. */
    suspend fun oublierTout()
}
