/*
 * Exportador reproducible del modelo que entregó el equipo de arte.
 * Uso: node tools/export_lunita_gecko.mjs <modelo.bbmodel> <directorio-assets>
 *
 * Blockbench guarda el árbol en `outliner`; GeckoLib consume el mismo árbol
 * como huesos Bedrock. El PNG embebido se conserva sin recomprimir.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [input, assets] = process.argv.slice(2);
if (!input || !assets) throw new Error('Faltan modelo o directorio de salida');
const model = JSON.parse(readFileSync(input, 'utf8'));
const elements = new Map(model.elements.filter(e => e.type === 'cube').map(e => [e.uuid, e]));
const groups = new Map(model.groups.map(g => [g.uuid, g]));
const cube = e => ({
  origin: e.from, size: e.to.map((v, i) => v - e.from[i]),
  pivot: e.origin || [0, 0, 0], rotation: e.rotation || [0, 0, 0],
  uv: e.uv_offset || [0, 0], inflate: e.inflate || 0, mirror: !!e.mirror_uv
});
const bone = (treeNode, parent) => {
  if (typeof treeNode === 'string') return null;
  // `outliner` solo contiene uuid/estado de interfaz; los datos reales viven
  // en `groups`. Se combinan para conservar jerarquía Y nombre de cada hueso.
  const node = groups.get(treeNode.uuid) || treeNode;
  const children = treeNode.children || node.children || [];
  const b = { name: node.name || 'root', pivot: node.origin || [0, 0, 0] };
  if (parent) b.parent = parent;
  if (node.rotation?.some(v => v)) b.rotation = node.rotation;
  const cubes = children.filter(x => typeof x === 'string').map(id => elements.get(id)).filter(Boolean).map(cube);
  if (cubes.length) b.cubes = cubes;
  return [b, ...children.filter(x => typeof x === 'object').flatMap(x => bone(x, b.name) || [])];
};
const bones = model.outliner.filter(x => typeof x === 'object').flatMap(x => bone(x, null) || []);
const geo = { format_version: '1.12.0', 'minecraft:geometry': [{
  description: { identifier: 'geometry.lunaeternal.lunita', texture_width: model.resolution.width,
    texture_height: model.resolution.height, visible_bounds_width: 5, visible_bounds_height: 4,
    visible_bounds_offset: [0, 1.5, 0] }, bones
}]};
mkdirSync(join(assets, 'geo'), { recursive: true });
mkdirSync(join(assets, 'textures/entity'), { recursive: true });
writeFileSync(join(assets, 'geo/lunita.geo.json'), JSON.stringify(geo));
const texture = model.textures?.[0]?.source || '';
const m = texture.match(/^data:image\/png;base64,(.+)$/);
if (!m) throw new Error('El .bbmodel no contiene una textura PNG embebida');
writeFileSync(join(assets, 'textures/entity/lunita.png'), Buffer.from(m[1], 'base64'));
console.log(`Exportados ${bones.length} huesos y ${elements.size} cubos.`);
