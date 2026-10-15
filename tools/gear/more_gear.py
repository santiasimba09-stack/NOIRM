"""The second set of wearable gear (kimonos, hakama, haori, village vests, cloaks, Kage robes and
hats, masks, plain forehead protectors, boots and arm guards). Called by gen_gear.py, which saves
the textures and icons and draws the preview.

All art is original. The rogue cloak uses our own pattern (silver crescent moons on black with a
violet lining), never a symbol from the series; forehead plates carry only our hook glyph.
Layers use the vanilla 64x32 armour layout (layer 1 = helmet, chest + arms, boots; layer 2 =
leggings), so they fit any humanoid armour model.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "skingen"))
from pixlib import Canvas, ARMOR_PARTS, hx, tone, noise, paint, mirror_side  # noqa: E402

A = ARMOR_PARTS


def layer():
    return Canvas(64, 32)


def pal(base, hi=None, sh=None, deep=None):
    b = hx(base)
    return {"b": b, "h": hx(hi) if hi else tone(b, 1), "s": hx(sh) if sh else tone(b, -1),
            "d": hx(deep) if deep else tone(b, -2.2)}


def weave(c, x, y, salt, amount=1.0):
    n = noise(x, y, salt)
    if n < 0.09:
        return tone(c, -0.3 * amount)
    if n > 0.95:
        return tone(c, 0.25 * amount)
    return c


STEEL = pal("#a7adb5", "#d8dde2", "#7c828a", "#4e535a")
GOLD = pal("#c9a03a", "#ecc860", "#94711f", "#5e470f")
WRAPC = hx("#ddd7c9")
WHITE = pal("#eeeae2", "#fbf9f4", "#cfc9bd", "#9e9788")
STRAP = hx("#2a2a30")
HOLE = hx("#0c0c0e")


def wraps(x, y):
    if (x + 2 * y) % 5 == 0:
        return tone(WRAPC, -0.7)
    return tone(WRAPC, 0.2 if y % 2 == 0 else -0.25)


# ---------------------------------------------------------------------------
# Kimono (chest) + kimono hem (leggings): a wrap robe, crossed collar, obi sash, scattered motif
# ---------------------------------------------------------------------------

# c robe, T collar trim, . open (whatever is under shows)
KIMONO_FRONT = [
    "cT....Tc",
    "ccT..Tcc",
    "cccTTccc",
    "ccccTccc",
    "cccccTcc",
    "cccccTcc",
    "cccccTcc",
]

KIMONOS = {
    # name: (robe, collar trim, obi, motif)
    "indigo": ("#2c3a6e", "#e6e0d2", "#b8862e", "#8fa2d8"),
    "crimson": ("#9a2430", "#efe6d6", "#2a2a34", "#e8a0a8"),
    "sage": ("#5d7a5a", "#efe9da", "#6a3f2a", "#c8dcb0"),
    "plum": ("#5e3458", "#e9dfe6", "#c9a03a", "#d6a8d0"),
}


def kimono(robe_hex, trim_hex, obi_hex, motif_hex, salt):
    P, T, O, M = pal(robe_hex), pal(trim_hex), pal(obi_hex), hx(motif_hex)
    l1, l2 = layer(), layer()

    def cloth(face, x, y, s):
        if noise(x, y, s) > 0.9 and (x + y) % 2 == 0:
            return M  # little scattered flowers
        c = weave(P["b"], x, y, s + 1)
        return tone(c, -0.2) if face in ("right", "left") else c

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["h"] if 0 < x < W - 1 else T["b"]
        if y in (7, 8):
            if face == "back" and y == 7 and x in (2, 3, 4, 5):
                return O["h"]  # the obi's bow on the back
            if face == "back" and y == 8 and x in (3, 4):
                return O["d"]
            return O["h"] if y == 7 else O["s"]
        if face == "front":
            if y < len(KIMONO_FRONT):
                ch = KIMONO_FRONT[y][x]
                if ch == ".":
                    return None
                if ch == "T":
                    return T["h"] if y == 0 else T["b"]
            if y >= 9 and x == 5:
                return P["d"]  # the overlap
        c = cloth(face, x, y, salt)
        if y == 11:
            c = P["s"]
        return c

    def arm(face, x, y, W, H):
        if face == "bottom" or y > 9:
            return None
        if face == "top":
            return P["h"]
        if y == 9:
            return T["s"]  # the sleeve's lined edge
        c = cloth(face, x, y, salt + 2)
        return tone(c, -0.35) if face == "left" else c

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)

    def waist(face, x, y, W, H):
        if face in ("top", "bottom") or y < 9:
            return None
        if face == "front" and x == 5:
            return P["d"]
        return cloth(face, x, y, salt + 3)

    def hem(face, x, y, W, H):
        if face in ("top", "bottom"):
            return None
        if y == 11:
            return T["s"]
        if y == 10:
            return P["s"]
        c = cloth(face, x, y, salt + 4)
        if face == "front" and x == 3 and y > 0:
            c = P["d"]  # the robe's edge falls down the front
        return c

    paint(l2, A["body"], waist)
    paint(l2, A["leg"], hem)
    return l1, l2


# ---------------------------------------------------------------------------
# Hakama: wide pleated trousers with a pale waist tie
# ---------------------------------------------------------------------------

def hakama():
    l2 = layer()
    P = pal("#34343e", "#4a4a58", "#25252d", "#18181e")

    def waist(face, x, y, W, H):
        if face in ("top", "bottom") or y < 9:
            return None
        if y == 9:
            return WHITE["s"] if face != "front" or x not in (3, 4) else WHITE["h"]
        if face == "front" and y == 10 and x in (3, 4):
            return WHITE["b"]  # the tie's knot
        return weave(P["b"], x, y, 81)

    def leg(face, x, y, W, H):
        if face == "top":
            return P["b"]
        if face == "bottom":
            return None
        if y == 11:
            return P["d"]
        c = weave(P["b"], x, y, 82)
        if face in ("front", "back") and x in (1, 3):
            c = P["s"] if y > 0 else c  # pleats
        if face == "front" and x == 0 and y < 3:
            c = P["h"]
        return tone(c, -0.15) if face in ("right", "left") else c

    paint(l2, A["body"], waist)
    paint(l2, A["leg"], leg)
    return l2


# ---------------------------------------------------------------------------
# Haori: a short black jacket worn open, white cords, our ring crest
# ---------------------------------------------------------------------------

def ring_crest(x, y, cx, cy):
    """A small 4x4 ring: our own crest."""
    dx, dy = x - cx, y - cy
    if (dx, dy) in ((1, 0), (2, 0), (0, 1), (3, 1), (0, 2), (3, 2), (1, 3), (2, 3)):
        return True
    return False


def haori():
    l1 = layer()
    P = pal("#1f2027", "#33343e", "#15161b", "#0c0c10")

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["h"]
        if face == "front":
            if x in (3, 4):
                if y == 4:
                    return WHITE["h"] if x == 3 else WHITE["s"]  # the cords (himo)
                if y == 5 and x == 3:
                    return WHITE["s"]
                return None  # worn open
            if x in (2, 5):
                return P["h"]  # lapels
            if ring_crest(x, y, 0 if x < 4 else 4, 1) and 1 <= y <= 4 and x not in (3, 4):
                return WHITE["b"]
        if face == "back" and ring_crest(x, y, 2, 1):
            return WHITE["b"]
        c = weave(P["b"], x, y, 91)
        if y == 11:
            c = P["d"]
        return tone(c, -0.15) if face in ("right", "left") else c

    def arm(face, x, y, W, H):
        if face == "bottom" or y > 8:
            return None
        if face == "top":
            return P["h"]
        if y == 8:
            return P["s"]
        if face == "right" and (x, y) in ((1, 2), (2, 2), (1, 3), (2, 3)):
            return WHITE["s"] if (x + y) % 2 else WHITE["b"]  # a small crest on the sleeve
        c = weave(P["b"], x, y, 92)
        return tone(c, -0.35) if face == "left" else c

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Vests: the jonin vest and one per village look
# ---------------------------------------------------------------------------

VESTS = {
    # name: (base, accent, options)
    "jonin_vest": ("#3e4a2c", "#232a19", {"collar": "pad", "pouches": 2, "pads": True, "back": "straps"}),
    "vest_sand": ("#b89a66", "#6e5232", {"collar": None, "pouches": 1, "strap": True, "pad_right": True}),
    "vest_snow": ("#d4dbe4", "#506e9e", {"collar": "fur", "pouches": 1, "quilt": True}),
    "vest_mist": ("#5c6c7c", "#344050", {"collar": "pad", "pouches": 1, "ribs": True}),
    "vest_cloud": ("#e2dccb", "#7e7a6c", {"collar": None, "pouches": 2, "strap": True}),
    "vest_stone": ("#8a3e2a", "#4e2216", {"collar": "pad", "pouches": 1, "sleeve": True}),
}


def vest(base_hex, accent_hex, o):
    B, C = pal(base_hex), pal(accent_hex)
    l1 = layer()

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            if o.get("collar") == "fur":
                return weave(WHITE["h"], x, y, 101)
            return B["h"]
        if y == 0:
            if o.get("collar") == "fur":
                return weave(WHITE["b"] if (x + y) % 2 else WHITE["h"], x, y, 102)
            if o.get("collar") == "pad":
                return B["d"] if face == "front" and x in (3, 4) else B["h"]
        if o.get("strap") and face in ("front", "back"):
            # a strap from the left shoulder down to the right hip
            sx = x if face == "front" else W - 1 - x
            if sx + y in (7, 8) and y <= 8:
                return C["b"] if sx + y == 7 else C["s"]
        if face == "front":
            if x == 3:
                return C["b"]  # the closing edge
            if x == 4:
                return B["s"]
            rows = (2, 6) if o.get("pouches", 1) >= 2 else (5, 8)
            if rows[0] <= y <= rows[1] and x in (0, 1, 2, 5, 6, 7):
                if y == rows[0]:
                    return B["h"]
                if y == rows[1]:
                    return C["s"]
                if x in (1, 6) and o.get("pouches", 1) >= 2:
                    return B["s"]
                return B["b"]
            if y == 9:
                return C["s"]
        if face == "back":
            if o.get("back") == "straps" and y in (3, 7):
                return C["b"]
            if y == 1:
                return B["s"]
        if face in ("right", "left") and y in (1, 2):
            return B["d"]
        c = weave(B["b"], x, y, 103)
        if o.get("ribs") and x % 2 == 0:
            c = tone(c, -0.35)
        if o.get("quilt") and y % 3 == 0:
            c = tone(c, -0.35)
        if y == 11:
            c = C["s"]
        return c

    def arm(face, x, y, W, H):
        if face == "bottom":
            return None
        if o.get("sleeve") and y <= 6:
            if face == "top":
                return B["h"]
            return C["s"] if y == 6 else weave(B["b"], x, y, 104)
        if o.get("pads") or o.get("pad_right"):
            if y > 2 or face == "left":
                return None
            if face == "top":
                return C["h"]
            return C["b"] if y < 2 else C["d"]
        if face == "left" or y > 1:
            return None
        if face == "top":
            return B["h"]
        return B["b"] if y == 0 else B["s"]

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)
    if o.get("pad_right") or o.get("sleeve"):
        # The armour model draws the same arm picture on both arms; the one-shouldered look is
        # suggested by the strap, so both arms get it. Nothing else to do.
        pass
    return l1


# ---------------------------------------------------------------------------
# Cloaks: a plain travelling cloak and the rogue cloak (our own crescent-moon pattern)
# ---------------------------------------------------------------------------

MOON = ["..ss", ".s..", ".s..", "..ss"]  # a crescent, 4x4


def moon_at(x, y, spots):
    for cx, cy in spots:
        dx, dy = x - cx, y - cy
        if 0 <= dx < 4 and 0 <= dy < 4 and MOON[dy][dx] == "s":
            return True
        # a pale violet rim right of the crescent's horns
        if 0 <= dx - 1 < 4 and 0 <= dy < 4 and MOON[dy][dx - 1] == "s" and not (0 <= dx < 4 and MOON[dy][dx] == "s"):
            return None
    return False


def cloak(base_hex, lining_hex, clasp, moons=False):
    P, L = pal(base_hex), pal(lining_hex)
    SILVER = hx("#c8ccd6")
    RIM = hx("#7d6a9e")
    l1 = layer()
    spots = {"front": [(0, 4), (4, 8)], "back": [(1, 2), (4, 6)], "right": [(0, 5)], "left": [(0, 8)]}
    arm_spots = {"right": [(0, 3)], "front": [(0, 7)], "back": [(0, 7)], "left": []}

    def pattern(face, x, y, table):
        if not moons:
            return None
        m = moon_at(x, y, table.get(face, []))
        if m is True:
            return SILVER
        if m is None:
            return RIM
        return None

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["h"]
        if face == "front":
            if y <= 1:
                if x in (3, 4) and y == 1:
                    return clasp  # the clasp
                return L["s"] if moons else P["h"]  # a high collar
            if x in (3, 4) and y >= 9:
                return L["b"]  # the cloak parts at the bottom: lining shows
            if x == 3 and y >= 2:
                return P["s"]
        if face == "back" and y <= 2 and not moons:
            return P["s"] if y < 2 else P["d"]  # a folded hood
        p = pattern(face, x, y, spots)
        if p is not None:
            return p
        c = weave(P["b"], x, y, 111)
        if y == 11:
            c = L["s"] if moons else P["d"]
        return tone(c, -0.2) if face in ("right", "left") else c

    def arm(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return P["h"]
        if face == "left":
            return L["s"] if y > 2 else P["s"]  # the lining inside
        p = pattern(face, x, y, arm_spots)
        if p is not None:
            return p
        c = weave(P["b"], x, y, 112)
        return P["d"] if y == 11 else c

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Kage robe: long white robe with gold trim and wide sleeves
# ---------------------------------------------------------------------------

def kage_robe():
    l1 = layer()

    def body(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return WHITE["h"]
        if y == 8:
            return hx("#8c8a92")  # a grey sash
        if face == "front":
            if y < len(KIMONO_FRONT):
                ch = KIMONO_FRONT[y][x]
                if ch == ".":
                    return None
                if ch == "T":
                    return GOLD["h"] if y == 0 else GOLD["b"]
            if y >= 9 and x == 5:
                return GOLD["s"]
        c = weave(WHITE["b"], x, y, 121, 0.7)
        if y == 11:
            c = GOLD["s"]
        return tone(c, -0.25) if face in ("right", "left") else c

    def arm(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return WHITE["h"]
        if y == 11:
            return GOLD["b"]
        c = weave(WHITE["b"], x, y, 122, 0.7)
        return tone(c, -0.4) if face == "left" else c

    paint(l1, A["body"], body)
    paint(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Arm guards and hand wraps (chest slot: only the forearms are covered)
# ---------------------------------------------------------------------------

def arm_guards(steel=True):
    l1 = layer()

    def arm(face, x, y, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return WRAPC
        if steel:
            if y < 5:
                return None
            if y == 5:
                return STRAP
            if 6 <= y <= 9 and face != "left":
                if y == 6:
                    return STEEL["h"]
                if y == 9:
                    return STEEL["d"]
                if face == "right" and x in (1, 2) and y == 7:
                    return STEEL["s"]  # a raised ridge
                return STEEL["b"] if face != "back" else STEEL["s"]
            return wraps(x, y)
        if y < 7:
            return None
        return wraps(x, y)

    paint(l1, A["arm"], arm)
    return l1


# ---------------------------------------------------------------------------
# Boots: tabi, straw sandals with leg wraps, steel shin guards
# ---------------------------------------------------------------------------

def tabi():
    l1 = layer()
    P = pal("#23273a", "#363c58", "#181b28", "#0e1018")

    def boot(face, x, y, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return hx("#c9c4b8")
        if y < 6:
            return None
        if y == 6:
            return P["h"]
        if face == "front" and y >= 10 and x == 1:
            return P["d"]  # the split toe
        c = weave(P["b"], x, y, 131)
        if face in ("right", "left") and y == 9 and mirror_side(face, x, W) < 2:
            c = STEEL["s"]  # the clasps at the ankle
        return P["s"] if y == 11 else c

    paint(l1, A["leg"], boot)
    return l1


def waraji():
    l1 = layer()
    STRAW = pal("#c8a860", "#e0c47c", "#9c7e40", "#6a5428")

    def boot(face, x, y, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return STRAW["b"] if (x + y) % 2 else STRAW["s"]
        if y < 5:
            return None
        if y == 11:
            return STRAW["s"] if x % 2 else STRAW["b"]
        if y == 10:
            if face == "front":
                return STRAW["h"] if x in (1, 2) else None  # thong between the toes
            return STRAW["d"] if face == "back" else None
        if (x + y) % 3 == 0:
            return tone(WRAPC, -0.8)  # crossed ties over the leg wraps
        return wraps(x, y)

    paint(l1, A["leg"], boot)
    return l1


def shin_guards():
    l1 = layer()
    LEATHER = pal("#4a3526", "#634836", "#33241a", "#20160f")

    def boot(face, x, y, W, H):
        if face == "top":
            return None
        if face == "bottom":
            return LEATHER["d"]
        if y < 3:
            return None
        if y >= 9:
            return LEATHER["h"] if y == 9 else LEATHER["b"] if y < 11 else LEATHER["s"]
        if face == "front":
            if y == 3:
                return STEEL["h"]
            if x in (0, 3):
                return STEEL["s"]
            return STEEL["h"] if (x == 1 and y % 2) else STEEL["b"]
        mx = mirror_side(face, x, W)
        if face in ("right", "left") and mx >= 2:
            return STEEL["s"] if y > 3 else STEEL["b"]
        if y in (4, 7):
            return STRAP  # straps round the back
        return None

    paint(l1, A["leg"], boot)
    return l1


# ---------------------------------------------------------------------------
# Masks (helmet). Front maps: w porcelain, s shade, e eye hole, r / k / g markings, . open
# ---------------------------------------------------------------------------

PORC = pal("#ecebe6", "#ffffff", "#c9c7c0", "#8e8b84")

ANIMAL_MASKS = {
    "mask_wolf": ([
        ".r....r.",
        "rwwwwwwr",
        "wwrwwrww",
        "wrwwwwrw",
        "weewweew",
        "wwwsswww",
        "wwrssrww",
        ".wwkkww.",
    ], {"r": "#4a6a9a", "k": "#1c1c22"}),
    "mask_hawk": ([
        "..wwww..",
        ".wkkkkw.",
        "wwrwwrww",
        "wrrwwrrw",
        "weewweew",
        "wwwggwww",
        ".wwggww.",
        "...gg...",
    ], {"r": "#b02a2a", "k": "#2a2a30", "g": "#c9a03a"}),
    "mask_boar": ([
        "r.wwww.r",
        "wwwwwwww",
        "wrrwwrrw",
        "wwwwwwww",
        "weewweew",
        "wwkkkkww",
        "wkskkskw",
        ".W.ww.W.",
    ], {"r": "#7a4a2a", "k": "#8a5a3a", "s": "#3a2418", "W": "#ffffff"}),
    "mask_crane": ([
        "...rr...",
        "..wrrw..",
        ".wwwwww.",
        "wkkwwkkw",
        "weewweew",
        "wwwwwwww",
        ".wwkkww.",
        "...kk...",
    ], {"r": "#c0282d", "k": "#1c1c22"}),
    "oni_mask": ([
        "H......H",
        "HrrrrrrH",
        "rkkrrkkr",
        "rrkrrkrr",
        "reyrryer",
        "rrrddrrr",
        "rWdWWdWr",
        ".rWrrWr.",
    ], {"r": "#a82a22", "k": "#1a1414", "y": "#e8c040", "d": "#4a0e0c", "W": "#f4f0e4", "H": "#e8dcc0",
        "w": "#a82a22", "s": "#7c1c16"}),
}


def face_mask(rows, colours, base=None):
    cv = layer()
    cmap = {k: hx(v) for k, v in colours.items()}
    base = base or PORC
    edge = cmap.get("s", base["s"]) if "w" in colours else base["s"]

    def head(face, x, y, W, H):
        if face == "front":
            ch = rows[y][x]
            if ch == ".":
                return None
            if ch == "e":
                return HOLE
            if ch == "w":
                c = cmap.get("w", base["b"])
                return tone(c, -0.6) if x in (0, 7) or y == 7 else c
            if ch == "s" and "s" not in cmap:
                return base["s"]
            return cmap.get(ch, base["b"])
        if face in ("right", "left"):
            mx = mirror_side(face, x, W)
            if mx == 7 and 1 <= y <= 6:
                return edge  # the mask's edge seen from the side
            return STRAP if y == 3 else None
        if face == "back":
            if y == 3:
                return STRAP
            if (x, y) in ((3, 4), (4, 4)):
                return tone(STRAP, 0.5)
        return None

    paint(cv, A["head"], head)
    return cv


def cloth_mask():
    cv = layer()
    P = pal("#2e3240", "#434858", "#20232e", "#14161d")

    def head(face, x, y, W, H):
        if face in ("top", "bottom") or y < 5:
            return None
        if face == "front":
            if y == 5:
                return P["h"] if x in (3, 4) else P["b"]  # over the bridge of the nose
            return weave(P["b"], x, y, 141)
        if face == "back" and y > 6:
            return None
        return weave(P["s"] if face == "back" else P["b"], x, y, 142)

    paint(cv, A["head"], head)
    return cv


# ---------------------------------------------------------------------------
# Forehead protectors: slashed plate (rogues) and a head wrap with a plate
# ---------------------------------------------------------------------------

def plate(x, y, slashed=False):
    """The 6x3 steel plate at front x 1..6, y 1..3 (our hook glyph), or None outside it."""
    if not (1 <= x <= 6 and 1 <= y <= 3):
        return None
    if slashed and (x, y) in ((1, 3), (2, 3), (3, 2), (4, 2), (5, 1), (6, 1)):
        return STEEL["d"] if (x, y) not in ((3, 2), (4, 2)) else hx("#33363c")  # a cut straight through
    if (x, y) in ((1, 1), (6, 1), (1, 3), (6, 3)):
        return STEEL["d"]
    if (x, y) in ((3, 2), (4, 2), (4, 1)):
        return STEEL["d"]
    if y == 1:
        return STEEL["h"]
    if y == 3:
        return STEEL["s"]
    return STEEL["b"] if x not in (1, 6) else tone(STEEL["b"], -0.4)


def slashed_band():
    cv = layer()
    cloth = pal("#3a3a42")

    def fn(face, x, y, W, H):
        if face in ("top", "bottom"):
            return None
        if face == "front":
            p = plate(x, y, slashed=True)
            if p is not None:
                return p
        if y == 2:
            return weave(cloth["h"] if face != "back" else cloth["b"], x, y, 3)
        if y == 3:
            return weave(cloth["s"], x, y, 4)
        if face == "back":
            if (x, y) in ((3, 4), (4, 4)):
                return cloth["b"]
            if (x, y) in ((3, 5), (4, 6), (3, 7), (4, 5)):
                return cloth["s"]
        return None

    paint(cv, A["head"], fn)
    return cv


def head_wrap():
    cv = layer()
    cloth = pal("#4b4f5e", "#62677a", "#383b47", "#24262e")

    def fn(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            return weave(cloth["h"], x, y, 151)
        if face == "front":
            p = plate(x, y)
            if p is not None:
                return p
            return weave(cloth["b"], x, y, 152) if y <= 3 else None
        if face == "back":
            if y <= 6:
                return weave(cloth["s"], x, y, 153)
            return cloth["d"] if x in (3, 4) else None  # the tied ends
        mx = mirror_side(face, x, W)
        if y <= 3 or (mx < 4 and y <= 6):
            return weave(cloth["b"], x, y, 154)
        return None

    paint(cv, A["head"], fn)
    return cv


# ---------------------------------------------------------------------------
# Hats (the outer "hat" box sits a little outside the head): straw hat and the Kage hats
# ---------------------------------------------------------------------------

STRAW = pal("#cdb070", "#e6cc8a", "#a08648", "#6e5a2c")

KAGE_HATS = {
    # name: panel colour (matches the village looks' paint)
    "kage_hat_red": "#b8302a",     # Leaf look
    "kage_hat_orange": "#d07a2a",  # Sand look
    "kage_hat_blue": "#3a5aa8",    # Snow look
    "kage_hat_teal": "#2a8a8a",    # Mist look
    "kage_hat_gold": "#d8b030",    # Cloud look
    "kage_hat_brown": "#7a4e2e",   # Stone look
}


def straw_hat():
    cv = layer()

    def hat(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            r = max(abs(x - 3.5), abs(y - 3.5))
            c = STRAW["h"] if r < 1 else STRAW["b"] if r < 2.5 else STRAW["s"]
            return c if (x + y) % 3 else tone(c, -0.4)  # woven
        if y > 1:
            return None
        return STRAW["s"] if y == 1 else (STRAW["b"] if x % 2 else STRAW["h"])

    def strings(face, x, y, W, H):
        if face in ("right", "left") and mirror_side(face, x, W) == 4 and 3 <= y <= 7:
            return hx("#6a5428")  # the chin cord
        return None

    paint(cv, A["hat"], hat)
    paint(cv, A["head"], strings)
    return cv


def kage_hat(panel_hex):
    cv = layer()
    P = pal(panel_hex)

    def hat(face, x, y, W, H):
        if face == "bottom":
            return None
        if face == "top":
            if x in (0, 7) or y in (0, 7):
                return WHITE["s"]
            if 2 <= x <= 5 and 2 <= y <= 5:
                return P["h"] if (x + y) % 4 == 0 else P["b"]  # the coloured crown
            return WHITE["b"]
        if y > 2:
            return None
        if face == "front":
            if abs(x - 3.5) + y <= 2.5:
                return P["b"] if y else P["h"]  # a coloured panel at the front
            return WHITE["h"] if y < 2 else WHITE["s"]
        return WHITE["b"] if y < 2 else P["s"]

    def veil(face, x, y, W, H):
        if face == "back" and 1 <= y <= 7:
            return weave(WHITE["b"], x, y, 161, 0.6) if y < 7 else WHITE["s"]
        if face in ("right", "left") and mirror_side(face, x, W) < 3 and 1 <= y <= 6:
            return weave(WHITE["s"], x, y, 162, 0.6)
        return None

    paint(cv, A["hat"], hat)
    paint(cv, A["head"], veil)
    return cv


# ---------------------------------------------------------------------------
# All the textures. Returns {texture: (layer1 or None, layer2 or None)}
# ---------------------------------------------------------------------------

def textures():
    tex = {}
    for i, (name, (robe, trim, obi, motif)) in enumerate(KIMONOS.items()):
        tex["kimono_" + name] = kimono(robe, trim, obi, motif, 200 + i * 10)
    tex["hakama"] = (None, hakama())
    tex["haori"] = (haori(), None)
    for name, (base, accent, o) in VESTS.items():
        tex[name] = (vest(base, accent, o), None)
    tex["travel_cloak"] = (cloak("#6b5038", "#4a3626", GOLD["b"]), None)
    tex["rogue_cloak"] = (cloak("#17161c", "#3c2f52", hx("#c8ccd6"), moons=True), None)
    tex["kage_robe"] = (kage_robe(), None)
    tex["arm_guards"] = (arm_guards(True), None)
    tex["hand_wraps"] = (arm_guards(False), None)
    tex["tabi"] = (tabi(), None)
    tex["waraji"] = (waraji(), None)
    tex["shin_guards"] = (shin_guards(), None)
    for name, (rows, colours) in ANIMAL_MASKS.items():
        tex[name] = (face_mask(rows, colours), None)
    tex["cloth_mask"] = (cloth_mask(), None)
    tex["forehead_protector_slashed"] = (slashed_band(), None)
    tex["head_wrap_protector"] = (head_wrap(), None)
    tex["straw_hat"] = (straw_hat(), None)
    for name, panel in KAGE_HATS.items():
        tex[name] = (kage_hat(panel), None)
    return tex


# ---------------------------------------------------------------------------
# Icons (16x16)
# ---------------------------------------------------------------------------

def icon(template, colors, detail=None):
    cv = Canvas(16, 16)
    for y, row in enumerate(template):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = colors.get(ch)
            if detail:
                d = detail(x, y, ch)
                if d is not None:
                    c = d
            if c is not None:
                cv.set(x, y, c)
    return cv


def cols(p, outline=None):
    return {"o": outline or tone(p["d"], -0.6), "h": p["h"], "b": p["b"], "s": p["s"]}


CLOAK_ICON = [
    "................",
    ".....oooooo.....",
    "....ohhhbbso....",
    "...ohhbbbbsso...",
    "..ohhbbbbbbsso..",
    "..ohbbbbbbbbso..",
    ".ohhbbbbbbbbsso.",
    ".ohbbbbbbbbbbso.",
    ".ohbbbbbbbbbbso.",
    "ohhbbbbbbbbbbsso",
    "ohbbbbbbbbbbbbso",
    "ohbbbbbbbbbbbbso",
    "ohbbbbbbbbbbbbso",
    "ohbbbbbbbbbbbsso",
    "oooooooooooooooo",
    "................",
]
HAT_ICON = [
    "................",
    "................",
    "................",
    "................",
    ".......oo.......",
    "......ohho......",
    ".....ohhbso.....",
    "....ohhbbbso....",
    "...ohhbbbbbso...",
    "..ohhbbbbbbbso..",
    ".ohbbbbbbbbbbso.",
    "oooooooooooooooo",
    "................",
    "................",
    "................",
    "................",
]
WRAPS_ICON = [
    "................",
    "................",
    "..oooo....oooo..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..ohso....ohso..",
    "..owwo....owwo..",
    "..owwo....owwo..",
    ".owwwwo..owwwwo.",
    ".owwwwo..owwwwo.",
    "..oooo....oooo..",
    "................",
    "................",
]
FACE_ICON = [  # a mask: the 8x8 face map is drawn into the middle, scaled 1.5x by this outline
    "................",
    "....oooooooo....",
    "...oFFFFFFFFo...",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "..oFFFFFFFFFFo..",
    "...oFFFFFFFFo...",
    "...oFFFFFFFFo...",
    "....oFFFFFFo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]


def icons(base):
    """base: gen_gear's CHEST / LEGS / BOOTS / BAND templates (dict)."""
    CHEST, LEGS, BOOTS, BAND = base["CHEST"], base["LEGS"], base["BOOTS"], base["BAND"]
    out = {}

    for name, (robe, trim, obi, motif) in KIMONOS.items():
        P, T, O = pal(robe), pal(trim), pal(obi)

        def k_detail(x, y, ch, T=T, O=O, P=P, motif=hx(motif)):
            if ch == "o":
                return None
            if 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)):
                return T["b"]
            if y in (7, 8):
                return O["h"] if y == 7 else O["s"]
            if y >= 9 and x == 9:
                return P["d"]
            if noise(x, y, 7) > 0.88 and ch == "b":
                return motif
            return None
        out["kimono_" + name] = icon(CHEST, cols(P), k_detail)

        def h_detail(x, y, ch, T=T, P=P):
            if ch == "o":
                return None
            if y == 12:
                return T["s"]
            if y == 2:
                return O["s"]
            return None
        out["kimono_" + name + "_hem"] = icon(LEGS, cols(P), h_detail)

    hk = pal("#34343e", "#4a4a58", "#25252d", "#18181e")

    def hakama_detail(x, y, ch):
        if y == 2 and ch in "bhs":
            return WHITE["b"]
        if ch == "b" and x in (5, 10) and y > 3:
            return hk["s"]
        return None
    out["hakama"] = icon(LEGS, cols(hk), hakama_detail)

    hp = pal("#1f2027", "#33343e", "#15161b", "#0c0c10")

    def haori_detail(x, y, ch):
        if ch == "o":
            return None
        if x in (7, 8) and y >= 3:
            return WHITE["h"] if y == 6 else hx("#5a5a64")
        if x in (6, 9) and y >= 2:
            return hp["h"]
        if (x, y) in ((4, 4), (5, 4), (4, 5), (5, 5)):
            return WHITE["b"]
        return None
    out["haori"] = icon(CHEST, cols(hp, hx("#08080a")), haori_detail)

    for name, (b, a, o) in VESTS.items():
        B, C = pal(b), pal(a)

        def v_detail(x, y, ch, B=B, C=C, o=o):
            if ch == "o":
                return None
            if x == 7 and y >= 2:
                return C["b"]
            if o.get("strap") and x + y in (13, 14) and 2 <= y <= 11:
                return C["s"]
            if 7 <= y <= 11 and x in (4, 5, 6, 9, 10, 11):
                if y == 7:
                    return B["h"]
                if y == 11:
                    return C["s"]
            if y in (2, 3) and ch in "bh":
                return WHITE["h"] if o.get("collar") == "fur" else B["h"]
            if o.get("ribs") and ch == "b" and x % 2 == 0:
                return B["s"]
            if (o.get("pads") or o.get("pad_right") or o.get("sleeve")) and y in (4, 5) and x in (0, 1, 14, 15):
                return C["b"]
            return None
        out[name] = icon(CHEST, cols(B), v_detail)

    tp = pal("#6b5038")

    def travel_detail(x, y, ch):
        if (x, y) in ((7, 2), (8, 2)):
            return GOLD["b"]
        if x in (7, 8) and y >= 11 and ch != "o":
            return tone(tp["d"], 0.3)
        return None
    out["travel_cloak"] = icon(CLOAK_ICON, cols(tp), travel_detail)

    rp = pal("#17161c", "#2a2832", "#100f14", "#08080a")
    moons = {(4, 5), (5, 4), (5, 6), (10, 9), (11, 8), (11, 10), (3, 11), (4, 10), (4, 12)}

    def rogue_detail(x, y, ch):
        if ch == "o":
            return None
        if (x, y) in moons:
            return hx("#c8ccd6")
        if (x - 1, y) in moons and ch != "o":
            return hx("#7d6a9e")
        if y in (1, 2) and ch != "o":
            return hx("#3c2f52")
        if x in (7, 8) and y >= 11:
            return hx("#3c2f52")
        return None
    out["rogue_cloak"] = icon(CLOAK_ICON, cols(rp, hx("#050507")), rogue_detail)

    def robe_detail(x, y, ch):
        if ch == "o":
            return None
        if 2 <= y <= 5 and x in (5 + y - 2, 10 - (y - 2)):
            return GOLD["b"]
        if y == 8:
            return hx("#8c8a92")
        if y >= 9 and x == 9:
            return GOLD["s"]
        if y == 12 or (y in (5, 6) and x in (0, 1, 14, 15)):
            return GOLD["b"]
        return None
    out["kage_robe"] = icon(CHEST, cols(WHITE, hx("#5e5a50")), robe_detail)

    out["arm_guards"] = icon(WRAPS_ICON, {"o": hx("#2a2c32"), "h": STEEL["h"], "s": STEEL["s"], "w": WRAPC},
                             lambda x, y, ch: tone(WRAPC, -0.5) if ch == "w" and (x + y) % 2 else None)
    out["hand_wraps"] = icon(WRAPS_ICON, {"o": hx("#6e685c"), "h": WRAPC, "s": WRAPC, "w": WRAPC},
                             lambda x, y, ch: tone(WRAPC, -0.5) if ch in "hsw" and (x + 2 * y) % 3 == 0 else None)

    tb = pal("#23273a", "#363c58", "#181b28", "#0e1018")
    out["tabi"] = icon(BOOTS, cols(tb), lambda x, y, ch: tb["d"] if y >= 10 and x in (4, 12) and ch != "o" else None)
    out["waraji"] = icon(BOOTS, cols(STRAW),
                         lambda x, y, ch: (WRAPC if (x + y) % 2 else tone(WRAPC, -0.6)) if y < 10 and ch in "hbs" else None)
    out["shin_guards"] = icon(BOOTS, cols(STEEL),
                              lambda x, y, ch: pal("#4a3526")["b"] if y >= 10 and ch in "hbs" else None)

    for name, (rows, colours) in list(ANIMAL_MASKS.items()):
        cmap = {k: hx(v) for k, v in colours.items()}
        base_c = cmap.get("w", PORC["b"])

        def m_detail(x, y, ch, rows=rows, cmap=cmap, base_c=base_c):
            if ch != "F":
                return None
            fx, fy = min(7, (x - 2) * 8 // 12), min(7, (y - 2) * 8 // 10)
            c = rows[fy][fx]
            if c in (".", "w"):
                return base_c
            if c == "e":
                return HOLE
            if c == "s" and "s" not in cmap:
                return PORC["s"]
            return cmap.get(c, base_c)
        out[name] = icon(FACE_ICON, {"o": tone(base_c, -2.5), "F": base_c}, m_detail)

    cm = pal("#2e3240", "#434858", "#20232e", "#14161d")

    def cloth_detail(x, y, ch):
        if ch != "F":
            return None
        if y < 7:
            return None if (x + y) % 7 else None
        return cm["h"] if y == 7 and x in (7, 8) else cm["b"]
    cloth_icon = icon(FACE_ICON, {"o": cm["d"], "F": None}, cloth_detail)
    for y in range(7, 12):  # draw the outline only round the cloth
        for x in range(16):
            if FACE_ICON[y][x] == "o":
                cloth_icon.set(x, y, cm["d"])
    for x in range(3, 13):
        cloth_icon.set(x, 6, cm["d"])
    out["cloth_mask"] = cloth_icon

    band = pal("#3a3a42")
    slash = {(6, 8), (7, 7), (8, 7), (9, 6), (10, 6), (5, 8)}
    out["forehead_protector_slashed"] = icon(BAND, {
        "o": tone(band["d"], -0.8), "h": band["h"], "b": band["b"], "s": band["s"], "t": band["s"],
        "M": STEEL["h"], "m": STEEL["b"], "n": STEEL["s"], "e": STEEL["d"]},
        lambda x, y, ch: hx("#33363c") if (x, y) in slash else None)
    wrap = pal("#4b4f5e", "#62677a", "#383b47", "#24262e")

    def wrap_detail(x, y, ch):
        if y < 5 and 2 <= x <= 13 and y >= 1:
            if y == 1 and not 5 <= x <= 10:
                return None
            if y == 2 and not 3 <= x <= 12:
                return None
            return wrap["h"] if y < 3 else wrap["b"]
        return None
    out["head_wrap_protector"] = icon(BAND, {
        "o": tone(wrap["d"], -0.8), "h": wrap["h"], "b": wrap["b"], "s": wrap["s"], "t": wrap["s"],
        "M": STEEL["h"], "m": STEEL["b"], "n": STEEL["s"], "e": STEEL["d"]}, wrap_detail)

    out["straw_hat"] = icon(HAT_ICON, cols(STRAW),
                            lambda x, y, ch: tone(STRAW["b"], -0.4) if ch == "b" and (x + y) % 3 == 0 else None)
    for name, panel in KAGE_HATS.items():
        P = pal(panel)

        def kh_detail(x, y, ch, P=P):
            if ch == "o":
                return None
            if 5 <= y <= 9 and abs(x - 7.5) <= (y - 4) * 0.6:
                return P["h"] if y < 7 else P["b"]
            return None
        hat = icon(HAT_ICON, cols(WHITE, hx("#5e5a50")), kh_detail)
        for y in range(12, 15):  # the white veil hanging behind
            for x in range(3, 13):
                hat.set(x, y, WHITE["s"] if y < 14 else hx("#5e5a50"))
            hat.set(2, y, hx("#5e5a50"))
            hat.set(13, y, hx("#5e5a50"))
        out[name] = hat
    return out
