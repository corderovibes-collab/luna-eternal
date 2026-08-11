# Modelo de datos y persistencia

## Purpose

Definir cómo se almacena el estado del servidor **antes** de construir ningún
sistema encima. Es la decisión más cara de revertir del proyecto: cambiar el
modelo de datos con jugadores dentro significa migrar economía viva.

## Dependencies

- [`../economy/economy-overview.md`](../economy/economy-overview.md)
- [`infrastructure.md`](infrastructure.md)

## Related Documents

- [`../architecture/modpack-decision.md`](../architecture/modpack-decision.md)
- `../trading/gts.md` — pendiente

## Current Status

**PROPUESTA con base verificada.** Base de datos MariaDB provisionada y
funcionando en el servidor de desarrollo el 2026-08-11.

## Last Decision

D-009 — MariaDB como almacén principal, no ficheros planos.

---

## 1. Decisión: base de datos relacional, no ficheros

### El hallazgo que la habilita

El plan de hosting incluye **4 bases de datos MariaDB por servidor**, sin coste
extra y sin usar. Provisionada y verificada en desarrollo:

```
s11945_luna @ s12.mia.us.tarohosting.lat:3306
```

### Por qué los ficheros planos no valen

Producción guarda hoy todo en ficheros (JSON, NBT, H2 aislado para LuckPerms).
Para un evento de tres horas es suficiente. **Para una economía persistente,
no**, y por una razón concreta:

> Una venta en el GTS es *"quitar el Pokémon al vendedor · quitar el dinero al
> comprador · dar el Pokémon al comprador · dar el dinero al vendedor"*.
> **Las cuatro cosas o ninguna.**

Si el servidor se cae entre la segunda y la tercera, con ficheros planos el
Pokémon **desaparece** o se **duplica**. No hay forma de evitarlo sin
transacciones. Y una duplicación en un MMORPG no es un bug: es el fin de la
economía, porque se propaga por el mercado antes de que nadie la detecte.

Una base de datos relacional da ACID. Es el motivo entero de usarla.

| | Ficheros planos | MariaDB |
|---|---|---|
| Transacciones atómicas | ❌ | ✅ |
| Consultas del GTS (filtrar por IV, precio…) | ❌ Cargar todo en RAM | ✅ Índices |
| Auditoría e historial | 🟡 Manual | ✅ Nativo |
| Acceso concurrente | ❌ Corrupción | ✅ Bloqueos |
| Backup consistente | ❌ Copia a medias | ✅ `mysqldump` |
| Multi-servidor futuro | ❌ | ✅ |

---

## 2. Las cinco reglas del esquema

Decisiones transversales. Cada una previene una clase entera de fallos.

### R1 · Clave sustituta para el jugador

```sql
player (
  player_id   BIGINT UNSIGNED PK AUTO_INCREMENT,   -- interno, inmutable
  mc_uuid     CHAR(36)  UNIQUE,                    -- el que da Minecraft
  username    VARCHAR(16),                         -- cambia; NUNCA es clave
  ...
)
```

**Todo lo demás apunta a `player_id`, nunca a `mc_uuid` ni al nombre.**

Esto resuelve tres problemas de golpe:

1. **Cambios de nombre** no rompen nada.
2. **La decisión de `online-mode` deja de ser estructural.** Hoy producción va
   en offline (UUID v3, derivado del nombre); si algún día se pasa a online, los
   UUID cambian — pero solo hay que actualizar **una columna**, no toda la base.
3. Permite fusionar cuentas si hiciera falta.

> Es una columna extra que cuesta nada y evita una migración masiva. La decisión
> `SEC-006` sigue siendo importante por el anti-abuso, pero **ya no bloquea el
> desarrollo**: el esquema es indiferente a ella.

### R2 · El dinero es entero, nunca decimal

```sql
balance BIGINT NOT NULL DEFAULT 0    -- en unidades mínimas
```

`FLOAT` y `DOUBLE` acumulan error de redondeo. En una economía, el error de
redondeo **es** un exploit: se repite la operación hasta que el redondeo genera
dinero. `DECIMAL` funcionaría pero es más lento y no hace falta si no hay
céntimos.

### R3 · El dinero se lleva por asiento, no por saldo

**El saldo no es la verdad. Es una caché.** La verdad es el libro de asientos:

```sql
ledger_entry (
  entry_id        BIGINT PK,
  player_id       BIGINT NOT NULL,
  currency        ENUM('POKEDOLLAR','MARK'),
  delta           BIGINT NOT NULL,      -- + o -
  balance_after   BIGINT NOT NULL,      -- para auditar sin recalcular
  reason          VARCHAR(48) NOT NULL, -- 'gts_sale', 'shop_buy', 'wild_catch'
  ref_type        VARCHAR(32),          -- entidad relacionada
  ref_id          BIGINT,
  idempotency_key CHAR(36) UNIQUE,      -- ver R4
  created_at      DATETIME(3) NOT NULL,
  INDEX (player_id, created_at),
  INDEX (reason, created_at)
)
```

