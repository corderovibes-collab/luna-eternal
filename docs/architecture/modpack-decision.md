# Decisión de arquitectura base — `ARCH-001` / B-002

## Purpose

Resolver la pregunta estructural del proyecto: **CobbleVerse, Cobblemon oficial
con addons mínimos, o Cobblemon con sistemas propios.**

## Dependencies

- [`../analysis/cobblemon-audit.md`](../analysis/cobblemon-audit.md)
- [`../analysis/current-server-audit.md`](../analysis/current-server-audit.md)

## Related Documents

- [`../game-design/vision.md`](../game-design/vision.md)
- [`../technical/infrastructure.md`](../technical/infrastructure.md)

## Current Status

**RECOMENDACIÓN FIRME**, pendiente de ratificación del usuario. La decisión no
se apoya en preferencia técnica sino en una **restricción legal verificada**.

## Last Decision

Pendiente de registrar como D-006.

---

## 1. El hallazgo que cierra la discusión

CobbleVerse se publica bajo **All Rights Reserved**. Sus términos incluyen esta
cláusula, citada literalmente de su página oficial de Modrinth (2026-08-11):

> *"You **MAY NOT** use COBBLEVERSE's custom structures, trainers, or datapacks
> on any public server that features a commercial shop (ranks, perks, virtual
> items, or monetary transactions)."*

Y además:

> *"**No Clones or Derivative Works:** Creating modified versions, 're-branded'
> variants, or clones of COBBLEVERSE for public distribution without prior
> explicit written permission from the COBBLEVERSE team is a violation of our
> license."*

> *"COBBLEVERSE includes mods and assets from independent third-party creators
> under non-transferable or non-commercial licenses. Simply running our modpack
> on a server **does not automatically grant you commercial rights** over those
> third-party mods."*

### Por qué esto es terminal para nuestro proyecto

El brief planifica explícitamente monetización: **rangos** (§21, con un apartado
llamado *monetization*), cosméticos de pago, títulos y prefijos. Eso es
literalmente *"ranks, perks, virtual items"*.

| Requisito del brief | Compatible con CobbleVerse |
|---|---|
| Tienda de rangos y cosméticos (§21) | ❌ **Prohibido explícitamente** |
| Identidad propia fuerte (§14) | ❌ Modificarlo = obra derivada, prohibida sin permiso escrito |
| Control total de la progresión (§3) | ❌ Su progresión es ARR y no se puede alterar |
| Servidor público | ⚠️ Solo con cumplimiento estricto de terceros |

**No es una cuestión de qué preferimos. Con monetización, CobbleVerse no es una
opción legal.**

### ⚠️ Implicación para el servidor de producción actual

`2a0a48ff` corre CobbleVerse 1.7.42 hoy. **Si PokeReport ya vende rangos,
perks o cualquier artículo virtual, podría estar incumpliendo esos términos
ahora mismo.**

No sé si hay monetización activa — no lo he comprobado y no está en la
documentación. **Hay que verificarlo** (`SEC-003`). Si la hay, es un riesgo
legal presente, no futuro: la vía limpia es pedir permiso escrito al equipo de
CobbleVerse o retirar el contenido afectado.

---

## 2. La matriz

Aun ignorando la licencia, la comparación no favorece a CobbleVerse para
*nuestros* objetivos:

| | **A · CobbleVerse** | **B · Cobblemon + addons mínimos** | **C · Cobblemon + mod propio** |
|---|---|---|---|
| **Legalidad con monetización** | ❌ Prohibido | ✅ Según licencia de cada mod | ✅ Nuestro código, MPL permite addon cerrado |
| **Time-to-playable** | ✅ Inmediato | 🟡 Semanas | 🔴 Meses |
| **Control de progresión** | ❌ De ellos | 🟡 Del autor de cada addon | ✅ **Total** |
| **Identidad propia** | ❌ Prohibida por licencia | 🟡 Limitada | ✅ Total |
| **Rendimiento** | 🔴 ~134 mods, 14 GB en producción | 🟡 Ajustable | ✅ Un mod sustituye a decenas |
| **Mantenimiento** | 🟡 Lo hacen ellos… | 🔴 N terceros, N calendarios | 🟡 Nuestro, pero predecible |
| **Riesgo en MC 1.22** | 🔴 Esperar a que actualicen | 🔴 Esperar al más lento de N | ✅ Depende solo de Cobblemon |
| **Coste de desarrollo** | ✅ Nulo | 🟡 Bajo | 🔴 Alto |
| **Gimnasios/medallas listos** | ✅ Sí | 🟡 Addons sueltos | ❌ Hay que hacerlos |

