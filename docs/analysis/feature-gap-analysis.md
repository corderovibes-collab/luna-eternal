# Análisis de huecos — nosotros vs Diosesmon

## Purpose

Comparar **funcionalidad a funcionalidad** con Diosesmon, decir qué tenemos,
qué falta, y en qué somos deliberadamente distintos. Es el documento que el
brief pedía en §8 y que faltaba.

## Dependencies

- [`diosesmon-analysis.md`](diosesmon-analysis.md) — sus 22 apps y su recorrido
- [`../ui/interfaces-catalog.md`](../ui/interfaces-catalog.md) — estado de pantallas

## Related Documents

- [`../roadmap/backlog.md`](../roadmap/backlog.md)

## Current Status

Auditoría completa 2026-08-11. **32 funcionalidades comparadas.**
Estado real: **11 hechas · 3 parciales · 18 pendientes.**

## Last Decision

D-019 · D-020 (qué se copia y qué no de su modelo)

---

## 1. Resumen honesto

```
HECHO         ██████████░░░░░░░░░░░░░  14 / 32
PARCIAL       ██░░░░░░░░░░░░░░░░░░░░░   3 / 32
PENDIENTE     ░░░░░░░░░░░░░░░░░░░░░░░  15 / 32
```

**Lo que ya está es el cimiento, no la fachada.** Economía auditada, mercado con
custodia, progresión persistida, 75 invariantes automatizados. Diosesmon tiene
más pantallas; nosotros tenemos más garantías bajo cada una.

**Lo que falta es casi todo lo visible.** Y hay un hueco que importa más que
los otros dieciocho juntos — §3.

---

## 2. La tabla completa

Leyenda: ✅ hecho · 🟡 parcial · ⬜ pendiente · ❌ descartado a propósito

### 2.1 · Las 22 apps de su PokePad

| # | Su app | Nosotros | Estado | Nota |
|---|---|---|---|---|
| 1 | Pokédex | Pokédex | ✅ | **Mejor:** enseña lo que aún no ha llegado, y la fase lunar de cada primera captura |
| 2 | Cosméticos | Cosméticos | ⬜ | Necesita catálogo y arte |
| 3 | Trabajos | Oficios | ⬜ | Ellos: 5 oficios, nivel 1/125 |
| 4 | Misiones | Misiones | ✅ | **Mejor:** el tutorial enseña el bucle en el orden en que se juega |
| 5 | Warps | LunaTaxi | ⬜ | Bloqueado por la ciudadela, no por código |
| 6 | Clan | Clan | ⬜ | |
| 7 | GTS | GTS | ✅ | **Mejor:** custodia real, sin doble compra, impuesto progresivo por tramos, entrega diferida a prueba de caídas |
| 8 | Tienda | Tienda | ✅ | **Mejor:** anti-arbitraje verificado al arrancar |
| 9 | ATM | Cartera | 🟡 | Consulta ✅. **Comprar moneda dentro del juego: descartado** — es el *"¡Cómpralo!"* que criticamos |
| 10 | Wiki | Wiki | ⬜ | Buena idea suya, la adoptamos |
| 11 | Cazas | Cazas | ⬜ | Ellos: rotan cada 12 h |
| 12 | Kits | Kits | ✅ | **Mejor:** tope de inyección diaria verificado al arrancar |
| 13 | Gyms | Medallas | ⬜ | **Distinto:** los gimnasios van repartidos por el mundo, no en una sala |
| 14 | Explorar | Explorar | 🟡 | Puerta del Mundo ✅; mapa y zonas ⬜ |
| 15 | Wonder Trade | — | ⬜ | Barato de hacer y muy social |
| 16 | PC (premium) | Caja | ⬜ | **Será gratis.** Cobrar por guardar es cobrar por jugar |
| 17 | Curar (premium) | Curar | ✅ | **Gratis**, con cooldown. Cobrar por curar es cobrar por jugar |
| 18 | Guardería | Criadero | ⬜ | |
| 19 | Tesoros | Tesoros | ⬜ | Decidido (D-020), sin implementar |
| 20 | **Modificadores** | — | ❌ | **Descartado.** Venta de estadísticas: mejora repetible y sin techo |
| 21 | Descargas | — | ⬜ | Nuestro launcher ya existe del proyecto anterior |
| 22 | Ajustes | Ajustes | ⬜ | |

### 2.2 · Sistemas de su wiki que no son apps

| Su sistema | Nosotros | Estado | Nota |
|---|---|---|---|
| Pokecenter | Centro Pokémon | ⬜ | Curar + PC en la ciudadela |
| Fusiones | — | ⬜ | Evaluar: ¿aporta o es ruido? |
| Torre Batalla | — | ⬜ | Contenido de endgame |
| Crianza | Criadero | ⬜ | |
| Pokeparadas | — | ⬜ | 🟡 **Mejorar:** que exijan gameplay, no solo caminar y pulsar |
| Legendarios | Legendarios | 🟡 | Diseñado (cupo por temporada en el Salvaje), sin implementar |
| Variocolor | Variocolor | ✅ | Se registra en la Pokédex |
| Objetos competitivos | — | ⬜ | |
| MTs y DTs | — | ⬜ | |
| Rangos | Rangos | 🟡 | 8 rangos en el tablist ✅; permisos y compra ⬜ |

### 2.3 · Estructura y recorrido

