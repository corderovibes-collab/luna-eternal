# El pack de cliente

## Purpose

Cómo entra un jugador. Qué necesita instalar y por qué.

## Dependencies

- [`../world/worlds.md`](../world/worlds.md) §7

## Current Status

**Generados y listos.** Son **dos** packs, no uno:

| Pack | Fichero | Mods | Descarga |
|---|---|---|---|
| **Jugador** | `PokeReport-LunaEternal-0.1.0.mrpack` | 7 | **136 MB** |
| **Constructor** | `PokeReport-LunaEternal-Constructor-0.1.0.mrpack` | 9 | 182 MB |

Ambos en `build/`. **Tú quieres el de constructor** — ver §2-bis.

---

## 1. Corrección de P10

El principio decía *"ningún mod de cliente es obligatorio"*. **Era cierto
cuando se escribió y dejó de serlo al instalar Cobblemon**, que es cliente y
servidor por naturaleza: sin él en el cliente, no se puede conectar.

El principio corregido no es "cero mods" sino **el mínimo posible**:

| | Diosesmon | Nosotros |
|---|---|---|
| Descarga | Modpack completo | **136 MB** |
| RAM recomendada | **8 200 MB** | 4 GB sobran |
| Mods | Decenas | **7** |
| Obligatorio antes de jugar | Sí | Sí, pero es un import de dos minutos |

La ventaja se mantiene, solo que la cifra honesta es 136 MB, no cero.

---

## 2. Qué lleva el pack de jugador, y de dónde sale la lista

**De Cobblemon, no de nosotros** (D-031, revoca D-024). La base es el
**modpack oficial de Cobblemon [Fabric] 1.7.3**: 76 ficheros, MC 1.21.1 y
Cobblemon 1.7.3 — exactamente nuestras versiones.

```
base        76 ficheros del pack oficial
- EXCLUIDOS  2  lo que quitamos, con el motivo escrito al lado
+ EXTRA      6  Euphoria, Shine · (constructor) WorldEdit CUI, Axiom, malilib, Litematica
+ shaders    2  Complementary Unbound y MakeUp, además del Reimagined que ya trae
+ nuestro    1  lunaneon
= 198 ficheros · jugador 185 MB · constructor 234 MB
```

**La lista no se mantiene a mano.** `gen_modpack.py` descarga su `.mrpack`, lee
el índice y usa **sus versiones**, que son las que ellos han probado juntas.
Actualizar cuando saquen pack nuevo es volver a ejecutarlo. Una lista de 76
líneas escrita a mano se queda obsoleta sin que nadie se entere — ya pasó con
la versión del cargador (§5).

### Lo que se quita, y por qué

| Mod | Motivo |
|---|---|
| **`stendhal`** | **CC-BY-NC-ND-4.0.** El *NonCommercial* prohíbe el uso con ánimo comercial, y el plan incluye venta de paquetes (D-007). No es cuestión de redistribuir: es **usarlo** lo que no se puede. Misma cláusula que descartó CobbleVerse (D-006) |
| **`bisect-mod`** | Es el mod de integración de BisectHosting: publicidad de un hosting que no es el nuestro |

### Lo que no se copia de su pack

| | |
|---|---|
| `config/yosbr/saves/` | **97 MB y 2267 ficheros de un mundo tutorial de UN JUGADOR.** Nuestros jugadores entran directos al servidor |
| `config/fancymenu/` | El menú de inicio con la marca, la música y las diapositivas de Cobblemon. Este pack se llama PokeReport |

Sí se copian los **113 ficheros de configuración de verdad** (143 KB), que
afinan los mods.

### `once`: las carpetas que cura el jugador

Todo lo que cae en `config/`, `shaderpacks/` y `resourcepacks/` va marcado
**`once`**: se escribe si falta y **no se pisa nunca**. Sin eso, a quien
ajustara o sustituyera un shader se lo revertiríamos en cada arranque.

