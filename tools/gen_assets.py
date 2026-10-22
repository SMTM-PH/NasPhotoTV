"""Generate placeholder media assets (abstract wallpapers) + launcher banner for the NAS Photo TV prototype."""
import math
import os
import random
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
ASSETS = os.path.join(ROOT, "media_samples")
os.makedirs(DRAWABLE, exist_ok=True)
os.makedirs(ASSETS, exist_ok=True)

W, H = 1920, 1080


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size, c0, c1, angle=0.0):
    w, h = size
    img = Image.new("RGB", (w, h), c0)
    px = img.load()
    ca, sa = math.cos(angle), math.sin(angle)
    denom = (abs(ca) * w + abs(sa) * h) or 1
    for y in range(h):
        for x in range(0, w, 2):
            t = (x * ca + y * sa) / denom
            t = min(1.0, max(0.0, t))
            c = lerp(c0, c1, t)
            px[x, y] = c
            if x + 1 < w:
                px[x + 1, y] = c
    return img


def blob_layer(size, color, cx, cy, r, alpha):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(r * 0.55))


def make_wallpaper(path, c0, c1, blobs, angle=0.6, seed=1):
    rnd = random.Random(seed)
    img = gradient((W, H), c0, c1, angle).convert("RGBA")
    for (cx, cy, r, col, a) in blobs:
        img = Image.alpha_composite(img, blob_layer((W, H), col, cx, cy, r, a))
    # subtle "grain" stars
    stars = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(stars)
    for _ in range(260):
        x, y = rnd.randint(0, W), rnd.randint(0, H)
        rr = rnd.choice([1, 1, 1, 2])
        sd.ellipse([x, y, x + rr, y + rr], fill=(255, 255, 255, rnd.randint(20, 90)))
    img = Image.alpha_composite(img, stars)
    # vignette
    vig = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vig)
    vd.ellipse([-W * 0.25, -H * 0.35, W * 1.25, H * 1.35], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(240))
    black = Image.new("RGBA", (W, H), (0, 0, 0, 170))
    img = Image.composite(img, black, vig)
    img.convert("RGB").save(path, quality=88, optimize=True)
    print("wrote", path)


PALETTES = [
    ("wall_aurora", (7, 16, 40), (32, 12, 64),
     [(400, 900, 520, (16, 185, 129), 120), (1200, 300, 620, (59, 130, 246), 110), (1600, 950, 480, (168, 85, 247), 110)], 0.9, 3),
    ("wall_sunset", (46, 12, 22), (255, 132, 72),
     [(1600, 260, 380, (255, 196, 92), 180), (300, 820, 560, (126, 34, 66), 160), (1100, 1050, 420, (255, 96, 96), 90)], 1.2, 5),
    ("wall_ocean", (2, 20, 34), (6, 78, 110),
     [(900, 1050, 640, (13, 148, 136), 130), (1600, 200, 420, (56, 189, 248), 90), (250, 260, 380, (99, 102, 241), 80)], 0.4, 7),
    ("wall_forest", (8, 26, 14), (22, 74, 42),
     [(500, 200, 520, (132, 204, 22), 80), (1400, 850, 600, (16, 100, 60), 170), (1000, 400, 300, (250, 240, 180), 40)], 1.6, 11),
    ("wall_mono", (18, 18, 22), (58, 58, 66),
     [(700, 300, 520, (120, 120, 130), 90), (1500, 900, 500, (90, 90, 100), 110)], 0.2, 13),
    ("wall_candy", (48, 12, 60), (14, 30, 90),
     [(360, 300, 460, (236, 72, 153), 130), (1450, 300, 420, (56, 189, 248), 120), (900, 980, 520, (251, 191, 36), 70)], 1.0, 17),
]


def make_video_poster(path, title_hue, seed):
    rnd = random.Random(seed)
    base = gradient((1280, 720), (10, 12, 20), (40, 30, 60), 1.1).convert("RGBA")
    for i in range(3):
        base = Image.alpha_composite(base, blob_layer((1280, 720), (60 + title_hue, 120 + i * 40, 220), rnd.randint(100, 1180), rnd.randint(100, 620), rnd.randint(220, 380), 80))
    base.convert("RGB").save(path, quality=85)
    print("wrote", path)


if __name__ == "__main__":
    for (name, c0, c1, blobs, ang, seed) in PALETTES:
        make_wallpaper(os.path.join(ASSETS, name + ".jpg"), c0, c1, blobs, ang, seed)
    for i, name in enumerate(["video_mountain", "video_city", "video_family"]):
        make_video_poster(os.path.join(ASSETS, name + ".jpg"), i * 60, 21 + i)

    # Launcher banner 320x180
    banner = gradient((320, 180), (10, 14, 30), (40, 22, 70), 0.8).convert("RGBA")
    banner = Image.alpha_composite(banner, blob_layer((320, 180), (16, 185, 129), 250, 150, 110, 150))
    banner = Image.alpha_composite(banner, blob_layer((320, 180), (59, 130, 246), 70, 40, 90, 140))
    d = ImageDraw.Draw(banner)
    # "photo frame" glyph
    d.rounded_rectangle([26, 52, 104, 128], radius=14, outline=(255, 255, 255, 235), width=6)
    d.ellipse([44, 66, 62, 84], fill=(255, 255, 255, 235))
    d.polygon([(36, 120), (66, 88), (82, 104), (92, 94), (96, 120)], fill=(255, 255, 255, 200))
    banner.convert("RGB").save(os.path.join(DRAWABLE, "app_banner.png"), optimize=True)

    # Launcher icon 288x288 (Android adaptive-ish fallback)
    icon = gradient((288, 288), (10, 14, 30), (46, 24, 78), 0.8).convert("RGBA")
    icon = Image.alpha_composite(icon, blob_layer((288, 288), (16, 185, 129), 230, 240, 120, 170))
    d = ImageDraw.Draw(icon)
    d.rounded_rectangle([70, 88, 218, 206], radius=22, outline=(255, 255, 255, 235), width=10)
    d.ellipse([96, 108, 124, 136], fill=(255, 255, 255, 235))
    d.polygon([(86, 192), (150, 128), (184, 164), (200, 148), (204, 192)], fill=(255, 255, 255, 200))
    icon.convert("RGB").save(os.path.join(DRAWABLE, "app_icon.png"), optimize=True)
    print("done")
