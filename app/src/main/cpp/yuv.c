// YUV 4:2:0 do decodificador de vídeo → bitmap RGBA, já girado e reduzido com média
// (sem o serrilhado de pegar um pixel só). Roda em C porque em Kotlin era o gargalo.
#include <jni.h>
#include <stdint.h>
#include <android/bitmap.h>

static inline uint8_t clamp255(int v) { return (uint8_t)(v < 0 ? 0 : (v > 255 ? 255 : v)); }

JNIEXPORT jboolean JNICALL
Java_com_hbfr_figurinhas_media_NativeImage_yuvToBitmap(
    JNIEnv* env, jclass cls, jobject yb, jobject ub, jobject vb,
    jint yRow, jint yPix, jint uRow, jint uPix, jint vRow, jint vPix,
    jint cropL, jint cropT, jint sw, jint sh, jint rotation, jobject bitmap) {
  const uint8_t* Y = (const uint8_t*)(*env)->GetDirectBufferAddress(env, yb);
  const uint8_t* U = (const uint8_t*)(*env)->GetDirectBufferAddress(env, ub);
  const uint8_t* V = (const uint8_t*)(*env)->GetDirectBufferAddress(env, vb);
  if (!Y || !U || !V || sw <= 0 || sh <= 0) return JNI_FALSE;

  AndroidBitmapInfo info;
  uint8_t* out = NULL;
  if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
  if (AndroidBitmap_lockPixels(env, bitmap, (void**)&out) < 0) return JNI_FALSE;

  const int tw = (int)info.width, th = (int)info.height;
  const int rotated = (rotation == 90 || rotation == 270);
  const int rw = rotated ? sh : sw, rh = rotated ? sw : sh;
  // Amostras por eixo dentro da área de cada pixel de saída (1 a 4).
  int nx = (rw + tw - 1) / tw; if (nx < 1) nx = 1; if (nx > 4) nx = 4;
  int ny = (rh + th - 1) / th; if (ny < 1) ny = 1; if (ny > 4) ny = 4;

  for (int y = 0; y < th; ++y) {
    uint8_t* row = out + (size_t)y * info.stride;
    for (int x = 0; x < tw; ++x) {
      int sy_ = 0, su = 0, sv = 0, n = 0;
      for (int j = 0; j < ny; ++j) {
        int ry = (int)(((int64_t)(2 * y + 1) * ny + 2 * j + 1 - ny) * rh / (2LL * th * ny));
        if (ry < 0) ry = 0; if (ry >= rh) ry = rh - 1;
        for (int i = 0; i < nx; ++i) {
          int rx = (int)(((int64_t)(2 * x + 1) * nx + 2 * i + 1 - nx) * rw / (2LL * tw * nx));
          if (rx < 0) rx = 0; if (rx >= rw) rx = rw - 1;
          int sx, sy;
          switch (rotation) {
            case 90:  sx = ry;          sy = sh - 1 - rx; break;
            case 180: sx = sw - 1 - rx; sy = sh - 1 - ry; break;
            case 270: sx = sw - 1 - ry; sy = rx;          break;
            default:  sx = rx;          sy = ry;          break;
          }
          sx += cropL; sy += cropT;
          sy_ += Y[sy * yRow + sx * yPix];
          su += U[(sy >> 1) * uRow + (sx >> 1) * uPix];
          sv += V[(sy >> 1) * vRow + (sx >> 1) * vPix];
          ++n;
        }
      }
      int c = sy_ / n - 16, d = su / n - 128, e = sv / n - 128;
      if (c < 0) c = 0;
      c *= 298;
      uint8_t* p = row + x * 4;
      p[0] = clamp255((c + 409 * e + 128) >> 8);
      p[1] = clamp255((c - 100 * d - 208 * e + 128) >> 8);
      p[2] = clamp255((c + 516 * d + 128) >> 8);
      p[3] = 255;
    }
  }
  AndroidBitmap_unlockPixels(env, bitmap);
  return JNI_TRUE;
}
