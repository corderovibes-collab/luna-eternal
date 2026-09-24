import subprocess
import time
import sys

SSH_KEY = r"C:\Users\JUAN\.ssh\pokereport_node01_ed25519"
SERVER_HOST = "root@15.235.16.131"
TARGET = "/var/lib/pterodactyl/volumes/1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd/mods/lunaeternal-0.1.0.jar"
SOURCE = r"mod\build\libs\lunaeternal-0.1.0.jar"

def upload():
    with open(SOURCE, "rb") as f:
        data = f.read()

    print(f"Uploading {len(data)} bytes ({len(data) / 1024 / 1024:.2f} MB) to {TARGET}...", flush=True)
    t0 = time.time()
    proc = subprocess.Popen([
        "ssh", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
        SERVER_HOST, f"cat > {TARGET}.tmp && mv {TARGET}.tmp {TARGET} && chown pterodactyl:pterodactyl {TARGET}"
    ], stdin=subprocess.PIPE)

    chunk_size = 1024 * 1024
    total_written = 0
    while total_written < len(data):
        chunk = data[total_written:total_written + chunk_size]
        proc.stdin.write(chunk)
        total_written += len(chunk)
        elapsed = time.time() - t0
        rate = (total_written / 1024 / 1024) / (elapsed if elapsed > 0 else 0.001)
        print(f"\rUploaded {total_written / 1024 / 1024:.1f}/{len(data) / 1024 / 1024:.1f} MB ({rate:.1f} MB/s)", end="", flush=True)

    proc.stdin.close()
    proc.wait()
    print(f"\nUpload complete in {time.time() - t0:.1f}s, exit code {proc.returncode}", flush=True)
    if proc.returncode != 0:
        sys.exit(1)

if __name__ == "__main__":
    upload()
