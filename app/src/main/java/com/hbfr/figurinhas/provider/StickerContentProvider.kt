package com.hbfr.figurinhas.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hbfr.figurinhas.BuildConfig
import com.hbfr.figurinhas.data.StickerStore
import com.hbfr.figurinhas.render.StickerRenderer
import java.io.File

/**
 * O WhatsApp lê os pacotes por aqui (contrato de github.com/WhatsApp/stickers).
 * Só pacotes com 3 ou mais figurinhas aparecem.
 */
class StickerContentProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, METADATA, ALL_PACKS)
        addURI(AUTHORITY, "$METADATA/*", ONE_PACK)
        addURI(AUTHORITY, "$STICKERS/*", PACK_STICKERS)
        addURI(AUTHORITY, "$STICKERS_ASSET/*/*", ASSET)
    }

    override fun onCreate() = true

    private fun packs() = StickerStore.read(context!!).packList.filter { it.missing == 0 }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        return when (matcher.match(uri)) {
            ALL_PACKS -> packCursor(uri, packs())
            ONE_PACK -> packCursor(uri, packs().filter { it.id == uri.lastPathSegment })
            PACK_STICKERS -> {
                val c = MatrixCursor(arrayOf(STICKER_FILE_NAME, STICKER_EMOJI, STICKER_ACCESSIBILITY))
                packs().firstOrNull { it.id == uri.lastPathSegment }?.stickers?.forEach {
                    c.addRow(arrayOf(it.fileName, it.emojis.joinToString(","), null))
                }
                c.setNotificationUri(context!!.contentResolver, uri)
                c
            }
            else -> null
        }
    }

    private fun packCursor(uri: Uri, list: List<com.hbfr.figurinhas.data.Pack>): Cursor {
        val c = MatrixCursor(arrayOf(
            PACK_ID, PACK_NAME, PACK_PUBLISHER, PACK_ICON, ANDROID_PLAY_STORE_LINK, IOS_APP_LINK,
            PUBLISHER_EMAIL, PUBLISHER_WEBSITE, PRIVACY_POLICY, LICENSE_AGREEMENT,
            IMAGE_DATA_VERSION, AVOID_CACHE, ANIMATED_PACK,
        ))
        list.forEach { p ->
            c.addRow(arrayOf(
                p.id, p.info.name, p.info.publisher, StickerStore.trayName(p.id),
                "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
                "https://apps.apple.com/app/id6823649557",
                "contato@hbfr.com.br", "https://hbfr.com.br/figurinhas/",
                "https://hbfr.com.br/figurinhas/privacidade.html", "",
                p.info.version.toString(), 0, if (p.animated) 1 else 0,
            ))
        }
        c.setNotificationUri(context!!.contentResolver, uri)
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != ASSET) return null
        val seg = uri.pathSegments
        val packId = seg[seg.size - 2]
        val name = seg.last()
        if (name.contains('/') || name.contains("..")) return null
        val base = File(context!!.filesDir, "store")
        val file = if (name == StickerStore.trayName(packId)) File(base, "trays/$name") else File(base, "stickers/$name")
        if (!file.exists() && name == StickerStore.trayName(packId)) makeTray(packId, file)
        if (!file.exists()) return null
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
    }

    /** Ícone ainda não gerado (pacote nunca enviado por este celular): gera a partir da primeira figurinha. */
    private fun makeTray(packId: String, out: File) {
        val first = StickerStore.read(context!!).packList.firstOrNull { it.id == packId }?.stickers?.firstOrNull() ?: return
        val src = File(context!!.filesDir, "store/stickers/${first.fileName}")
        if (!src.exists()) return
        out.parentFile?.mkdirs()
        out.writeBytes(StickerRenderer.trayPng(src.readBytes()))
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        ALL_PACKS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$METADATA"
        ONE_PACK -> "vnd.android.cursor.item/vnd.$AUTHORITY.$METADATA"
        PACK_STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$STICKERS"
        ASSET -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()

    companion object {
        val AUTHORITY: String = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"
        private const val ALL_PACKS = 1
        private const val ONE_PACK = 2
        private const val PACK_STICKERS = 3
        private const val ASSET = 4

        const val PACK_ID = "sticker_pack_identifier"
        const val PACK_NAME = "sticker_pack_name"
        const val PACK_PUBLISHER = "sticker_pack_publisher"
        const val PACK_ICON = "sticker_pack_icon"
        const val ANDROID_PLAY_STORE_LINK = "android_play_store_link"
        const val IOS_APP_LINK = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        const val PRIVACY_POLICY = "sticker_pack_privacy_policy_website"
        const val LICENSE_AGREEMENT = "sticker_pack_license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        const val ANIMATED_PACK = "animated_sticker_pack"
        const val STICKER_FILE_NAME = "sticker_file_name"
        const val STICKER_EMOJI = "sticker_emoji"
        const val STICKER_ACCESSIBILITY = "sticker_accessibility_text"

        fun notifyChanged(context: android.content.Context) {
            context.contentResolver.notifyChange(Uri.parse("content://$AUTHORITY/$METADATA"), null)
        }
    }
}
