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

---

## 7. Al cerrar el 2026-08-19

**Verificado en vivo, desde una carpeta de datos borrada:**

```
mods          135 ficheros
config        109
shaderpacks     2
servers.dat   102 bytes    ← el servidor aparece solo en la lista
estado        pack 0.2.0 · 155 ficheros anotados
huérfanos       0
```

La cadena entera funciona: puntero → manifiesto → huella → instancia → plan →
descarga con espejos → zips → estado.

### ⚠️ El fallo que casi lo arruina, y que hay que recordar

Antes de arreglarlo, **la cadena del puntero se rompía** y el manifiesto llegaba
vacío. `UpdateTask` reportaba éxito habiendo hecho nada. Y lo grave:

> Un manifiesto vacío produce un plan **sin nada que bajar y con todos los mods
> como huérfanos**. El barrido los retira y la tarea dice que todo fue bien.

Se salvó porque la instancia estaba recién creada. **Con las 135 anteriores, las
habría borrado todas.**

Hay dos frenos ahora —el manifiesto tiene que ser válido, y se para si el plan
quiere retirar más de diez ficheros sin instalar ninguno—, pero la lección es
más general:

**El barrido de huérfanos que arregló un incidente es el mismo que convierte
«manifiesto a medias» en «instalación destruida».** Una función que limpia bien
es una función que borra bien.

### ✅ APLICADO (2026-08-23): el servidor se llama **PokeReport Network**

**Decisión del usuario, 2026-08-20**, aplicada el 2026-08-23. «Luna Eternal»
deja de ser el nombre del servidor.

**Y resultó costar mucho menos de lo que decía esta sección**, porque el aviso
que había aquí estaba **atribuido a la variable equivocada**:

> ⚠️⚠️ **NO es `Launcher_AppID` lo que mueve la carpeta de datos: es
> `Launcher_CommonName`.** Se comprobó leyendo `Application.cpp`, no de memoria:
> `setOrganizationName(BuildConfig.LAUNCHER_NAME)` y `LAUNCHER_NAME` sale de
> `Launcher_CommonName`, así que de ahí salen `%APPDATA%\LunaEternal`, la
> carpeta de instalación y las claves del registro. `Launcher_AppID` solo se usa
> en el `.desktop` y el `.metainfo` de Linux y en el bundle de macOS.

Eso cambia el trabajo entero, porque **los dos nombres están separados**:

| Variable | Qué es | Se cambió |
|---|---|---|
| `Launcher_DisplayName` | **Todo lo que el jugador ve**: título del instalador, acceso directo del menú Inicio, acceso directo del escritorio, «Agregar o quitar programas», título de la ventana, «Acerca de», `ProductName` del `.exe` | ✅ → `PokeReport Network` |
| `Launcher_CommonName` | **Identidad**: `%APPDATA%`, carpeta de instalación, registro, clave de desinstalar | ❌ se queda `LunaEternal` |
| `Launcher_APP_BINARY_NAME` | El nombre del `.exe`, que el actualizador reemplaza fichero a fichero | ❌ se queda `lunaeternal` |
| `Launcher_AppID` | `.desktop` / `.metainfo` / bundle de macOS | ❌ se queda |

**El jugador ve «PokeReport Network» en todas partes y nadie vuelve a descargar
nada.** Lo único que sigue diciendo `LunaEternal` son rutas y el nombre del
`.exe`, que solo se ven yendo a buscarlas.

#### Dos cosas que sí había que migrar

**1 · El nombre de la instancia.** `findInstance()` la busca **por nombre**, así
que cambiarlo a secas le habría creado una **segunda instancia** a todo el que
ya lo tuviera: otros 450 MB, y su instancia de siempre —con sus partidas y sus
ajustes— ahí al lado, con pinta de haberse perdido. Ahora existe
`instanceNamesAntiguos()` y `findInstance()` **renombra** la que encuentra, así
que la migración pasa una sola vez y sola. Hay prueba que lo fija.

**2 · Los accesos directos.** Se llaman como el `DisplayName`, así que el viejo
no se sobrescribe: se quedaría al lado apuntando al mismo `.exe`. El instalador
borra `Luna Eternal.lnk` del menú Inicio y del escritorio antes de crear el
suyo, y el desinstalador también.

#### Los títulos de diálogo ya no llevan el nombre escrito a mano

Había **quince** `tr("Luna Eternal")` repartidos por `MainWindow.cpp` y
`Application.cpp`. Ahora leen `BuildConfig.LAUNCHER_DISPLAYNAME`. Un nombre
escrito a mano en quince sitios es un renombrado que se queda a medias sin dar
ningún error: simplemente hay una pantalla que sigue diciendo el nombre viejo.

#### Fuera del fork

| Dónde | Qué |
|---|---|
| `tools/gen_modpack.py` | El nombre en la lista de multijugador (`§6PokeReport §bNetwork`), el nombre de los dos `.mrpack` y la cabecera de `iris.properties`. ⚠️ `servers.dat` va marcado `once`, así que **solo lo ve quien instale de cero** — a quien ya lo tenga no se le toca su lista de servidores, y es lo correcto |
| `mod/` | Constantes `LunaEternal.NOMBRE` y `LunaEternal.PREFIJO`, **en un solo sitio**, y las usan el prefijo de chat del inicial y `/luna status`. Más la categoría de controles (`lang/*.json`) y el nombre del mod (`fabric.mod.json`) |
| `neon/` | El nombre del mod en su `fabric.mod.json` |
| `launcher/` (Electron) | `productName`, `shortcutName`, título de ventana y de la página. ⚠️ **`appId` NO se toca**: es la identidad con la que su autoactualizador reconoce la instalación. Su carpeta de datos está escrita a mano en `plataforma.js` (`.lunaeternal`) y no depende del `productName`, así que renombrar no la mueve |

