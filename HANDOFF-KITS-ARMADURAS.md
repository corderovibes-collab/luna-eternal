# HANDOFF — KITS EXCLUSIVOS Y ARMADURAS GECKOLIB

Documento de continuidad para otra IA. Describe el estado real al terminar el
trabajo del 11 de septiembre de 2026. No contiene credenciales ni claves.

## PROMPT PARA CONTINUAR

Trabaja como desarrollador senior de Minecraft Fabric 1.21.1, GeckoLib 4 y
Blockbench en el proyecto Luna Eternal. Continúa exclusivamente desde este
worktree:

`D:\pokereportversionmejorada\.claude\worktrees\armaduras-blockbench-server-023b12`

La rama es `claude/pase-batalla-pokepad-2fbdc9`. No trabajes desde la copia
antigua `D:\pokereportversionmejorada`, porque puede contener código atrasado.

Antes de modificar algo, lee este documento completo y revisa `git status` en
el repositorio principal y en `build/pack-repo`. Conserva los modelos
Blockbench originales sin sobrescribirlos. El usuario pidió gastar pocos
tokens y tiempo en pruebas: usa compilación y verificaciones puntuales, y solo
amplía las pruebas si aparece un fallo concreto.

El objetivo vigente es que los kits Magikarp, Pikachu y Eeveelution funcionen
como armaduras reales separadas en casco, pechera, pantalones y botas, se
anclen al jugador sin retraso, tengan texturas correctas, flipbooks visibles y
previsualizaciones bien encuadradas en inventario y PokéPad.

## ESTADO ACTUAL

Todo lo descrito aquí está guardado en commits. El repositorio principal y el
repositorio de distribución estaban limpios al cerrar esta entrega.

- Servidor Fabric 1.21.1 en Pterodactyl: id `7dc30799`.
- Último arranque comprobado: estado `running`.
- Registro comprobado: `Kits: 10 cargados (0 objetos omitidos por no existir)`.
- Registro comprobado: `Done (23.673s)!`.
- Manifiesto público vigente: `manifest-3b50417135.json`.
- SHA-1 del manifiesto: `3b5041713577c3fe9decaebcb8f2aeb6b60fa0b5`.
- Tamaño del manifiesto: `61326` bytes.
- El puntero público `latest.json` ya apunta a ese manifiesto.

Artefactos desplegados y distribuidos:

| Archivo lógico | Tamaño | SHA-1 |
| --- | ---: | --- |
| `lunaeternal-0.1.0.jar` | 53,836,980 | `d8a1e0318d48bae34b629502f000f938270400b5` |
| `pikachu-animated-armor-1.0.0.jar` | 169,046 | `93157aaf3da54a542170f52a3d7cbc9bf865ff99` |
| `magikarp-animated-armor-1.0.0.jar` | 203,860 | `764c404aab8835e7d739ee5ab55ba1a6f81016d3` |
| `eeveelution-armor-1.0.0.jar` | 317,096 | `608b119faee0c77c3b715783d2026a67677801de` |

Las copias nuevas también están en la instancia local del cliente:

`C:\Users\JUAN\AppData\Roaming\LunaEternal\instances\Luna Eternal\minecraft\mods`

El jugador debe cerrar completamente Minecraft y volverlo a abrir para cargar
los JAR nuevos. Recargar recursos dentro del juego no sustituye el reinicio.

## CAMBIOS GUARDADOS

Commits principales, del más reciente al más antiguo:

- `f518bfd` — centra las piezas de Magikarp y Eeveelution en GUI, mano, suelo y marco fijo.
- `b82e90b` — oculta cabeza y gorro vanilla debajo de los cascos GeckoLib.
- `ccd37af` — corrige el anclaje del casco Pikachu y genera fotogramas distintos.
- `2ffabf1` — añade el refmap necesario para el mixin cliente.
- `44cc26e` — corrige anclajes derecha/izquierda de Magikarp y Eeveelution.
- `c921df5` — sincroniza las escalas GeckoLib y corrige los flipbooks iniciales.
- `51531b8` — normaliza la jerarquía de huesos GeckoLib.
- `b598ab6` — evita previsualizaciones inválidas en kits no compatibles.