**No impide actualizar**: el nombre del fichero lleva la versión, así que subir
Complementary a r5.9 es una ruta nueva que sí se descarga, y la anterior
desaparece porque deja de estar en el manifiesto. `once` solo evita reescribir
una ruta idéntica.

> Esto **no se revisó a ojo**: lo cazó una prueba del launcher que descarga el
> manifiesto en vivo y falla si algo bajo esas carpetas no está marcado
> (`launcher/tools/smoke-test.mjs`). Los 4 shaderpacks se publicaron sin marcar
> y la suite se puso en rojo. Ojo al reejecutarla justo después de publicar: el
> CDN de GitHub cachea unos minutos y puede dar un rojo falso.

> **P10 se ha relajado a conciencia.** La descarga pasa de 143 a **185 MB**.
> Sigue muy por debajo de los 8 GB de Diosesmon, y la contrapartida es partir de
> una base que juegan millones de personas en vez de una lista de siete mods
> elegida a ojo.

---

## 2-bis. El pack de constructor, y por qué está separado

Añade dos mods que **solo sirven para construir**:

| Mod | Motivo | Licencia |
|---|---|---|
| **WorldEdit CUI** | Dibuja la selección. Sin él marcas dos esquinas y no ves qué marcaste | EPL-2.0 |
| **Axiom 5.4.2** | Editor de construcción con interfaz de verdad | propietaria — ver abajo |

**Por qué no van en el pack de todos:** son 46 MB de herramienta de desarrollo
que un jugador normal no usará jamás. Meterlos en el pack general contradice el
propio P10 que acabamos de corregir — el mínimo posible es el mínimo *para
jugar*, no el mínimo para trabajar.

> **Axiom y su licencia (D-008):** su uso no comercial en un servidor privado
> propio es gratuito, pero **exige pedir una whitelist en su Discord**. Está
> instalado en el servidor; el trámite sigue pendiente y es del usuario.
> Detalle en [construccion.md §3-bis](../world/construccion.md).

> **Sobre licencias (D-008):** el `.mrpack` **no redistribuye** ningún mod:
> guarda URLs y hashes, y el launcher los baja de Modrinth. Por eso las
> licencias restrictivas de Sodium y EntityCulling no son un problema — se
> descargan de su canal oficial.

---

## 2-ter. Qué va en cada lado — la tabla definitiva

**Las dos listas NO tienen que coincidir, y es correcto que no coincidan.**
Un mod va donde hace su trabajo. Verificado el 2026-08-11 listando los dos
directorios, no de memoria.

| Mod | Cliente | Servidor | Por qué |
|---|:---:|:---:|---|
| **Cobblemon** | ✅ | ✅ | Es el juego. Necesita los dos lados |
| **Fabric API** | ✅ | ✅ | Dependencia de todo lo demás |
| **Axiom** | ✅ | ✅ | Editar en vivo necesita los dos lados |
| **lunaeternal** (el nuestro) | ❌ | ✅ | Toda su lógica es de servidor, y su jar lleva dentro la economía y el conector de la base de datos. No se reparte |
| **lunaneon** (el nuestro) | ✅ | ✅ | **Bloques.** Un bloque no existe hasta que las dos partes saben que existe: sin él en el cliente se vería el cubo de textura ausente, Axiom no podría ofrecerlos y **Fabric ni dejaría entrar** (D-029) |
| **WorldEdit** | ❌ | ✅ | Los comandos `//` se ejecutan en el servidor |
| **WorldEdit CUI** | ✅ | ❌ | Solo dibuja la selección en tu pantalla |
| **Sodium** | ✅ | ❌ | Gráficos. Un servidor no dibuja |
| **Lithium** | ✅ | ❌ | Rendimiento del cliente |
| **FerriteCore** | ✅ | ❌ | Memoria del cliente |
| **EntityCulling** | ✅ | ❌ | No dibuja lo que no se ve |
| **Mod Menu** | ✅ | ❌ | Menú de mods, es de interfaz |
| **Iris** | ✅ | ❌ | Cargador de shaders. Un servidor no dibuja |
| **EuphoriaPatcher** | ✅ | ❌ | Genera el shader parcheado en el PC del jugador |
| **Shine** + **YACL** | ✅ | ❌ | Luz de color sin shaders. Es efecto de pantalla: el servidor no dibuja |
| **Krypton** · **Clumps** · **LetMeDespawn** | ✅ | ✅ | Trabajo de servidor puro. Vienen del pack oficial |
| **EasyAuth** | ❌ | ✅ | `/register` y `/login`. El cliente no participa |
| *(otros 60 del pack oficial)* | ✅ | ❌ | Interfaz, mapas, recetas, animaciones — ver §2-ter |
| | **79 jars** | **14 jars** | |

