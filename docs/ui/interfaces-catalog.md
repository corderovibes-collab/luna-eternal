# Catálogo de interfaces

## Purpose

Inventario de todas las pantallas que hay que construir, con su prioridad y lo
que necesita cada una. Es la lista de trabajo de UI.

## Dependencies

- [`navigation.md`](navigation.md) — El LunaPad y sus cuatro grupos

## Related Documents

- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md) §0

## Current Status

**Vivo.** Se actualiza conforme se construyen pantallas.
Framework y **6 pantallas operativas**; el resto por hacer.

## Last Decision

Pendiente.

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

| # | Pantalla | Estado | Prioridad | Depende de |
|---|---|---|---|---|
| 1 | **LunaPad** (principal) | ✅ hecho | — | — |
| 2 | **Barra lateral** | ✅ hecho | — | — |
| 2b | **Tablist** (cabecera, pie, rangos) | ✅ hecho | — | — |
| 3 | **Cartera** | ✅ hecho | — | — |
| 4 | **Vías** | ✅ hecho | — | — |
| 5 | **Puerta del mundo** | ✅ hecho | — | — |
| 6 | **Viajes / LunaTaxi** | ⬜ | 🟠 | ciudadela construida |
| 7 | **Tienda** | ✅ hecho | — | — |
| 8 | **GTS** | ✅ hecho | — | Pokémon cuando haya Cobblemon |
| 9 | **Pokédex** | 🟡 datos ✔, pantalla ⬜ | 🔴 | — |
| 10 | **Kits** | ⬜ | 🟠 | rangos |
| 11 | **Misiones** | ⬜ | 🟡 | sistema de quests |
| 12 | **Cazas** | ⬜ | 🟡 | rotación |
| 13 | **Medallas** | ⬜ | 🟡 | gimnasios |
| 14 | **Tesoros** | ⬜ | 🟡 | decisión de `treasures.md` §2 |
| 15 | **Oficios** | ⬜ | 🟡 | sistema de trabajos |
| 16 | **Cosméticos** | ⬜ | 🟡 | catálogo |
| 17 | **Clan** | ⬜ | 🟡 | sistema social |
| 18 | **Caja (PC)** | ⬜ | 🟡 | integración Cobblemon |
| 19 | **Criadero** | ⬜ | ⚪ | cría |
| 20 | **Explorar / mapa** | ⬜ | ⚪ | zonas |
| 21 | **Historial** | ⬜ | ⚪ | `ledger_entry` (ya existe) |
| 22 | **Rangos** | ⬜ | ⚪ | catálogo |

Las tres rojas están hechas y **las dimensiones ya existen** (`WLD-002`): la
Puerta del Mundo teletransporta de verdad. Lo que falta ahora es **construir**
la ciudadela, no programarla.

---

## 2. Patrones comunes

Para no rediseñar cada pantalla desde cero. Todas usan el framework de
[`navigation.md`](navigation.md) §7.

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
| Lo bloqueado: **qué es · qué falta · qué hacer** | `navigation.md` §5 |
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

## Next Actions

1. `PKM-004` — datapack de generaciones (Kanto + Johto)
2. `UI-013` — Pokédex
3. `WLD-005` — construir la ciudadela (no es programación)
4. `ART-001` — arte, cuando haya pantallas que vestir

## Related Systems

- [Navegación](navigation.md) · [Los mundos](../world/worlds.md)
- [Tesoros](../economy/treasures.md) · [GTS](../trading/gts.md)
