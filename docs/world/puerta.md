# La puerta: el lobby como única entrada

## Purpose
Cómo entra un jugador a este servidor: quién decide qué, qué pasa cuando algo
falla, y qué hay que tener puesto para poder encenderla.

## Dependencies
`Puerta.java` · `PuertaService.java` · `PuertaNpc.java` · `VisibilidadJugadores.java` ·
`Traslado.java` · V036 · EasyAuth 3.4.4 · `docs/ui/prompts-lobby.md`

## Related Documents
CLAUDE.md §Puerta · `docs/technical/despliegue.md` · `docs/world/construccion.md`

---

## 1. El agujero que esto tapa

⚠⚠⚠ **HASTA EL 2026-09-09, UN JUGADOR NUEVO NO PODÍA EMPEZAR A JUGAR — Y NO DABA
NINGÚN ERROR.**

Aparecía en el **Mundo Hogar** (`HOGAR` es `World.OVERWORLD`, o sea el spawn de
vainilla) y recibía por el chat un mensaje mandándole al laboratorio de Oak,
**que está en la ciudadela**. Y no había forma de llegar:

| Vía | Por qué no servía |
|---|---|
| `Explorar` | Solo ofrece **hogar** y **salvaje** |
| `Viajes` | Solo funciona **dentro** de la ciudadela |
| `/luna ir ciudadela` | `hasPermissionLevel(2)` — operador |

Los tres caminos a la ciudadela que existían eran **vueltas**: salir de una arena
de gimnasio, salir de la Torre, o ser operador. Todos asumen que ya estabas allí.
Y **sin inicial no arranca ninguna cadena de misiones**, así que el recorrido
entero estaba cerrado.

> El servidor **se comportaba como debe**. Ese es exactamente el tipo de fallo
> que este proyecto lleva media docena de veces documentando: el que no da error.

## 2. Las cinco capas, y cada una con un solo dueño

| Capa | Dueño | Qué decide |
|---|---|---|
| Identidad | **EasyAuth** | ¿Eres tú? Congela al no autenticado y lo devuelve a su sitio al hacer `/login` |
| La puerta | **`Puerta`** | ¿Has cruzado? Si no, estás en el lobby |
| El guardián | **`PuertaNpc`** | Clic derecho **o** izquierdo → comprueba y suelta en la ciudadela |
| Visibilidad | **`VisibilidadJugadores`** + mixin | A cuánta gente ves: lobby 0, ciudadela 30 |
| El candado | **cliente y servidor** | Sin PokePad ni teclas en el lobby |

⚠⚠ **NO SE CONSTRUYE UN SEGUNDO SISTEMA DE LOGIN.** EasyAuth ya hace su mitad.
Montar autenticación propia encima serían **dos sistemas peleándose por dónde
está el jugador** — el fallo de las tres listas de medallas, con la sesión de por
medio.

⚠⚠ **Y NO SE COMPRUEBA «QUE TENGA TODOS LOS MODS».** Eso ya lo hace **Fabric** al
sincronizar los registros, y **echa al cliente descuadrado en la puerta**, antes
de que exista como jugador (el `Registry remapping failed` de siempre). Quien
falla ahí **no llega nunca al lobby**. Lo que sí mira la puerta es lo que Fabric
no: **que tenga nuestro jar y al día**.

## 3. El recorrido

```
entra  ->  EasyAuth lo retiene en el lobby (hide-player-coords)
       ->  /register
       ->  la puerta comprueba que no ha cruzado y lo deja en el lobby
       ->  clic al guardián
       ->  ¿tiene el jar al día?  NO -> mensaje, se queda
                                  SI -> se apunta y va a la ciudadela
       ->  Oak, inicial, y el juego
```

Quien **ya cruzó** no pasa por nada de esto: entra donde lo dejó. Si su sesión de
EasyAuth caducó (15 min), EasyAuth lo retiene, se loguea, y **EasyAuth mismo lo
devuelve a su sitio**.

### 3-bis. Dos retenciones distintas en el mismo sitio

⚠⚠ **Esto confundió al usuario en vivo y por eso está escrito.** El lobby lo usan
**dos mecanismos que no tienen nada que ver**, y se parecen mucho desde dentro:

| | A quién | Qué ve | Quién lo suelta |
|---|---|---|---|
| **Retención de EasyAuth** | Cualquiera **sin autenticar** | Aparece **congelado** en el punto del lobby | `/login <clave>` — y **EasyAuth le devuelve a donde estaba** |
| **La puerta** | Solo quien **nunca ha cruzado** | Se queda en el lobby y **puede andar** | El **guardián** |

Un veterano **no vuelve a ver la puerta jamás**: la migración le marcó como
cruzado. Lo único que verá tras 15 minutos fuera es el lobby unos segundos
mientras teclea la clave.

