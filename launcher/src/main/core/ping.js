import net from 'node:net';

/**
 * Server List Ping de Minecraft (el mismo que usa la lista de servidores del juego).
 *
 * Se implementa a mano —son dos paquetes— para que el launcher pueda decir si el
 * servidor está arriba y cuánta gente hay antes de lanzar el juego. Así el piloto
 * de estado informa de algo real en vez de ser un adorno.
 */

function varInt(value) {
  const bytes = [];
  let v = value >>> 0;
  do {
    let b = v & 0x7f;
    v >>>= 7;
    if (v) b |= 0x80;
    bytes.push(b);
  } while (v);
  return Buffer.from(bytes);
}

function readVarInt(buf, offset) {
  let result = 0;
  let shift = 0;
  let pos = offset;
  while (pos < buf.length) {
    const byte = buf[pos++];
    result |= (byte & 0x7f) << shift;
    if (!(byte & 0x80)) return { value: result, size: pos - offset };
    shift += 7;
    if (shift > 35) throw new Error('VarInt demasiado largo');
  }
  return null; // aún no ha llegado entero
}

const packet = (id, ...parts) => {
  const body = Buffer.concat([varInt(id), ...parts]);
  return Buffer.concat([varInt(body.length), body]);
};

const mcString = (str) => {
  const data = Buffer.from(str, 'utf8');
  return Buffer.concat([varInt(data.length), data]);
};

export function pingServer({ host, port = 25565, timeout = 4000 }) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    let chunks = Buffer.alloc(0);
    let settled = false;

    const finish = (result) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(result);
    };

    socket.setTimeout(timeout);
    socket.on('timeout', () => finish({ online: false, error: 'timeout' }));
    socket.on('error', (err) => finish({ online: false, error: err.code ?? err.message }));

    socket.on('connect', () => {
      // 767 = protocolo de 1.21.1; da igual el valor exacto para un status.
      const handshake = packet(0x00, varInt(767), mcString(host), Buffer.from([port >> 8, port & 0xff]), varInt(1));
      socket.write(Buffer.concat([handshake, packet(0x00)]));
    });

    socket.on('data', (chunk) => {
      chunks = Buffer.concat([chunks, chunk]);

      const length = readVarInt(chunks, 0);
      if (!length) return;
      const total = length.size + length.value;
      if (chunks.length < total) return; // respuesta troceada, seguir esperando

      try {
        let offset = length.size;
        offset += readVarInt(chunks, offset).size;         // id de paquete
        const strLen = readVarInt(chunks, offset);
        offset += strLen.size;
        const json = JSON.parse(chunks.toString('utf8', offset, offset + strLen.value));
        finish({
          online: true,
          players: json.players?.online ?? 0,
          maxPlayers: json.players?.max ?? 0,
          version: json.version?.name ?? '',
        });
      } catch (err) {
        finish({ online: false, error: err.message });
      }
    });
  });
}
