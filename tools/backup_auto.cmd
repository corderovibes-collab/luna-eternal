@echo off
REM ===================================================================
REM  Copia de seguridad automatica de Luna Eternal.
REM  Lo lanza el Programador de tareas de Windows.
REM
REM  Deja registro en backups\backup.log: una tarea programada que
REM  falla en silencio es igual de util que no tener copia.
REM ===================================================================
setlocal

REM ⚠⚠ ESTAS DOS LINEAS NO SON DECORACION. SIN ELLAS LA TAREA FALLA.
REM
REM  Al ejecutarse desde el Programador, la salida va a un fichero y
REM  Python usa la codificacion del sistema (cp1252 en un Windows en
REM  espanol). En cuanto una linea lleva un caracter que cp1252 no
REM  tiene --y el script imprime "⚠" y "·"-- revienta con
REM  UnicodeEncodeError y NO SE HACE LA COPIA.
REM
REM  Lo peor es que a mano funciona perfectamente: quien lo pruebe
REM  desde una terminal no vera nunca el fallo. Se descubrio
REM  disparando la tarea de verdad, no ejecutando el script.
chcp 65001 >nul
set PYTHONIOENCODING=utf-8

set RAIZ=D:\pokereportversionmejorada
set PY=%RAIZ%\.toolchain\python\python.exe
set LOG=%RAIZ%\backups\backup.log

cd /d "%RAIZ%"
if not exist "%RAIZ%\backups" mkdir "%RAIZ%\backups"

echo. >> "%LOG%"
echo ================================================== >> "%LOG%"
echo INICIO %DATE% %TIME% >> "%LOG%"
"%PY%" "%RAIZ%\tools\backup_auto.py" >> "%LOG%" 2>&1
set CODIGO=%ERRORLEVEL%
echo FIN %DATE% %TIME%  codigo=%CODIGO% >> "%LOG%"

if not "%CODIGO%"=="0" (
  echo. >> "%LOG%"
  echo *** LA COPIA FALLO. Revisa el error de aqui arriba. *** >> "%LOG%"
)
exit /b %CODIGO%
