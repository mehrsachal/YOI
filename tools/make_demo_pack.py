"""Builds the bundled demo map: a FICTIONAL island in open ocean (20N 64E), so no real terrain or place is shown.
Outputs a layered-tint contour map and a Terrarium DEM as MBTiles into app/src/main/assets/demo/.
Usage: python3 tools/make_demo_pack.py   (needs numpy and Pillow)"""
import io, math, os, sqlite3
import numpy as np
from PIL import Image

Z0, ZMAX, DEM_ZMAX, ZMIN = 11, 14, 13, 8
LAT, LON = 20.0, 64.0
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'demo')
x0 = int((LON + 180) / 360 * 2 ** Z0)
y0 = int((1 - math.asinh(math.tan(math.radians(LAT))) / math.pi) / 2 * 2 ** Z0)
N = 256 * 2 ** (ZMAX - Z0)

rng = np.random.default_rng(3)
h = np.zeros((N, N), 'f4')
for cells, amp in [(3, 1), (6, .5), (12, .25), (24, .13), (48, .06), (96, .03)]:
    g = rng.random((cells + 1, cells + 1)).astype('f4')
    h += amp * np.asarray(Image.fromarray(g, mode='F').resize((N, N), Image.BICUBIC))
h = (h - h.min()) / (h.max() - h.min())
yy, xx = np.mgrid[0:N, 0:N] / N - 0.5
h = np.round(np.clip(h * np.clip(1 - (np.hypot(xx, yy) / 0.47) ** 2, 0, 1) * 3400 - 500, 0, None))

BANDS = [(0, (158, 198, 224)), (1, (206, 226, 178)), (200, (224, 232, 184)), (500, (240, 230, 180)), (800, (232, 208, 160)),
         (1100, (214, 182, 140)), (1400, (196, 160, 126)), (1700, (220, 210, 200)), (2000, (245, 245, 245))]

def render(hz, interval):
    rgb = np.zeros(hz.shape + (3,), 'u1')
    for lo, col in BANDS: rgb[hz >= lo] = col
    for step, col in [(interval, (150, 105, 60)), (interval * 5, (95, 60, 30))]:
        lv = np.floor(hz / step)
        edge = np.zeros(hz.shape, bool)
        edge[:, :-1] |= lv[:, :-1] != lv[:, 1:]; edge[:-1, :] |= lv[:-1, :] != lv[1:, :]
        rgb[edge & (hz > 0)] = col
    return rgb

def png(arr, palette):
    im = Image.fromarray(arr)
    if palette: im = im.quantize(24)
    b = io.BytesIO(); im.save(b, 'PNG', optimize=True); return b.getvalue()

def write(name, title, zmax, extra, make):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    if os.path.exists(path): os.remove(path)
    db = sqlite3.connect(path)
    db.execute('CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT)')
    db.execute('CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB, PRIMARY KEY (zoom_level, tile_column, tile_row))')
    w = x0 / 2 ** Z0 * 360 - 180; e = (x0 + 1) / 2 ** Z0 * 360 - 180
    lat = lambda y: math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / 2 ** Z0))))
    meta = dict(name=title, format='png', minzoom=ZMIN, maxzoom=zmax, bounds=f'{w},{lat(y0 + 1)},{e},{lat(y0)}', **extra)
    db.executemany('INSERT INTO metadata VALUES (?,?)', [(k, str(v)) for k, v in meta.items()])
    for z in range(ZMIN, zmax + 1):
        size = int(256 * 2 ** (z - Z0)); f = N // size
        hz = h.reshape(size, f, size, f).mean((1, 3))
        gx = int(x0 * 256 * 2 ** (z - Z0)); gy = int(y0 * 256 * 2 ** (z - Z0))
        tx0, ty0 = gx // 256, gy // 256; tx1, ty1 = (gx + size - 1) // 256, (gy + size - 1) // 256
        full = np.zeros(((ty1 - ty0 + 1) * 256, (tx1 - tx0 + 1) * 256), 'f4')
        full[gy - ty0 * 256: gy - ty0 * 256 + size, gx - tx0 * 256: gx - tx0 * 256 + size] = hz
        img = make(full, z)
        for ty in range(ty0, ty1 + 1):
            for tx in range(tx0, tx1 + 1):
                t = img[(ty - ty0) * 256:(ty - ty0 + 1) * 256, (tx - tx0) * 256:(tx - tx0 + 1) * 256]
                db.execute('INSERT INTO tiles VALUES (?,?,?,?)', (z, tx, 2 ** z - 1 - ty, png(t, t is not None and extra.get('type') == 'overlay')))
    db.commit(); db.close()
    print(name, os.path.getsize(path) // 1024, 'KB')

def terrarium(full, z):
    v = np.round(full).astype('i4') + 32768
    return np.dstack([v // 256, v % 256, np.zeros_like(v)]).astype('u1')

write('demo-topo.mbtiles', 'Demo island (fictional)', ZMAX, {'type': 'overlay'}, lambda full, z: render(full, 50 if z >= 13 else 100 if z >= 11 else 250))
write('demo-dem.mbtiles', 'Demo island elevation', DEM_ZMAX, {'type': 'overlay-dem', 'encoding': 'terrarium'}, terrarium)
