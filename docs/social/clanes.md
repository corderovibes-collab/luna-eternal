# Clanes

## Purpose

Cómo funcionan los clanes: fundar uno, entrar en el de otro, quién manda y qué
pasa con el dinero que se guarda en común. Es el primer sistema del proyecto en
el que **el estado no es de quien lo mira**, y eso cambia casi todas las reglas.

## Dependencies

- [CLAUDE.md](../../CLAUDE.md) — P6, P9, P9-bis, D-010, D-034
- [data-model.md](../technical/data-model.md) — R2, R3, R4
- [dibujado.md](../ui/dibujado.md) — las 6 reglas de dibujar una pantalla
- [economy/](../economy/) — el tesoro es dinero y pasa por el mismo sitio

## Related Documents

- [interfaces-catalog.md](../ui/interfaces-catalog.md)
- [misiones.md](../ui/misiones.md) — la pantalla anterior, mismo chasis

## Current Status

**Implementado y compilando. Migración `V013` aplicada, autotest ampliado.**

```
mod/src/main/java/net/pokereport/luna/clan/ClanService.java   el sistema
mod/src/main/resources/db/migration/V013__clanes.sql          3 tablas
mod/src/client/.../pokepad/ClanScreen.java                    la pantalla
mod/src/main/java/net/pokereport/luna/net/Red.java            6 paquetes
mod/src/main/java/net/pokereport/luna/ui/Tablist.java         la etiqueta
```

## Last Decision

**D-040 — Se escribe un sistema propio, no se adopta un mod de clanes.**

## Next Actions

- Verificar en el juego con dos cuentas (fundar, invitar, aceptar, echar).
- Decidir si el clan desbloquea algo más allá de la identidad y el tesoro
  (§7): hoy no da ventaja de ningún tipo, y eso es a propósito.

---

## 1. Por qué un sistema propio

**Petición del usuario, literal:** *«si hay algún mod de clan sería excelente
que tenga la opción de unirse a un clan existente, enviar invitación o crear un
clan desde cero, así tipo juegos MMORPG como Albion»*.

Se miró. Lo que hay para Fabric 1.21.1 son mods de **facciones con terreno**
(reclamar chunks, guerra, PvP por territorio) o de **equipos de chat**. Ninguno
de los dos es esto:

| | Lo que hacen | Lo que hace falta |
|---|---|---|
| Facciones | Reclamar chunks, PvP, guerra | La ciudadela es una isla que construimos nosotros; no hay terreno que repartir |
| Equipos de chat | Un canal privado | Eso es *una parte*, no el sistema |

Y sobre todo: **el clan tiene que estar conectado con lo demás** —la ficha del
PokePad, la etiqueta junto al nombre, el tesoro en Plata— y eso ningún mod
ajeno lo puede saber. P5 pone «mod maduro» antes que «sistema propio», pero solo
cuando el mod maduro *resuelve el problema*. Aquí no lo resuelve ninguno.

> ⚠ **El tesoro es lo que decide la cuestión.** Un mod de clanes ajeno guardaría
> el dinero en su propio almacén, y entonces habría **dos economías**: la nuestra
> —con libro de asientos, idempotencia y auditoría (R3, R4)— y la suya. La regla
> del proyecto es que todo lo económico pasa por `applyInTransaction`, y un mod
> externo no puede pasar por ahí.

---

## 2. El modelo

Tres tablas y ni una más.

```
clan          quién es el clan: nombre, etiqueta, color, líder, tesoro
clan_member   quién está dentro y con qué rol
clan_invite   a quién han invitado, y cuándo caduca
```

### 2.1 Un jugador, un clan

**No hay una columna que lo diga: lo dice la clave primaria.**

```sql
CREATE TABLE clan_member (
    player_id  BIGINT UNSIGNED NOT NULL PRIMARY KEY,   <- AQUI
    clan_id    BIGINT UNSIGNED NOT NULL,
    ...
```

> ⚠⚠ **Esto no es un detalle de rendimiento, es la regla del juego escrita en el
> sitio donde no se puede saltar.** Con `player_id` como clave primaria, meter a
> alguien en un segundo clan **falla en la base**, venga la petición de donde
> venga: de la pantalla, de un comando, de dos peticiones simultáneas o de un
> cliente modificado. Comprobarlo en Java también se hace —para dar un mensaje
> que se entienda— pero **la que manda es esta**.
>
> Sin ella, dos clicks rápidos en dos invitaciones distintas meten al jugador en
> dos clanes a la vez, y a partir de ahí `clanDe()` devuelve uno de los dos *al
> azar*.