> ⚠️ **`MOD_ID` no se toca ni se tocará.** Es identidad: registro de Fabric,
> espacios de nombres de datapacks y resource packs, y la ruta de los assets.
> Cambiarlo rompe el mundo guardado.

### Lo que falta para poder repartirlo

| | Esfuerzo |
|---|---|
| **Perfiles** jugador/constructor | Sin ellos, los constructores no reciben Axiom |
| **Aviso de migrar datos de Prism** | Un jugador no debe verlo |
| **Navegador de mods** | Idem |
| **Error 404 del actualizador** | Apunta a `luna-eternal-launcher`, que no existe (`GPL-001`). Debe fallar callando |
| **Instalador NSIS + CI** | Hoy habría que copiar una carpeta a mano |
| Icono de la instancia · imagen de Prism | Cosmético |

**Unos días**, no semanas: el riesgo grande —que el motor funcionara— está
despejado.

### Cómo retomar

```powershell
# compilar solo lo que toca (segundos, no minutos)
powershell tools/build-launcher.ps1 -Solo LunaEternal
powershell tools/build-launcher.ps1 -Solo LunaSync      # una prueba

# prueba de arranque limpio: borrar y abrir
#   %APPDATA%\LunaEternal
```

> ⚠️ **El enlazado falla con `LNK1104` si el launcher está abierto.** Ciérralo
> antes de compilar.

## 8. Por qué la gente abandonaba la instalación (2026-08-21)

Dos capturas de un jugador —«Múltiples subtareas fallidas / ¡Todos los intentos
han fracasado!» ×3, y detrás «No se pudo poner el pack al día»— destaparon
**tres fallos distintos**, ninguno de ellos el que parecía.

### 8.1 · El pack no reintentaba NADA. Ni una vez

`makeFileTask` montaba un `MultipleOptionsTask`, que prueba orígenes en
secuencia hasta que uno contesta. Suena a reintento y **no lo es**:

```
159 ficheros en el manifiesto
157 de ellos con UN SOLO origen   -> "varias opciones" = una = CERO reintentos
  2 con un espejo de verdad       -> el failover cubria al 1,3 % del pack
```

Y `Net::Download` tampoco reintenta solo: su `AutoRetry` hay que encenderlo a
mano —`makeFile` no lo hace— y además **solo cubre el HTTP 429**. Un corte de
TLS, un DNS lento, un 503 del CDN o una descarga parada más de
`RequestTimeout` (60 s) mataban el fichero al primer tropiezo.

**La cuenta que lo convierte en un problema de producto:**

| Fallo por fichero | Probabilidad de que la instalación ENTERA falle |
|---|---|
| 0,5 % | 55 % |
| 1 % | 80 % |
| **2 %** (wifi doméstico normal) | **96 %** |

Con 3 de 159 fallando, el jugador de la captura estaba justo en ese 2 %. **No
es mala suerte suya: es la tasa de abandono del launcher.**

Y la lección ya estaba aprendida en el launcher de Electron —«se rendía
demasiado pronto ante un 503: eran 4 reintentos con tope de 8 s… ahora 8 con
tope de 30 s»—. **El fork no la heredó.** Es el patrón de esta migración: el
motor se reescribió bien y las cicatrices se quedaron en el otro repositorio.

Arreglado en `luna/LunaDownload.cpp`: `FileTask` alterna orígenes y reintenta
con espera creciente (1 s, 2 s, 4 s…, tope 15 s), 4 intentos por origen. Un
4xx —salvo 408 y 429— sigue siendo definitivo **para ese origen**, que era lo
correcto del diseño anterior y se conserva.

### 8.2 · El error no nombraba nada

`MultipleOptionsTask` tira el error de debajo (`All attempts have failed!`) y
`ConcurrentTask` resume N fallos en `Multiple failed tasks`. Resultado: una
ventana que no dice **qué** fichero ni **de dónde** ni **por qué**.

> Diagnosticar aquella captura exigió leer el fuente del launcher con el
> manifiesto descargado al lado y probar las 159 URLs a mano. Eso no lo puede
> hacer un jugador, y tampoco debería tener que hacerlo la siguiente sesión.

Ahora el mensaje es `mods/x.jar - cdn.modrinth.com respondió HTTP 503 (4
intentos)`, `UpdateTask` acumula un motivo por fichero, y el diálogo final de
`MainWindow` **incluye el detalle y se puede seleccionar con el ratón** — antes
salían dos ventanas y la que se podía fotografiar era la que no decía nada.

### 8.3 · ⚠️⚠️ El instalador no traía el runtime de Visual C++

Medido sobre los binarios de `install/`, no supuesto:

```
lunaeternal.exe   importa   VCRUNTIME140.dll  VCRUNTIME140_1.dll  MSVCP140.dll
Qt6Core.dll       importa   además            MSVCP140_1.dll
install/          contiene  NINGUNA de ellas
win_install.nsi   instala   NINGUNA de ellas
```

Y no es un olvido de nadie: `launcher/CMakeLists.txt` **las excluye a
propósito**, heredado de aguas arriba — `NO_COMPILER_RUNTIME`,
`PRE_EXCLUDE_REGEXES "^vcruntime.*\.dll$"` y `POST_EXCLUDE_REGEXES "system32"`
(que además se lleva `msvcp140.dll`, porque se resuelve desde ahí).

