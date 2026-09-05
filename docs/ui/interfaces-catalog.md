# Catálogo de interfaces

## Purpose

Inventario de todas las pantallas que hay que construir, con su prioridad y lo
que necesita cada una. Es la lista de trabajo de UI.

## Dependencies

- [`interfaz-cliente.md`](interfaz-cliente.md) — cómo se construye y qué NO se hace

## Related Documents

- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md) §0

## Current Status

**Vivo, y ahora mismo todo está por construir.** El 2026-08-12 se retiró la
implementación entera (D-026): las pantallas que aquí figuraban como hechas
eran menús de cofre, y se borraron para rehacerlas como interfaz de cliente con
arte real.

**Este documento sobrevive porque el inventario sigue siendo válido.** Lo que
caducó es *cómo* se dibujaban, no *qué* pantallas hacen falta ni qué debe haber
en cada una — eso se pensó una vez y sigue en pie.

## Last Decision

D-026 · la interfaz se rehace en el cliente. Ninguna pantalla se implementa
como menú de cofre.

---

## 0. Referencia: las 22 apps de Diosesmon

Leído en su wiki el 2026-08-11. Sirve como lista de comprobación de *qué
espera un jugador de este género*, no como especificación:

```
Pokédex · Cosméticos · Trabajos · Misiones · Warps · Clan · GTS · Tienda
ATM · Wiki · Cazas · Kits · Gyms · Explorar · Wonder Trade · PC · Curar
Guardería · Tesoros · Modificadores · Descargas · Ajustes
```

**Cuatro decisiones respecto a esa lista:**

| Su app | Nosotros | Motivo |
|---|---|---|
| **Modificadores** (compra de mejoras de estadísticas) | ❌ **No existe** | Es venta directa de poder — T4 |
| **PC** y **Curar** como funciones premium | ✅ Gratis para todos | Son necesidades básicas, no lujos. Cobrar por curar es cobrar por jugar |
| **ATM** (comprar moneda premium dentro del juego) | 🟡 Solo consulta de saldo | La compra va fuera del juego; meter la caja en el HUD es lo que criticamos del *"¡Cómpralo!"* |
| **Wiki** dentro del PokePad | ✅ Buena idea, la adoptamos | Ayuda sin sacar al jugador |

---

## 1. Estado y prioridad

> **2026-08-23 — cinco pantallas hechas y el recorrido del jugador nuevo
> cerrado.** Falta añadir a esta tabla la **pantalla del inicial**, que no estaba
> en la lista de Diosesmon porque ellos no la tienen como app: se abre sola al
> entrar. Ver [`misiones.md`](misiones.md) §2.
>
> **Lo siguiente que más se nota es la TIENDA**: hoy un jugador captura pero no
> puede comprar Poké Balls ni curar su equipo, y cuatro misiones del árbol no se
> pueden completar por eso.

