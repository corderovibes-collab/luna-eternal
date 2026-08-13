# Cómo está hecho el PokePad de referencia

## Purpose

Qué aprendimos al **estudiar** el mod de cliente de Diosesmon, instalado en la
máquina del usuario y mirado con su permiso. Es análisis de arquitectura, no
material para copiar: no se importa aquí ni su código ni su arte.

## Dependencies

- [`interfaz-cliente.md`](interfaz-cliente.md) — cómo se construye la nuestra

## Current Status

`ANALYSIS`. Tres decisiones nuestras quedan confirmadas y tres hay que
revisar.

---

## 1. El dato que más importa: **346 × 207**

Todo su arte de interfaz está dibujado sobre un lienzo de **346 × 207 píxeles
de interfaz**, y hay **un fondo completo por aplicación**:

```
pokepad_base_new.png   346x207     el chasis
pokepad_cazas.png      346x207     Cazas, entera
pokepad_gts.png        346x207     GTS, entera
pokepad_tienda.png     346x207
pokepad_trabajos.png   346x207     ... una por app
sprites/app_cazas.png    24x25     el icono de la app
```

**No es un marco genérico con contenido dentro: cada pantalla es una
ilustración propia.** Eso es lo que hace que cada una se sienta diseñada y no
generada. Nosotros tenemos un único `pokepad.png` compartido, y por eso todas
nuestras pantallas se parecen.

> Nuestro Pad se dibuja a ~600 px de ancho, casi el doble que el suyo. Más
> grande no es mejor: a 346 el arte se ve nítido porque está dibujado a ese
> tamaño, y el texto ocupa proporcionalmente más, así que **cabe**. Nuestro
> problema de textos cortados viene en parte de aquí.

## 2. Lo que confirma nuestras decisiones

| Nuestra decisión | Lo que hacen ellos |
|---|---|
| **D-025** mod de cliente propio | `"environment": "client"`, un solo mod de cliente |
| Panel lateral con cabeza y monedas | `PlayerHeadPreview`, `CoinBalanceRow` |
| Botón «+» que lleva a la tienda | `StoreLink`, con `CosmeticTebexPriceStore` |
| Celdas bloqueadas para lo que falta | `ComingSoonScreen` |

Es tranquilizador: llegamos a lo mismo por nuestra cuenta.

## 3. Lo que hacen mejor, y hay que copiar como idea

### 3.1 · Chasis + aplicaciones

`PokePadChassisScreen` dibuja el aparato; `PhoneApp` + `PhoneAppRegistry`
registran las aplicaciones, y `PokePadAppOrderConfig` deja **al jugador
reordenar los iconos**. Nuestro `AlmanacPad` mezcla las dos cosas.

### 3.2 · Estado de cliente por módulo

Cada módulo tiene su `Client…State` y su `…ClientNetwork`:

```
modules/gts/ClientGtsState · GtsClientNetwork
modules/gts/GtsScreen · GtsCreateScreen · GtsFilterScreen
          · GtsListingScreen · GtsMyOffersScreen · GtsBuyQuantityScreen
```

**Aquí está la diferencia de fondo con nosotros.** Nuestro protocolo es
genérico: el servidor manda una pantalla ya resuelta y el cliente la pinta.
Eso es más simple y más seguro (P6), y para menús de iconos va perfecto.

Pero un GTS con filtros, paginación y campo de cantidad **no se puede
expresar así**: cada pulsación exigiría un viaje al servidor para redibujar.
Ellos tienen seis pantallas y estado local justo por eso.

> **Conclusión honesta:** nuestro modelo genérico se queda corto para GTS,
> Tienda y Tesoros. No hay que tirarlo —sirve para el 80 % de las pantallas—,
> pero esas tres necesitarán su propio protocolo, con el servidor validando
> igual: el cliente puede *filtrar una lista que ya tiene*, pero el precio y
> la compra los sigue decidiendo el servidor.

### 3.3 · Familia de widgets

`PokePadDraw`, `PokePadSprites` y cinco tipos de botón
(`IconButton`, `LabeledIconButton`, `SpriteButton`, `TextButton`,
`AnimatedIconButton`), más `NumericEditBox` y `ScaledEditBox`.

Nosotros tenemos `Tarjeta` y poco más. Hace falta esa familia para que las
pantallas complejas no se escriban a mano cada vez.

## 4. Lo que tienen y nosotros no

Del listado de módulos, funcionalidad que ni habíamos planteado:

`atm` · `peluqueria` · `pines` · `rtp` · `taxi` · `wondertrade` ·
`medallero` · `pokestops` · `hud/QuestHudOverlay`

De todas, la más barata y con más efecto es **`QuestHudOverlay`**: la misión
activa visible en pantalla sin abrir nada.

## 5. Qué hacemos con esto

| # | Acción | Por qué |
|---|---|---|
| 1 | Bajar nuestro Pad a **346 × 207** | Es el tamaño para el que está pensado este tipo de arte; arregla de raíz los textos cortados |
| 2 | **Un fondo por pantalla**, no uno compartido | Es lo que hace que cada app parezca diseñada |
| 3 | Separar chasis de aplicaciones | Hoy están mezclados en `AlmanacPad` |
| 4 | Familia de widgets propia | Sin ella, cada pantalla nueva es artesanía |
| 5 | Protocolo específico para GTS/Tienda/Tesoros | El genérico no da para filtros ni cantidades |

> **Nada de esto es copiar.** Son decisiones de arquitectura que se toman igual
> partiendo de cero; verlas resueltas solo ahorra descubrirlas a golpes.

## Next Actions

1. `UI-021` — llevar el Pad a 346×207
2. `ART-002` — un fondo por pantalla (prompts nuevos)
3. `UI-022` — separar chasis y apps

## Related Systems

- [La interfaz de cliente](interfaz-cliente.md) · [Catálogo](interfaces-catalog.md)