**En un Windows sin el redistribuible, el doble clic no hace absolutamente
nada.** Ni ventana, ni error, ni log: Windows no encuentra la DLL y abandona
antes de que corra una línea nuestra. Indistinguible de «el launcher está
roto», y sin rastro que el jugador pueda mandar.

> ⚠️ `VCRUNTIME140_1.dll` es la que suele faltar: llegó con Visual Studio 2019.
> Un equipo con redistribuibles antiguos tiene las otras dos y **no** esa — y
> el síntoma es idéntico a no tener ninguna.

Se arregla en **dos sitios, y hacen falta los dos**:

| | |
|---|---|
| `launcher/CMakeLists.txt` | `InstallRequiredSystemLibraries` copia las DLL **junto al .exe**. Windows mira la carpeta del ejecutable antes que el sistema, así que **el launcher arranca sin permisos de administrador y sin descargar nada** (~600 KB) |
| `luna/LunaPreflight` | Instala el redistribuible **del sistema**. Hace falta igualmente: los `natives` de LWJGL los carga **la JVM**, y a esos no les vale una copia junto a nuestro .exe |

### 8.4 · La comprobación de requisitos existía y se perdió al cambiar de launcher

El de Electron tenía `core/preflight.js`: Visual C++ (con instalación de un
clic), drivers de gráfica, disco, RAM, versión de Windows. **El fork nació sin
ello, y el fork es lo que la gente tiene instalado hoy.** No era una idea
nueva que diseñar: era una funcionalidad que devolver.

`luna/LunaPreflight.{h,cpp}` la reimplementa y `MainWindow::requisitosDelEquipo`
la ejecuta **antes de bajar el pack** — descubrir que falta una DLL de 600 KB
después de bajar 434 MB es gastarle a alguien media hora de conexión antes de
darle la mala noticia.

> **Avisar no es bloquear.** Solo `Nivel::Error` impide jugar. Con 6 GB de RAM
> el pack va a tirones, y esa es una decisión del jugador. Los avisos salen
> **una sola vez**, recordados por identificador: uno que sale en cada partida
> deja de leerse a la segunda, y el día que diga algo nuevo tampoco se leerá.

### 8.5 · Lo que se dio por hecho y NO era verdad

| Se creía | Lo que hay |
|---|---|
| «Falta Git» | **No hay ni una llamada a Git** en ninguno de los dos launchers. Se descargó todo por HTTPS desde CDN. Si alguien vio ese error, viene de otro sitio y hace falta la captura |
| «Falta Java» | El modo quiosco ya fuerza `AutomaticJavaDownload` y `AutomaticJavaSwitch`. El launcher se trae su propio Java |
| «Las URLs del pack están caídas» | Las **159** contestan `206`. Comprobadas una a una el 2026-08-21 |
| «Falta el runtime de Windows» | ✅ **Cierto, y es el único de la lista que lo era** |

### 8.6 · ⚠️⚠️ `--launch` se saltaba la sincronización entera

Encontrado al intentar automatizar una prueba. `Application::performMainStartupAction()`
llamaba a `launch(inst, ...)` **directamente**: ese camino no pasa por
`MainWindow::lanzarPoniendoAlDia`, así que **no ponía el pack al día ni miraba
los requisitos**.

No es un caso de laboratorio: **Prism ofrece «crear acceso directo» a la
instancia en su propio menú** (`on_actionCreateInstanceShortcut_triggered`), y
ese icono arranca por ahí. Quien lo usara se conectaba con el pack viejo, y lo
que veía era al servidor echándole con un error que no explica nada — justo lo
que el diálogo de Jugar existe para evitar.

El agujero estaba abierto desde que se enchufó `UpdateTask`, **porque se enchufó
en la ventana y no en el arranque**. La lección general: cuando algo es un
invariante del producto («nunca se juega con el pack desactualizado»), ponerlo en
*un* botón no basta — hay que ponerlo en **todos los caminos que arrancan el
juego**, y conviene enumerarlos antes de darlo por hecho.

### 8.7 · Qué está probado y qué no (2026-08-21)

| | |
|---|---|
| Compila sin un solo aviso | ✅ y el proyecto usa *warnings as errors* |
| 65/65 pruebas del motor | ✅ incluidas las 10 de `LunaDownload` con la API nueva |
| Las 8 DLL del runtime en el instalador | ✅ `File *.dll` las empaqueta; +632 KB comprimidos |
| Arranque, requisitos, sync 159/159, juego | ✅ verificado en vivo |
| La **política** de reintentos | ✅ `test_soloUn4xxDescartaElOrigen`, 11/11 en `LunaDownload` |
| **Los reintentos, contra un fallo real** | ❌ **NO**. No hubo nada que descargar, así que `FileTask` no bajó ni un byte |
| **El aviso de Visual C++** | ❌ **NO**. Este PC ya tiene el runtime; hace falta un Windows limpio |

> ⚠️ **El arreglo del 96 % sigue sin demostrarse.** Está escrito, compilado,
> publicado y con su política cubierta por pruebas — pero hasta que una descarga
> falle de verdad y se vea aguantar, el **mecanismo** es una hipótesis bien
> fundada, no un hecho. La prueba barata: quitar 3 mods de la instancia y darle
> a Jugar. **Se estrena en la máquina de un jugador.**

### 8.8 · Publicado: v0.2.0 (2026-08-21)

```
https://github.com/corderovibes-collab/luna-eternal-launcher/releases/download/v0.2.0/LunaEternal-Setup.exe
28.394.156 B (27,1 MB)  ·  CI verde, 13 pasos, 35 min
```

