# Tesoros — cofres y llaves

## Purpose

Diseñar el sistema de cofres con llave: qué contienen, cómo se consiguen las
llaves y dónde está la línea que no se cruza.

## Dependencies

- [`monetization.md`](monetization.md) — los cuatro niveles y el test
- [`economy-overview.md`](economy-overview.md) — las monedas

## Related Documents

- [`../ui/interfaces-catalog.md`](../ui/interfaces-catalog.md)

## Current Status

**DECIDIDO (D-020, 2026-08-11).** El usuario ratificó el sistema con
legendarios, al estilo de Diosesmon. Se implementa así.

El análisis de riesgos de §2 **se conserva como registro** de que la decisión
se tomó con la información delante, no por descuido. Las mitigaciones de §5
quedan **recomendadas pero opcionales**.

## Last Decision

D-020 — Los cofres incluyen legendarios y legendarios shiny.
`monetization.md` se ajusta: T4 admite esta excepción explícita y acotada.

---

## 1. Qué es

Cofres que se abren con llaves. Las llaves se compran con moneda premium.
Al abrir, sale una recompensa de una tabla de probabilidad — un gachapón.

Es un sistema **muy rentable** y muy conocido: Diosesmon lo tiene
(*Pokémon con Disfraz Rocket · Llave: 2.775 DiosesCoins*).

**La mecánica no es el problema. El contenido sí.**

---

## 2. ⚠️ La objeción: legendarios en cofres de pago

Pediste que los cofres den *"legendarios, legendarios shiny, gachapón,
disfraces para tu pokémon"*. Los disfraces son perfectos. **Los legendarios
tienen cuatro problemas, y ninguno es una opinión mía sobre monetización.**

### 2.1 · Contradice el marco que ya aprobaste

`monetization.md` define el nivel **T4 — PODER**, prohibido sin excepciones:

> *IVs o EVs · naturalezas garantizadas · **shinies** · **legendarios** ·
> míticos · ventaja estadística en combate*

Un legendario shiny en un cofre de pago es T4 literal. No es un caso límite.

### 2.2 · Rompe la economía que diseñamos

`ECO-001` se apoya en que **la riqueza vive en los Pokémon**. Si los
legendarios se compran:

```
llave de pago → legendario → se vende en el GTS por PokéDólares
                              ↑
              esto ES la conversión dinero real → moneda del juego
              que D-014 prohíbe, solo que dando un rodeo
```

Se crea el mercado gris **por la puerta de atrás**, exactamente lo que
evitamos al hacer el ReportCoin no transferible. Y además hunde el valor de
los legendarios conseguidos jugando: el que cazó uno durante semanas ve cómo
otro lo compra en treinta segundos.

### 2.3 · Riesgo legal real, no teórico

Dos frentes distintos:

- **Mojang**: el principio central de sus reglas comerciales es *no vender
  ventajas que afecten a la competición entre quien paga y quien no*. Un
  legendario es exactamente eso. (Sigue pendiente leer el texto oficial —
  `SEC-004`.)
- **Cajas de botín con dinero real**: están **prohibidas en Bélgica y Países
  Bajos**, y reguladas o en proceso en España y otros países. Un sistema de
  llaves de pago con premios aleatorios es una caja de botín en el sentido
  legal, no en el coloquial.

### 2.4 · Qué hace Diosesmon exactamente — verificado en su wiki

Leído en `wiki.diosesmon.net` el 2026-08-11. Sus seis cofres:

| Cofre | Contenido |
|---|---|
| **Tesoro Legendario Shiny** | Legendarios con variante shiny |
| **Tesoro Legendario** | *"La cuna del coleccionismo"* |
| **Tesoro Rocket** | Aspectos cosméticos para Pokémon |
| **Tesoro de Habilidad Oculta** | Pokémon con **habilidad oculta desbloqueada para competitivo** |
| **Máquina Gacha** | Objetos aleatorios de progresión |
| **Tesoro de Evento** | Cosméticos de temporada |

Y en el PokePad tienen además **"Modificadores — compra de servicios de mejora
de estadísticas para optimizar Pokémon"**.

