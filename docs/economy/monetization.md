# Monetización

## Purpose

Convertir *"free-to-play con paquetes de pago, tipo Diosesmon, pero sin ser
pay-to-win del todo"* en un **marco decidible**: una regla que responda sí o no
a cualquier producto futuro sin volver a discutirlo.

## Dependencies

- [`../../CLAUDE.md`](../../CLAUDE.md) — principio P4
- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md)

## Related Documents

- [`../architecture/modpack-decision.md`](../architecture/modpack-decision.md)
- `economy-overview.md` — pendiente (`ECO-001`)

## Current Status

**PROPUESTA.** Deriva de la decisión del usuario (2026-08-11): F2P + paquetes
de pago con beneficios, referencia Diosesmon.

## Last Decision

D-007 — El modelo es F2P con paquetes de pago. Sustituye al P4 original
("nunca ventaja competitiva"), que era más restrictivo de lo que el proyecto
quiere.

---

## 1. El problema con "pay-to-win pero no tanto"

Es la intención correcta y la formulación más peligrosa que existe, porque
**no permite decidir nada**. Cuando dentro de tres meses alguien proponga
"vendamos un multiplicador de captura ×2", la frase no dice si sí o si no.
Y la decisión acabará tomándose por lo que haga falta facturar ese mes.

Así que hay que sustituirla por un test que sí decida.

### El test equivocado: *"¿es justo?"*

No sirve. Es subjetivo, se discute infinitamente, y cada uno lo mueve según le
convenga.

### El test correcto: *"¿inyecta en la economía?"*

Un producto es peligroso cuando **crea dinero u objetos que el juego no ha
producido**. Y el motivo no es moral, es mecánico:

> El diseño económico (`ECO-001`) equilibra **sources** contra **sinks**. Si la
> tienda vende moneda, la tienda se convierte en la mayor *source* del
> servidor, y **todos los sinks diseñados quedan sorteados a la vez**. El
> equilibrio no se degrada: desaparece.

Y aquí está lo que suele pasarse por alto — **es mal negocio, no solo mal
diseño**:

```
vendes moneda → entra dinero sin producirse → inflación
             → suben los precios del mercado
             → lo que compró tu cliente de pago hace un mes vale menos hoy
             → el cliente que más gasta es el que más pierde
```

**Vender dinero devalúa las compras de tus mejores clientes.** Un servidor con
economía sana vende más a largo plazo que uno inflado, porque lo que se compra
conserva su valor.

---

## 2. Los cuatro niveles

Todo producto de la tienda cae en uno. **El nivel decide las reglas, no el
precio.**

### 🟢 T1 · IDENTIDAD — venta libre

Cosmético puro. Cero efecto sobre el juego.

> Skins · sombreros · mascotas · trajes · prefijos · títulos · partículas ·
> colores de chat · efectos de captura · pieles de Poké Ball · emotes ·
> decoración

| | |
|---|---|
| Riesgo económico | **Ninguno** |
| Riesgo competitivo | **Ninguno** |
| Regla | Sin límite. Es el motor principal de ingresos |

**Debe ser el grueso de la facturación.** Diosesmon lo demuestra: tres
categorías de cosméticos, nueve sets de armadura temática, prefijos. Es lo que
más se vende y lo único que no rompe nada.

Y tiene una propiedad que ningún otro nivel tiene: **su valor sube cuantos más
jugadores hay**, porque un cosmético solo vale si alguien lo ve.

---

### 🟡 T2 · COMODIDAD — venta con revisión

Ahorra fricción sin generar recursos.

> Homes adicionales · más espacio de PC · cola prioritaria · autoclasificación ·
> filtros guardados · acceso a estadísticas · slots de equipo cosmético

| | |
|---|---|
| Riesgo económico | Bajo |
| Riesgo competitivo | Bajo |
| Regla | **No puede saltarse un sink diseñado** |

**El matiz que importa:** si en `ECO-001` decidimos que ampliar el PC cuesta
dinero del juego como sink, entonces venderlo por dinero real **sí** rompe la
economía. La comodidad es segura solo cuando no sustituye a un gasto diseñado.

Cada producto T2 se revisa contra la lista de sinks antes de aprobarse.

---

