"""Reads the Meshy marker OBJ + texture and prints a radius/colour profile along its long axis."""
import sys, json, random
import numpy as np
from PIL import Image

obj, tex = sys.argv[1], sys.argv[2]
V, T, F = [], [], []
for line in open(obj):
    p = line.split()
    if not p: continue
    if p[0] == 'v': V.append([float(x) for x in p[1:4]])
    elif p[0] == 'vt': T.append([float(x) for x in p[1:3]])
    elif p[0] == 'f':
        F.append([tuple(int(i) - 1 if i else -1 for i in (c.split('/') + ['', ''])[:2]) for c in p[1:4]])
V, T = np.array(V), np.array(T)
img = Image.open(tex).convert('RGB'); W, H = img.size; px = img.load()
rng = random.Random(1)
pts, cols = [], []
for f in F:
    a, b, c = [V[i] for i, _ in f]
    ta, tb, tc = [T[j] for _, j in f]
    area = np.linalg.norm(np.cross(b - a, c - a))
    n = max(1, int(area * 40000))
    for _ in range(min(n, 60)):
        r1, r2 = rng.random(), rng.random()
        if r1 + r2 > 1: r1, r2 = 1 - r1, 1 - r2
        p = a + r1 * (b - a) + r2 * (c - a); uv = ta + r1 * (tb - ta) + r2 * (tc - ta)
        x = min(W - 1, max(0, int(uv[0] * W))); y = min(H - 1, max(0, int((1 - uv[1]) * H)))
        pts.append(p); cols.append(px[x, y])
pts, cols = np.array(pts), np.array(cols, dtype=float) / 255
c = pts.mean(0); u, s, vt = np.linalg.svd(pts - c, full_matrices=False); axis = vt[0]
t = (pts - c) @ axis
perp = (pts - c) - np.outer(t, axis)
# second axis for the cap clip asymmetry
side = perp @ vt[1]
tmin, tmax = t.min(), t.max(); L = tmax - tmin
N = int(sys.argv[3]) if len(sys.argv) > 3 else 32
prof = []
for i in range(N):
    m = (t >= tmin + L * i / N) & (t < tmin + L * (i + 1) / N + 1e-9)
    if not m.any(): prof.append(None); continue
    r = np.percentile(np.linalg.norm(perp[m], axis=1), 85) / L
    lum = cols[m] @ [0.299, 0.587, 0.114]
    prof.append({'r': round(float(r), 4), 'lum': round(float(np.median(lum)), 3), 'dark': round(float((lum < 0.35).mean()), 2),
                 'side_max': round(float(side[m].max() / L), 4), 'side_min': round(float(side[m].min() / L), 4)})
json.dump(prof, sys.stdout, indent=0)
