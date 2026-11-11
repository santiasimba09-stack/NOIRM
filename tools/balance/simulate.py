#!/usr/bin/env python3
"""
NOIRM survival pacing simulator (the "long arc").

It plays a *normal* survival player minute by minute, using the mod's real numbers. The numbers
are read straight from the code and data (Java constants, mission and encounter JSON), so when
someone retunes a constant the timeline here follows. What a "normal player" does is written
in ASSUMPTIONS below: change those to try other play styles.

Run:   python3 tools/balance/simulate.py              (the table, in Markdown)
       python3 tools/balance/simulate.py --json       (the raw milestones)
       python3 tools/balance/simulate.py --set SKILL=1.0 --set NOIRM_SHARE=0.8   (what-ifs)

It is a model, not the game: fights are decided by comparing power (the same "power" the game
uses for classes D-S) with a skill factor, and XP per fight comes from the hit/damage/kill rules.
Treat the hours as "about right", and the walls, skips and dead ends as the real output.
"""
import argparse
import glob
import json
import math
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "noirm")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "noirm")


# ------------------------------------------------------------------ reading the real numbers

def const(path, name):
    """A `static final` number (or the n-th of an array: name[i]) from a Java file under com/noirm."""
    text = open(os.path.join(JAVA, path), encoding="utf-8").read()
    index = None
    if "[" in name:
        name, index = name[:-1].split("[")
        index = int(index)
    m = re.search(r"\b" + re.escape(name) + r"\s*=\s*([^;,]+?)\s*[;,]", text)
    if index is not None:
        m = re.search(r"\b" + re.escape(name) + r"\s*=\s*\{([^}]*)\}", text)
        return float(m.group(1).split(",")[index].strip().rstrip("fF"))
    if not m:
        raise KeyError(f"{name} not found in {path}")
    expr = m.group(1).strip().rstrip("fFL")
    expr = re.sub(r"(\d)[fFL]\b", r"\1", expr).replace("_", "")
    return float(eval(expr, {"__builtins__": {}}, {}))  # constants like 20 * 60 * 5


def load_json(pattern):
    out = {}
    for f in sorted(glob.glob(os.path.join(DATA, pattern), recursive=True)):
        out[os.path.relpath(f, DATA)[:-5]] = json.load(open(f, encoding="utf-8"))
    return out


