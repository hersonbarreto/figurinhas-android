package com.hbfr.figurinhas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hbfr.figurinhas.R
import com.hbfr.figurinhas.data.Pack
import com.hbfr.figurinhas.data.StickerStore
import com.hbfr.figurinhas.data.StoreState
import com.hbfr.figurinhas.data.StoredSticker
import com.hbfr.figurinhas.data.WhatsAppLimits
import com.hbfr.figurinhas.data.WhatsAppSender
import com.hbfr.figurinhas.ui.theme.GradientButton
import com.hbfr.figurinhas.ui.theme.WhatsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val THUMB = 62.dp

@Composable
fun HomeScreen(
    state: StoreState,
    store: StickerStore,
    refreshKey: Int,
    onGallery: () -> Unit,
    onPaste: () -> Unit,
    onLink: () -> Unit,
    onSend: (Pack) -> Unit,
    isPro: Boolean,
    onPro: () -> Unit,
    onAbout: () -> Unit,
    onNewPack: () -> Unit,
) {
    var renaming by remember { mutableStateOf<Pack?>(null) }
    var viewing by remember { mutableStateOf<StoredSticker?>(null) }
    val packs = state.packList.filter { it.stickers.isNotEmpty() || state.packs.size > 1 }
    var deleting by remember { mutableStateOf<Pack?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onPro) {
                    Text((if (isPro) "★ " else "♛ ") + stringResource(R.string.pro_badge), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (isPro) R.string.new_pack else R.string.new_pack_pro)) },
                            onClick = { menu = false; onNewPack() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { menu = false; onAbout() })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_gallery), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.gallery), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                SourceButton(R.drawable.ic_paste, R.string.paste, onPaste, Modifier.weight(1f))
                SourceButton(R.drawable.ic_link, R.string.link, onLink, Modifier.weight(1f))
            }
        }
        if (packs.isEmpty()) item { EmptyCard() }
        items(packs, key = { it.id }) { pack ->
            PackCard(pack, store, refreshKey, onSend = { onSend(pack) }, onRename = { renaming = pack }, onTap = { viewing = it },
                onDelete = { deleting = pack })
        }
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            text = { Text(stringResource(R.string.delete_pack_q, p.stickers.size)) },
            confirmButton = { TextButton(onClick = {
                p.stickers.forEach { Thumbnails.evict(store.stickerFile(it)) }
                store.deletePack(p.id); deleting = null
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    renaming?.let { p ->
        RenameDialog(p, onDismiss = { renaming = null }) { name, author -> store.rename(p.id, name, author); renaming = null }
    }
    viewing?.let { s ->
        StickerDialog(s, store, onDismiss = { viewing = null }) {
            Thumbnails.evict(store.stickerFile(s)); store.delete(s); viewing = null
        }
    }
}

@Composable
private fun SourceButton(icon: Int, label: Int, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.height(52.dp).clip(MaterialTheme.shapes.medium).background(cs.surfaceContainerHigh).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = cs.primary)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun EmptyCard() {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp)) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackCard(pack: Pack, store: StickerStore, refreshKey: Int, onSend: () -> Unit, onRename: () -> Unit, onTap: (StoredSticker) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var added by remember { mutableStateOf(false) }
    LaunchedEffect(pack.id, pack.info.version, refreshKey) {
        added = withContext(Dispatchers.IO) {
            WhatsAppSender.installed(context).any { WhatsAppSender.isAdded(context, it, pack.id) }
        }
    }
    var expanded by rememberSaveable(pack.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.surfaceContainer).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onRename)) {
                Text(pack.info.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(pack.animated)
                    Spacer(Modifier.width(8.dp))
                    Text("${pack.stickers.size}/${WhatsAppLimits.MAX_STICKERS_PER_PACK}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (pack.stickers.size > 4) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.see_less else R.string.see_all), color = cs.primary)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more), tint = cs.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename_pack)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_pack), color = cs.error) }, onClick = { menu = false; onDelete() })
                }
            }
            SendButton(enabled = pack.missing == 0, added = added, onClick = onSend)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shown = if (expanded || pack.stickers.size <= 5) pack.stickers else pack.stickers.take(4)
            shown.forEach { s -> StickerThumb(s, store) { onTap(s) } }
            if (shown.size < pack.stickers.size) {
                Box(Modifier.size(THUMB).clip(MaterialTheme.shapes.small).background(cs.surfaceContainerHighest).clickable { expanded = true },
                    contentAlignment = Alignment.Center) {
                    Text("+${pack.stickers.size - shown.size}", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        if (pack.missing > 0) {
            Text(pluralStringResource(R.plurals.missing_whatsapp, pack.missing, pack.missing),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun KindBadge(animated: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg = if (animated) cs.primaryContainer else cs.surfaceContainerHighest
    val fg = if (animated) cs.onPrimaryContainer else cs.onSurfaceVariant
    Text((if (animated) "▶ " else "") + stringResource(if (animated) R.string.kind_animated else R.string.kind_static),
        style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun SendButton(enabled: Boolean, added: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (enabled) WhatsApp else cs.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (added) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
            stringResource(if (added) R.string.update_whatsapp else R.string.send_pack),
            tint = if (enabled) Color.White else cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StickerThumb(s: StoredSticker, store: StickerStore, onClick: () -> Unit) {
    Box(Modifier.size(THUMB).clip(MaterialTheme.shapes.small).checker().clickable(onClick = onClick)) {
        if (s.animated) {
            val px = with(LocalDensity.current) { THUMB.roundToPx() }
            AnimatedSticker(store.stickerFile(s), px, Modifier.fillMaxSize().padding(3.dp))
        } else {
            val img by rememberThumbnail(store.stickerFile(s), sample = 2)
            img?.let { Image(it, null, Modifier.fillMaxSize().padding(3.dp)) }
        }
    }
}

@Composable
private fun StickerDialog(s: StoredSticker, store: StickerStore, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val img = if (s.animated) null else rememberThumbnail(store.stickerFile(s), sample = 1).value
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(240.dp).clip(MaterialTheme.shapes.large).checker()) {
                    if (s.animated) {
                        val px = with(LocalDensity.current) { 240.dp.roundToPx() }
                        AnimatedSticker(store.stickerFile(s), px, Modifier.fillMaxSize())
                    } else img?.let { Image(it, null, Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.height(8.dp))
                Text(s.emojis.joinToString(" "), style = MaterialTheme.typography.titleLarge)
                if (confirm) Text(stringResource(R.string.delete_sticker_q), modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (confirm) onDelete() else confirm = true }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(if (confirm) R.string.cancel else R.string.close)) } },
    )
}

@Composable
private fun RenameDialog(p: Pack, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(p.info.name) }
    var author by remember { mutableStateOf(p.info.publisher) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.rename_pack)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(128) }, label = { Text(stringResource(R.string.pack_name)) }, singleLine = true)
                OutlinedTextField(author, { author = it.take(128) }, label = { Text(stringResource(R.string.pack_author)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && author.isNotBlank(), onClick = { onSave(name, author) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
