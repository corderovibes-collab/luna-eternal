# Interfaz profesional sin mod de cliente

## Purpose

Responder a una crítica justa del usuario: **«hiciste algo simple y básico,
quiero algo profesional con texturas»**. Explicar por qué salió básico, y cómo
se arregla sin romper P10.

## Dependencies

- [`navigation.md`](navigation.md) · [`interfaces-catalog.md`](interfaces-catalog.md)
- [`../technical/client-pack.md`](../technical/client-pack.md) — P10

## Current Status

`DONE` la infraestructura · `PENDING` el arte.

**Desplegado y verificado el 2026-08-11.** El servidor sirve el pack, el mod
dibuja los 10 fondos y el autotest pasa 109/109.

| | |
|---|---|
| Pack | `luna-eternal-pack` v1.0.0, público, SHA-1 verificado tras descarga |
| Servidor | `require-resource-pack=true`, `resource-pack-id` fijo |
| Mod | `Skin.java` + `Menu(title, rows, skin)` · `Interfaz: 10 fondos cargados` |
| Arte | ⚠️ **marcadores de posición.** Falta `ART-001` |

> Las texturas actuales son cuadros morados con una rejilla de comprobación.
> **Sirven para validar que la alineación es exacta al píxel**, no para ser
> bonitas. El sistema está listo; lo que falta es dibujar.

## Dibujar Pokémon en 3D: lo que costó cuatro intentos

Si alguna vez vuelve a parpadear un modelo dentro del Pad, **son estas tres
cosas y en este orden**. Las tres hacen falta; ninguna basta sola.

```java
// 1. Una rotación NUEVA por llamada.
//    drawProfilePokemon hace rotation.conjugate(), que MUTA el objeto.
//    Con una constante compartida, la rotación se invierte en cada llamada.

// 2. Dibujar en PASADAS: todos los fondos, luego todos los modelos,
//    luego todo el texto. Intercalar tarjeta-modelo-tarjeta-modelo hace
//    que cada fondo se pinte contra la profundidad del modelo anterior.

// 3. LIMPIAR LA PROFUNDIDAD después de los modelos:
RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
//    El modelo se dibuja con scale(s, s, -s) — Z invertida — y deja el
//    buffer escrito. Sin limpiarlo, lo que venga después se compara contra
//    esa profundidad y unas veces pasa y otras no.
```

> **Cómo se encontró, que importa más que el arreglo:** los tres primeros
> intentos fueron tocar estado de render por intuición y fallaron. Lo que lo
> resolvió fue una prueba de treinta segundos del usuario —**abrir la Caja de
> PC de Cobblemon**, que no parpadeaba— porque eso descartó de golpe el driver
> y el propio Cobblemon, y dejó claro que el fallo era nuestro.
>
> Ante un problema de render: **primero acota de quién es el fallo, después
> compara con código que se sabe que funciona.** Nunca al revés.

## Last Decision

D-023 — Resource pack con fuente de espacio negativo. Ver §4.

---

## 1. La crítica es correcta

Lo que hay hoy son **menús de cofre vanilla**: una rejilla gris con cabezas y
objetos. Funciona, es sólido y es 100 % de servidor — pero **parece un plugin
de 2015**, no un MMORPG.

Diosesmon no se ve así. Sus pantallas tienen fondo propio, iconos propios y
elementos superpuestos.

## 2. Por qué salió básico, sin excusas

Apliqué P10 (*cliente mínimo*) como si significara *"solo servidor, y punto"*.
Con esa restricción, los widgets disponibles son los de vanilla y no hay más.

**El error fue no separar dos cosas distintas:**

| | Obliga a instalar mods | Rompe P10 |
|---|---|---|
| **Mod de cliente** (GUI propia en código) | Sí | **Sí** |
| **Resource pack** (texturas y fuentes) | **No** — lo envía el servidor solo | **No** |

Un resource pack **no es un mod**. El servidor lo anuncia, el cliente lo baja
solo y el jugador acepta una vez. No hay instalación, no hay launcher, no hay
nada que explicar. **Nunca lo planteé, y ahí está el fallo.**

---

## 3. Qué se puede hacer de verdad, y no es poco

### 3.1 · Fondos y layouts propios: **fuente de espacio negativo**

Es la técnica estándar de los servidores grandes. Suena raro y es simple:

1. El resource pack define una **fuente propia** donde ciertos caracteres
   (de la zona de uso privado, `U+E000`…) no son letras sino **imágenes PNG**.
2. La misma fuente define caracteres de **anchura negativa** (proveedor
   `space`), que mueven el cursor hacia atrás.
3. El **título** del menú de cofre se escribe con esa fuente: retrocedes con
   espacios negativos y dibujas tu textura encima de la rejilla gris.

Resultado: **el menú de cofre deja de parecer un menú de cofre.** Fondo propio,
marco propio, cabeceras propias. Y sigue siendo un contenedor vanilla que el
servidor controla entero (P6 intacto).

```
assets/lunaeternal/font/default.json
  ├── provider "space"   → caracteres de anchura -1, -8, -16…
  └── provider "bitmap"  → U+E001 = almanaque_fondo.png
                           U+E002 = cartera_fondo.png
```

### 3.2 · Iconos propios: `custom_model_data`

Cada objeto de un menú puede llevar el componente
`minecraft:custom_model_data`. El resource pack le asigna **su propia textura**.
Se acabaron las cabezas de jugador y los tintes de lana como iconos.

