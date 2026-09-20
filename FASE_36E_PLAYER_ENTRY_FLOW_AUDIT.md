# POKÉREPORT NETWORK — FASE 36E PLAYER ENTRY FLOW AUDIT

Fecha de ejecución: 2026-09-20  
Nodo: `node01.pokereport.online` (`15.235.16.131`)  
Resultado: **IMPLEMENTACIÓN SEGURA PARCIAL — NO CERTIFICADA**

No se declara `POKÉREPORT PLAYER ENTRY FLOW — CERTIFIED`. La ruta de entrada,
AFK, EasyAuth y efectos de Ciudadela quedaron corregidos y desplegados, pero no
se simularon pruebas con una cuenta real y la topología offline actual no puede
demostrar criptográficamente que una conexión pertenece al dueño de una cuenta
premium. Tampoco existe una prueba server-side infalsificable de todos los mods
puramente client-only. Los bloqueos y la corrección mínima están documentados.

## 1. Backups previos

Todos se ejecutaron antes de modificar producción.

| Capa | Resultado | Evidencia |
|---|---|---|
| MariaDB | PASS, código 0 | `s11945_luna_2026-09-20_034223.sql.gz`, SHA-256 `f9ac5cb51946abcc81023841afb3af4ffc97af75931c40f140be05173123ac92` |
| Files + mundo + mods + config + EasyAuth SQLite | PASS, código 0 | `cobblemon_2026-09-20_034235.tar.zst`, SHA-256 `79f553baa044c2757a4f7aafb49e640bef9ef9d947178d1cb50c0b4888059b0c` |
| EasyAuth SQLite dentro del backup | PASS | `PRAGMA integrity_check = ok` |
| Offsite Google Drive | PASS, código 0 | `manifest-20260920T034345Z.txt`; contiene los dos hashes anteriores |
| Rollback puntual | PASS | `/root/pokereport-phase36e/pre/` contiene JAR y tres configs anteriores con SHA-256 |

La copia de Files activa `save-all flush`, desactiva autosave durante el
snapshot y restaura `save-on` mediante `trap`, incluso ante error.

## 2. Topología real

```text
Internet
  └─ 15.235.16.131:25565
       └─ Velocity (online-mode=false, modern forwarding)
            └─ Cobblemon Main 172.18.0.1:25567
                 ├─ EasyAuth 3.4.4
                 ├─ FabricProxy-Lite
                 ├─ LunaEternal / LunaNeon handshake
                 ├─ lunaeternal:lobby       ← Lobby oficial
                 └─ dimensiones de gameplay

Paper Lobby 172.18.0.1:25566
  └─ ONLINE para construcción/pruebas; vacío, sin plugins y sin auth;
     NO forma parte de la ruta oficial de login
```

La separación esperada “Paper Lobby → Cobblemon Main” no existe funcionalmente.
El Paper Lobby no tiene plugins, auth ni un mecanismo seguro de transferencia.
Encenderlo y ponerlo primero en `try` habría enviado usuarios a un backend sin
EasyAuth. La solución mínima y segura es mantener como Lobby oficial la dimensión
`lunaeternal:lobby` dentro de Main. Por eso `try=["cobblemon"]` es correcto para
esta arquitectura final, aunque el nombre histórico pueda inducir a error.

### Estado de procesos

| Componente | Estado final | Exposición |
|---|---|---|
| Velocity | ONLINE | público `25565` |
| Cobblemon Main | ONLINE | solo bridge `172.18.0.1:25567` |
| Paper Lobby | ONLINE | solo bridge `172.18.0.1:25566`; construcción/pruebas |
| MariaDB | ACTIVE | escucha `3306`; no fue cambiado en esta fase |
| Wings | ACTIVE | interno |
| Docker | ACTIVE | interno |
| Tebex | CONNECTED | log: `Connected to PokeReport - Minecraft (Offline/Geyser) server` |
| Staging | no encontrado/uso | no se creó |

Prueba externa desde la estación de administración:

| Puerto | Resultado |
|---|---|
| `25565` Velocity | OPEN |
| `25566` Paper Lobby | CLOSED |
| `25567` Cobblemon Main | CLOSED |

## 3. Dimensiones reales

Cada una respondió a una ejecución no mutante de `time query daytime` después
del despliegue.

