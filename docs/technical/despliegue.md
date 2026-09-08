# Desplegar: el procedimiento entero

## Purpose

**Cómo se pone algo en el servidor sin dejar a nadie fuera.** Escrito para que
lo siga otra persona —o otra IA— sin haber vivido los fallos que lo formaron.
Cada regla de aquí tiene una fecha y una avería detrás.

## Dependencies

`CLAUDE.md` · [distribucion.md](distribucion.md) · [client-pack.md](client-pack.md)

---

## 0. Lo primero: HAY DOS DESTINOS Y SE OLVIDA EL SEGUNDO

```
  servidor   python tools/desplegar.py mod --reiniciar
  clientes   python tools/gen_manifest.py --publicar
```

**El jar que baja el launcher NO sale del servidor: sale del manifiesto.** Subir
solo al servidor deja a todo el mundo con el jar viejo, y el síntoma es una
pantalla que «no abre» — se comporta como debe, y eso despista.

---

## 1. La regla que decide el ORDEN

> ⚠⚠⚠ **UN REGISTRO QUE SE SINCRONIZA NO DEGRADA: ECHA.**
> Bloques, objetos, entidades, tipos de contenedor y dimensiones **viajan como
> un número**, no como un nombre. El servidor dice «bloque 4721» y el cliente lo
> busca en SU tabla. Si las dos tablas no coinciden, la conexión se cae **en la
> puerta**, con un error que **no nombra al mod que falta**.

De ahí sale toda la aritmética:

| | |
|---|---|
| **El servidor tiene que ser SUBCONJUNTO del cliente** | un cliente con mods de más no molesta; un servidor con entradas que el cliente no conoce **echa a todo el mundo** |
| **AÑADIR un mod** | **manifiesto primero**, servidor después |
| **QUITAR un mod** | **servidor primero**, manifiesto después |

Las dos son la misma regla mirada desde cada lado: en todo momento el servidor
debe tener **lo mismo o menos** que el cliente.

> ⚠ Solo aplica a mods que **registran algo que se sincroniza**. Uno que solo
> añade comandos (EasyAuth, WorldEdit) puede estar solo en el servidor.
> Comprobación práctica: si tiene `blockstates/` y su `environment` no es
> `client`, tiene que estar en los dos lados.

---

## 2. Añadir o cambiar NUESTRO mod (el caso normal)

```bash
cd mod && ./build.sh
```
```bash
python tools/gen_manifest.py --publicar
```
```bash
python tools/desplegar.py mod --reiniciar
```

Y **después**, siempre:

```bash
python tools/rcon.py "luna autotest"
```

Tiene que decir **«N comprobaciones correctas»**. Si dice «NO desplegar», el
invariante que falla suele estar **diciendo la verdad**: mira si lo que cambió
fue una decisión, no el código. Ha pasado tres veces y las tres tenía razón.

> ⚠ **Avisa a la gente ANTES de reiniciar, no después.** Un mod nuevo no se
> carga en caliente, y con un registro nuevo nadie entra hasta reabrir el
> launcher.

---

## 3. Añadir un mod AJENO

1. Mirar **qué registra**, abriendo el jar. No suponerlo:
   ```bash
   unzip -l MOD.jar | grep -E "blockstates|models/item"
   unzip -p MOD.jar fabric.mod.json
   ```
   `"environment": "server"` y **cero blockstates** = no puede echar a nadie:
   va solo al servidor y no toca el manifiesto.
2. Mirar **sus dependencias** (`depends` del `fabric.mod.json`), incluidas las
   que viajan **dentro** (JiJ). Una que falte **solo se ve al reiniciar**, con
   `Incompatible mods found!`.
3. Si va a los dos lados: **manifiesto primero**, servidor después.

---

## 4. Quitar un mod

**Servidor primero.** Y el manifiesto se niega a publicar una baja:

```bash
python tools/gen_manifest.py --publicar --permitir-bajas
```

> ⚠⚠ Esa guarda existe por un fallo real: una republicación rutinaria se dejó
> fuera `cobblemon-cards`, que el servidor sí tenía, y **nadie pudo entrar**.
> El generador imprime lo que PONE; lo que se deja fuera no aparece por ningún
> sitio. `comprobar_bajas()` compara contra lo publicado.

---

## 5. ⚠⚠⚠ LA CONFIGURACIÓN DE UN MOD AJENO: PARAR, SUBIR, ARRANCAR

