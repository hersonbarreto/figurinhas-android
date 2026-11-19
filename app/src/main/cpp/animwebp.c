// Codificador de WebP animado para as figurinhas (WebPAnimEncoder da libwebp).
// Recebe um quadro por vez, para não guardar o vídeo inteiro na memória.
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/bitmap.h>
#include "webp/encode.h"
#include "webp/mux.h"

typedef struct {
  WebPAnimEncoder* enc;
  WebPConfig config;
  int width, height;
  uint8_t* rgba;  // buffer reaproveitado, sem pré-multiplicação
  int ok;
} Encoder;

JNIEXPORT jlong JNICALL
Java_com_hbfr_figurinhas_render_AnimatedWebP_nativeCreate(JNIEnv* env, jclass cls, jint w, jint h,
                                                          jfloat quality, jint method) {
  Encoder* e = (Encoder*)calloc(1, sizeof(Encoder));
  if (!e) return 0;
  WebPAnimEncoderOptions opts;
  if (!WebPAnimEncoderOptionsInit(&opts) || !WebPConfigInit(&e->config)) { free(e); return 0; }
  opts.anim_params.loop_count = 0;
  opts.anim_params.bgcolor = 0x00000000;
  opts.minimize_size = 0;
  // Poucos quadros-chave: menos tentativas por quadro (mais rápido) e arquivo menor.
  opts.kmin = 29;
  opts.kmax = 30;
  opts.allow_mixed = 0;
  e->config.quality = quality;
  e->config.method = method;
  e->config.alpha_quality = 80;
  e->config.thread_level = 1;
  e->width = w; e->height = h;
  e->rgba = (uint8_t*)malloc((size_t)w * h * 4);
  e->enc = WebPAnimEncoderNew(w, h, &opts);
  e->ok = e->enc != NULL && e->rgba != NULL;
  if (!e->ok) { if (e->enc) WebPAnimEncoderDelete(e->enc); free(e->rgba); free(e); return 0; }
  return (jlong)(intptr_t)e;
}

JNIEXPORT jboolean JNICALL
Java_com_hbfr_figurinhas_render_AnimatedWebP_nativeAdd(JNIEnv* env, jclass cls, jlong handle,
                                                       jobject bitmap, jint timestampMs) {
  Encoder* e = (Encoder*)(intptr_t)handle;
  if (!e || !e->ok) return JNI_FALSE;
  AndroidBitmapInfo info;
  void* px = NULL;
  if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
      (int)info.width != e->width || (int)info.height != e->height) return JNI_FALSE;
  if (AndroidBitmap_lockPixels(env, bitmap, &px) < 0) return JNI_FALSE;
  // Bitmap do Android é pré-multiplicado; a libwebp espera RGBA normal.
  for (int y = 0; y < e->height; ++y) {
    const uint8_t* src = (const uint8_t*)px + (size_t)y * info.stride;
    uint8_t* dst = e->rgba + (size_t)y * e->width * 4;
    for (int x = 0; x < e->width; ++x, src += 4, dst += 4) {
      const uint8_t a = src[3];
      if (a == 255) { dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; }
      else if (a == 0) { dst[0] = dst[1] = dst[2] = 0; }
      else {
        dst[0] = (uint8_t)((src[0] * 255 + a / 2) / a);
        dst[1] = (uint8_t)((src[1] * 255 + a / 2) / a);
        dst[2] = (uint8_t)((src[2] * 255 + a / 2) / a);
      }
      dst[3] = a;
    }
  }
  AndroidBitmap_unlockPixels(env, bitmap);

  WebPPicture pic;
  if (!WebPPictureInit(&pic)) return JNI_FALSE;
  pic.use_argb = 1;
  pic.width = e->width; pic.height = e->height;
  int ok = WebPPictureImportRGBA(&pic, e->rgba, e->width * 4) &&
           WebPAnimEncoderAdd(e->enc, &pic, timestampMs, &e->config);
  WebPPictureFree(&pic);
  if (!ok) e->ok = 0;
  return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_hbfr_figurinhas_render_AnimatedWebP_nativeFinish(JNIEnv* env, jclass cls, jlong handle, jint endMs) {
  Encoder* e = (Encoder*)(intptr_t)handle;
  if (!e || !e->ok) return NULL;
  jbyteArray out = NULL;
  WebPData data;
  WebPDataInit(&data);
  if (WebPAnimEncoderAdd(e->enc, NULL, endMs, NULL) && WebPAnimEncoderAssemble(e->enc, &data)) {
    out = (*env)->NewByteArray(env, (jsize)data.size);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize)data.size, (const jbyte*)data.bytes);
  }
  WebPDataClear(&data);
  return out;
}

JNIEXPORT void JNICALL
Java_com_hbfr_figurinhas_render_AnimatedWebP_nativeRelease(JNIEnv* env, jclass cls, jlong handle) {
  Encoder* e = (Encoder*)(intptr_t)handle;
  if (!e) return;
  if (e->enc) WebPAnimEncoderDelete(e->enc);
  free(e->rgba);
  free(e);
}
