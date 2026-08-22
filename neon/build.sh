#!/usr/bin/env bash
# Compila el mod. Usa SIEMPRE este script en vez de ./gradlew directo.
#
# Por que: existe un ~/.gradle/gradle.properties GLOBAL con
#   org.gradle.java.home = ...jdk-17...
# puesto por el proyecto Backrooms (Minecraft 1.20.1, que si usa Java 17).
# Minecraft 1.21.1 exige Java 21 y Loom comprueba la JVM de Gradle, no el
# toolchain -- asi que hay que sobrescribirlo por linea de comandos.
#
# NO se toca el fichero global: romperia el otro proyecto.
set -euo pipefail

# La busqueda del JDK esta en tools/jdk21.sh porque estaba DUPLICADA aqui y en
# el otro build.sh, y por eso el mismo fallo se arreglo dos veces.
. "$(cd "$(dirname "$0")/../tools" && pwd)/jdk21.sh"

exec ./gradlew "${@:-build}" \
  --console=plain \
  -Dorg.gradle.java.home="$JDK21_WIN"
