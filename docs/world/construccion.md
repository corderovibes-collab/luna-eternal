# Cómo se construye la ciudadela

## Purpose

Responder a la pregunta práctica: **¿se construye a mano dentro del servidor, o
se puede hacer fuera y traerlo?** Y con qué herramientas.

## Dependencies

- [`worlds.md`](worlds.md) · [`world-structure.md`](world-structure.md)

## Current Status

Servidor de desarrollo listo para construir (2026-08-11):

| | |
|---|---|
| **WorldEdit 7.3.8** | instalado y cargado |
| **Axiom 5.4.2** | instalado y cargado — `Initializing Axiom/5.4.2` |
| Ciudadela | plataforma de **80×80** en el origen, y=63 |

**Axiom edita en vivo sin ningún trámite.** Verificado contra su propia API de
autorización, no supuesto — ver §3-bis. El cliente está montado en la instancia
`PokeReport-LunaEternal-0.1.0` de PrismLauncher.

---

## 1. La respuesta corta

**No hay que construir a mano dentro del servidor.** Se construye donde sea
—en un mundo local, en creativo, con las herramientas cómodas— y se **pega** en
el servidor con un solo comando.

El flujo estándar de cualquier servidor grande:

```
construir en local  →  exportar .schem  →  subir al servidor  →  //paste
```

---

## 2. Las tres vías, de menos a más esfuerzo

### 2.1 · Descargar algo ya construido *(horas, no meses)*

Existen ciudades enteras hechas, gratuitas y de pago, en formato `.schem`. Es
lo que hace la mayoría de servidores: se descarga una base y **se personaliza**,
en vez de empezar por un solar vacío.

| Dónde | Qué hay |
|---|---|
| Planet Minecraft | Miles de builds gratuitos, calidad muy variable |
| MC-Market · BuiltByBit | Hubs y ciudades profesionales, de pago |
| Schematic packs de Patreon | Suelen venir por temas (medieval, moderno) |

> ⚠️ **La licencia también aplica aquí** (D-008). Un build gratuito puede
> prohibir uso comercial, y nuestro servidor va a tener tienda. Comprobar la
> licencia **antes** de pegarlo, no después.

### 2.2 · Construir en local y traerlo *(lo que preguntabas)*

Es el flujo natural: se construye con calma en un mundo propio, sin lag, sin
nadie mirando, y cuando está listo se trae.

```
1. Mundo local en creativo, plano y vacío
2. Construir
3. Seleccionar la zona y exportarla a .schem
4. Subir el .schem al servidor
5. //schem load ciudadela  +  //paste
```

**Ventaja real:** se puede rehacer diez veces sin que nadie lo vea, y el
`.schem` queda como copia de seguridad de la construcción, independiente del
mundo.

### 2.3 · Construir directamente en la ciudadela

Se puede, pero solo tiene sentido para retoques. Para volumen, las otras dos
son mucho mejores.

---

## 3. Las herramientas, y cuál usar

| Herramienta | Dónde corre | Para qué | Veredicto |
|---|---|---|---|
| **WorldEdit** | Servidor | Copiar, pegar, rellenar, rotar, cargar `.schem` | 🟢 **Imprescindible.** Es el que pega |
| **Axiom** | Cliente + servidor | Editor de construcción moderno, con interfaz de verdad | 🟢 **La mejor opción para construir** |
| **Litematica** | **Solo cliente** | Ver un plano superpuesto y colocarlo bloque a bloque | 🟡 Útil para copiar a mano |
| **WorldPainter** | Fuera del juego | Generar terreno y relieve | 🟡 Para el mundo, no para la ciudad |
| **Amulet** | Fuera del juego | Editar un mundo guardado sin abrir Minecraft | 🟡 Para operaciones grandes |

> **Lección ya pagada en el proyecto anterior:** *"Litematica es un mod de
> cliente. Instalarlo en el servidor no hace nada"*. Sirve para **guiar** una
> construcción manual, no para pegarla.

### Recomendación

**Axiom en local para construir, WorldEdit en el servidor para pegar.**

