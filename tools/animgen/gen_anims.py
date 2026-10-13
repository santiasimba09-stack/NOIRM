#!/usr/bin/env python3
"""Generates NOIRM's own player animations (PlayerAnimator / GeckoLib JSON) from simple poses.

Run from the repo root:  python3 tools/animgen/gen_anims.py
It writes src/main/resources/assets/noirm/player_animation/<name>.json.

Conventions (same as the Boundless set): rotation in degrees; for arms and legs a negative X
swings the limb forward/up; position in pixels, negative Z is forward, positive Y is up.
"body" moves the whole player, "torso" only the upper body.
Each key is (fraction of the animation, {bone: (rotation, position)}). Bones left out of a key
are at rest there, so every key is a full pose and the in-betweens are predictable.

Bends (NOIRM extension, NPC model only; PlayerAnimator ignores them): pose(..., bend=dict(right_arm=90))
in degrees. Positive flexes the joint: the elbow curls the forearm forward, the knee folds the
shin back, the torso hunches forward at the waist. A bone gets a "bend" channel only if one of
its keys bends it; keys that leave it out are at 0 there.
"""
import json
import os

OUT = "src/main/resources/assets/noirm/player_animation"
BONES = ["body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"]
Z = (0, 0, 0)


def pose(bend=None, **bones):
    frame = {name: (value if isinstance(value[0], (tuple, list)) else (value, Z)) for name, value in bones.items()}
    frame["_bend"] = dict(bend or {})
    return frame


UPPER = ["torso", "head", "right_arm", "left_arm"]


def build(name, length, keys, bones_used=None, loop=False):
    bones = {}
    for bone in bones_used or BONES:
        rotation = {}
        position = {}
        bend = {}
        for t, frame in keys:
            rot, pos = frame.get(bone, (Z, Z))
            stamp = f"{round(t * length, 4)}"
            rotation[stamp] = {"vector": list(rot), "easing": "linear"}
            position[stamp] = {"vector": list(pos), "easing": "linear"}
            bend[stamp] = {"vector": [frame.get("_bend", {}).get(bone, 0)], "easing": "linear"}
        bones[bone] = {"rotation": rotation, "position": position}
        if any(v["vector"][0] != 0 for v in bend.values()):
            bones[bone]["bend"] = bend
    anim = {"animation_length": length, "bones": bones}
    if loop:
        anim["loop"] = True
    return {"format_version": "1.8.0", "animations": {name: anim}}


REST = pose()
GUARD = pose(right_arm=(-70, -20, 0), left_arm=(-70, 20, 0))