⚠ **«Pasa el tiempo» no es «se desconecta».** La sesión dura 15 minutos: quien
vuelva antes entra directo, sin login y sin lobby. Es lo que se pidió, pero de
lejos parece que la retención «no funciona».

⚠ Para probarla sin esperar: **`/logout`** mata la sesión al momento.

## 4. Las decisiones que no son obvias

### 4.1 El candado va en `Traslado.ir`, no en los receptores

⚠⚠⚠ Hay **más de treinta** paquetes que acaban moviendo a alguien —explorar,
viajes, gimnasios, torre, nichos—. En cada uno serían treinta sitios que un día
dejan de estar de acuerdo, y **el trigésimo primero, el que alguien añada el mes
que viene, nacería sin él**.

`Traslado` es el **único** camino por el que se mueve a un jugador en este
proyecto (quedó unificado el 30-ago, cuando se juntaron los ocho
teletransportes), así que es el único sitio donde la regla no se elude por
olvido.

**Sin esto el lobby es decoración**: un cliente modificado manda
`AccionExplorar("hogar")` y se planta en el mundo — y el viaje funciona
perfectamente, que es el problema.

### 4.2 La puerta es un invariante, no un evento

⚠⚠⚠ **EasyAuth se la saltaba sin querer, y solo se vio leyendo su config.** Con
`hide-player-coords=true`, EasyAuth **apunta dónde estabas, te retiene, y te
devuelve a tu sitio al hacer `/login`**. Para un veterano es lo que se quiere.
Para un **nuevo** es un desastre mudo: entra al Hogar, la puerta lo manda al
lobby, se registra… y **EasyAuth lo devuelve al Hogar**, que es «donde estaba».

No se arregla pidiéndole a EasyAuth que avise —no expone nada para eso, y
**encadenar nuestra puerta a los eventos de otro mod la rompe el día que ese mod
cambie**—. Se arregla dejando de tratar la puerta como un momento:
`Puerta.vigilar` comprueba **cada segundo** que *quien no ha cruzado está en el
lobby*.

⚠⚠ Y de propina cubre todo lo demás que puede sacar a alguien de ahí sin pasar
por `Traslado`: un operador con `/tp`, otro mod, una cama, un portal. **La lista
de formas de mover a un jugador no se puede enumerar; el estado correcto, sí.**

### 4.3 No se compara la versión del mod, se compara un protocolo

⚠⚠⚠ `mod_version` lleva en `0.1.0` **desde el primer día** — lo que distingue un
jar de otro es la **huella del nombre del fichero**
(`lunaeternal-0.1.0-3598884202.jar`), que el mod no puede leerse a sí mismo.
Comparar «0.1.0 contra 0.1.0» habría dicho **siempre** que todo el mundo está al
día: una comprobación que no comprueba nada, que es peor que no tenerla.

`Puerta.PROTOCOLO` se sube **a mano y solo cuando el cliente tiene que
actualizarse**. Subirlo por costumbre manda al lobby a gente que estaba bien; no
subirlo cuando toca deja entrar a quien verá pantallas que «no abren».

⚠⚠ **Y la AUSENCIA del saludo es la señal**: un jar viejo no sabe mandarlo, así
que no hay que esperar una respuesta que nunca va a llegar.

⚠⚠⚠ **PERO EL SALUDO NO PUEDE SER UN DISPARO AL AIRE, Y LA PRIMERA VERSIÓN LO
ERA.** Salía **una sola vez**, desde `ClientPlayConnectionEvents.JOIN`, en el
instante en que **el cliente** cree estar listo. El usuario reabrió el launcher,
entró con el jar nuevo puesto, tocó al guardián — y le salió *«tu versión está
desfasada»*. Y **nada lo reintentaba**: reabrir el launcher no arregla algo que
no depende del launcher, así que el jugador se queda fuera **para siempre**.

Hoy el saludo es una **respuesta**: el servidor manda `EstadoPuerta` a los dos
segundos de entrar —y cada vez que cambia algo— y quien tenga el mod contesta
siempre. **El momento lo elige quien sabe que ya está listo**, en vez de
adivinarlo. Contestar de más cuesta cuatro bytes un puñado de veces por sesión;
contestar de menos deja a alguien fuera del mundo.

⚠⚠ **Lo que hizo diagnosticable el fallo fue distinguir los dos rechazos.**
`SIN_MOD` y `DESFASADO` parecen el mismo error con distinta redacción, y no lo
son: salió `DESFASADO`, que significa que `canSend` era **cierto** — o sea que el
cliente **sí** tenía el jar nuevo, porque `EstadoPuerta` y `Saludo` se añadieron
en el mismo commit. Con un único mensaje de «no puedes pasar» habría sido
imposible saber por dónde mirar.

