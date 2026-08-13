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
| Python 3.10+ | Las herramientas de `tools/` |
| **Node 20+** | Solo si tocas el launcher (`launcher/`) |
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
| La lista de `Database.MIGRATIONS` | **Añade ahí cada migración nueva.** Olvidarlo hace que el servidor arranque sin la tabla |
| `player_id` en SQL | Siempre `BIGINT UNSIGNED`. Con `BIGINT` a secas la clave ajena no se forma (errno 150) |
| `StarterService.CLAVE` (`__inicial`) | Es la marca de «ya elegiste». Cambiarla **regala un segundo inicial a todo el mundo** |
| Las claves de los kits | `kits.json` ↔ `kit_claim` en la base. Renombrar una re-habilita el kit |
| Rutas del `manifest.json` | `tools/gen_manifest.py` ↔ lo que el launcher tiene instalado. Cambiar una ruta desinstala el fichero viejo y baja el nuevo (que es lo correcto, pero conviene saberlo) |

## 4. Trampas que ya nos costaron horas

Están documentadas donde toca, pero conviene conocerlas de antemano.

**Toda migración se registra a sí misma.** El motor solo mira la tabla
`schema_version`; si tu `.sql` no acaba con su `INSERT`, se reaplica en cada
arranque. Con un `DROP TABLE` dentro, eso borra datos. El motor ahora lo
comprueba y falla, pero mejor no llegar ahí.

**Nunca reemplaces un jar del cliente con Minecraft abierto.** Fabric carga
las clases cuando las necesita, así que el juego sigue tan tranquilo hasta que
toca cargar una nueva — y revienta con un error de `netty` que no se parece en
nada a la causa. Hoy eso lo cubre el launcher: reconoce el síntoma y ofrece
reparar (`launcher/src/main/core/diagnostico.js`).

**Cobblemon: compila contra el JAR de 1.7.3, no contra el fuente clonado.** El
`main` del repositorio va por 1.8.0 y sus firmas difieren. Usar el fuente
equivocado da `NoSuchMethodError` en ejecución.

**Dibujar Pokémon en 3D exige tres cosas a la vez** — rotación nueva por
llamada, dibujar en pasadas y limpiar la profundidad. Con dos de las tres,
parpadea. Ver [`docs/ui/interfaz-cliente.md`](docs/ui/interfaz-cliente.md) §5.

**No implementes ninguna pantalla como menú de cofre.** Ni provisional. Se
borraron todos el 2026-08-12 (D-026) y volver a meter uno «mientras tanto» es
exactamente lo que se decidió no hacer: un provisional que funciona se queda, y
fija el diseño de lo que venga después.

## 5. Antes de dar algo por bueno

```
cd mod && bash build.sh          # el mod compila
cd launcher && npm test          # el nucleo del launcher, 25 pruebas
```

Y en el servidor, `/luna autotest`. **Verde o no se despliega.**

## 6. Credenciales

**No están en el repositorio, y es correcto.** Para trabajar en el código no
hacen falta: se compila y se prueba en local.

Si necesitas desplegar, pídeselas al dueño por privado y ponlas en `.env`
(está git-ignorado). Plantilla en `.env.example`.

## 7. Por dónde va el proyecto

`CLAUDE.md` §0 tiene el estado actual y qué toca después. `docs/roadmap/backlog.md`
tiene las tareas con su id.

Resumen a 2026-08-12: el mod funciona contra MariaDB y el launcher está
terminado y se autoactualiza. **La interfaz de juego no existe**: se borró
entera para rehacerla con arte real (D-026), así que hoy el trabajo es
**construir la ciudadela** —que no es programación— y esperar el arte.
