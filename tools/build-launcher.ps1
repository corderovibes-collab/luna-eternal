# Compila el launcher Qt (fork de FreesmLauncher) con el toolchain LOCAL.
#
# ⚠ NADA DE ESTO TOCA EL SISTEMA. Qt y vcpkg viven en `.toolchain/`, que esta
#   git-ignorado, y el JDK global (Java 8 de Red Hat) NO se usa ni se cambia:
#   el launcher se descarga su propio Java en tiempo de ejecucion
#   (`Launcher_ENABLE_JAVA_DOWNLOADER=ON`).
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

Push-Location $SRC
try {
  cmake --preset windows_msvc
  if ($LASTEXITCODE -ne 0) { throw "cmake configure fallo ($LASTEXITCODE)" }
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