**Muchos mods cargan su config al arrancar, la guardan en memoria y LA VUELVEN A
ESCRIBIR AL APAGARSE.** Subirla con el servidor arriba y reiniciar hace esto:

```
  subes el fichero        ->  bien
  el servidor se apaga    ->  vuelca su copia VIEJA encima
  el servidor arranca     ->  lee lo que él mismo acaba de escribir
```

**No da ningún error.** Gana el que escribe el último. Nos mordió dos veces con
ClaimBlocks (`claims.json` y `texts.json`): los textos volvían al inglés reinicio
tras reinicio sin una línea en el log.

**El procedimiento correcto:**

1. `stop` y **esperar a `offline` de verdad**
2. subir el fichero
3. `start`

Y una vez hecho **se sostiene solo**: a partir de ahí su copia en memoria ya es
la nuestra. Lo mismo vale para **borrar datos** de un mod (una parcela, una fila).

> ⚠ Es la misma familia que **congelar el mundo antes de copiarlo** (`save-off`
> + `save-all flush` en `backup.py`): nunca se toca un fichero que un proceso
> vivo tiene abierto.

---

## 6. ⚠⚠⚠ DOS RAMAS, UN SERVIDOR

**Si dos ramas compilan el MISMO jar, la segunda que despliegue borra a la
primera.** Pasó tres veces en una semana:

| Síntoma | Causa |
|---|---|
| La migración `V028` se saltó sola | dos ramas eligieron el número 28 |
| `cobblemon-cards` desapareció del manifiesto | la rama que publicó no lo conocía |
| El icono de CARTAS desapareció del PokePad | el jar publicado no llevaba ese código |

**Ninguno da un error.** El de las cartas fue una pantalla que dejó de existir.

> **La regla:** antes de desplegar, `git status` en **todos** los worktrees
> (`git worktree list`). Commitear al terminar no es orden: **es lo único que
> hace que el trabajo exista.**

---

## 7. Migraciones de base de datos

- Se añaden **a mano** a la lista de `Database.java`. Olvidarlo = el servidor
  arranca sin la tabla.
- `player_id` es **`BIGINT UNSIGNED`**; con `BIGINT` a secas la clave ajena no
  se forma (errno 150).
- **El número puede estar cogido por otra rama.** El runner compara también la
  **descripción** y **se niega a arrancar** diciendo que renumeres. Antes se lo
  saltaba en silencio y el fallo aparecía días después como
  `Table ... doesn't exist`.
- Si una migración falla a medias el servidor entra en bucle y el panel se queda
  en `stopping`: se sale con `kill` y luego `start`.

---

## 8. Verificar que de verdad está puesto

```bash
python tools/rcon.py "luna autotest"
```

Y comprobar que **lo que sirve el manifiesto es lo que compilaste**: el
`sha1` del jar publicado tiene que coincidir con el local. Publicar no es
haber publicado — el CDN cachea ~3 min.

Volver atrás son **250 bytes**:

```bash
python tools/gen_manifest.py --volver-a <huella>
```

---

## 9. Trampas sueltas que cuestan horas

| | |
|---|---|
| **Sobrescribir un jar lo corrompe** | no da error al subir; revienta al cargar una clase con `ZipFile invalid LOC header`. **Borrar antes de subir** (`ptero.subir` ya lo hace) |
| **La JVM se cuelga al apagar** | el log dice «All dimensions are saved» y el proceso no sale. `kill` + `start` |
| **`@e` no ve lo que está en un chunk descargado** | un censo miente. `forceload add` antes, `forceload remove all` después |
| **`execute in <dim> ... @e` NO acota el selector** | la sonda que dice la verdad es **por bloque** |
| **Un generador borra solo lo que sabe generar** | y **dice** lo que conserva. Un generador se llevó tres PNG ajenos y dejó **seis pantallas en magenta** |
| **Un generador no puede depender de un fichero que no está en git** | si no, no se puede volver a ejecutar |
| **Una clave de traducción que no existe se ve cruda** | `python tools/comprobar_textos.py` |

---

## 10. El resumen, para pegar

```bash
# 1. compilar
cd mod && ./build.sh

# 2. clientes (SIEMPRE antes de reiniciar, si se AÑADE algo)
python tools/gen_manifest.py --publicar

# 3. servidor
python tools/desplegar.py mod --reiniciar

# 4. comprobar
python tools/rcon.py "luna autotest"
```

**Quitando un mod, el 2 y el 3 se invierten** y el 2 lleva `--permitir-bajas`.

**Tocando la config de un mod ajeno**: parar, subir, arrancar. Nunca en caliente.
