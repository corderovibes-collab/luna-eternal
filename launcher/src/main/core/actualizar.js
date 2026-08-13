import { app } from 'electron';
import electronUpdater from 'electron-updater';

import { ES_MAC } from './plataforma.js';

/**
 * Autoactualización del propio launcher.
 *
 * ESTO ES LO QUE HACE QUE NO HAYA QUE REPARTIR UN .EXE NUNCA MÁS.
 *
 * El pack ya se actualizaba solo (manifiesto + SHA1). Lo que NO se actualizaba
 * era el launcher: cada arreglo obligaba a mandarle el instalador a todo el
 * mundo por Discord y a rezar para que lo instalaran. Con esto, el instalador
 * se reparte **una vez** y a partir de ahí el launcher se pone al día él solo.
 *
 * Cómo funciona
 * -------------
 * `electron-builder` publica en cada release un `latest.yml` con la versión, el
 * SHA512 del instalador y su `.blockmap`. El launcher lee ese YAML, compara con
 * `app.getVersion()` y, si hay novedad, descarga **solo los bloques que
 * cambian** gracias al blockmap: una actualización típica son unos pocos MB en
 * vez de los ~95 MB del instalador entero.
 *
 * Decisiones que conviene conocer
 * -------------------------------
 * - **Nunca bloquea el juego.** Si GitHub no responde, o el YAML no está, o la
 *   descarga falla, se registra y se sigue. Un launcher que no deja jugar
 *   porque no pudo comprobar su propia versión es peor que uno desactualizado.
 * - **No se instala a traición.** `autoInstallOnAppQuit` queda activo, así que
 *   la actualización entra al cerrar; y hay un botón para hacerlo ya. Reiniciar
 *   el launcher en mitad de una descarga de 900 MB sería una crueldad.
 * - **En macOS solo avisa.** `electron-updater` exige firma de código para
 *   instalar en Mac, y este launcher no va firmado (es privado). Sin firma, la
 *   actualización se descarga y falla al aplicarse, que es peor que no
 *   intentarlo: en Mac se abre la página de descargas y ya.
 * - **En desarrollo no hace nada.** Sin empaquetar no hay `app-update.yml`, y
 *   electron-updater lanza un error feo. `app.isPackaged` lo corta antes.
 */

// electron-updater es CommonJS. Con "type": "module" en package.json, el
// import nombrado NO funciona: hay que sacar el default y desestructurar.
// El síntoma si se hace mal es "SyntaxError: Named export 'autoUpdater' not
// found", y ocurre solo en el build empaquetado.
const { autoUpdater } = electronUpdater;

/** Cada cuánto se vuelve a mirar, con el launcher abierto. */
const CADA = 30 * 60 * 1000;

/** Página de descargas, para el caso de macOS y para el enlace de «ver notas». */
export const PAGINA_DESCARGAS =
  'https://github.com/corderovibes-collab/luna-eternal-pack/releases/latest';

let estado = { fase: 'inactivo', version: null, error: null, progreso: 0 };
let avisar = () => {};

export const estadoActualizacion = () => estado;

function cambiar(parche) {
  estado = { ...estado, ...parche };
  avisar(estado);
}

/**
 * Arranca la vigilancia. `onEstado` recibe cada cambio para pintarlo.
 *
 * Devuelve siempre, incluso si no hay nada que vigilar: el resto del launcher
 * no tiene que saber si está empaquetado o no.
 */
export function vigilarActualizaciones(onEstado) {
  avisar = onEstado ?? (() => {});

  if (!app.isPackaged) {
    cambiar({ fase: 'desarrollo' });
    return { comprobar: async () => estado, instalar: () => false };
  }

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  // Los `-beta` solo llegan a quien ya corre una beta. Un jugador en estable
  // nunca recibe algo a medio probar.
  autoUpdater.allowPrerelease = /-(alpha|beta|rc)/.test(app.getVersion());
  autoUpdater.logger = null;

  autoUpdater.on('checking-for-update', () => cambiar({ fase: 'comprobando', error: null }));
  autoUpdater.on('update-not-available', () => cambiar({ fase: 'al-dia' }));
  autoUpdater.on('update-available', (info) =>
    cambiar({ fase: ES_MAC ? 'disponible-mac' : 'descargando', version: info?.version ?? null }));
  autoUpdater.on('download-progress', (p) =>
    cambiar({ fase: 'descargando', progreso: (p?.percent ?? 0) / 100 }));
  autoUpdater.on('update-downloaded', (info) =>
    cambiar({ fase: 'lista', version: info?.version ?? null, progreso: 1 }));
  autoUpdater.on('error', (err) =>
    // Se registra y se sigue: comprobar la versión no es motivo para no jugar.
    cambiar({ fase: 'error', error: err?.message ?? String(err) }));

  const comprobar = async () => {
    try {
      // En Mac no se descarga: se avisa y se manda a la página. Descargar algo
      // que no se va a poder instalar solo gasta datos y confunde.
      if (ES_MAC) await autoUpdater.checkForUpdates();
      else await autoUpdater.checkForUpdatesAndNotify();
    } catch (err) {
      cambiar({ fase: 'error', error: err?.message ?? String(err) });
    }
    return estado;
  };

  comprobar();
  const temporizador = setInterval(comprobar, CADA);
  app.on('before-quit', () => clearInterval(temporizador));

  return {
    comprobar,
    /** Cierra y aplica. Solo tiene sentido si la fase es `lista`. */
    instalar: () => {
      if (estado.fase !== 'lista') return false;
      // `isSilent = false` para que el instalador se vea: si algo sale mal, el
      // jugador ve la ventana de NSIS en vez de un launcher que no vuelve.
      autoUpdater.quitAndInstall(false, true);
      return true;
    },
  };
}
