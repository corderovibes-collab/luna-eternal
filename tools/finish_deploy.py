import subprocess
import time
import sys
from pathlib import Path

SSH_KEY = r"C:\Users\JUAN\.ssh\pokereport_node01_ed25519"
SERVER_HOST = "root@15.235.16.131"
CONTAINER_ID = "1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd"
VOLUME_DIR = f"/var/lib/pterodactyl/volumes/{CONTAINER_ID}"
LOCAL_JAR = Path("mod/build/libs/lunaeternal-0.1.0.jar")
EXPECTED_SIZE = LOCAL_JAR.stat().st_size

def run_ssh(cmd):
    res = subprocess.run([
        "ssh", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
        SERVER_HOST, cmd
    ], capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error running remote command: {cmd}\n{res.stderr}")
        raise RuntimeError(res.stderr)
    return res.stdout.strip()

def main():
    print(f"Esperando que /tmp/test_speed.jar alcance {EXPECTED_SIZE} bytes...")
    while True:
        try:
            out = run_ssh("stat -c %s /tmp/test_speed.jar 2>/dev/null || echo 0")
            size = int(out.strip())
            pct = (size / EXPECTED_SIZE) * 100.0
            print(f"\rProgreso: {size / 1024 / 1024:.1f} / {EXPECTED_SIZE / 1024 / 1024:.1f} MB ({pct:.1f}%)", end="", flush=True)
            if size >= EXPECTED_SIZE:
                print("\n¡Subida completada con éxito!")
                break
        except Exception as e:
            print(f"\nError temporal al comprobar tamaño: {e}")
        time.sleep(5)

    target_jar = f"{VOLUME_DIR}/mods/lunaeternal-0.1.0.jar"
    print(f"Moviendo /tmp/test_speed.jar a {target_jar}...")
    run_ssh(f"mv /tmp/test_speed.jar {target_jar} && chown pterodactyl:pterodactyl {target_jar}")
    print("Permisos asignados.")

    print("Iniciando contenedor Docker...")
    run_ssh(f"docker start {CONTAINER_ID}")
    print("Contenedor iniciado. Esperando arranque del servidor...")

    print("Publicando actualización en el Launcher...")
    subprocess.run([
        sys.executable, "tools/publicar_jar_luna.py", "--publicar",
        "--mensaje", "Arreglo combate Misty nivel 18-19, deduplicacion automatica Ciudadela y hologramas limpios"
    ], check=True)

    print("\nMonitorizando log de arranque del servidor...")
    start_time = time.time()
    booted = False
    while time.time() - start_time < 180:
        time.sleep(5)
        logs = run_ssh(f"docker logs --tail 40 {CONTAINER_ID}")
        if "Done (" in logs or "Done!" in logs:
            print("\n¡El servidor ha iniciado completamente (Done!)!")
            booted = True
            break
        print(f"Esperando inicio del servidor... ({int(time.time() - start_time)}s)")

    if not booted:
        print("Advertencia: No se detectó 'Done!' dentro de los 180 segundos. Revisar logs manuales.")
    else:
        print("=== TODO DESPLEGADO Y OPERATIVO ===")

if __name__ == "__main__":
    main()
