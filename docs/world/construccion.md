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

### ⚠️ La whitelist NO hace falta. Verificado, no supuesto

Yo dije que sí hacía falta. **Me equivoqué**, y esto es lo que lo demuestra.

El candado no está en el servidor: `AxiomServer` solo comprueba permisos
(`axiom.*` o ser OP). Quien decide es **el cliente**, con este método:

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
GET …/connect?uuid=432ef323-…&server=s12.mia.us.tarohosting.com:33043
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
| Axiom en el cliente | ✅ instalado en la instancia de PrismLauncher |
| Pedir whitelist en su Discord | ❌ **innecesario** — `https://discord.gg/axiomtool` si algún día cambia |

> **Si algún día dejara de funcionar**, el síntoma sería el mensaje
> *"This server has Axiom, but your client doesn't support multiplayer"*, y el
> diagnóstico se pide con el comando `/whynoaxiom`. El flujo `.schem` →
> `//paste` del §4 seguiría funcionando igual.

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