### 🟠 T3 · ACELERACIÓN — zona negociada, con topes duros

Es el nivel que el usuario ha pedido explícitamente. **Es la zona real de
riesgo**, y por eso es la que necesita reglas más estrictas.

> Boosters de XP · kits periódicos · multiplicadores de recompensa ·
> señuelos · acceso anticipado a contenido no competitivo

| | |
|---|---|
| Riesgo económico | **Alto** |
| Riesgo competitivo | Medio |

**Las cinco reglas de T3, no negociables:**

1. **Nunca moneda directa.** Ni "compra 10 000 PokéDollars". Es la única forma
   garantizada de romper la economía.
2. **Nunca objetos comerciables.** Si lo que se compra se puede revender en el
   GTS, es moneda con otro nombre — y además se convierte en un mercado
   secundario que no controlamos.
3. **Tope absoluto.** Un booster tiene techo. "×2 de XP" sí; "×10 acumulable"
   no. El jugador de pago va **más rápido**, nunca **más lejos**.
4. **Acelera lo repetitivo, nunca lo escaso.** Se puede acelerar el nivelado.
   **No** se puede acelerar la captura de un shiny, ni un legendario, ni un
   descubrimiento único.
5. **Cooldown, no acumulación.** Los kits tienen frecuencia fija y no se
   apilan comprando más.

> **La frase que resume T3:** *el que paga llega antes; nunca llega a un sitio
> al que el otro no puede llegar.*

---

### 🔴 T4 · PODER — nunca, bajo ninguna circunstancia

> IVs o EVs · naturalezas garantizadas · shinies · legendarios · míticos ·
> ventaja estadística en combate · acceso a zonas de progresión · saltarse
> requisitos · objetos competitivos exclusivos

| | |
|---|---|
| Riesgo | **Destruye el producto** |
| Regla | Prohibido. Sin excepciones, sin "solo por el aniversario" |

**Por qué es absoluto:** el brief entero se apoya en que la progresión tenga
valor. Un shiny comprado destruye el significado de los cientos de horas del
que lo cazó — y con él, la razón por la que ese jugador sigue conectándose.

Además, **T4 mata T1**. Los cosméticos se compran por estatus. Si el estatus se
puede comprar directamente, el cosmético deja de significar nada. Vender poder
canibaliza el negocio que sí funciona.

---

## 3. La regla del PvP

Una línea que resuelve la mayor parte del problema competitivo:

> **Ningún efecto de pago se aplica en PvP clasificatorio, torneos ni
> clasificaciones oficiales.**

Boosters, kits y aceleradores quedan suspendidos en contexto competitivo.
Permite ser generoso en T3 durante el juego normal sin que nadie pueda decir
nunca *"me ganó porque pagó"*.

Es barato de implementar (un contexto de LuckPerms) y protege lo único que no
se puede recuperar una vez perdido: la credibilidad de la competición.

---

## 4. Los rangos

Un rango **no es un nivel de poder: es un paquete de T1 + T2**, con quizá un
toque acotado de T3.

```
Rango = identidad visible  +  comodidad  +  (aceleración con tope)
        ───────────────────────────────────────────────────────────
        prefijo, color,     homes, PC,      booster limitado
        cosméticos,         cola            y suspendido en PvP
        partículas
```

Y una propiedad importante: **el rango debe seguir siendo deseable para alguien
que ya lo tiene todo desbloqueado por juego.** Si la única razón de comprarlo es
saltarse progresión, es T4 disfrazado.

---

## 5. Por qué el F2P tiene que ser genuinamente bueno

No es filantropía. Es la condición para que lo de pago valga algo.

| El jugador F2P es… | Por qué lo necesitas |
|---|---|
| **Público** | Un cosmético sin nadie que lo vea no vale nada |
| **Contraparte** | Sin mercado con volumen no hay economía |
| **Competencia** | Sin rivales, ganar no significa nada |
| **Comunidad** | La masa crítica es lo que hace un MMORPG |
| **Futuro cliente** | Nadie paga por un juego que no ha disfrutado gratis |

> Un servidor donde solo se divierten los que pagan **se queda sin los que
> pagan**, porque desaparece aquello por lo que pagaban.

**Criterio operativo:** un jugador F2P debe poder llegar al endgame, competir y
completar la colección. Más lento. Nunca bloqueado.