Commits correspondientes en `build/pack-repo`:

- `357fc28` — manifiesto actual y encuadre de ítems de armadura.
- `84620ec` — cobertura de la cabeza vanilla.
- `8771de4` — casco y flipbook Pikachu.
- `d5fb148` — refmap del mixin cliente.
- `3bad9f7` — corrección de lados.
- `a2c5768` — anclajes y flipbooks anteriores.

Archivos centrales:

- `tools/build_pikachu_armor_assets.py`: conversión reproducible de los cuatro
  BBModel de Pikachu a GeoJSON, atlas, flipbook, animación e ítems.
- `tools/normalize_custom_armor_sides.py`: normalización de anclajes derechos e
  izquierdos sin copiar geometría entre lados.
- `mod/src/client/java/net/pokereport/luna/client/mixin/HumanoidModelArmorCoverageMixin.java`:
  oculta cabeza/gorro, torso o piernas vanilla cuando corresponda.
- `mod/src/main/resources/lunaeternal.client.mixins.json`: declara el refmap
  `client-lunaeternal-refmap.json`.
- `addons/*-armor/src/main/java/.../*ArmorRenderer.java`: sincroniza escala de
  cabeza, cuerpo, brazos, piernas y botas con `BipedEntityModel`.
- `mod/src/client/java/net/pokereport/luna/client/pokepad/KitsScreen.java`:
  previsualiza el jugador con las cuatro piezas reales equipadas temporalmente.

## FUENTES DE LOS MODELOS

No sobrescribir estas fuentes:

- Magikarp: `D:\blockbench\kit_exclusivos\magikar\Traje magikarp (1).bbmodel`
- Eeveelution: `D:\blockbench\kit_exclusivos\evee\eevelution.bbmodel`
- Pikachu: `D:\blockbench\kit_exclusivos\pikachu\armadura\pikachu skin.bbmodel`
- Piezas Pikachu usadas por el generador:
  `D:\blockbench\kit_exclusivos\pikachu\armadura\pikachu_complete_animated_set_FINAL\armor`

El JAR `C:\Users\JUAN\Downloads\diosesmon-1.7.9.jar` se utilizó únicamente
como referencia técnica. No reinstalarlo, copiarlo ni sustituir los mods
actuales con él. Su patrón útil fue `GeoArmorRenderer`, el seguimiento de
escala del `BipedEntityModel` y la cobertura de partes vanilla.

## CORRECCIONES TÉCNICAS IMPORTANTES

### Anclaje de la armadura

Los renderers de las tres armaduras llaman primero a las transformaciones base
de GeckoLib y después copian `xScale`, `yScale` y `zScale` desde cada parte
vanilla a su `GeoBone`. Esto evita que el modelo quede retrasado o se despegue
al caminar.

Para el casco Pikachu, los huesos `head` y `armorHead` usan el pivote estándar
`[0, 24, 0]`. El modelo anterior conservaba un desplazamiento de plantilla de
entidad. Además, el mixin ahora oculta `model.head` y `model.hat` al llevar un
casco de los namespaces `magikarparmor`, `pikachuarmor` o `eeveelution`.

### Lados correctos

En el contrato Minecraft/GeckoLib:

- `rightArm` y `rightLeg` usan X negativa.
- `leftArm` y `leftLeg` usan X positiva.

Se corrigieron los nombres y padres de los anclajes. No se reflejó ni duplicó
la geometría artística.

### Flipbook Pikachu

El fallo no estaba en el `.mcmeta`: las ocho celdas de las texturas originales
eran idénticas. El generador aplica ahora un pulso moderado solo a amarillos y
cianes de la paleta. El primer fotograma conserva exactamente el arte fuente.

- Hay 8 fotogramas verticales por pieza.
- Los siete pares de fotogramas consecutivos presentan diferencias reales.
- El bucle es ping-pong: `0,1,2,3,4,5,6,7,6,5,4,3,2,1`.
- No usa interpolación ni degradados suaves.
- Los cuatro PNG tienen su `.png.mcmeta` dentro del JAR.
- GeckoLib actualiza `AnimatableTexture` durante el render de armadura e ítem.