**Es venta directa de poder competitivo, sin rodeos.** Su wiki lo llama *"un
sistema totalmente legal y transparente"* porque publican los porcentajes.
Publicar probabilidades resuelve el problema de **transparencia del azar** —
que es real y hay que copiarlo—, pero **no toca** el otro problema: vender
ventaja de juego a quien paga. Son dos cuestiones distintas y ellos solo
responden a una.

> Que un servidor grande lo haga significa que **hasta hoy no le ha pasado
> nada**, no que sea correcto ni que a nosotros no nos pase. Ellos tienen la
> apuesta ganada por tamaño e inercia; nosotros empezaríamos de cero con el
> riesgo puesto y sin la comunidad que lo justifique.
>
> **Y hay una lectura de producto, no solo de riesgo:** si su modelo es
> *"paga y tendrás mejores Pokémon"*, ese hueco ya está ocupado por alguien
> más grande. Competir ahí es competir en su terreno. El hueco libre es el
> contrario — y encaja con la visión que ya escribimos.

---

## 2-bis. La decisión tomada — D-020

El usuario ratificó el sistema con legendarios tras leer §2. **Queda cerrado.**

### Contenido de los cofres

| Cofre | Contenido | Llave |
|---|---|---|
| **Común** | Cosméticos básicos, tintes, títulos | Juego |
| **Rocket** | Disfraces para Pokémon | Juego o premium |
| **Gachapón** | Objetos variados de progresión | Juego o premium |
| **Legendario** | Legendarios, apertura progresiva | Premium |
| **Legendario Shiny** | Legendarios con variante shiny | Premium |
| **Evento** | Exclusivos de temporada | Temporal |

### Lo que sigue siendo obligatorio, incluso con legendarios

Estas cuatro no son una objeción reciclada: son **higiene del sistema** y
protegen tanto al servidor como al jugador.

| Regla | Motivo |
|---|---|
| **Probabilidades públicas** en cada cofre | Es lo que hace defendible el sistema. Diosesmon lo hace y es lo correcto |
| **Piedad acumulada** (§4) | Acota el gasto máximo para conseguir algo concreto |
| **Idempotencia** en cada apertura (R4) | Un doble clic no puede abrir dos veces |
| **Registro en el libro de asientos** | Toda apertura auditada |

### Lo que sigue fuera, y esto sí lo mantengo

**Modificadores de estadísticas** (D-019). Diosesmon los vende; nosotros no.
La diferencia con un legendario de cofre es real: un legendario es **una
pieza**, un modificador es **una mejora aplicada a cualquier Pokémon**, sin
techo y repetible. Lo segundo no tiene fondo.

Si en algún momento quieres reconsiderarlo, lo hablamos — pero conviene que
sea una decisión aparte, no un arrastre de esta.

---

## 3. La alternativa que NO se eligió (registro)

> Se conserva porque si el sistema da problemas —regulatorios, de economía o
> de percepción— este es el plan B ya escrito, no hay que rehacerlo.

**No hay que renunciar a los cofres.** El gachapón funciona igual de bien —y
factura igual de bien— con cosméticos. De hecho es lo que más se vende en
Diosesmon: sus disfraces de Pokémon, no sus legendarios.

| ✅ Dentro del cofre | ❌ Fuera del cofre |
|---|---|
| **Disfraces para tu Pokémon** (lo pediste, y es ideal) | Legendarios |
| Peluches y decoración | Shinies |
| Títulos, prefijos, partículas | IVs, EVs, naturalezas |
| Monturas y mascotas cosméticas | Objetos comerciables |
| Aspectos de Poké Ball | Cualquier ventaja de combate |
| Fondos y marcos del LunaPad | Acceso a contenido |

### Y los legendarios, ¿de dónde salen?

**Del mundo, que es donde tienen sentido.** Es además la respuesta a *"¿qué
hace especial al Mundo Salvaje?"* ([worlds.md](../world/worlds.md)):

- Aparición rara en el Mundo Salvaje, condicionada por fase lunar y zona
- Incursiones de grupo — ya tenemos la calibración medida del proyecto anterior
- Eventos con fecha
- Recompensa de final de una vía de progresión

Así el legendario **cuenta una historia**: dónde estabas, con quién, qué luna
había. Uno comprado no cuenta nada.