---

## 3-bis. Axiom: instalado y funcionando, sin trámites

Sus términos, citados literalmente de su página:

> *"If you are using Axiom **non-commercially**, you can use it in singleplayer,
> localhost servers, or plot-based servers... If you wish to use it on your own
> **private multiplayer server**, you can request a multiplayer whitelist
> through the Axiom discord"*

> *"We ask that **'Professional Builders'** (i.e. people who make money through
> building in Minecraft) purchase a **Commercial License**"*

La licencia comercial apunta a **quien cobra por construir** — estudios que
venden builds por encargo. No es nuestro caso: construimos nuestro propio
servidor. La vía que nos corresponde es la primera, la no comercial.

### ⚠️ CORRECCIÓN (2026-08-12): la whitelist SÍ hace falta, y hay cuenta atrás

Aquí decía *"la whitelist NO hace falta, verificado"*. **La observación era
correcta y la conclusión estaba mal.** Axiom sí autoriza — pero por un motivo
que caduca.

De su documentación oficial, literal:

> *"An **automatic 30d multiplayer whitelist** is given the first time using
> Axiom on a server."*
> *"For non-commercial purposes, a whitelist can be requested to gain
> multiplayer access for **90 days**, or **180 days** for Patrons or Discord
> Server Boosters."*
> *"Some servers, like Builder's Refuge or The Bakery, have a **server
> whitelist**, making it possible for everyone to use Axiom without needing a
> whitelist."*

Es decir: lo que funciona hoy es **el periodo de cortesía de 30 días**, que
empieza la primera vez que cada persona usa Axiom en este servidor.

| | |
|---|---|
| Primer uso del propietario | 2026-08-11 |
| **Deja de funcionar** | **~2026-09-10**, salvo que se pida la whitelist |
| Cada constructor nuevo | tiene **sus propios** 30 días desde su primer uso |

**Verificado hoy con dos UUID inventados**, no con el nuestro: los dos reciben
`HTTP 200` y `commercial: false` contra nuestra dirección. Eso confirma las dos
cosas a la vez — que **cualquier constructor entrará sin trámite** (por eso
"funciona y es raro") y que **la concesión es automática**, o sea, la de 30
días.

```
GET …/connect?uuid=11111111-2222-…&server=s12.mia.us.tarohosting.lat:33043
HTTP 200  JWT → {"commercial": false, "exp": +24 h, "sub": "<uuid>/<servidor>"}
```

> **Qué hay que hacer, y es del usuario:** pedir la **whitelist de servidor**
> en `#whitelist-request` del Discord de Axiom (`https://discord.gg/axiomtool`).
> La de servidor, no la personal: cubre a **todo el equipo de construcción** de
> golpe y no hay que repetir el trámite por cada persona nueva.
>
> Si se deja pasar, el síntoma llegará un mes después de empezar a construir y
> será *"This server has Axiom, but your client doesn't support multiplayer"*.
> Diagnóstico: `/whynoaxiom`. El flujo `.schem` → `//paste` del §4 seguiría
> funcionando igual, así que **no se pierde nada de lo construido**.

### Dónde NO está el candado

El servidor no comprueba nada de licencias: `AxiomServer` solo mira permisos.
Quien decide es **el cliente**, con este método:

```
Authorization.checkServer(server, host, uuid)
  → GET https://axiom.moulberry.com/api/mcauth/connect?uuid=…&server=…&host=…
  → HTTP != 200                      → NO          bloqueado
  → JWT con claim commercial = true  → COMMERCIAL  exige licencia de pago
  → JWT con claim commercial = false → YES         permitido
```

Y en `ClientEvents`, `YES` hace `allowedOnServer = true`. Sin más condiciones.

Consultado con nuestros datos reales, el 2026-08-11:

```
GET …/connect?uuid=432ef323-…&server=s12.mia.us.tarohosting.lat:33043
HTTP 200   {"commercial": false, "sub": "432ef323-…/s12.mia.us…:33043"}
        → YES → allowedOnServer = true
```

