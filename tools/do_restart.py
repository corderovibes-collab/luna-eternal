import subprocess

SSH_KEY = r"C:\Users\JUAN\.ssh\pokereport_node01_ed25519"
SERVER_HOST = "root@15.235.16.131"

with open("tools/remote_restart.py", "r", encoding="utf-8") as f:
    restart_script = f.read()

res = subprocess.run([
    "ssh", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
    SERVER_HOST, "python3"
], input=restart_script.encode("utf-8"), capture_output=True)

print("STDOUT:\n", res.stdout.decode("utf-8", errors="replace"))
if res.stderr:
    print("STDERR:\n", res.stderr.decode("utf-8", errors="replace"))
