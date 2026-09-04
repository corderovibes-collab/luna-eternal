# Cobblemon Cards — qué hace cada cosa, y cómo probarla

## Purpose

Las **49 piezas y 8 bloques** del mod, para qué sirve cada una y cómo llegar a
ella. Todo leído del código y de sus recetas, no del README.

## Dependencies

- [cobblemon-cards.md](cobblemon-cards.md) — la auditoría y por qué hay un fork

## Current Status

En vivo desde el 2026-09-02. **Sin verificar en el juego.**

---

## 0. ⚠ Antes de empezar: tres cosas están apagadas a propósito

No son averías. Si las buscas, no vas a encontrarlas:

| Apagado | Por qué | Se ve como |
|---|---|---|
| **Las estadísticas de las cartas** | Una carta de pago no puede dar daño ni armadura (T4, D-007/D-014) | El archivador dice «Sin bonificación» |
| **Sobres en cofres** | Choca con Tesoros (D-020) | No aparecen nunca en un cofre |
| **Todo lo que pase del 251** | Kanto + Johto (D-017) | Ninguna carta de Gen 3+ |
| **El mercader errante** | Un segundo comerciante que nuestra economía no ve | No vende cartas |
| **La restauradora** | Decisión del usuario | Sin receta y fuera del creativo |

> ⚠ **Lo tercero es lo que hay que comprobar de verdad**: es un parche nuestro y
> es lo único que no se puede dar por bueno sin abrir muchos sobres.

---

## 1. Los comandos

**Todos son de nivel 4** — o sea, solo tú. Los constructores llevan nivel 2 por
D-028 y a nivel 2 podían acuñar cartas.

```
/cobblecard give <jugador> <pokemon> <shiny> <rareza> <stat> <valor> [fondo] [efecto]
/cobblecard render <pokemon> <shiny> <rareza> [fondo] [efecto] [more ...]
/cobblecard workshop                  el taller: carta a medida con interfaz
/cobblecard carddex fill              rellena tu Dex de cartas al 100 %
/cobblecard fill_cabinet              llena la cabina que estés mirando
/custombooster                        el creador de sobres personalizados
```

- **rareza** — `common` · `uncommon` · `rare` · `epic` · `legendary` · `mythic`
- **render** encadena hasta **5 cartas** con `more`, y abre una pantalla de
  escaparate pensada para capturas

Todo lo que no sea una carta se da con el `/give` normal:

```
/give @s cobblemon-cards:booster_pack 8
```

---

## 2. Las cartas

Una carta guarda **especie · variocolor · rareza · fondo · efecto holográfico ·
nota**. Se examina con **clic derecho** y se enseña a los demás: quien esté a
16 bloques la ve, y tu personaje **levanta los brazos** para mostrarla (eso es
su mixin, y convive con el nuestro de los trajes).

**Seis rarezas** y **más de 45 efectos holográficos** procedurales.

---

## 3. Los sobres — 22

| Objeto | Qué trae |
|---|---|
| `booster_pack` | 5 cartas de cualquier especie |
| `booster_pack_gen1` … `gen9` | solo de esa generación |
| `booster_pack_<tipo>` (18) | solo de ese tipo |

> ⚠⚠ **Con el tope en 251, `gen3`…`gen9` salen VACÍOS o fallan.** Existen porque
> están en el registro y quitarlos habría roto sus recetas y traducciones; no se
> reparten por ninguna vía nuestra. **Los útiles aquí son `gen1` y `gen2`**, que
> filtran exactamente Kanto y Johto.

Se abren con **clic derecho** y sale su pantalla: pulsas las 5 cartas de una en
una y las recoges con ESC.

---

## 4. Los archivadores — 6

Guardan cartas y se abren con la tecla asignada o desde nuestro PokePad.

| Archivador | Páginas | Receta |
|---|---|---|
| Cuero | 1 | cuero + cuerda + libro |
| Hierro | 2 | hierro + el de cuero |
| Oro | 3 | oro + el de hierro |
| Diamante | 6 | diamante + el de oro |
| Netherita | 10 | mejora del de diamante |
| **Álbum de colección** | **1.000** | cristal + el de cuero |

> ⚠ **Con las estadísticas apagadas son almacenamiento y nada más.** Su panel de
> bonificación dirá «Sin bonificación», y es correcto.
>
> ⚠ Se pueden equipar en una ranura de **Accessories** (ya instalado).

---

## 5. El polvo y su cadena — 4

**El polvo es la moneda interna del mod, y es lo que mejor funciona de todo.**

Una sola fuente —reciclar repetidas— y **dos sumideros**, después de retirar el
mercader y la restauradora:

| Sumidero | Para qué |
|---|---|
| **Cargar un disco de estructura** | **el bueno**: convierte repetidas en la carta que sí quieres |
| Estación de calificación | 5 de polvo por nota |

> ⚠⚠ **El bucle entero, en una frase:** juntas repetidas → las reciclas →
> con 1.000 de polvo cargas un disco, escaneas cinco veces el Pokémon que
> quieras y **te imprimes su carta en Legendaria**.

```
9 polvo   = 1 saquito
9 saquitos = 1 saco (bloque)
```

y se deshace en las dos direcciones. Lo que da una carta al reciclarla:

| Rareza | Polvo |
|---|---|
| Común | 1 |
| Poco común | 2 |
| Rara | 3 |
| Épica | 5 |
| Legendaria | 10 |
| Mítica | 15 |

**×2 si es variocolor · +2 si tiene fondo · +3 si tiene efecto holográfico.**

---

## 6. Las máquinas — 5 bloques