**Responde autorizado con el UUID offline**, así que `online-mode=false`
tampoco lo impide — el riesgo que había anotado aquí no existe.

> **Lo que sigue siendo cierto:** que no haya candado técnico no cambia sus
> términos. La licencia comercial la piden a *"people who make money **through
> building**"* — estudios que cobran por construir. No es nuestro caso (D-022).
> Si algún día lo fuera, se compra.

| Paso | Estado |
|---|---|
| Axiom en el servidor | ✅ hecho |
| Axiom en el cliente | ✅ va en el pack de constructor |
| **Pedir la whitelist de SERVIDOR en su Discord** | ⬜ **pendiente, con fecha límite ~2026-09-10** |

---

## 3-ter. Construir entre varios a la vez

Sí se puede, y no hace falta instalar nada más en el servidor. Lo que hace
falta es dar de alta a cada persona en **dos** sitios.

### 1 · Darlos de alta — un solo comando

```
python tools/constructor.py --anadir Pepe Ana Luis
python tools/constructor.py --listar
python tools/constructor.py --quitar Pepe
```

Hace las dos cosas que hacen falta —whitelist **y** operador de nivel 2— y
calcula el UUID offline correcto. **No lo hagas a mano**: el riesgo está en el
§2 de aquí abajo.

> ⚠️ Con `online-mode=false`, **el nombre ES la identidad**. Quien se lo cambie
> aparece como otra persona: pierde sus permisos, su inventario y su progreso.
> Que cada uno elija el suyo a la primera y no lo toque.

> `ops.json` **se lee al arrancar**, así que quien acabe de ser dado de alta no
> tendrá permisos hasta el siguiente reinicio. La whitelist sí se recarga en
> caliente, y el script lo hace solo.

### 2 · Por qué nivel 2 y no nivel 4

Esto es lo importante, y está **leído de los dos jars**, no supuesto:

| Mod | Qué comprueba | Leído en |
|---|---|---|
| **Axiom 5.4.2** | `isOp(jugador)` → `hasPermissionLevel(2)`. Si es OP, concede **todos** los permisos `axiom.*` de golpe | `AxiomServer.isOp` |
| **WorldEdit 7.3.8** | `cheatMode \|\| playerManager.isOperator(perfil) \|\| (creativeEnable && modo == CREATIVO)` | `FabricPermissionsProvider$VanillaPermissionsProvider` |

Conclusión: **con ser operador de nivel 2 basta para los dos.** No hace falta
LuckPerms ni ningún mod de permisos (P5).

Y el nivel importa mucho:

| Nivel | Qué da | Para quién |
|---|---|---|
| 2 | creativo, `/tp`, `//paste`, Axiom completo | ✅ **constructores** |
| 3 | además `/ban`, `/kick`, `/op`, `/whitelist` | moderación |
| 4 | además `/stop`, `/save-all` | solo el propietario |

**`/op <nombre>` da nivel 4 directamente**, que es demasiado: un constructor con
nivel 4 puede apagar el servidor o quitarte a ti el OP, por accidente. Por eso
existe el script del §1, que escribe la entrada con el nivel correcto:

```json
{ "uuid": "…", "name": "Pepe", "level": 2, "bypassesPlayerLimit": false }
```

> El UUID offline se calcula del nombre —UUID v3 sobre
> `"OfflinePlayer:<nombre>"`— y **la fórmula está verificada** contra el UUID
> real de `TheJuanCE` que ya estaba en `ops.json`.

### 3 · Ajustes del servidor que hicieron falta

Aplicados el 2026-08-12 y **verificados leyendo `server.properties` después**:

| Ajuste | Antes | Ahora | Por qué |
|---|---|---|---|
| `allow-flight` | `false` | **`true`** | Axiom mueve la cámara en vuelo libre y con no-clip. Sin esto, el servidor expulsa con *"Flying is not enabled on this server"* en mitad de una construcción |
| `enforce-secure-profile` | `true` | **`false`** | Con `online-mode=false` los perfiles no van firmados: se estaba exigiendo una firma que nadie puede dar |
| `require-resource-pack` | `true` | **`false`** | El pack solo maquillaba los menús de cofre, que ya no existen (D-026). Con `true`, quien lo rechazara era expulsado por un pack inútil |
| `resource-pack*` | URL + SHA1 | vacío | Idem |