| # | Pantalla | Estado | Prioridad | Depende de |
|---|---|---|---|---|
| 1 | **LunaPad** (principal) | ⬜ **rehacer** | 🔴 | — |
| 2 | **Barra lateral** | ⬜ **rehacer** | 🔴 | — |
| 2b | **Tablist** (cabecera, pie, rangos) | ⬜ **rehacer** | 🔴 | — |
| 3 | **Cartera** | ⬜ **rehacer** | 🔴 | — |
| 4 | **Vías** | ✅ **hecha** (2026-08-23, `TrabajosScreen`) | — | — |
| 5 | **Puerta del mundo** | ⬜ **rehacer** | 🔴 | — |
| 6 | **Viajes / LunaTaxi** | ⬜ | 🟠 | ciudadela construida |
| 7 | **Tienda** | ✅ **hecha** (2026-08-23, 5 categorías) | ✅ | — |
| 8 | **GTS** | ⬜ **rehacer** | 🔴 | Pokémon cuando haya Cobblemon |
| 9 | **Pokédex** | ⬜ **rehacer** | 🔴 | — |
| 10 | **Kits** | ⬜ **rehacer** | 🔴 | rangos, para los de rango |
| 11 | **Misiones** | ✅ **hecha** (2026-08-23, árbol de 28 en 6 cadenas) | — | — |
| 12 | **Cazas** | ⬜ | 🟡 | rotación |
| 13 | **Medallas** | ⬜ | 🟡 | gimnasios |
| 14 | **Tesoros** | ⬜ | 🟡 | decisión de `treasures.md` §2 |
| 15 | **Oficios** | ✅ **hecho** (2026-08-23) — minar, pescar, cosechar y criar dan Plata | — | — |
| 16 | **Cosméticos** | ✅ **hecha** (2026-08-22) — 65 disfraces, 378 sombreros, 11 auras | — | — |
| 17 | **Clan** | ✅ **hecha** (2026-08-23, V013 — ver [clanes.md](../social/clanes.md)) | ✅ | sistema social |
| 18 | **Caja (PC)** | ⬜ | 🟡 | integración Cobblemon |
| 19 | **Criadero** | ⬜ | ⚪ | cría |
| 20 | **Explorar / mapa** | ⬜ | ⚪ | zonas |
| 21 | **Historial** | ⬜ | ⚪ | `ledger_entry` (ya existe) |
| 22 | **Rangos** | ⬜ | ⚪ | catálogo |

> **Ojo con leer esta tabla como trabajo perdido.** Lo que hay que rehacer es
> la pantalla; **lo de debajo sigue vivo y probado**: economía, progresión,
> tienda, GTS, Pokédex, kits, misiones, cazas y viaje entre dimensiones siguen
> en el mod con sus invariantes en `/luna autotest`. Rehacer la Cartera es
> dibujarla, no reinventar la cartera.

Las dimensiones ya existen (`WLD-002`), así que la Puerta del Mundo tiene a
dónde llevar. Lo que falta ahí es **construir** la ciudadela, no programarla.

---

## 2. Patrones comunes

Para no rediseñar cada pantalla desde cero. Son patrones de **contenido**, no
de rejilla de cofre: siguen valiendo para la interfaz de cliente.

### 2.1 · Lista paginada
`Pokédex · GTS · Caja · Cosméticos · Tienda`

```
┌──────────────────────────────────────────┐
│ [🔍 filtro]              [◀] 3/41 [▶]    │
├──────────────────────────────────────────┤
│  ▪  ▪  ▪  ▪  ▪  ▪  ▪                     │
│  ▪  ▪  ▪  ▪  ▪  ▪  ▪    ← 28 por página  │
│  ▪  ▪  ▪  ▪  ▪  ▪  ▪                     │
│  ▪  ▪  ▪  ▪  ▪  ▪  ▪                     │
├──────────────────────────────────────────┤
│ [← Atrás]                     [✖ Cerrar] │
└──────────────────────────────────────────┘
```

Reglas: **nunca cargar la lista entera**; consultar por página con `LIMIT`.

### 2.2 · Detalle + acción
`GTS (comprar) · Tienda · Tesoros · Kits`

Siempre: qué recibes · qué pagas · **saldo después** · confirmar/cancelar.
Ninguna acción con coste se ejecuta al primer clic.

### 2.3 · Rejilla de tarjetas
`Oficios · Kits · Puerta del mundo · Iniciales`

Diosesmon lo usa en Trabajos (5 tarjetas con nivel y barra de XP) y funciona
muy bien: se compara de un vistazo. Lo adoptamos.

### 2.4 · Panel lateral fijo
`Misiones · GTS · Tesoros`

Diosesmon reserva la izquierda para el detalle de lo seleccionado. **No es
replicable en un menú de cofre** —no hay dos zonas— así que lo resolvemos con
navegación: lista → detalle → atrás.

---

## 3. Reglas de estilo

