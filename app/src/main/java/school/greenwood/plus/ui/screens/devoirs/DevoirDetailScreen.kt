package school.greenwood.plus.ui.screens.devoirs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import school.greenwood.plus.AppContainer
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Devoir
import school.greenwood.plus.model.DevoirDétail
import school.greenwood.plus.model.SoumissionDétail
import school.greenwood.plus.ui.components.GwsCard
import school.greenwood.plus.ui.components.Puce
import school.greenwood.plus.ui.components.SectionLabel
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.tabulaire
import school.greenwood.plus.util.Fichiers
import school.greenwood.plus.util.frenchShort
import school.greenwood.plus.util.htmlToPlainMultiline

/*
 * Détail d'un devoir (issue #60, corrigé par #68) : le devoir affiché tout de
 * suite vient de la liste/cache, puis le payload détail du serveur
 * (GET `devoirs&devoir=<id>`) enrichit l'écran — pièces jointes réelles,
 * état de soumission et droits (`can_set_done`/`can_add_files`).
 *
 * Le marquage « fait » est un POST `devoirs_date_v2` DÉFINITIF (aucun retour
 * en arrière) : une confirmation est exigée avant l'envoi. Une fois le devoir
 * fait, la section d'envoi permet de joindre une copie (base64 dans le champ
 * `devoir`, comme l'upload du bundle officiel).
 */

