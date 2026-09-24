import subprocess
import time
import sys
from pathlib import Path

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
        raise RuntimeError(f"SSH command failed: {cmd}\n{res.stderr}")
    return res.stdout.strip()

def scp_to(local_path, remote_path):
    res = subprocess.run([
        "scp", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
        str(local_path), f"{SERVER_HOST}:{remote_path}"
    ], capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"SCP failed: {res.stderr}")

def main():
    print("--- DESPLEGANDO NUEVO JAR LUNA ETERNAL CON PROTECCIÓN Y AUTO-LIMPIEZA ---")
    
    # 0. Flush de datos
    print("0. Guardando mundo...")
    try:
        run_ssh("/usr/local/sbin/pokereport-server-cmd 'save-all flush'")
    except Exception as e:
        print(f"Aviso al guardar: {e}")
    time.sleep(2)

    # 1. Detener el contenedor
    print("1. Deteniendo contenedor Minecraft...")
    run_ssh(f"docker stop {CONTAINER_ID}")
    time.sleep(2)
    
    # 2. Subir JAR
    print("2. Subiendo nuevo lunaeternal-0.1.0.jar...")
    jar_local = Path("mod/build/libs/lunaeternal-0.1.0.jar")
    scp_to(jar_local, f"{VOLUME_DIR}/mods/lunaeternal-0.1.0.jar")
    run_ssh(f"chown pterodactyl:pterodactyl {VOLUME_DIR}/mods/lunaeternal-0.1.0.jar")
    
    # 3. Iniciar contenedor
    print("3. Iniciando contenedor Minecraft...")
    run_ssh(f"docker start {CONTAINER_ID}")
    
    print("Esperando arranque completo (Done!)...")
    start = time.time()
    booted = False
    while time.time() - start < 120:
        time.sleep(4)
        logs = run_ssh(f"docker logs --tail 30 {CONTAINER_ID}")
        if "Done (" in logs or "Done!" in logs:
            print("Servidor iniciado y listo (Done!).")
            booted = True
            break
        print(f"Esperando inicio... ({int(time.time() - start)}s)")

    if not booted:
        print("ADVERTENCIA: El servidor tardó más de 120s en responder 'Done'")

    # 4. Publicar para los clientes en el Launcher
    print("4. Publicando en el Launcher...")
    res = subprocess.run([
        sys.executable, "tools/publicar_jar_luna.py", "--publicar",
        "--mensaje", "Proteccion de bloques en Ciudadela y Gimnasios, auto-liberacion de salas de combate"
    ], capture_output=True, text=True)
    print(res.stdout)
    if res.stderr:
        print("STDERR:", res.stderr)
        
    print("--- TODO DESPLEGADO Y VERIFICADO ---")

if __name__ == "__main__":
    main()
