package school.greenwood.plus.data.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import school.greenwood.plus.data.api.MediaUrls
import school.greenwood.plus.model.Absence
import school.greenwood.plus.model.ArticleCommande
import school.greenwood.plus.model.CantineJour
import school.greenwood.plus.model.CommandeBoutique
import school.greenwood.plus.model.PrérempliCommande
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Créneau
import school.greenwood.plus.model.Demande
import school.greenwood.plus.model.DemandeReponse
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.DevoirDétail
import school.greenwood.plus.model.SoumissionDétail
import school.greenwood.plus.model.FicheBibliotheque
import school.greenwood.plus.model.FicheBibliothequeDetail
import school.greenwood.plus.model.Eleve
import school.greenwood.plus.model.JournéeCours
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.ParentInfo
import school.greenwood.plus.model.Commentaire
import school.greenwood.plus.model.Post
import school.greenwood.plus.model.PostDetail
import school.greenwood.plus.model.ProduitBoutique
import school.greenwood.plus.model.ProduitDétail
import school.greenwood.plus.model.QuestionPost
import school.greenwood.plus.model.QuizDetail
import school.greenwood.plus.model.QuizQuestion
import school.greenwood.plus.model.QuizReponse
import school.greenwood.plus.model.QuizRésultat
import school.greenwood.plus.model.Ressource
import school.greenwood.plus.model.RubriqueBoutique
import school.greenwood.plus.model.UniteBibliotheque
import school.greenwood.plus.model.SemaineCours
import school.greenwood.plus.model.VarianteBoutique
import school.greenwood.plus.model.ThemeMessage
import school.greenwood.plus.util.extractDate
import school.greenwood.plus.util.extractDateTime
import school.greenwood.plus.util.extractHeure
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Normalisation des réponses brutes → modèles du domaine. Le serveur mélange
 * chaînes d'affichage, champs manquants et structures variables ; chaque
 * normaliseur tolère tout ça et rend un modèle propre ou null.
 */

object Normalizers {