**Fue la primera vez que `luna-release.yml` llegó hasta el final.** En `v0.1.0`
el flujo que corrió y falló fue el de Freesm (§8.6 del commit `c588d710d`).

Dos cosas que se interceptaron *entre* el «vamos a publicar» y el tag, las dos
habrían llegado a los jugadores:

| | |
|---|---|
| **La versión estaba escrita a mano** como `0.1.0` en `CMakeLists.txt`, y la CI **no la deriva de la etiqueta** | Publicar `v0.2.0` habría colgado una release con un binario que se identifica como 0.1.0. El autoactualizador compara versiones: actualiza, sigue viendo la vieja, **vuelve a actualizar en cada arranque**. Bucle sin ningún error visible |
| **`release.yml` se disparaba con `tags: "*"`** | Cada publicación arrancaba la matriz entera de Freesm con secretos que no tenemos, y peleándose por la misma release. Pasa a `workflow_dispatch` |

> ⚠️ **Subir de versión = tocar el número en `CMakeLists.txt` Y empujar su
> etiqueta.** No hay nada que lo compruebe automáticamente; si se olvida, el
> síntoma es el bucle de actualización, que no da error.

**Mejora pendiente, medida:** la CI compila los **27 ejecutables de prueba**,
cada uno enlazado con LTCG — exactamente lo que hacía eterna la compilación en
local (§9). Pasarle `-DBUILD_TESTING=OFF` al flujo de publicación se llevaría
buena parte de esos 35 minutos. No se tocó para no cambiar el workflow con una
ejecución en marcha.

## 9. El PC de desarrollo se formateó (2026-08-20) y qué costó eso

**Windows se reinstaló el 2026-08-20 a las 22:23, desde una imagen de 22H2 de
abril de 2023 (build 19045.2965).** Con el formateo se fueron Visual Studio
Build Tools, Python, `winget` y `pwsh`; `.toolchain/` sobrevivió porque vive en
`D:`, pero solo conserva **Qt 6.10.2 y vcpkg** — MSVC, CMake y Ninja venían
dentro de Build Tools.

Reinstalado el 2026-08-21: **Build Tools 2022, MSVC 14.44.35207**. Y encaja
mejor que el v18 anterior — el Qt de `.toolchain` es `msvc2022_64`, compilado
con ese mismo toolset.

### ⚠️ `build-launcher.ps1` tenía la ruta de Visual Studio escrita a mano

```powershell
$VS = 'C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools'
```

El número de versión va **dentro** de la ruta, así que reinstalar otra edición
la rompe aunque todo lo demás esté bien — y aquí pasó: la reinstalación cayó en
`2022`, no en `18`. Peor aún, el síntoma era

```
cmake : El término 'cmake' no se reconoce...
```

que apunta a CMake y **no** a que no hay compilador, y que además es idéntico al
que sale si Visual Studio simplemente está en otra carpeta.

Corregido: la ruta se busca con `vswhere.exe` —que se instala en una ruta fija
con cualquier edición, Build Tools incluidas— con rastreo manual de respaldo. Y
se comprueban `cl`, `cmake`, `ninja` y Qt **por separado**, para que faltar uno
diga *cuál*.

### ⚠️⚠️ Una imagen de 2023 en una CPU de 2025 no arranca programas .NET 9

Con Build Tools ya instalado, la compilación seguía muriendo — **antes de
compilar una sola línea nuestra**:

```
Detecting compiler hash for triplet x64-windows...
error: ...\powershell-core-7.6.3-windows\pwsh.exe --version failed
Fatal error.
Your Windows doesn't fully support CET. Please install all available Windows updates.
```

**CET** (Control-flow Enforcement Technology) es la pila sombra por hardware. El
**Ryzen 7 250** de este equipo la soporta y la anuncia; el Windows de abril de
2023 **no tiene las APIs completas** para usarla. .NET 9 detecta el desacuerdo y
aborta. Y `pwsh` 7.6.3 —que vcpkg exige, en esa versión exacta— es .NET 9.

> **No es un problema del proyecto ni del launcher.** En ese equipo, tal como
> estaba, **ningún** programa .NET 9 podía arrancar.

**Y la solución era un reinicio.** `KB5066790` y `KB5066791` estaban instaladas
desde el mismo 2026-08-21, pero el UBR seguía en `2965` porque el equipo no se
había reiniciado desde el formateo. Las cuatro señales lo decían:

```
CBS RebootPending · WU RebootRequired · PackagesPending · PendingFileRenameOperations
```

> ⚠️ **Antes de dar por rota una cadena de herramientas en un equipo recién
> formateado, mira si hay un reinicio pendiente.** `Get-HotFix` puede listar
> parches de hoy mientras el `UBR` del registro sigue anclado en el de la
> imagen: eso es exactamente esa situación, y se confunde con «las
> actualizaciones no se instalan».

### ⚠️ `-Publicar` NO CAMBIA NADA EN MSVC. Lo lento eran las pruebas

Medido, no supuesto. Dos compilaciones seguidas, una con `LTO: OFF` y otra con
`LTO: ON`, dieron **el mismo ejecutable de 16,4 MB en ~2,5 minutos las dos**. La
causa está en el `CMakeLists.txt`, líneas 48-60:

```cmake
"$<$<COMPILE_LANGUAGE:C,CXX>:/LTCG;/MANIFEST:NO;/STACK:8388608>"   # enlazado
add_compile_options("$<$<AND:$<CONFIG:Release,...>:/GL>")          # compilacion
```

