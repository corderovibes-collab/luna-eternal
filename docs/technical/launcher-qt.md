# El launcher Qt — fork de FreesmLauncher

## Purpose

**Dónde está el fork, qué tiene hecho, y por dónde seguir.** Este documento
existe para que una sesión nueva pueda continuar sin reconstruir el contexto:
si algo aquí no coincide con el código, gana el código y se corrige esto.

## Dependencies

- [`launcher.md`](launcher.md) — el launcher de Electron, que es **el que se
  reparte hoy**
- [`distribucion.md`](distribucion.md) — el manifiesto que este fork consume
- CLAUDE.md **D-035** (la decisión de forkear) y **D-036** (la distribución)

## Current Status

**2026-08-19 · el motor funciona de punta a punta contra el servidor real.**

Verificado en vivo: el launcher trae el puntero, sigue al manifiesto, comprueba
su huella, **crea la instancia** con la versión que dice el manifiesto y
**descarga el pack entero**.

```
mods          135 ficheros
config        109
shaderpacks     2
estado        pack 0.2.0 · 155 ficheros anotados
```

Y **Jugar sincroniza antes de arrancar**, que es el comportamiento esencial del
quiosco.

> ⚠️ **Aun así NO se reparte todavía.** Faltan las pantallas de Prism que un
> jugador no debe ver, los perfiles, el diagnóstico y la cadena de publicación.
> El launcher oficial sigue siendo el de Electron (**1.1.1**, publicado y
> autoactualizable).

### Lo que ya hace

| | |
|---|---|
| Instancia única | La busca por nombre y la crea si falta |
| Jugar | Pone el pack al día y **solo entonces** arranca |
| Gestión de instancias | Fuera de las dos barras |
| Asistente de configuración | Desactivado: idioma, Java y CurseForge decididos |
| Panel de noticias | Oculto |
| Traducción | Español al 99,8 % de serie |

---

## 1. Dónde está todo

| | |
|---|---|
| Fuente | `D:\luna-launcher` · rama **`luna`** (no `develop`, que es la de Freesm) |
| Origen | `upstream` → FreesmTeam/FreesmLauncher · commit de partida `5114de67a` (2.2.2) |
| Toolchain | `.toolchain/` del proyecto principal, **git-ignorado** |
| Compilar | `powershell tools/build-launcher.ps1` |
| Publicar | `powershell tools/build-launcher.ps1 -Publicar` (con LTO) |

Nada se instaló en el sistema: Qt 6.10.2 y vcpkg viven dentro de `.toolchain/`
y se borran con la carpeta. MSVC y CMake salen de Visual Studio Build Tools.

### Recuperar el toolchain en una máquina limpia

```powershell
python -m venv .toolchain\venv
.toolchain\venv\Scripts\python -m pip install aqtinstall
.toolchain\venv\Scripts\python -m aqt install-qt windows desktop 6.10.2 win64_msvc2022_64 `
    -m qtimageformats qtnetworkauth -O .toolchain\Qt
git clone --depth 1 https://github.com/microsoft/vcpkg .toolchain\vcpkg
.toolchain\vcpkg\bootstrap-vcpkg.bat -disableMetrics
```

---

## 2. El motor: siete piezas, 64 pruebas

Todo vive en `launcher/luna/` y las pruebas en `tests/Luna*_test.cpp`. Se
enlaza contra `Launcher_logic`, así que **la lógica que decide qué se instala
en la máquina de un jugador se puede probar entera sin red y sin disco**.

```
LunaConfig     dónde vive el pack — un solo sitio          7 pruebas
LunaFetch      lo trae: puntero → manifiesto → huella       —
LunaManifest   lo interpreta y valida                      12
LunaInstance   crea la instancia con SUS versiones          8
LunaSync       decide qué bajar, qué borrar, qué respetar  14
LunaDownload   descarga con espejos, huella por origen     10
LunaApply      escribe, extrae, borra                      13
LunaUpdate     el orquestador que junta todo                —
```

### Correr las pruebas

```bash
export PATH="/d/pokereportversionmejorada/.toolchain/Qt/6.10.2/msvc2022_64/bin:$PATH"
cd /d/luna-launcher/build/Release
for t in LunaManifest LunaInstance LunaSync LunaDownload LunaApply LunaConfig; do
  ./$t.exe -o /tmp/$t.txt,txt >/dev/null 2>&1
  grep -E "^Totals" /tmp/$t.txt