> **`lunaneon` se desplegó el 2026-08-13** en los dos lados: en el servidor por
> `tools/desplegar.py`, en el cliente por el manifiesto del launcher. Las cifras
> anteriores (9 y 5) se verificaron el 2026-08-11 listando los directorios; esta
> suma uno a cada lado. El orden —**cliente primero**— y el porqué están en
> [neon.md §5 y §7](../world/neon.md).

> **Por qué el servidor dice «60 mods» y aquí pone 5:** Fabric API es un
> paquete de ~40 submódulos y el log los cuenta uno por uno. **Jars instalados
> hay 5.** Las dos cifras son ciertas, cuentan cosas distintas.

> **La versión de Fabric API difiere** (cliente `0.116.15`, servidor
> `0.116.14`). **Es inofensivo y no hay que tocarlo**: Fabric API mantiene
> compatibilidad entre parches. Solo importaría si un mod pidiera una versión
> mínima concreta, y ninguno lo hace.

### La regla, para no volver a preguntarlo

```
¿el mod dibuja algo o toca la interfaz?   → CLIENTE
¿el mod decide reglas, datos o economía?  → SERVIDOR
¿ambas cosas?                             → LOS DOS
```

### ⚠️ Y la regla que manda sobre todas: servidor ⊆ cliente

Fabric sincroniza el registro al conectar. **Si el servidor registra un bloque
o un objeto que al cliente le falta, al cliente no le deja entrar** — lo echa
con *«Registry remapping failed»*. Al revés no pasa nada: un cliente con mods de
más es el caso normal.

Por eso `tools/mods_servidor.py` **lee las versiones del manifiesto que ya usa
el launcher**, no de Modrinth: así el jar del servidor y el del jugador son
literalmente el mismo fichero, byte a byte, y la pareja no se puede
desincronizar.

### De los 76 del pack, en el servidor van 5

`environment: "*"` en un jar significa *«puede cargar en los dos lados»*, no
*«hace falta en los dos»*. Verificado leyendo los 76 `fabric.mod.json`, no los
metadatos de Modrinth: **34 se declaran de cliente**, y de los 41 restantes la
mayoría son interfaz — mapas, recetas, animaciones, tooltips. En un servidor
solo gastarían RAM, y este tiene 4 GB (`B-003`).

| Mod | Qué hace en el servidor |
|---|---|
| **Lithium** | Optimiza mobs, físicas y chunks. Lo teníamos solo en el cliente, donde hace mucho menos |
| **FerriteCore** | Baja la memoria de los estados de bloque. Con 96 neones nuevos, importa |
| **Krypton** | Optimiza la capa de red. Es de servidor por definición |
| **Clumps** | Junta las bolas de experiencia. Menos entidades, menos lag |
| **LetMeDespawn** | Los mobs desaparecen como deberían, en vez de acumularse |

Ninguno registra bloques ni objetos, así que **ninguno puede dejar a nadie
fuera**. Consumo tras añadirlos: 2 209 MB de 4 096.

> ⚠️ **Un mod puede exigir otro, y no te enteras hasta el reinicio.** Subir
> `letmedespawn` sin `almanac` dejó el servidor sin arrancar con
> `Incompatible mods found!`. El script ahora resuelve la cadena
> (`letmedespawn` → `almanac` → `cloth-config`) leyendo cada `fabric.mod.json`,
> y **aborta sin tocar nada** si algo no está en el manifiesto del cliente.