---

## 6. El test de una sola pregunta

Para cualquier producto futuro, en orden. La primera que dé "sí" decide:

```
1. ¿Da estadísticas, IVs/EVs, shinies, legendarios o acceso a progresión?
   → SÍ = T4 = NO SE VENDE.  Fin.

2. ¿Crea moneda u objetos comerciables?
   → SÍ = inyección económica = NO SE VENDE.  Fin.

3. ¿Sustituye a un sink que diseñamos en ECO-001?
   → SÍ = rediseñar o descartar.

4. ¿Se aplica en PvP clasificatorio?
   → SÍ = suspenderlo ahí.

5. ¿Tiene tope y cooldown?
   → NO = ponérselos.

6. ¿Un jugador sin pagar puede conseguir lo mismo con tiempo?
   → NO = revisar. Debería poder, salvo en T1 puro.

   Si llega aquí: SE VENDE.
```

Este test es la respuesta operativa a *"pay-to-win pero no tanto"*.

---

## 7. Riesgos abiertos

| Riesgo | Estado |
|---|---|
| **Reglas comerciales de Mojang** | 🟡 **Parcialmente verificado** — ver §7-bis |
| Presión de ingresos a corto plazo | El test de §6 existe precisamente para resistirla. Su valor está en aplicarlo cuando incomoda |
| Inflación por acumulación de T3 | Se mide en `ECO-001`; los topes son la defensa |
| Licencias de terceros | Cada mod de terceros puede prohibir uso comercial. Verificar **uno por uno** antes de instalar |

> El último punto es el mismo que descartó CobbleVerse. **Con monetización
> confirmada, la licencia deja de ser un detalle y pasa a ser un criterio de
> selección de mods.** Todo mod candidato se revisa por licencia antes que por
> funcionalidad.

---

## 7-bis. Reglas de Mojang — lo que sé y lo que NO

**Honestidad sobre el alcance:** intenté leer el texto oficial de
`minecraft.net/usage-guidelines` el 2026-08-11 y **la página no respondió**
(timeout, en tres intentos y por dos rutas distintas). Las fuentes secundarias
disponibles son artículos de marketing de empresas de hosting, que no cito como
autoridad.

### Lo que está bien establecido

El principio central de las guías comerciales de Mojang, estable durante años:

> **No se pueden vender ventajas de juego que afecten a la competición entre
> quien paga y quien no.**

Cosméticos, rangos y mejoras de calidad de vida se aceptan; vender poder
competitivo, no.

### Lo que esto implica para T3 — y es incómodo

**Nuestro nivel T3 (aceleración) es la zona de riesgo también en lo legal.** Un
booster de XP es defendible como "no competitivo" o atacable como "ventaja de
juego", según cómo se lea la norma y cómo esté implementado.

Mitigaciones que ya tenemos y que juegan a favor:

- **La regla del PvP** (§3): ningún efecto de pago aplica en competición. Es
  precisamente el argumento que separa "va más rápido" de "compite con ventaja".
- **Tope y cooldown** en todo T3.
- **Nunca moneda ni objetos comerciables**.

### Qué falta, y no lo puedo hacer yo

- [ ] **Leer el texto oficial vigente directamente.** No conseguí acceder
- [ ] Contrastar T3 punto por punto contra ese texto
- [ ] Si T3 resulta restringido: el negocio se apoya en T1 + T2, que son
      seguros y que **deberían ser el grueso de todos modos**

> **No trates este apartado como asesoría legal.** Es un marco de diseño. La
> lectura del texto oficial es un paso que hay que dar antes de abrir tienda,
> y conviene darlo pronto: rehacer el catálogo después cuesta mucho más.

---

## Next Actions

1. Aprobar o corregir los cuatro niveles y el test
2. `SEC-004` — leer las reglas comerciales vigentes de Mojang
3. `ECO-001` — diseñar sinks y sources; T2 y T3 se validan contra ellos
4. Catálogo inicial de tienda clasificado por nivel

## Related Systems

- [Decisión de arquitectura](../architecture/modpack-decision.md)
- [Análisis de Diosesmon](../analysis/diosesmon-analysis.md)
- Economía · Rangos — pendientes
