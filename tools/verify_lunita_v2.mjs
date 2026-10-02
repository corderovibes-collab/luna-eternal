import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const assets = path.join(root, 'mod/src/main/resources/assets/lunaeternal');
const geo = JSON.parse(fs.readFileSync(path.join(assets, 'geo/lunita.geo.json')))['minecraft:geometry'][0];
const animations = JSON.parse(fs.readFileSync(path.join(assets, 'animations/lunita.animation.json'))).animations;
const bones = new Map(geo.bones.map(b => [b.name, b]));
assert.equal(bones.size, geo.bones.length);
for (const bone of bones.values()) {
  const ancestors = new Set([bone.name]);
  let parent = bone.parent;
  while (parent) {
    assert(bones.has(parent), `Missing parent ${parent}`);
    assert(!ancestors.has(parent), `Cycle at ${parent}`);
    ancestors.add(parent);
    parent = bones.get(parent).parent;
  }
}
const png = fs.readFileSync(path.join(assets, 'textures/entity/lunita.png'));
assert.equal(png.readUInt32BE(16), geo.description.texture_width);
assert.equal(png.readUInt32BE(20), geo.description.texture_height);
for (const [name, animation] of Object.entries(animations)) {
  for (const [bone, channels] of Object.entries(animation.bones ?? {})) {
    assert(bones.has(bone), `${name}: missing ${bone}`);
    for (const value of Object.values(channels)) {
      assert(!value.vector, `${name}: unconverted Cobblemon channel`);
      if (Array.isArray(value)) continue;
      for (const time of Object.keys(value)) assert(Number.isFinite(Number(time)), `${name}: invalid time ${time}`);
    }
  }
}
for (const name of ['idle', 'walk', 'blink']) assert.equal(animations[`animation.lunita.${name}`].loop, true);
assert.equal(animations['animation.lunita.greet'].animation_length, 25 / 20);
assert.equal(bones.get('lunita_chin_fur').parent, 'head_angle');
assert.equal(bones.get('lunita_chin_fur').cubes.length, 1);
assert.equal(bones.get('lunita_nose').parent, 'head_angle');
assert.deepEqual(animations['animation.lunita.walk'].bones.front_leg_left.rotation['0'],
                 animations['animation.lunita.walk'].bones.front_leg_right.rotation['0.3572']);
if (process.argv[2]) {
  const source = process.argv[2];
  const original = JSON.parse(fs.readFileSync(path.join(source, 'eevee_female.geo.json')))['minecraft:geometry'][0];
  for (const bone of original.bones.filter(b => b.name !== 'Lunita')) assert.deepEqual(bones.get(bone.name), bone);
  assert.deepEqual(png, fs.readFileSync(path.join(source, 'lunita textura.png')));
  const bb = JSON.parse(fs.readFileSync(path.join(source, 'Lunita v2 - Converted.bbmodel')));
  assert.equal(geo.bones.reduce((n, b) => n + (b.cubes?.length ?? 0), 0), bb.elements.filter(e => e.type === 'cube').length);
}
console.log(`PASS: ${bones.size} bones, ${Object.keys(animations).length} animations, texture 128x64, hierarchy, facial attachments and walk cycle.`);