**Añadir en el lado equivocado no rompe nada, pero no sirve de nada** — es la
lección ya pagada con Litematica en el proyecto anterior.

---

## 2-quater. Shaders

**Vienen instalados y apagados.** El jugador los activa en
*Opciones → Gráficos → Shader Packs* cuando quiera, y su elección se conserva
para siempre — el fichero de configuración va marcado `once`, así que las
actualizaciones no la pisan.

| Pieza | Qué es | Licencia |
|---|---|---|
| **Iris 1.8.8** | El cargador. Sin él, un shaderpack en la carpeta **no se puede activar de ninguna manera**: la opción ni aparece en el menú | LGPL-3.0 |
| **EuphoriaPatcher 1.9.3-r5.8.1** | **No es un shader.** Es el mod que *genera* Euphoria Patches en el PC del jugador a partir de Complementary | MPL-2.0 |
| **Complementary Unbound r5.8.1** | El tier de calidad. Con el parche anterior queda idéntico al de CobbleVerse | propietaria — ver abajo |
| **MakeUp Ultra Fast 9.5c** | El tier de rendimiento, para equipos modestos | LGPL-3.0+ |
| **Shine** + **YACL** | **Luz de color SIN shaders** — ver abajo | ARR (vía Modrinth) · LGPL-3.0+ |

### Shine, y por qué hizo falta

**La luz de Minecraft no tiene color.** El motor guarda un número del 0 al 15
por bloque y nada más, así que un neón cian derrama luz **blanca** por mucho que
el bloque se dibuje cian. No es algo que `lunaneon` pueda arreglar: no existe la
API.

Shine añade un halo que toma el color del propio píxel y **se aplica solo a
bloques emisores**, así que los 96 neones entran sin declarar ninguno. Es efecto
de pantalla, no luz real: **no tiñe el suelo**. Para eso hay que activar los
shaders y subir `COLORED_LIGHTING`, que viene en `0` en el perfil por defecto.

Los dos hacen cosas distintas y no se estorban: Shine da el resplandor, el
shader da la iluminación. Detalle en [neon.md §1](../world/neon.md).

> YACL es dependencia dura de Shine —sin ella no arranca—, por eso van juntos y
> ninguno se marca `optional`.

En la lista del juego aparecen **tres** entradas: el Complementary base, el
`Complementary Unbound r5.8.1 + Euphoria Patches` que genera el parcheador —el
bueno— y el MakeUp.

> El parcheador necesita **conexión la primera vez** para bajar el diff. Si el
> jugador arranca sin red, el Complementary base sigue funcionando; el parcheado
> aparece al siguiente arranque.

### Por qué no se renombran a «PokeReport - Shaders»

Se planteó copiarlos de la instancia de CobbleVerse y renombrarlos. **No se
puede, y la licencia viene dentro de la propia carpeta:**

| Cláusula | Qué dice |
|---|---|
| Complementary **§1.3.d** | Un pack renombrado *«must look noticeably different from the Original Pack in multiple common gameplay scenarios… regardless of the setting or variable changes»*. Renombrar no cambia cómo se ve |
| Complementary **§1.2.d** | *«It is not allowed to redistribute This Pack using a direct file upload»* — hay que servirlo por Modrinth o CurseForge |
| Euphoria **§2.1** | Solo se pueden obtener copias **ejecutando el mod Patcher** o el instalador oficial. *«Any other form of direct redistribution is prohibited»* |
| Euphoria **§2.2.a** | En un modpack, solo si **el modpack incluye el Patcher** |

Conservar el fichero de licencia dentro es **una** de las condiciones, no un
salvoconducto. Es la misma trampa que descartó CobbleVerse (D-006), y pesa más
aquí porque el plan incluye venta de paquetes (D-007).

