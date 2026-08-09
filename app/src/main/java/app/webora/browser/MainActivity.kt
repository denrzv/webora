package app.webora.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import app.webora.browser.browser.BrowserScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ enforces edge-to-edge; opting in here rather than at PLAY-001
        // avoids retrofitting insets handling into a finished UI.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BrowserScreen(
                    startUrl = START_URL,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        const val START_URL = "https://example.com"
    }
}
