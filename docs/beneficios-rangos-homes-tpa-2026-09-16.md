# Sistema de Beneficios de Rango, Homes, Pwarps y TPA — 16/09/2026

## Purpose
Documentar la arquitectura técnica, reglas de diseño, integración con Cobblemon, esquema de base de datos MariaDB y despliegue del sistema de rangos comerciales (Élite, Campeón, Maestro, Leyenda) y jugadores sin rango (Entrenador).

## Dependencies
- [`docs/README.md`](README.md)
- [`docs/auditoria-fauna-2026-09-16.md`](auditoria-fauna-2026-09-16.md)
- [`docs/economy/monetization.md`](economy/monetization.md)
- [`docs/technical/data-model.md`](technical/data-model.md)

## Current Status
✅ **Desplegado y verificado en producción (Pterodactyl + MariaDB + Launcher Pack).**  
Sustituye la propuesta preliminar de mods externos (HuskHomes / LuckPerms) por una solución 100% nativa integrada en el mod `lunaeternal`.

---

## 1. Matriz de Rangos y Beneficios Acumulados

Los beneficios y comandos son **estrictamente acumulativos** (cada rango hereda todos los comandos y facultades de los rangos precedentes).

| Rango | Comandos Exclusivos Acumulados | Límite Homes | Límite Pwarps | Beneficios y Comandos Clave |
|---|:---:|:---:|:---:|---|
| **Entrenador** *(Sin Rango)* | **0** | 0 | 0 | Acceso F2P básico. Curación en Enfermería de Ciudadela o bloques curadores; PC física en el mundo. |
| **Élite** | **2** | 3 | 1 | • `/pokeheal` (reutilización: 15 min)<br>• `/craft` (mesa de crafteo 3×3 portátil) |
| **Campeón** | **5** | 4 | 1 | • `/pokeheal` (reutilización reducida a **5 min**)<br>• `/craft`<br>• `/checkspawns` (análisis de probabilidad de spawn Cobblemon local)<br>• `/enderchest` (acceso remoto a su cofre de Ender)<br>• `/basura` (interfaz segura para incinerar ítems no deseados)<br>• **`/tpa`** (solicitud de teletransporte a otro jugador) |
| **Maestro** | **10** | 5 | 2 | • `/pokeheal` (**Ilimitado**, sin tiempo de espera / anti-spam 5s)<br>• Todos los de Campeón (`/craft`, `/checkspawns`, `/enderchest`, `/basura`, `/tpa`)<br>• **`/pc` remoto exclusivo** (abre la interfaz Cobblemon nativa de almacenamiento de cajas sin colocar bloques)<br>• **`/ivs [1-6]`** (diagnóstico detallado de valores genéticos individuales y % competitivo)<br>• **`/evs [1-6]`** (diagnóstico de puntos de esfuerzo entrenados y total / 510)<br>• `/anvil` (yunque virtual con consumo normal de experiencia)<br>• `/back` (retorno al último punto antes de un teletransporte) |
| **Leyenda** | **14** | 7 | 3 | • **Protección total de XP al morir** (conserva nivel, barra y total exacto sin soltar orbes ni duplicación)<br>• Todos los 10 comandos de Maestro (`/pokeheal` ilimitado, `/pc`, `/ivs`, `/evs`, `/tpa`, `/checkspawns`, etc.)<br>• `/stonecutter` (cortapiedras portátil)<br>• `/smithing` (mesa de herrería portátil)<br>• `/grindstone` (afiladora portátil para desencantar/reparar)<br>• `/rename <nuevo nombre>` (renombrado cosmético del ítem en mano) |

---

## 2. Sistema de Teletransporte entre Jugadores (`/tpa`)

Diseñado con un enfoque centrado en la seguridad, la prevención de abusos en combate y la accesibilidad para toda la comunidad.