### La regla de una línea

> **Del cofre sale cómo te ves, nunca lo que puedes hacer.**

Es fácil de explicar a los jugadores, fácil de defender ante cualquiera, y no
deja zona gris para futuras discusiones internas.

---

## 4. Diseño del sistema (válido con cualquier contenido)

### Tipos de cofre

| Cofre | Llave | Contenido |
|---|---|---|
| **Común** | se gana jugando | Cosméticos básicos, tintes, títulos |
| **Raro** | juego o premium | Disfraces, mascotas |
| **Legendario** | premium | Disfraces exclusivos, monturas, efectos |
| **De evento** | temporal | Cosméticos de temporada, no vuelven |

### Reglas duras

1. **Toda llave se puede conseguir jugando**, aunque más despacio. Ninguna
   recompensa es exclusiva de pago si es cosmética de progresión.
2. **Probabilidades públicas.** Cada cofre muestra su tabla exacta antes de
   abrirse. Es lo correcto, y en varios países ya es obligatorio.
3. **Previsualización** del contenido posible sin gastar nada — Diosesmon ya
   lo hace (*Previsualizar*), y está bien.
4. **Protección anti-duplicado**: cada apertura lleva clave de idempotencia
   (R4). Un cofre no puede abrirse dos veces por un doble clic.
5. **Sin "casi lo consigues"**: nada de animaciones que simulen que estuviste
   cerca. Es manipulación y no la necesitamos.
6. **Registro en el libro de asientos**: cada apertura queda auditada.

### Piedad acumulada (*pity*)

Tras N aperturas sin premio mayor, el siguiente lo garantiza. Reduce la
frustración y **acota el gasto máximo** para obtener algo concreto — que es
justo lo que las regulaciones de cajas de botín miran con lupa.

---

## 5. Si decides mantener los legendarios

Es tu proyecto y tu decisión. Si la mantienes, **estas mitigaciones reducen el
daño sin quitar el sistema**:

| Mitigación | Efecto |
|---|---|
| El legendario del cofre nace **vinculado**: no se vende ni se intercambia | Corta el mercado gris de §2.2 |
| **No apto para PvP clasificatorio** ni torneos | Aplica la regla del PvP de `monetization.md` §3 |
| IVs bajos o fijos | Es una pieza de colección, no competitiva |
| Marcado visiblemente como "de origen comercial" | Honestidad, y el prestigio del cazado se mantiene |
| Nunca shiny | El shiny es el símbolo de la caza; venderlo lo vacía |

Con esas cinco, el legendario de pago pasa de T4 a algo defendible: **una
pieza de colección, no una ventaja**. Sigue habiendo riesgo regulatorio por
la caja de botín, que solo se elimina con probabilidades públicas y piedad
acumulada (§4).

**Registra la decisión en `CLAUDE.md` §5 sea cual sea**, para que dentro de seis
meses nadie tenga que reconstruir el razonamiento.

---

## 6. La moneda de las llaves

Mencionaste **ReportCoins** para lo premium y luego **LunaCoins** para las
llaves. **Recomiendo una sola moneda premium**, por dos motivos:

1. Dos monedas de pago obligan al jugador a decidir *cuál* comprar antes de
   decidir *qué* comprar. Añade fricción justo en el paso que factura.
2. Sobra saldo inútil en una mientras falta en la otra, y eso genera tickets
   de soporte.

**Decisión técnica tomada:** en el código la moneda se llama `REPORTCOIN`,
pero **el nombre que ve el jugador es configurable**. Elegir entre
"ReportCoins" y "LunaCoins" es cambiar una línea de configuración, no una
migración de base de datos. Dime cuál prefieres cuando quieras — el diseño no
espera por eso.

---

## Next Actions

1. **Decidir §2**: cosméticos solamente, o legendarios con las mitigaciones de §5
2. Elegir el nombre visible de la moneda premium
3. `ECO-005` — tablas de probabilidad y piedad acumulada
4. `UI-006` — interfaz de Tesoros con previsualización y probabilidades

## Related Systems

- [Monetización](monetization.md) · [Economía](economy-overview.md)
- [Los dos mundos](../world/worlds.md)