| Nombre | Tipo | Registrada/cargable | Entrada esperada |
|---|---|---:|---|
| `minecraft:overworld` | Hogar | PASS | viaje desde gameplay |
| `minecraft:the_nether` | vanilla | PASS | mecánica vanilla/controlada |
| `minecraft:the_end` | vanilla | PASS | mecánica vanilla/controlada |
| `lunaeternal:lobby` | Lobby oficial | PASS | toda conexión y todo AFK |
| `lunaeternal:ciudadela` | hub gameplay | PASS | guardián del Lobby |
| `lunaeternal:salvaje` | salvaje 1 | PASS | sistema Explorar |
| `lunaeternal:salvaje2` | salvaje 2 | PASS | rotación |
| `lunaeternal:salvaje3` | salvaje 3 | PASS | rotación |
| `lunaeternal:salvaje4` | salvaje 4 | PASS | rotación |
| `lunaeternal:salvaje5` | salvaje 5 | PASS | rotación |
| `lunaeternal:salvaje6` | salvaje 6 | PASS | rotación |
| `lunaeternal:gimnasios` | arenas | PASS | sistema Gimnasios |
| `lunaeternal:torre` | Torre | PASS | sistema Torre |
| `lumymon:nightmare` | mod | PASS | mecánica del mod |
| `lumymon:origin` | mod | PASS | mecánica del mod |
| `cobblemonraiddens:raid_dimension` | mod | PASS | Raid Dens |

Hogar no es una dimensión adicional: es `minecraft:overworld`. No se inventó
ningún servidor o mundo nuevo.

## 4. Arquitectura anterior y bugs

Antes del cambio, `player_puerta` era a la vez historial y autorización. Una
fila persistente hacía que el jugador antiguo evitara el Lobby al reconectar.
También existían excepciones AFK para creativo, espectador y combate, y el AFK
no revocaba el cruce de la sesión. Los efectos de Ciudadela solo se aplicaban a
OP nivel 2 y eran Speed II, Night Vision I y Jump Boost I, con icono visible.

Bugs corregidos:

1. Veteranos, staff y OP podían conservar el bypass histórico del Lobby.
2. Un reconnect rápido podía conservar la ruta anterior.
3. AFK en combate/evento/creativo/espectador quedaba sin política global.
4. AFK llegaba al Lobby con el permiso anterior aún válido.
5. El primer saludo del cliente podía ser descartado por EasyAuth antes del login.
6. Los efectos de Ciudadela tenían niveles, audiencia y visibilidad incorrectos.
7. La limpieza de efectos no identificaba la firma completa de ownership.
8. Los logs decían “jugadores nuevos” aunque la política final es universal.

## 5. Flujo final implementado

```text
CONNECT por Velocity
  → Cobblemon Main
  → PuertaService crea autorización de sesión = false
  → traslado diferido a lunaeternal:lobby
  → EasyAuth exige /login o /register
  → LunaEternal reintenta el saludo cada 5 s, solo hasta recibirlo
  → guardián Lugia (clic derecho o izquierdo)
  → EasyAuth permite la interacción únicamente si ya autenticó
  → protocolo del cliente PokeReport validado
  → autorización de sesión = true
  → lunaeternal:ciudadela en (4.27, 70, 0.36)
```

La tabla `player_puerta` se conserva como historial y para comandos existentes,
pero ya no autoriza una conexión nueva. No se borró Pokémon, inventario,
economía, rango, playerdata, home, claim ni posición histórica.

`puedeIrA` falla cerrado: mientras la autorización sea `null` o `false`, el
único destino permitido es Lobby. Si Ciudadela no está disponible, la sesión se
revoca y el jugador permanece seguro en Lobby.

### Reconnect

Toda conexión nueva crea un estado de sesión falso, independientemente de:

- UUID existente;
- rango, OP o permisos;
- dimensión/posición al desconectar;
- tiempo desde la desconexión;
- fila histórica en `player_puerta`.

La posición antigua puede seguir existiendo en playerdata, pero no se usa como
destino final de entrada. El destino posterior al clic es Ciudadela.

### Guardián

La entidad real existe. Se cargó temporalmente el chunk `[2,2]` del Lobby y
`data get entity @e[tag=luna_puerta,limit=1] UUID` devolvió una entidad Lugia.
Después se retiró el forceload y `forceload query` confirmó que no quedó ningún
chunk forzado. El antirrebote de clic es de 2 s y la escritura DB usa
`INSERT ... ON DUPLICATE KEY`, por lo que el doble clic es idempotente.

## 6. EasyAuth personalizado

Configuración final:

