"""Regenerates coords/src/test/resources/vectors.csv from independent libraries (mgrs + pyproj).
Columns: lat,lon,mgrs(1 m),zone,utmE,utmN,lat/lon of the MGRS cell corner."""
import mgrs, pyproj, random
random.seed(7); m = mgrs.MGRS()
pts = [(33.6844,73.0479),(35.9208,74.3144),(24.8607,67.0011),(30.1798,66.9750),(-33.8568,151.2153),(0.0001,0.0001),
       (-0.5,-78.5),(64.5,5.9),(78.2,15.6),(-79.5,179.9),(83.9,-179.9),(20.0,64.0),(31.9999,72.0),(36.0,71.99999)]
pts += [(round(random.uniform(-79.9,83.9), 7), round(random.uniform(-180,180), 7)) for _ in range(40)]
for la, lo in pts:
    g = m.toMGRS(la, lo, MGRSPrecision=5); z = int(g[:-13]); la2, lo2 = m.toLatLon(g)
    t = pyproj.Transformer.from_crs(4326, pyproj.CRS.from_dict({'proj':'utm','zone':z,'south':la<0,'ellps':'WGS84'}), always_xy=True)
    E, N = t.transform(lo, la)
    print(f'{la:.7f},{lo:.7f},{g},{z},{E:.3f},{N:.3f},{la2:.8f},{lo2:.8f}')