@Composable
fun DevoirDetailScreen(
    container: AppContainer,
    padding: PaddingValues,
    devoirId: String,
    retour: () -> Unit,
) {
    val vm: DevoirDetailViewModel = viewModel(key = "devoir_detail_$devoirId") {
        DevoirDetailViewModel(container, devoirId)
    }
    val devoir by vm.devoir.collectAsStateWithLifecycle()
    val détail by vm.détail.collectAsStateWithLifecycle()
    val soumissionEnCours by vm.soumissionEnCours.collectAsStateWithLifecycle()
    val copiesLocales by vm.copies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val téléchargements = remember { mutableStateMapOf<String, Boolean>() }
    val échecsLot = remember { mutableStateMapOf<String, Boolean>() }
    val batchEnCours = remember { mutableStateOf(false) }

    // Confirmations — le POST est définitif, rien ne se lance sans accord.
    var confirmerFait by remember { mutableStateOf(false) }

    // Sélecteur de copie (SAF) — la pièce est copiée dans l'espace d'attente.
    val sélecteurCopie = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) vm.choisirCopie(context, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RegistreTheme.colors.paper)
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = retour) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    tint = RegistreTheme.colors.ink,
                )
            }
        }

        val d = devoir
        if (d == null) {
            // Charge ou introuvable : même écran d'attente dans les deux cas,
            // le dépôt retombe sur le cache avant le réseau.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = RegistreTheme.colors.ink)
            }
        } else {
            val infos = détail
            val fait = infos?.devoir?.fait ?: d.fait
            val copiesEnvoyées = infos?.copiesEnvoyées ?: emptyList()
            val fichiersEnvoyés = (infos?.devoir?.filesSent ?: d.filesSent) || copiesEnvoyées.isNotEmpty()
            val peutMarquerFait = (infos?.peutMarquerFait ?: false) && !fait
            val peutAjouterFichiers = (infos?.peutAjouterFichiers ?: false) && fait

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GwsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (d.matiere.isNotBlank()) Puce(d.matiere)
                            d.categorie?.takeIf { it.isNotBlank() }?.let { Puce(it) }
                        }

                        Text(
                            text = d.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = RegistreTheme.colors.ink,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            d.dateRemise?.let {
                                Text(
                                    text = "Pour le ${it.frenchShort()}",
                                    style = MaterialTheme.typography.labelMedium.tabulaire(),
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                            d.enseignant?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RegistreTheme.colors.chalk,
                                )
                            }
                        }

                        // Le rouge ne marque que l'action requise (docs/product/DESIGN.md §2).
                        // Le marquage local « fait pour moi » (issue #82) éteint
                        // le rouge sans toucher au suivi officiel.
                        when {
                            fait -> Puce("Travail fait")
                            d.faitLocal -> Puce("Fait pour moi")
                            d.dateRemise != null && !d.dateRemise.isAfter(LocalDate.now()) ->
                                Puce("À faire", tintRed = true)
                        }

                        // Description HTML aplatie en paragraphes lisibles.
                        val texte = d.description?.takeIf { it.isNotBlank() }?.htmlToPlainMultiline()
                        if (!texte.isNullOrBlank()) {
                            Text(
                                text = texte,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RegistreTheme.colors.ink,
                            )
                        }

                        // Marquage « fait pour moi » (issue #82) — purement
                        // local : jamais envoyé à l'école, réversible d'un
                        // clic, d'où l'absence de confirmation. Orange quand
                        // le travail est fait pour soi mais pas encore pour
                        // l'école ; vert Greenwood quand l'école le sait aussi.
                        TextButton(
                            onClick = { vm.basculerFaitLocal() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (d.faitLocal) Icons.Rounded.CheckCircle
                                else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = when {
                                    d.faitLocal && fait ->
                                        RegistreTheme.colors.accents.getValue("registre").teinte
                                    d.faitLocal -> RegistreTheme.colors.signetVif
                                    else -> RegistreTheme.colors.chalk
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = if (d.faitLocal) "Retirer le « fait pour moi »"
                                else "Marquer fait pour moi (local)",
                            )
                        }
                        Text(
                            text = "Visible uniquement dans l'app — les professeurs " +
                                "et l'administration ne le voient pas.",
                            style = MaterialTheme.typography.labelSmall,
                            color = RegistreTheme.colors.chalk,
                        )

                        // Marquage « fait » — définitif, d'où la confirmation.
                        if (peutMarquerFait) {
                            Button(
                                onClick = { confirmerFait = true },
                                enabled = !soumissionEnCours,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RegistreTheme.colors.ink,
                                    contentColor = RegistreTheme.colors.page,
                                ),
                                shape = ControlShape,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Marquer comme fait")
                            }
                        }

                        // Copie déjà partie : l'état sert de preuve.
                        if (fait && fichiersEnvoyés) {
                            Puce("Copie envoyée")
                        }
                    }
                }

                // Section d'envoi (après le fait) — « Envoyez vos devoirs faits »
                // du bundle officiel : joindre une copie, puis envoyer.
                if (fait && peutAjouterFichiers) {
                    GwsCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SectionLabel(text = "Envoyez vos devoirs faits", pointAccent = true)
                            if (copiesLocales.isNotEmpty()) {
                                copiesLocales.forEach { copie ->
                                    LigneCopieLocale(copie) { vm.retirerCopie(copie) }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { sélecteurCopie.launch("*/*") },
                                    enabled = !soumissionEnCours,
                                    shape = ControlShape,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Joindre un fichier")
                                }
                                Button(
                                    onClick = { vm.envoyerCopies() },
                                    enabled = copiesLocales.isNotEmpty() && !soumissionEnCours,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RegistreTheme.colors.ink,
                                        contentColor = RegistreTheme.colors.page,
                                    ),
                                    shape = ControlShape,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (soumissionEnCours) "Envoi…" else "Envoyer")
                                }
                            }
                        }
                    }
                }

                // Pièces jointes réelles du détail (repli : celles de la liste).
                val pièces = (infos?.devoir?.attachments?.takeIf { it.isNotEmpty() } ?: d.attachments)
                if (pièces.isNotEmpty()) {
                    GwsCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SectionLabel(
                                text = "Pièces jointes (${pièces.size})",
                                pointAccent = true,
                            )

                            // Téléchargement groupé dès deux pièces jointes —
                            // enregistré dans Downloads/gws-plus (visible dans
                            // les Fichiers du téléphone), jamais ouvert un à un.
                            if (pièces.size > 1) {
                                val terminées = pièces.count { téléchargements[it.url] == false }
                                val échecs = pièces.count { téléchargements[it.url] == false && échecsLot[it.url] == true }
                                val toutFait = terminées == pièces.size
                                Button(
                                    onClick = {
                                        batchEnCours.value = true
                                        échecsLot.clear()
                                        vm.téléchargerTout(pièces, context, { pièce, enCours, uri ->
                                            téléchargements[pièce.url] = enCours
                                            if (!enCours && uri == null) échecsLot[pièce.url] = true
                                        }, {
                                            batchEnCours.value = false
                                        })
                                    },
                                    enabled = !batchEnCours.value && !toutFait,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RegistreTheme.colors.ink,
                                        contentColor = RegistreTheme.colors.page,
                                    ),
                                    shape = ControlShape,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = when {
                                            batchEnCours.value -> "Téléchargement… ($terminées/${pièces.size})"
                                            échecs > 0 -> "Terminé — ${pièces.size - échecs}/${pièces.size} ($échecs échec${if (échecs > 1) "s" else ""})"
                                            toutFait -> "Tout est téléchargé"
                                            else -> "Tout télécharger (${pièces.size})"
                                        },
                                    )
                                }
                            }

                            pièces.forEach { pièce ->
                                LignePièceJointeDétail(
                                    pièce = pièce,
                                    enCours = téléchargements[pièce.url] == true,
                                    onTélécharger = {
                                        téléchargements[pièce.url] = true
                                        vm.téléchargerPièce(pièce, context) { uri ->
                                            téléchargements[pièce.url] = false
                                            if (uri != null) {
                                                val intention = Fichiers.intentionOuvrirUri(context, uri)
                                                if (intention != null) context.startActivity(intention)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Copies envoyées au serveur, téléchargeables.
                if (copiesEnvoyées.isNotEmpty()) {
                    GwsCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SectionLabel(text = "Copies envoyées (${copiesEnvoyées.size})", pointAccent = true)
                            copiesEnvoyées.forEach { copie ->
                                LignePièceJointeDétail(
                                    pièce = copie,
                                    enCours = téléchargements[copie.url] == true,
                                    onTélécharger = {
                                        téléchargements[copie.url] = true
                                        vm.téléchargerPièce(copie, context) { uri ->
                                            téléchargements[copie.url] = false
                                            if (uri != null) {
                                                val intention = Fichiers.intentionOuvrirUri(context, uri)
                                                if (intention != null) context.startActivity(intention)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // Confirmation avant le POST définitif (issue #68 : impossible d'annuler).
    if (confirmerFait) {
        AlertDialog(
            onDismissRequest = { confirmerFait = false },
            title = {
                Text(
                    text = "Marquer ce devoir comme fait ?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "Cette action est définitive : il sera impossible de revenir en arrière.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmerFait = false
                        vm.marquerFait()
                    },
                    shape = ControlShape,
                    enabled = !soumissionEnCours,
                ) {
                    Text("Marquer comme fait")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmerFait = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    // Alerte de la soumission (succès du serveur, comme l'alerte officielle).
    vm.soumission.collectAsStateWithLifecycle().value?.let { résultat ->
        AlertDialog(
            onDismissRequest = { vm.acquis() },
            title = {
                Text(
                    text = résultat.titre ?: if (résultat.fait) "Travail fait" else "Soumission",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = résultat.message
                        ?: if (résultat.envoyées) "Copies envoyées à l'école." else "Le devoir est marqué comme fait.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.acquis() },
                    shape = ControlShape,
                ) {
                    Text("Fermer")
                }
            },
        )
    }
}

@Composable
private fun LignePièceJointeDétail(
    pièce: Attachment,
    enCours: Boolean,
    onTélécharger: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Attachment,
            contentDescription = null,
            tint = RegistreTheme.colors.chalk,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = pièce.name,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (enCours) {
            CircularProgressIndicator(
                color = RegistreTheme.colors.ink,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            IconButton(onClick = onTélécharger, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Télécharger ${pièce.name}",
                    tint = RegistreTheme.colors.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LigneCopieLocale(
    fichier: File,
    onRetirer: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Attachment,
            contentDescription = null,
            tint = RegistreTheme.colors.chalk,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = fichier.name,
            style = MaterialTheme.typography.bodySmall,
            color = RegistreTheme.colors.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetirer) {
            Text("Retirer")
        }
    }
}

class DevoirDetailViewModel(
    private val container: AppContainer,
    private val devoirId: String,
) : ViewModel() {

    private val _devoir = MutableStateFlow<Devoir?>(null)
    val devoir: StateFlow<Devoir?> = _devoir.asStateFlow()

    private val _détail = MutableStateFlow<DevoirDétail?>(null)
    val détail: StateFlow<DevoirDétail?> = _détail.asStateFlow()

    private val _soumission = MutableStateFlow<SoumissionDétail?>(null)
    val soumission: StateFlow<SoumissionDétail?> = _soumission.asStateFlow()

    private val _soumissionEnCours = MutableStateFlow(false)
    val soumissionEnCours: StateFlow<Boolean> = _soumissionEnCours.asStateFlow()

    /** Copies locales choisies, à envoyer avec la soumission. */
    private val _copies = MutableStateFlow<List<File>>(emptyList())
    val copies: StateFlow<List<File>> = _copies.asStateFlow()

    init {
        viewModelScope.launch {
            // Cache d'abord (lecture instantanée), réseau ensuite pour rafraîchir.
            val enCache = runCatching { container.devoirs.listeEnCache() }.getOrNull()
            if (enCache != null) {
                _devoir.value = enCache.firstOrNull { it.id == devoirId }
            }
            if (_devoir.value == null) {
                val tous = runCatching { container.devoirs.liste() }.getOrDefault(emptyList())
                _devoir.value = tous.firstOrNull { it.id == devoirId }
            }
            // Payload détail (issue #68) : pièces jointes réelles + droits +
            // état de soumission — la liste seule ne porte rien de tout cela.
            runCatching { container.devoirs.détail(devoirId) }.onSuccess { infos ->
                _détail.value = infos
                infos?.devoir?.takeIf { nouveau ->
                    nouveau.fait || nouveau.filesSent || nouveau.attachments.isNotEmpty()
                }?.let { enrichi ->
                    // Le détail serveur fait foi sur l'état ; on garde la liste
                    // pour ce qu'elle seule porte (publication, échéance ISO).
                    _devoir.update { courant ->
                        courant?.copy(
                            fait = enrichi.fait,
                            filesSent = enrichi.filesSent,
                            attachments = enrichi.attachments.ifEmpty { courant.attachments },
                        ) ?: enrichi
                    }
                }
            }
        }
    }

    /** Bascule « fait pour moi » (issue #82) — local uniquement, réversible,
     *  jamais envoyé au serveur ; l'écran suit aussitôt, comme le cache. */
    fun basculerFaitLocal() {
        viewModelScope.launch {
            runCatching { container.devoirs.basculerFaitLocal(devoirId) }.onSuccess { fait ->
                _devoir.update { it?.copy(faitLocal = fait) }
                _détail.update { infos ->
                    infos?.copy(devoir = infos.devoir.copy(faitLocal = fait))
                }
            }
        }
    }

    fun marquerFait() {
        viewModelScope.launch {
            _soumissionEnCours.value = true
            runCatching { container.devoirs.soumettre(devoirId, _copies.value) }
                .onSuccess { résultat ->
                    _soumission.value = résultat
                    val fait = résultat.fait
                    _devoir.update { it?.copy(fait = fait, filesSent = résultat.envoyées) }
                    _détail.update { infos ->
                        infos?.copy(
                            devoir = infos.devoir.copy(fait = fait, filesSent = résultat.envoyées),
                            copiesEnvoyées = if (résultat.envoyées) résultat.copies else infos.copiesEnvoyées,
                        )
                    }
                    // L'onglet Devoirs (cache + état) reflète le changement au retour.
                    runCatching {
                        container.devoirs.rafraîchirCache(devoirId) { it.copy(fait = fait, filesSent = résultat.envoyées) }
                    }
                    container.devoirsModifiés.tryEmit(devoirId)
                    _copies.value = emptyList()
                }
                .onFailure {
                    _soumission.value = SoumissionDétail(
                        envoyées = false,
                        fait = false,
                        titre = "Envoi impossible",
                        message = "La soumission a échoué. Vérifie la connexion et réessaie.",
                    )
                }
            _soumissionEnCours.value = false
        }
    }

    fun envoyerCopies() {
        marquerFait()
    }

    fun choisirCopie(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            Fichiers.copierDepuisSaf(context, uri)?.let { fichier ->
                _copies.update { it + fichier }
            }
        }
    }

    fun retirerCopie(fichier: File) {
        _copies.update { it - fichier }
    }

    fun acquis() {
        _soumission.value = null
    }

    fun téléchargerPièce(pièce: Attachment, context: android.content.Context, onFait: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            // Dossier public Downloads/gws-plus (issue #68) — visible dans
            // les Fichiers du téléphone, ouvert une fois enregistré.
            val uri = Fichiers.téléchargerPublic(context, pièce.url, pièce.name)
            onFait(uri)
        }
    }

    /** Télécharge les pièces une par une (séquentiel, sans ouvrir les fichiers). */
    fun téléchargerTout(
        pièces: List<Attachment>,
        context: android.content.Context,
        onÉtat: (Attachment, Boolean, android.net.Uri?) -> Unit,
        onFini: () -> Unit = {},
    ) {
        viewModelScope.launch {
            pièces.forEach { pièce ->
                onÉtat(pièce, true, null)
                val uri = runCatching {
                    Fichiers.téléchargerPublic(context, pièce.url, pièce.name)
                }.getOrNull()
                onÉtat(pièce, false, uri)
            }
            onFini()
        }
    }
}