| Ajuste | Antes | Ahora |
|---|---:|---:|
| `session-timeout` | 900 s | `-1` — contraseña en cada conexión |
| idioma | `en_us` | `es_es` |
| mensajes | genéricos | prefijo y textos PokéReport para login/registro/error/éxito |
| contraseña mínima | 4 | 8 caracteres |
| contraseña máxima | ilimitada | 64 caracteres |
| usuario case-insensitive | true | false |
| log de registro/login | false | true, sin contraseñas |
| packet pre-auth permitido | ninguno | solo `lunaeternal:saludo` |
| interacción con entidades pre-auth | false | false (sin cambio) |
| movimiento/inventario/chat pre-auth | false | false (sin cambio) |
| `skip-all-auth-checks` | false | false |

El packet permitido solo comunica el entero de protocolo de LunaEternal; no
contiene contraseña, token ni secreto. Las acciones de gameplay continúan
bloqueadas por EasyAuth.

### Seguridad premium — BLOCKER

No se instaló FastLogin ni otro plugin al azar. EasyAuth 3.4.4 ejecuta la
verificación Mojang de `prevent-offline-players-with-online-usernames` dentro de
la rama `server.usesAuthentication()`. Main está en offline mode detrás de un
Velocity también configurado con `online-mode=false`; por tanto esas banderas
no protegen esta topología. Se dejaron en `false` para no presentar seguridad
inexistente.

La contraseña EasyAuth sigue protegiendo la identidad de todas las cuentas,
incluidas las premium, pero no prueba propiedad Mojang.

**Causa:** el proxy acepta identidades offline y no entrega una prueba Mojang
confiable al backend. Un mod solo en Main no puede reconstruir esa prueba.

**Corrección mínima segura, con decisión de producto previa:**

- premium-only: `online-mode=true` en Velocity, forwarding moderno y migración
  controlada de UUID/datos; expulsaría a usuarios no premium;
- red mixta: desplegar en staging una solución híbrida compatible y mantenida
  en el proxy, con enlace explícito de cuenta y migración/rollback de UUID. La
  ruta EasyAuth/Fabric actual no tiene un puente premium verificado.

Cambiar esa política sin decidir si se admiten usuarios offline podría separar
inventarios, Pokémon, claims y economía por UUID; se detuvo correctamente.

## 7. Client integrity

Mecanismos reales encontrados:

1. Manifiesto vivo inmutable y verificado por huella SHA-1 del puntero:
   `manifest-3592757b8f.json`.
2. Pack `0.2.0`, Minecraft `1.21.1`, Fabric Loader `0.19.5`, 158 ficheros.
3. El launcher compara hashes y elimina cualquier JAR inesperado de `mods/`
   antes de lanzar el juego.
4. Fabric rechaza incompatibilidades de registros/dependencias requeridas.
5. LunaEternal exige su canal cliente y protocolo `1` antes del guardián.
6. Ausencia o protocolo incorrecto muestra un mensaje legible y no un stacktrace.

El protocolo de red no cambió. Sí se republicó el pack de forma dirigida para:

- apuntar el launcher y `servers.dat` a `play.pokereport.online:25565`;
- migrar en el cliente la entrada histórica sin borrar otros servidores;
- entregar Raid Dens con `enable_spawning=false`;
- entregar RCT con aparición natural de entrenadores en `0`;
- actualizar LunaEternal con la política Gen 1–2 y fauna Hogar/Salvaje.

El manifiesto conserva 158 entradas. Frente al anterior solo cambiaron
`mods/lunaeternal-0.1.0.jar`, `config`, `servers.dat` y los metadatos del
servidor; no se aceptó la regeneración que pretendía cambiar Raid Dens y
TMCraft de versión.

### Límite honesto

El servidor no puede enumerar de forma confiable todos los mods puramente
client-only de un cliente hostil. El launcher oficial sí elimina extras en el
flujo normal, pero un launcher alterado podría mentir o inyectarlos después. No
se declara “extras imposibles” fuera del threat model del launcher oficial.

Para una política más fuerte se necesita un attestation firmado por un
componente bajo control del launcher; aun así, software ejecutado en una
máquina hostil no ofrece una prueba absoluta sin una raíz de confianza externa.

## 8. AFK global

- Límite: 10 minutos sin desplazamiento real.
- Aviso: 1 minuto antes.
- Alcance: jugador, admin, OP, creativo, espectador, combate y eventos.
- Única excepción: quien ya está en Lobby, para evitar bucle.
- Acción: revoca la autorización de sesión y mueve al Lobby.
- Regreso: nuevo clic al guardián y nueva validación cliente.
- No borra datos ni desconecta al jugador.

