"""Scratch debugging helper (not part of the app): guarded key sending + screenshots.

Usage:
  python tools/dbg.py restart <name>            # force-stop + start, screenshot
  python tools/dbg.py keys 19,21,22 <name> [waitMs]
  python tools/dbg.py shot <name>
  python tools/dbg.py focus                     # dumpsys current focus
"""
import subprocess
import sys
import time

ADB = r"E:\Android\Sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
PKG = "com.nasphoto.tv"
ACT = "com.nasphoto.tv/.MainActivity"
SHOTS = r"E:\NasPhotoTV\docs\screenshots"


def adb(*args, check=False):
    r = subprocess.run([ADB, "-s", DEV, *args], capture_output=True)
    if check and r.returncode != 0:
        raise RuntimeError(r.stderr.decode("utf-8", "ignore"))
    return r


def start():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", ACT)
    time.sleep(4.5)


def key(code, wait_ms=800):
    adb("shell", "input", "keyevent", str(code))
    time.sleep(wait_ms / 1000.0)


def keys(codes, wait_ms=800):
    for c in codes:
        key(c, wait_ms)


def shot(name):
    r = adb("exec-out", "screencap", "-p")
    path = f"{SHOTS}\\{name}.png"
    with open(path, "wb") as f:
        f.write(r.stdout)
    print("saved", name, len(r.stdout))


def focus():
    out = adb("shell", "dumpsys", "window").stdout.decode("utf-8", "ignore")
    for line in out.splitlines():
        if "mCurrentFocus" in line or "mFocusedApp" in line:
            print(line.strip())


def main():
    cmd = sys.argv[1]
    if cmd == "restart":
        start()
        if len(sys.argv) > 2:
            shot(sys.argv[2])
    elif cmd == "shot":
        shot(sys.argv[2])
    elif cmd == "keys":
        codes = [int(x) for x in sys.argv[2].split(",") if x]
        wait = int(sys.argv[4]) if len(sys.argv) > 4 else 800
        keys(codes, wait)
        if len(sys.argv) > 3:
            time.sleep(0.6)
            shot(sys.argv[3])
    elif cmd == "raw":
        # raw <keycode-list> : no restart, no shot
        codes = [int(x) for x in sys.argv[2].split(",") if x]
        wait = int(sys.argv[3]) if len(sys.argv) > 3 else 800
        keys(codes, wait)
    elif cmd == "focus":
        focus()
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
