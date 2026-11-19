package com.hbfr.figurinhas

import android.app.Application
import com.hbfr.figurinhas.data.StickerStore
import com.hbfr.figurinhas.purchases.PurchaseManager

class FigurinhasApp : Application() {
    val store: StickerStore by lazy { StickerStore(this) }
    val purchases: PurchaseManager by lazy { PurchaseManager(this).also { it.start() } }
}
