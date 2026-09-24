# Cinemática del Profesor Oak — implementación y recuperación

**Estado:** desplegada y operativa  
**Fecha de cierre:** 2026-09-21  
**Alcance:** reproducción dentro de Minecraft, sin abrir navegador externo.

## Resultado vigente

El libro de bienvenida de la Ciudadela ejecuta `/luna videooak`. El servidor
envía el payload `lunaeternal:cinematica` y el cliente oficial abre una pantalla
propia respaldada por WaterMedia. El video se descarga por completo, se valida
con SHA-256 y solo entonces comienza la reproducción.

Esto evita que variaciones de GitHub/CDN congelen una película que ya está en
curso. Las reproducciones posteriores usan la copia local verificada.

## Artefacto de video canónico

| Campo | Valor |
|---|---|
| Archivo | `profesor-oak-intro-stream-v2.mp4` |
| Resolución / FPS | 1920×1080, 60 FPS |
| Tamaño | 15,157,601 bytes |
| Bitrate aproximado | 4.23 Mbps |
| SHA-256 | `eca79c6a08a26c9b5730f0bf772af1278e17b81d942090d890c1f1f18ffd5c9a` |
| SHA-1 | `8c77e8338793803ce695f38b7219761e39602e88` |
| Release | `https://github.com/corderovibes-collab/luna-eternal-pack/releases/download/pack-assets/profesor-oak-intro-stream-v2-eca79c6a08a2.mp4` |
| Copia de trabajo | `build/video/profesor-oak-intro-stream-v2.mp4` |

El archivo anterior sustituyó al original de unos 60 MB y 16.8 Mbps. La
reducción se hizo sin bajar de 1080p60, usando H.264, GOP de 120, `faststart` y
audio AAC a 160 kbit/s.

Comando de referencia para reconstruirlo:

```powershell
ffmpeg -i profesoroakintro.mp4 -c:v libx264 -preset medium -crf 21 `
  -maxrate 8M -bufsize 16M -profile:v high -level 4.2 -r 60 `
  -g 120 -keyint_min 60 -sc_threshold 0 -movflags +faststart `
  -c:a aac -b:a 160k -ar 48000 profesor-oak-intro-stream-v2.mp4
```

No se debe reemplazar el archivo de la release bajo el mismo nombre. Cualquier
nueva edición debe publicarse con nombre y checksum nuevos y actualizar juntos
`BienvenidaOak.VIDEO` y `CinematicaScreen.OAK_SHA256`.

## Componentes de código

- `mod/src/main/java/net/pokereport/luna/puerta/BienvenidaOak.java`
  contiene la URL canónica, crea el libro y envía la película.
- `mod/src/main/java/net/pokereport/luna/net/Red.java`
  define y registra el payload S2C `Cinematica`.
- `mod/src/client/java/net/pokereport/luna/client/LunaCliente.java`
  recibe el payload y abre la pantalla.
- `mod/src/client/java/net/pokereport/luna/client/CinematicaScreen.java`
  descarga, verifica, almacena, reproduce y libera WaterMedia.
- `mod/build.gradle`
  incorpora la API de WaterMedia al classpath de compilación.
- `tools/gen_modpack.py`
  incluye WaterMedia y WaterMedia Binaries en el pack del jugador.

La caché del jugador se ubica en:

```text
<directorio de Minecraft>/cache/pokereport/cinematicas/profesor-oak-intro-v2.mp4
```

La descarga temporal usa la extensión `.part`. La copia solo se promueve al
nombre definitivo si responde HTTP 2xx y su SHA-256 coincide. Si la caché está
corrupta, se vuelve a descargar automáticamente.

## Dependencias fijadas

| Dependencia | Versión / archivo | Tamaño | SHA-1 |
|---|---|---:|---|
| WaterMedia | `watermedia-3.0.0.23.jar` | 3,625,444 | `a644ab079cc9ed19a62b181e0252be5a9a422166` |
| WaterMedia Binaries | `watermedia_binaries-3.0.0.6.jar` | 143,725,808 | `8f132fc2fec0fe91a9a085c11088f0773da187fc` |

Ambas dependencias son de cliente. No deben eliminarse del manifiesto aunque el
mod Luna compile correctamente: sin los binarios nativos el reproductor no
puede decodificar el MP4.

## Despliegue certificado

| Campo | Valor |
|---|---|
| JAR Luna desplegado | `lunaeternal-0.1.0.jar` |
| Tamaño | 68,153,278 bytes |
| SHA-1 | `888ec47f7ab508ff7bff591f1ff498ecc678edd8` |
| Manifiesto del launcher | `build/pack/manifest-481ec75f23.json` |
| Identificador de manifiesto | `481ec75f23` |
| Validación | `gradlew test build` correcto |
| Servidor | reinicio limpio y mensaje `Done` confirmado |

La instancia local de Luna Eternal también recibió el mismo JAR. El jugador
debe cerrar Minecraft por completo y abrirlo desde el launcher para cargarlo.
En la primera reproducción es normal ver “Descargando y preparando video…”;
después se usa la caché.

## Verificación rápida

1. Confirmar que el launcher entrega Luna con SHA-1
   `888ec47f7ab508ff7bff591f1ff498ecc678edd8` y las dos dependencias.
2. Entrar con el cliente oficial y ejecutar `/luna videooak`.
3. En la primera ejecución, esperar la preparación del archivo.
4. Confirmar imagen, audio, 60 FPS y cierre automático alrededor de los 29 s.
5. Repetir: debe comenzar desde la caché sin nueva descarga.
6. Si falla, borrar únicamente el archivo de caché de la cinemática y volver a
   probar; no es necesario borrar la instancia completa.

## Recuperación y rollback

El paquete local `build/backups/cinematica-oak-2026-09-21.zip` conserva los
fuentes específicos, el manifiesto, el JAR desplegado, el MP4 optimizado y un
archivo de checksums. Para recuperar, extraerlo en una carpeta temporal,
comparar los hashes y restaurar únicamente los archivos enumerados.

El artefacto previo identificado durante la publicación fue `da92fce1c5`; no
debe restaurarse salvo rollback deliberado, porque no incluye la descarga local
anti-tirones.

## Restricciones de mantenimiento

- No volver a reproducir el MP4 directamente desde HTTP mientras corre.
- No quitar la verificación SHA-256 ni el uso de `.part`.
- No cambiar URL, nombre de caché o checksum por separado.
- No incluir tokens de GitHub, credenciales SSH ni secretos en documentación.
- No confundir Flashback (producción/edición de escenas) con WaterMedia
  (reproducción final dentro del cliente).

