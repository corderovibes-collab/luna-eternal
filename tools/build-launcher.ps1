# Compila el launcher Qt (fork de FreesmLauncher) con el toolchain LOCAL.
#
# ⚠ NADA DE ESTO TOCA EL SISTEMA. Qt y vcpkg viven en `.toolchain/`, que esta
#   git-ignorado, y el JDK global (Java 8 de Red Hat) NO se usa ni se cambia:
#   el launcher se descarga su propio Java en tiempo de ejecucion
#   (`Launcher_ENABLE_JAVA_DOWNLOADER=ON`).
param(
  # Compilacion de PUBLICACION: con LTO. Lenta a proposito.
  [switch]$Publicar,

  # Genera ademas el instalador NSIS. Implica compilacion completa.
  [switch]$Instalador,

  # ⚠ COMPILAR SOLO UN OBJETIVO. Es lo que quita las esperas al desarrollar.
  #
  # Cualquier cambio en `launcher/luna/` obliga a reenlazar los ~20 ejecutables
  # de prueba que trae Prism MAS el .exe de 15 MB. De todos esos, mientras se
  # escribe motor, interesa UNO.
  #
  #   .uild-launcher.ps1 -Solo LunaSync     una prueba, segundos
  #   .uild-launcher.ps1                    todo, minutos
  #
  # Objetivos utiles: LunaManifest, LunaInstance, LunaSync, LunaDownload,
  # LunaApply, LunaConfig, LunaEternal (el ejecutable; ojo a las mayusculas).
  [string]$Solo = '',

  # ⚠ NO COMPILAR LOS 27 EJECUTABLES DE PRUEBA. Es lo que hace utilizable el
  #   ciclo "cambio algo -> quiero el instalador para probarlo".
  #
  # Prism trae 27 objetivos de test, y CADA UNO es un .exe que hay que enlazar
  # contra `Launcher_logic`. Tocar un solo fichero de `launcher/luna/` los
  # reenlaza los 27 aunque no se vayan a ejecutar. Para empaquetar no aportan
  # nada: el instalador no los lleva.
  #
  #   .\build-launcher.ps1 -Instalador -SinPruebas    instalador, minutos
  #   .\build-launcher.ps1                            todo, con pruebas
  #
  # ⚠ Y ENTONCES HAY QUE ACORDARSE DE CORRERLAS. Saltarselas al empaquetar esta
  #   bien; saltarselas SIEMPRE es como no tenerlas. La regla: se compilan y se
  #   pasan cuando se toca `launcher/luna/`, y se omiten en las tandas de
  #   empaquetado posteriores al mismo cambio.
  [switch]$SinPruebas,

  # Cuantas tareas de compilacion a la vez. 0 = se calcula de la RAM.
  #
  # ⚠ NO LO SUBAS "porque tienes nucleos". Lo que se agota enlazando es la
  #   MEMORIA: cada `link.exe` de MSVC pide 1-2 GB, y pasarse cuelga el equipo
  #   entero en vez de dar un error.
  [int]$Trabajos = 0
)
$ErrorActionPreference = 'Stop'

$TC   = 'D:\pokereportversionmejorada\.toolchain'
$SRC  = 'D:\luna-launcher'
$QTVER= '6.10.2'

