package app.webora.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.siteskin.core.SiteSkinSchema

/**
 * Placeholder shell. The real browser surface arrives with BROWSE-002/003.
 * Present so the module assembles and the D8 / jvmTarget combination is verifiable
 * from the first commit.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ enforces edge-to-edge; opting in here rather than at PLAY-001
        // avoids retrofitting insets handling into a finished UI.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BootstrapScreen()
            }
        }
    }
}

@Composable
private fun BootstrapScreen() {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Webora Browser", style = MaterialTheme.typography.headlineMedium)
            Text(
                "One browser. A native-like experience for every website.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "SiteSkin schema ${SiteSkinSchema.CURRENT}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
