import sys
import subprocess

def run_remote_cmd(server_cmd):
    res = subprocess.run([
        "ssh", "-i", r"C:\Users\JUAN\.ssh\pokereport_node01_ed25519",
        "-o", "StrictHostKeyChecking=no",
        "root@15.235.16.131",
        f"/usr/local/sbin/pokereport-server-cmd '{server_cmd}'"
    ], capture_output=True, text=True)
    print("Sent:", server_cmd)
    if res.stdout:
        print(res.stdout)
    if res.stderr:
        print("ERR:", res.stderr)

if __name__ == "__main__":
    cmd = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else "list"
    run_remote_cmd(cmd)
