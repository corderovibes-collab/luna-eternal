/**
 * Visor del personaje en 3D, con CSS puro.
 *
 * Se construye el muñeco con cajas de seis caras y se recorta la textura de la
 * skin con `background-position`. No hace falta WebGL ni ninguna librería: son
 * 36 divs, arranca al instante y respeta la CSP del launcher (que bloquea
 * cualquier script externo).
 */

const U = 6; // pixeles de pantalla por pixel de skin

// El muñeco ocupa de -8 (alto de la cabeza) a +24 (pies) tomando el hombro como
// origen, o sea 32 px de skin cuyo punto medio cae en +8. Hay que subirlo esos
// 8 para que quede centrado en el marco en vez de salirse por abajo.
const CENTER = 8 * U;

// Coordenadas de cada cara dentro de la textura, tal y como las define Minecraft.
// [x, y] de la esquina superior izquierda; el tamaño sale de las medidas de la caja.
const UV = {
  head: { w: 8, h: 8, d: 8, faces: { top: [8, 0], bottom: [16, 0], right: [0, 8], front: [8, 8], left: [16, 8], back: [24, 8] } },
  body: { w: 8, h: 12, d: 4, faces: { top: [20, 16], bottom: [28, 16], right: [16, 20], front: [20, 20], left: [28, 20], back: [32, 20] } },
  armR: { w: 4, h: 12, d: 4, faces: { top: [44, 16], bottom: [48, 16], right: [40, 20], front: [44, 20], left: [48, 20], back: [52, 20] } },
  armL: { w: 4, h: 12, d: 4, faces: { top: [36, 48], bottom: [40, 48], right: [32, 52], front: [36, 52], left: [40, 52], back: [44, 52] } },
  legR: { w: 4, h: 12, d: 4, faces: { top: [4, 16], bottom: [8, 16], right: [0, 20], front: [4, 20], left: [8, 20], back: [12, 20] } },
  legL: { w: 4, h: 12, d: 4, faces: { top: [20, 48], bottom: [24, 48], right: [16, 52], front: [20, 52], left: [24, 52], back: [28, 52] } },
};

// Las skins antiguas (64x32) no traen mitad izquierda: se reutiliza la derecha.
const LEGACY = { armL: 'armR', legL: 'legR' };

// Centro de cada pieza. El origen (0,0) es el hombro; +y baja.
const PLACE = {
  head: [0, -4, 0],
  body: [0, 6, 0],
  armR: [-6, 6, 0],
  armL: [6, 6, 0],
  legR: [-2, 18, 0],
  legL: [2, 18, 0],
};

function face(name, [ux, uy], { w, h, d }, skin, texH) {
  const el = document.createElement('i');
  const size = { front: [w, h], back: [w, h], right: [d, h], left: [d, h], top: [w, d], bottom: [w, d] }[name];

  const transform = {
    front: `translateZ(${(d / 2) * U}px)`,
    back: `rotateY(180deg) translateZ(${(d / 2) * U}px)`,
    right: `rotateY(-90deg) translateZ(${(w / 2) * U}px)`,
    left: `rotateY(90deg) translateZ(${(w / 2) * U}px)`,
    top: `rotateX(90deg) translateZ(${(h / 2) * U}px)`,
    bottom: `rotateX(-90deg) translateZ(${(h / 2) * U}px)`,
  }[name];

  Object.assign(el.style, {
    position: 'absolute',
    left: `${-(size[0] / 2) * U}px`,
    top: `${-(size[1] / 2) * U}px`,
    width: `${size[0] * U}px`,
    height: `${size[1] * U}px`,
    backgroundImage: `url("${skin}")`,
    // El alto real de la textura importa: las skins antiguas son 64x32 y, si se
    // escala como si fueran 64x64, todas las coordenadas verticales se van a la
    // mitad (el torso acaba pintando la cara).
    backgroundSize: `${64 * U}px ${texH * U}px`,
    backgroundPosition: `${-ux * U}px ${-uy * U}px`,
    imageRendering: 'pixelated',
    backgroundRepeat: 'no-repeat',
    transform,
  });
  return el;
}

function box(part, skin, texH) {
  const spec = UV[part];
  const legacy = texH === 32;
  const source = legacy && LEGACY[part] ? UV[LEGACY[part]] : spec;
  const [px, py, pz] = PLACE[part];

  const el = document.createElement('div');
  Object.assign(el.style, {
    position: 'absolute',
    left: '50%',
    top: '50%',
    width: '0',
    height: '0',
    transformStyle: 'preserve-3d',
    transform: `translate3d(${px * U}px, ${py * U}px, ${pz * U}px)`,
  });

  for (const [name, uv] of Object.entries(source.faces)) {
    el.append(face(name, uv, { w: spec.w, h: spec.h, d: spec.d }, skin, texH));
  }
  return el;
}

/**
 * Dibuja el personaje dentro de `container`.
 * Devuelve una función para soltar los listeners al reemplazarlo.
 */
export function renderSkin(container, { dataUri } = {}) {
  container.replaceChildren();
  if (!dataUri) return () => {};

  const stage = document.createElement('div');
  Object.assign(stage.style, {
    position: 'relative',
    width: '100%',
    height: '100%',
    perspective: '900px',
  });

  const model = document.createElement('div');
  Object.assign(model.style, {
    position: 'absolute',
    inset: '0',
    transformStyle: 'preserve-3d',
    // Se sube el muñeco para que quede centrado: mide 32 px de skin de alto y el
    // origen esta en el hombro, no en el centro.
    transform: `translateY(${-CENTER}px) rotateX(-8deg) rotateY(-22deg)`,
    transition: 'transform .12s linear',
  });

  const img = new Image();
  img.onload = () => {
    for (const part of Object.keys(UV)) model.append(box(part, dataUri, img.height));
  };
  img.src = dataUri;

  stage.append(model);
  container.append(stage);

  // Giro con el ratón; si no se toca, gira solo despacio.
  let angle = -22;
  let tilt = -8;
  let dragging = false;
  let lastX = 0;
  let lastY = 0;
  let idle = true;

  const apply = () => {
    model.style.transform = `translateY(${-CENTER}px) rotateX(${tilt}deg) rotateY(${angle}deg)`;
  };

  const onDown = (e) => { dragging = true; idle = false; lastX = e.clientX; lastY = e.clientY; };
  const onMove = (e) => {
    if (!dragging) return;
    angle += (e.clientX - lastX) * 0.6;
    tilt = Math.max(-30, Math.min(30, tilt - (e.clientY - lastY) * 0.3));
    lastX = e.clientX;
    lastY = e.clientY;
    apply();
  };
  const onUp = () => { dragging = false; };

  stage.addEventListener('pointerdown', onDown);
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
  stage.style.cursor = 'grab';

  const spin = setInterval(() => {
    if (!idle) return;
    angle = (angle + 0.35) % 360;
    apply();
  }, 40);

  return () => {
    clearInterval(spin);
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
  };
}
