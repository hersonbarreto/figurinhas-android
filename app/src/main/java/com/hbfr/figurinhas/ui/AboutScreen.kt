package com.hbfr.figurinhas.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hbfr.figurinhas.BuildConfig
import com.hbfr.figurinhas.R
import com.hbfr.figurinhas.purchases.PurchaseManager
import kotlinx.coroutines.launch

private const val CONTACT = "contato@hbfr.com.br"

@Composable
fun AboutScreen(purchases: PurchaseManager, onBack: () -> Unit, onPro: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val isPro by purchases.isPro.collectAsState()
    var test by remember { mutableStateOf(purchases.isTestUnlocked) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Group {
                Item(stringResource(R.string.plan), if (isPro) stringResource(R.string.pro_badge) else stringResource(R.string.plan_free),
                    onClick = if (isPro) null else onPro)
                Item(stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                if (Build.VERSION.SDK_INT >= 33) {
                    Item(stringResource(R.string.language), null) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Group {
                Item(stringResource(R.string.contact), CONTACT) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT"))) }
                }
                Item(stringResource(R.string.help), null) { open("https://hbfr.com.br/figurinhas/suporte.html") }
                Item(stringResource(R.string.privacy), null) { open("https://hbfr.com.br/figurinhas/privacidade.html") }
            }
            Text(stringResource(R.string.contact_body), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            Spacer(Modifier.height(8.dp))
            Group {
                Item(stringResource(R.string.restore), null) {
                    scope.launch {
                        val ok = purchases.refresh()
                        onMessage(context.getString(if (!ok) R.string.pro_unavailable else if (purchases.isPro.value) R.string.pro_restored else R.string.pro_none))
                    }
                }
                Item(stringResource(R.string.redeem), null) { open("https://play.google.com/redeem") }
            }
            if (BuildConfig.TEST_UNLOCK) {
                Spacer(Modifier.height(16.dp))
                Group {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.test_unlock), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.test_unlock_body), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Switch(test, { test = it; purchases.setTestUnlock(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) { content() }
}

@Composable
private fun Item(title: String, value: String?, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null && value == null) cs.primary else cs.onSurface,
            modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
    }
    HorizontalDivider(color = cs.outlineVariant)
}