| Regla | Motivo |
|---|---|
| Título con el nombre de la sección, siempre | El jugador debe saber dónde está |
| **Atrás** en el hueco 48, **Cerrar** en el 53 | Posición fija en todas |
| Marco de cristal gris; el color solo para lo importante | Si todo destaca, nada destaca |
| Lo bloqueado: **qué es · qué falta · qué hacer** | nunca se oculta (P9) |
| Los saldos, siempre en el mismo sitio | Se consultan sin buscar |
| Sonido al pulsar; sonido distinto si no hace nada | El jugador sabe si falló él o la interfaz |
| Sin cursivas (Minecraft las mete por defecto) | Ya resuelto en `Icon` |

---

## 4. Texturas y arte

Diosesmon tiene interfaces dibujadas a medida (marcos, PokePad, taxi). **Eso
requiere resourcepack y, en su caso, mod de cliente.** Nosotros no lo
exigimos (P10), así que hay dos capas:

| Capa | Sin resourcepack | Con nuestro launcher |
|---|---|---|
| **Base** | Menú de cofre vanilla + iconos de objetos | Igual |
| **Mejorada** | — | Marcos propios con fuentes de mapa de bits y espacios negativos |

La técnica de la capa mejorada **ya la conocemos**: se usó en el proyecto
anterior para el HUD del evento. No hay que investigarla.

> **Orden correcto:** primero que funcione en vanilla, después el arte. Al
> revés se acaba con pantallas preciosas que no hacen nada.

### Lo que habrá que dibujar (cuando toque)

- Marco del LunaPad y de las pantallas secundarias
- Iconos de las 22 secciones
- Barras de progreso de vías y oficios
- Insignias de las medallas
- Fondo del mapa de viajes
- Marcos de rareza para el GTS

Tarea `ART-001`, aún sin empezar y **no bloquea nada**.

---

## 5. El LunaTaxi

Merece nota propia porque es la que enseñaste con más detalle.

```
┌─────────────────── LUNATAXI ────────────────────┐
│ DESTINOS          │                             │
│  Plaza central    │      [ mapa de la           │
│  Zona Oeste       │        ciudadela con        │
│  Zona Este        │        puntos marcados ]    │
│  Gimnasios        │                             │
│  Torre Batalla    │                             │
│  Tejados          │                             │
│  [ VIAJAR ]       │                             │
└─────────────────────────────────────────────────┘
```

**Solo funciona dentro de la ciudadela.** Es comodidad pura y ahí conviene ser
generoso: la ciudadela no tiene nada que descubrir, tiene servicios que usar.

El viaje que sí hay que racionar es el que salta exploración del mundo
([vision.md](../game-design/vision.md) §5). Son dos cosas distintas y
mezclarlas sería un error.

**Nota técnica:** el mapa dibujado del suyo requiere resourcepack. La versión
base es una lista de destinos con iconos — funciona igual de bien y no bloquea.

---

---

## 5-bis · El Santuario (2026-09-04)

Dos pantallas sobre el chasis compartido, en `docs/world/santuario.md`:

| Pantalla | Patrón | Notas |
|---|---|---|
| `SantuarioScreen` | lista paginada + detalle («mi nicho») | los precios viajan del servidor; una acción por fila |
| `MemorialScreen` | detalle + acción | foto por `TexturasFoto`, campanilla solo si el servidor confirma |

El holograma de la foto en el mundo NO es una pantalla: se dibuja a mano en
`AFTER_ENTITIES` sobre la posición del proyector (sin entidades nuevas).

## Next Actions

1. `UI-015` — Medallas
2. `ECO-003` — telemetría (necesaria para calibrar de verdad)
3. `WLD-005` — construir la ciudadela (no es programación)
4. `ART-001` — arte, cuando haya pantallas que vestir

## Related Systems

- [La interfaz de cliente](interfaz-cliente.md) · [Los mundos](../world/worlds.md)
- [Tesoros](../economy/treasures.md) · [GTS](../trading/gts.md)