Ventajas que no da una columna `balance` suelta:

- **Auditoría completa**: de dónde salió cada moneda del servidor
- **Detección de duplicación**: si `balance ≠ Σ delta`, algo falló
- **Rollback selectivo**: revertir un exploit sin tocar al resto
- **Telemetría gratis**: `ECO-001` §7 pide medir dinero creado y destruido por
  fuente. Es un `GROUP BY reason`

El saldo vive en `player_economy.balance` y se actualiza **en la misma
transacción** que el asiento. Rápido de leer, verificable siempre.

### R4 · Idempotencia en toda operación económica

```sql
idempotency_key CHAR(36) UNIQUE
```

Un doble clic, un reintento de red o un lag pueden ejecutar la misma compra dos
veces. Con clave de idempotencia, el segundo intento **falla por índice único**
en vez de duplicar. Es una línea de esquema que elimina una familia entera de
exploits.

### R5 · Máquinas de estado explícitas, nunca booleanos

```sql
state ENUM('ACTIVE','RESERVED','SOLD','CANCELLED','EXPIRED')
```

Nada de `is_sold` + `is_active` + `is_cancelled`: tres booleanos permiten ocho
combinaciones, de las cuales cinco son estados imposibles que tarde o temprano
alguien alcanza. Un `ENUM` solo permite estados válidos.

Encaja además con el sistema de bloqueos que pide el brief §27
(`AVAILABLE / LOCKED / UNLOCKING / UNLOCKED / DISABLED / ERROR / COOLDOWN`).

---

## 3. Entidades principales

### Identidad y progresión

```
player                player_id · mc_uuid · username · first_seen · last_seen
player_economy        player_id · currency · balance          (caché de R3)
ledger_entry          el libro de asientos (R3)
player_progression    player_id · vía · nivel · xp · reputación
player_unlock         player_id · unlock_id · state · unlocked_at
achievement           catálogo
player_achievement    player_id · achievement_id · progreso · completed_at
```

### Colección — solo eventos, no copia de datos

**Cobblemon ya es el dueño de los Pokémon.** No duplicamos su almacén: sería
dos fuentes de verdad y se desincronizarían.

```
pokedex_entry         player_id · species · form · seen · caught · shiny_caught
                      first_caught_at · best_ivs
collection_event      append-only: qué, cuándo, dónde, en qué fase lunar
```

Guardamos **observaciones**, no el Pokémon. Barato, indexable, y suficiente para
Pokédex, logros y estadísticas.

### GTS — aquí sí necesitamos custodia

Es la excepción, y hay que entender por qué:

> **Mientras un Pokémon está listado, no puede estar en poder del vendedor.**
> Si sigue en su PC, puede intentar operar con él a la vez que se vende. Ese es
> el vector de duplicación número uno de todos los GTS mal hechos.

```sql
gts_listing (
  listing_id      BIGINT PK,
  seller_id       BIGINT NOT NULL,
  state           ENUM('ACTIVE','RESERVED','SOLD','CANCELLED','EXPIRED'),
  price           BIGINT NOT NULL,
  currency        ENUM('POKEDOLLAR'),        -- las Marcas NO comercian
  payload         LONGBLOB NOT NULL,         -- Pokémon serializado en custodia
  payload_hash    CHAR(64) NOT NULL,         -- integridad
  -- columnas desnormalizadas SOLO para filtrar/ordenar:
  species, form, level, nature, gender, is_shiny, ability,
  iv_hp, iv_atk, iv_def, iv_spa, iv_spd, iv_spe, iv_total, tera_type,
  listed_at, expires_at, buyer_id, sold_at,
  INDEX (state, species, price),
  INDEX (state, iv_total),
  INDEX (seller_id, state)
)
```

**Las columnas desnormalizadas son deliberadas.** El brief §20 pide filtrar por
especie, nivel, naturaleza, género, shiny, IV, habilidad y Tera. Buscar eso
dentro de un blob serializado es inviable; en columnas indexadas es inmediato.
El blob sigue siendo la verdad; las columnas son el índice.

**El flujo de venta, en una sola transacción:**