### 3.3 · Superposición en pantalla (lo que pedías)

Todo esto es vanilla, todo admite la fuente propia del §3.1:

| Elemento | Dónde sale | Uso previsto |
|---|---|---|
| **BossBar** | Arriba del todo | Progreso de misión, evento activo, barra de jefe |
| **ActionBar** | Sobre la barra rápida | Avisos cortos: «+250 PokéDólares» |
| **Title / Subtitle** | Centro de la pantalla | Entrar a zona, subir de vía, capturar shiny |
| **Sidebar** | Derecha | ✅ ya está |
| **Text Display** | En el mundo | Carteles flotantes, nombres de tienda |

Con la fuente propia, **un BossBar puede dibujar una imagen**, no solo texto.
Ahí está el «encima de la pantalla» que viste en Diosesmon.

### 3.4 · Y la marca antes de entrar

`FancyMenu` (del modpack oficial, §5) permite **menú principal propio** con
fondo, música y botones nuestros. Ese sí es mod de cliente, y por eso va al
pack de constructor primero, a evaluar.

---

## 4. Decisión — D-023

**Resource pack propio, servido por el servidor, con fuente de espacio negativo
y `custom_model_data`.** Sin mods de cliente nuevos.

**Por qué esta vía y no un mod de cliente:**

| | Resource pack | Mod de cliente propio |
|---|---|---|
| Instalación del jugador | **Ninguna** | Otro jar en el pack |
| Se rompe al salir MC 1.22 | Poco | **Mucho** |
| Libertad visual | Alta | Total |
| Trabajo | Arte + plantillas | Arte + código + mantenimiento |

La libertad total no compensa cuando el 90 % del resultado se consigue sin
código de cliente. Si algún día hace falta lo que falta, se añade **encima**,
no en vez de.

### Alojamiento

El servidor necesita una URL HTTPS y el SHA-1 del zip:

```
resource-pack=https://…/lunaeternal-1.0.zip
resource-pack-sha1=…
require-resource-pack=true
resource-pack-prompt=<texto propio>
```

Se publicará en **GitHub Releases** del repositorio del proyecto — gratis,
estable y versionado. `gh` ya está autenticado; falta crear el repositorio.

> `require-resource-pack=true` **es lo correcto aquí**: si la interfaz depende
> del pack, un jugador sin él vería cuadros rotos. Mejor exigirlo.

---

## 5. Qué se aprovecha del modpack oficial de Cobblemon

Analizado el 2026-08-11: `cobblemon-fabric` 1.7.3, **76 mods, 180,7 MB**.

**No se adopta entero**, por tres razones:

1. **Es un pack de cliente, no de servidor.** Son mods visuales y de comodidad
   (Iris, shaders, mapas, EMI/JEI, partículas). **No aporta ni una sola regla
   de juego** — que es exactamente lo que nosotros escribimos en `lunaeternal`.
2. **`stendhal` es `CC-BY-NC-ND-4.0`.** No comercial **y** sin obras derivadas.
   Es la misma clase de problema que tumbó CobbleVerse (D-006, D-008). Un solo
   mod así contamina el pack entero.
3. Son 76 mods frente a nuestros 7-9. Contradice P5 y P10 de frente.

**Sí se cosechan** ideas y mods sueltos, por su cuenta:

| Mod | Licencia | Para qué nos sirve |
|---|---|---|
| **FancyMenu** | DSMSLv3 | Menú principal con nuestra marca (§3.4) |
| **Iris** | LGPL-3.0 | Shaders opcionales. Vende mucho en capturas |
| **EMI** o **JEI** | MIT | Recetas. Comodidad real |
| **Xaero's minimap** | ARR | Mapa. **ARR**: usable vía `.mrpack`, no editable |
| **CraftPresence** | MIT | Presencia en Discord = publicidad gratis |

> **Sobre «editarlos y alinearlos»:** técnicamente se podría con los MIT y
> LGPL. **No merece la pena.** Son infraestructura (rendimiento, shaders,
> recetas), no jugabilidad: editarlos no nos acerca al MMORPG y nos deja
> manteniendo un fork ajeno para siempre. **Nuestra diferenciación vive en
> `lunaeternal`, que es nuestro al 100 %** — ahí sí se escribe sin pedir
> permiso a nadie.

---

## 6. Plan de implementación

| # | Tarea | Depende de |
|---|---|---|
| ~~`UI-016`~~ | ✅ Repositorios creados y pack publicado | — |
| ~~`UI-017`~~ | ✅ Fuente de espacio negativo + `gen_resourcepack.py` | — |
| ~~`UI-018`~~ | ✅ Las 10 pantallas ya llevan fondo | — |
| `ART-001` | **Arte real**: fondos, marcos, iconos | `UI-018` |
| `UI-019` | Migrar el resto de pantallas al nuevo estilo | `ART-001` |
| `UI-020` | BossBar, ActionBar y Titles con la fuente propia | `UI-017` |

> **El cuello de botella es el arte, no el código.** El generador y las
> plantillas los escribo yo; los PNG hay que dibujarlos o encargarlos.
> Conviene decidir pronto si se encarga a alguien.

## Next Actions

1. `UI-016` — repositorio y alojamiento del pack
2. `UI-017` — fuente y generador
3. Decidir quién dibuja el arte (`ART-001`)

## Related Systems

- [Navegación](navigation.md) · [Catálogo](interfaces-catalog.md)
- [Pack de cliente](../technical/client-pack.md)
