@echo off
REM ======================================================================
REM  Compila SIHCA y genera build\sihca.jar  (requiere JDK 11+ en el PATH)
REM ======================================================================
setlocal
cd /d "%~dp0"
if exist build\clases rmdir /s /q build\clases
mkdir build\clases\fuentes build\clases\sql
dir /s /b src\*.java > build\fuentes.txt
javac --release 11 -encoding UTF-8 -d build\clases -cp lib\mysql-connector-j-8.4.0.jar @build\fuentes.txt
if errorlevel 1 goto error
copy /y recursos\fuentes\*.ttf build\clases\fuentes\ >nul
copy /y sql\script_completo_bd_horarios_esclavas.sql build\clases\sql\ >nul
jar --create --file build\sihca.jar --manifest recursos\MANIFEST.MF -C build\clases .
if errorlevel 1 goto error
echo Listo: build\sihca.jar
echo Para ejecutar:  java -jar build\sihca.jar   (copie lib\ junto al jar como build\lib\)
goto fin
:error
echo Error de compilacion.
:fin
endlocal