> ⚠️ **`allow-flight=true` hay que revertirlo antes de abrir a jugadores.** Es
> el hueco por el que entra un hack de vuelo en supervivencia. Mientras el
> servidor sea whitelist de constructores, el riesgo es cero.

### 4 · El solar (2026-08-12)

**Ahora mismo hay una sola isla: la plaza, 56×56, flotando en el vacío.** Todo
lo demás está limpio, a propósito — se empieza por una zona y punto.

| | |
|---|---|
| Plaza | `-28..27` en los dos ejes, centrada en el origen |
| Suelo | y=**63**, cuarzo liso. Se camina en 64 |
| Arriba / abajo | hasta y=319 y hasta y=-64. **Se puede construir hacia abajo** |
| Borde del mundo | 208, con aviso a 4 |

> El vacío alrededor no es un descampado a medias: es una decisión. Una isla
> flotante deja ver **dónde acaba** lo que estás construyendo, y su canto se ve
> desde abajo — es silueta, no un corte de un bloque.

El plan completo de la ciudad sigue existiendo y se puede volver a dibujar
cuando toque. Son nueve parcelas de 56×56 con avenidas de 8:

```
                       NORTE  (-Z)
        ┌────────────┬────────────┬────────────┐
        │  Salón de  │Laboratorio │   Gremio   │
        │  Medallas  │            │            │
        ├────────────┼────────────┼────────────┤
 OESTE  │   Centro   │   PLAZA    │  Mercado   │  ESTE
 (-X)   │   Pokémon  │  CENTRAL   │            │  (+X)
        ├────────────┼────────────┼────────────┤
        │ Sastrería  │  Puerta    │ Reservado  │
        │            │  al Mundo  │            │
        └────────────┴────────────┴────────────┘
                        SUR  (+Z)
```

**La plaza deja sitio a eso:** cuatro accesos, uno por lado, centrados y de al
menos 8 de ancho. Es lo único que hay que respetar mientras se construye sola.

### Las órdenes

```bash
python tools/ciudadela.py --solo-centro          # una isla de 56x56, nada más
python tools/ciudadela.py --solo-centro --tam 80 # ...más grande
python tools/ciudadela.py --plano                # el replanteo de las 9 parcelas
python tools/ciudadela.py --limpiar              # solar liso de 192x192
```

### Cómo se llega

```
/luna ir ciudadela
```

Hace falta ser operador de **nivel 2**, o sea: los constructores lo tienen.
También `lobby`, `hogar` y `salvaje`. Sustituye a la Puerta del Mundo, que se
fue con los menús (D-026).

### La ciudadela es de NOCHE, siempre

`fixed_time: 18000` en su `dimension_type`. El servidor se llama *Luna Eternal*
y tener su plaza principal a pleno sol era una oportunidad tirada.

**No cuesta nada:** `monster_spawn_light_level: 0` ya impedía que apareciera un
solo monstruo, así que la noche aquí es puro ambiente sin ningún riesgo. Se
subió `ambient_light` de `0.3` a `0.45` para que se siga viendo sin depender de
que nadie tenga shaders.

> El `dimension_type` **solo se relee al arrancar**. Cambiarlo obliga a
> redesplegar el mod y reiniciar; no basta con recargar datapacks.

### La luna gigante: hay que construirla

**El tamaño de la luna de Minecraft no se puede cambiar desde el servidor.** Un
resource pack solo sustituye su textura (`moon_phases.png`), no su tamaño: el
cuadrado sobre el que se dibuja es fijo. Y el shader que instalamos
—Complementary Reimagined— **tampoco tiene ese ajuste**: comprobado grepeando
sus `.glsl`, no existe ninguna opción de tamaño de sol o luna.

Así que la luna gigante **se construye**, que además es lo único que ve todo el
mundo tenga o no shaders. Con WorldEdit es un comando: vuela a donde la
quieras y

