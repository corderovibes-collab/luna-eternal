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

# ⚠ EL JDK SE BUSCA, NO SE ESCRIBE.
#
#   Aquí estaba clavado "/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot",
#   con el número de revisión dentro. El 2026-08-20 se formateó el equipo, esa
#   carpeta dejó de existir, y el mod no se podía compilar: el error decía "no
#   hay JDK 21 en <ruta>", que suena a que falta Java cuando lo que faltaba era
#   ESA revisión concreta. Actualizar el JDK rompía el script igual.
#
#   Se mira primero en `.toolchain/jdk21`, que es donde vive el resto del
#   utillaje del proyecto (Qt, vcpkg, NSIS, Python) y va git-ignorado; después,
#   cualquier Adoptium instalado en el sistema. Y se acepta cualquier revisión,
#   que es lo que hacía falta desde el principio.
BUSCAR=()
for d in "$(dirname "$0")/../.toolchain/jdk21"/jdk-21* \
         "/c/Program Files/Eclipse Adoptium"/jdk-21* \
         "/c/Program Files/Java"/jdk-21*; do
  [ -d "$d" ] && BUSCAR+=("$d")
done

JDK21_UNIX=""
for d in "${BUSCAR[@]:-}"; do
  if [ -x "$d/bin/javac.exe" ] || [ -x "$d/bin/javac" ]; then
    JDK21_UNIX="$(cd "$d" && pwd)"
    break
  fi
done

if [ -z "$JDK21_UNIX" ]; then
  cat >&2 <<'FIN'
ERROR: no se encuentra ningún JDK 21.

Minecraft 1.21.1 lo exige, y NO vale el 17 que usa el launcher Qt (ese está ahí
porque Prism compila su NewLaunch.jar con -source 7). Hacen falta los dos.

Bájalo en zip y extráelo en .toolchain/jdk21:
  https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse
FIN
  exit 1
fi

# Gradle en Windows necesita la ruta con barras invertidas; el shell, con barras
# normales. Se derivan la una de la otra en vez de mantener dos constantes que
# pueden desincronizarse -- que es como estaban antes.
JDK21_WIN="$(printf '%s' "$JDK21_UNIX" | sed 's#^/\([a-zA-Z]\)/#\U\1:\\#; s#/#\\#g')"
echo "JDK 21: $JDK21_UNIX"

export JAVA_HOME="$JDK21_UNIX"
export PATH="$JAVA_HOME/bin:$PATH"

exec ./gradlew "${@:-build}" \
  --console=plain \
  -Dorg.gradle.java.home="$JDK21_WIN"
