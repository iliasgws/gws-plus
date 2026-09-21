package school.greenwood.plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import school.greenwood.plus.ui.AppNav
import school.greenwood.plus.ui.theme.GwsPlusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GwsPlusTheme {
                val container = (application as GwsApplication).container
                AppNav(container = container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as GwsApplication).container
        // Retour au premier plan : la durée d'absence est comparée au réglage
        // (lisible seulement en suspension) — au-delà, les écrans chargés
        // rafraîchissent en silence. Au démarrage à froid, aucun arrêt n'est
        // enregistré : la reprise ne déclenche rien, les init{} chargent.
        lifecycleScope.launch {
            container.veille.enregistrerReprise(container.session.actualisationRetour.first())
        }
    }

    override fun onStop() {
        super.onStop()
        (application as GwsApplication).container.veille.enregistrerArrêt()
    }
}
