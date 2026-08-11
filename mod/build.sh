#!/usr/bin/env bash
# Compila el mod. Usa SIEMPRE este script en vez de ./gradlew directo.
#
# Por qué: existe un ~/.gradle/gradle.properties GLOBAL con
#   org.gradle.java.home = ...jdk-17...
# puesto por el proyecto Backrooms (Minecraft 1.20.1, que sí usa Java 17).
# Minecraft 1.21.1 exige Java 21 y Loom comprueba la JVM de Gradle, no el
# toolchain — así que hay que sobrescribirlo por línea de comandos.
#
# NO se toca el fichero global: rompería el otro proyecto.
set -euo pipefail

# Ajusta esta ruta si cambia el JDK 21 de la máquina.
JDK21_UNIX="/c/Users/JUAN/AppData/Roaming/PrismLauncher/java/java-runtime-delta"
JDK21_WIN='C:\Users\JUAN\AppData\Roaming\PrismLauncher\java\java-runtime-delta'

if [ ! -x "$JDK21_UNIX/bin/javac.exe" ] && [ ! -x "$JDK21_UNIX/bin/javac" ]; then
  echo "ERROR: no hay JDK 21 en $JDK21_UNIX" >&2
  echo "Instala un JDK 21 y actualiza las rutas de este script." >&2
  exit 1
fi

export JAVA_HOME="$JDK21_UNIX"
export PATH="$JAVA_HOME/bin:$PATH"

exec ./gradlew "${@:-build}" \
  --console=plain \
  -Dorg.gradle.java.home="$JDK21_WIN"
