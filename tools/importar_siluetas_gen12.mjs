#!/usr/bin/env node
// Imports only user-provided Gen 1/2 silhouettes; later generations remain opt-in.
import fs from 'node:fs';
import path from 'node:path';

const source = 'C:/Users/JUAN/Downloads/siluetas';
const destination = path.resolve(import.meta.dirname, '../mod/src/client/resources/assets/lunaeternal/textures/quien_es_ese_pokemon/siluetas');
fs.mkdirSync(destination, { recursive: true });
for (let dex = 1; dex <= 251; dex++) {
  const input = path.join(source, `${dex}.png`);
  if (!fs.existsSync(input)) throw new Error(`Falta la silueta local ${dex}.png`);
  fs.copyFileSync(input, path.join(destination, `${String(dex).padStart(3, '0')}.png`));
}
console.log('Importadas 251 siluetas locales (Pokédex 001–251).');
