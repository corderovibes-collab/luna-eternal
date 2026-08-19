# Compila el launcher Qt (fork de FreesmLauncher) con el toolchain LOCAL.
#
# ⚠ NADA DE ESTO TOCA EL SISTEMA. Qt y vcpkg viven en `.toolchain/`, que esta
#   git-ignorado, y el JDK global (Java 8 de Red Hat) NO se usa ni se cambia:
#   el launcher se descarga su propio Java en tiempo de ejecucion
#   (`Launcher_ENABLE_JAVA_DOWNLOADER=ON`).
param(
  # Compilacion de PUBLICACION: con LTO. Lenta a proposito.
  [switch]$Publicar,

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
  # LunaApply, LunaConfig, lunaeternal (el ejecutable).
  [string]$Solo = ''
)
$ErrorActionPreference = 'Stop'

$TC   = 'D:\pokereportversionmejorada\.toolchain'
$SRC  = 'D:\luna-launcher'
$VS   = 'C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools'
$QTVER= '6.10.2'

# El entorno de MSVC no se hereda: `vcvars64.bat` exporta ~40 variables (INCLUDE,
# LIB, PATH...) y sin ellas el compilador no encuentra ni <stdio.h>. Se ejecuta
# en un cmd hijo y se importan las variables que deja.
cmd /c "`"$VS\VC\Auxiliary\Build\vcvars64.bat`" >nul 2>&1 && set" | ForEach-Object {
  if ($_ -match '^([^=]+)=(.*)$') { Set-Item -Path "env:$($matches[1])" -Value $matches[2] }
}

$env:VCPKG_ROOT       = "$TC\vcpkg"
$env:CMAKE_PREFIX_PATH= "$TC\Qt\6.10.2\msvc2022_64"
$env:ARTIFACT_NAME    = 'luna-launcher-win-x64'
$env:BUILD_PLATFORM   = 'windows-msvc'
$env:PATH             = "$TC\Qt\6.10.2\msvc2022_64\bin;$VS\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;$VS\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja;$env:PATH"

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
  cmake --preset windows_msvc $argLto
  if ($LASTEXITCODE -ne 0) { throw "cmake configure fallo ($LASTEXITCODE)" }
  if ($Solo) {
    cmake --build --preset windows_msvc --config Release --target $Solo
    if ($LASTEXITCODE -ne 0) { throw "cmake build de '$Solo' fallo ($LASTEXITCODE)" }
    "BUILD OK  ·  solo $Solo"
    # Sin ejecutable principal no hay nada que empaquetar, y empaquetar es lo
    # segundo mas lento de todo esto.
    Pop-Location
    return
  }

  cmake --build --preset windows_msvc --config Release
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
} finally { Pop-Location }
