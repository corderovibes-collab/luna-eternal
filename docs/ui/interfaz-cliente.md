# La interfaz de cliente

## Purpose

Cómo se construye la interfaz de Luna Eternal, y **qué no se vuelve a hacer
nunca**. Es el documento que sustituye a `navigation.md` y `visual-identity.md`,
que describían un sistema —menús de cofre pintados con una fuente— que ya no
existe.

## Dependencies

- [`interfaces-catalog.md`](interfaces-catalog.md) — qué pantallas hacen falta
- [`pokepad-referencia.md`](pokepad-referencia.md) — el análisis de la referencia

## Current Status

**Nada construido, a propósito.** El 2026-08-12 se borró la implementación
entera (D-026) a la espera del arte real.

## Last Decision

D-026 · **ninguna pantalla se implementa como menú de cofre.** Ni como
provisional, ni como respaldo, ni «mientras tanto».

---

## 1. La regla, en una frase

> **Todo lo que el jugador ve se dibuja en el cliente, con arte propio, y lo
> decide el servidor.**

No hay excepciones «temporales». Un menú de cofre provisional tiene dos
problemas: se queda —porque funciona— y **fija el diseño** de lo que venga
después a la rejilla de 9 columnas de un cofre.

## 2. Por qué un cofre no puede ser la interfaz

No es una cuestión de gusto. La referencia del género lo demuestra
(`pokepad-referencia.md`):

| | Menú de cofre | Lo que hace falta |
|---|---|---|
| Rejilla | 9 columnas fijas, celdas de 18 px | **5 columnas**, celdas del tamaño que decida el arte |
| Inventario del jugador | **siempre visible debajo** | no se enseña |
| Botones | solo dentro de la rejilla | donde el diseño quiera |
| Fondo | el del cofre; se puede tapar con trucos de fuente | una imagen, sin trucos |
| Texto | una línea de nombre y tooltip | libre |
| Pokémon | un icono de objeto | **modelo 3D real** |

Se intentó el camino del truco —una fuente de espacio negativo que pinta el
fondo sobre el título del cofre— y **funcionaba**. El problema no era que
fallara: era que el techo seguía siendo el de un cofre.

## 3. Reparto de responsabilidades

```
SERVIDOR                                CLIENTE
  decide qué se ve y cuándo               dibuja
  valida cada pulsación                   manda "he pulsado X"
  toda la lógica de juego                 cero lógica de juego
```

**P6 no se toca.** El cliente no sabe cuánto dinero puede gastar, ni qué está
desbloqueado: lo pinta porque el servidor se lo dijo. Cualquier validación que
viva en el cliente es un agujero, no una optimización.

De la implementación anterior, tres cosas se aprendieron y hay que conservar:

| Lección | Por qué |
|---|---|
| **El servidor guarda qué pantalla tiene abierta cada jugador** | Sin eso, un cliente modificado pulsa botones que nunca vio, incluidos los de otra pantalla |
| **El historial de «atrás» vive en el servidor** | Si lo decide el cliente, se modifica para saltar a una pantalla que no te toca |
| **El protocolo va versionado** | Un cliente viejo contra un servidor nuevo tiene que fallar de forma limpia, no a medias |

## 4. Qué hace falta antes de escribir código

**El arte, y esa es la razón de que no haya nada.** El cuello de botella nunca
fue el código: la implementación anterior se escribió en un día y su techo lo
puso el arte de relleno generado por script.

Lo que hay que recibir, por pantalla:

| Pieza | Qué es |
|---|---|
| Fondo | La pantalla completa: marco, área de contenido, paneles |
| Celda | Los tres estados: reposo, encima, bloqueada |
| Iconos | Uno por sección (ver el inventario del catálogo) |
| Botones | Atrás, inicio, cerrar, y los que pida el diseño |

> **Una condición que no es negociable: la composición tiene que ser la misma
> en todos los fondos.** El código mide dónde cae el área de contenido una vez;
> si en un fondo queda más arriba o más estrecha, la rejilla no encaja y hay
> que medir a mano pantalla por pantalla.

## 5. Lo que ya está resuelto y no hay que volver a investigar

Aunque el código se borró, estos hallazgos costaron tiempo y siguen siendo
ciertos:

- **Dibujar un Pokémon en 3D sin parpadeo son TRES cosas a la vez:** rotación
  nueva por llamada, dibujar en pasadas separadas y limpiar el buffer de
  profundidad. Con dos de las tres, parpadea.
- **Los tipos de paquete se registran en el entrypoint `main`**, no en el de
  servidor: `main` es el único que corre en los dos lados. Registrarlos solo en
  el servidor hacía que el cliente reventara al arrancar.
- **Nunca reemplazar el jar del cliente con el juego abierto.** Revienta más
  tarde con `ZipFile invalid LOC header` en un stack que no se parece a la
  causa. Hoy lo cubre el launcher, que reconoce ese síntoma y ofrece reparar.

## Next Actions

1. **Recibir el arte** (`ART-002`) — es del usuario, y bloquea todo lo demás
2. Definir la composición sobre el primer fondo real
3. Volver a crear `mod/src/client/` y el protocolo, con el arte ya medido
4. Pantallas por orden del catálogo, empezando por la principal y la Cartera

## Related Systems

- [Catálogo de interfaces](interfaces-catalog.md) · [Referencia](pokepad-referencia.md)
- [El launcher](../technical/launcher.md) — es quien reparte el mod de cliente
