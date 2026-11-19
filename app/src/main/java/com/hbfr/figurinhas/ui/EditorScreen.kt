package com.hbfr.figurinhas.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hbfr.figurinhas.R
import com.hbfr.figurinhas.data.StickerStore
import com.hbfr.figurinhas.provider.StickerContentProvider
import com.hbfr.figurinhas.render.StickerLayout
import com.hbfr.figurinhas.render.StickerRenderer
import com.hbfr.figurinhas.ui.theme.GradientButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val WHATSAPP_EMOJIS = listOf("😀", "😂", "🤣", "😍", "🥰", "😎", "😭", "😡", "😱", "🤔", "😴", "🥳", "👍", "🙏", "👏", "❤️", "🔥", "🎉", "💀", "🤡")

@Composable
fun EditorScreen(uri: Uri, store: StickerStore, onBack: () -> Unit, onSaved: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var layout by remember { mutableStateOf(StickerLayout()) }
    var tab by remember { mutableIntStateOf(0) }
    var moveEmoji by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val emojis = remember { mutableStateListOf<String>() }
    var packId by remember { mutableStateOf(store.suggestedPackId(animated = false)) }
    val loadError = stringResource(R.string.load_error)
    val names = StickerStore.DefaultNames(
        stringResource(R.string.default_pack), stringResource(R.string.default_pack_animated), stringResource(R.string.numbered_pack))

    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { runCatching { StickerRenderer.decode(context, uri) }.getOrNull() }
        if (bmp == null) { onError(loadError); return@LaunchedEffect }
        bitmap = bmp
        layout = StickerLayout(scale = StickerRenderer.fitScale(bmp))
    }
    val fit = bitmap?.let { StickerRenderer.fitScale(it) } ?: 1f
    val fill = bitmap?.let { StickerRenderer.SIZE / min(it.width, it.height).toFloat() } ?: 1f

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.back)) }
            Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Prévia: o mesmo desenho que vira o arquivo, em escala.
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge).checker()) {
                val bmp = bitmap
                if (bmp == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Canvas(Modifier.fillMaxSize().pointerInput(moveEmoji) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            val f = size.width / StickerRenderer.SIZE.toFloat()
                            val c = centroid / f
                            val p = pan / f
                            val l = layout
                            layout = if (moveEmoji) {
                                l.copy(emojiX = l.emojiX + p.x, emojiY = l.emojiY + p.y,
                                    emojiSize = (l.emojiSize * zoom).coerceIn(40f, 420f))
                            } else {
                                val rad = Math.toRadians(rotation.toDouble())
                                val vx = l.centerX - c.x; val vy = l.centerY - c.y
                                val rx = (vx * cos(rad) - vy * sin(rad)).toFloat() * zoom
                                val ry = (vx * sin(rad) + vy * cos(rad)).toFloat() * zoom
                                l.copy(centerX = c.x + rx + p.x, centerY = c.y + ry + p.y,
                                    scale = StickerRenderer.clampScale(l.scale * zoom), rotation = l.rotation + rotation)
                            }
                        }
                    }) {
                        val f = size.width / StickerRenderer.SIZE
                        drawIntoCanvas { cv ->
                            val n = cv.nativeCanvas
                            n.save(); n.scale(f, f)
                            StickerRenderer.draw(n, bmp, layout)
                            n.restore()
                        }
                    }
                }
            }
            Text(stringResource(R.string.editor_hint), style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))

            val tabs = listOf(R.string.tab_adjust, R.string.tab_text, R.string.tab_emoji)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, t ->
                    SegmentedButton(tab == i, { tab = i; moveEmoji = i == 2 && layout.emoji.isNotBlank() },
                        SegmentedButtonDefaults.itemShape(i, tabs.size)) { Text(stringResource(t)) }
                }
            }
            Spacer(Modifier.height(10.dp))

            when (tab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolTile(painterResource(R.drawable.ic_rotate), R.string.rotate) {
                            layout = layout.copy(rotation = (layout.rotation + 90f) % 360f)
                        }
                        ToolTile(painterResource(R.drawable.ic_mirror), R.string.mirror) { layout = layout.copy(mirrored = !layout.mirrored) }
                        ToolTile(painterResource(R.drawable.ic_center), R.string.fit) {
                            layout = layout.copy(centerX = 256f, centerY = 256f, rotation = 0f, scale = fit)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Panel {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = layout.scale == fit, onClick = {
                                layout = layout.copy(centerX = 256f, centerY = 256f, scale = fit)
                            }, label = { Text(stringResource(R.string.fit_whole)) })
                            FilterChip(selected = layout.scale == fill, onClick = {
                                layout = layout.copy(centerX = 256f, centerY = 256f, scale = fill)
                            }, label = { Text(stringResource(R.string.fit_fill)) })
                        }
                        Spacer(Modifier.height(8.dp))
                        val pct = layout.scale / fit
                        Row {
                            Text(stringResource(R.string.zoom), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        Slider(value = pct.coerceIn(0.3f, 4f), valueRange = 0.3f..4f,
                            onValueChange = { layout = layout.copy(scale = fit * it) })
                    }
                }
                1 -> Panel {
                    OutlinedTextField(layout.topText, { layout = layout.copy(topText = it.take(60)) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.top_text)) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(layout.bottomText, { layout = layout.copy(bottomText = it.take(60)) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.bottom_text)) }, singleLine = true)
                }
                else -> Panel {
                    OutlinedTextField(layout.emoji, { v ->
                        layout = layout.copy(emoji = v.take(8)); moveEmoji = v.isNotBlank()
                    }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.emoji_on_sticker)) }, singleLine = true)
                    if (layout.emoji.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(!moveEmoji, { moveEmoji = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.move_image)) }
                            SegmentedButton(moveEmoji, { moveEmoji = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.move_emoji)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.whatsapp_emojis), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WHATSAPP_EMOJIS.forEach { e ->
                            val on = e in emojis
                            FilterChip(selected = on, onClick = {
                                if (on) emojis.remove(e) else if (emojis.size < 3) emojis.add(e)
                            }, label = { Text(e, style = MaterialTheme.typography.titleMedium) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Barra de baixo: "Salvar em" + botão.
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            PackPicker(store, packId, names.static) { packId = it }
            Spacer(Modifier.height(8.dp))
            GradientButton(
                enabled = bitmap != null && !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val bmp = bitmap ?: return@GradientButton
                    saving = true
                    scope.launch {
                        val l = layout
                        val webp = withContext(Dispatchers.Default) {
                            val out = StickerRenderer.render(bmp, l)
                            StickerRenderer.encodeStaticWebP(out).also { out.recycle() }
                        }
                        val saved = withContext(Dispatchers.IO) {
                            store.add(webp, animated = false, emojis = emojis.toList(), packId = packId, defaultNames = names)
                                .also { StickerContentProvider.notifyChanged(context) }
                        }
                        val packName = store.state.value.packs.firstOrNull { it.id == saved.packId }?.name.orEmpty()
                        onSaved(context.getString(R.string.saved, packName))
                    }
                },
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.saving), style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_sticker), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp)) {
        content()
    }
}

@Composable
private fun RowScope.ToolTile(icon: Painter, label: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.weight(1f).height(64.dp).clip(MaterialTheme.shapes.medium).background(cs.surfaceContainerHigh).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = cs.primary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PackPicker(store: StickerStore, selected: String?, defaultName: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val packs = store.packsAccepting(animated = false)
    var open by remember { mutableStateOf(false) }
    val current = packs.firstOrNull { it.id == selected }?.info?.name ?: defaultName
    Box {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(MaterialTheme.shapes.medium).background(cs.surfaceContainerHigh)
                .clickable(enabled = packs.size > 1) { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save_to), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(current, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            if (packs.size > 1) Icon(Icons.Default.ArrowDropDown, null, tint = cs.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            packs.forEach { p ->
                DropdownMenuItem(text = { Text("${p.info.name} (${p.stickers.size})") }, onClick = { onPick(p.id); open = false })
            }
        }
    }
}
