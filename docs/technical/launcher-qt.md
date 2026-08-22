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

### ⏰ PENDIENTE: el servidor pasa a llamarse **PokeReport Network**

**Decisión del usuario, 2026-08-20.** «Luna Eternal» deja de ser el nombre del
servidor. Está anotado aquí y **no aplicado a propósito**: recompilar el fork
entero cuesta mucho, y no se hace de madrugada justo antes de repartir la
primera versión.

Cuando toque, hay que mirar **todos** estos sitios — es la lección de esta
sesión: la marca vive en más lugares de los que uno recuerda.

| Dónde | Qué |
|---|---|
| `program_info/CMakeLists.txt` | `Launcher_CommonName`, `Launcher_DisplayName`, `Launcher_AppID`, `Launcher_Domain`, `Launcher_Authors` |
| `CMakeLists.txt` (raíz) | `Launcher_APP_BINARY_NAME` |
| `program_info/*` | 18 ficheros con el nombre dentro (`lunaeternal.*`, `net.pokereport.LunaEternal.*`) |
| `launcher/luna/LunaInstance.cpp` | `instanceName()` — el nombre de la instancia |
| `launcher/main.cpp` | `Q_INIT_RESOURCE(lunaeternal)` |
| `tests/LunaInstance_test.cpp` | fija el nombre de la instancia |
| `luna-release.yml` | nada, usa `${{ github.repository }}` |

> ⚠️ **Cambiar `Launcher_AppID` mueve la carpeta de datos.** Quien ya tenga el
> launcher instalado se encontraría con una instalación «vacía» y volvería a
> bajar los 450 MB. Si hay gente usándolo, hay que migrar o avisar.

> ⚠️ **Y el nombre de la instancia es cómo `findInstance()` la encuentra.**
> Cambiarlo sin más crea una segunda instancia al lado en vez de renombrar la
> existente.

Afecta también, fuera del fork: el nombre visible del servidor en `servers.dat`
(lo genera `gen_modpack.py`), el launcher de Electron, y el propio mod.

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
| **Los reintentos, contra un fallo real** | ❌ **NO**. No hubo nada que descargar, así que `FileTask` no bajó ni un byte |
| **El aviso de Visual C++** | ❌ **NO**. Este PC ya tiene el runtime; hace falta un Windows limpio |

> ⚠️ **El arreglo del 96 % sigue sin demostrarse.** Está escrito, compilado y en
> el binario, pero hasta que una descarga falle de verdad y se vea aguantar, es
> una hipótesis bien fundada — no un hecho. La prueba barata: quitar 3 mods de
> la instancia y darle a Jugar.

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

## Last Decision

**2026-08-21** — arreglados los tres fallos que hacían abandonar la
instalación (§8): reintentos en las descargas del pack, errores que nombran el
fichero y la causa, y el runtime de Visual C++ empaquetado y comprobado.
**Escrito y revisado, sin compilar**: ver §9.

**2026-08-18** — motor completo y probado (64 pruebas, 7 piezas). Nada
enchufado a la interfaz todavía. Ver commits `23201dc45` … `c8fd670e7` en la
rama `luna`.

## Next Actions

1. **Reiniciar el equipo** para que se apliquen `KB5066790`/`KB5066791` (§9) —
   sin eso vcpkg no arranca y no se puede compilar nada
2. Compilar: `powershell tools/build-launcher.ps1` (sin LTO, rápido, para ver
   si el código de §8 compila) y luego `-Publicar -Instalador`
3. Verificar en un Windows **limpio**, que es el único sitio donde se ve §8.3
4. Reinstalar Python — `tools/*.py` no corren (§9)
5. Publicar y medir: cuántos llegan a la plaza sin pedir ayuda
6. Diagnóstico y reparar (lo que el de Electron sí tiene)