## 9. Spawn y efectos de Ciudadela

Spawns reales existentes:

| Lugar | Dimensión | X | Y | Z | yaw | pitch |
|---|---|---:|---:|---:|---:|---:|
| Lobby | `lunaeternal:lobby` | 43.998 | 72 | 47.97 | 90 | 0 |
| Ciudadela | `lunaeternal:ciudadela` | 4.27 | 70 | 0.36 | se conserva | se conserva |

No se inventó un yaw/pitch de Ciudadela: la implementación oficial previa
conserva la orientación del jugador. Las coordenadas están centralizadas en
`TravelService`.

Mientras cualquier jugador esté en Ciudadela, cada 5 s se valida:

| Efecto | Nivel visual | Amplifier API | Partículas | Icono HUD |
|---|---:|---:|---:|---:|
| Speed | IV | 3 | false | false |
| Night Vision | I | 0 | false | false |
| Jump Boost | III | 2 | false | false |

No existe bypass de staff. Al salir solo se retira un efecto que coincida con
la firma completa gestionada: infinito, amplifier exacto, ambiental, sin
partículas y sin icono. Una poción o habilidad normal no se elimina.

## 10. Observabilidad

Se añadieron eventos sin secretos y sin spam por tick:

- `PLAYER_CONNECT`
- `AUTH_SUCCESS` (evidencia: EasyAuth permitió interacción con entidad)
- `CLIENT_VALIDATED`
- `CLIENT_REJECTED`
- `LOBBY_READY`
- `CITY_ENTRY_REQUEST`
- `TRANSFER_STARTED`
- `TRANSFER_FAILED`
- `CITY_SPAWN_APPLIED`
- `AFK` / `AFK_TRANSFER_FAILED`

El saludo se reintenta como máximo cada 5 s únicamente mientras falte; después
no genera tráfico periódico.

## 11. QA matrix

`PASS-LIVE` significa observado en producción. `PASS-CODE` significa compilado,
testeado y demostrado por la ruta server-side, pero sin una cuenta Minecraft
QA real. No se tomaron cuentas existentes ni contraseñas de usuarios.

| # | Caso | Resultado | Evidencia / límite |
|---:|---|---|---|
| 1 | New player + cliente correcto | PASS-CODE | sesión nueva=false; auth y guardián obligatorios; falta cuenta QA real |
| 2 | Returning player | PASS-CODE | historial separado de autorización de sesión |
| 3 | Admin/OP | PASS-CODE | no hay chequeo de permisos/rango |
| 4 | Falta mod requerido | PASS-CODE | Fabric registry/dependency + ausencia de canal Luna |
| 5 | Versión requerida incorrecta | PASS-CODE | Fabric/protocolo; no se probó cliente deliberadamente roto |
| 6 | Cliente PokeReport desactualizado | PASS-CODE | protocolo fail-closed y mensaje al usuario |
| 7 | Mod extra no autorizado | PARCIAL | launcher elimina JARs extras; no hay attestation server-side infalsificable |
| 8 | Disconnect Ciudadela | PASS-CODE | nueva conexión restablece sesión=false |
| 9 | Disconnect Salvaje | PASS-CODE | igual; última dimensión no autoriza |
| 10 | Disconnect Hogar | PASS-CODE | igual |
| 11 | Disconnect Torre | PASS-CODE | igual |
| 12 | Disconnect Gimnasio | PASS-CODE | igual |
| 13 | Reconnect 5 s | PASS-CODE | estado se elimina al disconnect y `session-timeout=-1` |
| 14 | Reconnect 10 min | PASS-CODE | sin sesión EasyAuth persistente |
| 15 | Reconnect 15 min | PASS-CODE | sin sesión EasyAuth persistente |
| 16 | Death en Ciudadela | PASS-CODE | repaso server-side cada 5 s |
| 17 | Respawn Ciudadela | PASS-CODE | repaso server-side cada 5 s |
| 18 | Salir de Ciudadela | PASS-CODE | ownership exacto al retirar |
| 19 | Volver a Ciudadela | PASS-CODE | reaplicación automática |
| 20 | Doble clic rápido | PASS-CODE | debounce 2 s + UPSERT idempotente |
| 21 | Reinicio backend | PASS-LIVE | reinicio Wings, `Done (4.367s)`, puerta activa |
| 22 | Reinicio Lobby | N/A | Lobby oficial es dimensión de Main, probado con #21 |
| 23 | Reinicio Velocity | NO EJECUTADO | proxy estable; no era necesario para el cambio interno |
| 24 | Direct connect backend | PASS-LIVE | `25567` cerrado externamente y ligado a `172.18.0.1` |

