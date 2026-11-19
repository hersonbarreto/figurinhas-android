# Figurinhas (Android)

App Android para criar figurinhas do WhatsApp sem borda branca. Versão Android do app iOS (repositório figurinhas-ios).

- Kotlin + Jetpack Compose, Material 3 com as cores da marca (`ui/theme`, vindo de design/android). Sem blur nem transparência: o app precisa ser leve em celular fraco.
- minSdk 28 (Android 9), targetSdk 35. Pacote `com.hbfr.figurinhas`.
- WhatsApp: integração oficial do Android (github.com/WhatsApp/stickers). `provider/StickerContentProvider` expõe os pacotes com 3+ figurinhas; `data/WhatsAppSender` abre o WhatsApp com `ENABLE_STICKER_PACK`. `image_data_version` muda a cada alteração do pacote, então reenviar atualiza o pacote em vez de duplicar.
- Figurinhas e pacotes ficam em `filesDir/store` (`index.json`, `packs.json`, `stickers/*.webp`, `trays/*.png`), mesmo modelo do iOS.
- `render/StickerRenderer`: desenha a figurinha 512×512 (a prévia do editor usa a mesma função) e grava WebP ≤100 KB. Todo o processamento roda fora da thread principal; a foto é lida com no máximo 1600 px.

## Compilar

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Precisa do Android SDK (35) em `local.properties` (`sdk.dir=...`). O release ainda assina com a chave de debug, só para testes.

## Etapas
1. ✅ Esqueleto, editor de estática, envio ao WhatsApp
2. ✅ Animadas (libwebp nativa em app/src/main/cpp), vídeo (MediaCodec) e Trecho
3. ✅ Recorte de fundo (ML Kit), fundo liso, corte livre, colar, links e compartilhar
4. ✅ Pro (Google Play Billing, R$ 19,90), tela Sobre
5. Ficha na Play Store e teste fechado
