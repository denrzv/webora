package app.webora.browser

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.webora.browser.browser.BrowserScreen
import app.webora.browser.browser.LaunchDestination
import app.webora.browser.browser.OnboardingScreen
import app.webora.browser.browser.OnboardingStore
import app.webora.browser.browser.launchDestination
import app.webora.browser.web.enqueueDownload
import app.webora.browser.web.launchExternal
import app.webora.browser.web.launchExternalUrl
import app.webora.browser.web.sharePage

class MainActivity : ComponentActivity() {
    private var uploadResult: ((String?) -> Unit)? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uploadResult?.invoke(uri?.toString())
        uploadResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ enforces edge-to-edge; opting in here rather than at PLAY-001
        // avoids retrofitting insets handling into a finished UI.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val store = remember { OnboardingStore(applicationContext) }
                var destination by remember {
                    mutableStateOf(launchDestination(store.isCompleted()))
                }
                when (destination) {
                    LaunchDestination.Onboarding -> OnboardingScreen(
                        onComplete = {
                            store.complete()
                            destination = LaunchDestination.Home
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    LaunchDestination.Home -> BrowserScreen(
                        modifier = Modifier.fillMaxSize(),
                        onLaunchExternal = { launchExternal(this, it) },
                        onDownload = { enqueueDownload(this, it) },
                        onOpenExternalUrl = { launchExternalUrl(this, it) },
                        onShare = { sharePage(this, it) },
                        onFileChooser = { mimeType, complete ->
                            uploadResult?.invoke(null)
                            uploadResult = complete
                            filePicker.launch(mimeType)
                        },
                    )
                }
            }
        }
    }
}
