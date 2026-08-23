@echo off
REM ===================================================================
REM  Copia de seguridad automatica de Luna Eternal.
REM  Lo lanza el Programador de tareas de Windows; no se ejecuta a mano
REM  salvo para probar.
REM
REM  Deja registro en backups\backup.log para poder mirar despues si
REM  una noche fallo: una tarea programada que falla en silencio es
REM  exactamente igual de util que no tener copia.
REM ===================================================================
setlocal
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
exit /b %CODIGO%
