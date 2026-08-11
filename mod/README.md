# Mod — Luna Eternal

Mod de servidor de PokeReport: Luna Eternal. **Solo servidor**, sin componente
de cliente obligatorio.

- Minecraft **1.21.1** · Fabric · **Java 21**
- Persistencia en **MariaDB** (D-009)

Diseño y justificación en [`../docs/technical/data-model.md`](../docs/technical/data-model.md).

---

## Compilar

```bash
./build.sh
```

**Usa siempre `build.sh`, no `./gradlew` a secas.** Hay un
`~/.gradle/gradle.properties` global que fija JDK 17 (del proyecto Backrooms,
que usa MC 1.20.1). Minecraft 1.21.1 exige JDK 21 y Loom comprueba la JVM de
Gradle, no el toolchain. `build.sh` lo sobrescribe sin tocar el fichero global.

El jar sale en `build/libs/lunaeternal-<version>.jar`.

## Configurar

Al primer arranque el mod crea `config/lunaeternal.properties` y **detiene el
servidor** a propósito. Rellena las credenciales y reinicia.

```properties
db.host=
db.port=3306
db.name=
db.user=
db.password=
db.pool.size=6
db.failFast=true
```

`db.failFast=true` significa que **el servidor no arranca si la base de datos
no responde**. Es lo correcto: arrancar sin persistencia pierde progreso de los
jugadores en silencio.

> Este fichero contiene credenciales. Está fuera del repositorio y debe
> seguir estándolo.

## Comandos de verificación

No son la interfaz final —esa es El Almanaque— sino comprobaciones del
circuito de persistencia.

| Comando | Permiso | Qué hace |
|---|---|---|
| `/luna saldo` | jugador | Muestra PokéDólares y Marcas |
| `/luna dar <moneda> <n>` | nivel 3 | Ingresa dinero |
| `/luna auditar` | nivel 3 | Comprueba que saldo == suma del libro |
| `/luna estado` | nivel 3 | Jugadores en caché |

`/luna auditar` es el importante: si alguna vez da descuadre, hay duplicación o
pérdida de dinero y hay que investigarlo.

## Reglas de código

| | |
|---|---|
| Nunca consultar la BD en el hilo del servidor | usar `LunaEternal.submit()` |
| Nunca `float`/`double` para dinero | `long` / `BIGINT` |
| Toda operación económica lleva clave de idempotencia | R4 |
| El saldo se actualiza en la misma transacción que el asiento | R3 |
| Operaciones compuestas comparten `Connection` | `applyInTransaction` |
| Bloquear filas en orden ascendente de `player_id` | evita interbloqueos |

## Migraciones

Los `.sql` viven en `src/main/resources/db/migration/` y se registran en el
array `MIGRATIONS` de `Database.java`. Se aplican una sola vez, en orden y
dentro de una transacción.

**Nunca se edita una migración ya aplicada.** Se añade otra nueva.
