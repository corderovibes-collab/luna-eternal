# Continuidad y Reporte de Despliegue — PokeReport / Rotom Dex

> **Fecha:** 30 de septiembre de 2026  
> **Para:** ChatGPT / Siguiente asistente de IA  
> **Estado:** Implementado, corregido (blur removido), publicado en launcher y desplegado en servidor de producción.

---

## 1. Árbol de trabajo y control de versiones

- **Directorio de trabajo:** `C:\Users\JUAN\.codex\worktrees\remediacion-gym-audit\pokereportversionmejorada`
- **Rama:** `remediacion/luna-eternal`
- **Commits relevantes:**
  - `e7971cb5` — `feat(rotom): NPC Rotom Dex tutorial de gimnasios` (Implementación base)
  - `711f73d5` — `fix(rotom): eliminar desenfoque blur de pantalla al reproducir video` (Corrección de desenfoque)
- **Estado de git:** Árbol limpio (`working tree clean`), sincronizado con `origin/remediacion/luna-eternal`.
- **Nota sobre checkout alternativo:** `D:\pokereportversionmejorada` es otro checkout; **NO** copiarlo encima ni trabajar allí a ciegas. El trabajo activo está en el worktree `remediacion-gym-audit`.

---

## 2. Qué se implementó (Sistema Rotom Dex)

Se implementó el NPC interactivo de tutorial de gimnasios:
- **Entidad:** `lunaeternal:rotom_dex`, spawn en la dimensión `lunaeternal:ciudadela` en las coordenadas exactas `(-113.479, 69, 0.500)`, yaw -90 (este).
- **Protecciones completas:** Inmóvil, sin daño, sin empuje, inmune al fuego, sin colisiones de empuje, no abordable, no atable con correa, sin portales, bloqueado el renombrado con name tags.
- **Interacción:** Clic derecho a menos de 6 bloques con cooldown individual de 3 segundos. Envía el paquete de red S2C `lunaeternal:rotom_tutorial`.
- **Modelo y Animaciones:**
  - `arte/rotom/rotom_dex.bbmodel`: 40 cubos, 8 huesos, 6 animaciones (idle, blink, notice, interact, scan, happy).
  - Assets GeckoLib: `assets/lunaeternal/geo/rotom_dex.geo.json`, `assets/lunaeternal/animations/rotom_dex.animation.json`.
  - Texturas con máscaras emisivas: `assets/lunaeternal/textures/entity/rotom_dex_0.png` hasta `_6.png` con sus respectivos `_glowmask.png`.
- **Reproductor de Video:**
  - `RotomVideoScreen.java`: Utiliza WaterMedia 3.0.0.23, descarga bajo demanda hacia `cache/pokereport/cinematicas/rotom-gimnasios-v1.mp4`, valida tamaño y hash SHA-256 antes de reproducir.
  - Soporte de audio atenuado (ducking) del resto del juego, botón de silenciar ("Audio: ON/OFF"), botón "Cerrar", tecla ESC y fade-out al finalizar.

---

## 3. Corrección crítica aplicada: Desenfoque / Blur de la pantalla

### El problema reportado
Al abrirse la cinemática, el video de tutorial se reproducía detrás de un efecto borroso de desenfoque gaussiano (blur) muy agresivo que impedía leer los textos y detalles del video. Sin embargo, los botones ("Audio: ON" y "Cerrar") aparecían perfectamente nítidos.

### Causa raíz técnica
En Minecraft 1.20.5+ y 1.21.1, la implementación vanilla de `Screen.render(DrawContext, int, int, float)` invoca por defecto `this.renderBackground()`.
En estas versiones modernas, `renderBackground()` ejecuta:
1. `this.applyBlur(delta)` (shader de post-procesamiento `menuBackgroundBlurriness` del juego).
2. `this.renderDarkening(context)` (oscurecido del fondo).

En `RotomVideoScreen.java`, al invocarse `super.render(ctx, mx, my, delta)` al final del ciclo de dibujado para que se renderizaran los botones hijos, Minecraft ejecutaba `renderBackground()`, aplicando el shader de desenfoque **encima de todo el framebuffer que ya tenía el video renderizado**. Como los botones se dibujaban después del desenfoque, solo los botones quedaban nítidos.

### Solución implementada
En [`mod/src/client/java/net/pokereport/luna/client/rotom/RotomVideoScreen.java`](mod/src/client/java/net/pokereport/luna/client/rotom/RotomVideoScreen.java):
1. Se sobrescribió `renderBackground()` como método vacío (`no-op`):
   ```java
   @Override
   public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
       // Anulado: Screen.render invoca renderBackground() que en 1.20.5+ aplica
       // el shader de desenfoque (applyBlur) y oscurecido sobre el framebuffer.
       // Al anularlo, el reproductor de video se mantiene 100% nítido en resolución nativa.
   }
   ```
