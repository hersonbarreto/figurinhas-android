package com.hbfr.figurinhas.purchases

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hbfr.figurinhas.R
import com.hbfr.figurinhas.ui.theme.GradientButton
import kotlinx.coroutines.launch

enum class PaywallReason(val title: Int) {
    General(R.string.pro_title_general), Animated(R.string.pro_title_animated), Background(R.string.pro_title_background),
    Limit(R.string.pro_title_limit), Packs(R.string.pro_title_packs),
}

@Composable
fun PaywallDialog(reason: PaywallReason, purchases: PurchaseManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val isPro by purchases.isPro.collectAsState()
    val price by purchases.price.collectAsState()
    val storeError by purchases.storeError.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { purchases.refresh() }
    LaunchedEffect(isPro) { if (isPro) onDismiss() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background, contentColor = cs.onBackground) {
            Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                Image(painterResource(R.drawable.pro_icon), null, Modifier.size(112.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                if (reason == PaywallReason.General) {
                    Text(buildAnnotatedString {
                        append("Figurinhas "); withStyle(SpanStyle(color = cs.primary)) { append("Pro") }
                    }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text(stringResource(reason.title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.pro_subtitle), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val items = listOf(R.string.pro_f1 to R.string.pro_f1d, R.string.pro_f2 to R.string.pro_f2d,
                        R.string.pro_f3 to R.string.pro_f3d, R.string.pro_f4 to R.string.pro_f4d)
                    items.forEachIndexed { i, (t, d) ->
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(stringResource(t), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(d), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        if (i < items.size - 1) HorizontalDivider(color = cs.outlineVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).border(BorderStroke(2.dp, cs.primary), MaterialTheme.shapes.large)
                    .background(cs.surfaceContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(price ?: "R$ 19,90", style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.pro_once), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.pro_no_sub), style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cs.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.height(20.dp))
                GradientButton(enabled = price != null && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    val act = context as? Activity ?: return@GradientButton
                    busy = true; message = null
                    purchases.purchase(act) { o ->
                        busy = false
                        message = when (o) {
                            PurchaseManager.Outcome.Pending -> context.getString(R.string.pro_pending)
                            PurchaseManager.Outcome.Failed -> context.getString(R.string.pro_failed)
                            else -> null
                        }
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_loading),
                        style = MaterialTheme.typography.labelLarge)
                }
                if (storeError && price == null) {
                    Text(stringResource(R.string.pro_unavailable), style = MaterialTheme.typography.bodySmall, color = cs.error,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true; message = null
                        scope.launch {
                            val ok = purchases.refresh()
                            busy = false
                            message = context.getString(if (!ok) R.string.pro_unavailable else if (purchases.isPro.value) R.string.pro_restored else R.string.pro_none)
                        }
                    }) { Text(stringResource(R.string.restore)) }
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem"))) }
                    }) { Text(stringResource(R.string.redeem)) }
                }
                Text(stringResource(R.string.pro_footer), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
            }
        }
    }
}