# Stances: looping, upper body only (the legs keep walking normally). Each style stands its own way.
STANCES = {
    # Academy: a plain, balanced guard (fists up in front of the chin on the NPC model).
    "stance_basic": (2.0, [
        (0.0, pose(right_arm=(-50, -15, 5), left_arm=(-60, 20, -5), torso=(0, 15, 0), bend=dict(right_arm=75, left_arm=85, torso=5))),
        (0.5, pose(right_arm=(-54, -15, 5), left_arm=(-64, 20, -5), torso=(2, 15, 0), bend=dict(right_arm=80, left_arm=90, torso=7))),
        (1.0, pose(right_arm=(-50, -15, 5), left_arm=(-60, 20, -5), torso=(0, 15, 0), bend=dict(right_arm=75, left_arm=85, torso=5))),
    ]),
    # Brawler: Muay Thai high guard, fists by the face, hunched, bouncing on the beat.
    "stance_brawler": (0.8, [
        (0.0, pose(right_arm=((-115, -30, 10), (0, 0, -1)), left_arm=((-120, 35, -10), (0, 0, -1)), torso=(12, 10, 0), head=(10, 0, 0),
                   bend=dict(right_arm=60, left_arm=55, torso=12))),
        (0.5, pose(right_arm=((-110, -30, 10), (0, -1, -1)), left_arm=((-115, 35, -10), (0, -1, -1)), torso=(16, 10, 0), head=(14, 0, 0),
                   bend=dict(right_arm=65, left_arm=60, torso=16))),
        (1.0, pose(right_arm=((-115, -30, 10), (0, 0, -1)), left_arm=((-120, 35, -10), (0, 0, -1)), torso=(12, 10, 0), head=(10, 0, 0),
                   bend=dict(right_arm=60, left_arm=55, torso=12))),
    ]),
    # Strong Fist: Might Guy and Lee: one hand behind the back, the other open and forward, upright.
    "stance_strong_fist": (1.6, [
        (0.0, pose(right_arm=(-75, 10, 0), left_arm=((35, 0, -25), (1, 0, 2)), torso=(-4, 25, 0), bend=dict(right_arm=15, left_arm=30))),
        (0.5, pose(right_arm=(-80, 10, 0), left_arm=((35, 0, -25), (1, 0, 2)), torso=(-6, 25, 0), bend=dict(right_arm=20, left_arm=30))),
        (1.0, pose(right_arm=(-75, 10, 0), left_arm=((35, 0, -25), (1, 0, 2)), torso=(-4, 25, 0), bend=dict(right_arm=15, left_arm=30))),
    ]),
    # Gentle Fist: the Hyūga stance: side-on, lead palm out low, rear palm high, very still.
    "stance_gentle_fist": (3.0, [
        (0.0, pose(right_arm=(-70, 0, -20), left_arm=(-110, 0, 30), torso=(5, 55, 0), head=(0, -40, 0), bend=dict(right_arm=25, left_arm=45, torso=8))),
        (0.5, pose(right_arm=(-72, 0, -20), left_arm=(-112, 0, 30), torso=(6, 55, 0), head=(0, -40, 0), bend=dict(right_arm=27, left_arm=47, torso=9))),
        (1.0, pose(right_arm=(-70, 0, -20), left_arm=(-110, 0, 30), torso=(5, 55, 0), head=(0, -40, 0), bend=dict(right_arm=25, left_arm=45, torso=8))),
    ]),
}