2. Se ajustó el letterboxing a negro 100% opaco: `ctx.fill(0, 0, width, height, 0xFF000000);` (antes tenía `0xFA040C16`, con 2% de transparencia).

---

## 4. Estado de los activos y publicación

### A. Video tutorial en GitHub Releases
- **Repositorio:** `corderovibes-collab/luna-eternal-pack`
- **Release:** `pack-assets`
- **Archivo:** `rotom-gimnasios-v1.mp4`
- **Tamaño:** 38,198,649 bytes
- **SHA-256:** `906a33cf2aab7743601cff34c27742d708195e1510442e0090bc07352320167f`
- **URL pública directa:**  
  `https://github.com/corderovibes-collab/luna-eternal-pack/releases/download/pack-assets/rotom-gimnasios-v1.mp4`

### B. Manifiesto del Launcher
- **Puntero activo:** Release `pack-manifest` -> `latest.json` apunta a `manifest-84d20227bd.json`
- **JAR del mod en el manifiesto:**
  - Nombre: `lunaeternal-0.1.0-936c2346bd.jar`
  - SHA-1: `936c2346bd7410c6bf3376229a60e7c140e4eb22`
  - Tamaño: 108,197,570 bytes
  - URL de descarga: `https://github.com/corderovibes-collab/luna-eternal-pack/releases/download/pack-assets/lunaeternal-0.1.0-936c2346bd.jar`
- **Herramienta usada:** `tools/publicar_luna_dirigido.py` (actualiza únicamente el JAR de Luna Eternal sin alterar los 159 mods y configuraciones existentes del pack).

---

## 5. Estado del servidor de producción

- **Nodo:** `15.235.16.131` (Pterodactyl Daemon / Docker container `1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd`).
- **JAR instalado en servidor:**
  - Ruta: `/var/lib/pterodactyl/volumes/1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd/mods/lunaeternal-0.1.0.jar`
  - SHA-1 verificado: `936c2346bd7410c6bf3376229a60e7c140e4eb22`
  - Permisos: `pterodactyl:pterodactyl`
- **Comprobación en juego (`/luna autotest`):**
  - Todas las 6 pruebas de Rotom pasan en verde:
    - `✔ Rotom recurso empaquetado: geo/rotom_dex.geo.json`
    - `✔ Rotom recurso empaquetado: animations/rotom_dex.animation.json`
    - `✔ Rotom recurso empaquetado: textures/entity/rotom_dex_0.png`
    - `✔ Rotom recurso empaquetado: textures/entity/rotom_dex_4_glowmask.png`
    - `✔ Rotom video fijado por SHA-256`
    - `✔ Rotom identidad propia registrada`
  *(Nota: Los 2 fallos reportados en autotest corresponden a `campeon_johto` y el icono del tablist, ambos temas preexistentes ajenos a Rotom).*
- **Entidad Rotom Dex en Ciudadela:**
  - Verificada en mundo con query de selector:
    `[Server thread/INFO]: [Not Secure] [Server] ROTOM CONFIRMADO`

---

## 6. Advertencias importantes para continuar el trabajo

1. **Compilación de Java:**
   - **Usar siempre:** `& 'C:\Program Files\Git\bin\bash.exe' -c 'cd mod && bash build.sh ...'`
   - **NO ejecutar `./gradlew` directo** en Windows: existe una variable global en `~/.gradle/gradle.properties` que fuerza Java 17 para otro proyecto. `build.sh` sobrescribe esto forzando JDK 21.

2. **Edición del Modelo Blockbench:**
   - Si el usuario modifica `arte/rotom/rotom_dex.bbmodel` directamente en Blockbench, **NO** ejecutar `tools/build_rotom_assets.py` a ciegas, ya que dicho script fue el generador sintético inicial y sobrescribiría los cambios manuales del artista.

3. **Subidas de JAR al Servidor:**
   - Pterodactyl bloquea / corrompe archivos si se sobrescriben en caliente (`ZipFile invalid LOC header`).
   - Siempre subir primero a una ruta temporal (ej: `/tmp/lunaeternal-0.1.0.jar`), aplicar `chown pterodactyl:pterodactyl`, y mover atómicamente con `mv` al directorio `/mods/` tras detener o reiniciar el contenedor.

4. **Actualizaciones de Launcher:**
   - Para publicar únicamente cambios en el código de Luna Eternal al launcher, usar siempre `python tools/publicar_luna_dirigido.py`. Esto preserva intacto el resto de entradas de Modrinth y configuraciones de `gen_manifest.py`.
