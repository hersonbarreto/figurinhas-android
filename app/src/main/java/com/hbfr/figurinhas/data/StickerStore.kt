package com.hbfr.figurinhas.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class StoredSticker(
    val id: String,
    val animated: Boolean,
    val emojis: List<String>,
    val createdAt: Long,
    val packId: String?,
) {
    val fileName get() = "$id.webp"
}

data class StoredPack(
    val id: String,
    val name: String,
    val publisher: String,
    /** null enquanto o pacote está vazio e ainda não tem tipo. */
    val animated: Boolean?,
    val createdAt: Long,
    /** Muda a cada alteração para o WhatsApp buscar o pacote de novo. */
    val version: Int,
)

data class Pack(val info: StoredPack, val stickers: List<StoredSticker>) {
    val id get() = info.id
    val animated get() = info.animated ?: false
    val missing get() = maxOf(0, WhatsAppLimits.MIN_STICKERS_PER_PACK - stickers.size)
    val isFull get() = stickers.size >= WhatsAppLimits.MAX_STICKERS_PER_PACK
}

object WhatsAppLimits {
    const val MIN_STICKERS_PER_PACK = 3
    const val MAX_STICKERS_PER_PACK = 30
    const val MAX_EMOJIS = 3
    const val STATIC_MAX_BYTES = 100 * 1024
    const val ANIMATED_MAX_BYTES = 500 * 1024
    const val TRAY_MAX_BYTES = 50 * 1024
    const val MAX_NAME_LENGTH = 128
}

/** Snapshot do que está no disco: figurinhas (index.json) e pacotes (packs.json). */
data class StoreState(val stickers: List<StoredSticker>, val packs: List<StoredPack>) {
    val packList: List<Pack>
        get() {
            val byPack = stickers.groupBy { it.packId }
            return packs.map { p -> Pack(p, byPack[p.id].orEmpty().sortedBy { it.createdAt }) }
        }
    val staticCount get() = stickers.count { !it.animated }
}

/**
 * Guarda as figurinhas em filesDir/stickers e os índices em JSON.
 * Leitura e escrita são pequenas e síncronas; o ContentProvider lê o mesmo disco com [read].
 */
class StickerStore(context: Context) {
    private val dir = File(context.filesDir, "store").apply { mkdirs() }
    val stickerDir = File(dir, "stickers").apply { mkdirs() }
    val trayDir = File(dir, "trays").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("store", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read(context))
    val state: StateFlow<StoreState> = _state

    val suggestedPublisher: String get() = prefs.getString("lastPublisher", null) ?: DEFAULT_PUBLISHER
    private var lastPackId: String?
        get() = prefs.getString("lastPackId", null)
        set(v) { prefs.edit().putString("lastPackId", v).apply() }

    fun stickerFile(s: StoredSticker) = File(stickerDir, s.fileName)
    fun trayFile(packId: String) = File(trayDir, trayName(packId))

    @Synchronized
    fun add(webp: ByteArray, animated: Boolean, emojis: List<String>, packId: String?, defaultNames: DefaultNames): StoredSticker {
        var st = _state.value
        val target = packId?.takeIf { canHold(st, it, animated) } ?: run {
            val (id, newState) = suggestedOrNewPack(st, animated, defaultNames)
            st = newState
            id
        }
        val s = StoredSticker(UUID.randomUUID().toString().lowercase(), animated,
            emojis.take(WhatsAppLimits.MAX_EMOJIS).ifEmpty { listOf(DEFAULT_EMOJI) }, System.currentTimeMillis(), target)
        File(stickerDir, s.fileName).writeBytes(webp)
        val packs = st.packs.map { if (it.id == target) it.copy(animated = animated, version = it.version + 1) else it }
        commit(StoreState(st.stickers + s, packs))
        lastPackId = target
        return s
    }

    @Synchronized
    fun delete(s: StoredSticker) {
        val st = _state.value
        stickerFile(s).delete()
        val packs = st.packs.map { if (it.id == s.packId) it.copy(version = it.version + 1) else it }
        commit(StoreState(st.stickers.filter { it.id != s.id }, packs))
    }

    @Synchronized
    fun rename(packId: String, name: String, publisher: String) {
        val st = _state.value
        val n = name.trim().take(WhatsAppLimits.MAX_NAME_LENGTH)
        val p = publisher.trim().take(WhatsAppLimits.MAX_NAME_LENGTH)
        if (n.isEmpty() || p.isEmpty()) return
        prefs.edit().putString("lastPublisher", p).apply()
        commit(st.copy(packs = st.packs.map { if (it.id == packId) it.copy(name = n, publisher = p, version = it.version + 1) else it }))
    }