### Flujo de Funcionamiento
1. **Emisión de Solicitud**:
   - Comando: `/tpa <jugador>`.
   - Requisito: Exclusivo para rangos **Campeón**, **Maestro** y **Leyenda**.
   - Validación: Bloqueado si el emisor o el objetivo se encuentran en combate Cobblemon o si el objetivo ya tiene una petición pendiente.
2. **Recepción Universal**:
   - **Cualquier jugador**, independientemente de su rango (incluyendo Entrenadores sin rango), puede recibir solicitudes. Esto permite que los jugadores con rango visiten y ayuden a cualquier compañero.
   - En el chat del destinatario se muestra un mensaje interactivo con botones clickeables:
     - `[ACEPTAR]` → ejecuta `/tpaccept` (o `/tpyes`).
     - `[RECHAZAR]` → ejecuta `/tpdeny` (o `/tpno`).
3. **Warmup y Teletransporte**:
   - Una vez aceptada la solicitud, el emisor entra en estado de espera inmóvil de **5 segundos** gestionado por `Espera.pedir`.
   - Si el emisor se mueve del bloque o sufre daño, el teletransporte se aborta automáticamente por seguridad.
   - Si la preparación concluye exitosamente, el jugador es trasladado a las coordenadas del objetivo (`Traslado.ir`).
4. **Cancelación y Expiración**:
   - Expiración pasiva a los **60 segundos** de inactividad.
   - Cancelación activa por parte del emisor con `/tpcancel`.

---

## 3. Sistema de Hogares (`/home`) y Lugares Públicos (`/pwarp`)

Para evitar la instalación de mods externos pesados que ralenticen el servidor o presenten incompatibilidades con dimensiones personalizadas, se desarrolló el motor nativo `HomeService` y `HomeCommands` con almacenamiento directo en MariaDB.

### Reglas de Dimensión
- **Creación (`/sethome [nombre]`)**:
  - **Exclusivamente permitido en la dimensión Hogar** (`lunaeternal:hogar` / `minecraft:overworld`).
  - Si un jugador intenta guardar un home en cualquier otra dimensión (`salvaje`, `ciudadela`, arenas, dimensiones de minería), el servidor rechaza la acción informando que solo se permiten hogares en el mundo de construcción y convivencia comunitaria.
- **Viaje (`/home [nombre]`)**:
  - Permitido desde **cualquier dimensión**. Un jugador que esté explorando el Salvaje o en la Ciudadela puede ejecutar `/home` para regresar de forma segura a su residencia en Hogar.
  - Requiere **5 segundos** inmóvil sin recibir daño ni estar en combate.

### Comandos de Hogar
- `/sethome [<nombre>]`: Guarda la posición actual (rotación y coordenadas). Si no se indica nombre, se usa `home`. Verifica el cupo disponible según el rango en MariaDB.
- `/home [<nombre>]`: Inicia el traslado hacia el hogar especificado.
- `/delhome <nombre>`: Elimina un hogar registrado liberando el cupo.
- `/homes`: Lista todos los hogares del jugador con formato estético, indicando coordenadas y estado del cupo (ej. `Hogares (3/5)`).

### Lugares Públicos (`/pwarp`)
Permite a los jugadores compartir coordenadas de tiendas, granjas o construcciones con el resto de la comunidad:
- `/setpwarp <nombre>`: Publica un hogar como punto de teletransporte público (sujeto al límite de pwarps del rango).
- `/delpwarp <nombre>`: Retira la visibilidad pública del hogar.
- `/pwarp <creador> <nombre>`: Teletransporta a cualquier jugador al pwarp indicado.
- `/pwarps`: Muestra la lista de destinos comunitarios disponibles.

---

## 4. Integración Profunda con Cobblemon

Los comandos de Cobblemon fueron implementados directamente interactuando con la API interna de Cobblemon 1.8.0:

1. **Curación Progresiva (`/pokeheal` y PokéPad)**:
   - Administrado por `HealService`.
   - Cooldown dinámico:
     - Entrenador: 30 minutos (vía PokéPad base).
     - Élite: 15 minutos.
     - Campeón: 5 minutos.
     - Maestro / Leyenda: Ilimitado (5 segundos de anti-spam).
   - Cura salud, estados alterados y PP del equipo Pokémon completo de forma segura.