**Por eso el montaje es el que es:** los shaders se bajan de Modrinth por
URL+hash (§1.2.d cumplido) y Euphoria llega como parcheador, no parcheado
(§2.1 y §2.2.a cumplidos). El resultado para el jugador es idéntico; lo único
que cambia es que en la lista salen con su nombre real.

---

## 2-quinquies. ⚠️ Sodium bajó de 0.8.12 a 0.6.13, y no es un error

**Iris y Sodium no se pueden elegir por separado.** Iris se niega a arrancar con
un Sodium fuera de su rango, y la última **estable** de Iris para 1.21.1
(`1.8.8`) declara `sodium: 0.6.x`. Existe `1.8.14-beta.1`, que sí acepta
`0.8.x`, pero el criterio de este script es explícito: *«release antes que
beta: el cliente de un servidor no es sitio para probar versiones inestables»*.

Así que Sodium baja. **Pero el número no está escrito a mano en ningún sitio**:
`gen_modpack.py` descarga el jar de Iris, lee la dependencia que declara en su
`fabric.mod.json` y elige el Sodium más nuevo que encaje. El día que Iris saque
una estable con `0.8.x`, regenerar el pack sube Sodium **solo**.

> Es la lección de la versión del cargador, aplicada antes de que costara nada:
> todo número escrito a mano caduca en silencio. Y si algún día Iris declarara
> su dependencia en un formato que el script no sabe leer, **falla y no
> publica** en vez de emparejar mal — un Iris y un Sodium incompatibles dejan
> el juego sin arrancar, y el error no menciona ni a uno ni a otro.

---

## 3. Cómo se instala

### PrismLauncher *(el que ya tienes)*

```
Add Instance → Import → seleccionar el .mrpack → Launch
```

### Modrinth App

```
Arrastrar el .mrpack a la ventana
```

El servidor **ya viene en la lista de multijugador**: el pack incluye un
`servers.dat` con la dirección, así que no hay que escribir ninguna IP.

---

## 4. Por qué un `.mrpack` y no el launcher de Electron

El proyecto anterior tiene un launcher propio en Electron, con instaladores
para Windows y macOS. **Es mejor producto y llegará**, pero:

| | `.mrpack` | Launcher Electron |
|---|---|---|
| Tiempo hasta jugar | **Hoy** | Días de trabajo |
| Actualizaciones | Reimportar | Automáticas |
| Marca propia | Ninguna | Completa |
| Java incluido | No | Sí |

Para **probar el servidor ahora**, el `.mrpack` sobra. El launcher es lo que
se le da a los jugadores el día del lanzamiento, y se adapta del que ya existe
en `D:\PokeReport 2\launcher` (Electron 43, electron-builder, CI en GitHub
Actions ya montada).

---

## 5. Regenerarlo

Los packs se generan con un script contra la API de Modrinth, así que
actualizar versiones es volver a ejecutarlo — no editar JSON a mano. Una sola
ejecución produce los dos.

```
python tools/gen_modpack.py
```

> ⚠️ **La versión del cargador también se consulta, no se escribe.** Estaba
> fijada a mano en `0.16.14` y el pack **no arrancaba**: Cobblemon 1.7.3 exige
> `0.17.2` o superior y Fabric aborta con *"Incompatible mods found!"*. Ahora
> se pide a `meta.fabricmc.net` la última estable y se comprueba contra
> `LOADER_MINIMO`; si algún día fuera menor, el script falla en vez de generar
> un pack roto.
>
> **La lección es la de siempre en este proyecto:** todo número escrito a mano
> caduca en silencio. Las versiones de los 9 mods ya se consultaban; la del
> cargador se me quedó fuera.

## Next Actions

1. Importar el pack de **constructor** y entrar al servidor
2. Pedir la whitelist de Axiom en su Discord (§2-bis)
3. `LNC-001` — adaptar el launcher de Electron cuando el servidor esté listo

## Related Systems

- [Los mundos](../world/worlds.md) · [Construcción](../world/construccion.md)