**Ninguna de las dos está dentro de `if(ENABLE_LTO)`.** En MSVC, *toda*
compilación Release lleva ya optimización de programa completo: verificado en
`build/CMakeFiles/impl-Release.ninja`, con `/GL` en 493 compilaciones y `/LTCG`
en los 3 enlazados. `ENABLE_LTO` solo activa `CMAKE_INTERPROCEDURAL_OPTIMIZATION`,
que en MSVC añade exactamente esos mismos dos flags — es decir, nada nuevo.

> Lo que hacía eterna la compilación no era el LTO: eran **27 ejecutables de
> prueba**, cada uno enlazado con LTCG. Quitarlos con `-SinPruebas` bajó el ciclo
> completo **de ~15 minutos a 2,4**, instalador incluido.

`-Publicar` se queda porque en otros compiladores (Clang, GCC) sí hace algo, y
porque el día que se compile fuera de MSVC volverá a importar. Pero **en Windows
no hay que esperar por él**.

### Lo que sigue faltando en el equipo

**Python: recuperado el 2026-08-21**, en `.toolchain\python` (3.12.10 + pip +
Pillow + numpy). Verificado con `gen_bloques.py --verificar`: 602 bloques, 1540
modelos, 230 texturas. `.toolchain/venv` es un resto muerto — apuntaba a
`C:\Python314`, borrado con el formateo.

> ⚠️ **El instalador MSI de Python falla con `0x80070003` en este equipo**, en
> `.toolchain` y también en la ruta por defecto. Se usa la **distribución
> embebida** (zip), que además no toca el sistema. Dos trampas suyas, las dos
> mudas:
>
> 1. `import site` viene **comentado** en `python312._pth`. Sin descomentarlo no
>    hay `site-packages` y `pip` se instala pero no sirve para nada.
> 2. **Con un `._pth` presente, Python NO añade el directorio del script a
>    `sys.path`.** Por eso `gen_bloques.py` moría con
>    `ModuleNotFoundError: No module named 'bloques'` con el paquete justo al
>    lado. Se arregla con `..\..\tools` como primera línea del `._pth`.

**Windows 10 22H2 está fuera de soporte** desde octubre de 2025. El equipo
admite Windows 11 de sobra. No bloquea nada hoy; conviene decidirlo antes de que
sí lo haga.

> ⚠️ **Node.js tampoco estaba** (2026-08-23). Es el **cuarto** que se llevó el
> formateo, después de JDK 21, ffmpeg y `gh`, y hacía falta para dos cosas
> distintas: las **37 pruebas del launcher de Electron** (`npm test`) y las
> herramientas de diseño. Instalado con
> `winget install OpenJS.NodeJS.LTS` (24.19.0).
> Misma trampa de siempre: **winget cambia el PATH pero no el de la terminal ya
> abierta**, así que la primera vez hay que llamarlo por ruta completa
> (`C:\Program Files\nodejs\node.exe`).

---

## 10. El bucle de actualización (2026-08-23)

**El síntoma que reportó el usuario:** el launcher avisaba de que había una
versión nueva **teniendo ya la última instalada**, y al pulsar *Actualizar*
salía un error. Las dos cosas a la vez, en cada arranque.

Eran **tres fallos independientes**, y los tres nacen del **mismo carácter**:
la `v` con la que empiezan las etiquetas de git.

### 10.1 · ⚠️⚠️ `Version("v0.2.0")` es MAYOR que `Version("0.2.0")`. Siempre

`Version` parte la cadena en tramos y los compara de uno en uno. El primer tramo
de `v0.2.0` es la letra **`v`** —de tipo *texto*— y el de `0.2.0` es el **`0`**
—de tipo *número*—. Con tipos distintos, `Version::Section::operator<=>` cae a
comparar **código de carácter**:

```
'v' = 0x76        '0' = 0x30        0x76 > 0x30
```

Y ahí se acaba la comparación: devuelve *mayor* sin llegar a mirar ni un solo
dígito. `Version("v0.1.0") > Version("9.9.9")` es **cierto**.

Como `parseReleasePage()` hacía `release.version = Version(release.tag_name)`, y
la versión instalada es `0.2.0` sin `v`, **toda release del repositorio parecía
más nueva que cualquier cosa instalada**. El jugador actualizaba, arrancaba, y
se le volvía a ofrecer la misma actualización.

**Ni un error en el log**, porque desde dentro todo funcionaba exactamente como
estaba escrito.

Arreglado con `Version::fromTag()` (en `launcher/Version.h`, que es donde se
puede probar), y la `v` **se quita solo para comparar**: `tag_name` se sigue
enseñando tal cual en el diálogo y en el fichero de bloqueo.

> ⚠️ La `v` se quita **solo si va seguida de un dígito**. Una etiqueta que de
> verdad empiece por esa letra —`voyager-1`— tiene que sobrevivir entera, y hay
> prueba que lo fija.

### 10.2 · ⚠️⚠️ El instalador no se llamaba como el actualizador buscaba

`validReleaseArtifacts()` recorre los ficheros de la release y **se queda solo
con los que llevan `Launcher_BUILD_ARTIFACT` en el nombre**:

```cpp
bool for_platform = !platform.isEmpty() && asset_name.contains(platform);
```

`Launcher_BUILD_ARTIFACT` vale `luna-launcher-win-x64` (lo pone la CI). Y NSIS
llamaba al instalador **`LunaEternal-Setup.exe`**, que sale de
`Launcher_CommonName` en `program_info/CMakeLists.txt`.