### 2.2 El nombre y la etiqueta, en minúsculas

```sql
name_lower  VARCHAR(24) NOT NULL UNIQUE,
tag_lower   VARCHAR(5)  NOT NULL UNIQUE,
```

> ⚠ **La columna que lleva el índice único NO es la que se enseña.** Se guardan
> las dos: `name` con las mayúsculas que eligió el fundador, y `name_lower` para
> el índice. Sin la segunda, «Luna» y «LUNA» son dos clanes que **en el chat se
> ven exactamente igual** — que es como se suplanta a un clan.

### 2.3 El tesoro no puede ser negativo

```sql
treasury BIGINT NOT NULL DEFAULT 0 CHECK (treasury >= 0),
```

`BIGINT`, nunca `DECIMAL` ni `DOUBLE` (R2). Y el `CHECK` es **la última red**:
el código comprueba el saldo antes de sacar, pero si algún día un camino nuevo
se olvidara, la base lo rechaza en vez de dejar un tesoro en −4.000.

### 2.4 `ON DELETE` en dos direcciones distintas

```
clan_member.clan_id   -> clan         ON DELETE CASCADE
clan_member.player_id -> player       ON DELETE RESTRICT
```

**Y es deliberado que no sean iguales.** Borrar un clan tiene que llevarse a sus
miembros y sus invitaciones: el clan ya no existe. Borrar un *jugador* no puede
llevarse nada por delante en silencio — si alguien intenta borrar a un jugador
que aún dirige un clan, tiene que **fallar** y que alguien lo mire.

> ⚠ Esto muerde en el autotest: la limpieza tiene que borrar las filas de clan
> **antes** que la del jugador, o el `RESTRICT` la aborta y la ejecución
> siguiente falla con «ese nombre ya está cogido».

---

## 3. Los roles

Tres, y las reglas caben en una tabla:

| | LÍDER | OFICIAL | MIEMBRO |
|---|---|---|---|
| Invitar | ✅ | ✅ | ❌ |
| Echar | ✅ | ✅ (a miembros) | ❌ |
| Ascender / degradar | ✅ | ❌ | ❌ |
| Sacar del tesoro | ✅ | ✅ | ❌ |
| Aportar al tesoro | ✅ | ✅ | ✅ |
| Traspasar el liderazgo | ✅ | ❌ | ❌ |
| Disolver | ✅ | ❌ | ❌ |
| Salir | solo si está solo | ✅ | ✅ |

Tres cosas que no son obvias y que el autotest fija:

> ⚠ **El líder NO puede salir mientras quede alguien.** Si pudiera, el clan se
> quedaría sin nadie capaz de invitar, echar ni disolverlo: **vivo y sin
> gobierno para siempre**. Tiene que traspasar primero, o disolver.

> ⚠ **Nadie puede *ascender* a otro a LÍDER.** Eso es `traspasar`, que además
> **baja al anterior** en la misma transacción. Si fuera un cambio de rol más,
> habría dos líderes — y entonces «el líder no puede salir» dejaría de proteger
> nada.

> ⚠ **Un oficial no puede echar a otro oficial ni al líder.** Si pudiera, dos
> oficiales podrían echarse mutuamente en una carrera, y el que llegara primero
> se quedaría el clan.

**Y todo esto vive en `ClanService`, en ningún otro sitio.** La pantalla esconde
los botones que no te tocan, pero eso es **cortesía, no permiso**: el servidor
rechaza igual la acción si llega de un cliente modificado (P6).

---

## 4. El tesoro

Es dinero de verdad, en **Plata** (`POKEDOLLAR`, D-034), y pasa por donde pasa
todo el dinero:

```java
LunaEternal.economy().applyInTransaction(
        c, playerId, Currency.POKEDOLLAR, -cantidad,
        "clan_aportar", "clan", clanId, clave);
```

La misma `Connection` que actualiza `clan.treasury` (R3), con clave de
idempotencia (R4). Así, un doble clic o un paquete repetido **no cobra dos
veces**, y en el libro de asientos queda quién movió qué y a qué clan.

> ⚠ **Suma cero.** Lo que sale del bolsillo entra en el tesoro y al revés. El
> autotest lo comprueba explícitamente (`aportado == sacado + tesoro`) porque es
> el único invariante que detecta que se ha creado o destruido dinero — y crear
> dinero es lo peor que le puede pasar a esta economía (P3).

