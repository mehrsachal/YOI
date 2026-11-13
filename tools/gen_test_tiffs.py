"""Writes the small GeoTIFFs used by the map module's tests (needs Pillow and numpy)."""
import os
import numpy as np
from PIL import Image, TiffImagePlugin

out = os.path.join(os.path.dirname(__file__), '..', 'map', 'src', 'test', 'resources')
W, H = 300, 200
rgb = np.zeros((H, W, 3), 'u1')
rgb[..., 0] = np.arange(W)[None, :] % 256          # red = column
rgb[..., 1] = np.arange(H)[:, None]                # green = row
rgb[..., 2] = (np.arange(W)[None, :] // 7 * 13 + np.arange(H)[:, None] // 5 * 29) % 256

def save(name, im, scale, tie, keys, **kw):
    ifd = TiffImagePlugin.ImageFileDirectory_v2()
    ifd[33550] = scale; ifd.tagtype[33550] = 12
    ifd[33922] = tie; ifd.tagtype[33922] = 12
    ifd[34735] = keys; ifd.tagtype[34735] = 3
    im.save(os.path.join(out, name), tiffinfo=ifd, **kw)

GEO = (1, 1, 0, 2, 1024, 0, 1, 2, 2048, 0, 1, 4326)
UTM = (1, 1, 0, 2, 1024, 0, 1, 1, 3072, 0, 1, 32641)
MERC = (1, 1, 0, 2, 1024, 0, 1, 1, 3072, 0, 1, 3857)
im = Image.fromarray(rgb)
save('geo_none.tif', im, (0.0005, 0.0005, 0.0), (0.0, 0.0, 0.0, 64.0, 20.1, 0.0), GEO)
save('geo_lzw.tif', im, (0.0005, 0.0005, 0.0), (0.0, 0.0, 0.0, 64.0, 20.1, 0.0), GEO, compression='tiff_lzw')
save('utm_deflate.tif', im, (30.0, 30.0, 0.0), (0.0, 0.0, 0.0, 600000.0, 2216000.0, 0.0), UTM, compression='tiff_adobe_deflate')
save('merc_palette.tif', im.quantize(64), (40.0, 40.0, 0.0), (0.0, 0.0, 0.0, 7124000.0, 2276000.0, 0.0), MERC, compression='tiff_lzw')
save('grey.tif', im.convert('L'), (0.0005, 0.0005, 0.0), (0.0, 0.0, 0.0, 64.0, 20.1, 0.0), GEO)
im.save(os.path.join(out, 'plain.tif'))
