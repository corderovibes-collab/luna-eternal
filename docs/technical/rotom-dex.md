# Rotom Dex — guía de gimnasios

## Purpose / Dependencies

Dispositivo inmóvil en `lunaeternal:ciudadela`, posición `-113.479 69 0.500`,
yaw inicial -90 (este). Fabric 1.21.1, Java 21, GeckoLib 4.9.2 y WaterMedia
3.0.0.23 ya presentes. No añade librerías ni mixins de combate.

## Implementación

- `rotom/RotomTutorial`: registro común, paquete S2C sin parámetros, comandos
  `/luna rotom spawn|reset|tp|testvideo` (OP 4), reconciliación local por segundo.
- `rotom/RotomEntity`: inmóvil, sin metas de movimiento, daño/caída/empuje/agua/
  portales/correas/pasajeros bloqueados. Callback anterior a interacción vanilla
  consume objetos como etiquetas. Mano secundaria no activa; cooldown individual
  de 3 segundos y distancia máxima de interacción de 6 bloques.
- Tag `luna_tutorial_rotom_dex` y UUID determinista identifican únicamente este
  dispositivo. Las demás entidades Rotom sin ese tag no son deduplicadas.
- La comprobación solo corre si el chunk ya está cargado; no fuerza su carga.
  Los comandos administrativos explícitos sí pueden cargar ese chunk.
- `client/rotom/RotomClient`: renderer GeckoLib con máscaras emissive, expresiones
  sincronizadas y parpadeo desfasado por entidad.
- `client/rotom/RotomVideoScreen`: caché validada por tamaño/SHA-256, cancelación,
  descarga a temporal único, reproducción WaterMedia local con audio, letterbox,
  cerrar/ESC/mute y fade al terminar. Redimensionar no crea otra descarga.
  Desconexión/cambio de dimensión/retirada de pantalla liberan reproductor.
  Atenuación de sonido mediante SoundManager sin escribir opciones; restauración
  de los niveles de opciones vigentes al salir.

## Recursos

`arte/rotom/rotom_dex.bbmodel` conserva 40 cubos, 8 huesos y 6 animaciones
editables: idle, blink, notice, interact, scan y happy. `rotom_dex.png` es el
atlas de 128×128. El parpadeo en juego usa expresión de textura; la animación
blink queda disponible para edición. Siete vistas en `arte/rotom/vistas.png`.

Los archivos runtime están en `assets/lunaeternal/geo`, `animations` y
`textures/entity/rotom_dex*`. `tools/build_rotom_assets.py` reconstruye recursos
desde geometría; `tools/render_rotom.py` renderiza los JSON exportados con su UV.
Los renders son vistas ortográficas del modelo, no capturas de Minecraft.

## Video

Original del usuario: `C:/Users/JUAN/Downloads/tutorialgimnasios.mp4`, intacto.
MP4 H.264 Main 1920×1080 60 FPS, AAC 44.1 kHz estéreo; 53,15 s, 107.756.091 bytes,
16,22 Mbps. Derivado: `build/video/rotom-gimnasios-v1.mp4`, H.264 High 1080p30,
CRF 21, maxrate 6M, buffer 12M, AAC 160k 48 kHz, faststart. 38.198.649 bytes.

SHA-256: `906a33cf2aab7743601cff34c27742d708195e1510442e0090bc07352320167f`.
URL prevista: release pack-assets de luna-eternal-pack, `rotom-gimnasios-v1.mp4`.
La URL debe estar publicada y comprobada antes de desplegar el JAR.

## Verificación y límites

Compilación cliente/servidor correcta. Dos pruebas JUnit validan jerarquía,
animaciones, equivalencia del número de cubos con Blockbench, decodificación de
texturas/máscaras y expresiones distintas. FFmpeg decodifica el derivado completo
sin errores. `/luna autotest` incorpora recursos y registro de Rotom.

Pendientes de ejecución real: orientación en Minecraft, apariencia con/sin
shaders, dos jugadores, sonido sincronizado, memoria/FPS tras reproducciones,
persistencia tras reinicio y carga/descarga de chunk. No se presentan como
verificados a partir de la compilación o los renders ortográficos.

La referencia es una adaptación voxel, no una copia píxel por píxel de la
ilustración. Variantes shiny/premium no se activan: la petición exige el Rotom
rojo tutorial; el atlas contiene las expresiones necesarias.