**El riesgo de la columna B** merece énfasis: encadenar 10 addons significa que
la migración a Minecraft 1.22 va al ritmo del autor **más lento**, y basta que
uno abandone para bloquear el servidor entero. Es exactamente el problema que
tiene hoy producción con 134 mods.

---

## 3. Recomendación: **C, por fases**

```
Base            Cobblemon oficial 1.7.3 (MPL 2.0)
Contenido       datapack + resourcepack propios
                especies, spawns, rarezas, ciclo, señuelos, estructuras
Servicios       mods maduros con licencia compatible
                LuckPerms · Ledger · economía · NPCs/trainers
Identidad       UN mod de servidor propio  (D-005)
                progresión · GTS · colección · UI · gating · anti-abuso
```

**Un solo mod propio, no cincuenta dependencias.** Es el principio P5 llevado a
su conclusión: cuando la lista de mods de terceros crece lo suficiente, escribir
uno propio *reduce* la dependencia en vez de aumentarla.

### Por qué C es más barato de lo que parece

La auditoría del jar (`cobblemon-audit.md` §4-bis) cambia la ecuación. El motor
de "un mundo con horarios" **ya existe dentro de Cobblemon**: `moonPhase`,
`timeRange`, `isRaining`, `structures`, `minY/maxY`, `neededNearbyBlocks`,
`minLureLevel` y 4 buckets de rareza. Todo por datapack, **sin una línea de
Java**.

Es decir: **el pilar de identidad del proyecto es trabajo de diseño y datos, no
de programación.** El mod propio solo tiene que cubrir lo que Cobblemon
realmente no puede — que es un conjunto acotado y conocido:

1. Leer el equipo Pokémon del jugador (gating)
2. GTS y mercado
3. Progresión transversal y desbloqueos
4. Calendario del servidor (ritmo largo)
5. UI de navegación
6. Anti-abuso económico

### Y ya sabemos hacer las dos partes

- `addon-luna/` demuestra que sabemos crear especies propias por datapack
- Los generadores en Python demuestran la disciplina de "todo se regenera"

---

## 4. Qué se pierde y cómo se cubre

| Se pierde de CobbleVerse | Cómo se cubre |
|---|---|
| Gimnasios y medallas | Mod propio + NPCs. **Es contenido de identidad: queremos los nuestros** |
| Estructuras exclusivas | `structures` nativo + generación propia; WorldEdit ya instalado |
| Progresión lista | Es exactamente lo que el brief nos pide diseñar |
| Dimensiones extra | Aplazable; no es del core loop |
| Tiempo | **El coste real.** Meses en vez de semanas |

**El tiempo es el precio honesto de esta decisión.** No es un detalle menor: es
la diferencia entre abrir en semanas o en meses. Pero el brief pide
explícitamente un producto con identidad y monetización, y por licencia esas
dos cosas **no caben** en la opción A.

---

## 5. Plan por fases

Para que "meses" no signifique "meses sin nada jugable":

| Hito | Contenido | Jugable |
|---|---|---|
| **M1** | Cobblemon limpio + datapack de spawns propio + economía base | Sí — capturar y explorar con reglas nuestras |
| **M2** | Mod propio v1: progresión, colección, gating | Sí — con progresión real |
| **M3** | GTS y mercado | Sí — economía completa |
| **M4** | Gimnasios, quests, mundo | Producto completo |
| **M5** | Cosméticos, rangos, endgame | Monetización |

Cada hito es jugable. No hay un valle de meses sin servidor.

---

## 6. Riesgos de la recomendación

| Riesgo | Mitigación |
|---|---|
| **Se subestima el esfuerzo del mod propio** | Alcance cerrado a los 6 puntos de §3. Todo lo demás, datapack |
| Nadie del equipo escribe Kotlin/Java | ⚠️ **A confirmar con el usuario.** Es el supuesto más frágil de este plan |
| Producción sigue con CobbleVerse mientras tanto | Correcto: son proyectos separados (D-001) |
| Cobblemon rompe la API en 1.8 | MPL y código abierto: podemos leer y adaptarnos |

> El segundo riesgo es el que puede tumbar el plan. **Si no hay capacidad de
> desarrollo en Java/Kotlin, la opción C no es viable** y hay que replantear
> hacia B con licencias verificadas una por una.

---

## Next Actions

1. **Ratificar o rechazar** esta recomendación → D-006
2. `SEC-003` — comprobar si producción monetiza hoy con CobbleVerse instalado
3. Confirmar capacidad de desarrollo en Java/Kotlin
4. Si se ratifica: `ARCH-002`, diseño del mod propio
5. Si se rechaza: matriz de licencias de la opción B, mod por mod

## Related Systems

- [Auditoría de Cobblemon](../analysis/cobblemon-audit.md)
- [Visión](../game-design/vision.md) · [Core loop](../game-design/core-loop.md)
