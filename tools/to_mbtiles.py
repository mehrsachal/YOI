#!/usr/bin/env python3
"""Converts scanned map sheets and satellite exports (GeoTIFF from Survey of Pakistan scans, Sentinel-2, Google Earth
Engine, drone mosaics) into an MBTiles map pack that P-Quest imports from SD card or USB.

    ./to_mbtiles.py sheet.tif                 -> sheet.mbtiles   (zooms chosen from the image resolution)
    ./to_mbtiles.py s2.tif --min 8 --max 14   -> explicit zoom range
    ./to_mbtiles.py dem.tif --dem             -> Terrarium elevation pack (spot heights, profiles, hillshade, contours)

Any projection GDAL understands is accepted (it is warped to web mercator), which is the way to bring in sheets on
grids the phone cannot read directly. Needs GDAL with Python bindings (apt install gdal-bin python3-gdal) and numpy."""
import argparse, math, os, sqlite3, sys
import numpy as np
from osgeo import gdal

p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
p.add_argument('src'); p.add_argument('--out'); p.add_argument('--min', type=int); p.add_argument('--max', type=int)
p.add_argument('--dem', action='store_true', help='single-band elevation in metres -> Terrarium tiles')
p.add_argument('--name')
a = p.parse_args()
out = a.out or os.path.splitext(a.src)[0] + '.mbtiles'
R = 20037508.342789244

warped = gdal.Warp('', a.src, format='VRT', dstSRS='EPSG:3857', resampleAlg='bilinear' if a.dem else 'cubic', dstAlpha=not a.dem)
gt = warped.GetGeoTransform(); W, H = warped.RasterXSize, warped.RasterYSize
x0, y1 = gt[0], gt[3]; x1, y0 = x0 + W * gt[1], y1 + H * gt[5]
zmax = a.max if a.max is not None else min(18 if not a.dem else 13, max(0, math.ceil(math.log2(2 * R / 256 / gt[1]))))
zmin = a.min if a.min is not None else max(0, zmax - 6)
lon = lambda x: x / R * 180
lat = lambda y: math.degrees(math.atan(math.sinh(y / 6378137.0)))

if os.path.exists(out): os.remove(out)
db = sqlite3.connect(out)
db.execute('CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT)')
db.execute('CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB, PRIMARY KEY (zoom_level, tile_column, tile_row))')
meta = dict(name=a.name or os.path.splitext(os.path.basename(a.src))[0], format='png', minzoom=zmin, maxzoom=zmax, type='overlay',
            bounds=f'{lon(x0)},{lat(y0)},{lon(x1)},{lat(y1)}')
if a.dem: meta['encoding'] = 'terrarium'
db.executemany('INSERT INTO metadata VALUES (?,?)', [(k, str(v)) for k, v in meta.items()])
mem = gdal.GetDriverByName('MEM'); png = gdal.GetDriverByName('PNG'); n = 0
for z in range(zmin, zmax + 1):
    size = 2 * R / 2 ** z
    for tx in range(int((x0 + R) // size), int((x1 + R) // size) + 1):
        for ty in range(int((R - y1) // size), int((R - y0) // size) + 1):
            bounds = [tx * size - R, R - (ty + 1) * size, (tx + 1) * size - R, R - ty * size]
            t = gdal.Warp('', warped, format='MEM', outputBounds=bounds, width=256, height=256, resampleAlg='bilinear' if a.dem else 'cubic')
            arr = t.ReadAsArray()
            if a.dem:
                h = np.nan_to_num(arr.astype('f8')); nod = t.GetRasterBand(1).GetNoDataValue()
                if nod is not None: h[arr == nod] = 0
                if not h.any(): continue
                v = np.clip(h + 32768, 0, 65535.996)
                rgb = np.stack([np.floor(v / 256), np.floor(v % 256), np.floor((v % 1) * 256)]).astype('u1')
            else:
                if arr.ndim == 2: arr = arr[None]
                alpha = arr[-1]
                if not alpha.any(): continue
                rgb = arr[:3] if arr.shape[0] >= 4 else np.repeat(arr[:1], 3, 0)
                rgb = np.concatenate([rgb, alpha[None]]).astype('u1')
            m = mem.Create('', 256, 256, rgb.shape[0], gdal.GDT_Byte); m.WriteArray(rgb)
            path = f'/vsimem/t{n}.png'; png.CreateCopy(path, m)
            f = gdal.VSIFOpenL(path, 'rb'); gdal.VSIFSeekL(f, 0, 2); ln = gdal.VSIFTellL(f); gdal.VSIFSeekL(f, 0, 0)
            db.execute('INSERT INTO tiles VALUES (?,?,?,?)', (z, tx, 2 ** z - 1 - ty, gdal.VSIFReadL(1, ln, f))); gdal.VSIFCloseL(f); gdal.Unlink(path); n += 1
    db.commit(); print(f'zoom {z} done ({n} tiles so far)', file=sys.stderr)
db.close(); print(f'{out}: {n} tiles, zoom {zmin}-{zmax}, {os.path.getsize(out) // 1024} KB')