```
//hsphere white_concrete 36
```

`hsphere` es hueca — una esfera maciza de radio 36 son 195 000 bloques y no
aporta nada, porque solo se ve la cáscara.

| | |
|---|---|
| Radio recomendado | 30-40 |
| Altura | y ≈ 180-220 |
| Distancia | dentro de ~100 bloques del centro, o queda fuera de los chunks cargados |
| Material | `white_concrete` para la cara, y por dentro algo que emita luz |

> Para que **brille de verdad**, la cáscara exterior de bloque opaco y una capa
> de `sea_lantern` o `glowstone` justo debajo, o `light` con `//replace`. Una
> esfera de piedra blanca de noche es una mancha gris.

### Ajustes de obra ya aplicados

`doWeatherCycle`, `doFireTick`, `mobGriefing` y `doMobSpawning` en `false`,
`keepInventory` en `true`, y sobre todo **`randomTickSpeed 0`** — el que evita
que la hierba crezca y el hielo se derrita encima de lo que estás construyendo.
El mediodía fijo y la ausencia de monstruos vienen del `dimension_type`.

> ⚠️ Los gamerules de Minecraft son **globales**, no por dimensión: también
> afectan al Hogar y al Salvaje. En desarrollo da igual; antes de abrir a
> jugadores hay que revisarlo.

---

### 5 · Reglas de convivencia mientras se construye

Axiom **no bloquea la zona en la que estás**: el último que escribe, gana. No
es un problema si se reparte el terreno.

| Regla | Por qué |
|---|---|
| **Una zona por persona**, acordada antes de empezar | Es lo único que evita pisarse. Axiom no avisa |
| `//pos1` `//pos2` `//copy` `//schem save <zona>-<fecha>` al terminar cada sesión | Un `.schem` pesa poco y es mejor backup que el del mundo (§4) |
| Nadie construye en el **Mundo Hogar** ni en el **Salvaje** | El Salvaje se reinicia por temporada (D-016): lo que se construya ahí se pierde |
| Operaciones de más de ~200×200 bloques, de una en una | 4 GB de RAM. Dos personas rellenando a la vez es lo que tira el servidor |

---

## 4. El flujo completo, paso a paso

### Preparación (una vez)

1. ~~Instalar **WorldEdit** en el servidor~~ ✅ hecho
2. ~~Instalar **Axiom** en el servidor~~ ✅ hecho
3. ~~Instalar el **pack de constructor** en tu cliente~~ ✅ hecho: la instancia
   `PokeReport-LunaEternal-0.1.0` ya lleva Axiom y WorldEdit CUI
4. *(opcional)* Un mundo local en **creativo, superplano** si prefieres
   construir fuera y pegar después

### Entrar a construir

```
1. PrismLauncher → instancia PokeReport-LunaEternal-0.1.0 → Launch
2. Multijugador → "PokeReport : Luna Eternal" (ya está en la lista)
3. /gamemode creative
4. /execute in lunaeternal:ciudadela run tp @s 0 64 0
5. Pulsa  Shift derecho  → se abre el editor de Axiom
```

Teclas por defecto, **leídas del propio jar** (códigos GLFW en `ClientEvents`),
no de memoria:

| Tecla | Código | Qué hace |
|---|---|---|
| **Shift derecho** | 344 | Abre y cierra el editor (`toggle_editor_ui`) |
| **Alt izquierdo** | 342 | Menú contextual: vuelo, no-clip, alcance infinito |
| `R` | 82 | Modo reemplazo |
| `0` | 48 | Ranura de herramienta de constructor |

> `/whynoaxiom` es el comando de diagnóstico si el editor no se activa.

> **Hace falta ser OP**, y lo eres (nivel 4). `AxiomServer` comprueba el permiso
> `axiom.*` o el nivel de operador; sin eso responde *"The server hasn't given
> you permission to use Axiom. Do you have OP?"*.

