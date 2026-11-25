"""How an Aburame's colony grows over play time (docs/ABURAME.md, 2.2 and 2.13).

    python3 tools/balance/colony.py

Reads the tuning from aburame/ColonyGrowth.java and the hive ranks from aburame/HiveRank.java, then plays
an hour-by-hour Aburame: breeding in the body (Vigour 70), stats rising slowly (Reserves and Control),
draining some chakra in fights (+6 insects per chakra), jars, a gourd and hives as they become
affordable. It prints the colony per hour and when each hive rank's number is reached (the deeds are
separate). The check: every rank's number is reachable in a playthrough (under ~60 hours).
"""
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "src/main/java/com/noirm/aburame")


def const(name, file="ColonyGrowth.java"):
    text = open(os.path.join(SRC, file)).read()
    m = re.search(r"\b" + name + r"\s*=\s*([0-9_.]+)f?", text)
    return float(m.group(1).replace("_", ""))


def ranks():
    text = open(os.path.join(SRC, "HiveRank.java")).read()
    return [(n, int(v.replace("_", ""))) for n, v in re.findall(r"^\s+([A-Z_]+)\((\d[\d_]*),", text, re.M)]


def main():
    base, per_res, per_ctl = const("BODY_BASE"), const("BODY_PER_RESERVES"), const("BODY_PER_CONTROL")
    share, flat = const("BREED_SHARE_PER_MIN"), const("BREED_FLAT_PER_MIN")
    jar, gourd, hive = const("JAR_CAPACITY"), const("GOURD_CAPACITY"), const("HIVE_CAPACITY")
    per_chakra = const("INSECTS_PER_CHAKRA_EATEN")
    vig = 1.08  # Vigour 70
    body, reserves, control = 3000.0, 10.0, 13.0
    stored, hives, hive_count = 0.0, 0, 0.0
    reached = {}
    targets = ranks()
    for hour in range(1, 61):
        reserves += 1.2  # a little training each hour
        control += 0.8
        cap = base + per_res * reserves + per_ctl * control
        for _ in range(60):
            body = min(cap, body + (share * body + flat) * vig)
        body = min(cap, body + 40 * per_chakra / 2)  # about 40 chakra drained in fights an hour
        if hour >= 3:
            stored = min(stored + 3000, 2 * jar + (gourd if hour >= 12 else 0))
        if hour >= 8 and hives < 3 and hour % 6 == 2:
            hives += 1
        if hives:
            # hives breed 1% of what they hold a minute (compounding), and you deposit 5,000 an hour from the body
            hive_count = min(hives * hive, hive_count * (1.01 ** 60) + 5000)
        total = body + stored + hive_count
        for name, need in targets:
            if total >= need and name not in reached:
                reached[name] = hour
        if hour in (1, 2, 5, 10, 20, 30, 40, 60):
            print(f"hour {hour:2d}: body {body:8.0f}/{cap:6.0f}  jars+gourd {stored:6.0f}  hives {hive_count:7.0f}  total {total:8.0f}")
    print()
    for name, need in targets:
        print(f"{name:12s} {need:7d}: hour {reached.get(name, 'not reached')}")
    missing = [n for n, _ in targets if n not in reached]
    print("OK: every hive rank is reachable" if not missing else "TOO SLOW: " + ", ".join(missing))


if __name__ == "__main__":
    main()