### Recicladora de cartas
Carta → polvo, según la tabla de arriba. 40 ticks (2 s) por carta.
Receta: hierro + cobre + lámpara de redstone + antorcha + **una mejora**.

### Estación de calificación
Le pone **nota del 1 al 10** a una carta. Cuesta **5 de polvo** y tarda 100
ticks.

> ⚠⚠⚠ **NECESITA UN `cobblemon:monitor` JUSTO ENCIMA.** Es el monitor de PC de
> Cobblemon, y sin él no arranca — el mensaje lo dice, pero es el fallo número
> uno al probarla. Leído del código: comprueba el bloque de arriba y exige
> namespace `cobblemon` y ruta `monitor`.

### ~~Restauradora de cartas~~ — RETIRADA

Subía la nota de una carta ya calificada. **Fuera por decisión del usuario**
(2026-09-02): sin receta y fuera del creativo.

> ⚠ Se retira **haciéndola inalcanzable, no borrando su registro**. Quitar un
> bloque del registro obliga a tocar su receta, su traducción, su modelo, su
> tabla de botín y la pestaña del creativo, y cada cabo suelto es un error al
> cargar. Mismo criterio que los sobres `gen3`…`gen9`.
>
> ⚠ Y de diseño sale mejor: con la nota valiendo algo, **calificar pasa a ser
> una tirada definitiva** en vez de «califica, restaura, restaura hasta el 10».

### Cabina de cartas
**12.000 cartas** con buscador, orden y páginas. Receta: madera + cofre +
álbum de colección.

### Saco de polvo
Bloque de almacenamiento, 81 de polvo.

---

## 7. Los proyectores — 3

Enseñan una carta como **holograma 3D** encima del bloque.

| | |
|---|---|
| **Proyector** | una carta. Bloque de hierro + saquito |
| **Miniproyector** | hasta **4 en un mismo bloque** |
| **Proyector avanzado** | hasta **27 cartas** en secuencia |

**Seis modos**, y se cambian **agachado + clic derecho**: rotación continua ·
mirar al jugador · dinámico · fijo · plano · flotar.

> **Y hay un huevo de pascua**: un proyector encima de una **gramola** pone
> música de Pokémon elegida según la carta.

---

## 8. El escaneo — Instant-Dex + disco

La vía para **fabricar** una carta concreta en vez de esperar a que salga.

> ⚠⚠⚠ **«Legendaria» AQUI ES LA RAREZA DE LA CARTA, NO UN POKEMON LEGENDARIO.**
> Un Rattata puede tener una carta Legendaria. Son dos cosas con el mismo
> nombre, y es la confusión número uno de todo el sistema.

1. **Disco de estructura** — se carga con polvo a **clic derecho** (mayús +
   clic derecho mete la pila entera). El polvo decide la rareza que va a salir:

   | Polvo | Rareza |
   |---|---|
   | < 50 | Común |
   | ≥ 50 | Poco común |
   | ≥ 200 | Rara |
   | ≥ 500 | Épica |
   | ≥ 1000 | Legendaria |

2. **Instant-Dex** — con el disco en el inventario, se escanea un Pokémon
   **salvaje**. Puedes elegir **cualquiera**… pero solo el primero: el disco se
   **calibra** con esa especie y a partir de ahí rechaza las demás.
3. **Cinco escaneos** del mismo Pokémon (`MAX_SCANS = 5`, leído del código) y
   imprime la carta.

> ⚠ Escanear uno variocolor añade los datos variocolor al disco.

---

## 9. Los extras

| Objeto | Qué hace |
|---|---|
| **Boleto divino** | Clic derecho: **tu siguiente sobre es un God Pack**. Sale de abrir sobres al 1 % |
| **Atajo de calificación** | Termina al instante el análisis de una estación |
| **Dex de cartas** | La lista de todo lo que has coleccionado: nacional, regionales y megas |
| **Cinta de campeón** | Cosmético |
| **Sobre personalizado** | Lo crea `/custombooster`: eliges 5 objetos, aspecto y nombre |

---

## 10. Los huevos de pascua

Todos se hacen **escaneando con el Instant-Dex** en unas condiciones concretas:

| | |
|---|---|
| **MissingNo.** | de noche, con **luna llena** y a oscuras |
| **Fantasma de Pueblo Lavanda** | un tipo Fantasma, cerca de medianoche, sobre **arena de almas** |
| **Bidoof divino** | un Bidoof llevando una **manzana dorada** |
| **Onix de cristal** | un Onix llevando una **esquirla de amatista** |
| **Lugia oscuro** | un Lugia **en tormenta** y con **Marchitamiento** |
| **Sylveon del orgullo** | un Sylveon con tintes de la bandera trans |
| **Tú y Mew** | un Mew llevando **tu carta de jugador** |
| **Carta de jugador** | escanear a **otro jugador**: sale Mítica nota 10 con su skin |

---

## 11. Lo que hay que mirar de verdad

Por orden de «si esto falla, importa»:

1. ⚠⚠⚠ **Que no salga ninguna carta por encima del 251.** Es nuestro parche y
   es la razón de que exista el fork. Se prueba abriendo muchos sobres
   normales, no de generación.
2. ⚠⚠ **Que el archivador diga «Sin bonificación».** Si dice otra cosa, las
   estadísticas están encendidas y eso cruza la línea roja.
3. ⚠ Que los sobres **no aparezcan en cofres**.
4. Que la **estación de calificación** funcione con su monitor encima.
5. Que el mixin de «enseñar carta» **no se pelee con el traje de rango** — los
   dos tocan el mismo método del modelo del jugador.

## Next Actions

Probarlo. Nada de esto está verificado en el juego todavía.
