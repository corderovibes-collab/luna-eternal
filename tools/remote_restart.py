import time
import yaml
import requests
import urllib3
import subprocess

urllib3.disable_warnings()

print("1. Flushing world data...")
subprocess.run(["/usr/local/sbin/pokereport-server-cmd", "save-all flush"])
time.sleep(2)

print("2. Triggering Wings restart...")
with open("/etc/pterodactyl/config.yml") as f:
    cfg = yaml.safe_load(f)
token = cfg.get("token")
headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
uuid = "1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd"
r = requests.post(f"https://127.0.0.1:8080/api/servers/{uuid}/power", headers=headers, json={"action": "restart"}, verify=False, timeout=15)
print("Wings response:", r.status_code)

print("3. Waiting for Cobblemon Main to report Done!...")
booted = False
start = time.time()
while time.time() - start < 120:
    time.sleep(4)
    res = subprocess.run(["docker", "logs", "--tail", "30", uuid], capture_output=True, text=True)
    logs = res.stdout + res.stderr
    if "Done (" in logs or "Done!" in logs:
        print("Server reached Done!")
        booted = True
        break
    print(f"Waiting... ({int(time.time() - start)}s)")

if not booted:
    print("WARNING: Server did not report Done within 120s")
else:
    print("Restart completed successfully.")