```
BEGIN
  SELECT ... FROM gts_listing WHERE listing_id=? AND state='ACTIVE' FOR UPDATE
  -- si no devuelve fila: otro se adelantó. Abortar limpiamente
  UPDATE gts_listing SET state='RESERVED', buyer_id=?
  INSERT ledger_entry (comprador, -precio)     idempotency_key=?
  INSERT ledger_entry (vendedor, +precio-tasa) idempotency_key=?
  INSERT ledger_entry (sistema,  +tasa)        -- el sink de ECO-001
  UPDATE player_economy ... (ambos)
  UPDATE gts_listing SET state='SOLD', sold_at=NOW()
COMMIT
→ solo entonces: entregar el Pokémon al comprador
→ si la entrega falla: queda en 'SOLD' sin reclamar, recuperable. Nunca perdido
```

`FOR UPDATE` es lo que impide que dos compradores se lleven el mismo Pokémon.

### Resto de entidades

```
quest · quest_progress · hunt · hunt_progress
clan · clan_member · clan_treasury
cosmetic · player_cosmetic · rank · player_rank
pokestop · pokestop_cooldown        (cooldown SIEMPRE en servidor)
world_location · discovery
purchase                            compras con dinero real, auditoría aparte
```

---

## 4. Rendimiento

El brief §30 lo exige. Reglas concretas:

| Regla | Motivo |
|---|---|
| **Nunca consultar la BD en el bucle de tick** | 20 tps × N jugadores mata cualquier base |
| **Estado del jugador en memoria al entrar** | Una lectura por sesión, no por acción |
| **Escritura diferida en lotes** | Cada 15-30 s y al salir. `cobbledollars` ya usa 15 s |
| **Excepción: el dinero se escribe al momento** | Perder una transacción económica no es aceptable |
| **Índices sobre lo que se filtra** | El GTS es la consulta más cara del servidor |
| **Paginar siempre** | Nunca `SELECT *` sobre listados |
| **Pool de conexiones** (HikariCP) | Abrir conexiones por operación es carísimo |

**El compromiso importante:** todo lo demás puede ser diferido; **el dinero y el
GTS, no**. Un progreso de quest perdido en un cierre inesperado es una molestia.
Una transacción económica perdida es un agujero.

---

## 5. Backups — y aquí hay un problema nuevo

`INF-002` ya señaló que `AdvancedBackups` copia el mundo. **Pero la base de
datos MariaDB no vive en el servidor de Minecraft**: está en un host aparte.

> **`AdvancedBackups` NO respalda la base de datos.** Si añadimos MariaDB sin
> más, creamos un punto de fallo sin copia — y encima el que guarda la economía.

Requisito, no sugerencia:

```
mysqldump programado → almacenado FUERA del hosting
frecuencia ≥ la del backup del mundo (diaria)
restauración probada, igual que INF-002
```

Y algo que suele olvidarse: **mundo y base de datos deben respaldarse
coordinados**. Restaurar un mundo del martes con una base del jueves deja
Pokémon que existen en la base pero no en el mundo, y al revés.

Nueva tarea: `INF-007`.

---

## 6. Migraciones

Con jugadores dentro, el esquema cambiará. Desde el primer día:

```
schema_version (version INT PK, applied_at DATETIME, description VARCHAR)
```

- Migraciones **numeradas, versionadas en git, idempotentes**
- Cada una con su **rollback escrito antes de aplicarla**
- Se prueban en desarrollo **con datos reales copiados**, nunca en vacío
- Nunca se aplica una migración sin backup verificado inmediatamente antes

Es la parte de la *Definition of Done* (CLAUDE.md §4) que casi nadie cumple y
que evita los desastres más caros.

---

## 7. Riesgos

| Riesgo | Mitigación |
|---|---|
| **La BD está en otro host** → latencia y caída de red | Pool de conexiones, timeouts cortos, degradación elegante: si la BD cae, el servidor bloquea operaciones económicas en vez de perder datos |
| Base de datos sin backup | `INF-007`, bloqueante antes de producción |
| Límite de 4 bases | Suficiente: 1 producción + 1 desarrollo + 2 de reserva |
| Sobreingeniería | El esquema cubre lo diseñado, no lo imaginado. Se amplía con migraciones |
| Nadie sabe SQL/JDBC en el equipo | ⚠️ Mismo riesgo que `B-006`. Se acumula |

> El primero merece atención: **si la base de datos no responde, el servidor no
> debe seguir jugando como si nada.** Debe bloquear GTS, tienda y transferencias
> y avisar. Perder disponibilidad es recuperable; perder consistencia económica,
> no.

---

## Next Actions

1. `INF-007` — backup de la base de datos, coordinado con el del mundo
2. `TRD-001` — GTS sobre este esquema
3. `ECO-003` — telemetría: consultas sobre `ledger_entry`
4. Esquema SQL inicial y primera migración

## Related Systems

- [Economía](../economy/economy-overview.md) · [Infraestructura](infrastructure.md)
- [Decisión de arquitectura](../architecture/modpack-decision.md)