**El tesoro no es un sink.** El dinero sigue en el juego, solo ha cambiado de
bolsillo. Lo que **sí** es un sink son los 5.000 de fundar: eso desaparece.

---

## 5. Dónde se ve el clan

El usuario lo pidió así: *«cuando se escanee al jugador salga también si tiene
clan y en qué clan está»*. Sale en **cuatro sitios**, y solo uno es una pantalla
nuestra:

| Dónde | Cómo |
|---|---|
| Encima de la cabeza | prefijo del equipo de marcador |
| En el tablist | el mismo prefijo |
| En el chat | el mismo prefijo |
| En la ficha del PokePad | el campo `clan` del paquete `Ficha` |

> ⚠⚠ **Un jugador solo puede estar en UN equipo de marcador, y el rango ya usaba
> uno.** Por eso el equipo pasa a ser **por jugador** (`luna<hash>`) y su prefijo
> lleva **rango + clan** juntos. La alternativa —un equipo por cada combinación
> de rango y clan— multiplica los equipos por los clanes que haya, y hay que
> crearlos y borrarlos a mano cada vez que se funda o se disuelve uno.
>
> Un equipo por jugador suena a mucho y no lo es: son diez jugadores como máximo
> en este servidor, y un equipo de marcador es una fila en memoria.

> ⚠ **Se hace con un equipo de marcador y no con un paquete nuestro** porque el
> prefijo lo pinta **vanilla** en los tres sitios. Un paquete propio solo lo
> verían los que tuvieran el mod, y la etiqueta de clan tiene que verla todo el
> mundo o no sirve de nada.

> ⚠ **La etiqueta se refresca cuando el clan cambia, no solo al entrar.** Sin lo
> segundo, quien funde o abandone un clan seguiría con la etiqueta anterior hasta
> reconectar. Es exactamente el fallo que este proyecto pagó cuatro veces el
> 2026-08-23 (§6).

### D-038, cumplida

`Ficha` llevaba el campo `clan` viajando con la cadena vacía desde el
2026-08-17, y aquella decisión escribió: *«tenerlos ya en el protocolo hace que
encenderlos sea rellenar tres líneas en vez de tocar paquete, códec, caché y
dibujado»*. **Fue exacto: han sido esas tres líneas.**

---

## 6. La pantalla

Chasis de Cosméticos, y la geometría es **copia literal** de
`CosmeticosScreen.recalcular()`.

> ⚠ **Escribirla de cero es lo que sacó la pantalla de Trabajos al cuádruple**,
> por olvidar dividir entre el GUI Scale. Una pantalla nueva del Pad empieza
> copiando una que funciona.

### Dos caras

Lo que ve alguien **sin clan** y alguien **con clan** no se parece en nada:

```
SIN CLAN                          CON CLAN
izquierda  formulario de fundar   identidad, tesoro, tu rol
pestañas   INVITACIONES · CLANES  MIEMBROS · TESORO
```

**Las pestañas salen de los datos, no de una lista escrita a mano**, igual que
en Cosméticos y Misiones. Así, al fundar o al salir, la pantalla pasa de una cara
a la otra sin que el jugador la reabra.

> ⚠ **`clan() == null` y `clan().mio() == null` son cosas distintas**, y
> confundirlas enseñaría el formulario de fundar durante el medio segundo que
> tarda la respuesta, **aunque el jugador lleve meses en un clan**. Lo primero es
> «todavía no lo sé»; lo segundo es «no tienes clan».

### Un solo paquete para doce acciones

`AccionClan(accion, texto, texto2, objetivo, cantidad)`.

Podrían ser doce paquetes y sería peor: doce registros, doce receptores y **doce
sitios donde olvidarse de comprobar los permisos**. Con uno, la comprobación vive
en un solo sitio, que es donde tiene que estar en un sistema social.

Lo que **no** se hace es mandar un mapa o un JSON: entonces el formato dejaría de
estar declarado, y el servidor tendría que fiarse de las claves que le manden.

### Se reenvía a todo el clan

> ⚠⚠ **Esto es lo que separa un sistema social de una pantalla personal.** El
> estado **no es de quien lo mira**. Si alguien echa a un miembro y solo se
> refresca a sí mismo, los demás siguen viendo al echado en la lista —y el
> echado sigue creyendo que está dentro— hasta que reabran la pantalla.
>
> Es la lección del 2026-08-23, otra vez: **si el servidor cambia un estado que
> el cliente dibuja, el servidor lo reenvía.**