def read_game():
    g = {}
    t = "training/TrainingSystem.java"
    for n in ["DRILL_CAP", "HIT_XP", "HEAVY_HIT_BONUS", "DODGE_REAL_XP", "FAST_STRING_XP", "SPRINT_XP", "SPRINT_INTERVAL",
              "REAL_FIGHT_BONUS", "MIN_OPPONENT_RATIO", "MAX_OPPONENT_RATIO", "VITALITY_XP_PER_DAMAGE", "HEALTH_PER_VITALITY",
              "RESERVES_XP_PER_CHAKRA", "CHAKRA_PER_RESERVES", "REGEN_PER_RESERVES"]:
        g[n] = const(t, n)
    g["KILL_XP"] = const("training/KillXp.java", "KILL_XP")
    g["MIN_WORTH"] = const("training/KillXp.java", "MIN_WORTH")
    g["MAX_WORTH"] = const("training/KillXp.java", "MAX_WORTH")
    g["DUR_XP_PER_PERCENT"] = const("training/Durability.java", "XP_PER_PERCENT")
    g["DUR_MAX"] = const("training/Durability.java", "MAX_NEGATION")
    g["DUR_SCALE"] = const("training/Durability.java", "SCALE")
    g["MEDITATION_RES_XP"] = const("senjutsu/Meditation.java", "RESERVES_XP_PER_SECOND")
    g["MEDITATION_REGEN"] = const("senjutsu/Meditation.java", "CHAKRA_REGEN_MULTIPLIER")
    g["CHAKRA_REGEN_PER_TICK"] = const("player/ShinobiData.java", "CHAKRA_REGEN_PER_TICK")
    g["MAX_CHAKRA"] = const("player/ShinobiData.java", "DEFAULT_MAX_CHAKRA")
    g["CTL_WATER_XP"] = const("training/Supplementary.java", "CTL_WATER_XP")
    g["WATER_INTERVAL"] = const("training/Supplementary.java", "WATER_INTERVAL")
    g["CTL_PRESSURE_XP"] = const("training/Supplementary.java", "CTL_PRESSURE_XP_PER_CHAKRA")
    g["DRILL_SECONDS"] = const("nature/NatureDrills.java", "DRILL_SECONDS")
    g["HIDDEN_PACE"] = const("nature/NatureDrills.java", "HIDDEN_PACE")
    g["MASTER_PACE"] = const("nature/NatureDrills.java", "MASTER_TEACHER_PACE")
    g["RESERVES_FOR_RANK"] = [const("nature/JutsuLearning.java", f"RESERVES_FOR_RANK[{i}]") for i in range(6)]
    g["REQUIRED_MASTERY"] = const("clan/NatureSystem.java", "REQUIRED_MASTERY")
    g["OFFERS"] = [const("mission/Missions.java", f"OFFERS_PER_LETTER[{i}]") for i in range(5)]
    g["EXAM_COOLDOWN_MIN"] = const("mission/Exams.java", "COOLDOWN_TICKS") / 1200
    g["ROGUE_RYO"] = const("mission/CareerEvents.java", "ROGUE_RYO")
    g["ROGUE_RYO_BONUS"] = const("mission/CareerEvents.java", "ROGUE_RYO_BONUS")
    w = "world/wild/WildSpawner.java"
    for n in ["BASE_DANGER", "BLOCKS_PER_DANGER", "DISTANCE_DANGER_MAX", "DANGER_PER_HOUR", "TIME_DANGER_MAX", "BASE_CHANCE",
              "ATTEMPT_TICKS", "SAFE_PLAY_MINUTES"]:
        g[n] = const(w, n)
    b = "bounty/BingoBook.java"
    for n in ["A_MIN", "A_MAX", "S_MIN", "S_MAX", "LEGEND_MIN", "LEGEND_MAX", "A_BOUNTY_MIN", "A_BOUNTY_MAX", "S_BOUNTY_MIN",
              "S_BOUNTY_MAX", "LEGEND_BOUNTY_MIN", "LEGEND_BOUNTY_MAX", "LEGEND_NEW_PLAYER_MINUTES", "MIN_DISTANCE",
              "LEGEND_SAFE_RADIUS", "BOOK_PRICE"]:
        g["BINGO_" + n] = const(b, n)
    g["SAGE_BONUS"] = const("bounty/NinjaClass.java", "SAGE")
    g["PER_JUTSU"] = const("bounty/NinjaClass.java", "PER_JUTSU")
    g["MAX_JUTSU"] = const("bounty/NinjaClass.java", "MAX_JUTSU")
    g["RYO_PER_EMERALD"] = const("economy/Shop.java", "RYO_PER_EMERALD")
    g["SANCTUARY_MIN"] = const("world/sanctuary/SanctuaryStructure.java", "MIN_DISTANCE")
    # Ranks: points, invitations (PlayerRank enum lines).
    rank_text = open(os.path.join(JAVA, "mission/PlayerRank.java"), encoding="utf-8").read()
    g["RANKS"] = [(m.group(1).lower(), int(m.group(2)), m.group(3) == "true", None if m.group(4) == "null" else m.group(4).strip('"'),
                   int(m.group(5)))
                  for m in re.finditer(r'\n\s*([A-Z_]+)\("\w+", (\d+), (true|false), (null|"\w"), (\d+)\)', rank_text)]
    exam_text = open(os.path.join(JAVA, "mission/Exams.java"), encoding="utf-8").read()
    g["EXAMS"] = {m.group(1).lower(): [float(x.strip().rstrip("f")) for x in m.group(2).split(",")]
                  for m in re.finditer(r"case (\w+) -> new float\[\] \{([^}]*)\}", exam_text)}
    class_text = open(os.path.join(JAVA, "bounty/NinjaClass.java"), encoding="utf-8").read()
    g["CLASSES"] = [(m.group(1), float(m.group(2)), float(m.group(3)))
                    for m in re.finditer(r"\n\s*([DCBAS])\((\d+)f, (\d+)f, 0x", class_text)]
    g["MISSIONS"] = {k: v for k, v in load_json("missions/*.json").items()}
    g["EXAM_MISSIONS"] = load_json("missions/exam/*.json")
    g["ENCOUNTERS"] = {k.split("/")[-1]: v for k, v in load_json("encounters/*.json").items()}
    g["JUTSU"] = load_json("jutsu/**/*.json")
    g["MASTERS"] = {k.split("/")[-1]: v for k, v in load_json("masters/*.json").items()}
    return g


# ------------------------------------------------------------------ how a normal player plays

