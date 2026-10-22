import subprocess
import sys
import time

ADB = r"E:\Android\Sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
PKG = "com.nasphoto.tv"
ACT = "com.nasphoto.tv/.MainActivity"
SHOTS = r"E:\NasPhotoTV\docs\screenshots"


def adb(*a):
    return subprocess.run([ADB, "-s", DEV, *a], capture_output=True)


def start():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", ACT)
    time.sleep(4.5)


def raw_keys(codes, gap=2):
    """Send a key sequence as ONE adb shell invocation to minimise freezes."""
    seq = "; ".join(f"input keyevent {c}; sleep {gap}" for c in codes)
    adb("shell", seq)


def shot(name):
    r = adb("exec-out", "screencap", "-p")
    with open(f"{SHOTS}\\{name}.png", "wb") as f:
        f.write(r.stdout)
    print("saved", name, len(r.stdout))


def main():
    spec = sys.argv[1]  # e.g. "restart|19|22,22,22|shot:b1|66|shot:b2"
    for step in spec.split("|"):
        if step == "restart":
            start()
        elif step.startswith("shot:"):
            time.sleep(0.5)
            shot(step[5:])
        elif step.startswith("wait:"):
            time.sleep(float(step[5:]))
        else:
            codes = [int(x) for x in step.split(",") if x]
            raw_keys(codes)
            time.sleep(0.9)


if __name__ == "__main__":
    main()