    @Synchronized
    fun deletePack(packId: String) {
        val st = _state.value
        st.stickers.filter { it.packId == packId }.forEach { stickerFile(it).delete() }
        trayFile(packId).delete()
        commit(StoreState(st.stickers.filter { it.packId != packId }, st.packs.filter { it.id != packId }))
        if (lastPackId == packId) lastPackId = null
    }

    @Synchronized
    fun createPack(defaultNames: DefaultNames): String {
        val st = _state.value
        val p = newPack(st, uniqueName(st, defaultNames.numbered))
        commit(st.copy(packs = st.packs + p))
        lastPackId = p.id
        return p.id
    }

    /** Pacote sugerido para "Salvar em": o último usado se ainda aceita, senão o primeiro que aceita. */
    fun suggestedPackId(animated: Boolean): String? {
        val st = _state.value
        val open = st.packList.filter { accepts(it, animated) }
        lastPackId?.let { last -> if (open.any { it.id == last }) return last }
        return open.firstOrNull()?.id
    }

    fun packsAccepting(animated: Boolean) = _state.value.packList.filter { accepts(it, animated) }

    private fun accepts(p: Pack, animated: Boolean) = !p.isFull && (p.info.animated == null || p.info.animated == animated)
    private fun canHold(st: StoreState, id: String, animated: Boolean) = st.packList.firstOrNull { it.id == id }?.let { accepts(it, animated) } ?: false

    private fun suggestedOrNewPack(st: StoreState, animated: Boolean, names: DefaultNames): Pair<String, StoreState> {
        val open = st.packList.filter { accepts(it, animated) }
        lastPackId?.let { last -> if (open.any { it.id == last }) return last to st }
        open.firstOrNull()?.let { return it.id to st }
        val base = if (animated) names.animated else names.static
        val name = if (st.packs.none { it.name == base }) base else uniqueName(st, "$base %d", start = 2)
        val p = newPack(st, name)
        return p.id to st.copy(packs = st.packs + p)
    }

    private fun uniqueName(st: StoreState, pattern: String, start: Int = st.packs.size + 1): String {
        val names = st.packs.map { it.name }.toSet()
        var n = start
        while (pattern.format(n) in names) n++
        return pattern.format(n)
    }

    private fun newPack(st: StoreState, name: String) = StoredPack(
        id = "pack-" + UUID.randomUUID().toString().lowercase(),
        name = name, publisher = suggestedPublisher, animated = null,
        createdAt = System.currentTimeMillis(), version = 1,
    )

    private fun commit(st: StoreState) {
        write(dir, st)
        _state.value = st
    }

    /** Nomes padrão vêm dos recursos de texto (idioma do aparelho). */
    data class DefaultNames(val static: String, val animated: String, val numbered: String)

    companion object {
        const val DEFAULT_PUBLISHER = "Figurinhas"
        const val DEFAULT_EMOJI = "😀"
        fun trayName(packId: String) = "tray-$packId.png"

        fun read(context: Context): StoreState {
            val dir = File(context.filesDir, "store")
            val stickers = runCatching {
                val a = JSONArray(File(dir, "index.json").readText())
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    val e = o.optJSONArray("emojis") ?: JSONArray()
                    StoredSticker(o.getString("id"), o.optBoolean("animated"),
                        List(e.length()) { e.getString(it) }, o.optLong("createdAt"),
                        if (o.isNull("packId")) null else o.optString("packId"))
                }
            }.getOrDefault(emptyList())
            val packs = runCatching {
                val a = JSONArray(File(dir, "packs.json").readText())
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    StoredPack(o.getString("id"), o.getString("name"), o.getString("publisher"),
                        if (o.isNull("animated") || !o.has("animated")) null else o.getBoolean("animated"),
                        o.optLong("createdAt"), o.optInt("version", 1))
                }
            }.getOrDefault(emptyList())
            return StoreState(stickers, packs)
        }

        private fun write(dir: File, st: StoreState) {
            val a = JSONArray()
            st.stickers.forEach { s ->
                a.put(JSONObject().put("id", s.id).put("animated", s.animated)
                    .put("emojis", JSONArray(s.emojis)).put("createdAt", s.createdAt)
                    .put("packId", s.packId ?: JSONObject.NULL))
            }
            val p = JSONArray()
            st.packs.forEach { k ->
                p.put(JSONObject().put("id", k.id).put("name", k.name).put("publisher", k.publisher)
                    .put("animated", k.animated ?: JSONObject.NULL).put("createdAt", k.createdAt).put("version", k.version))
            }
            atomicWrite(File(dir, "index.json"), a.toString())
            atomicWrite(File(dir, "packs.json"), p.toString())
        }

        private fun atomicWrite(f: File, text: String) {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
    }
}
