import subprocess
import os
import sys
import time
import zlib
import struct
import io
from pathlib import Path
import nbtlib

SSH_KEY = r"C:\Users\JUAN\.ssh\pokereport_node01_ed25519"
SERVER_HOST = "root@15.235.16.131"
CONTAINER_ID = "1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd"
VOLUME_DIR = f"/var/lib/pterodactyl/volumes/{CONTAINER_ID}"

def run_ssh(cmd):
    res = subprocess.run([
        "ssh", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
        SERVER_HOST, cmd
    ], capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error executing remote command: {cmd}\n{res.stderr}")
        raise RuntimeError(res.stderr)
    return res.stdout.strip()

def scp_from(remote_path, local_path):
    res = subprocess.run([
        "scp", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
        f"{SERVER_HOST}:{remote_path}", str(local_path)
    ], capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"SCP from failed: {res.stderr}")

def scp_to(local_path, remote_path):
    res = subprocess.run([
        "scp", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
        str(local_path), f"{SERVER_HOST}:{remote_path}"
    ], capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"SCP to failed: {res.stderr}")

def deduplicate_ciudadela_mca(in_path, out_path):
    with open(in_path, 'rb') as f:
        data = bytearray(f.read())

    header = data[:4096]
    for cz in range(32):
        for cx in range(32):
            idx = 4 * (cx + cz * 32)
            offset = (header[idx] << 16) | (header[idx+1] << 8) | header[idx+2]
            sector_count = header[idx+3]
            if offset == 0: continue
            sec_start = offset * 4096
            length = struct.unpack('>I', data[sec_start:sec_start+4])[0]
            compressed = data[sec_start+5:sec_start+4+length]
            raw = zlib.decompress(compressed)
            nbt_file = nbtlib.File.parse(io.BytesIO(raw))

            if 'Entities' in nbt_file and cx == 23 and cz == 3:
                orig_len = len(nbt_file['Entities'])
                print(f"Found chunk ({cx}, {cz}) with {orig_len} entities. Filtering...")

                kept = []
                seen_brock = False
                seen_misty = False
                seen_onix = False
                seen_starmie = False
                seen_cartel_brock = False
                seen_cartel_misty = False

                for ent in nbt_file['Entities']:
                    eid = str(ent.get('id', ''))
                    tags = [str(t) for t in ent.get('Tags', [])]
                    pos = [float(p) for p in ent.get('Pos', [0, 0, 0])]

                    if eid == 'rctmod:trainer':
                        tid = str(ent.get('TrainerId', ''))
                        if 'brock' in tid or 'luna_gym_brock' in tags:
                            if not seen_brock:
                                # Limpiar custom name para que no genere etiqueta blanca
                                ent['CustomNameVisible'] = nbtlib.Byte(0)
                                if 'CustomName' in ent:
                                    del ent['CustomName']
                                kept.append(ent)
                                seen_brock = True
                                print(f"  + Kept 1 Brock trainer: pos={pos}")
                        elif 'misty' in tid or 'luna_gym_misty' in tags:
                            if not seen_misty:
                                ent['CustomNameVisible'] = nbtlib.Byte(0)
                                if 'CustomName' in ent:
                                    del ent['CustomName']
                                kept.append(ent)
                                seen_misty = True
                                print(f"  + Kept 1 Misty trainer: pos={pos}")

                    elif eid == 'cobblemon:pokemon':
                        # Distinguir por Z: Onix está en Z ~52, Starmie en Z ~60.39
                        if pos[2] < 56.0:
                            if not seen_onix:
                                kept.append(ent)
                                seen_onix = True
                                print(f"  + Kept 1 Onix: pos={pos}")
                        else:
                            if not seen_starmie:
                                kept.append(ent)
                                seen_starmie = True
                                print(f"  + Kept 1 Starmie: pos={pos}")

                    elif eid == 'minecraft:text_display':
                        bg = ent.get('background', 0)
                        txt = str(ent.get('text', ''))
                        # Ignorar el holograma antiguo que tiene fondo 0x40000000 o no tiene el título estilizado
                        if int(bg) == 0x40000000 or ('✦ LÍDER' not in txt and '✦ CAMPEÓN' not in txt):
                            print(f"  - Purged OLD legacy cartel (bg={bg}): pos={pos}")
                            continue


                        # Distinguir por Z: Brock en Z ~49.37, Misty en Z ~58.85
                        if pos[2] < 55.0:
                            if not seen_cartel_brock:
                                kept.append(ent)
                                seen_cartel_brock = True
                                print(f"  + Kept 1 New Brock Cartel: pos={pos}")
                        else:
                            if not seen_cartel_misty:
                                kept.append(ent)
                                seen_cartel_misty = True
                                print(f"  + Kept 1 New Misty Cartel: pos={pos}")

                nbt_file['Entities'] = nbtlib.List[nbtlib.Compound](kept)
                print(f"Chunk ({cx}, {cz}) deduplicated: {orig_len} -> {len(kept)} entities.")

                buf = io.BytesIO()
                nbt_file.write(buf)
                new_compressed = zlib.compress(buf.getvalue())
                new_payload_len = len(new_compressed) + 1
                assert (new_payload_len + 4) <= sector_count * 4096, "New chunk data exceeds sector count!"
                data[sec_start:sec_start+4] = struct.pack('>I', new_payload_len)
                data[sec_start+4] = 2
                data[sec_start+5:sec_start+4+new_payload_len] = new_compressed
                pad_start = sec_start + 4 + new_payload_len
                pad_end = sec_start + sector_count * 4096
                data[pad_start:pad_end] = b'\x00' * (pad_end - pad_start)

    with open(out_path, 'wb') as f:
        f.write(data)
    print(f"Generated clean MCA: {out_path}")

def main():
    print("=== DEDUPLICACIÓN Y PURGA DEFINITIVA DE CIUDADELA ===")
    os.makedirs("build", exist_ok=True)
    mca_remote = f"{VOLUME_DIR}/world/dimensions/lunaeternal/ciudadela/entities/r.-1.0.mca"
    mca_local = "build/r.-1.0.mca"
    mca_clean = "build/r.-1.0.mca.clean"

    # 1. Detener el servidor Docker
    print("Paso 1: Deteniendo servidor Minecraft Docker...")
    run_ssh(f"docker stop {CONTAINER_ID}")
    time.sleep(3)
    print("Servidor detenido.")

    # 2. Backup remoto
    ts = time.strftime("%Y%m%d_%H%M%S")
    print(f"Paso 2: Creando backup remoto de r.-1.0.mca (timestamp: {ts})...")
    run_ssh(f"cp {mca_remote} {mca_remote}.bak_{ts}")

    # 3. Descargar r.-1.0.mca
    print("Paso 3: Descargando r.-1.0.mca...")
    scp_from(mca_remote, mca_local)

    # 4. Procesar y deduplicar
    print("Paso 4: Deduplicando entidades de Brock y Misty en chunk (23, 3)...")
    deduplicate_ciudadela_mca(mca_local, mca_clean)

    # 5. Subir MCA limpio
    print("Paso 5: Subiendo r.-1.0.mca limpio al servidor...")
    scp_to(mca_clean, mca_remote)
    run_ssh(f"chown pterodactyl:pterodactyl {mca_remote}")

    # 6. Subir JAR actualizado
    print("Paso 6: Subiendo lunaeternal-0.1.0.jar actualizado...")
    jar_local = Path("mod/build/libs/lunaeternal-0.1.0.jar")
    if jar_local.exists():
        scp_to(jar_local, f"{VOLUME_DIR}/mods/lunaeternal-0.1.0.jar")
        run_ssh(f"chown pterodactyl:pterodactyl {VOLUME_DIR}/mods/lunaeternal-0.1.0.jar")
        print("JAR subido con éxito.")
    else:
        print("ADVERTENCIA: No se encontró mod/build/libs/lunaeternal-0.1.0.jar local!")

    # 7. Iniciar el servidor
    print("Paso 7: Iniciando servidor Minecraft Docker...")
    run_ssh(f"docker start {CONTAINER_ID}")
    time.sleep(5)
    print("Servidor iniciado.")

    # 8. Publicar actualización en launcher
    print("Paso 8: Publicando manifest y release para el launcher...")
    res_pub = subprocess.run([
        sys.executable, "tools/publicar_jar_luna.py", "--publicar",
        "--mensaje", "Purga de holograma duplicado de Brock y recepciones optimizadas en Ciudadela"
    ], capture_output=True, text=True)
    print(res_pub.stdout)
    if res_pub.stderr:
        print("STDERR publicar:", res_pub.stderr)

    print("=== DEDUPLICACIÓN Y DESPLIEGUE COMPLETADOS AL 100% ===")

if __name__ == "__main__":
    main()
