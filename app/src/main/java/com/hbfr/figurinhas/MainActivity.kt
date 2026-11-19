package com.hbfr.figurinhas

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hbfr.figurinhas.data.Pack
import com.hbfr.figurinhas.data.WhatsAppSender
import com.hbfr.figurinhas.importer.EditorInput
import com.hbfr.figurinhas.importer.ImportException
import com.hbfr.figurinhas.importer.Importer
import com.hbfr.figurinhas.ui.EditorScreen
import com.hbfr.figurinhas.ui.HomeScreen
import com.hbfr.figurinhas.ui.theme.Background
import com.hbfr.figurinhas.ui.theme.FigurinhasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Intent de "Compartilhar" que ainda não foi tratado. */
    private var sharedIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedIntent = intent.takeIf { it.action == Intent.ACTION_SEND }
        Importer.cleanup(this)
        val store = (application as FigurinhasApp).store
        setContent {
            FigurinhasTheme {
                val state by store.state.collectAsStateWithLifecycle()
                var editing by remember { mutableStateOf<EditorInput?>(null) }
                var choosing by remember { mutableStateOf<Pack?>(null) }
                var linkDialog by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                var refreshKey by remember { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val noWhatsApp = stringResource(R.string.no_whatsapp)
                val pasteEmpty = stringResource(R.string.paste_empty)

                fun importError(e: Throwable): String = getString(when ((e as? ImportException)?.message) {
                    "bad_link" -> R.string.err_bad_link
                    "not_image" -> R.string.err_not_image
                    "post_no_media" -> R.string.err_post_no_media
                    "too_large" -> R.string.err_too_large
                    else -> R.string.err_download
                })
                fun runImport(block: suspend () -> EditorInput?, emptyMessage: String? = null) {
                    busy = true
                    scope.launch {
                        val r = runCatching { block() }
                        busy = false
                        r.onSuccess { input -> if (input != null) editing = input else emptyMessage?.let { snackbar.showSnackbar(it) } }
                            .onFailure { snackbar.showSnackbar(importError(it)) }
                    }
                }

                // Compartilhar de outro app.
                val pendingShare = sharedIntent
                androidx.compose.runtime.LaunchedEffect(pendingShare) {
                    val si = pendingShare ?: return@LaunchedEffect
                    sharedIntent = null
                    editing = null
                    val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) si.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        else @Suppress("DEPRECATION") si.getParcelableExtra(Intent.EXTRA_STREAM)
                    val link = Importer.findLink(si.getStringExtra(Intent.EXTRA_TEXT))
                    when {
                        stream != null -> runImport({ Importer.fromSharedStream(this@MainActivity, stream, si.type) })
                        link != null -> runImport({ Importer.fromLink(this@MainActivity, link) })
                    }
                }

                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) editing = Importer.fromPicker(this, uri)
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
                        val input = editing
                        if (input == null) {
                            Box(Modifier.weight(1f).navigationBarsPadding()) {
                                HomeScreen(state, store, refreshKey,
                                    onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                    onPaste = { runImport({ Importer.fromClipboard(this@MainActivity) }, pasteEmpty) },
                                    onLink = {
                                        val clip = getSystemService(android.content.ClipboardManager::class.java).primaryClip
                                        linkDialog = Importer.findLink(clip?.getItemAt(0)?.text?.toString()).orEmpty()
                                    },
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
                            EditorScreen(input, store,
                                onBack = { editing = null },
                                onSaved = { msg -> editing = null; scope.launch { snackbar.showSnackbar(msg) } },
                                onError = { msg -> editing = null; scope.launch { snackbar.showSnackbar(msg) } },
                                onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }

                if (busy) {
                    AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.downloading))
                        }
                    })
                }

                linkDialog?.let { initial ->
                    var text by remember { mutableStateOf(initial) }
                    AlertDialog(
                        onDismissRequest = { linkDialog = null },
                        title = { Text(stringResource(R.string.link_title)) },
                        text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(stringResource(R.string.link_hint)) }) },
                        confirmButton = {
                            TextButton(enabled = text.isNotBlank(), onClick = {
                                linkDialog = null
                                val t = text
                                runImport({ Importer.fromLink(this@MainActivity, Importer.findLink(t) ?: t) })
                            }) { Text(stringResource(R.string.open)) }
                        },
                        dismissButton = { TextButton(onClick = { linkDialog = null }) { Text(stringResource(R.string.cancel)) } },
                    )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND) sharedIntent = intent
    }
}