Los dos nombres eran correctos **por separado**, y no se parecían en nada. La
release se publicaba entera y bien, el launcher veía que había versión nueva, y
al pulsar *Actualizar* **no encontraba ni un fichero que instalar**. El error no
nombraba ni el fichero ni el motivo; el «platforms do not match» solo sale en el
log.

Arreglado en `luna-release.yml`: el instalador **se renombra** a
`luna-launcher-win-x64-Setup.exe` y el paso **falla** si el nombre no contiene
la cadena. Y `ARTIFACT_NAME` **sube al `env` del job**, porque ahora la usan dos
pasos que tienen que decir lo mismo: el que *configura* (lo que el actualizador
busca) y el que *renombra* (lo que encuentra). Escrita dos veces, un día una
cambia y la otra no.

> ⚠️ El nombre no es libre. El mismo filtro descarta lo que contenga
> `portable`, lo que acabe en `.zip`/`.tar.gz`, lo que contenga `arm64` y lo que
> case con `-qt<número>`. `x64` sí vale: lo que descarta es `arm64`.

### 10.3 · ⚠️ Y el launcher se llamaba a sí mismo `0.2.0-1a2b3c4d`

La misma `v`, por el otro lado. `printableVersionString()` pega el canal detrás
cuando la compilación no es una release:

```cpp
if (VERSION_CHANNEL != "stable" && GIT_TAG != vstr)
```

`GIT_TAG` es `v0.2.0` y `vstr` es `0.2.0`, así que **nunca eran iguales** y a
*toda* compilación etiquetada —o sea, a todas las que se reparten— se le pegaba
el hash del commit detrás. No era solo feo en el «Acerca de»: **esa es la cadena
con la que el actualizador se compara**.

### 10.4 · Lo que impide que vuelva

| | |
|---|---|
| `tests/Version_test.cpp` | **62 pruebas, 0 fallos.** Dos nuevas: una fija el comportamiento crudo (`Version("v0.2.0") > Version("9.9.9")`, que **no cambia**) y comprueba que `fromTag` lo neutraliza sin romper el orden entre versiones de verdad; la otra fija que una versión con canal no pide actualizar a la misma |
| `luna-release.yml` | Un paso nuevo **compara la versión de `CMakeLists.txt` con la etiqueta y aborta si no cuadran** — y aborta *antes* de gastar 35 minutos compilando. Estaba avisado en `CLAUDE.md` desde el 21-ago y aun así dependía de que alguien se acordara |
| `luna-release.yml` | El paso del instalador **falla** si el `.exe` no lleva `ARTIFACT_NAME` en el nombre |

> ⚠️ **Nada de esto se ha visto todavía en el juego.** Compila
> (`Version` y `LunaEternal_updater`) y las pruebas pasan, pero el ciclo de
> verdad —release nueva, launcher viejo, actualizar— **solo se puede comprobar
> publicando**. Y para comprobarlo hace falta subir a `0.2.1`: contra `v0.2.0`
> el launcher arreglado dirá, correctamente, que no hay nada que actualizar.

## 11. El tema visual (2026-08-23)

**Petición del usuario:** *«que se vea más bonito, más estilo Pokémon, con el
logo del server… algo bien diseñado y optimizado pero que se vea bellísimo».*

### 11.1 · Los colores salen del logo, no de un gusto

El usuario envió dos PNG: la **Poké Ball en llamas** y el **rótulo POKEREPORT
NETWORK**. Se muestrearon los dos y de ahí salen los cinco colores que mandan:

| | | De dónde |
|---|---|---|
| `#FFC420` | oro | El color de la casa. **Lo único que dice «púlsame»** |
| `#FFE04F` | oro claro | El amarillo alto de las letras |
| `#F86800` | naranja | El borde del rótulo y las llamas |
| `#5C1210` | granate | La banda de detrás de las letras |
| `#E8189B` | magenta | El neón del botón de la ball. **Selección** |

> **El oro es invitación y el magenta es estado**, y por eso no compiten. Con el
> mismo color para las dos cosas, pasar el ratón por una lista parece que va
> cambiando lo que tienes elegido.

Los grises tiran a **cálidos** a propósito: sobre un gris azulado el oro vira a
mostaza y el logo deja de pegar con su propia interfaz.

> ⚠️ **El oro y el ámbar de aviso son la misma familia de color**, y es el precio
> de que la marca sea dorada. Se separan **por forma, no por color**: la acción
> principal es un **degradado** oro→naranja con texto oscuro; el aviso es
> **plano**, sin relleno, y siempre con icono y texto. **No hay botones de
> aviso.**

> ⚠️ **La tipografía del logo NO se usa en la interfaz.** Es de píxeles y a 13 px
> sería ilegible. Vive en el rótulo y solo ahí; el resto es Segoe UI Variable
> Text, la del sistema.

### 11.2 · El QSS es un fichero, no un literal de C++

`FreesmTheme::appStyleSheet()` devuelve su hoja **concatenada en ~40 trozos de
cadena dentro del `.cpp`**. No se puede leer, y en un commit cambiar un color
aparece como una línea de 4.000 caracteres.

El nuestro vive en `:/pokereport/pokereport.qss`, dentro del recurso Qt, y se
edita como lo que es.

### 11.3 · ⚠️⚠️ Y hubo que iniciar el recurso a mano

**El primer intento salió con el gris de Qt**, sin ningún error de compilación.

`Launcher_logic` es una **librería estática**. El enlazador tira todo objeto al
que nadie referencia, y al inicializador que genera `rcc` **no lo referencia
nadie**: el `.qss` sencillamente no estaba dentro del binario.