    fun str(obj: JsonObject, key: String): String? =
        (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun int(obj: JsonObject, key: String): Int? =
        (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toIntOrNull()

    fun bool(obj: JsonObject, key: String): Boolean? {
        val prim = obj[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return prim.booleanOrNull ?: when (prim.contentOrNull) {
            "1", "true", "True" -> true
            "0", "false", "False" -> false
            else -> null
        }
    }

    fun arr(obj: JsonObject, key: String): JsonArray =
        (obj[key] as? JsonArray) ?: JsonArray(emptyList())

    fun obj(item: JsonElementAlias): JsonObject? = item as? JsonObject

    // — Devoirs ------------------------------------------------------------

    /**
     * Devoir des listes parsées (`devoirs_remettre` / `devoirs_ancien`) :
     * `date_remise` ISO = échéance, `publication` en chaîne d'affichage.
     * La date de publication exacte vient des items bruts du bucket `data`
     * (champ `date` ISO) — fusionnée par le dépôt via `publicationBrute`.
     */
    fun devoir(raw: JsonObject, publicationBrute: LocalDateTime? = null): Devoir? {
        val id = str(raw, "id") ?: return null
        val pieces = buildList {
            (raw["file"] as? JsonObject)?.let { addAll(MediaUrls.piècesJointes(str(it, "link"), str(it, "text"))) }
            arr(raw, "files").forEach { f ->
                (f as? JsonObject)?.let { addAll(MediaUrls.piècesJointes(str(it, "link"), str(it, "filename") ?: str(it, "text"))) }
            }
        }.map { Attachment(it.first, it.second) }
        val fait = (raw["devoir_fait"] as? JsonObject)
        return Devoir(
            id = id,
            title = str(raw, "title") ?: "",
            matiere = str(raw, "matiere") ?: str(raw, "categorie") ?: "",
            categorie = str(raw, "categorie"),
            enseignant = str(raw, "enseignant"),
            description = str(raw, "description"),
            dateRemise = extractDate(str(raw, "date_remise")),
            publication = publicationBrute ?: extractDateTime(str(raw, "publication")),
            fait = bool(fait ?: JsonObject(emptyMap()), "fait") ?: false,
            filesSent = bool(fait ?: JsonObject(emptyMap()), "file_sent") ?: false,
            attachments = pieces,
        )
    }

    /** Payload détail (issue #68, sonde devoirs_single_*) : le même devoir que
     *  la liste, enrichi des droits et de l'état de soumission. Les pièces
     *  jointes vivent dans `files[]` (lien direct + `filename`). */
    fun devoirDétail(rep: JsonObject): DevoirDétail {
        val devoir = devoir(rep) ?: Devoir(
            id = str(rep, "id") ?: "",
            title = str(rep, "title") ?: "",
            matiere = str(rep, "matiere") ?: "",
        )
        val état = (rep["devoir_fait"] as? JsonObject)
        return DevoirDétail(
            devoir = devoir,
            peutMarquerFait = bool(rep, "can_set_done") ?: false,
            peutAjouterFichiers = bool(rep, "can_add_files") ?: false,
            montrerFichiers = bool(rep, "show_files") ?: false,
            copiesEnvoyées = arr(état ?: JsonObject(emptyMap()), "files").mapNotNull { f ->
                (f as? JsonObject)?.let { o ->
                    val url = MediaUrls.lienRéel(str(o, "link")) ?: return@mapNotNull null
                    Attachment(
                        name = str(o, "name") ?: str(o, "filename") ?: "copie",
                        url = url,
                    )
                }
            },
        )
    }

    /** Réponse du POST `devoirs_date_v2` (bundle 3537.js / 154.js) :
     *  `file_sent` true → copies parties, `files` servies en retour ;
     *  sinon le devoir est simplement marqué fait. Dans les deux cas le
     *  devoir est terminé (l'UI ne soumet que pour clore un devoir). */
    fun soumission(rep: JsonObject): SoumissionDétail {
        val envoyées = bool(rep, "file_sent") ?: false
        return SoumissionDétail(
            envoyées = envoyées,
            fait = bool(rep, "fait") ?: true,
            copies = arr(rep, "files").mapNotNull { f ->
                (f as? JsonObject)?.let { o ->
                    val url = MediaUrls.lienRéel(str(o, "link")) ?: return@mapNotNull null
                    Attachment(
                        name = str(o, "name") ?: str(o, "filename") ?: "copie",
                        url = url,
                    )
                }
            },
            titre = str(rep, "title"),
            message = str(rep, "message"),
        )
    }

    // — Actualités ---------------------------------------------------------

    fun post(raw: JsonObject): Post? {
        val id = str(raw, "id") ?: return null
        val pieces = buildList {
            when (val f = raw["file"]) {
                is JsonObject -> addAll(MediaUrls.piècesJointes(str(f, "link"), str(f, "text")))
                is kotlinx.serialization.json.JsonPrimitive -> addAll(MediaUrls.piècesJointes(f.contentOrNull))
                else -> {}
            }
            arr(raw, "files").forEach { f ->
                when (f) {
                    is JsonObject -> addAll(MediaUrls.piècesJointes(str(f, "link"), str(f, "filename") ?: str(f, "text")))
                    is kotlinx.serialization.json.JsonPrimitive -> addAll(MediaUrls.piècesJointes(f.contentOrNull))
                    else -> {}
                }
            }
        }.map { Attachment(it.first, it.second) }

        val bookmark = str(raw, "bookmark") == "bookmark"
        val auteur = (raw["user"] as? JsonObject)?.let { str(it, "nom") ?: str(it, "nomcomplet") }
            ?: str(raw, "user_nom")
            ?: str(raw, "user")
        val permitComments = bool(raw, "permitComments") ?: bool(raw, "permit_comments") ?: false
        val permitNewComments = bool(raw, "permitNewComments") ?: bool(raw, "permit_new_comments") ?: false
        val permitQuiz = bool(raw, "permitQuiz") ?: bool(raw, "permit_quiz") ?: false

        return Post(
            id = id,
            title = str(raw, "title") ?: "",
            categorie = str(raw, "categorie"),
            date = extractDateTime(str(raw, "date")),
            intro = str(raw, "intro"),
            description = str(raw, "description"),
            image = MediaUrls.lienRéel(str(raw, "image")),
            attachments = pieces,
            bookmark = bookmark,
            auteur = auteur,
            permitComments = permitComments,
            permitNewComments = permitNewComments,
            permitQuiz = permitQuiz,
        )
    }

    fun commentaire(raw: JsonObject): Commentaire? {
        val auteur = str(raw, "nom") ?: str(raw, "auteur") ?: ""
        val texte = str(raw, "commentaire") ?: str(raw, "comment") ?: str(raw, "texte") ?: ""
        if (auteur.isBlank() && texte.isBlank()) return null
        val date = extractDateTime(str(raw, "date"))
        val image = MediaUrls.lienRéel(str(raw, "img") ?: str(raw, "image"))
        val sousCommentaires = buildList {
            when (val sc = raw["sousComment"] ?: raw["sousCommentaires"]) {
                is JsonArray -> addAll(sc.mapNotNull { (it as? JsonObject)?.let(::commentaire) })
                is JsonObject -> addAll(arr(sc, "comments").mapNotNull { (it as? JsonObject)?.let(::commentaire) })
                else -> {}
            }
        }
        return Commentaire(
            auteur = auteur,
            texte = texte,
            date = date,
            image = image,
            sousCommentaires = sousCommentaires,
        )
    }

    fun questionPost(raw: JsonObject): QuestionPost? {
        val label = str(raw, "label") ?: str(raw, "question") ?: return null
        val alias = str(raw, "alias")
        val réponses = arr(raw, "reponses").mapNotNull { r ->
            when (r) {
                is kotlinx.serialization.json.JsonPrimitive -> r.contentOrNull
                is JsonObject -> str(r, "reponse") ?: str(r, "label")
                else -> null
            }
        }
        val réponseChoisie = str(raw, "res") ?: str(raw, "reponseChoisie")
        return QuestionPost(
            alias = alias,
            label = label,
            réponses = réponses,
            réponseChoisie = réponseChoisie,
        )
    }

    fun postDetail(raw: JsonObject): PostDetail? {
        val data = (raw["data"] as? JsonObject) ?: raw
        val postObj = (data["post"] as? JsonObject) ?: data
        val id = str(postObj, "id") ?: str(data, "id") ?: return null

        val pieces = buildList {
            when (val f = postObj["file"] ?: data["file"]) {
                is JsonObject -> addAll(MediaUrls.piècesJointes(str(f, "link"), str(f, "text")))
                is kotlinx.serialization.json.JsonPrimitive -> addAll(MediaUrls.piècesJointes(f.contentOrNull))
                else -> {}
            }
            arr(postObj, "files").ifEmpty { arr(data, "files") }.forEach { f ->
                when (f) {
                    is JsonObject -> addAll(MediaUrls.piècesJointes(str(f, "link"), str(f, "filename") ?: str(f, "text")))
                    is kotlinx.serialization.json.JsonPrimitive -> addAll(MediaUrls.piècesJointes(f.contentOrNull))
                    else -> {}
                }
            }
        }.map { Attachment(it.first, it.second) }

        val images = arr(data, "images").ifEmpty { arr(postObj, "images") }.mapNotNull { im ->
            when (im) {
                is kotlinx.serialization.json.JsonPrimitive -> im.contentOrNull?.let(MediaUrls::lienRéel)
                is JsonObject -> str(im, "image")?.let(MediaUrls::lienRéel) ?: str(im, "url")?.let(MediaUrls::lienRéel)
                else -> null
            }
        }

        val commentaires = arr(data, "comments").ifEmpty { arr(data, "commentaires") }.ifEmpty { arr(postObj, "comments") }.ifEmpty { arr(postObj, "commentaires") }.mapNotNull { c ->
            (c as? JsonObject)?.let(::commentaire)
        }

        val questionsRaw = arr(raw, "quiz").ifEmpty { arr(data, "quiz") }.ifEmpty { arr(postObj, "quiz") }
        val questions = questionsRaw.mapNotNull { q ->
            (q as? JsonObject)?.let(::questionPost)
        }

        val bookmark = str(postObj, "bookmark") == "bookmark"
        val auteur = (postObj["user"] as? JsonObject)?.let { str(it, "nom") ?: str(it, "nomcomplet") }
            ?: str(postObj, "user_nom")
            ?: str(data, "user_nom")
            ?: str(postObj, "user")
        val peutCommenter = bool(postObj, "permitComments") ?: bool(postObj, "permit_comments") ?: bool(data, "permitComments") ?: bool(data, "permit_comments") ?: false
        val peutNouveauCommentaire = bool(postObj, "permitNewComments") ?: bool(postObj, "permit_new_comments") ?: bool(data, "permitNewComments") ?: bool(data, "permit_new_comments") ?: false
        val peutRépondre = bool(data, "canSendComment") ?: bool(data, "can_send_comment") ?: bool(postObj, "canSendComment") ?: bool(postObj, "can_send_comment") ?: false
        val peutQuiz = bool(postObj, "permitQuiz") ?: bool(postObj, "permit_quiz") ?: bool(data, "permitQuiz") ?: bool(data, "permit_quiz") ?: false

        return PostDetail(
            id = id,
            title = str(postObj, "title") ?: str(data, "title") ?: "",
            categorie = str(postObj, "cat_name") ?: str(postObj, "categorie") ?: str(data, "categorie"),
            date = extractDateTime(str(postObj, "date") ?: str(data, "date")),
            intro = str(postObj, "intro") ?: str(data, "intro"),
            descriptionHtml = str(postObj, "desc") ?: str(postObj, "description") ?: str(data, "desc") ?: str(data, "description"),
            image = MediaUrls.lienRéel(str(postObj, "image") ?: str(data, "image")),
            bookmark = bookmark,
            auteur = auteur,
            files = pieces,
            images = images,
            commentaires = commentaires,
            peutCommenter = peutCommenter,
            peutNouveauCommentaire = peutNouveauCommentaire,
            peutRépondre = peutRépondre,
            peutQuiz = peutQuiz,
            questions = questions,
        )
    }

    /**
     * Fusionne deux pages de posts d'actualité.
     * Si départ == 0, remplace la liste (pull-to-refresh ou première page).
     * Sinon, ajoute les nouveaux éléments sans doublon d'identifiant.
     */
    fun fusionner(anciens: List<Post>, nouveaux: List<Post>, départ: Int): List<Post> {
        if (départ == 0) return nouveaux
        val idsExistants = anciens.map { it.id }.toSet()
        val aAjouter = nouveaux.filter { it.id !in idsExistants }
        return anciens + aAjouter
    }

    /**
     * Sélectionne l'actualité la plus récente par date (utilisée pour la carte
     * « Dernière actualité » du registre).
     */
    fun dernière(posts: List<Post>): Post? =
        posts.maxByOrNull { it.date ?: LocalDateTime.MIN }

    // — Emploi du temps ------------------------------------------------------

    /**
     * Semaine d'emploi du temps (GET `cours_v2`). La forme de tête est vérifiée
     * (2026-09-20, ENDPOINT-MAP) ; la forme des créneaux intérieurs NE l'est PAS
     * (sondage : `seances[]` vide — « do not rely »). Extraction défensive sur
     * des noms de champs plausibles ; une réponse méconnaissable rend null.
     */
    fun semaineCours(rep: JsonObject): SemaineCours? {
        val data = (rep["data"] as? JsonObject) ?: rep
        if (data.isEmpty() || arr(data, "seances").isEmpty()) return null

        val translation = (data["translation"] as? JsonObject) ?: JsonObject(emptyMap())
        val restricted = data["restricted"] as? JsonObject

        // Le libellé porte l'ISO du lundi (« Du  2026/09/14 … ») ; repli :
        // la veille de la semaine renvoyée est last_week + 7 jours.
        val lundi = extractDate(str(data, "label"))
            ?: str(data, "last_week")?.let(::extractDate)?.plusDays(7)

        val jours = arr(data, "seances").mapIndexed { position, élément ->
            (élément as? JsonObject)?.let { journéeCours(it, lundi, position + 1) }
        }.filterNotNull().sortedBy { it.jour }

        return SemaineCours(
            label = str(data, "label"),
            lundi = lundi,
            jourSélectionné = int(data, "selected_day"),
            journées = jours,
            semaineSuivante = str(data, "next_week")?.let(::extractDate),
            semainePrécédente = str(data, "last_week")?.let(::extractDate),
            aucunCours = str(translation, "aucun_cours"),
            restreint = restricted?.let { bool(it, "restricted") } == true,
            // HTML brut — aplati à l'écran (le normaliseur reste JVM-testable).
            messageRestriction = str(restricted ?: JsonObject(emptyMap()), "label")
                ?: str(restricted ?: JsonObject(emptyMap()), "contact"),
        )
    }

    /** Un jour : `day` 1-based (lundi = 1), libellé « L », date dérivée du lundi. */
    private fun journéeCours(raw: JsonObject, lundi: LocalDate?, position: Int): JournéeCours? {
        val jour = int(raw, "day") ?: position
        val créneaux = arr(raw, "seances").mapNotNull { s ->
            when (s) {
                is JsonObject -> créneau(s)
                is kotlinx.serialization.json.JsonPrimitive ->
                    s.contentOrNull?.takeIf { it.isNotBlank() }?.let { Créneau(matière = it) }
                else -> null
            }
        }
        return JournéeCours(
            jour = jour,
            label = str(raw, "label"),
            date = lundi?.plusDays((jour - 1).coerceIn(0, 6).toLong()),
            créneaux = créneaux,
        )
    }

    /**
     * Créneau intérieur — forme INCONNUE (ENDPOINT-MAP « do not rely ») :
     * on tente des noms de champs plausibles, matière/heure/salle/prof ; à
     * défaut le premier champ texte disponible devient le libellé affiché.
     */
    private fun créneau(raw: JsonObject): Créneau? {
        val matière = str(raw, "matiere") ?: str(raw, "matière") ?: str(raw, "title")
            ?: str(raw, "label") ?: str(raw, "name") ?: str(raw, "cours")
        val début = extractHeure(
            str(raw, "heure_debut") ?: str(raw, "heureDebut") ?: str(raw, "hdebut")
                ?: str(raw, "start") ?: str(raw, "debut"),
        )
        val fin = extractHeure(
            str(raw, "heure_fin") ?: str(raw, "heureFin") ?: str(raw, "hfin")
                ?: str(raw, "end") ?: str(raw, "fin"),
        )
        val salle = str(raw, "salle") ?: str(raw, "room") ?: str(raw, "classroom")
        val enseignant = str(raw, "prof") ?: str(raw, "professeur") ?: str(raw, "enseignant")
            ?: str(raw, "nom") ?: (raw["user"] as? JsonObject)?.let { str(it, "nom") ?: str(it, "nomcomplet") }

        if (matière == null && début == null && fin == null && salle == null && enseignant == null) {
            // Forme totalement méconnaissable : d'abord un vrai texte (jamais
            // un identifiant numérique), à défaut n'importe quelle valeur.
            val primitives = raw.values.filterIsInstance<kotlinx.serialization.json.JsonPrimitive>()
            val texte = primitives.firstOrNull { it.isString && !it.contentOrNull.isNullOrBlank() }?.contentOrNull
                ?: primitives.mapNotNull { it.contentOrNull }.firstOrNull { it.isNotBlank() }
                ?: return null
            return Créneau(matière = texte)
        }
        return Créneau(matière = matière, début = début, fin = fin, salle = salle, enseignant = enseignant)
    }

    // — Messages -----------------------------------------------------------

    fun conversation(raw: JsonObject): Conversation? {
        val id = str(raw, "id") ?: return null
        val messages = sansDoublonsConsécutifs(
            arr(raw, "conversation").mapNotNull { m ->
                (m as? JsonObject)?.let { message(it) }
            },
        )
        return Conversation(
            id = id,
            sujet = str(raw, "sujet") ?: str(raw, "to") ?: "",
            messages = messages,
            theme = str(raw, "theme"),
        )
    }

    /** Catégories du composeur (serveur `themes[]`, ids 8/9/10/11/13). */
    fun themes(rep: JsonObject): List<ThemeMessage> =
        arr(rep, "themes").mapNotNull { t ->
            (t as? JsonObject)?.let {
                ThemeMessage(
                    id = str(it, "id") ?: return@mapNotNull null,
                    label = str(it, "label") ?: "",
                    description = str(it, "description"),
                )
            }
        }

    fun message(raw: JsonObject): Message? {
        val texte = str(raw, "message") ?: return null
        val pieces = arr(raw, "files").mapNotNull { f ->
            (f as? JsonObject)?.let { o ->
                MediaUrls.piècesJointes(str(o, "link"), str(o, "filename") ?: str(o, "text"))
                    .firstOrNull()
                    ?.let { Attachment(it.first, it.second) }
            }
        }
        return Message(
            id = str(raw, "message_id") ?: str(raw, "id") ?: "",
            deLAdmin = bool(raw, "is_self") != true,
            texte = texte,
            date = extractDateTime(str(raw, "datetime")),
            vuLe = extractDateTime(str(raw, "vu_le")),
            attachments = pieces,
            audio = audio(raw),
        )
    }

    /**
     * Le serveur renvoie parfois le même message deux fois (double envoi
     * observé à une seconde d'écart, `message_id` distincts — ENDPOINT-MAP
     * quirk 7). On retire le doublon consécutif : même texte, même direction,
     * datés à moins de deux secondes d'écart. Jamais de dédoublonnage par
     * `message_id` — un vrai rappel du parent à quelques minutes d'écart est
     * légitime et doit rester visible.
     */
    fun sansDoublonsConsécutifs(messages: List<Message>): List<Message> =
        buildList {
            messages.forEach { m ->
                val précédent = lastOrNull()
                val doublon = précédent != null &&
                    précédent.texte == m.texte &&
                    précédent.deLAdmin == m.deLAdmin &&
                    précédent.date != null && m.date != null &&
                    kotlin.math.abs(java.time.Duration.between(précédent.date, m.date).seconds) < 2
                if (!doublon) add(m)
            }
        }

    /** Message vocal : lien direct (chaîne) ou objet `{link, …}` — forme non
     *  observée en production (toujours null dans le sondage), tolérant. */
    private fun audio(raw: JsonObject): Attachment? {
        val brut = raw["audio"] ?: return null
        return when (brut) {
            is JsonObject ->
                MediaUrls.piècesJointes(str(brut, "link"), str(brut, "filename") ?: str(brut, "text") ?: "Message vocal")
                    .firstOrNull()
                    ?.let { Attachment(it.first, it.second) }
            else ->
                MediaUrls.lienRéel((brut as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull)
                    ?.let { url ->
                        val nom = url.substringBefore('?').substringAfterLast('/').ifBlank { "Message vocal" }
                        Attachment(name = nom, url = url)
                    }
        }
    }

    // — Demandes -----------------------------------------------------------

    fun demande(raw: JsonObject): Demande? {
        val id = str(raw, "id") ?: return null
        return Demande(
            id = id,
            titre = str(raw, "type") ?: "",
            statut = str(raw, "statut"),
            cree = str(raw, "cree"),
            dateCreation = parseCreationCourte(str(raw, "created_at")),
            dateAffichee = str(raw, "date"),
            file = MediaUrls.lienRéel(str(raw, "file")),
            reponses = arr(raw, "reponses").mapNotNull { r ->
                (r as? JsonObject)?.let {
                    DemandeReponse(label = str(it, "label"), reponse = str(it, "reponse") ?: "")
                }
            },
        )
    }

    /** « 07/09/26 13h59 » — format court de l'administration. */
    fun parseCreationCourte(brut: String?): LocalDateTime? {
        if (brut.isNullOrBlank()) return null
        val m = Regex("""(\d{1,2})/(\d{1,2})/(\d{2})\s+(\d{1,2})h(\d{2})""").find(brut) ?: return null
        val (j, mo, a, h, mi) = m.destructured
        return runCatching {
            LocalDateTime.of(2000 + a.toInt(), mo.toInt(), j.toInt(), h.toInt(), mi.toInt())
        }.getOrNull()
    }

    // — Absences -----------------------------------------------------------

    /** Item non observé en production (aucune absence sur le compte sondé) —
     *  normalisation défensive sur les noms de champs plausibles. */
    fun absence(raw: JsonObject): Absence? {
        val id = str(raw, "id") ?: return null
        val justifiee = bool(raw, "justifie")
            ?: (str(raw, "justifie")?.equals("1", true) == true)
        return Absence(
            id = id,
            motif = str(raw, "motif") ?: str(raw, "description"),
            du = extractDate(str(raw, "date_debut") ?: str(raw, "date")),
            au = extractDate(str(raw, "date_fin") ?: str(raw, "date")),
            justifiee = justifiee,
        )
    }

    // — Ressources (espace documents) ---------------------------------------

    fun ressource(raw: JsonObject): Ressource? {
        val id = str(raw, "id") ?: return null
        return Ressource(
            id = id,
            matiere = str(raw, "matiere") ?: "",
            label = str(raw, "label") ?: "",
            presentation = str(raw, "presentation"),
            type = str(raw, "type"),
            couleur = str(raw, "color"),
            icone = str(raw, "icon")?.takeIf { it.startsWith("http") },
        )
    }

    // — Bibliothèque ---------------------------------------------------------

    /** Une matière (unité) de la Bibliothèque (GET `bibliotheque`, sonde du
     *  22/09/2026). `count_resources` arrive en chaîne, `has_new` en booléen
     *  ou en chaîne — bool() couvre les deux. */
    fun uniteBibliotheque(raw: JsonObject): UniteBibliotheque? {
        val id = str(raw, "id") ?: return null
        return UniteBibliotheque(
            id = id,
            label = str(raw, "label") ?: "",
            image = MediaUrls.lienRéel(str(raw, "icon"))?.takeIf { it.startsWith("http") },
            couleur = str(raw, "color"),
            nombreRessources = int(raw, "count_resources")
                ?: str(raw, "count_resources")?.toIntOrNull(),
            aDuNeuf = bool(raw, "has_new") == true,
        )
    }

    /** Une fiche de la Bibliothèque (GET `bibliotheque?unite=<id>`). La fiche
     *  ne porte pas sa matière — elle est portée par l'unité et passée ici. */
    fun ficheBibliotheque(raw: JsonObject, uniteId: String, matiere: String): FicheBibliotheque? {
        val id = str(raw, "id") ?: return null
        return FicheBibliotheque(
            id = id,
            uniteId = uniteId,
            matiere = matiere,
            titre = str(raw, "title") ?: "",
            categorie = str(raw, "categorie"),
            date = str(raw, "date"),
            par = str(raw, "by"),
            couleur = str(raw, "color"),
            image = MediaUrls.lienRéel(str(raw, "image"))?.takeIf { it.startsWith("http") },
        )
    }

    /** Détail d'une fiche (GET `ressource_details?ressource=<id>` — le param
     *  est `ressource`, pas `resource`) : les fichiers y portent enfin leur
     *  URL média signée. Le `file.link` de la liste n'est qu'un nom de
     *  fichier — on ne garde que les liens http réellement téléchargeables. */
    fun détailBibliotheque(rep: JsonObject): FicheBibliothequeDetail? {
        val data = (rep["data"] as? JsonObject) ?: rep
        val id = str(data, "id") ?: return null
        return FicheBibliothequeDetail(
            id = id,
            matiere = str(data, "matiere"),
            description = str(data, "description"),
            fichiers = arr(data, "files").mapNotNull { f ->
                (f as? JsonObject)?.let(::fichierRessource)
            },
        )
    }

    fun fichierRessource(raw: JsonObject): Attachment? {
        val url = MediaUrls.lienRéel(str(raw, "link") ?: str(raw, "path")) ?: return null
        if (!url.startsWith("http")) return null
        return Attachment(
            name = str(raw, "filename") ?: str(raw, "name") ?: "document",
            url = url,
        )
    }

    // — Quiz ------------------------------------------------------------------

    /**
     * Détail d'un quiz (GET `quiz?quiz_id=…`) — forme vérifiée en sonde
     * lecture-seule le 19/09/2026 (docs/api/ENDPOINT-MAP.md). Tolérant :
     * champs manquants, `can_play` absent, questions sans réponses.
     */
    fun quiz(rep: JsonObject): QuizDetail? {
        val data = (rep["data"] as? JsonObject) ?: rep
        val id = str(data, "quiz_id") ?: str(data, "id") ?: return null
        val questions = arr(data, "questions").mapNotNull { q ->
            (q as? JsonObject)?.let { question(it) }
        }
        return QuizDetail(
            id = id,
            label = str(data, "label") ?: "",
            matiere = str(data, "matiere"),
            niveau = str(data, "niveau"),
            couleur = str(data, "color"),
            image = MediaUrls.lienRéel(str(data, "image"))?.takeIf { it.startsWith("http") },
            minutes = str(data, "minutes"),
            peutJouer = bool(data, "can_play") ?: true,
            peutRejouer = bool(data, "can_replay") ?: false,
            questions = questions,
        )
    }

    fun question(raw: JsonObject): QuizQuestion? {
        val texte = str(raw, "question") ?: return null
        return QuizQuestion(
            texte = texte,
            image = MediaUrls.lienRéel(str(raw, "image"))?.takeIf { it.startsWith("http") },
            tempsReponse = int(raw, "temps_reponse"),
            reponses = arr(raw, "reponses").mapNotNull { r ->
                (r as? JsonObject)?.let {
                    QuizReponse(
                        texte = str(it, "reponse") ?: return@mapNotNull null,
                        correcte = bool(it, "correct") == true,
                    )
                }
            },
        )
    }

    /** Score d'une tentative : plat ou sous `data` (le bundle lit
     *  `resultatScore.score` sans enveloppe). */
    fun quizRésultat(rep: JsonObject): QuizRésultat {
        val data = (rep["data"] as? JsonObject) ?: rep
        return QuizRésultat(
            score = str(data, "score") ?: str(rep, "score"),
            temps = str(data, "time") ?: str(rep, "time"),
            peutRejouer = bool(data, "can_replay") ?: bool(rep, "can_replay"),
        )
    }

    // — Comptes ------------------------------------------------------------

    fun eleve(raw: JsonObject): Eleve? {
        val id = str(raw, "id") ?: return null
        return Eleve(
            id = id,
            nomComplet = str(raw, "nomcomplet") ?: "",
            prenom = str(raw, "prenom"),
            nom = str(raw, "nom"),
            niveau = str(raw, "niveau"),
            image = MediaUrls.lienRéel(str(raw, "img")),
        )
    }

    fun parent(raw: JsonObject?): ParentInfo? {
        val o = raw ?: return null
        val id = str(o, "id") ?: return null
        return ParentInfo(
            id = id,
            nomComplet = str(o, "nomcomplet") ?: "",
            image = MediaUrls.lienRéel(str(o, "image")),
        )
    }

    /** Le serveur renvoie parfois des chaînes vides au lieu de null. */
    fun listePosts(obj: JsonObject): List<Post> =
        arr(obj, "data").mapNotNull { (it as? JsonObject)?.let(::post) }

    fun aujourdhuiOuRecent(date: LocalDateTime?, aujourdhui: LocalDate): Boolean =
        date != null && (date.toLocalDate() == aujourdhui || date.isAfter(LocalDateTime.now()))

    // — Boutique (GET/POST `shop`, sondé le 21/09/2026) ---------------------

    /** Rubrique du catalogue : l'id arrive en nombre (-1 = « Tout ») ou en
     *  chaîne, selon l'entrée — tolérant aux deux. */
    fun rubrique(raw: JsonObject): RubriqueBoutique? {
        val id = str(raw, "id") ?: int(raw, "id")?.toString() ?: return null
        val icône = raw["icon"] as? JsonObject
        return RubriqueBoutique(
            id = id,
            label = str(raw, "label") ?: "",
            icone = icône?.let { str(it, "link") },
            fond = icône?.let { str(it, "bg") },
            notif = icône?.let { int(it, "notif") } ?: 0,
        )
    }

    fun produitCatalogue(raw: JsonObject): ProduitBoutique? {
        val id = str(raw, "id") ?: return null
        return ProduitBoutique(
            id = id,
            label = str(raw, "label") ?: "",
            image = MediaUrls.lienRéel(str(raw, "image")),
            prix = str(raw, "price"),
        )
    }

    /** Un jour du planning cantine (sonde du 21/09/2026) : l'id est celui du
     *  produit à commander, l'état de réservation est prêt à afficher. */
    fun cantine(raw: JsonObject): CantineJour? {
        val id = str(raw, "id") ?: int(raw, "id")?.toString() ?: return null
        val jour = raw["date"] as? JsonObject
        val dispo = raw["availability"] as? JsonObject
        return CantineJour(
            id = id,
            jourLabel = jour?.let { str(it, "label") },
            jourDate = jour?.let { str(it, "date") },
            jourValeur = jour?.let { str(it, "value") },
            dispoLabel = dispo?.let { str(it, "label") },
            dispoCouleur = dispo?.let { str(it, "color") },
            image = MediaUrls.lienRéel(str(raw, "img")),
            label = str(raw, "label") ?: "",
            description = str(raw, "description"),
            prix = str(raw, "price"),
            actif = bool(raw, "active") ?: false,
            peutRéserver = bool(raw, "can_reserve") ?: false,
            déjàRéservé = bool(raw, "is_reserved") ?: false,
            réservéLibellé = str(raw, "reserved"),
        )
    }

    fun variante(raw: JsonObject): VarianteBoutique? {
        val id = str(raw, "id") ?: int(raw, "id")?.toString() ?: return null
        return VarianteBoutique(
            id = id,
            label = str(raw, "label") ?: str(raw, "value") ?: "",
            couleur = str(raw, "color"),
            montant = str(raw, "amount"),
            stock = int(raw, "qte"),
        )
    }

    fun produitDétail(rep: JsonObject): ProduitDétail? {
        val produit = obj(rep["product"] ?: return null) ?: return null
        val id = str(produit, "id") ?: return null
        return ProduitDétail(
            id = id,
            label = str(produit, "label") ?: "",
            image = MediaUrls.lienRéel(str(produit, "image")),
            description = str(produit, "desc"),
            prixRaw = str(produit, "price"),
            peutCommander = bool(produit, "can_add_to_cart") ?: true,
            variantes = arr(rep, "variants").mapNotNull { (it as? JsonObject)?.let(::variante) },
            prérempli = (rep["commande"] as? JsonObject)?.let { prérempli ->
                PrérempliCommande(
                    taille = str(prérempli, "size"),
                    quantité = int(prérempli, "qte") ?: 1,
                    commentaire = str(prérempli, "comment"),
                )
            },
        )
    }

    fun articleCommande(raw: JsonObject): ArticleCommande? {
        val id = str(raw, "id") ?: int(raw, "id")?.toString() ?: return null
        return ArticleCommande(
            id = id,
            produitId = str(raw, "product_id"),
            image = MediaUrls.lienRéel(str(raw, "image")),
            label = str(raw, "label") ?: "",
            taille = str(raw, "size"),
            quantité = str(raw, "quantity")?.filter { it.isDigit() }?.toIntOrNull(),
            prix = str(raw, "price"),
            modifiable = bool(raw, "can_edit") ?: false,
            supprimable = bool(raw, "can_delete") ?: false,
        )
    }

    fun commande(raw: JsonObject): CommandeBoutique? {
        val id = str(raw, "id") ?: int(raw, "id")?.toString() ?: return null
        val état = raw["state"] as? JsonObject
        return CommandeBoutique(
            id = id,
            date = str(raw, "date"),
            prix = str(raw, "price"),
            étatAlias = état?.let { str(it, "alias") },
            étatLabel = état?.let { str(it, "label") },
            articles = arr(raw, "articles").mapNotNull { (it as? JsonObject)?.let(::articleCommande) },
            supprimable = bool(raw, "can_delete") ?: false,
        )
    }
}

/** Alias court pour éviter un import bruyant. */
typealias JsonElementAlias = kotlinx.serialization.json.JsonElement