Build local: `gradlew clean test build` → **BUILD SUCCESSFUL**.  
JAR desplegado: SHA-256
`96bcda650c520b1b80927f4327822036b7bb353e07f1616e6d8db576ae761bb7`.

## 12. Salud final

| Métrica | Resultado |
|---|---|
| Jugadores | `0/10` durante despliegues |
| TPS | `20.0 / 20.0 / 20.0 / 20.0 / 20.0` |
| Tick 10 s min/med/p95/max | `0.2 / 0.4 / 0.8 / 9.0 ms` |
| Main cgroup | ~5.17 GiB / 14.7 GiB tras el reinicio final |
| Velocity | ~289 MiB / 1.15 GiB |
| Swap host | 3 MiB / 4 GiB |
| Filesystem | 18% usado |
| EasyAuth DB | `integrity_check = ok` |
| Guardian | Lugia encontrado por tag `luna_puerta` |
| Forceload residual | ninguno |
| Tebex | conectado |
| OOM/watchdog/corrupción | no encontrados |

Persisten mensajes preexistentes de terceros durante el arranque: registros sin
data fixer, un advancement inválido de Tom's Storage y `No key id in MapLike`.
Ya aparecían fuera de esta lógica y el servidor completa `Done`; no se ocultaron
ni se atribuyeron falsamente a Fase 36E.

## 13. Archivos modificados

Código:

- `mod/src/main/java/net/pokereport/luna/puerta/Puerta.java`
- `mod/src/main/java/net/pokereport/luna/puerta/PuertaService.java`
- `mod/src/main/java/net/pokereport/luna/world/Afk.java`
- `mod/src/main/java/net/pokereport/luna/world/ConstructorBuffs.java`
- `mod/src/main/java/net/pokereport/luna/world/FaunaControl.java`
- `mod/src/main/java/net/pokereport/luna/world/PoliticaFauna.java`
- `mod/src/client/java/net/pokereport/luna/client/LunaCliente.java`
- `mod/src/client/java/net/pokereport/luna/client/mixin/MainMenuMixin.java`
- `tools/gen_modpack.py`

Producción:

- `mods/lunaeternal-0.1.0.jar`
- `config/EasyAuth/main.conf`
- `config/EasyAuth/extended.conf`
- `config/EasyAuth/translation.conf`
- launcher `manifest-3592757b8f.json`, `servers.dat` y ZIP `config`

No se cambió `velocity.toml`, Paper, MariaDB, DNS, Xmx, flags JVM, inventarios,
claims ni infraestructura de backups. Paper Lobby solo se encendió para los
constructores; no se convirtió en la ruta oficial de autenticación.

## 14. Rollback

Rollback puntual preservado en `/root/pokereport-phase36e/pre/`:

1. confirmar cero jugadores;
2. restaurar el JAR y los tres `.conf` desde `pre/`;
3. verificar sus SHA-256 contra los registrados allí;
4. reiniciar Main mediante Wings, no con `docker start` directo;
5. esperar `Done`, comprobar `/list`, Tebex, DB y acceso por Velocity.

No requiere tocar DB de jugadores ni eliminar datos.

## 15. Bloqueos para certificación

### BLOCKER A — premium ownership

**Cause:** Velocity y backend aceptan identidad offline; EasyAuth 3.4.4 no puede
probar Mojang en esta topología.  
**Minimum safe fix:** decidir premium-only vs red mixta, implementar el control
en el proxy/staging y diseñar migración de UUID con rollback.

### BLOCKER B — QA real y extras client-only

**Cause:** no existe una cuenta QA autorizada con la que ejecutar los 24 flujos,
y el servidor no puede observar de forma confiable todo mod client-only.  
**Minimum safe fix:** proporcionar una cuenta QA controlada (no una cuenta de
usuario real) y, si se exige una política adversarial de extras, diseñar
attestation del launcher con amenaza y límites explícitos.

Hasta cerrar ambos bloqueos, el estado correcto es:

**POKÉREPORT PLAYER ENTRY FLOW — IMPLEMENTED, NOT CERTIFIED**