2. **PC Remoto Exclusivo (`/pc`)**:
   - Reservado únicamente para **Maestro** y **Leyenda**.
   - Abre la interfaz client-side oficial de cajas de Cobblemon enviando un paquete de red `OpenPCPacket`.
   - Elimina la necesidad de colocar bloques de PC físicos temporales o fantasmas, evitando desincronizaciones de inventario.
3. **Auditoría de IVs y EVs (`/ivs`, `/evs`)**:
   - `/ivs [slot 1-6]`: Muestra los 6 valores individuales (0-31 en PS, Ataque, Defensa, At. Especial, Def. Especial, Velocidad), calcula el porcentaje de perfección genética (ej. `88%`) y presenta una barra visual estética.
   - `/evs [slot 1-6]`: Detalla los puntos de esfuerzo acumulados por estadística (máximo 252 por stat) y la suma global sobre el tope de 510 puntos.
4. **Análisis de Ecosistema (`/checkspawns`)**:
   - Calcula las probabilidades de aparición de Pokémon salvajes según el bioma, la hora del día, el clima y la dimensión en la que se encuentra el jugador.

---

## 5. Mecánica de Protección de Experiencia (Rango Leyenda)

El rango **Leyenda** cuenta con la ventaja de no perder experiencia al morir. Para prevenir exploits y duplicación de experiencia en el servidor:

1. **Supresión de Orbes al Morir (`MixinLeyendaDeath`)**:
   - Se inyecta en `PlayerEntity.getXpToDrop()`.
   - Si el jugador posee rango `LEYENDA`, el método retorna inmediatamente `0`.
   - **Efecto crítico**: No cae ningún orbe de XP al suelo en el lugar de la muerte, impidiendo que otros jugadores (o el mismo jugador al regresar) recolecten experiencia duplicada.
2. **Restauración Atómica en Respawn**:
   - Se captura el evento del ciclo de vida de Fabric `ServerPlayerEvents.COPY_FROM`.
   - Al generarse la nueva entidad de jugador tras pulsar "Reaparecer", se transfieren de forma intacta:
     - `experienceLevel` (Nivel numérico de XP).
     - `experienceProgress` (Progreso de la barra).
     - `totalExperience` (Total acumulado).
   - Se despacha de inmediato el paquete de sincronización `ExperienceBarUpdateS2CPacket` al cliente.

---

## 6. Persistencia en Base de Datos MariaDB

Se creó la migración versionada `V042__homes_pwarps.sql`:

```sql
CREATE TABLE IF NOT EXISTS player_homes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    dimension VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    is_pwarp BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_player_home (uuid, name),
    INDEX idx_player_homes_uuid (uuid),
    INDEX idx_player_homes_pwarp (is_pwarp, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Características de Rendimiento:
- Operaciones asíncronas mediante `CompletableFuture` para no bloquear el hilo de ticks de Minecraft.
- Caché en memoria por jugador con invalidación reactiva tras inserciones o borrados.

---

## 7. Despliegue y Distribución

1. **Binario del Servidor**: Compilado con Gradle (`lunaeternal-0.1.0.jar`) y desplegado en el panel Pterodactyl.
2. **Manifiesto del Launcher**:
   - Publicado mediante `tools/publicar_jar_luna.py`.
   - Hash SHA-1 verificado: `f8cd8042a9e1002d6be749d202ac14c848dc2bc7`.
   - Puntero `latest.json` actualizado a `manifest-5190fc9a0d.json` en el repositorio `luna-eternal-pack`.
   - Cualquier jugador que inicie el juego desde el launcher oficial recibe la actualización automáticamente.
3. **Verificación RCON en Vivo**:
   - Servidor operativo en estado `running`.
   - Comandos `/tpa`, `/home`, `/pc`, `/pokeheal` validados y respondiendo en el log del servidor.