> **Hace falta ser OP**, y lo eres (nivel 4). `AxiomServer` comprueba el permiso
> `axiom.*` o el nivel de operador; sin eso responde *"The server hasn't given
> you permission to use Axiom. Do you have OP?"*.

### Cada vez que se construya algo

```
EN LOCAL
  //pos1 y //pos2      marcar las esquinas de lo construido
  //copy               copiar respecto a donde estás
  //schem save plaza   guardar como plaza.schem

SUBIR
  el fichero sale en  .minecraft/config/worldedit/schematics/plaza.schem
  se sube al servidor a  /config/worldedit/schematics/

EN EL SERVIDOR
  /execute in lunaeternal:ciudadela run tp @s 0 64 0
  //schem load plaza
  //paste -a           -a no pega el aire, así no borra lo de alrededor
```

> **`//paste -a` importa.** Sin la `-a`, el aire del `.schem` borra lo que
> hubiera debajo. Con estructuras que se solapan, la diferencia entre pegar y
> destruir.

### Antes de pegar nada grande

**Backup**, y aquí hay un problema que conviene saber.

> ⚠️ **Este servidor tiene 0 ranuras de backup.** El panel responde
> `TooManyBackupsException: limit of 0 backups`. El botón de backups de
> Pterodactyl **no existe para nosotros** (`INF-002`).

**La vía que sí funciona**, verificada de extremo a extremo el 2026-08-11:
comprimir la dimensión por la API y descargarla.

```
POST /files/compress   {"root":"/world/dimensions/lunaeternal",
                        "files":["ciudadela"]}
PUT  /files/rename     → ciudadela-AAAA-MM-DD.tar.gz
GET  /files/download   → se baja a build/backups/
```

La primera instantánea ya existe: `ciudadela-2026-08-11-vacia.tar.gz`
(408 KB, la plataforma vacía). Pídemela cuando quieras otra.

**Y además, guarda un `.schem` al terminar cada sesión.** Es mejor backup que
el del mundo: pesa poco, es independiente del servidor y se puede volver a
pegar en cualquier sitio.

```
//pos1 · //pos2 · //copy · //schem save plaza-v3
```

---

## 5. Qué construir, y en qué orden

De [world-structure.md](world-structure.md) §3, ordenado por lo que desbloquea:

| Orden | Zona | Desbloquea |
|---|---|---|
| 1 | **Plaza central** | Punto de aparición. Sin esto se cae al vacío |
| 2 | **Laboratorio** | El NPC del inicial tiene dónde estar |
| 3 | **Centro Pokémon** | Curar y Caja dejan de ser solo menús |
| 4 | **Mercado** | Tienda y GTS tienen un sitio físico |
| 5 | **Puerta al mundo** | Salida al Hogar y al Salvaje |
| 6 | **Salón de Medallas** | Cuando existan los gimnasios |
| 7 | Gremio, sastrería, adornos | Cuando haya clanes y cosméticos |

**Con la 1 y la 2 el servidor ya se siente un servidor.** El resto es mejora.

> Mientras no exista nada, el mod coloca una **plataforma de piedra** al entrar
> para que nadie caiga al vacío. En cuanto haya suelo real, deja de activarse
> sola.

---

## 6. Lo que hace falta de mi parte

Cuando la plaza exista:

- [ ] Fijar el punto de aparición de la ciudadela en las coordenadas reales
- [ ] Colocar el NPC del laboratorio que abre la elección de inicial
- [ ] `LunaTaxi` con los destinos reales
- [ ] `PCLink` en el Centro Pokémon — es lo que falta para la Caja

Nada de eso se puede hacer contra una dimensión vacía.

---

## Next Actions

1. ~~Instalar WorldEdit y Axiom en el servidor~~ ✅ hecho
2. ~~Pedir la whitelist de Axiom~~ ❌ innecesaria (§3-bis)
3. Decidir vía: descargar una base o construir desde cero
4. Construir plaza + laboratorio
5. Avisarme con las coordenadas → fijo spawn, NPC del inicial, taxi y `PCLink`

## Related Systems

- [Los mundos](worlds.md) · [Estructura](world-structure.md)
