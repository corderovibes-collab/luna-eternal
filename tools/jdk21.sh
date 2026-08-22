# Encuentra un JDK 21 y exporta JAVA_HOME, PATH y JDK21_WIN.
#
# Se hace con `source`, no ejecutando: quien lo llama necesita las variables.
#   . "$(dirname "$0")/../tools/jdk21.sh"
#
# ⚠ EL JDK SE BUSCA, NO SE ESCRIBE. Estuvo clavado en dos sitios --mod/build.sh
#   y neon/build.sh-- con el numero de revision dentro
#   ("jdk-21.0.12.101-hotspot", y el otro apuntando al JDK de PrismLauncher).
#   El 2026-08-20 se formateo el equipo, las dos carpetas dejaron de existir, y
#   ninguno de los dos mods se podia compilar. El error decia "no hay JDK 21 en
#   <ruta>", que suena a que falta Java cuando lo que faltaba era ESA revision.
#
#   Estaba DUPLICADO, y por eso se arreglo dos veces con seis dias de por medio:
#   el de `mod` en cuanto fallo, y el de `neon` solo cuando toco compilarlo. Un
#   mismo fallo en dos ficheros se arregla una vez y sigue vivo en el otro.
#
# ⚠ TIENE QUE SER 21, NO 17. El launcher Qt usa el 17 --Prism compila su
#   NewLaunch.jar con `-source 7`, que el JDK 20 elimino-- y Minecraft 1.21.1
#   exige el 21. NO son intercambiables y hacen falta los dos.

_luna_jdk21_raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

_luna_jdk21_candidatos=()
for _d in "$_luna_jdk21_raiz/.toolchain/jdk21"/jdk-21* \
          "/c/Program Files/Eclipse Adoptium"/jdk-21* \
          "/c/Program Files/Java"/jdk-21*; do
  [ -d "$_d" ] && _luna_jdk21_candidatos+=("$_d")
done

JDK21_UNIX=""
for _d in "${_luna_jdk21_candidatos[@]:-}"; do
  if [ -x "$_d/bin/javac.exe" ] || [ -x "$_d/bin/javac" ]; then
    JDK21_UNIX="$(cd "$_d" && pwd)"
    break
  fi
done

if [ -z "$JDK21_UNIX" ]; then
  cat >&2 <<'AYUDA'
ERROR: no se encuentra ningun JDK 21.

Minecraft 1.21.1 lo exige, y NO vale el 17 que usa el launcher Qt (ese esta ahi
porque Prism compila su NewLaunch.jar con -source 7). Hacen falta los dos.

  winget install EclipseAdoptium.Temurin.21.JDK

...o bajalo en zip y extraelo en .toolchain/jdk21:
  https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse

⚠ winget modifica el PATH pero NO el shell ya abierto: abre otra terminal.
AYUDA
  return 1 2>/dev/null || exit 1
fi

# Gradle en Windows necesita la ruta con barras invertidas; el shell, con barras
# normales. Se DERIVA la una de la otra en vez de mantener dos constantes que
# pueden desincronizarse -- que es como estaban antes.
# ⚠ NADA DE `sed` AQUI, y no es preferencia de estilo.
#
#   La expresion era  sed 's#^/\([a-zA-Z]\)/#\U\1:\#; s#/#\#g'  y tiene que
#   sobrevivir intacta a un heredoc, a git y a cualquier editor. No lo hizo:
#   al reescribir el fichero se colapsaron las barras dobles y el script murio
#   con  sed: unknown option to `s'  -- que no dice nada de rutas ni de Java.
#
#   Se hace con expansion de bash y un solo `tr`, que no necesita escapar nada.
_luna_letra="${JDK21_UNIX:1:1}"      # /d/... -> d
_luna_resto="${JDK21_UNIX:2}"        # /d/foo -> /foo
JDK21_WIN="$(printf '%s' "$_luna_letra" | tr 'a-z' 'A-Z'):$(printf '%s' "$_luna_resto" | tr '/' '\134')"    # \134 = barra invertida
unset _luna_letra _luna_resto

export JAVA_HOME="$JDK21_UNIX"
export PATH="$JAVA_HOME/bin:$PATH"
echo "JDK 21: $JDK21_UNIX"

unset _luna_jdk21_raiz _luna_jdk21_candidatos _d
