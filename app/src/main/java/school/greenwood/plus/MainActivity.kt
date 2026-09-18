package school.greenwood.plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
}