### Los campos de texto

Primera pantalla del proyecto con `TextFieldWidget`. Dos cosas:

- **Se crean en `init()`, no en el constructor.** `init` vuelve a correr al
  cambiar el tamaño de la ventana, y un campo colocado con las medidas viejas se
  queda donde ya no está su hueco.
- **Se crean los cuatro siempre**, aunque solo se usen dos a la vez. Crearlos
  según el caso obligaría a recrearlos cada vez que llega un paquete, y el
  jugador perdería lo que estuviera escribiendo a mitad de palabra.

> ⚠ Y `keyPressed`/`charTyped` se los pasan **antes** que la pantalla: sin eso,
> escribir «e» en el nombre del clan abriría el inventario.

### No hay botón de «pedir entrar»

La pestaña CLANES lista los clanes que hay, y **no** ofrece unirse: hoy solo se
entra **por invitación**. La lista está para saber a quién buscar, y decirlo con
una línea de texto es mejor que poner un botón que no hace nada.

---

## 7. Lo que un clan NO da

**Ninguna ventaja.** Ni XP extra, ni descuentos, ni acceso a nada. Da:

- **Identidad** — la etiqueta junto al nombre
- **Un sitio donde juntar dinero** — el tesoro

Es a propósito, y por dos motivos. El primero es de diseño: un clan que da
ventaja convierte «tener amigos» en una estadística, y quien juegue solo queda
por detrás sin poder hacer nada al respecto. El segundo es económico (P3): **una
ventaja de clan es una fuente**, y este proyecto tiene el problema contrario.

> Si algún día se le añade algo, la pregunta de P2 que hay que responder primero
> es la octava: **cómo se abusa**. Un bono de clan con 30 plazas y sin coste de
> entrada se abusa fundando un clan, metiendo a todo el mundo y no volviendo a
> hablarse nunca.

---

## 8. Números

| | |
|---|---|
| Fundar | **5.000 de Plata** |
| Miembros | **30** máximo |
| Nombre | 3 a 24 caracteres, letras, números y espacios |
| Etiqueta | 2 a 5 caracteres, letras y números |
| Invitación | caduca a los **7 días** |

> ⚠ **Los 5.000 están calibrados contra la tienda**, donde lo más caro son 3.000
> de Plata: fundar cuesta *un poco más que el objeto más caro*. Es la única
> referencia que existe hoy — hasta que alguien juegue de verdad, el ingreso
> diario es una estimación (ver CLAUDE.md, «calibrar con datos reales»).

> ⚠ **El nombre y la etiqueta solo admiten letras, números y espacios**, y el que
> importa es el `§`: con un código de color dentro, la etiqueta **pintaría el
> resto de la línea del chat de todo el mundo**.

---

## 9. Autotest

**51 comprobaciones nuevas**, y la mayoría comprueban **lo que no se puede
hacer**.

> ⚠⚠ **Una regla de permiso no falla ruidosamente.** Falla dejando que alguien
> haga algo que no debía: no hay excepción, no hay traza en el log, y el síntoma
> llega semanas después en forma de jugador enfadado porque le han vaciado el
> tesoro. Un sistema social **es** sus reglas de permiso, así que probar solo el
> camino feliz no prueba nada.

Lo que fija:

```
el nombre     corto, etiqueta larga, codigo de color
fundar        sin dinero no, con dinero si, cobra EXACTAMENTE el coste
uno solo      no se funda estando en uno; nombre y etiqueta unicos
              -- y unicos IGNORANDO MAYUSCULAS
invitar       un desconocido no; el invitado recibe; aceptar consume
lo prohibido  un raso no invita, no echa, no asciende, no disuelve
              un oficial no echa al lider ni disuelve
              el lider no se echa a si mismo ni sale acompañado
              nadie asciende a otro a LIDER
el tesoro     aportar sale del bolsillo Y entra en el tesoro
              un raso no saca; el rechazo no lo toca
              no se saca mas de lo que hay; nunca queda negativo
              SUMA CERO: aportado == sacado + tesoro
disolver      borra el clan y lo saca del listado
```

---

## 10. Comandos

**Ninguno.** P9: todo se hace con clics. Los clanes nacen ya con su pantalla, así
que no hay que abrir la excepción de «mientras tanto, por comando».