### Presentación en inventario

Los doce modelos de ítem de las tres armaduras siguen usando
`"parent": "builtin/entity"`, como requiere el renderer GeckoLib, pero ahora
incluyen transformaciones explícitas para `gui`, `ground`, `fixed`, primera
persona y tercera persona. Esto evita modelos enormes, cortados o desplazados
dentro de los slots.

## PUBLICACIÓN Y DESPLIEGUE

Herramientas y rutas actuales:

- Python: `D:\pokereportversionmejorada\.toolchain\python\python.exe`
- Java 21: `D:\pokereportversionmejorada\.toolchain\jdk21\jdk-21.0.12.1+1`
- Despliegue: `tools/desplegar.py`
- Repositorio del pack: `build/pack-repo`
- Release de archivos: `pack-assets`
- Release del puntero: `pack-manifest`

Los secretos de Pterodactyl están en el `.env` ignorado por Git. Nunca copiar
la clave al código, a un commit, al chat ni a otro documento.

Para una actualización futura:

1. Modificar solo el addon o el mod afectado.
2. Ejecutar una compilación dirigida con `gradlew.bat build --no-daemon`.
3. Calcular tamaño y SHA-1 del JAR producido.
4. Mantener idénticos los JAR de cliente, servidor y manifiesto.
5. Subir el archivo con nombre versionado por hash al release `pack-assets`.
6. Crear un manifiesto inmutable nuevo y actualizar `latest.json`.
7. Usar un salto de línea real al final del JSON. No escribir los caracteres
   literales `\` y `n`; eso provoca `JSONDecodeError: Extra data`.
8. Desplegar con `tools/desplegar.py <mod|pikachu|magikarp|eeveelution> --reiniciar`.
9. Comprobar estado `running`, carga de 10 kits y la línea `Done`.
10. Hacer commit tanto en el repositorio principal como en `build/pack-repo`.

Mantener paridad cliente/servidor es obligatorio. Un cliente con registros o
mods distintos puede desconectarse con registros desconocidos o con un error
al decodificar `minecraft:custom_payload`.

## VERIFICACIÓN YA REALIZADA

Se hicieron verificaciones breves, como pidió el usuario:

- Compilación correcta del mod principal y de los tres addons.
- Integridad ZIP correcta del JAR Pikachu.
- Cuatro PNG y cuatro `.png.mcmeta` dentro del JAR Pikachu.
- Diferencias reales en los ocho fotogramas de cada textura Pikachu.
- Pivotes `head` y `armorHead` de Pikachu en `[0,24,0]`.
- Los doce modelos de ítem contienen `builtin/entity` y transformación `gui`.
- JAR local, servidor y manifiesto comparados por tamaño y SHA-1.
- Servidor reiniciado, `running`, con diez kits cargados.

No se ejecutó una batería amplia de tests ni se afirmó una validación visual
final dentro del juego.

## RECOMENDACIONES PARA LA SIGUIENTE IA

1. La primera prueba debe ser visual y corta después de reiniciar totalmente el
   cliente: casco Pikachu de frente, espalda y perfil; caminar/correr; abrir
   inventario; abrir PokéPad > Kits; observar el pulso al menos cinco segundos.
2. Si el casco aún está desplazado, corregir la transformación de la geometría
   hija con mediciones de Blockbench. No volver a cambiar a ciegas el contrato
   de huesos `head`/`armorHead` ni restaurar el antiguo Z `-6`.
3. Si el pulso resulta demasiado sutil, modificar `PULSE` en
   `tools/build_pikachu_armor_assets.py`. Mantener el cambio localizado en
   amarillos/cianes y evitar parpadeos fuertes en toda la textura.
4. Si se desea una animación más elaborada, diseñar cada fotograma como pixel
   art: desplazar líneas eléctricas o highlights en vez de aumentar brillo de
   toda la paleta. Conservar 8 cuadros y el bucle ping-pong limpio.
5. No confundir el flipbook PNG con la animación de huesos GeckoLib. El primero
   usa `.png.mcmeta`; la segunda usa `.animation.json` y controladores Java.
6. Revisar cada lado por separado. Simetría de anclajes no implica copiar
   piezas artísticas ni borrar asimetrías intencionales.
7. MCreator sí puede utilizarse como interfaz de autoría para importar y
   organizar armaduras, herramientas, modelos y animaciones. Como el servidor
   actual es Fabric 1.21.1, no se debe sustituir directamente el addon Fabric
   por un JAR NeoForge generado por MCreator. La ruta segura es usar un
   workspace MCreator de autoría y trasladar sus recursos al addon Fabric, o
   verificar primero una combinación exacta de MCreator, generador Fabric y
   plugin GeckoLib que soporte armadura animada en 1.21.1.
8. No regenerar todos los recursos si cambia una sola pieza. Reconstruir y
   publicar únicamente los JAR afectados.
9. Los avisos `No data fixer registered` y el mensaje existente
   `Tried to load invalid item: 'No key id in MapLike[{}]'` aparecían antes y
   no bloquearon el arranque. Investigar el segundo por separado si afecta un
   ítem real, sin atribuirlo automáticamente a estas armaduras.
10. Consultar la guía oficial de ítems GeckoLib antes de cambiar el sistema de
    render: https://github.com/bernie-g/geckolib/wiki/Geckolib-Items-%28Geckolib4%29

## RUTA MCREATOR RECOMENDADA

En el PC Windows del usuario ya está instalado MCreator `2026.2.33518` en
`C:\Program Files\Pylo\MCreator\mcreator.exe`. También existe el workspace
`C:\Users\JUAN\MCreatorWorkspaces\armaduraspokereport`, configurado con el
generador `fabric-1.21.1` y todavía sin elementos.

En `C:\Users\JUAN\.mcreator\plugins` están instalados los generadores Fabric
para 1.21.8, 26.1.2 y un `Plugin.Fabric.1.21.1.zip`. La inspección del último
confirma que solo aporta definiciones de bloque, ítem y pestaña; no contiene
definiciones de armadura, GUI ni código. Por ello sirve para herramientas e
ítems básicos, pero no expone por sí solo armadura GeckoLib avanzada en la UI.

MCreator 2024.4 soporta oficialmente NeoForge 1.21.1. La versión final del
plugin Nerdy's GeckoLib para esa edición añade elementos de armadura e ítems
animados. Esta combinación es útil para diseñar y configurar los elementos en
una interfaz gráfica, pero su JAR de salida es NeoForge y no puede colocarse
directamente en este servidor Fabric.

Para mantener Fabric 1.21.1:

1. Crear una copia de autoría en MCreator 2024.4.
2. Instalar el plugin GeckoLib compatible con MCreator 2024.4.
3. Importar desde Blockbench el modelo GeckoLib de armadura, su textura y su
   archivo de animación.
4. Crear cuatro elementos de armadura y cada herramienta desde la interfaz.
5. Usar el workspace para editar propiedades, nombres, iconos y animaciones.
6. Llevar los `.geo.json`, `.animation.json`, PNG, `.png.mcmeta` y modelos de
   ítem resultantes a los addons Fabric actuales.
7. Conservar las clases Java Fabric existentes para registro y render, o portar
   de forma explícita el código generado; nunca copiar clases NeoForge sin
   adaptación.

Existe un generador Fabric comunitario para MCreator, pero la versión de
Minecraft, la versión de MCreator y las funciones de armadura animada deben
coincidir exactamente. No usar el antiguo generador experimental 1.21.1 para
2024.2: su publicación declaraba soporte básico de ítems y recetas, insuficiente
para estas armaduras avanzadas.

## CONDICIÓN DE ACEPTACIÓN PENDIENTE

La entrega técnica está compilada, publicada y desplegada. La única validación
pendiente es visual dentro del cliente reiniciado. Se considera terminada
cuando:

- el casco Pikachu cubra correctamente la cabeza sin mostrar la skin vanilla;
- las cuatro piezas sigan el cuerpo sin retraso visible;
- los ítems estén centrados y completos dentro de sus slots;
- el pulso de textura cambie de forma visible, suave y continua;
- Magikarp y Eeveelution conserven su apariencia y anclaje actuales.
