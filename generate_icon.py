#!/usr/bin/env python3
"""Generate Village Castles' mod menu icon: a broken curtain wall on open ground.

Cloud Kingdoms' icon is already a keep, so this one is deliberately a different shape: wide
and low where that is narrow and tall, standing on grass rather than on a cloud, and ruined -
teeth missing from the battlement, a corner fallen away, moss at the foot. What the mod
generates is a fort weathering in the landscape, and the break is what says so at sixteen
pixels.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow, the same script
generated art approach as the rest of the suite. Deterministic: re-running produces identical
bytes.

Usage: python3 generate_icon.py [path/to/minecraft.jar]
"""

import glob
import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/village-castles/icon.png")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the sprite is cut from the same jar the
    mod is built against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the
    vanilla art comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth
    vanilla actually ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


def scale(pixels, n):
    """Nearest neighbour only: these are pixel textures, never smooth them."""
    return [[px for px in row for _ in range(n)] for row in pixels for _ in range(n)]


def blank(size=16):
    return [[CLEAR] * size for _ in range(size)]


def stamp(sprite, art, left, top):
    """Lay art onto the sprite at (left, top); transparent source pixels leave
    the sprite alone."""
    for y, row in enumerate(art):
        for x, px in enumerate(row):
            if px[3] and 0 <= top + y < len(sprite) and 0 <= left + x < len(sprite[0]):
                sprite[top + y][left + x] = px
    return sprite


def crop(pixels, left, top, width, height):
    return [row[left:left + width] for row in pixels[top:top + height]]



def here(path):
    """A texture this mod ships, read from the repo rather than the jar."""
    with open(os.path.join(HERE, path), "rb") as f:
        return decode_png(f.read())


def sample(pixels, width, height):
    """Nearest neighbour to any size, up or down. Pixel art is never smoothed."""
    src_h, src_w = len(pixels), len(pixels[0])
    return [[pixels[y * src_h // height][x * src_w // width] for x in range(width)]
            for y in range(height)]


def fill(colour, size=16):
    return [[colour] * size for _ in range(size)]


SKY = (140, 190, 236, 255)
GATEWAY = (26, 22, 20, 255)
LEFT, RIGHT = 2, 14           # the wall, with sky either side of it
WALL_TOP, GROUND = 6, 11
FALLEN = ((6, 6), (6, 7), (12, 6), (13, 6), (13, 7))     # where it has come down
MOSSY = ((3, 10), (4, 11), (11, 10), (12, 11))


def build_icon():
    sprite = fill(SKY)
    cobble, mossy = vanilla("block/cobblestone.png"), vanilla("block/mossy_cobblestone.png")
    grass, dirt = vanilla("block/grass_block_top.png"), vanilla("block/dirt.png")
    for y in range(GROUND, 16):
        for x in range(16):
            sprite[y][x] = grass[y][x] if y < GROUND + 2 else dirt[y][x]
    for y in range(WALL_TOP, GROUND):
        for x in range(LEFT, RIGHT):
            sprite[y][x] = cobble[y][x]
    for x in range(LEFT, RIGHT, 2):            # battlements, every other block
        sprite[WALL_TOP - 1][x] = cobble[WALL_TOP - 1][x]
    for x, y in FALLEN:
        sprite[y][x] = SKY
    middle = (LEFT + RIGHT) // 2
    for x in range(middle - 1, middle + 2):    # the gate
        for y in range(9, GROUND + 1):
            sprite[y][x] = GATEWAY
    for x, y in MOSSY:
        sprite[y][x] = mossy[y][x]
    return scale(sprite, 8)


if __name__ == "__main__":
    icon = build_icon()
    assert len(icon) == 128 and len(icon[0]) == 128, "mod menu icons are 128x128"
    write_png(OUT, icon)
