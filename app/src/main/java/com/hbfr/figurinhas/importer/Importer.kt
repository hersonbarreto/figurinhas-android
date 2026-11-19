package com.hbfr.figurinhas.importer

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** O que o editor abre: um arquivo e o tipo dele. */
data class EditorInput(val uri: Uri, val mime: String)

class ImportException(message: String) : IOException(message)

/**
 * Entradas do app: Galeria, Compartilhar, Colar e Link.
 * Tudo que vem de fora é copiado para o cache, para continuar legível depois que o outro app fecha.
 */
object Importer {
    private const val MAX_BYTES = 60L * 1024 * 1024
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun inbox(context: Context) = File(context.cacheDir, "inbox").apply { mkdirs() }

    /** Apaga o que sobrou de importações antigas. */
    fun cleanup(context: Context) {
        val limit = System.currentTimeMillis() - 24 * 3600 * 1000L
        inbox(context).listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    /** Item da Galeria: o seletor do sistema já dá acesso duradouro, não precisa copiar. */
    fun fromPicker(context: Context, uri: Uri) = EditorInput(uri, context.contentResolver.getType(uri) ?: "image/*")

    /** Arquivo compartilhado por outro app. */
    suspend fun fromSharedStream(context: Context, uri: Uri, mimeHint: String?): EditorInput = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { s -> readLimited(s) }
            ?: throw ImportException("")
        save(context, bytes, context.contentResolver.getType(uri) ?: mimeHint)
    }

    /** Texto compartilhado ou colado: procura um link nele. */
    fun findLink(text: String?): String? =
        text?.let { Regex("""https?://\S+""").find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }

    /** Colar: imagem/vídeo copiado ou link. Devolve null se não houver nada útil. */
    suspend fun fromClipboard(context: Context): EditorInput? {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            item.uri?.let { u ->
                val type = context.contentResolver.getType(u)
                if (type != null && (type.startsWith("image/") || type.startsWith("video/"))) return fromSharedStream(context, u, type)
            }
            findLink(item.text?.toString())?.let { return fromLink(context, it) }
        }
        return null
    }

    /** Link: imagem, GIF ou vídeo direto, post (pela API pública do FxTwitter) ou página com og:image. */
    suspend fun fromLink(context: Context, raw: String): EditorInput = withContext(Dispatchers.IO) {
        val url = runCatching { URL(raw.trim()) }.getOrNull()
            ?.takeIf { it.protocol == "http" || it.protocol == "https" } ?: throw ImportException("bad_link")
        postMediaUrl(url)?.let { media -> return@withContext download(context, media) }
        val (bytes, type) = fetch(url)
        if (type.contains("html") || sniff(bytes) == null) {
            val html = String(bytes, Charsets.UTF_8)
            for (m in ogMedia(html, url)) {
                runCatching { return@withContext download(context, m) }
            }
            throw ImportException("not_image")
        }
        save(context, bytes, type)
    }

    private fun download(context: Context, url: URL): EditorInput {
        val (bytes, type) = fetch(url)
        if (sniff(bytes) == null) throw ImportException("not_image")
        return save(context, bytes, type)
    }

    private fun postMediaUrl(url: URL): URL? {
        val hosts = setOf("twitter.com", "x.com", "mobile.twitter.com", "www.twitter.com", "www.x.com",
            "fxtwitter.com", "fixupx.com", "vxtwitter.com", "fixvx.com")
        if (url.host.lowercase() !in hosts) return null
        val parts = url.path.split('/')
        val i = parts.indexOf("status")
        val id = parts.getOrNull(i + 1)?.takeIf { i >= 0 && it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val (bytes, _) = fetch(URL("https://api.fxtwitter.com/status/$id"))
        val media = runCatching { JSONObject(String(bytes)).getJSONObject("tweet").getJSONObject("media") }.getOrNull()
            ?: throw ImportException("post_no_media")
        media.optJSONArray("videos")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }?.let { return URL(it) }
        media.optJSONArray("photos")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }?.let { return URL(it) }
        throw ImportException("post_no_media")
    }

    private fun fetch(url: URL, redirects: Int = 5): Pair<ByteArray, String> {
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        try {
            val code = c.responseCode
            if (code in 300..399 && redirects > 0) {
                val loc = c.getHeaderField("Location") ?: throw ImportException("http_$code")
                return fetch(URL(url, loc), redirects - 1)
            }
            if (code !in 200..299) throw ImportException("http_$code")
            val bytes = c.inputStream.use { readLimited(it) }
            return bytes to (c.contentType ?: "").lowercase()
        } finally {
            c.disconnect()
        }
    }

    private fun readLimited(s: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = s.read(buf); if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw ImportException("too_large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** og:image, og:video e twitter:image. Ordem: GIF, vídeo, outras imagens. */
    fun ogMedia(html: String, base: URL): List<URL> {
        val tag = Regex("""<meta[^>]+(?:property|name)\s*=\s*["'](og:image(?::url|:secure_url)?|og:video(?::url|:secure_url)?|twitter:image)["'][^>]*>""", RegexOption.IGNORE_CASE)
        val content = Regex("""content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val gifs = ArrayList<URL>(); val videos = ArrayList<URL>(); val images = ArrayList<URL>()
        for (m in tag.findAll(html)) {
            val v = content.find(m.value)?.groupValues?.get(1)?.replace("&amp;", "&") ?: continue
            val u = runCatching { URL(base, v) }.getOrNull() ?: continue
            when {
                u.path.lowercase().endsWith(".gif") -> gifs += u
                m.groupValues[1].lowercase().startsWith("og:video") -> videos += u
                else -> images += u
            }
        }
        return (gifs + videos + images).distinctBy { it.toString() }
    }

    /** Tipo pelo conteúdo (sites às vezes mandam o Content-Type errado). */
    private fun sniff(b: ByteArray): String? {
        fun at(i: Int, s: String) = b.size >= i + s.length && String(b, i, s.length, Charsets.ISO_8859_1) == s
        return when {
            at(0, "GIF8") -> "image/gif"
            at(0, "\u0089PNG") -> "image/png"
            b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "image/jpeg"
            at(0, "RIFF") && at(8, "WEBP") -> "image/webp"
            at(4, "ftyp") -> if (at(8, "heic") || at(8, "heix") || at(8, "mif1") || at(8, "avif")) "image/heif" else "video/mp4"
            at(0, "\u001AEß£") -> "video/webm"
            else -> null
        }
    }

    private fun save(context: Context, bytes: ByteArray, typeHint: String?): EditorInput {
        val mime = sniff(bytes) ?: typeHint?.substringBefore(';')?.trim()?.takeIf { it.startsWith("image/") || it.startsWith("video/") }
            ?: throw ImportException("not_image")
        val ext = when {
            mime == "image/gif" -> "gif"; mime == "image/png" -> "png"; mime == "image/webp" -> "webp"
            mime.startsWith("video/") -> "mp4"; else -> "jpg"
        }
        val f = File(inbox(context), "${UUID.randomUUID()}.$ext")
        f.writeBytes(bytes)
        return EditorInput(Uri.fromFile(f), mime)
    }
}
