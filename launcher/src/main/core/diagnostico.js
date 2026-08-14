/**
 * Traduce un cierre del juego a una frase que un jugador pueda entender.
 *
 * POR QUÉ EXISTE ESTO
 *
 * Cuando Minecraft se cae, lo que ve el jugador es una ventana que desaparece.
 * Lo que ve el que da soporte es un mensaje como
 * `java.util.zip.ZipException: ZipFile invalid LOC header`, dentro de un stack
 * de netty que **no se parece en nada a la causa** — ese caso concreto ya costó
 * una sesión entera en este proyecto.
 *
 * Cada entrada de esta tabla es un fallo que ya ha ocurrido de verdad, en este
 * proyecto o en el anterior. No se inventan síntomas: si algún día aparece uno
 * nuevo, se añade aquí con su causa y su arreglo, y deja de costar una tarde.
 *
 * `accion` es lo que el launcher puede hacer solo:
 *   reparar   comprobar fichero a fichero y volver a bajar lo que no cuadre
 *   ram       abrir Ajustes en la memoria asignada
 *   ninguna   no hay botón: se explica y punto
 */

const CAUSAS = [
  {
    patron: /invalid LOC header|Unexpected end of ZLIB|zip END header not found|Invalid CEN header/i,
    titulo: 'Hay un fichero del pack corrupto',
    detalle: 'Una descarga se cortó a medias y el jar quedó roto. El tamaño puede '
      + 'parecer correcto, así que no se nota hasta que el juego intenta abrirlo.',
    accion: 'reparar',
  },
  {
    patron: /OutOfMemoryError|Could not reserve enough space for object heap/i,
    titulo: 'El juego se quedó sin memoria',
    detalle: 'Sube la RAM asignada en Ajustes. Si ya está al máximo que permite tu '
      + 'equipo, cierra el navegador antes de jugar: suele ser lo que se la come.',
    accion: 'ram',
  },
  {
    patron: /Incompatible mods found|requires version .* of mod|Mod resolution (failed|encountered an incompatible)/i,
    titulo: 'Las versiones de los mods no encajan',
    detalle: 'Tu instalación quedó a medio actualizar. Repararla vuelve a dejarla '
      + 'exactamente igual que la del servidor.',
    accion: 'reparar',
  },
  {
    patron: /UnsupportedClassVersionError|class file version \d+\.\d+/i,
    titulo: 'Java no es el que toca',
    detalle: 'El launcher instala su propio Java 21 y no usa el del sistema, así que '
      + 'esto suele significar que su copia quedó a medias. Repara y vuelve a probar.',
    accion: 'reparar',
  },
  {
    patron: /Mixin apply failed|MixinApplyError|MixinTransformerError/i,
    titulo: 'Dos mods se pisan entre ellos',
    detalle: 'Casi siempre es un mod añadido a mano en la carpeta del juego. Repara '
      + 'para dejar solo los del servidor.',
    accion: 'reparar',
  },
  {
    // ⚠ `OpenGL 3\.2` a secas estaba aquí y saltaba en CADA arranque correcto.
    // Minecraft escribe una línea de ÉXITO que lo contiene:
    //
    //   GPU: Intel(R) UHD Graphics 620 (Supports OpenGL 3.2.0 - Build 31.0…)
    //
    // Un diagnóstico que se equivoca siempre es peor que no tenerlo: enseña a
    // la gente a ignorar los avisos, y el día que haya uno de verdad tampoco lo
    // van a leer. Ahora solo casan frases que únicamente aparecen al fallar.
    patron: /Failed to create window|GLFW error|Pixel format not accelerated|No OpenGL context|requires? OpenGL|OpenGL \d\.\d[^)]{0,24}(or (higher|later|newer)|required)|does ?n[o']?t support OpenGL/i,
    titulo: 'La tarjeta gráfica no arranca el juego',
    detalle: 'Actualiza el controlador de la gráfica desde la web de NVIDIA, AMD o '
      + 'Intel. Minecraft 1.21 necesita OpenGL 3.2 o superior.',
    accion: 'ninguna',
  },
  {
    patron: /Connection refused|UnknownHostException|Failed to download|ETIMEDOUT|ENOTFOUND/i,
    titulo: 'Se cortó la conexión durante la descarga',
    detalle: 'Comprueba internet y dale otra vez. Lo ya descargado no se pierde: '
      + 'el launcher sigue por donde iba.',
    accion: 'ninguna',
  },
];

/** Códigos de salida que significan algo concreto, no un error del juego. */
const CODIGOS = {
  0: null,                                   // salida normal
  '-1073741819': {                           // 0xC0000005 en Windows
    titulo: 'El juego se cerró de golpe (violación de acceso)',
    detalle: 'Casi siempre es el controlador de la gráfica. Actualízalo; si acabas '
      + 'de hacerlo, reinicia el equipo antes de volver a probar.',
    accion: 'ninguna',
  },
  '-1073740791': {                           // 0xC0000409 stack buffer overrun
    titulo: 'El juego se cerró de golpe',
    detalle: 'Suele ser memoria mal asignada o un mod añadido a mano. Repara la '
      + 'instalación y, si sigue, baja un poco la RAM en Ajustes.',
    accion: 'reparar',
  },
};

/**
 * @param {number} codigo   código de salida del proceso
 * @param {string} registro últimas líneas del log del juego
 * @returns {{titulo:string, detalle:string, accion:string}|null}
 *          `null` si el cierre fue normal y no hay nada que explicar.
 */
export function diagnosticar(codigo, registro = '') {
  // El log manda sobre el código: un juego puede morir con código 1 por
  // cualquier motivo, pero si el log dice «invalid LOC header», eso es lo que
  // pasó y es lo que hay que enseñar.
  for (const causa of CAUSAS) {
    if (causa.patron.test(registro)) {
      const { patron, ...resto } = causa;
      return resto;
    }
  }

  const conocido = CODIGOS[String(codigo)];
  if (conocido !== undefined) return conocido;

  return {
    titulo: `El juego se cerró de forma inesperada (código ${codigo})`,
    detalle: 'No reconozco la causa. El registro completo está en la carpeta de '
      + 'logs: ábrela desde Ajustes y mándalo por Discord.',
    accion: 'ninguna',
  };
}
