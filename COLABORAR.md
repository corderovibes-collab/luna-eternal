# Trabajar en este proyecto

Guía para quien se incorpora. Está pensada para leerse una vez y volver a ella
cuando algo falle.

---

## 1. Lo primero: el prompt de arranque

Pega esto en Claude Code al abrir el proyecto, **cada sesión**:

```
Lee CLAUDE.md entero antes de tocar nada. Es el documento maestro y manda
sobre cualquier suposición.

Reglas de este proyecto:
- Antes de empezar: git pull.
- Todo sistema nuevo añade sus invariantes a /luna autotest ANTES de
  desplegarse (MOD-006). No se despliega con el autotest en rojo.
- Nunca inventes datos de juego ni números de balance: si no está decidido
  en docs/, pregunta.
- No renombres clases, columnas, ids de pantalla ni canales de red sin
  decírmelo: hay cosas atadas por nombre entre Java, SQL y Python.
- Los comentarios explican POR QUÉ, no qué hace la línea.

Dime qué vas a hacer antes de hacerlo si toca base de datos, protocolo de
red o despliegue.
```

## 2. Montarlo en tu máquina

```
git clone https://github.com/corderovibes-collab/luna-eternal.git
cd luna-eternal
```

| Necesitas | Por qué |
|---|---|
| **JDK 21** | Minecraft 1.21.1 no arranca con menos |
| Python 3.10+ con **Pillow** | Las herramientas de `tools/` |
| Git | — |

Compilar el mod:

```
cd mod && bash build.sh
```

> **Usa siempre `build.sh`, nunca `./gradlew` directo.** Hay un
> `~/.gradle/gradle.properties` global que fuerza JDK 17 —lo puso otro
> proyecto de la máquina original— y el script lo sobrescribe por línea de
> comandos. **No toques ese fichero global**: rompe el otro proyecto.

## 3. Lo que NO se toca sin avisar

Son cosas atadas por **nombre** entre ficheros distintos. Renombrar una y no
la otra no da error de compilación: falla en ejecución, o peor, en silencio.

| Cosa | Atada a |
|---|---|
| `PadPayloads.VERSION` | Súbela al cambiar cualquier record de ese fichero. Va en el nombre del canal, y así un cliente viejo cae al menú de cofre en vez de desconectarse |
| Nombres de pantalla (`"cazas"`, `"cartera"`…) | El cliente busca `pokepad_<nombre>.png`. Cambiar uno deja la pantalla con el fondo genérico |
| Nombres de icono | `tools/gen_iconos.py` ↔ `arte-origen/icono/` ↔ las celdas en Java |
| La lista de `Database.MIGRATIONS` | **Añade ahí cada migración nueva.** Olvidarlo hace que el servidor arranque sin la tabla |
| `player_id` en SQL | Siempre `BIGINT UNSIGNED`. Con `BIGINT` a secas la clave ajena no se forma (errno 150) |

## 4. Trampas que ya nos costaron horas

Están documentadas donde toca, pero conviene conocerlas de antemano.

**Toda migración se registra a sí misma.** El motor solo mira la tabla
`schema_version`; si tu `.sql` no acaba con su `INSERT`, se reaplica en cada
arranque. Con un `DROP TABLE` dentro, eso borra datos. El motor ahora lo
comprueba y falla, pero mejor no llegar ahí.

**Nunca reemplaces el jar del cliente con Minecraft abierto.** Fabric carga
las clases cuando las necesita, así que el juego sigue tan tranquilo hasta que
toca cargar una nueva — y revienta con un error de `netty` que no se parece en
nada a la causa. Usa `python tools/instalar_cliente.py`, que se niega a copiar
si el juego está abierto.

**Cobblemon: compila contra el JAR de 1.7.3, no contra el fuente clonado.** El
`main` del repositorio va por 1.8.0 y sus firmas difieren. Usar el fuente
equivocado da `NoSuchMethodError` en ejecución.

**Dibujar Pokémon en 3D exige tres cosas a la vez** — rotación nueva por
llamada, dibujar en pasadas y limpiar la profundidad. Ver
[`docs/ui/visual-identity.md`](docs/ui/visual-identity.md).

## 5. Antes de dar algo por bueno

```
cd mod && bash build.sh                    # compila
python tools/auditar_textos.py             # ningún texto se corta
```

Y en el servidor, `/luna autotest`. **125/125 o no se despliega.**

## 6. Credenciales

**No están en el repositorio, y es correcto.** Para trabajar en el código no
hacen falta: se compila y se prueba en local.

Si necesitas desplegar, pídeselas al dueño por privado y ponlas en `.env`
(está git-ignorado). Plantilla en `.env.example`.

## 7. Por dónde va el proyecto

`CLAUDE.md` §0 tiene el estado actual y qué toca después. `docs/roadmap/backlog.md`
tiene las tareas con su id.

Resumen: el mod funciona contra MariaDB, la interfaz propia (**el PokePad**)
está montada con Pokémon en 3D, y lo que falta sobre todo es **construir la
ciudadela** — que no es programación.
