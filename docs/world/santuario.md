# El Santuario de Monumentos

## Purpose

Cómo un jugador reclama un **nicho de 3×3** en la zona de Monumentos de la
ciudadela, le pone **foto, título e historia**, y los demás le dejan **honores**.
Es el sistema de homenaje del servidor: un memorial es identidad pura (T1), se
paga con Plata y LunaCoins, y **no da ninguna ventaja de juego** — la misma
línea que D-040 puso a los clanes y D-039 a los cosméticos.

## Dependencies

- [CLAUDE.md](../../CLAUDE.md) — D-007 · D-014 · D-028 · D-033 · D-034 · D-039 · P3 · P5 · P6 · P9-bis
- [estatuas.md](estatuas.md) — los decorativos, de donde sale el NPC
- [gimnasios.md](gimnasios.md) — el patrón de zonas fijas en el mundo
- [dibujado.md](../ui/dibujado.md) — las seis reglas de toda pantalla
- `mod/src/main/java/net/pokereport/luna/santuario/` — todo el sistema
- `mod/src/main/resources/db/migration/V031__santuario.sql` — el esquema

## Current Status

**Construido y compilando (2026-09-04). Sin desplegar** — falta la aprobación
del usuario para publicar, y **los nichos aún no están construidos en el
mundo** (las coordenadas de la config se rellenan cuando lo estén).

| | |
|---|---|
| Migración `V031` | santuario · santuario_foto · santuario_honor · santuario_honor_click |
| Servicio | `SantuarioService` — alquilar, comprar, caducar, honrar, textos, fotos |
| Protección | `SantuarioProteccion` — nadie rompe ni coloca en un nicho ajeno |
| Config | `config/lunaeternal/santuario.json` — geometría de los nichos (aún vacía) |
| Pantallas | `SantuarioScreen` (lista + mi nicho) · `MemorialScreen` (foto, historia, honores) |
| Holograma | `HologramaSantuario` — la foto flota sobre el proyector |
| NPC | Chansey decorativa, `/luna santuario npc` (nivel 4) |
| Moderación | `/luna santuario aprobar|rechazar <id>` · `/luna santuario pendientes` (nivel 3) |
| Autotest | +56 comprobaciones de santuario (economía, honores, fotos, config) |

---

## 1. El recorrido

```
Monumentos                          PokePad
─────────────────                   ─────────────────────────────
Chansey en la entrada                icono «Santuario» (página 2)
   clic derecho ──────────────→      abre SantuarioScreen
                                     lista de nichos:
                                       LIBRE    → ALQUILAR 24 H · COMPRAR
                                       de otro  → VER MEMORIAL
                                       TUYO     → VER MEMORIAL · MI NICHO
                                     MI NICHO:
                                       título + historia + foto (subir/poner/quitar)
clic derecho en el proyector
   de un nicho ocupado ─────────→    MemorialScreen: foto grande, historia,
                                     ♡ total y HONRAR (10 por día)
```

## 2. Las reglas del usuario, tal cual se fijaron (2026-09-04)

| Regla | Dónde vive |
|---|---|
| Alquilar 24 h cuesta **5.000 de Plata** | `SantuarioService.PRECIO_ALQUILER` (provisional) |
| Comprar para siempre cuesta **LunaCoins** | `PRECIO_PERMANENTE = 300` (provisional — el usuario fijó la moneda, no el importe) |
| **Un nicho por jugador**; desde **CAMPEÓN**, más | `SantuarioService.tope(escalon)` — compara contra `Rank.CAMPEON.escalon`, no contra un número escrito |
| **10 honores por jugador y por nicho cada 24 h**, acumulando de por vida | `HONORES_DIA` · `VENTANA_HONOR_MS` · columna `santuario.honores` |
| La foto se sube **desde el PokePad** y **un staff la aprueba** | `subirFoto` → PENDIENTE · `/luna santuario aprobar` |
| NPC = **Chansey**, clic derecho abre la app | `SantuarioNpc` |

### ⚠⚠ Los precios son provisionales, como en la tienda y los sobres

Orden del usuario (la misma que rige la tienda): no se fijan precios hasta el
análisis general de economía. Están **juntos y en un solo sitio**
(`PRECIO_ALQUILER`, `PRECIO_PERMANENTE`) para que aplicarlo sea cambiar dos
números. Los 300 LunaCoins del permanente salen de comparar con el sobre
dorado (50) y el cosmético más barato (1.200): es un T1, no un T4.

## 3. Las decisiones de diseño que no son obvias

### 3.1 · ⚠⚠ El permanente se compra con LunaCoins, y eso NO cruza D-014

D-014 prohíbe **convertir** monedas, no cobrar servicios distintos con monedas
distintas (la tienda ya lo hace: peluches por LunaCoins, el resto por Plata).
Un memorial es **identidad pura** — no protege, no sube nada, no desbloquea
nada —, o sea T1, la misma categoría que los cosméticos de D-039. ⚠ Y por eso
**el alquiler NO se devuelve al pasarse a permanente**: devolverlo crearía una
conversión Plata → LunaCoins por la puerta de atrás.

### 3.2 · ⚠⚠ Uno no se honra a sí mismo

