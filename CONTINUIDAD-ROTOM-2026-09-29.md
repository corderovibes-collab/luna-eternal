# Continuidad de trabajo — PokeReport / Rotom Dex

Estado guardado el 29 de septiembre de 2026. Leer este documento antes de editar.

## Árbol y rama correctos

- Trabajar en `C:\Users\JUAN\.codex\worktrees\remediacion-gym-audit\pokereportversionmejorada`.
- Rama: `remediacion/luna-eternal`.
- HEAD comprobado: `962dfafe27aff81fd318026efb0b60929d44352d`.
- Los cambios de Rotom están guardados en disco, SIN commit. Muchos archivos son nuevos/no rastreados: `git diff` por sí solo NO los incluye.
- `D:\pokereportversionmejorada` es otro checkout; no copiarlo encima de este ni cambiar de rama a ciegas. El trabajo reciente de Rotom NO está allí.
- Antes de continuar: leer COLABORAR.md y las instrucciones aplicables; ejecutar git status, git branch --show-current y revisar diferencias y archivos no rastreados. Preservar cambios de otras IA. No reset/clean/restore destructivos.

## Alcance y petición actual

NPC tutorial Rotom Dex rojo, fijo en Ciudadela pero animado. Solo interacción mediante clic derecho: sin daño, empujones, correas, pasajeros, portales ni renombrado con etiquetas. Reproduce tutorial de gimnasios individualmente usando WaterMedia existente. No toca la cinemática de Oak ni introduce librerías nuevas.

Petición original completa: `C:\Users\JUAN\.codex\attachments\b5644aac-20f2-472e-85bd-13b20161cec5\Texto pegado.txt`.
Referencia visual: `C:\Users\JUAN\AppData\Local\Temp\codex-clipboard-a652ca59-8811-45c4-99bc-41c6b69bbe16.png`.
Video original intacto: `C:\Users\JUAN\Downloads\tutorialgimnasios.mp4`.

## Mapa de archivos (rutas relativas al árbol correcto)

- `arte/rotom/rotom_dex.bbmodel`: modelo editable en Blockbench, textura incorporada, 40 cubos, 8 huesos, 6 animaciones.
- `arte/rotom/rotom_dex.png`: atlas; `arte/rotom/vistas.png` y vistas individuales: renders del modelo, NO capturas del juego.
- `mod/src/main/java/net/pokereport/luna/rotom/RotomEntity.java`: entidad protegida, anclaje, expresiones y controladores de animación.
- `mod/src/main/java/net/pokereport/luna/rotom/RotomTutorial.java`: registro, paquete S2C, comandos, spawn y reconciliación por tag/UUID.
- `mod/src/client/java/net/pokereport/luna/client/rotom/RotomClient.java`: renderer, capas luminosas y recepción del paquete.
- `mod/src/client/java/net/pokereport/luna/client/rotom/RotomVideoScreen.java`: descarga/caché validada, reproducción, mute/cierre, cancelación y liberación.
- `mod/src/main/resources/assets/lunaeternal/geo/rotom_dex.geo.json`.
- `mod/src/main/resources/assets/lunaeternal/animations/rotom_dex.animation.json`.
- `mod/src/main/resources/assets/lunaeternal/textures/entity/rotom_dex*.png`: atlas, expresiones y glowmasks.
- `mod/src/test/java/net/pokereport/luna/rotom/RotomAssetsTest.java`: dos pruebas de contratos de assets.
- Registros modificados: `mod/src/main/java/net/pokereport/luna/net/Red.java`, `mod/src/main/java/net/pokereport/luna/LunaEternal.java`, `mod/src/client/java/net/pokereport/luna/client/LunaCliente.java`.
- `mod/src/main/java/net/pokereport/luna/test/AutoTest.java`: invariantes Rotom añadidas.
- `tools/build_rotom_assets.py`: generador de modelo, texturas y animaciones. NO ejecutarlo después de editar manualmente el modelo sin reconciliar cambios: sobrescribe sus salidas.
- `tools/render_rotom.py`: renderizador ortográfico del modelo exportado.
- `docs/technical/rotom-dex.md`: documentación técnica detallada.

## Configuración implementada