Y **no sirve ponerlo en `main.cpp`** con los demás `Q_INIT_RESOURCE`, que es lo
primero que uno prueba: esa lista se ejecuta **después** de construir
`Application`, y el tema se aplica **dentro** de ese constructor. Llegaría tarde.

Hoy lo llama el propio tema (`iniciarRecursoPokeReport()`), que es idempotente.

> **Lo que hizo que costara minutos y no una tarde:** el `qWarning` que se
> escribió para ese caso. En el log salió literalmente *«no se pudo abrir
> :/pokereport/pokereport.qss … comprueba que pokereport.qrc está en
> `qt_add_resources()`»*. Devolver la cadena vacía en silencio habría mandado a
> buscar el fallo en los colores.

### 11.4 · El fondo por defecto era un meme de TypeScript

Heredado de Freesm, y **ocupa la pantalla entera del launcher**: es lo primero
que ve alguien al abrirlo, y no dice nada de este servidor.

Ahora es la ball y el rótulo **al 40 % de alfa** — fondo, no protagonista.

> ⚠️ **`CatPainter` lo ancla abajo a la derecha**, así que lo que esté pegado al
> borde del lienzo queda pegado al borde de la ventana. Con 20 px de margen el
> rótulo salía **cortado**; son 90.

### 11.5 · «Ciudadela» y «-luna» se colaban en la barra de título

**Orden del usuario:** *«no debe decir nada de ciudadela ni luna, solo PokeReport
Network»*. Salían de **dos sitios que nadie tenía fichados**:

```
setApplicationDisplayName(DISPLAYNAME + VERSION_CODENAME + printableVersionString())
                                        ^ "Ciudadela"      ^ "0.2.0-luna"
```

El segundo es el **canal**, que es **el nombre de la rama de git**. Hoy el título
es `PokeReport Network 0.2.0` y la versión completa sigue donde hace falta: en
«Acerca de», en el registro y en lo que compara el actualizador. Los temas
heredados dejan de llamarse «Luna oscuro/claro».

### 11.6 · Qué está verificado

| | |
|---|---|
| `PokeReportTheme` | **7 pruebas, 0 fallos.** La que importa comprueba que **la hoja viaja dentro del binario** — si `pokereport.qrc` se cae del CMakeLists, todo sigue compilando y la ventana sale gris |
| `LunaInstance` · `Version` | 9/9 y 62/62 |
| **En la ventana** | ✅ Verificado con una instalación aislada (`--dir`): título limpio, rótulo en la barra, fondo propio, botón principal dorado |

> ⚠️ **Solo lo ve quien no tenga ajustes todavía.** `registerSetting` pone el
> valor **por defecto**: a quien ya haya abierto el launcher se le respeta el
> tema guardado. Es lo correcto —nadie quiere que le cambien la interfaz al
> actualizar— pero significa que **para verlo hay que entrar una vez a Ajustes ▸
> Apariencia**.

## 12. Diagnóstico y reparar (2026-08-23)

Eran **lo único que el fork había perdido** respecto al launcher de Electron que
ya se repartía. Ahora las tiene las dos.

### 12.1 · Por qué existe el diagnóstico

Cuando Minecraft se cae, **lo que ve el jugador es una ventana que desaparece**.
Lo que ve quien da soporte es algo como
`java.util.zip.ZipException: ZipFile invalid LOC header`, dentro de una pila de
netty que **no se parece en nada a la causa** — ese caso concreto ya costó una
sesión entera en este proyecto.

`launcher/luna/LunaDiagnostico.cpp` traduce el cierre a una frase, y **cada regla
es un fallo que ya ha ocurrido de verdad**: jar corrupto, sin memoria, mods que
no encajan, Java equivocado, dos mods que se pisan, la gráfica, la conexión.

> ⚠️ **El registro manda sobre el código de salida.** Un juego puede morir con
> código 1 por cualquier motivo; si el log dice «invalid LOC header», eso es lo
> que pasó y eso es lo que se enseña.

> ⚠️ **Pero el código hace falta igual**, y por eso se plumbeó
> (`LaunchTask::setExitCode`). Los dos cierres de golpe de Windows —`0xC0000005`
> y `0xC0000409`— matan el proceso dentro del controlador de la gráfica o del
> propio sistema, **sin que Java escriba una sola línea**. Sin el código, esos
> dos casos se quedan en «se cerró y no se sabe por qué».

#### ⚠️⚠️ El falso positivo que se hereda, y que es la prueba más valiosa

El patrón de la gráfica llevaba `OpenGL 3.2` **a secas**. Minecraft escribe esa
cadena en una línea de **éxito**:

```
GPU: Intel(R) UHD Graphics 620 (Supports OpenGL 3.2.0 - Build 31.0.101.2135)
```

O sea: **saltaba en cada arranque correcto**.

> **Un diagnóstico que se equivoca siempre es peor que no tenerlo**: enseña a la
> gente a ignorar los avisos, y el día que haya uno de verdad tampoco lo van a
> leer. Hay una prueba dedicada a esa línea exacta.

### 12.2 · Reparar no es «actualizar otra vez»

Y esta distinción es la razón de que sea una acción aparte:

| | |
|---|---|
| **Actualizar** | Se fía del estado guardado (`installed.json`). Un fichero que se corrompió **después** de instalarse sigue apuntado como correcto y **sobrevive a cualquier número de actualizaciones** |
| **Reparar** | No se fía de nada: recalcula el **sha1 de todo lo que hay en disco** |