done
```

> ⚠️ **QtTest en Windows no manda su salida por la tubería.** Hay que pasarle
> `-o fichero,txt` y leer el fichero. Un `exit 0` mudo **no** significa que no
> haya corrido nada.

---

## 3. Las decisiones que no son obvias

Cada una viene de un fallo real de este proyecto. Están comentadas en el código,
pero conviene tenerlas juntas.

### El orden de `LunaUpdate` no es negociable

```
1. traer el manifiesto
2. leer el estado guardado
3. decidir
4. BORRAR lo retirado        ← antes de descargar
5. descargar
6. extraer los zips
7. GUARDAR EL ESTADO         ← lo último de todo
```

**El estado el último.** Dice «esto es lo que hay instalado». Guardarlo antes de
terminar dejaría anotado como instalado algo que no está, y el arranque
siguiente se fiaría del atajo y **no lo volvería a bajar nunca**: un pack
incompleto que el launcher cree completo. No da error y no se arregla solo.

**Los borrados antes que las descargas.** Al revés, quitar un mod y añadir su
sustituto en la misma publicación los deja conviviendo un rato. Con mods de
bloques eso es `Registry remapping failed` en la cara del jugador.

### `mods/` se barre de verdad, no solo lo anotado

`DiskProbe::modJars()` lista lo que hay **realmente** en la carpeta, y el
planificador retira todo `.jar` que el manifiesto no mencione.

Sin eso, la limpieza solo alcanza lo que el estado guardado recuerda. Un jar que
llegó por otra vía —una instalación anterior, o una versión del launcher que aún
no lo apuntaba— sobrevive a **todas** las actualizaciones.

**Eso dejó a jugadores fuera del servidor el 2026-08-19.** Arrastraban
`trinkets` y `accessories-compat-layer` de un pack anterior; el servidor ya no
los tenía, y ese puente le mandaba unas ranuras que no sabía leer:

```
Failed to decode packet 'clientbound/minecraft:custom_payload'
Caused by: StructFieldException: [Field: exported_slots]
```

Al dueño **no le pasaba** —su instalación estaba bien anotada— así que parecía
cosa de máquinas concretas. Se perdieron varios diagnósticos comparando servidor
y cliente (jars, versiones de mods, perfiles) cuando la diferencia estaba en el
registro del propio launcher.

> ⚠️ **Consecuencia asumida:** un mod que el jugador añada a mano a `mods/`
> desaparece en la siguiente actualización. Es lo correcto aquí —la regla del
> proyecto es que el servidor sea *subconjunto* del cliente, y un mod extra que
> registre algo sincronizado es justo lo que echa a la gente—, pero es un cambio
> de comportamiento.
>
> **Solo `mods/`.** `config/`, `resourcepacks/` y `shaderpacks/` llevan cosas del
> jugador y ahí no se toca nada sin anotar.

### Tres cerraduras contra `../`

El manifiesto viene de la red, y los zips también.

1. **La huella del manifiesto**, verificada antes de interpretar una sola línea
2. **`isSafeRelativePath`** en el planificador — filtra la entrada y ni la anota
3. **`resolveInInstance`** antes de escribir, que además confirma que la ruta
   absoluta sigue cayendo dentro

La barra invertida se construye por código (`QChar(0x5C)`) y **nunca como
literal**: un literal de barra se pierde con facilidad al pasar por generadores
y plantillas, y equivocarse ahí abre justo la puerta que esas funciones existen
para cerrar.

### Las versiones salen del manifiesto, nunca escritas a mano

`LunaInstance::versionsFor()` saca Minecraft y Fabric del manifiesto. Ya costó
una vez: la versión del cargador estaba puesta a mano, caducó en silencio y el
pack generado no arrancaba.

Truco que evita una dependencia entera: el sistema de componentes de Prism solo
pide `descriptor()` —una cadena— y resuelve al arrancar el juego. Por eso
`PlainVersion` envuelve la cadena del manifiesto en vez de cargar por red las
listas de versiones.

### El validador de huella va en cada origen

Si un espejo sirve un fichero corrupto, esa opción falla y se pasa a la
siguiente. Validando solo al final, un espejo malo tumbaría la descarga entera
sin llegar a reintentar en otro sitio.

`MultipleOptionsTask` de Prism ya hace el failover: ejecuta subtareas en
secuencia hasta que una funciona. **No hay que escribirlo.**

### Ficheros ocultos en Windows

`writeFileSafely()` borra y reescribe si el primer intento falla. En Windows,
abrir para escritura un fichero **oculto** falla, y el error no menciona en
ningún momento que la causa sea el atributo.

Llegó a un jugador el 2026-08-18: el pack trae
`config/euphoria_patcher/.data.json`, el mod lo recrea oculto, y toda
actualización que reextrajera `config/` moría al 99 %.

---

## 4. Trampas de la cadena de herramientas

Las cinco costaron tiempo, y **ninguna señala su causa**. Están anotadas donde
tocan; aquí van juntas.

| Síntoma | Causa real |
|---|---|
| 3 símbolos sin resolver al enlazar una prueba | **`moc` no parsea literales en bruto** (`R"(...)"`). Deja de ver la clase con `Q_OBJECT`. La única pista es una nota fácil de pasar por alto: *«AutoMoc: No relevant classes found»* |
| `ninja: failed recompaction: Permission denied` | **Otra compilación abierta.** El script ahora se niega a arrancar si detecta procesos vivos |
| `exit 0` mudo al correr una prueba | **QtTest no escribe por la tubería en Windows.** Usar `-o fichero,txt` |
| El script decía `LTO: ON` y CMake recibía basura | **PowerShell 5.1 no expande variables** en un argumento suelto que empieza por `-` y lleva `=`. Hay que montar la cadena antes |
| `no se puede convertir Luna::State a Task::State` | **La clase base tapa el nombre.** Dentro del cuerpo de la clase hay que cualificar `Luna::State` |

### El LTO

El preset trae `ENABLE_LTO=ON`, que cuesta **~9 minutos y 2 GB de RAM por
enlazado**. Se apaga para desarrollar:

```
build-launcher.ps1              rápido, sin LTO   ← desarrollar
build-launcher.ps1 -Publicar    con LTO           ← publicar
```

> ⚠️ **Lo que se reparte tiene que salir de `-Publicar`.** Sin LTO funciona
> igual, pero es más grande y algo más lento.

---

## 5. La marca (GPL)

Renombrar es obligación, no gusto: GPL §7.c/e, y Prism lo pide a sus forks.

| | |
|---|---|
| Binario | `lunaeternal.exe` |
| AppID | `net.pokereport.LunaEternal` — misma convención que el paquete del mod |
| Versión | **0.1.0 «Ciudadela»**, no la 2.2.2 heredada |

**La versión no es cosmética.** El autoactualizador compara contra *nuestras*
releases: arrancando en 2.2.2, la primera que publicáramos sería menor que la
instalada y **no le llegaría una actualización a nadie**, sin dar ningún error.

**El arte se sustituye, no se hereda.** Los logos de Freesm llevan CC BY-SA 4.0
y acreditan a sus autores por nombre. Se generan con
`python program_info/genicons-luna.py` desde una sola imagen.

**El copyright de los anteriores se conserva entero** y el nuestro se añade
encima. Borrar las líneas de Freesm, Prism, PolyMC y MultiMC sería incumplir la
GPL §5a.

---

## 6. Lo que falta

| | Esfuerzo |
|---|---|
| **Llamar al motor al arrancar** — `Application::performMainStartupAction()` | 1-2 días |
| **Modo quiosco** — ocultar gestor de instancias y navegador de mods, botón «Jugar» | 3-5 días |
| **Diagnóstico + reparar** — la tabla de `launcher.md` §5, cada fila un fallo real | 2-3 días |
| **Perfiles** jugador/constructor | 1-2 días |
| **Traducción al español** | 2-3 días |
| **CI, instalador NSIS, autoactualización** | 3-5 días |

**Total: 3-4 semanas.**

### Cabos sueltos, señalados en el código

- **`Launcher_META_URL` apunta a `meta.freesmlauncher.org`.** De ahí salen los
  metadatos de Minecraft y Fabric: cada jugador dependería de una máquina que no
  controlamos. Prism sirve los **mismos 13 paquetes** que usamos; los 3 de
  diferencia son inyección de auth que no usamos. Lo que toca algún día es
  levantar el nuestro — el generador es libre
- **`program_info/LunaEternal.icon`** sigue siendo arte de Freesm. Solo se usa al
  compilar para macOS; hay que rehacerlo antes de publicar un binario de Mac
- **`GPL-001`**: crear el repositorio público. Obligatorio **el día que se
  reparta el binario**, no antes

## Last Decision

**2026-08-18** — motor completo y probado (64 pruebas, 7 piezas). Nada
enchufado a la interfaz todavía. Ver commits `23201dc45` … `c8fd670e7` en la
rama `luna`.

## Next Actions

1. Enchufar `Luna::UpdateTask` al arranque de la aplicación
2. Modo quiosco
3. Diagnóstico y reparar
