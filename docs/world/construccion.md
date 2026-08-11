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

⚠️ **Falta un paso que solo puedes dar tú:** pedir la whitelist multijugador
gratuita en el Discord de Axiom — ver §3-bis. El mod está instalado, pero hasta
que la concedan, editar en vivo no funcionará.

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

## 3-bis. Axiom: está instalado, y falta un trámite gratuito

Sus términos, citados literalmente de su página:

> *"If you are using Axiom **non-commercially**, you can use it in singleplayer,
> localhost servers, or plot-based servers... If you wish to use it on your own
> **private multiplayer server**, you can request a multiplayer whitelist
> through the Axiom discord"*

> *"We ask that **'Professional Builders'** (i.e. people who make money through
> building in Minecraft) purchase a **Commercial License**"*

La licencia comercial apunta a **quien cobra por construir** — estudios que
venden builds por encargo. No es nuestro caso: construimos nuestro propio
servidor. La vía que nos corresponde es la primera, y es **gratuita**.

### Lo que hay que hacer, y es un trámite

| Paso | Quién | Coste |
|---|---|---|
| Instalar Axiom en el servidor | ✅ hecho | 0 € |
| **Pedir la whitelist multijugador en el Discord de Axiom** | **tú** | 0 € |
| Comprar licencia comercial | solo si algún día se vende el servicio de construcción | — |

> **Por qué lo tienes que pedir tú:** la whitelist se solicita desde una cuenta
> de Discord asociada al servidor. No es algo que se pueda automatizar ni que yo
> pueda hacer en tu nombre.

**Mientras tanto no estás bloqueado.** Axiom en local es libre sin ningún
trámite, y el flujo `.schem` → `//paste` del §4 funciona hoy. De hecho es mejor
flujo: sin lag, sin nadie mirando, y el `.schem` queda como copia de seguridad.

---

## 4. El flujo completo, paso a paso

### Preparación (una vez)

1. ~~Instalar **WorldEdit** en el servidor~~ ✅ hecho
2. ~~Instalar **Axiom** en el servidor~~ ✅ hecho
3. Instalar el **pack de constructor** en tu cliente — trae Axiom y WorldEdit
   CUI ya montados ([client-pack.md](../technical/client-pack.md))
4. Crear un mundo local en **creativo, superplano, sin estructuras**

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

**Backup.** Pegar es destructivo y no siempre hay `//undo` disponible tras un
reinicio. La ciudadela es una dimensión aparte
([worlds.md](worlds.md)), así que el riesgo está acotado — pero acotado no es
cero.

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
2. **Pedir la whitelist multijugador en el Discord de Axiom** (§3-bis) — tuyo
3. Decidir vía: descargar una base o construir desde cero
4. Construir plaza + laboratorio
5. Avisarme con las coordenadas → fijo spawn, NPC del inicial, taxi y `PCLink`

## Related Systems

- [Los mundos](worlds.md) · [Estructura](world-structure.md)
