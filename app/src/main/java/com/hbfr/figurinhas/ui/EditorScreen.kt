package com.hbfr.figurinhas.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import com.hbfr.figurinhas.ui.theme.Violet
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
import com.hbfr.figurinhas.media.FrameReader
import com.hbfr.figurinhas.importer.EditorInput
import com.hbfr.figurinhas.render.BackgroundRemover
import androidx.compose.material3.TextButton
import com.hbfr.figurinhas.render.AnimatedWebP
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val WHATSAPP_EMOJIS = listOf("😀", "😂", "🤣", "😍", "🥰", "😎", "😭", "😡", "😱", "🤔", "😴", "🥳", "👍", "🙏", "👏", "❤️", "🔥", "🎉", "💀", "🤡")


private enum class Tab(val label: Int) { Trim(R.string.tab_trim), Adjust(R.string.tab_adjust), Background(R.string.tab_background), Text(R.string.tab_text), Emoji(R.string.tab_emoji) }

@Composable
fun EditorScreen(input: EditorInput, store: StickerStore, onBack: () -> Unit, onSaved: (String) -> Unit, onError: (String) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    // Origem: foto parada ou leitor de GIF/vídeo.
    var still by remember { mutableStateOf<Bitmap?>(null) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var bgBusy by remember { mutableStateOf(false) }
    var tolerance by remember { mutableFloatStateOf(40f) }
    var cropping by remember { mutableStateOf(false) }
    var reader by remember { mutableStateOf<FrameReader?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val animated = reader != null
    val logicalW = reader?.width ?: still?.width ?: 1
    val logicalH = reader?.height ?: still?.height ?: 1

    // Trecho (só animadas).
    var trimStart by remember { mutableLongStateOf(0L) }
    var trimLength by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var frames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var framesLoading by remember { mutableStateOf(false) }
    var frameIdx by remember { mutableIntStateOf(0) }
    var strip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var layout by remember { mutableStateOf(StickerLayout()) }
    var tab by remember { mutableStateOf(Tab.Adjust) }
    var moveEmoji by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val emojis = remember { mutableStateListOf<String>() }
    var packId by remember { mutableStateOf<String?>(null) }
    val loadError = stringResource(R.string.load_error)
    val tooBig = stringResource(R.string.too_big)
    val names = StickerStore.DefaultNames(
        stringResource(R.string.default_pack), stringResource(R.string.default_pack_animated), stringResource(R.string.numbered_pack))

    LaunchedEffect(input) {
        val uri = input.uri
        val r = withContext(Dispatchers.IO) { runCatching { FrameReader.open(context, uri, input.mime) }.getOrNull() }
        if (r != null && r.durationMs > 0 && r.width > 0) {
            reader = r
            trimLength = minOf(r.durationMs, AnimatedWebP.MAX_DURATION_MS)
            tab = Tab.Trim
            layout = StickerLayout(scale = StickerRenderer.fitScale(r.width, r.height))
            packId = store.suggestedPackId(animated = true)
            strip = withContext(Dispatchers.IO) {
                (0 until 10).mapNotNull { i -> r.thumbnail(r.durationMs * (2 * i + 1) / 20, 120) }
            }
        } else {
            r?.close()
            val bmp = withContext(Dispatchers.IO) { runCatching { StickerRenderer.decode(context, uri) }.getOrNull() }
            if (bmp == null) { onError(loadError); return@LaunchedEffect }
            still = bmp
            original = bmp
            layout = StickerLayout(scale = StickerRenderer.fitScale(bmp))
            packId = store.suggestedPackId(animated = false)
        }
        loaded = true
    }
    DisposableEffect(Unit) { onDispose { reader?.close() } }

    // Quadros da prévia (320 px) do trecho escolhido; recarrega pouco depois de mexer.
    LaunchedEffect(reader, trimStart, trimLength) {
        val r = reader ?: return@LaunchedEffect
        delay(200)
        framesLoading = true
        val list = ArrayList<Bitmap>()
        withContext(Dispatchers.Default) {
            r.read(trimStart, trimLength, AnimatedWebP.STEP_MS, 320) { f, _ -> list.add(f.copy(Bitmap.Config.ARGB_8888, false)) }
        }
        val old = frames
        frames = list
        frameIdx = 0
        framesLoading = false
        old.forEach { it.recycle() }
    }
    LaunchedEffect(frames, speed) {
        if (frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(AnimatedWebP.frameDuration(speed))
            frameIdx = (frameIdx + 1) % frames.size
        }
    }

    val cropW = (logicalW * (layout.cropR - layout.cropL)).toInt().coerceAtLeast(1)
    val cropH = (logicalH * (layout.cropB - layout.cropT)).toInt().coerceAtLeast(1)
    val fit = StickerRenderer.fitScale(cropW, cropH)
    val fill = StickerRenderer.fillScale(cropW, cropH)
    val cutoutNone = stringResource(R.string.cutout_none)
    val cutoutError = stringResource(R.string.cutout_error)

    if (cropping) {
        val bmp = if (animated) frames.getOrNull(0) else still
        if (bmp == null) cropping = false else CropDialog(bmp, androidx.compose.ui.geometry.Rect(layout.cropL, layout.cropT, layout.cropR, layout.cropB),
            onCancel = { cropping = false }) { r ->
            cropping = false
            val cw = (logicalW * r.width).toInt().coerceAtLeast(1); val ch = (logicalH * r.height).toInt().coerceAtLeast(1)
            layout = layout.copy(cropL = r.left, cropT = r.top, cropR = r.right, cropB = r.bottom,
                centerX = 256f, centerY = 256f, rotation = 0f, scale = StickerRenderer.fitScale(cw, ch))
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.back)) }
            Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Prévia: o mesmo desenho que vira o arquivo, em escala.
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge).checker()) {
                val bmp = if (animated) frames.getOrNull(frameIdx.coerceAtMost(frames.size - 1)) else still
                if (bmp != null) {
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
                            StickerRenderer.draw(n, bmp, layout, logicalW)
                            n.restore()
                        }
                    }
                }
                if (!loaded || (animated && framesLoading && frames.isEmpty())) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Text(stringResource(R.string.editor_hint), style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))

            val tabs = if (animated) listOf(Tab.Trim, Tab.Adjust, Tab.Text, Tab.Emoji) else listOf(Tab.Adjust, Tab.Background, Tab.Text, Tab.Emoji)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, t ->
                    SegmentedButton(tab == t, { tab = t; moveEmoji = t == Tab.Emoji && layout.emoji.isNotBlank() },
                        SegmentedButtonDefaults.itemShape(i, tabs.size)) { Text(stringResource(t.label), maxLines = 1) }
                }
            }
            Spacer(Modifier.height(10.dp))

            when (tab) {
                Tab.Trim -> reader?.let { r ->
                    TrimPanel(r.durationMs, trimStart, trimLength, speed, strip, framesLoading,
                        onTrim = { s, l -> trimStart = s; trimLength = l },
                        onSpeed = { sp ->
                            speed = sp
                            val maxLen = AnimatedWebP.maxSourceLength(sp)
                            if (trimLength > maxLen) trimLength = maxLen
                        })
                }
                Tab.Adjust -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolTile(painterResource(R.drawable.ic_crop), R.string.crop) { cropping = true }
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
                Tab.Background -> Panel {
                    val src = original
                    Text(stringResource(R.string.auto_cutout), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.auto_cutout_body), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    GradientButton(enabled = src != null && !bgBusy, modifier = Modifier.fillMaxWidth(), onClick = {
                        val s = src ?: return@GradientButton
                        bgBusy = true
                        scope.launch {
                            val out = runCatching { withContext(Dispatchers.Default) { BackgroundRemover.liftSubject(s) } }
                            bgBusy = false
                            out.onSuccess { b -> if (b == null) onMessage(cutoutNone) else still = b }
                                .onFailure { onMessage(cutoutError) }
                        }
                    }) {
                        if (bgBusy) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        else { Icon(painterResource(R.drawable.ic_magic), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                        Text(stringResource(if (bgBusy) R.string.working else R.string.auto_cutout), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.solid_bg), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.solid_bg_body), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tolerance), style = MaterialTheme.typography.bodyMedium)
                        Slider(value = tolerance, valueRange = 5f..120f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            onValueChange = { tolerance = it },
                            onValueChangeFinished = {
                                val s = src ?: return@Slider
                                bgBusy = true
                                scope.launch {
                                    still = withContext(Dispatchers.Default) { BackgroundRemover.removeSolidBackground(s, tolerance) }
                                    bgBusy = false
                                }
                            })
                    }
                    androidx.compose.material3.OutlinedButton(enabled = src != null && !bgBusy, onClick = {
                        val s = src ?: return@OutlinedButton
                        bgBusy = true
                        scope.launch {
                            still = withContext(Dispatchers.Default) { BackgroundRemover.removeSolidBackground(s, tolerance) }
                            bgBusy = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.solid_bg)) }
                    if (still !== original) {
                        TextButton(onClick = { still = original }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(stringResource(R.string.undo_bg))
                        }
                    }
                }
                Tab.Text -> Panel {
                    OutlinedTextField(layout.topText, { layout = layout.copy(topText = it.take(60)) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.top_text)) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(layout.bottomText, { layout = layout.copy(bottomText = it.take(60)) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.bottom_text)) }, singleLine = true)
                }
                Tab.Emoji -> Panel {
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
            PackPicker(store, animated, packId, if (animated) names.animated else names.static) { packId = it }
            Spacer(Modifier.height(8.dp))
            GradientButton(
                enabled = loaded && !saving && (!animated || frames.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saving = true
                    progress = 0f
                    scope.launch {
                        val l = layout
                        var note: String? = null
                        val webp = withContext(Dispatchers.Default) {
                            val r = reader
                            if (r != null) {
                                val res = AnimatedWebP.encode(context, r, l, trimStart, trimLength, speed) { p -> progress = p }
                                if (res != null && res.sourceLengthMs + AnimatedWebP.STEP_MS < trimLength) {
                                    val secs = String.format(Locale.getDefault(), "%.1f", res.sourceLengthMs / speed / 1000f)
                                    note = context.getString(R.string.trimmed, secs)
                                }
                                res?.bytes
                            } else {
                                val out = StickerRenderer.render(still!!, l)
                                StickerRenderer.encodeStaticWebP(out).also { out.recycle() }
                            }
                        }
                        if (webp == null) { saving = false; onError(tooBig); return@launch }
                        val saved = withContext(Dispatchers.IO) {
                            store.add(webp, animated = animated, emojis = emojis.toList(), packId = packId, defaultNames = names)
                                .also { StickerContentProvider.notifyChanged(context) }
                        }
                        val packName = store.state.value.packs.firstOrNull { it.id == saved.packId }?.name.orEmpty()
                        onSaved(listOfNotNull(context.getString(R.string.saved, packName), note).joinToString(" "))
                    }
                },
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (animated) stringResource(R.string.encoding, (progress * 100).toInt()) else stringResource(R.string.saving),
                        style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_sticker), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Trecho, como no iPhone: tira de quadros do vídeo inteiro com uma janela.
 * Arrastar o meio move a janela; as pontas mudam início e fim. Setas andam um quadro.
 */
@Composable
private fun TrimPanel(
    duration: Long, start: Long, length: Long, speed: Float, strip: List<Bitmap>, loading: Boolean,
    onTrim: (Long, Long) -> Unit, onSpeed: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val maxLen = minOf(duration, AnimatedWebP.maxSourceLength(speed))
    val minLen = minOf(duration, 500L)
    var s by remember(start, length) { mutableLongStateOf(start) }
    var len by remember(start, length) { mutableLongStateOf(length) }
    val thumbs = remember(strip) { strip.map { it.asImageBitmap() } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleW = with(density) { 16.dp.toPx() }
    fun secs(ms: Long) = String.format(Locale.getDefault(), "%.1f s", ms / 1000f)
    fun snap(ms: Long) = (ms / AnimatedWebP.STEP_MS) * AnimatedWebP.STEP_MS

    Panel {
        Row {
            Text(stringResource(R.string.tab_trim), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${secs(s)} – ${secs(s + len)} · ${secs(len)}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(58.dp)) {
            Row(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).background(cs.surfaceContainerHigh)) {
                thumbs.forEach { b -> Image(b, null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Canvas(Modifier.fillMaxSize().pointerInput(duration, maxLen) {
                var part = 0 // 1 início, 2 fim, 3 mover
                var origS = 0L; var origLen = 0L; var acc = 0f
                detectDragGestures(
                    onDragStart = { p ->
                        val pps = size.width / duration.toFloat()
                        val x0 = s * pps; val x1 = (s + len) * pps
                        part = when {
                            kotlin.math.abs(p.x - x0) <= handleW * 1.5f && p.x < (x0 + x1) / 2 -> 1
                            kotlin.math.abs(p.x - x1) <= handleW * 1.5f -> 2
                            else -> 3
                        }
                        origS = s; origLen = len; acc = 0f
                    },
                    onDragEnd = { onTrim(s, len) },
                    onDragCancel = { onTrim(s, len) },
                ) { change, d ->
                    change.consume()
                    acc += d.x
                    val dMs = (acc / size.width * duration).toLong()
                    when (part) {
                        3 -> s = snap((origS + dMs).coerceIn(0L, duration - origLen))
                        1 -> {
                            val end = origS + origLen
                            val ns = snap((origS + dMs).coerceIn(maxOf(0L, end - maxLen), end - minLen))
                            s = ns; len = end - ns
                        }
                        2 -> len = snap((origLen + dMs).coerceIn(minLen, minOf(maxLen, duration - origS))).coerceAtLeast(minLen)
                    }
                }
            }) {
                val pps = size.width / duration.toFloat()
                val x0 = s * pps; val x1 = ((s + len) * pps).coerceAtMost(size.width)
                val dim = Color(0xA6000000)
                drawRect(dim, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(x0, size.height))
                drawRect(dim, androidx.compose.ui.geometry.Offset(x1, 0f), androidx.compose.ui.geometry.Size(size.width - x1, size.height))
                val stroke = 3.dp.toPx()
                drawRoundRect(com.hbfr.figurinhas.ui.theme.BrandBrush.LightGradient,
                    topLeft = androidx.compose.ui.geometry.Offset(x0 + stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size((x1 - x0 - stroke).coerceAtLeast(1f), size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                val r = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                drawRoundRect(com.hbfr.figurinhas.ui.theme.Violet, androidx.compose.ui.geometry.Offset(x0, 0f),
                    androidx.compose.ui.geometry.Size(handleW * 0.9f, size.height), r)
                drawRoundRect(com.hbfr.figurinhas.ui.theme.Cyan, androidx.compose.ui.geometry.Offset(x1 - handleW * 0.9f, 0f),
                    androidx.compose.ui.geometry.Size(handleW * 0.9f, size.height), r)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val ns = (s - AnimatedWebP.STEP_MS).coerceAtLeast(0L); onTrim(ns, len)
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.frame_back)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.trim_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (loading) Text(stringResource(R.string.loading_frames), style = MaterialTheme.typography.bodySmall, color = cs.primary)
            }
            IconButton(onClick = {
                val ns = (s + AnimatedWebP.STEP_MS).coerceAtMost(duration - len).coerceAtLeast(0L); onTrim(ns, len)
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.frame_forward)) }
        }
        Row {
            Text(stringResource(R.string.speed), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(String.format(Locale.getDefault(), "%.2gx", speed), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Slider(value = speed, valueRange = 0.5f..2f, steps = 5, onValueChange = onSpeed)
    }
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
private fun PackPicker(store: StickerStore, animated: Boolean, selected: String?, defaultName: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val packs = store.packsAccepting(animated)
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
