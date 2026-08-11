# Cómo se construye la ciudadela

## Purpose

Responder a la pregunta práctica: **¿se construye a mano dentro del servidor, o
se puede hacer fuera y traerlo?** Y con qué herramientas.

## Dependencies

- [`worlds.md`](worlds.md) · [`world-structure.md`](world-structure.md)

## Current Status

**WorldEdit 7.3.8 instalado** en el servidor de desarrollo (2026-08-11), y la
ciudadela tiene una **plataforma de 80×80** en el origen para empezar.

⚠️ **Axiom NO está instalado en el servidor**, y no por descuido — ver §3-bis.

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

## 3-bis. Axiom: por qué no está en el servidor

Sus propios términos, citados de su página:

> *"If you wish to use it on your own **private multiplayer server**, you can
> request a multiplayer whitelist through the Axiom discord"*

> *"We ask that **'Professional Builders'** (i.e. people who make money through
> building in Minecraft) purchase a **Commercial License**"* — y esa licencia
> es la que desbloquea **Multiplayer Support**.

O sea: **el modo multijugador de Axiom está condicionado**, y nuestro servidor
va a tener tienda. Instalarlo sin resolver eso sería justo lo que D-008 dice
que no hagamos.

### Las tres salidas, y no hay prisa por elegir

| Opción | Coste | Cuándo |
|---|---|---|
| **Axiom en local + WorldEdit para pegar** | 0 € | **Ahora.** No toca la restricción: en singleplayer es libre |
| Pedir la whitelist en su Discord | 0 € | Si quieres editar en vivo y el servidor aún no factura |
| Comprar la licencia comercial | € | Cuando el servidor gane dinero y quieras multijugador |

**La primera es la recomendada, y no por el dinero:** construir en local es
mejor de todos modos — sin lag, sin nadie mirando, y el `.schem` te queda como
copia de seguridad de la construcción.

---

## 4. El flujo completo, paso a paso

### Preparación (una vez)

1. Instalar **WorldEdit** en el servidor → `mods/`
2. Instalar **Axiom** en tu cliente (y en el servidor si quieres editar en vivo)
3. Crear un mundo local en **creativo, superplano, sin estructuras**

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

1. Decidir vía: descargar una base o construir desde cero
2. Instalar WorldEdit en el servidor de desarrollo
3. Construir plaza + laboratorio
4. Avisarme con las coordenadas

## Related Systems

- [Los mundos](worlds.md) · [Estructura](world-structure.md)
