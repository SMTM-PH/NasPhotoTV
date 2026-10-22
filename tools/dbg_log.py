import subprocess
import sys
import time

ADB = r"E:\Android\Sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
PKG = "com.nasphoto.tv"


def adb(*a):
    return subprocess.run([ADB, "-s", DEV, *a], capture_output=True).stdout.decode("utf-8", "ignore")


def pid():
    out = adb("shell", "pidof", PKG)
    return out.strip()


def log_since(pidv):
    return adb("logcat", "-d", "--pid", str(pidv))


def main():
    p = pid()
    print("pid:", p)
    n0 = len(log_since(p).splitlines())
    adb("shell", "input", "keyevent", "19")
    time.sleep(1.2)
    lines = log_since(p).splitlines()
    print("new lines:", len(lines) - n0)
    for l in lines[n0:]:
        low = l.lower()
        if any(k in low for k in ("focus", "key", "exception", "error", "crash", "anr")):
            print(l[:300])


if __name__ == "__main__":
    main()