ASSUMPTIONS = {
    # Share of play time spent on NOIRM things (the rest: building, mining, farming, the Nether...).
    "NOIRM_SHARE": 0.6,
    # A human reads a fight better than the AI: you beat an NPC up to this much stronger than you.
    "SKILL": 1.15,
    # Minutes to find the first ninja settlement without /locate (spacing 26 chunks, walking and looking).
    "FIND_VILLAGE_MIN": 15,
    # Blocks from the world spawn where the player lives (their village) and hunts wild ninjas.
    "HOME_DISTANCE": 350,
    # How far the player is willing to roam once they are strong (blocks): grows with class.
    "ROAM_BY_CLASS": {"D": 350, "C": 500, "B": 900, "A": 1500, "S": 2500},
    # The player's first clan natures (1 is the most common roll) and their first jutsu's cost.
    "CLAN_NATURES": 1,
    # Light hits mixed with heavies: average raw damage of a landed hit (jab 3, hook 4, heavy 7).
    "AVG_HIT": 4.0,
    # Swings per landed clean hit (guards, misses): 1.5.
    "SWINGS_PER_HIT": 1.5,
    # Landed hits per second at the dummy (combos), and the share of them in fast strings.
    "DUMMY_HITS_PER_SEC": 1.2,
    "STRING_SHARE": 0.5,
    # A won fight costs this share of your health (it trains vitality and durability).
    "DAMAGE_SHARE": 0.45,
    # Jutsu cast per fight, and their average chakra cost.
    "JUTSU_PER_FIGHT": 3,
    "JUTSU_COST": 22,
    # Minutes per wild fight (finding the group, the fight, catching your breath).
    "WILD_FIGHT_MIN": 4.0,
    # Blocks per second when travelling (sprinting, terrain, looking for the marker).
    "TRAVEL_BPS": 3.5,
    # Minutes a mission costs on top of travel and fights (the board, finding the target, the client's pace).
    "MISSION_OVERHEAD_MIN": 2.0,
    # Share of the board's daily offers a normal player takes (they also build, farm, explore).
    "MISSION_APPETITE": 0.7,
    # At most this share of a day's NOIRM time goes to missions; the rest is wild fights and spars.
    "MISSION_SHARE": 0.5,
    # Minutes of meditation per night spent (N) once a nature drill has started (nights are 7 min).
    "MEDITATE_PER_NIGHT": 5.0,
    # The hidden nature: the paper shows one about half the time for the nature a normal player picks.
    "HIDDEN_CHANCE": 0.5,
    # Extra minutes to reach the Stone Hermit once you go (rumour: far, mountain/jungle, searching).
    "HERMIT_SEARCH_MIN": 25,
    # Water walking a normal player does per hour (minutes): trains chakra control below 30.
    "WATER_MIN_PER_HOUR": 3,
}


# ------------------------------------------------------------------ the rules (as the game has them)

def xp_to_next(stat):
    s = math.floor(stat)
    past = max(0.0, s - 50)
    return 20 + 8 * s + 0.05 * past * past


class Player:
    STATS = ["tai", "spd", "vit", "res", "dur", "ctl", "wil"]

    def __init__(self, g):
        self.g = g
        self.s = {k: 10.0 for k in self.STATS}
        self.xp = {k: 0.0 for k in self.STATS}
        self.jutsu = 4  # two clan starters + Substitution + Body Flicker
        self.sage = False
        self.casts = 0.0  # casts of the main clan starter (mastery)

    def grant(self, stat, xp):
        if xp <= 0:
            return
        x = self.xp[stat] + xp
        while x >= xp_to_next(self.s[stat]):
            x -= xp_to_next(self.s[stat])
            self.s[stat] = math.floor(self.s[stat]) + 1
        self.xp[stat] = x

    def drill(self, stat, xp):
        if self.s[stat] < self.g["DRILL_CAP"]:
            self.grant(stat, xp)

    def power(self):
        s = self.s
        stat = 0.25 * s["tai"] + 0.25 * s["spd"] + 0.15 * s["vit"] + 0.15 * s["dur"] + 0.10 * s["res"] + 0.05 * s["ctl"] + 0.05 * s["wil"]
        extras = min(self.g["MAX_JUTSU"], max(0, self.jutsu - 2) * self.g["PER_JUTSU"]) + (self.g["SAGE_BONUS"] if self.sage else 0)
        return stat * (1 + extras)

    def max_health(self):
        return 20 + (self.s["vit"] - 10) * self.g["HEALTH_PER_VITALITY"]


def ninja_power(strength, g, jutsu_bonus=0.06):
    """A wild or mission ninja of this strength (speed = taijutsu = vitality = DUR = strength)."""
    return strength * (1 + jutsu_bonus)