ANIMS = {
    # Strong Fist: Lee's lightning-fast single-arm straight, the other hand behind the back.
    "strong_fist_jab": (0.35, [
        (0.0, pose(right_arm=(-75, 10, 0), left_arm=(35, 0, -25), torso=(-4, 25, 0), bend=dict(right_arm=15, left_arm=30))),
        (0.3, pose(right_arm=((-92, 0, 0), (0, 0, -9)), left_arm=(35, 0, -25), torso=(6, -20, 0), body=((0, 0, 0), (0, 0, -4)),
                   bend=dict(left_arm=30, right_leg=15, left_leg=10))),
        (0.6, pose(right_arm=((-90, 0, 0), (0, 0, -6)), left_arm=(35, 0, -25), torso=(4, -15, 0), body=((0, 0, 0), (0, 0, -2)),
                   bend=dict(right_arm=5, left_arm=30, right_leg=10, left_leg=5))),
        (1.0, pose(right_arm=(-75, 10, 0), left_arm=(35, 0, -25), torso=(-4, 25, 0), bend=dict(right_arm=15, left_arm=30))),
    ]),
    "strong_fist_straight": (0.45, [
        (0.0, pose(right_arm=(-75, 10, 0), left_arm=(35, 0, -25), torso=(-4, 25, 0), bend=dict(right_arm=15, left_arm=30))),
        (0.25, pose(right_arm=((-60, 0, 30), (0, 0, 3)), left_arm=(30, 0, -25), torso=(0, 50, 0), right_leg=(-15, 0, 0),
                    bend=dict(right_arm=80, left_arm=30, right_leg=20, left_leg=15))),
        (0.45, pose(right_arm=((-95, 0, -5), (0, 0, -10)), left_arm=(40, 0, -25), torso=(10, -40, 0), right_leg=(-35, 0, 0),
                    left_leg=(25, 0, 0), body=((0, 0, 0), (0, -1, -5)), bend=dict(left_arm=30, right_leg=30, left_leg=10, torso=6))),
        (0.75, pose(right_arm=(-85, 0, 0), left_arm=(35, 0, -25), torso=(5, -25, 0), bend=dict(right_arm=10, left_arm=30, right_leg=10))),
        (1.0, pose(right_arm=(-75, 10, 0), left_arm=(35, 0, -25), torso=(-4, 25, 0), bend=dict(right_arm=15, left_arm=30))),
    ]),
    # Brawler: short, close strikes.
    "elbow_strike": (0.55, [
        (0.0, REST),
        (0.25, pose(torso=(0, 45, 0), right_arm=((-90, 0, 70), (0, 0, 2)), left_arm=(-60, 20, 0),
                    bend=dict(right_arm=130, left_arm=95, right_leg=15, left_leg=15))),
        (0.4, pose(torso=(5, -40, 0), right_arm=((-95, -60, 10), (0, 0, -3)), left_arm=(-50, 20, 0), body=((0, 0, 0), (0, 0, -2)),
                   bend=dict(right_arm=135, left_arm=100, torso=10, right_leg=20, left_leg=10))),
        (0.7, pose(torso=(0, -20, 0), right_arm=(-70, -30, 10), left_arm=(-55, 20, 0), bend=dict(right_arm=110, left_arm=90, torso=5))),
        (1.0, REST),
    ]),
    "knee_strike": (0.6, [
        (0.0, REST),
        (0.3, pose(right_arm=(-110, 0, 10), left_arm=(-110, 0, -10), right_leg=((20, 0, 0), (0, 0, 2)), torso=(-10, 0, 0),
                   bend=dict(right_arm=50, left_arm=50, right_leg=40, left_leg=15))),
        (0.45, pose(right_arm=(-60, 0, 15), left_arm=(-60, 0, -15), right_leg=((-100, 0, 0), (0, 3, -3)),
                    torso=(15, 0, 0), body=((0, 0, 0), (0, 2, -2)), bend=dict(right_arm=90, left_arm=90, right_leg=120, left_leg=10, torso=15))),
        (0.7, pose(right_arm=(-50, 0, 10), left_arm=(-50, 0, -10), right_leg=(-40, 0, 0), bend=dict(right_arm=70, left_arm=70, right_leg=60))),
        (1.0, REST),
    ]),
    "headbutt": (0.6, [
        (0.0, REST),
        (0.35, pose(torso=(-25, 0, 0), head=(-20, 0, 0), right_arm=(-80, 0, 15), left_arm=(-80, 0, -15),
                    bend=dict(torso=-10, right_arm=60, left_arm=60, right_leg=15, left_leg=15))),
        (0.5, pose(torso=(35, 0, 0), head=(25, 0, 0), right_arm=(-60, 0, 15), left_arm=(-60, 0, -15),
                   body=((0, 0, 0), (0, 0, -3)), bend=dict(torso=30, right_arm=80, left_arm=80, right_leg=20, left_leg=10))),
        (0.75, pose(torso=(15, 0, 0), head=(10, 0, 0), right_arm=(-40, 0, 10), left_arm=(-40, 0, -10), bend=dict(torso=10, right_arm=60, left_arm=60))),
        (1.0, REST),
    ]),
    "clinch_grab": (0.5, [
        (0.0, REST),
        (0.35, pose(right_arm=((-105, 0, 25), (0, 0, -3)), left_arm=((-105, 0, -25), (0, 0, -3)), torso=(10, 0, 0),
                    body=((0, 0, 0), (0, 0, -2)), bend=dict(right_arm=20, left_arm=20, torso=8, right_leg=15, left_leg=15))),
        (0.6, pose(right_arm=(-85, 0, 10), left_arm=(-85, 0, -10), torso=(8, 0, 0), bend=dict(right_arm=70, left_arm=70, torso=10))),
        (1.0, pose(right_arm=(-75, 0, 10), left_arm=(-75, 0, -10), bend=dict(right_arm=90, left_arm=90, torso=8))),
    ]),
    "throw": (0.8, [
        (0.0, pose(right_arm=(-75, 0, 10), left_arm=(-75, 0, -10), bend=dict(right_arm=90, left_arm=90, torso=8))),
        (0.3, pose(torso=(0, 40, 0), right_arm=(-90, 0, 30), left_arm=(-90, 0, -10), right_leg=(-20, 0, 0),
                   bend=dict(right_arm=60, left_arm=60, right_leg=30, left_leg=25, torso=10))),
        (0.55, pose(body=(0, -90, 0), torso=(20, -40, 0), right_arm=(-160, 0, 20), left_arm=(-140, 0, -20),
                    bend=dict(right_arm=30, left_arm=30, right_leg=15, left_leg=15, torso=15))),
        (0.75, pose(body=(0, -120, 0), torso=(30, -20, 0), right_arm=(-40, 0, 20), left_arm=(-40, 0, -20),
                    bend=dict(right_arm=20, left_arm=20, torso=20, right_leg=10, left_leg=10))),
        (1.0, REST),
    ]),
    "flying_knee": (0.7, [
        (0.0, REST),
        (0.25, pose(torso=(-10, 0, 0), right_arm=(30, 0, 10), left_arm=(30, 0, -10), right_leg=(15, 0, 0),
                    bend=dict(right_leg=50, left_leg=40, right_arm=20, left_arm=20))),
        (0.45, pose(body=((0, 0, 0), (0, 6, -6)), torso=(10, 0, 0), right_arm=(40, 0, 20), left_arm=(-60, 0, -10),
                    right_leg=((-110, 0, 0), (0, 3, -3)), left_leg=(35, 0, 0), bend=dict(right_leg=130, left_leg=50, left_arm=80, torso=10))),
        (0.7, pose(body=((0, 0, 0), (0, 2, -3)), right_leg=(-40, 0, 0), left_leg=(15, 0, 0), bend=dict(right_leg=60, left_leg=20))),
        (1.0, REST),
    ]),
    # Gentle Fist (Jūken): wide, low stance, open palms.
    "palm_strike": (0.5, [
        (0.0, pose(right_leg=(-20, 0, 0), left_leg=(20, 0, 0), bend=dict(right_leg=25, left_leg=25, right_arm=30, left_arm=30))),
        (0.3, pose(torso=(0, 35, 0), right_arm=((-60, 0, 15), (0, 0, 3)), left_arm=(-80, 0, -10),
                   right_leg=(-25, 0, 0), left_leg=(25, 0, 0), body=((0, 0, 0), (0, -2, 0)),
                   bend=dict(right_arm=70, left_arm=40, right_leg=35, left_leg=30))),
        (0.45, pose(torso=(5, -30, 0), right_arm=((-92, 0, 0), (0, 0, -7)), left_arm=(-20, 0, -20),
                    right_leg=(-30, 0, 0), left_leg=(30, 0, 0), body=((0, 0, 0), (0, -3, -2)),
                    bend=dict(left_arm=50, right_leg=40, left_leg=30, torso=6))),
        (0.75, pose(right_arm=(-80, 0, 0), right_leg=(-20, 0, 0), left_leg=(20, 0, 0), bend=dict(right_arm=15, right_leg=25, left_leg=20))),
        (1.0, REST),
    ]),
    "double_palm": (0.7, [
        (0.0, REST),
        (0.35, pose(right_arm=((-70, 0, 20), (0, 0, 3)), left_arm=((-70, 0, -20), (0, 0, 3)), torso=(-10, 0, 0),
                    right_leg=(-25, 0, 0), left_leg=(25, 0, 0), body=((0, 0, 0), (0, -2, 1)),
                    bend=dict(right_arm=85, left_arm=85, right_leg=35, left_leg=25))),
        (0.5, pose(right_arm=((-95, 0, 5), (0, 0, -8)), left_arm=((-95, 0, -5), (0, 0, -8)), torso=(15, 0, 0),
                   right_leg=(-35, 0, 0), left_leg=(30, 0, 0), body=((0, 0, 0), (0, -3, -3)), bend=dict(right_leg=40, left_leg=20, torso=8))),
        (0.8, pose(right_arm=(-85, 0, 5), left_arm=(-85, 0, -5), bend=dict(right_arm=15, left_arm=15))),
        (1.0, REST),
    ]),
    # Strong Fist (Gōken): Might Guy and Rock Lee's big, acrobatic kicks.
    "leaf_whirlwind": (0.8, [
        (0.0, REST),
        (0.2, pose(body=((0, 0, 0), (0, -2, 0)), right_arm=(-40, 0, 40), left_arm=(-40, 0, -40), left_leg=(-20, 0, 0),
                   bend=dict(right_leg=30, left_leg=35, right_arm=30, left_arm=30))),
        (0.4, pose(body=((0, 120, 0), (0, 3, 0)), right_leg=(-10, 0, 85), right_arm=(0, 0, 70), left_arm=(0, 0, -70),
                   bend=dict(left_leg=20, right_arm=15, left_arm=15))),
        (0.55, pose(body=((0, 240, 0), (0, 4, 0)), right_leg=(-10, 0, 90), right_arm=(0, 0, 70), left_arm=(0, 0, -70),
                    bend=dict(left_leg=20, right_arm=15, left_arm=15))),
        (0.7, pose(body=((0, 360, 0), (0, 2, 0)), right_leg=(-10, 0, 60), bend=dict(right_leg=20, left_leg=25))),
        (1.0, pose(body=(0, 360, 0))),
    ]),
    "dynamic_entry": (0.9, [
        (0.0, REST),
        (0.2, pose(body=((0, 0, 0), (0, -3, 0)), torso=(20, 0, 0), right_arm=(40, 0, 15), left_arm=(40, 0, -15),
                   right_leg=(-30, 0, 0), left_leg=(30, 0, 0), bend=dict(right_leg=60, left_leg=45, right_arm=20, left_arm=20, torso=12))),
        (0.45, pose(body=((-15, 0, 0), (0, 8, -10)), torso=(-10, 0, 0), right_arm=(50, 0, 30), left_arm=(-40, 0, -20),
                    right_leg=((-90, 0, 0), (0, 2, -4)), left_leg=(40, 0, 0), bend=dict(left_leg=75, left_arm=60))),
        (0.65, pose(body=((-10, 0, 0), (0, 5, -6)), right_leg=(-80, 0, 0), left_leg=(30, 0, 0), right_arm=(30, 0, 20),
                    bend=dict(right_leg=10, left_leg=45))),
        (1.0, REST),
    ]),
    "leaf_rising_wind": (0.7, [
        (0.0, REST),
        (0.25, pose(body=((0, 0, 0), (0, -3, 0)), torso=(15, 0, 0), right_arm=(-30, 0, 20), left_arm=(-30, 0, -20),
                    right_leg=(10, 0, 0), left_leg=(-20, 0, 0), bend=dict(right_leg=55, left_leg=50, torso=10, right_arm=30, left_arm=30))),
        (0.45, pose(body=((-10, 0, 0), (0, 4, 0)), torso=(-20, 0, 0), right_leg=((-170, 0, 0), (0, 2, -2)),
                    right_arm=(20, 0, 40), left_arm=(20, 0, -40), bend=dict(left_leg=20))),
        (0.7, pose(torso=(-10, 0, 0), right_leg=(-90, 0, 0), bend=dict(right_leg=30, left_leg=15))),
        (1.0, REST),
    ]),
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, (length, keys) in ANIMS.items():
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(build(name, length, keys), f, indent=1)
        print("wrote", name)
    for name, (length, keys) in STANCES.items():
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(build(name, length, keys, UPPER, loop=True), f, indent=1)
        print("wrote", name)