| Elemento | Nosotros | Estado | Nota |
|---|---|---|---|
| Lobby con NPC de acceso | Dimensión `lobby` | 🟡 | Dimensión ✅, contenido ⬜ |
| Ciudadela | Dimensión `ciudadela` | 🟡 | Dimensión ✅, **construcción ⬜** |
| Libro de bienvenida | — | ⬜ | |
| Cinemática de entrada | — | ⬜ | **Mejor:** in-game con nuestras 57 voces, no un vídeo de YouTube |
| **Elección de inicial** | Inicial | ✅ | Kanto/Johto, con el consejo de cada uno |
| Dos mundos | Hogar + Salvaje | ✅ | **Mejor:** decimos cuándo es el reinicio y qué se pierde |
| Barra lateral | Barra lateral | ✅ | **Mejor:** fase lunar arriba; sin *"¡Cómpralo!"* |
| Tablist con rangos | Tablist | ✅ | **Mejor:** sin anunciar lo que no existe |
| Modpack obligatorio | — | ❌ | **Descartado (P10).** Exigirlo antes de jugar es la mayor barrera que existe |

---

## 3. 🔴 El hueco que importa más que todos los demás

> **Un jugador nuevo entra al servidor y no tiene ningún Pokémon.**

Todo lo construido —economía, mercado, progresión, Pokédex— **no se puede usar
sin un Pokémon**. Y no hay forma de conseguir el primero:

- No hay elección de inicial
- No hay libro ni NPC que lo entregue
- Capturar exige una Poké Ball, que exige dinero, que exige capturar

Es un **bloqueo circular**, y hace que el servidor sea hoy técnicamente sólido
y jugablemente inútil.

**El Kit de Inicio lo rompe a medias** —da 10 Poké Balls— pero un jugador sin
Pokémon no puede debilitar a nada, así que capturar es cuestión de suerte.

### Prioridad absoluta, por delante de la telemetría

`CLAUDE.md` decía que lo siguiente era la telemetría. **Estaba mal
priorizado**: medir una economía que nadie puede jugar no sirve de nada.

El orden correcto:

```
1. Inicial            un jugador nuevo puede empezar
2. Curar y Caja       puede seguir jugando tras el primer combate
3. Telemetría         ahora sí hay algo que medir
4. Medallas, ciudadela, el resto
```

---

## 4. En qué somos mejores, y por qué

No es orgullo: es lo que justifica no habernos limitado a copiarles.

| | Diosesmon | Nosotros |
|---|---|---|
| **Duplicación de objetos** | Desconocido | Imposible por diseño: custodia + `FOR UPDATE`, verificado |
| **Arbitraje en la tienda** | Desconocido | **El servidor no arranca** si el catálogo lo permite |
| **Inyección de los kits** | Desconocido | **El servidor no arranca** si pasa del tope |
| **Pérdida por caída** | Desconocido | Entrega diferida: nada se pierde |
| **Auditoría económica** | Desconocido | Libro de asientos completo, cuadre comprobable |
| **Progresión** | Un número | Cinco vías: el progreso es un perfil |
| **Barrera de entrada** | Modpack + 8 GB | Cliente normal |
| **Venta de poder** | Modificadores, habilidad oculta | Descartada |
| **Cinemática** | Vídeo de YouTube | In-game, con voces propias |
| **Pokédex** | Registro | Enseña lo que llegará, y la luna de cada primera captura |

Y una diferencia estructural: **75 invariantes que se ejecutan con una orden**.
Cada sistema económico nuevo añade los suyos antes de desplegarse.

---

## 5. Fases hasta un servidor jugable

Reordenadas según §3.

### FASE A — Que se pueda jugar *(bloqueante)*

| | Tarea | Por qué |
|---|---|---|
| A1 | ~~Elección de inicial~~ ✅ | **Hecho.** Kanto/Johto, una sola vez |
| A2 | ~~Curar~~ ✅ | **Hecho.** Gratis, con cooldown de 30 min |
| A3 | **Caja (PC)** | Requiere `PCLink` con bloque físico: llega con la ciudadela |
| A4 | Libro de bienvenida | Que el jugador sepa qué hacer |

### FASE B — Que haya algo que hacer

| | Tarea |
|---|---|
| B1 | Medallas y gimnasios |
| B2 | ~~Misiones~~ ✅ | 6 de tutorial + 2 diarias |
| B3 | Cazas rotativas |
| B4 | ~~Telemetría económica~~ ✅ | `/luna economia`, con diagnóstico automático |

### FASE C — Que se quiera volver

| | Tarea |
|---|---|
| C1 | Construir la ciudadela |
| C2 | LunaTaxi |
| C3 | Cosméticos y Tesoros |
| C4 | Clanes |
| C5 | Criadero |

### FASE D — Negocio

| | Tarea |
|---|---|
| D1 | Rangos con permisos |
| D2 | Tienda externa y ReportCoins |
| D3 | Wonder Trade, Torre Batalla, competitivo |

---

## 6. Lo que NO vamos a copiar, y por qué

| Suyo | Motivo |
|---|---|
| **Modificadores de estadísticas** | Mejora repetible y sin techo aplicable a cualquier Pokémon. No tiene fondo |
| **PC y Curar de pago** | Son necesidades básicas. Cobrar por curar es cobrar por jugar |
| **Comprar moneda dentro del juego** | Convierte el HUD en una caja registradora |
| **`Rango: ¡Cómpralo!`** | Anuncio permanente en la pantalla |
| **`DIVISION: ¡MUY PRONTO!`** | No se anuncia lo que no existe |
| **Modpack obligatorio de 8 GB** | La mayor barrera de entrada posible |
| **Cinemática en YouTube** | Saca al jugador del juego en su primer minuto |

---

## Next Actions

1. **A1 — Elección de inicial.** Es el bloqueo circular de §3
2. A2 y A3 — Curar y Caja
3. Reordenar `CLAUDE.md` §0 con este orden

## Related Systems

- [Diosesmon](diosesmon-analysis.md) · [Catálogo de interfaces](../ui/interfaces-catalog.md)
- [Backlog](../roadmap/backlog.md)