# ---------------------------------------------------------------- Visual Studio
#
# ⚠ LA RUTA SE BUSCA, NO SE ESCRIBE. Aqui estuvo clavado
#   '...\Microsoft Visual Studio\18\BuildTools' y el 2026-08-21 esa carpeta ya
#   no existia: el toolchain se habia desinstalado de la maquina. El script
#   moria en `cmake : no se reconoce`, un mensaje que apunta a CMake y no a lo
#   que pasaba de verdad --que no habia compilador--, y que ademas es identico
#   al que sale si Visual Studio simplemente esta en otra carpeta.
#
#   El numero de version va DENTRO de la ruta ('18', '2022', ...), asi que
#   reinstalar una edicion distinta a la de quien escribio esta linea rompe la
#   compilacion aunque todo lo demas este bien. Y no es hipotetico: la de este
#   proyecto se reinstalo como 2022, que es ademas la que corresponde al Qt
#   `msvc2022_64` de `.toolchain`.
#
#   `vswhere.exe` es la respuesta oficial de Microsoft a esto: se instala en una
#   ruta FIJA con cualquier edicion --Community, Professional, Build Tools-- y
#   sabe donde estan todas.
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$VS = $null
if (Test-Path $vswhere) {
  # `-products *` incluye Build Tools, que NO sale en la busqueda por defecto.
  # `-requires` filtra las instalaciones sin compilador de C++: una de solo
  # .NET pasaria el filtro y luego no tendria `vcvars64.bat`.
  $VS = & $vswhere -products * -latest -prerelease `
                   -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
                   -property installationPath
}
if (-not $VS) {
  # Sin vswhere queda rastrear a mano. Se ordena descendente para quedarse con
  # la version mas alta ('2022' > '18' alfabeticamente no, pero por fecha de
  # escritura si), y se exige `vcvars64.bat` para no elegir una instalacion
  # incompleta.
  $VS = Get-ChildItem @("${env:ProgramFiles(x86)}\Microsoft Visual Studio", "$env:ProgramFiles\Microsoft Visual Studio") `
          -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Get-ChildItem $_.FullName -Directory -ErrorAction SilentlyContinue } |
        Where-Object { Test-Path (Join-Path $_.FullName 'VC\Auxiliary\Build\vcvars64.bat') } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $VS) {
  throw @"
No se encuentra Visual Studio con el compilador de C++.

Instala Visual Studio Build Tools con la carga de trabajo de C++:
  https://aka.ms/vs/17/release/vs_BuildTools.exe
  --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended

(Sin esto no hay cl.exe, ni CMake, ni Ninja: los tres vienen dentro.)
"@
}
$vcvars = Join-Path $VS 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path $vcvars)) { throw "Visual Studio esta en '$VS' pero le falta vcvars64.bat (instalacion incompleta)" }
Write-Host "Visual Studio: $VS"

