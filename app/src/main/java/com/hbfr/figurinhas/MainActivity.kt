package com.hbfr.figurinhas

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hbfr.figurinhas.data.Pack
import com.hbfr.figurinhas.data.WhatsAppSender
import com.hbfr.figurinhas.ui.theme.Background
import com.hbfr.figurinhas.ui.EditorScreen
import com.hbfr.figurinhas.ui.theme.FigurinhasTheme
import com.hbfr.figurinhas.ui.HomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as FigurinhasApp).store
        setContent {
            FigurinhasTheme {
                val state by store.state.collectAsStateWithLifecycle()
                var editing by rememberSaveable { mutableStateOf<Uri?>(null) }
                var choosing by remember { mutableStateOf<Pack?>(null) }
                var refreshKey by remember { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val noWhatsApp = stringResource(R.string.no_whatsapp)

                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) editing = uri
                }
                val whatsapp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                    refreshKey++
                    if (r.resultCode == Activity.RESULT_CANCELED) {
                        r.data?.getStringExtra("validation_error")?.let { err ->
                            scope.launch { snackbar.showSnackbar(getString(R.string.whatsapp_error, err)) }
                        }
                    }
                }
                fun send(pack: Pack, app: String) {
                    scope.launch {
                        val intent = withContext(Dispatchers.IO) { WhatsAppSender.prepare(this@MainActivity, store, pack, app) }
                        runCatching { whatsapp.launch(intent) }.onFailure { snackbar.showSnackbar(noWhatsApp) }
                    }
                }

                Box(Modifier.fillMaxSize().background(Background)) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        val uri = editing
                        if (uri == null) {
                            Box(Modifier.weight(1f).navigationBarsPadding()) {
                                HomeScreen(state, store, refreshKey,
                                    onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    onSend = { pack ->
                                        val apps = WhatsAppSender.installed(this@MainActivity)
                                        when (apps.size) {
                                            0 -> scope.launch { snackbar.showSnackbar(noWhatsApp) }
                                            1 -> send(pack, apps[0])
                                            else -> choosing = pack
                                        }
                                    })
                            }
                        } else {
                            BackHandler { editing = null }
                            EditorScreen(uri, store,
                                onBack = { editing = null },
                                onSaved = { msg -> editing = null; scope.launch { snackbar.showSnackbar(msg) } },
                                onError = { msg -> editing = null; scope.launch { snackbar.showSnackbar(msg) } })
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }

                choosing?.let { pack ->
                    AlertDialog(
                        onDismissRequest = { choosing = null },
                        title = { Text(stringResource(R.string.choose_whatsapp)) },
                        confirmButton = { TextButton(onClick = { choosing = null; send(pack, WhatsAppSender.CONSUMER) }) { Text(stringResource(R.string.whatsapp)) } },
                        dismissButton = { TextButton(onClick = { choosing = null; send(pack, WhatsAppSender.BUSINESS) }) { Text(stringResource(R.string.whatsapp_business)) } },
                    )
                }
            }
        }
    }
}
