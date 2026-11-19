package com.hbfr.figurinhas.media

import android.graphics.Bitmap
import java.nio.ByteBuffer

object NativeImage {
    init { System.loadLibrary("figwebp") }

    @JvmStatic external fun yuvToBitmap(
        y: ByteBuffer, u: ByteBuffer, v: ByteBuffer,
        yRow: Int, yPix: Int, uRow: Int, uPix: Int, vRow: Int, vPix: Int,
        cropL: Int, cropT: Int, sw: Int, sh: Int, rotation: Int, out: Bitmap,
    ): Boolean
}
