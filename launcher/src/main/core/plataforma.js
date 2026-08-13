import os from 'node:os';
import path from 'node:path';

/**
 * Todo lo que cambia entre sistemas operativos, en un solo sitio.
 *
 * El resto del núcleo no debería preguntar por `process.platform` nunca: si lo
 * hace, es que falta algo aquí. Tenerlo repartido fue justo lo que dejó el
 * launcher clavado a Windows sin que se notara hasta intentar sacarlo de ahí.
 */

export const ES_WINDOWS = process.platform === 'win32';
export const ES_MAC = process.platform === 'darwin';
export const ES_LINUX = process.platform === 'linux';

/**
 * Nombre del sistema tal y como lo escribe Mojang en sus reglas.
 *
 * Comprobado contra el JSON de versión de 1.21.1: los únicos valores que
 * aparecen son `windows`, `osx` y `linux`. Ojo con `osx` — no es `mac` ni
 * `macos`.
 */
export const OS_MOJANG = ES_MAC ? 'osx' : ES_WINDOWS ? 'windows' : 'linux';

/** Arquitectura como la nombra Mojang en `rules[].os.arch`. */
export const ARCH_MOJANG = { x64: 'x86_64', ia32: 'x86', arm64: 'arm64' }[process.arch] ?? process.arch;

/** Arquitectura como la nombra Adoptium en su API. */
export const ARCH_ADOPTIUM = { x64: 'x64', arm64: 'aarch64', ia32: 'x86' }[process.arch] ?? null;

/**
 * Clasificadores de natives válidos para esta máquina.
 *
 * ESTO ES LO QUE MÁS DUELE SI SE HACE MAL.
 *
 * Mojang le pone **la misma regla** a todas las variantes de arquitectura:
 *
 *     org.lwjgl:lwjgl-glfw:3.3.3:natives-macos        -> allow os=osx
 *     org.lwjgl:lwjgl-glfw:3.3.3:natives-macos-arm64  -> allow os=osx
 *
 * O sea que evaluar `rules` no basta: en un Mac las dos pasan el filtro, se
 * extraen las dos en la misma carpeta con los mismos nombres de fichero, y
 * gana la última. En Intel puede que funcione de casualidad; en Apple Silicon
 * carga los `.dylib` equivocados y el juego no abre.
 *
 * Por eso hay que mirar el sufijo del nombre Maven, no solo las reglas.
 */
function clasificadoresValidos() {
  if (ES_MAC) {
    // `natives-macos-patch` es el parche de freetype de LWJGL: va en las dos
    // arquitecturas, no es una variante alternativa.
    return process.arch === 'arm64'
      ? ['natives-macos-arm64', 'natives-macos-patch']
      : ['natives-macos', 'natives-macos-patch'];
  }
  if (ES_WINDOWS) {
    if (process.arch === 'arm64') return ['natives-windows-arm64'];
    if (process.arch === 'ia32') return ['natives-windows-x86'];
    return ['natives-windows'];
  }
  return ['natives-linux'];
}

const VALIDOS = clasificadoresValidos();

/**
 * ¿Es este `natives-*` el de nuestra máquina?
 *
 * Se compara el sufijo completo para que `natives-macos` no case con
 * `natives-macos-arm64`, que es exactamente el error que se quiere evitar.
 */
export function nativoDeEstaMaquina(nombreMaven = '') {
  const clasificador = nombreMaven.split(':')[3];
  if (!clasificador) return false;
  return VALIDOS.includes(clasificador);
}

/** ¿El nombre Maven se refiere a unos natives, sean de quien sean? */
export function esNativo(nombreMaven = '') {
  return /:natives-/.test(nombreMaven);
}

// --- rutas ----------------------------------------------------------------

/**
 * Raíz de datos del launcher, siguiendo la convención de cada sistema.
 *
 *   Windows   %APPDATA%\.lunaeternal
 *   macOS     ~/Library/Application Support/lunaeternal
 *   Linux     ~/.local/share/lunaeternal   (XDG)
 *
 * En Mac y Linux se quita el punto del nombre: allí la carpeta está en un sitio
 * que ya es "de aplicaciones", y esconderla solo complica dar soporte.
 *
 * El nombre es propio y NO se comparte con el launcher del proyecto anterior
 * (`.cobbleverse`): son dos servidores distintos, con mods y versiones
 * distintas, y compartir carpeta significaría que instalar uno rompe el otro.
 */
export function raizDatos() {
  if (ES_WINDOWS) {
    const appData = process.env.APPDATA ?? path.join(os.homedir(), 'AppData', 'Roaming');
    return path.join(appData, '.lunaeternal');
  }
  if (ES_MAC) {
    return path.join(os.homedir(), 'Library', 'Application Support', 'lunaeternal');
  }
  const xdg = process.env.XDG_DATA_HOME ?? path.join(os.homedir(), '.local', 'share');
  return path.join(xdg, 'lunaeternal');
}

// --- Java -----------------------------------------------------------------

/**
 * Ejecutable de Java sin consola.
 *
 * `javaw` **solo existe en Windows**. En Mac y Linux se usa `java` a secas: no
 * abre ninguna ventana de terminal, así que hace el mismo papel.
 */
export function javaSinConsola(home) {
  return path.join(home, 'bin', ES_WINDOWS ? 'javaw.exe' : 'java');
}

/** Ejecutable de Java con salida por consola, para preguntarle la versión. */
export function javaConConsola(home) {
  return path.join(home, 'bin', ES_WINDOWS ? 'java.exe' : 'java');
}

/**
 * Dónde queda el JAVA_HOME dentro de lo que descarga Adoptium.
 *
 * En Mac los paquetes vienen empaquetados como un bundle de macOS y el home
 * real cuelga de `Contents/Home`. En Windows y Linux es la carpeta tal cual.
 */
export function homeDentroDelPaquete(dir) {
  return ES_MAC ? path.join(dir, 'Contents', 'Home') : dir;
}

/** Sistema y extensión que hay que pedirle a la API de Adoptium. */
export function adoptium() {
  return {
    os: ES_MAC ? 'mac' : ES_WINDOWS ? 'windows' : 'linux',
    // Adoptium publica .zip para Windows y .tar.gz para Mac y Linux.
    comprimido: ES_WINDOWS ? 'zip' : 'tar.gz',
  };
}

// --- lanzamiento ----------------------------------------------------------

/**
 * Flags de JVM propias del sistema.
 *
 * En macOS **no hace falta añadir `-XstartOnFirstThread` a mano**: el propio
 * JSON de versión de Mojang lo trae bajo una regla `os=osx`, así que aparece
 * solo en cuanto las reglas se evalúan bien. Ponerlo aquí además lo duplicaría.
 *
 * Lo que sí conviene es el nombre de la aplicación, que es lo que se ve en el
 * Dock y en la barra de menús mientras se juega.
 */
export function flagsDeSistema() {
  return ES_MAC ? ['-Dapple.awt.application.name=Luna Eternal'] : [];
}
