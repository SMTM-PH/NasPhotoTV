"""Convert the v24.02.06 ChinaAdminDivisonSHP city layer to the offline place index.

Requires pyshp and Shapely. Pass the path to city.shp (with its DBF/SHX
beside it) and the destination TSV path.
"""
import argparse
import math
from pathlib import Path

import shapefile
from shapely.geometry import shape
from shapely.validation import make_valid

MUNICIPALITIES = {"11": "北京", "12": "天津", "31": "上海", "50": "重庆"}


def to_wgs84(lon, lat):
    """Invert the GCJ-02 offsets in the source geometries."""
    estimated_lon, estimated_lat = lon, lat
    for _ in range(3):
        x, y = estimated_lon - 105, estimated_lat - 35
        wave = (20 * math.sin(6 * x * math.pi) + 20 * math.sin(2 * x * math.pi)) * 2 / 3
        north = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * math.sqrt(abs(x))
        north += wave + (20 * math.sin(y * math.pi) + 40 * math.sin(y * math.pi / 3)) * 2 / 3
        north += (160 * math.sin(y * math.pi / 12) + 320 * math.sin(y * math.pi / 30)) * 2 / 3
        east = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
        east += wave + (20 * math.sin(x * math.pi) + 40 * math.sin(x * math.pi / 3)) * 2 / 3
        east += (150 * math.sin(x * math.pi / 12) + 300 * math.sin(x * math.pi / 30)) * 2 / 3
        rad = math.radians(estimated_lat)
        eccentricity = 0.006693421622965943
        factor = 1 - eccentricity * math.sin(rad) ** 2
        axis = 6378245
        estimated_lon = lon - math.degrees(east * math.sqrt(factor) / (axis * math.cos(rad)))
        estimated_lat = lat - math.degrees(north * factor ** 1.5 / (axis * (1 - eccentricity)))
    return round(estimated_lon, 5), round(estimated_lat, 5)


def polygons(geometry):
    if geometry.geom_type == "Polygon":
        yield geometry
    elif hasattr(geometry, "geoms"):
        for part in geometry.geoms:
            yield from polygons(part)


def generate(source, destination):
    with shapefile.Reader(str(source)) as reader, destination.open("w", encoding="utf-8") as output:
        output.write("# ChinaAdminDivisonSHP v24.02.06 (MIT), see place-boundaries-license.txt\n")
        for feature in reader.iterShapeRecords():
            code, name, province, region, *_ = feature.record
            if region == name or province[:2] in MUNICIPALITIES:
                code = province
                name = MUNICIPALITIES.get(province[:2], name)
            name = name.removesuffix("市")
            geometry = make_valid(shape(feature.shape.__geo_interface__)).simplify(0.003)
            for polygon in polygons(geometry):
                rings = [list(dict.fromkeys(to_wgs84(x, y) for x, y in ring.coords[:-1]))
                         for ring in (polygon.exterior, *polygon.interiors)]
                if len(rings[0]) < 3:
                    continue
                rings = [ring for ring in rings if len(ring) >= 3]
                points = rings[0]
                bbox = (min(x for x, _ in points), min(y for _, y in points),
                        max(x for x, _ in points), max(y for _, y in points))
                bounds = ",".join(map(str, bbox))
                vertices = "|".join(",".join(f"{x},{y}" for x, y in ring) for ring in rings)
                output.write(f"{code}\t{name}\t{region}\t{bounds}\t{vertices}\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    generate(args.source, args.destination)
