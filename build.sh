#!/bin/sh
# Compila el aplicativo y genera build/sihca.jar (requiere JDK 11 o superior)
set -e
cd "$(dirname "$0")"
rm -rf build/clases && mkdir -p build/clases
javac --release 11 -encoding UTF-8 -d build/clases -cp lib/mysql-connector-j-8.4.0.jar $(find src -name "*.java")
mkdir -p build/clases/fuentes build/clases/sql
cp recursos/fuentes/*.ttf build/clases/fuentes/
cp sql/script_completo_bd_horarios_esclavas.sql build/clases/sql/
jar --create --file build/sihca.jar --manifest recursos/MANIFEST.MF -C build/clases .
echo "Listo: build/sihca.jar"