El honor es el gesto **de los demás**. Si el dueño pudiera, su presupuesto
diario sería un +10 garantizado a su propio contador y el número dejaría de
significar nada. Sin recompensa económica no es un agujero de dinero, pero sí
de sentido. (Decisión anotada para el usuario; se cambia en una línea si lo
prefiere al revés.)

### 3.3 · ⚠⚠ Honrar NO da nada, a propósito

Cero recompensa. Con `online-mode=false` pendiente (B-004), una multicuenta
podría honrar diez veces por cuenta; sin premio no hay incentivo para hacerlo,
y el número sigue significando lo que debe: cuánta gente pasó y dejó algo.

### 3.4 · ⚠⚠ La foto es del servidor, el cliente solo la dibuja

La foto llega del PokePad, el servidor la **decodifica, reescala a 512 y
recodifica a PNG** (se va cualquier EXIF —localización GPS incluida— y
cualquier payload escondido), la guarda en `config/lunaeternal/fotos/<sha1>.png`
y la deja **PENDIENTE**. Un staff la aprueba y solo entonces se puede colocar.
El cliente la pide por sha1, la cachea en disco y la pinta como holograma. **No
registra ni un bloque, objeto ni entidad nuevos**: el quad se dibuja a mano
sobre la posición del proyector que manda el servidor.

### 3.5 · ⚠⚠ El proyector holo es nuestro, no del mod de cartas

El clic derecho en el proyector **siempre** abre el memorial (si el nicho está
reclamado) y nunca el menú de inventario del mod. El proyector es el pedestal
del memorial; meterle una carta es una idea futura, no un cajón abierto hoy.

### 3.6 · ⚠ Las coordenadas viven en la config, no en la base

`config/lunaeternal/santuario.json` declara cada nicho (id, nombre, caja 3×3,
proyector). La base guarda **solo la reclamación**. Mover un nicho no toca la
economía, y una reclamación sobrevive a que se redecore Monumentos. **La
config se valida al arrancar y revienta si está mal**: dos ids iguales, nichos
solapados o un proyector fuera de su caja son fallos que NO dan ningún error —
dan un hueco que alguien descubre rompiendo el memorial de otro.

```json
{
  "nichos": [
    { "id": "nicho_01", "nombre": "Nicho del Alba",
      "min": [x1, y1, z1], "max": [x2, y2, z2],
      "proyector": [px, py, pz] }
  ]
}
```

## 4. Los invariantes (autotest)

Cada uno caza un fallo que **no daría ningún error**:

| Invariante | El fallo que caza |
|---|---|
| El alquiler cobra exactamente 5.000 de Plata | un precio que derivó y cobra de más o de menos |
| La compra cobra LunaCoins, no Plata | las dos monedas cambiadas, y D-014 roto en silencio |
| El alquiler expira a 24 h ±5 min | un expira mal escrito = permanente o instantáneo |
| Lo permanente no expira nunca | un `permanente` que en realidad alquila |
| Sin rango CAMPEÓN+, un segundo nicho se rechaza | el tope se cayó solo |
| Uno no se honra a sí mismo | el +10 diario garantizado |
| El clic reenviado no suma dos honores (idem único) | un paquete repetido infla el contador público |
| El total no pasa del presupuesto gastado | el tope de 10 convertido en 11 |
| `honores == COUNT(santuario_honor_click)` mientras está reclamado | un honor fantasma o uno comido |
| El barrido libera de verdad (dueño, memorial y honores a cero) | un nicho ocupado para siempre |
| Una imagen rota/gigante no entra; la cuarta pendiente se rechaza | un cliente que llena el disco (P6) |
| Una foto ajena/pendiente/rechazada no se puede colocar | un holograma que el moderador nunca vio |
| Ningún nicho tiene colgada una foto que no esté APROBADA (consulta) | el mismo, directo en la base |
| La config rechaza ids duplicados, solapes y proyector fuera | geometría que miente |
| Cada nicho de la config tiene su fila en la base | un nicho que se ve y no se puede alquilar |

## 5. ⚠⚠ Despliegue (cuando el usuario lo apruebe)

El procedimiento de siempre ([despliegue.md](../technical/despliegue.md) §2),
con tres avisos propios:

1. **Publicar el manifiesto ANTES de reiniciar.** El sistema no registra
   bloques, objetos ni entidades (el proyector es del mod de cartas, ya
   desplegado; el holograma es dibujado a mano), así que en teoría no echa a
   nadie — pero el jar viaja con protocolo nuevo: un cliente viejo pierde la
   app, un servidor nuevo con clientes viejos… no pasa nada grave, y aun así
   el orden manda.
2. **`python tools/gen_pokepad.py`** antes del build: regenera el icono desde
   `arte/pokepad/icons/icon_santuario.png` (el PNG commiteado es un port del
   sangrado de alfa; el generador es el canónico).
3. **Después del reinicio**: `/luna autotest` en verde, y construir los nichos
   + rellenar `config/lunaeternal/santuario.json` + `/luna santuario npc`
   para la Chansey.

## Next Actions

1. Aprobar el despliegue (F5) y verificar en el juego con dos cuentas.
2. Construir los nichos en Monumentos y rellenar la config.
3. Sonido de ambiente propio (OGG CC0) — hoy la campanilla de amatista
   (vainilla) hace de voz del memorial.
4. Confirmar o ajustar `PRECIO_PERMANENTE` (300 LunaCoins, provisional).