def class_of(power, g):
    letter = "D"
    for name, minimum, _ in g["CLASSES"]:
        if power >= minimum:
            letter = name
    return letter


def worth(power, g):
    return {name: w for name, _, w in g["CLASSES"]}[class_of(power, g)]


def negation(dur, g):
    return 0 if dur <= 10 else g["DUR_MAX"] * (1 - math.exp(-(dur - 10) / g["DUR_SCALE"]))


def danger(g, distance, hours):
    return g["BASE_DANGER"] + min(g["DISTANCE_DANGER_MAX"], distance / g["BLOCKS_PER_DANGER"]) + min(g["TIME_DANGER_MAX"], hours * g["DANGER_PER_HOUR"])


def fight(p, foe_power, a, g):
    """One won fight against a foe of this power: grants the XP the game would. Returns True if won."""
    me = p.power()
    if me * a["SKILL"] < foe_power:
        return False
    ratio = max(g["MIN_OPPONENT_RATIO"], min(g["MAX_OPPONENT_RATIO"], foe_power / max(1, me)))
    factor = ratio * g["REAL_FIGHT_BONUS"]
    strength = foe_power / 1.06
    ehp = (40 + strength - 10) * math.sqrt(max(1, strength / 10)) / (1 - negation(strength, g))
    hits = ehp / (a["AVG_HIT"] * max(0.5, p.s["tai"] / 10)) * a["SWINGS_PER_HIT"]
    p.grant("tai", hits * (g["HIT_XP"] + g["HEAVY_HIT_BONUS"] / 3) * factor)
    p.grant("spd", hits * a["STRING_SHARE"] * g["FAST_STRING_XP"] * factor + hits / 3 * g["DODGE_REAL_XP"] * factor)
    taken = a["DAMAGE_SHARE"] * p.max_health()
    p.grant("vit", taken * g["VITALITY_XP_PER_DAMAGE"] * factor)
    p.grant("dur", 100 * a["DAMAGE_SHARE"] * g["DUR_XP_PER_PERCENT"] * factor)
    p.casts += a["JUTSU_PER_FIGHT"] * 0.5
    spent = a["JUTSU_PER_FIGHT"] * a["JUTSU_COST"]
    p.grant("res", spent * g["RESERVES_XP_PER_CHAKRA"])
    p.grant("ctl", spent / 2 * g["CTL_PRESSURE_XP"])
    pool = g["KILL_XP"] * max(g["MIN_WORTH"], min(g["MAX_WORTH"], worth(foe_power, g) / worth(me, g)))
    for stat, share in (("tai", 0.35), ("spd", 0.25), ("vit", 0.12), ("dur", 0.12), ("res", 0.11), ("ctl", 0.05)):
        p.grant(stat, pool * share)
    if foe_power >= 1.5 * me:
        p.grant("wil", pool * 0.25)
    return True


# ------------------------------------------------------------------ the simulation

def mission_foe(m, g):
    """The strongest enemy a mission puts in front of you (its encounter's leader at the template strength)."""
    enc = m.get("encounter") or m.get("ambush")
    if not enc:
        return 0
    e = g["ENCOUNTERS"][enc.split(":")[1]]
    return ninja_power(m.get("strength", 12) * e.get("leader_strength", 1.0), g)


def mission_minutes(m, a):
    dist = (m.get("min_distance", 100) + m.get("max_distance", 160)) / 2
    kind = m["type"]
    travel = 2 * dist / a["TRAVEL_BPS"] / 60
    if kind == "scout":
        travel *= 1 + 0.5 * (m.get("count", 1) - 1) * 0.5
    if kind == "defend":
        travel = 0
        fights = m.get("count", 1) * 2.0
    elif kind == "escort":
        travel *= 1.6  # walking pace of the client
        fights = 1 if m.get("ambush") else 0
    elif kind in ("hunt", "retrieve"):
        fights = 1.5 if "band" in str(m.get("encounter")) else 1
    else:
        fights = 1 if m.get("ambush") else 0
    return travel + fights * 1.5, fights


