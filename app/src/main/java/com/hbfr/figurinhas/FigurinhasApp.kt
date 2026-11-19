package com.hbfr.figurinhas

import android.app.Application
import com.hbfr.figurinhas.data.StickerStore

class FigurinhasApp : Application() {
    val store: StickerStore by lazy { StickerStore(this) }
}
