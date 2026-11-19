package com.hbfr.figurinhas.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hbfr.figurinhas.provider.StickerContentProvider
import com.hbfr.figurinhas.render.StickerRenderer

/** Envia pacotes ao WhatsApp pelo jeito oficial do Android (intent + ContentProvider). */
object WhatsAppSender {
    const val CONSUMER = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"
    private const val ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val WHITELIST_AUTHORITY = "com.whatsapp.provider.sticker_whitelist_check"

    fun installed(context: Context): List<String> = listOf(CONSUMER, BUSINESS).filter {
        runCatching { context.packageManager.getPackageInfo(it, 0); true }.getOrDefault(false)
    }

    /** true se o pacote já está no WhatsApp indicado. */
    fun isAdded(context: Context, whatsapp: String, packId: String): Boolean = runCatching {
        val uri = Uri.Builder().scheme("content").authority(if (whatsapp == BUSINESS) "$BUSINESS.provider.sticker_whitelist_check" else WHITELIST_AUTHORITY)
            .appendPath("is_whitelisted")
            .appendQueryParameter("authority", StickerContentProvider.AUTHORITY)
            .appendQueryParameter("identifier", packId).build()
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow("result")) == 1
        } ?: false
    }.getOrDefault(false)

    /** Prepara o ícone e devolve o intent que abre o WhatsApp para adicionar o pacote. */
    fun prepare(context: Context, store: StickerStore, pack: Pack, whatsapp: String): Intent {
        val first = store.stickerFile(pack.stickers.first()).readBytes()
        store.trayFile(pack.id).writeBytes(StickerRenderer.trayPng(first))
        StickerContentProvider.notifyChanged(context)
        return Intent(ACTION).apply {
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", pack.info.name)
            setPackage(whatsapp)
        }
    }

}