def simulate(g, a, max_hours=60, verbose=False):
    p = Player(g)
    curve = {}
    ev = {}
    log = []

    def mark(key, minute):
        if key not in ev:
            ev[key] = round(minute / 60, 2)
            log.append((ev[key], key))

    letters = "DCBAS"
    rank_index = 0
    points = 0
    done = {k: 0 for k in letters}
    ryo = 0
    total_ryo = 0
    exam_cooldown = 0
    drill_progress = 0.0
    drill_needed = g["DRILL_SECONDS"] / 60 / (g["HIDDEN_PACE"] if a["HIDDEN_CHANCE"] >= 0.5 else 1)
    natures = a["CLAN_NATURES"]
    second_jutsu_casts = 0
    village = False
    met_hermit = False
    shopping = [("bingo_book", g["BINGO_BOOK_PRICE"]), ("chakra_paper", g["RYO_PER_EMERALD"]), ("swift_seals", 150),
                ("nature_insight", 250), ("healing_palm", 200), ("c_scroll", 6 * g["RYO_PER_EMERALD"]),
                ("b_scroll", 14 * g["RYO_PER_EMERALD"]), ("nature_scroll", 24 * g["RYO_PER_EMERALD"])]
    bought = set()
    minute = 0.0
    day_len = 20.0
    dummy = False
    first_mission = None
    bounties = 0
    bounty_days = set()
    taken = {}
    mission_time = {}
    while minute < max_hours * 60:
        hours = minute / 60
        for stat in ("tai", "spd", "vit", "res", "dur"):
            if p.s[stat] >= 15:
                mark("a stat at 15", minute)
            if p.s[stat] >= 30:
                mark("a stat at 30 (drill cap)", minute)
            if p.s[stat] >= 50:
                mark("a stat at 50", minute)
        if village:
            mark(f"player is class {class_of(p.power(), g)}", minute)
        if total_ryo >= 1000:
            mark("1,000 ryo earned", minute)
        if total_ryo >= 10000:
            mark("10,000 ryo earned", minute)
        if int(minute) // 60 not in curve:
            h = int(minute) // 60
            curve[h] = (round(p.power(), 1), class_of(p.power(), g),
                        round(ninja_power(danger(g, a["HOME_DISTANCE"], h) * g["ENCOUNTERS"]["rogue"]["leader_strength"], g), 1),
                        round(ninja_power(danger(g, 1200, h) * g["ENCOUNTERS"]["rogue_strong"]["leader_strength"], g), 1), g["RANKS"][rank_index][0])
        day = int(minute // day_len)
        in_day = minute % day_len
        night = in_day >= 13
        # The real-time minute only partly goes to NOIRM.
        step = 1.0
        active = a["NOIRM_SHARE"]
        minute += step
        # Running around always trains a little speed (drill).
        p.drill("spd", 60 * active * 0.3 / (g["SPRINT_INTERVAL"] / 20) * g["SPRINT_XP"])
        if hours * 60 * a["WATER_MIN_PER_HOUR"] / 60 > 0:
            p.drill("ctl", active * a["WATER_MIN_PER_HOUR"] / 60 * 60 / (g["WATER_INTERVAL"] / 20) * g["CTL_WATER_XP"])
        if minute < 2:
            mark("character + Handbook", minute)
            continue
        if not village:
            if minute * active >= a["FIND_VILLAGE_MIN"]:
                village = True
                mark("first village found", minute)
            continue
        cls = class_of(p.power(), g)
        roam = a["ROAM_BY_CLASS"][cls]
        local = danger(g, roam, hours)
        # ---- what this minute is spent on
        budget = active
        # Night: meditate on the nature drill (and reserves); a hidden nature first (chakra paper).
        if night and natures < 2 and "chakra_paper" in bought and in_day < 13 + a["MEDITATE_PER_NIGHT"]:
            pace = g["MASTER_PACE"] if "nature_insight" in bought else 1.0
            drill_progress += budget * pace
            p.drill("res", budget * 60 * g["MEDITATION_RES_XP"])
            if "nature_insight" in bought and "insight_used" not in ev:
                drill_progress += drill_needed * 0.5
                ev["insight_used"] = hours
            mark("first nature drill started", minute)
            if drill_progress >= drill_needed:
                natures += 1
                mark("first drill done = second nature", minute)
            continue
        # Dummy drills while the core stats are under the drill cap (a normal player does some, not all).
        if (p.s["tai"] < g["DRILL_CAP"] or p.s["spd"] < g["DRILL_CAP"]) and (int(minute) % 2 == 0 or rank_index == 0) and not night:
            dummy = True
            hits = a["DUMMY_HITS_PER_SEC"] * 60 * budget * 0.5
            p.drill("tai", hits * (g["HIT_XP"] + g["HEAVY_HIT_BONUS"] / 3))
            p.drill("spd", hits * a["STRING_SHARE"] * g["FAST_STRING_XP"])
            # practising jutsu between strings
            casts = budget * 60 * g["CHAKRA_REGEN_PER_TICK"] * 20 * (1 + (p.s["res"] - 10) * g["REGEN_PER_RESERVES"]) / a["JUTSU_COST"]
            p.casts += casts * 0.5
            p.drill("res", casts * a["JUTSU_COST"] * g["RESERVES_XP_PER_CHAKRA"])
            mark("first training (dummy)", minute)
            if p.casts >= 1:
                mark("first jutsu cast", minute)
            if p.casts >= 10:
                mark("first ★ (10 casts)", minute)
            if p.casts >= 30:
                mark("first ★2 (a mastery chain opens)", minute)
            if p.casts >= 60:
                mark("fire clan: Great Fireball ★3 (60 casts; S-rank chain jutsu, ungated)", minute)
            continue
        # Exams and rank-ups.
        if rank_index + 1 < len(g["RANKS"]):
            nxt = g["RANKS"][rank_index + 1]
            name, need, exam, letter, count = nxt
            if points >= need:
                if exam and minute >= exam_cooldown:
                    rounds = list(g["EXAMS"].get(name, []))
                    if name == "jonin":
                        trial = g["EXAM_MISSIONS"]["missions/exam/jonin_trial"]
                        rounds = [trial["strength"] * g["ENCOUNTERS"]["rogue_strong"]["leader_strength"]] + rounds
                    hardest = max(ninja_power(r, g) for r in rounds)
                    if p.power() * a["SKILL"] < 0.85 * hardest:
                        ev.setdefault("_exam_waits", {}).setdefault(name, round(minute / 60, 1))
                    elif all(p.power() * a["SKILL"] >= ninja_power(r, g) for r in rounds):
                        rank_index += 1
                        mark(f"rank: {name}", minute)
                        for r in rounds:
                            fight(p, ninja_power(r, g), a, g)
                        minute += 4 * len(rounds)
                        continue
                    else:
                        exam_cooldown = minute + g["EXAM_COOLDOWN_MIN"]
                        ev.setdefault("_exam_fails", {}).setdefault(name, 0)
                        ev["_exam_fails"][name] += 1
                        minute += 3
                        continue
                if not exam and done[letter] >= count:
                    rank_index += 1
                    mark(f"rank: {name}", minute)
        # Missions on the board today (one board; the player takes what they can win).
        taken_today = taken
        took = False
        for li in reversed(range(len(letters))):
            letter = letters[li]
            if li > rank_index or (li == 4 and rank_index < 4):
                continue
            key = (day, letter)
            if mission_time.get(day, 0) >= day_len * a["MISSION_SHARE"]:
                break
            if taken_today.get(key, 0) >= max(1, round(g["OFFERS"][li] * a["MISSION_APPETITE"])):
                continue
            pool = [m for k, m in g["MISSIONS"].items() if m["rank"] == letter and not m.get("exam")]
            m = pool[(day * 7 + taken_today.get(key, 0)) % len(pool)]
            foe = mission_foe(m, g)
            if foe and p.power() * a["SKILL"] < foe:
                taken_today[key] = taken_today.get(key, 0) + 1  # can't win it: skipped
                ev.setdefault("_skipped", {}).setdefault(letter, 0)
                ev["_skipped"][letter] += 1
                continue
            mins, fights = mission_minutes(m, a)
            spent = (mins + a["MISSION_OVERHEAD_MIN"]) / max(0.2, a["NOIRM_SHARE"])
            mission_time[day] = mission_time.get(day, 0) + spent
            minute += spent - 1
            for _ in range(int(round(fights))):
                fight(p, foe * 0.9, a, g)
            if foe:
                for c in letters[:letters.index(class_of(foe, g)) + 1]:
                    mark(f"beat class {c}", minute)
            taken_today[key] = taken_today.get(key, 0) + 1
            points += m.get("points", 10)
            ryo += m.get("reward", 100)
            total_ryo += m.get("reward", 100)
            done[letter] += 1
            mark("first mission", minute)
            mark(f"first {letter}-rank mission", minute)
            took = True
            break
        if took:
            pass
        else:
            # Wild fights near home, or roaming once strong. Rogues need 300+ blocks; a group every ~4 minutes.
            enc = g["ENCOUNTERS"]
            foe = ninja_power(local * enc["rogue"]["leader_strength"], g)
            if hours * 60 >= enc["rogue"].get("min_played_minutes", 0):
                if fight(p, foe, a, g):
                    mark("first wild ninja beaten", minute)
                    drop = g["ROGUE_RYO"] + g["ROGUE_RYO_BONUS"] / 2 + foe / 5
                    ryo += drop
                    total_ryo += drop
                    for c in letters[:letters.index(class_of(foe, g)) + 1]:
                        mark(f"beat class {c}", minute)
                else:
                    ev.setdefault("_too_strong_minutes", 0)
                    ev["_too_strong_minutes"] += 1
                    # too strong out there: back to safer ground (a weaker spar near home).
                    fight(p, ninja_power(danger(g, a["HOME_DISTANCE"], 0) , g), a, g)
                minute += a["WILD_FIGHT_MIN"] / max(0.2, a["NOIRM_SHARE"]) - 1
            # Rogue strong (2.2x) out past 1200 blocks once roaming that far: beat it when able.
            if roam >= 1200 and hours * 60 >= enc["rogue_strong"].get("min_played_minutes", 0):
                strong = ninja_power(danger(g, roam, hours) * enc["rogue_strong"]["leader_strength"], g)
                if p.power() * a["SKILL"] >= strong:
                    fight(p, strong, a, g)
                    for c in letters[:letters.index(class_of(strong, g)) + 1]:
                        mark(f"beat class {c}", minute)
        # Bingo book bounties (A from the start of the list; S one in three; legends after the grace time).
        if "bingo_book" in bought and day % 2 == 0 and day not in bounty_days:
            for kind, lo, hi, blo, bhi in (("A", g["BINGO_A_MIN"], g["BINGO_A_MAX"], g["BINGO_A_BOUNTY_MIN"], g["BINGO_A_BOUNTY_MAX"]),
                                           ("S", g["BINGO_S_MIN"], g["BINGO_S_MAX"], g["BINGO_S_BOUNTY_MIN"], g["BINGO_S_BOUNTY_MAX"]),
                                           ("legend", g["BINGO_LEGEND_MIN"], g["BINGO_LEGEND_MAX"], g["BINGO_LEGEND_BOUNTY_MIN"],
                                            g["BINGO_LEGEND_BOUNTY_MAX"])):
                foe = ninja_power(lo, g, 0.1 + (g["SAGE_BONUS"] if kind == "legend" else 0))
                if kind == "legend" and hours * 60 < g["BINGO_LEGEND_NEW_PLAYER_MINUTES"]:
                    continue
                if p.power() * a["SKILL"] >= foe:
                    bounty_days.add(day)
                    fight(p, foe, a, g)
                    bounty = (blo + bhi) / 2
                    ryo += bounty
                    total_ryo += bounty
                    bounties += 1
                    minute += 15
                    mark("first bounty claimed", minute)
                    if kind == "legend":
                        mark("beat a legend (S+)", minute)
                    for c in letters[:letters.index(class_of(foe, g)) + 1]:
                        mark(f"beat class {c}", minute)
                    break
        # Shopping (in order of what a new player wants).
        for item, price in shopping:
            need_rank = {"swift_seals": 1, "nature_insight": 1, "healing_palm": 1, "b_scroll": 2}.get(item, 0)
            if item not in bought and ryo >= price and rank_index >= need_rank:
                ryo -= price
                bought.add(item)
                mark(f"bought: {item}", minute)
                if item in ("c_scroll", "b_scroll", "healing_palm"):
                    p.jutsu += 1
        # Second nature: its first jutsu to ★2 (30 casts) opens the combined nature in the tree.
        if natures >= 2:
            second_jutsu_casts += budget * 2.0
            if second_jutsu_casts >= 30 and p.casts >= 30:
                mark("combined nature (kekkei genkai) unlocked", minute)
        # Reserves gate for A/S jutsu from scrolls, rank gates (B chūnin, A jōnin, S ANBU).
        if p.casts >= 60 and p.s["res"] >= g["RESERVES_FOR_RANK"][5]:
            mark("fire clan: S-rank chain jutsu with the Reserves 40 gate", minute)
        if p.s["res"] >= g["RESERVES_FOR_RANK"][4] and rank_index >= 3:
            mark("A-rank jutsu (scroll or Rensai, gates met)", minute)
        if p.s["res"] >= g["RESERVES_FOR_RANK"][5] and rank_index >= 4:
            mark("S-rank jutsu (scroll, gates met)", minute)
        # Stone Hermit: a normal player goes once they roam 1,500+ blocks; Sage Mode needs CTL 20, TAI 15.
        if not met_hermit and roam >= 1500:
            met_hermit = True
            minute += a["HERMIT_SEARCH_MIN"]
            mark("met the Stone Hermit", minute)
        if met_hermit and not p.sage and p.s["ctl"] >= 20 and p.s["tai"] >= 15:
            p.sage = True
            mark("Sage Mode", minute)
    ev["_curve"] = curve
    ev["_final"] = {"power": round(p.power(), 1), "stats": {k: round(v) for k, v in p.s.items()}, "rank": g["RANKS"][rank_index][0],
                    "points": points, "ryo_earned": round(total_ryo), "bounties": bounties}
    return ev, log


# ------------------------------------------------------------------ chain shortcuts (jutsu, independent of the sim)

def chain_shortcuts(g):
    """Casts of a basic-nature starter needed to reach an A- or S-rank jutsu by mastery chains alone."""
    level_xp = {1: 10, 2: 30, 3: 60, 4: 100, 5: 150}
    J = {"noirm:" + k.split("jutsu/", 1)[1]: v for k, v in g["JUTSU"].items()}
    out = []
    for jid, d in J.items():
        if d.get("rank") not in ("A", "S") or d.get("nature") not in ("fire", "water", "wind", "earth", "lightning"):
            continue
        casts, cur, steps = 0, jid, []
        while True:
            u = J[cur].get("unlock")
            if not u or "after" not in u:
                break
            casts += level_xp[u.get("mastery", 2)]
            steps.append(cur)
            cur = u["after"]
        starter = J[cur].get("starter", False)
        out.append((casts if starter else None, jid, d["rank"], d["nature"], cur, J[cur].get("rank")))
    return sorted(out, key=lambda r: (r[0] is None, r[0] or 0))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--hours", type=float, default=60)
    ap.add_argument("--set", action="append", default=[], help="override an assumption, e.g. SKILL=1.0")
    ap.add_argument("--root", help="read the numbers from another checkout (e.g. an older commit, to compare)")
    args = ap.parse_args()
    if args.root:
        global JAVA, DATA
        JAVA = os.path.join(args.root, "src", "main", "java", "com", "noirm")
        DATA = os.path.join(args.root, "src", "main", "resources", "data", "noirm")
    g = read_game()
    a = dict(ASSUMPTIONS)
    for kv in args.set:
        k, v = kv.split("=", 1)
        a[k] = type(a[k])(v) if not isinstance(a[k], dict) else json.loads(v)
    ev, log = simulate(g, a, args.hours)
    if args.json:
        print(json.dumps({"milestones": {k: v for k, v in ev.items()}, "assumptions": a}, indent=1, default=str))
        return
    print("| Milestone | Hours of play | In-game days |")
    print("|---|---:|---:|")
    for hours, key in log:
        print(f"| {key} | {hours:.1f} | {hours * 3:.0f} |")
    wanted = ["rank: genin", "rank: chunin", "rank: jonin", "rank: anbu", "rank: kage_candidate", "first drill done = second nature",
              "combined nature (kekkei genkai) unlocked", "A-rank jutsu (scroll or Rensai, gates met)", "S-rank jutsu (scroll, gates met)",
              "Sage Mode", "beat class B", "beat class A", "beat class S", "beat a legend (S+)"]
    missing = [w for w in wanted if w not in ev]
    print()
    print(f"Not reached in {args.hours:.0f} h: " + (", ".join(missing) if missing else "none"))
    print(f"Final: {ev['_final']}")
    print()
    print("| Hour | Your power (class) | Rank | Wild rogue near home (350 blocks) | Strong rogue at 1,200 blocks |")
    print("|---:|---|---|---:|---:|")
    for h in sorted(ev["_curve"]):
        if h <= 12 or h % 4 == 0:
            pw, c, home, strong, rank = ev["_curve"][h]
            print(f"| {h} | {pw} ({c}) | {rank} | {home} ({class_of(home, g)}) | {strong} ({class_of(strong, g)}) |")
    print(f"Exam ready on points but too weak to try, from hour: {ev.get('_exam_waits', {})}")
    print(f"Exam fails: {ev.get('_exam_fails', {})}; missions skipped as unwinnable: {ev.get('_skipped', {})}; "
          f"minutes where local wild ninjas were too strong: {ev.get('_too_strong_minutes', 0)}")
    print()
    print("Mastery-chain routes (casts of a clan starter; from casting, the jutsu also waits for the Reserves of its rank: B 15, A 25, S 40):")
    for casts, jid, rank, nature, root, root_rank in chain_shortcuts(g)[:8]:
        print(f"  {jid} ({rank}) from {root} ({root_rank}): {casts if casts is not None else '-'} casts")


if __name__ == "__main__":
    main()