⚠ Y por eso **se deja rastro en el log** la primera vez que cada jugador saluda,
con su protocolo y el exigido:

```
Puerta: TheJuanCE saluda con protocolo 1 (se exige 1)
```

Desde el juego, «no llegó el saludo» y «llegó con otro número» **se ven
exactamente igual**.

### 4.4 Nace apagada

⚠⚠⚠ El día que esto llegó al servidor, el lobby era una dimensión **vacía**. Con
la puerta encendida desde el primer arranque, cada jugador nuevo aparecería en un
vacío sin nada que tocar y **`Traslado` le impediría salir** — que es justo lo
que la hace funcionar. Y de esa dimensión **no se sale andando**.

`/luna puerta activar` **comprueba que el guardián está puesto** en vez de
fiarse, y exige estar **dentro del lobby**: un barrido solo ve entidades en
chunks cargados, así que desde fuera un cero significaría «no lo estoy mirando» y
no «no hay guardián» (la lección de los cuatro diagnósticos con `@e`).

### 4.5 La visibilidad: un botón, dos números

⚠⚠⚠ **ESTO NO BAJA EL LAG DEL SERVIDOR.** Quita **ancho de banda y FPS del
cliente** —dibujar 200 muñecos con armadura y cosméticos es trabajo de quien
mira— pero el servidor **sigue tickeando a los 200** y cargando sus chunks. Es la
misma lección ya escrita para las dimensiones. Para 200 de verdad el paso es **un
proxy (Velocity) con el lobby en otra máquina**, que D-009 ya deja posible porque
todo vive en MariaDB.

⚠⚠ «En el lobby no se ve nadie» y «en la ciudadela ves a treinta» **son el mismo
mecanismo con dos números**. Con dos sistemas distintos llega el día en que dicen
cosas distintas sobre el mismo jugador, y el síntoma es alguien invisible para
unos y visible para otros.

⚠⚠ **Compilar no es aplicar, y no se puede comprobar en el autotest**: lo natural
sería `Class.forName` sobre la clase objetivo, pero **en producción las clases de
Minecraft llevan nombres `intermediary` y en desarrollo los de Yarn**, así que
esa comprobación pasaría aquí y fallaría allí. El mixin se marca vivo al correr y
`/luna puerta` lo enseña — **mirado con gente dentro**, que es cuando la
respuesta significa algo.

## 5. Los comandos

```
/luna puerta                       estado: protocolo, tu cliente, activa, visibilidad
/luna puerta npc [especie]         coloca el guardián donde estás (solo en el lobby)
/luna puerta npc quitar            lo quita
/luna puerta activar               la enciende — exige guardián puesto
/luna puerta desactivar            la apaga
```

## 6. Puesta en marcha

**En este orden, y el orden importa:**

1. **Reabrir el launcher.** El saludo del cliente es nuevo: un jar viejo **no
   saluda** y la puerta lo tratará como desfasado — al operador el primero.
2. Construir el lobby alrededor del punto de llegada (ver
   [prompts-lobby.md](../ui/prompts-lobby.md)).
3. `/luna ir lobby` → `/luna puerta npc` → `/luna puerta activar`.

**El punto de llegada, medido en el juego (2026-09-09):**

```
43.998 / 72 / 47.97     mirando al OESTE (yaw 90)
```

⚠⚠ **En Minecraft el cero del yaw es el SUR, no el norte**: sur 0 · oeste 90 ·
norte 180 · este −90. Escribirlo de memoria no da error — da a alguien
apareciendo de espaldas a lo único que tiene que ver.

⚠ **Al lobby se llega mirando a un sitio fijo, y al resto de mundos
conservando hacia dónde mirabas.** Allí hay una sola cosa que hacer, y aparecer
de espaldas al guardián es la primera fricción para quien acaba de entrar. En
el Hogar o el Salvaje sería al revés: girar a alguien por sorpresa es lo raro.

⚠ **El lobby solo admite y = 0 .. 255** (`min_y: 0`, `height: 256`), al contrario
que la ciudadela y el Hogar, que llegan a −64.

⚠⚠ **Cerrarlo o poner `barrier`.** Es vacío: quien se caiga muere y reaparece en
el overworld. `Puerta.vigilar` lo devuelve en menos de un segundo, así que no se
rompe nada — pero es feo y desconcierta.

## 7. La config de EasyAuth

```
session-timeout    86400 -> 900      15 min (decisión del usuario)
hide-player-coords false -> true     sin esto world-spawn no se usa PARA NADA
world-spawn        overworld -> lunaeternal:lobby  43.998 / 72 / 47.97  yaw 90
```

