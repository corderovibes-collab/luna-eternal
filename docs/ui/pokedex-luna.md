# La Pokédex a luz de luna

## Purpose

Cómo se reviste la Pokédex de Cobblemon con la identidad del servidor, y hasta
dónde se puede llegar sin tocar código ajeno.

## Dependencies

- [`../technical/client-pack.md`](../technical/client-pack.md) §2 — de dónde sale el pack

## Current Status

**Desplegado.** 83 texturas revestidas (66 KB), incrustadas en `lunaneon` y
**activadas solas**.

```
genera    python tools/gen_pokedex.py --comparativa
compila   cd neon && bash build.sh
despliega python tools/desplegar.py neon --reiniciar
publica   python tools/gen_manifest.py --publicar
```

---

## 1. Lo que se puede y lo que no

La Pokédex de Cobblemon se dibuja con **114 texturas** en
`assets/cobblemon/textures/gui/pokedex/`. Todo lo que es fondo, casco, ranura,
pestaña o marco es una textura, y un resource pack las sustituye sin más.

**Pero el texto lo pinta el código.** Son dos colores fijos dentro de
Cobblemon, y ningún resource pack los alcanza:

| Color | Usos | Qué es |
|---|---|---|
| `0x606B6E` | 16 | Gris. Los números, los nombres, las descripciones |
| `0x3A96B6` | 3 | Turquesa. Acentos |

Y **ese gris va encima de los paneles claros**. De ahí la regla que gobierna
todo el revestido:

```
la pantalla cambia de TONO pero conserva la LUMINANCIA
el casco sí se oscurece: encima no hay texto
```

Si oscureciéramos la pantalla, los números quedarían ilegibles y no habría
forma de arreglarlo desde aquí. El turquesa fijo, en cambio, encaja de forma
natural en una paleta azul, así que los acentos siguen pegando.

> **Para una Pokédex de verdad oscura** haría falta un mixin de cliente que
> sustituya esos dos colores. Se planteó y **se descartó de momento**: ata el
> servidor a las clases internas de Cobblemon y se rompe cuando ellos toquen su
> GUI. La opción sigue ahí si algún día compensa.

---

## 2. Cómo se reviste

`tools/gen_pokedex.py` lee las texturas **del jar de Cobblemon** —descargado de
Modrinth y cacheado, no de una carpeta de la máquina— y les cambia el tono.
Nada se dibuja a mano: si Cobblemon cambia sus texturas, se reejecuta.

| | |
|---|---|
| **Solo el cian** | Se desplazan únicamente los píxeles con tono entre 165° y 205°. Así las plataformas de tipo —fuego naranja, planta verde— se quedan intactas **sin tener que listarlas**: no son cian, no se tocan |
| **83 de 114** | Las otras 28 no llevan ni un píxel cian (flechas, iconos) y no se incluyen: solo pesarían |
| **El casco, al 70 %** | Cobblemon tiene **siete** Pokédex de colores distintos, y son objetos separados. Arrastrar el tono del todo las dejaría idénticas; no moverlo dejaba la roja en granate. Al 70 % todas leen como nocturnas y aún se distinguen |

---

## 3. Cómo llega activado

**El pack vive dentro del jar de `lunaneon`**, en
`resourcepacks/pokedex_luna/`, y el mod lo registra con
`ResourcePackActivationType.DEFAULT_ENABLED`. Minecraft lo enciende solo la
primera vez que lo ve, y sigue pudiendo apagarse en *Opciones → Paquetes de
recursos* como cualquier otro.

### El intento que falló, y por qué

Primero se hizo como un `.zip` en `resourcepacks/` más una línea añadida a la
plantilla `config/yosbr/options.txt`. **El zip se instalaba y se quedaba
apagado.** No fue un descuido: YOSBR copia esa plantilla **solo si
`options.txt` no existe**, así que a quien ya había jugado una vez no le
llegaba nunca — es decir, a todo el mundo.

La vía buena estaba a la vista en el `options.txt` de cualquier jugador:

```
resourcePacks:["vanilla","fabric","cobblemon:gyaradosjump","cobblemon:regionbiasforms",…]
```

Esos `cobblemon:` son packs que Cobblemon lleva **dentro de su jar** y registra
igual. Por eso aparecen solos.

> El registro va en un `try/catch`: es puramente cosmético y de cliente, pero el
> mismo jar corre en el servidor. Que falle no puede llevarse por delante el
> registro de los 96 bloques de neón, que sí es esencial.

---

## 4. Licencia

Cobblemon es **MPL-2.0**, que permite obra derivada y uso comercial — al
contrario que la CC-BY-NC-ND de `stendhal`, que por eso se excluyó (D-031).

El MPL es copyleft **por fichero**: estas texturas derivan de las suyas, así que
siguen siendo MPL-2.0. El pack lleva dentro `LICENSE-COBBLEMON.txt` con la
atribución y el aviso. No hace falta nada más.

## Next Actions

1. Verlo en el juego y ajustar tono/saturación si hace falta — es una constante
   en `gen_pokedex.py`
2. Decidir si el resto de interfaces de Cobblemon (PC, resumen, combate) se
   revisten igual: el mismo script sirve cambiando la ruta

## Related Systems

- [El pack de cliente](../technical/client-pack.md) · [La interfaz propia](interfaz-cliente.md)