**El motor ya lo soportaba** — `Luna::Mode::Repair` existe y está probado desde
que se escribió `LunaSync`. Lo que faltaba era el botón.

> ⚠️ **Se avisa de que tarda, y no es cortesía**: son 159 ficheros, algunos de
> decenas de MB. Sin el aviso la barra parece colgada y el jugador cierra el
> launcher a medias.

> ⚠️ **Va al menú y no a la barra.** Es la acción de cuando algo va mal; en la
> barra invitaría a pulsarla sin motivo. Se llega también desde el propio aviso.

### 12.3 · Dónde vive cada cosa

```
luna/LunaDiagnostico.{h,cpp}   la decisión. Función pura, sin interfaz
launch/LaunchTask.h            +setExitCode/exitCode
minecraft/launch/
  LauncherPartLaunch.cpp       lo registra al morir el proceso
Application.cpp                diagnostica y EMITE una señal
ui/MainWindow.cpp              dibuja el aviso y ofrece el botón
```

> ⚠️ **Va por señal a propósito.** `Application` no tiene ventana a la que colgar
> un diálogo, y el botón que a veces hace falta —Reparar— vive en `MainWindow`.
> Allí solo se sabe **qué** pasó; qué hacer con ello es cosa de la interfaz. Y
> `Accion` viaja **como int**: la conexión puede ser en cola, y un enum sin
> registrar en el sistema de metatipos se perdería por el camino **sin dar
> ningún error**.

> ⚠️ **El orden importa en `controllerFinished`**: el diagnóstico se hace
> **antes** de `extras.controller.reset()`, que se lleva por delante la tarea de
> la que sale el código de salida.

### 12.4 · El registro se lee del final

Un log de Minecraft son miles de líneas y **la excepción que mató al juego está
siempre al final**. `colaDelRegistro()` mantiene una ventana de las últimas N
líneas y tira el resto según lee, en vez de cargar el fichero entero.

Y **el botón de abrir la carpeta de registros se ofrece siempre**, también cuando
sabemos la causa: es lo que hay que mandar por Discord si el arreglo no funciona.

## Last Decision

**2026-08-23 · DIAGNÓSTICO Y REPARAR** — el fork deja de estar por detrás del
launcher de Electron (§12). El motor ya sabía reparar (`Mode::Repair`); lo que
faltaba era el botón. El diagnóstico es nuevo y **cada regla es un fallo real**,
con una prueba dedicada al falso positivo de la GPU — porque un diagnóstico que
se equivoca siempre enseña a ignorar los avisos.

**2026-08-23 · TEMA DE LA CASA** — la interfaz del launcher sale del logo
(§11): cinco colores muestreados del arte, QSS como fichero de verdad, rótulo en
la barra y fondo propio en vez del meme de TypeScript que venía de Freesm.
Verificado en la ventana. **La trampa que costó el primer intento**: el recurso
Qt vive en una librería estática y el enlazador se lleva su inicializador — y
ponerlo en `main.cpp` llega tarde, porque el tema se aplica dentro del
constructor de `Application`.

**2026-08-23 · EL BUCLE DE ACTUALIZACIÓN, CERRADO** — tres fallos, y los tres
salían del **mismo carácter**: la `v` de las etiquetas de git (§10). El launcher
ofrecía actualizar a la versión que ya estaba instalada, y al aceptar no
encontraba nada que instalar. `Version::fromTag()` + renombrado del instalador
en la CI + dos guardias nuevas que hacen fallar la publicación en vez de
publicar algo que no se puede instalar. 62 pruebas de `Version`, 0 fallos.
**Sin verificar de punta a punta**: eso pide publicar una `0.2.1`.

**2026-08-21 · v0.2.0 PUBLICADA** — arreglados los tres fallos que hacían
abandonar la instalación (§8) más el `--launch` que se saltaba la sincronización
(§8.6). Toolchain reconstruido tras el formateo (§9). CI verde a la primera que
llegó hasta el final. Commits `def0b0086`, `c588d710d`, `b9bf77a38` en `luna`.

> **Lo que NO quedó demostrado, y conviene no olvidarlo:** el mecanismo de
> reintentos nunca se ejercitó contra un fallo de red real, y el aviso de
> Visual C++ necesita un Windows limpio. Ver §8.7.

**2026-08-18** — motor completo y probado (64 pruebas, 7 piezas). Nada
enchufado a la interfaz todavía. Ver commits `23201dc45` … `c8fd670e7` en la
rama `luna`.

## Next Actions

1. **Probar en un Windows limpio.** Es el único sitio donde se ve §8.3 — el
   fallo del runtime de Visual C++, que es el que dejaba el doble clic sin hacer
   absolutamente nada. El PC de desarrollo ya tiene el runtime y no sirve
2. **Cuando alguien reporte un fallo de descarga, pedirle la captura.** El
   mensaje ahora trae fichero, servidor y código: es la primera vez que un
   informe de un jugador va a servir para diagnosticar, y además es la única
   forma de ver si los reintentos aguantan
3. `-DBUILD_TESTING=OFF` en `luna-release.yml` — 27 enlazados con LTCG de menos
   (§8.8)
4. Medir: cuántos llegan a la plaza sin pedir ayuda. **Ese numero es el que dice
   si esto sirvió de algo**; hasta ahora la única señal era gente que se iba
5. `MARCA-001` — el renombrado a «PokeReport Network». ⚠️ Cambiar el `AppID`
   mueve la carpeta de datos: quien ya lo tenga instalado se encontraría una
   instalación vacía y volvería a bajar 450 MB
6. Diagnóstico y reparar (lo que el de Electron sí tiene)