# El entorno de MSVC no se hereda: `vcvars64.bat` exporta ~40 variables (INCLUDE,
# LIB, PATH...) y sin ellas el compilador no encuentra ni <stdio.h>. Se ejecuta
# en un cmd hijo y se importan las variables que deja.
cmd /c "`"$vcvars`" >nul 2>&1 && set" | ForEach-Object {
  if ($_ -match '^([^=]+)=(.*)$') { Set-Item -Path "env:$($matches[1])" -Value $matches[2] }
}

$env:VCPKG_ROOT       = "$TC\vcpkg"
$env:CMAKE_PREFIX_PATH= "$TC\Qt\6.10.2\msvc2022_64"
$env:ARTIFACT_NAME    = 'luna-launcher-win-x64'
$env:BUILD_PLATFORM   = 'windows-msvc'
$env:PATH             = "$TC\Qt\$QTVER\msvc2022_64\bin;$VS\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;$VS\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja;$env:PATH"

# ⚠ SE COMPRUEBA QUE LAS HERRAMIENTAS ESTAN, Y SE DICE CUAL FALTA.
#
# Sin esto, faltar cualquiera de las tres da el mismo mensaje de PowerShell --
# "el termino X no se reconoce" -- que suena a que X esta mal escrito y no a que
# la instalacion de Visual Studio no incluyo la carga de C++. Media hora de
# buscar en el sitio equivocado.
foreach ($h in 'cl', 'cmake', 'ninja') {
  if (-not (Get-Command $h -ErrorAction SilentlyContinue)) {
    throw "Falta '$h'. Visual Studio esta en '$VS' pero sin la carga de trabajo de C++ completa (CMake y Ninja vienen con ella)."
  }
}
if (-not (Test-Path "$TC\Qt\$QTVER\msvc2022_64\bin\windeployqt.exe")) {
  throw "Falta Qt $QTVER en '$TC\Qt'. Recuperalo con aqtinstall -- receta en docs/technical/launcher-qt.md §1."
}

# ------------------------------------------------------------------------ JDK
#
# `libraries/launcher` es el trozo JAVA de Prism: el `NewLaunch.jar` que arranca
# Minecraft de verdad. Su CMakeLists hace `project(launcher Java)` y
# `find_package(Java 1.7 REQUIRED COMPONENTS Development)`, asi que sin `javac`
# la CONFIGURACION entera se para -- antes de compilar una linea de C++.
#
# ⚠ TIENE QUE SER JDK 17, NO 21. Prism compila ese jar con `-source 7 -target 7`
#   (libraries/launcher/CMakeLists.txt:7) y JDK 20 elimino el soporte de 7:
#   con un 21 el error es "Source option 7 is no longer supported".
#
# ⚠ Y NO VALE EL JAVA QUE SE BAJA EL LAUNCHER. En
#   %APPDATA%\LunaEternal\java\java-runtime-delta hay un javac 21 --tentador,
#   porque ya esta ahi-- pero ademas de ser la version equivocada, esa carpeta
#   la gestiona el propio launcher: puede borrarla o reemplazarla, y en un
#   equipo recien montado no existe hasta que alguien juega. Una dependencia de
#   compilacion no puede vivir ahi.
#
# Va en `.toolchain\jdk` como todo lo demas: git-ignorado y se borra con la
# carpeta. Se busca el `jdk-*` de dentro en vez de fijar la version, para que
# actualizarlo no obligue a tocar este fichero.
$jdk = Get-ChildItem "$TC\jdk" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
       Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk -or -not (Test-Path "$($jdk.FullName)\bin\javac.exe")) {
  throw @"
Falta el JDK en '$TC\jdk' (hace falta para el NewLaunch.jar de Prism).

Descarga Temurin 17 (zip, NO 21: Prism compila con -source 7) y extraelo ahi:
  https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse
"@
}
$env:JAVA_HOME = $jdk.FullName
$env:PATH      = "$($jdk.FullName)\bin;$env:PATH"
Write-Host "JDK: $($jdk.Name)"

# ⚠ NO LANZAR DOS COMPILACIONES A LA VEZ.
#
# Ninja mantiene abiertos `.ninja_deps` y `.ninja_log`, y una segunda ejecucion
# muere en la CONFIGURACION con:
#
#     ninja: error: failed recompaction: Permission denied
#
# que no menciona por ningun lado que el problema sea que ya hay otra corriendo.
# El enlazado final con LTO tarda ~8 minutos, asi que es facil creer que la
# anterior ya acabo cuando aun le quedan objetivos.
$vivos = @(Get-Process ninja, link, cl -ErrorAction SilentlyContinue)
if ($vivos.Count -gt 0) {
  throw "Ya hay una compilacion en marcha ($($vivos.Count) procesos). Espera a que termine."
}

Push-Location $SRC
try {
  # ⚠ EL LTO SE APAGA PARA DESARROLLAR, Y NO ES UN ATAJO SUCIO.
  #
  # El preset trae ENABLE_LTO=ON: optimiza el binario ENTERO de una pieza en vez
  # de fichero a fichero. Sale un ejecutable mas rapido y mas pequeño, y cuesta
  # ~9 MINUTOS Y 2 GB DE RAM en cada enlazado.
  #
  # Para publicar, merece la pena. Para cambiar tres lineas y ver si compila, es
  # tirar el tiempo: se paga el precio completo por un cambio que no lo
  # necesita.
  #
  #   .uild-launcher.ps1              rapido, sin LTO      <- desarrollar
  #   .uild-launcher.ps1 -Publicar    con LTO              <- publicar
  #
  # ⚠ Lo que se REPARTE tiene que salir de `-Publicar`. Un binario sin LTO
  #   funciona igual, pero es mas grande y algo mas lento, y no es lo que
  #   queremos en la maquina de un jugador.
  $lto = if ($Publicar) { 'ON' } else { 'OFF' }
  Write-Host "LTO: $lto$(if (-not $Publicar) { '  (usa -Publicar para el binario que se reparte)' })"
  # ⚠ EL ARGUMENTO SE MONTA ANTES, Y NO ES UN CAPRICHO DE ESTILO.
  #
  # PowerShell 5.1 NO EXPANDE variables en un argumento suelto que empieza por
  # `-` y lleva `=`:
  #
  #     cmake -DENABLE_LTO=$lto     ->  llega literalmente "-DENABLE_LTO=$lto"
  #     $arg = "-DENABLE_LTO=$lto"  ->  llega "-DENABLE_LTO=ON"
  #
  # Y se disfraza de que funciona: el Write-Host de arriba SI expande --esta
  # entre comillas-- asi que el script decia "LTO: ON" mientras a CMake le
  # llegaba basura. CMake tomaba esa cadena por falsa y apagaba el LTO, o sea
  # que `-Publicar` habria producido EN SILENCIO un binario de publicacion sin
  # optimizar.
  $argLto = "-DENABLE_LTO=$lto"
  # Mismo motivo que `$argLto`: PowerShell 5.1 no expande variables en un
  # argumento suelto que empieza por `-` y lleva `=`.
  $argTests = "-DBUILD_TESTING=$(if ($SinPruebas) { 'OFF' } else { 'ON' })"
  if ($SinPruebas) { Write-Host "Pruebas: NO se compilan (27 ejecutables menos)" }

  # ------------------------------------------------------------ PARALELISMO
  #
  # ⚠⚠⚠ SIN ESTE TOPE, COMPILAR ESTO TUMBA EL PC. No es una exageracion: paso
  #     el 2026-08-23 y hubo que apagar a lo bruto.
  #
  #     Ninja lanza por defecto tantas tareas como nucleos. Al llegar a los
  #     ejecutables de PRUEBA --27, cada uno enlazado por separado-- eso son 17
  #     `link.exe` a la vez, y el enlazador de MSVC se come 1-2 GB CADA UNO.
  #     Diecisiete por dos son 34 GB en una maquina de 13,7: el sistema se
  #     queda sin memoria y se lleva por delante lo que pillo.
  #
  #     Y NO SE MANIFIESTA COMO UN ERROR DE COMPILACION. Se manifiesta como
  #     "esto va lentisimo" y despues como un PC congelado, que no manda a
  #     nadie a mirar los ajustes del build.
  #
  # ⚠ Se calcula de la RAM TOTAL y no de los nucleos: lo que se agota es la
  #   memoria, no la CPU. Un cuarto de la RAM en GB deja ~4 GB de margen para
  #   Windows y para lo que el usuario tenga abierto -- que en esa maquina era
  #   el navegador, Minecraft y el propio launcher.
  if ($Trabajos -le 0) {
    $ramGB = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
    $Trabajos = [math]::Max(2, [math]::Min([Environment]::ProcessorCount, [math]::Floor($ramGB / 4)))
    Write-Host ("Paralelismo: {0} tareas  (RAM {1} GB, {2} nucleos)" -f $Trabajos, $ramGB, [Environment]::ProcessorCount)
  } else {
    Write-Host "Paralelismo: $Trabajos tareas (forzado)"
  }
  $argJ = "-j$Trabajos"

  # ⚠ LAS VARIABLES `CACHE` DE CMAKE NO SE PISAN DESDE EL CMakeLists.
  #
  # Cambiar un `set(... CACHE ...)` en el fichero NO cambia nada si ya hay valor
  # guardado: CMake conserva el de la cache. Se descubrio con
  # `Launcher_UPDATER_GITHUB_REPO`, que se dejo vacio en el fuente y seguia
  # apuntando al repositorio viejo -- compilaba "bien" y el comportamiento no
  # cambiaba.
  #
  # Las que tienen que mandar desde el fuente se repiten aqui.
  cmake --preset windows_msvc $argLto $argTests "-DLauncher_UPDATER_GITHUB_REPO=https://github.com/corderovibes-collab/luna-eternal-launcher"
  if ($LASTEXITCODE -ne 0) { throw "cmake configure fallo ($LASTEXITCODE)" }
  if ($Solo) {
    cmake --build --preset windows_msvc --config Release $argJ --target $Solo
    if ($LASTEXITCODE -ne 0) { throw "cmake build de '$Solo' fallo ($LASTEXITCODE)" }
    "BUILD OK  ·  solo $Solo"
    # Sin ejecutable principal no hay nada que empaquetar, y empaquetar es lo
    # segundo mas lento de todo esto.
    Pop-Location
    return
  }

  cmake --build --preset windows_msvc --config Release $argJ
  if ($LASTEXITCODE -ne 0) { throw "cmake build fallo ($LASTEXITCODE)" }
  'BUILD OK'

  # ------------------------------------------------------------------ Qt
  # SIN ESTE PASO EL .exe NO ARRANCA EN OTRO PC.
  #
  # El build deja las 10 DLL de vcpkg (archive, cmark, qrencode...) pero
  # NINGUNA de Qt: aqui funciona solo porque el `bin` de Qt esta en el PATH de
  # esta sesion. En la maquina de un jugador eso no pasa, y el sintoma es el
  # peor posible -- un doble clic que no hace absolutamente nada, sin ventana y
  # sin mensaje, porque Windows no encuentra Qt6Core.dll y se rinde en silencio.
  #
  # `windeployqt` copia las DLL de Qt Y sus plugins (plataforma, estilos,
  # formatos de imagen), que se cargan en tiempo de ejecucion y por eso ningun
  # analisis de dependencias del .exe los encuentra.
  $exe = Join-Path $SRC 'build\Release\lunaeternal.exe'
  if (-not (Test-Path $exe)) { throw "no hay lunaeternal.exe que empaquetar" }
  $wdq = Join-Path $TC (Join-Path "Qt" (Join-Path $QTVER (Join-Path "msvc2022_64" (Join-Path "bin" "windeployqt.exe"))))
  & $wdq --release --no-translations --no-system-d3d-compiler $exe
  if ($LASTEXITCODE -ne 0) { throw "windeployqt fallo ($LASTEXITCODE)" }

  $n = (Get-ChildItem (Split-Path $exe) -Filter *.dll).Count
  "EMPAQUETADO OK  ·  $n DLL junto al ejecutable"

  if ($Instalador) {
    # ⚠ `cmake --install` PRIMERO, y no se empaqueta build/Release directamente.
    #
    # Esa carpeta tiene ademas los ~20 ejecutables de prueba de Prism, los .lib
    # y los .pdb. `--install` copia SOLO lo que hay que repartir, siguiendo las
    # reglas del propio CMakeLists.
    $inst = Join-Path $SRC 'install'
    if (Test-Path $inst) { Remove-Item $inst -Recurse -Force }
    cmake --install (Join-Path $SRC 'build') --prefix $inst --config Release
    if ($LASTEXITCODE -ne 0) { throw "cmake --install fallo ($LASTEXITCODE)" }

    # NSIS: primero el del toolchain --que es portable y va con el proyecto-- y
    # solo despues el instalado en el sistema, si alguien lo tiene.
    #
    # ⚠ EL MENSAJE DE ANTES DECIA `winget install --id NSIS.NSIS`. En el equipo
    #   recien formateado del 2026-08-21 NO HABIA WINGET, asi que la unica
    #   instruccion que daba el error tampoco se podia seguir.
    $mk = @(Get-ChildItem "$TC\nsis" -Recurse -Filter makensis.exe -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty FullName) +
          @("${env:ProgramFiles(x86)}\NSIS\makensis.exe", "$env:ProgramFiles\NSIS\makensis.exe") |
          Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $mk) {
      throw @"
Falta NSIS (hace falta para generar el instalador).

Bajalo en zip --no necesita instalarse-- y extraelo en '$TC\nsis':
  https://downloads.sourceforge.net/project/nsis/NSIS%203/3.11/nsis-3.11.zip

⚠ Descargalo con curl, NO con Invoke-WebRequest: el PowerShell 5.1 de Windows
  se trae una pagina de SourceForge de 120 KB en vez del zip, y el fallo
  aparece luego como "no se encuentra el registro de fin de directorio central".
"@
    }
    Write-Host "NSIS: $mk"

    # ⚠ HAY QUE ESTAR DENTRO de la carpeta de instalacion: el .nsi coge los
    #   ficheros por ruta RELATIVA, y `-NOCD` le dice a makensis que no se mueva
    #   al directorio del script. Lanzandolo desde otro sitio, el instalador
    #   sale vacio SIN dar error.
    Push-Location $inst
    try {
      & $mk -NOCD (Join-Path $SRC 'build\program_info\win_install.nsi')
      if ($LASTEXITCODE -ne 0) { throw "makensis fallo ($LASTEXITCODE)" }
    } finally { Pop-Location }

    # ⚠ EL INSTALADOR NO SALE EN LA CARPETA DE INSTALACION.
    #
    # El `.nsi` lo escribe en la RAIZ del proyecto ($SRC), no donde estan los
    # ficheros que empaqueta. Buscarlo en `install/` daba "makensis termino
    # pero no dejo instalador" con el instalador ya creado a 27 MB -- un error
    # que decia justo lo contrario de lo que pasaba.
    $setup = Get-ChildItem $SRC -Filter "*Setup*.exe" -File | Select-Object -First 1
    if ($setup) { "INSTALADOR OK  ·  $($setup.Name)  $([math]::Round($setup.Length/1MB,1)) MB" }
    else { throw "makensis termino pero no dejo instalador" }
  }
} finally { Pop-Location }
