"""Builds the 3D marker item model from a profile measured off the Meshy mesh (see marker_profile.py).

Each slice along the marker gets two crossed cuboids so it reads round; the whole marker is tilted 45 degrees
like a tool sprite. Tip and cap use tintindex 1 so they take the marker's colour.
"""
import json, sys
from PIL import Image

prof = json.load(open(sys.argv[1]))
out_model, out_tex = sys.argv[2], sys.argv[3]
LENGTH, START, THICK = 20.0, -2.0, 1.4
N = len(prof)
step = LENGTH / N

def cls(p):
    if p['lum'] < 0.35: return 'ink'
    if p['dark'] > 0.2: return 'ring'
    return 'body'

# swatches (2x2 px each) in a 16x16 texture
SW = {'body': (0, 0xF4F4F2), 'cone': (2, 0xDCDCDA), 'ink': (4, 0xEAEAEA), 'clip': (6, 0xB4B4B4), 'ring': (8, 0x9C9EA2)}
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for u, rgb in SW.values():
    for x in range(u, u + 2):
        for y in range(2): img.putpixel((x, y), (rgb >> 16, rgb >> 8 & 255, rgb & 255, 255))
img.save(out_tex)

segs = []
for i, p in enumerate(prof):
    c = cls(p)
    if c == 'body' and i < 4: c = 'cone'
    r = round(p['r'] * LENGTH * THICK * 4) / 4
    clip = p['side_max'] - p['r'] > 0.008 and c == 'ink'
    if segs and segs[-1]['c'] == c and segs[-1]['r'] == r and segs[-1]['clip'] == clip:
        segs[-1]['n'] += 1
    else:
        segs.append({'c': c, 'r': r, 'clip': clip, 'i': i, 'n': 1, 'side': p['side_max']})

def faces(swatch, tint):
    u = SW[swatch][0]
    f = {d: {'uv': [u, 0, u + 2, 2], 'texture': '#marker'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    if tint:
        for v in f.values(): v['tintindex'] = 1
    return f

def r3(v): return round(v, 3)
ROT = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}
elements = []
for s in segs:
    y0, y1 = START + s['i'] * step, START + (s['i'] + s['n']) * step
    r, tint = max(s['r'], 0.5), s['c'] == 'ink'
    for wx, wz in ((r, r * 0.7), (r * 0.7, r)):
        elements.append({'from': [r3(8 - wx), r3(y0), r3(8 - wz)], 'to': [r3(8 + wx), r3(y1), r3(8 + wz)],
                         'rotation': ROT, 'faces': faces(s['c'], tint)})
    if s['clip']:  # pocket clip on the cap
        cx = r3(8 + s['side'] * LENGTH * THICK)
        elements.append({'from': [r3(8 + r * 0.7), r3(y0), 7.6], 'to': [cx, r3(y1), 8.4], 'rotation': ROT, 'faces': faces('clip', True)})

model = {
    'gui_light': 'front',
    'textures': {'marker': 'scribbled:item/marker_model', 'particle': 'scribbled:item/marker_model'},
    'elements': elements,
    'display': {
        'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [1, 1, 1]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
        'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4, 0.5], 'scale': [0.7, 0.7, 0.7]},
        'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4, 0.5], 'scale': [0.7, 0.7, 0.7]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.5, 0.5, 0.5]},
    },
}
json.dump(model, open(out_model, 'w'), indent=1)
print(len(segs), 'segments', len(elements), 'elements')