- Entidad `lunaeternal:rotom_dex`; dimensión `lunaeternal:ciudadela`.
- Posición `-113.479 69 0.500`, orientación inicial este (yaw -90).
- Tag `luna_tutorial_rotom_dex`; UUID determinista.
- `/luna rotom spawn`, `/luna rotom reset`, `/luna rotom tp`, `/luna rotom testvideo`: OP nivel 4.
- Comprobación de existencia cada segundo solo en chunk cargado; comandos explícitos sí cargan el chunk.
- Cooldown de interacción individual de 3 s; protección Puerta; apertura de video solo para el jugador que interactúa.
- Animaciones: idle/flotación, blink, notice, interact, scan, happy. Parpadeo runtime por textura; blink también disponible en Blockbench.

## Compilación y pruebas realmente realizadas

Java 21, Fabric MC 1.21.1. Dependencias reutilizadas: GeckoLib 4.9.2, WaterMedia 3.0.0.23. Cobblemon del proyecto 1.8.0; comprobar instalación real antes de afirmar versión del servidor.

Desde el árbol correcto en PowerShell:

```powershell
& 'C:\Program Files\Git\bin\bash.exe' -c 'cd mod && bash build.sh compileClientJava'
& 'C:\Program Files\Git\bin\bash.exe' -c 'cd mod && bash build.sh test --tests net.pokereport.luna.rotom.RotomAssetsTest'
& 'C:\Program Files\Git\bin\bash.exe' -c 'cd mod && bash build.sh build -x test'
```

Los tres terminaron correctamente. El último build NO ejecuta tests; las dos pruebas de Rotom se ejecutaron aparte. JAR: `mod/build/libs/lunaeternal-0.1.0.jar`. No afirmar que toda la suite pasa: existe antecedente de fallo no relacionado en CrianzaConfigTest que debe verificarse por separado.

El video convertido fue decodificado completo por FFmpeg sin errores. NO se ha probado todavía en un cliente Minecraft ni se ejecutó `/luna autotest` en el servidor para esta versión.

## Video y publicación: PENDIENTES

Derivado local: `build/video/rotom-gimnasios-v1.mp4`, 1080p30 H.264 High, AAC, faststart; 38.198.649 bytes.
SHA256: `906a33cf2aab7743601cff34c27742d708195e1510442e0090bc07352320167f`.
URL prevista en código: `https://github.com/corderovibes-collab/luna-eternal-pack/releases/download/pack-assets/rotom-gimnasios-v1.mp4`.

NO se ha subido este video ni publicado este JAR al launcher ni desplegado Rotom al servidor en este trabajo. No confundir archivos compilados con actualización instalada. Verificar URL y hash antes de despliegue. Coordinar cliente/servidor: se añade un registro de entidad nuevo.

Herramienta existente para publicación dirigida: `tools/publicar_luna_dirigido.py`; inspeccionarla antes de usar. Manifest público histórico: release `pack-manifest/latest.json`, no la carpeta `public` del repositorio. Confirmar estado remoto actual.

## Próximos pasos y puntos de revisión

1. El usuario va a abrir el modelo en Blockbench. Preservar sus posibles cambios antes de regenerar assets.
2. Revisar implementación completa y límites: carga asíncrona de entidades/duplicados; orientación; expresión attentive comparte apariencia de idle y no parpadea mientras FACE=3; manejo de excepciones del render nativo.
3. Validar aparición, anclaje, inmunidad y clic derecho en Minecraft; dos jugadores simultáneos; audio, ESC, mute, resize, desconexión/cambio de dimensión; reproducción repetida y memoria/FPS; persistencia tras reinicio/chunks; shaders.
4. Publicar video y verificar descarga/hash. Preparar despliegue con copia recuperable y comprobaciones. Nunca sobrescribir un JAR cargado: staging separado, parada controlada y sustitución segura.
5. Ejecutar autotest y revisar logs antes de declarar terminado. No garantizar ausencia global de bugs basándose solo en compilación.

## Trabajo histórico del proyecto

La conversación contiene gimnasios, PokéPad, eventos, Poképaradas, lobby y cambios de mods anteriores. Este documento NO certifica su estado actual ni que todos estén desplegados. Para continuar esos sistemas, inspeccionar código, historial, documentación y servidor vigente. El HEAD anterior a Rotom es el identificador indicado arriba; no revertirlo por usar una copia vieja.

## Coordinación con otra IA

Leer este archivo y `docs/technical/rotom-dex.md`. Trabajar sobre este árbol o crear un aislamiento preservando también archivos no rastreados. No editar simultáneamente los mismos archivos. No se ha hecho commit/push ni restart para guardar esta continuidad; el guardado es local.