⚠⚠ **PARAR, SUBIR, ARRANCAR.** EasyAuth carga su config al arrancar y **la
reescribe al apagarse**: subirla en caliente y reiniciar hace que la parada
vuelque su copia vieja encima. **No da ningún error — gana el que escribe el
último.** Ya mordió dos veces con ClaimBlocks. Se verifica **después** del
arranque, que es la única comprobación que vale.

⚠⚠⚠ **LAS COORDENADAS DEL LOBBY ESTÁN EN DOS SITIOS** —`TravelService.SPAWN_LOBBY`
y la config de EasyAuth— y **nada las obliga a coincidir**. Si divergieran,
EasyAuth soltaría al jugador en un punto del lobby y la puerta en otro: no daría
error, daría **gente apareciendo fuera de la construcción**, y en una dimensión
de vacío eso es caerse. Al mover el lobby hay que tocar **los dos**.

✅ **Y desde el 2026-09-09 lo vigila el autotest**, así que dejó de ser un aviso
que hay que recordar: `PUERTA: EASYAUTH Y LA PUERTA LLEGAN AL MISMO SITIO`.
**Se puede comprobar porque la config de otro mod es un fichero en nuestro
disco** — no hace falta su API ni compilar contra él. Con holgura de dos
bloques: lo que se caza es «alguien movió el lobby y actualizó uno de los dos»,
que son decenas de bloques; exigir igualdad exacta se pondría rojo por cómo cada
uno escribe un decimal, y un rojo que no significa nada enseña a ignorar los
rojos.

⚠ `vanish-until-auth=true` pide el mod **Vanish**, que no está instalado, así que
hoy no hace nada — **y no hace falta**: el recorte de visibilidad ya deja el
lobby a cero jugadores visibles.

## 8. Lo que se dejó fuera a conciencia

⚠⚠ **No hay guarda por acción en los ~35 receptores que gastan.** Están
bloqueados **el viaje** (`Traslado`) y **la ficha del PokePad** (`PedirSaldo`, y
sin ella no se puebla ninguna pantalla). Lo que queda expuesto es un cliente
modificado reclamando su **kit diario o su sobre gratis** desde el lobby — de
alguien que **no puede salir de ahí** y que va a cruzar de todos modos. Cerrarlo
del todo es un trabajo deliberado, no un añadido al final.

## Current Status

✅ **En vivo desde 2026-09-09.** `Done (30,397 s)` · V036 aplicada · **autotest
695/695** · EasyAuth 3.4.4 con la config nueva verificada tras el arranque.

✅ **El lobby está construido y el guardián colocado** (2026-09-09). Punto de
llegada `43.998 / 72 / 47.97` mirando al oeste, en los dos sitios y cruzado por
el autotest.

✅ **El saludo llega**, verificado en el log en vivo:
`Puerta: TheJuanCE saluda con protocolo 1 (se exige 1)`.

⚠ **La puerta está APAGADA.** Se apagó para desplegar el arreglo del saludo —con
el fallo vivo, un jugador nuevo entraba al lobby y **no podía cruzar**— y **no se
ha vuelto a encender**. Se enciende con `/luna puerta activar`, estando dentro
del lobby.

⚠⚠ **El recorrido completo de un jugador nuevo NO se ha probado todavía**, y no
se puede probar con la cuenta del operador: está marcada como cruzada por el
relleno de la V036. Hace falta **una segunda cuenta**, o el comando del punto 2
de aquí abajo.

## Last Decision
D-050 — el lobby es la única entrada, y la visibilidad se recorta por dimensión.

## Next Actions

1. **Rematar el lobby y `/luna puerta activar`** (usuario). ⚠ Reabrir el launcher
   antes: el jar cambió con el arreglo del saludo.
2. **Escribir `/luna puerta reiniciar <jugador>`** — no existe, y hoy **la única
   forma de probar el recorrido de un jugador nuevo es entrar con otra cuenta**.
   Borra su fila de `player_puerta` y su entrada de la caché, y en el siguiente
   segundo `Puerta.vigilar` lo devuelve al lobby. Es el hermano de
   `/luna inicial reiniciar`, que hace lo mismo para el inicial.
   ⚠ Y hay que **borrar también la caché**, no solo la fila: la fila la lee el
   evento de conexión, así que sin tocar la memoria el jugador seguiría contando
   como cruzado hasta que se desconecte — el fallo de `/luna reiniciarinicial`,
   que «no servía» porque borraba la fila y el cliente seguía con su copia.
3. **Comprobar el recorte de visibilidad con dos cuentas**
   (`/luna puerta` → «el recorte ha corrido: sí»). Con el servidor vacío ese
   «todavía no» no significa nada.
4. Pendiente y aparte: la guarda por acción del §8, y el proxy si se llega a 200.
