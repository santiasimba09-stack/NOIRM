#!/usr/bin/env python3
"""Generates NOIRM's own player animations (PlayerAnimator / GeckoLib JSON) from simple poses.

Run from the repo root:  python3 tools/animgen/gen_anims.py
It writes src/main/resources/assets/noirm/player_animation/<name>.json.

Conventions (same as the Boundless set): rotation in degrees; for arms and legs a negative X
swings the limb forward/up; position in pixels, negative Z is forward, positive Y is up.
"body" moves the whole player, "torso" only the upper body.
Each key is (fraction of the animation, {bone: (rotation, position)}). Bones left out of a key
are at rest there, so every key is a full pose and the in-betweens are predictable.
"""
import json
import os

OUT = "src/main/resources/assets/noirm/player_animation"
BONES = ["body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"]
Z = (0, 0, 0)


def pose(**bones):
    return {name: (value if isinstance(value[0], (tuple, list)) else (value, Z)) for name, value in bones.items()}


def build(name, length, keys):
    bones = {}
    for bone in BONES:
        rotation = {}
        position = {}
        for t, frame in keys:
            rot, pos = frame.get(bone, (Z, Z))
            stamp = f"{round(t * length, 4)}"
            rotation[stamp] = {"vector": list(rot), "easing": "linear"}
            position[stamp] = {"vector": list(pos), "easing": "linear"}
        bones[bone] = {"rotation": rotation, "position": position}
    return {"format_version": "1.8.0", "animations": {name: {"animation_length": length, "bones": bones}}}


REST = pose()
GUARD = pose(right_arm=(-70, -20, 0), left_arm=(-70, 20, 0))

ANIMS = {
    # Brawler: short, close strikes.
    "elbow_strike": (0.55, [
        (0.0, REST),
        (0.25, pose(torso=(0, 45, 0), right_arm=((-90, 0, 70), (0, 0, 2)), left_arm=(-60, 20, 0))),
        (0.4, pose(torso=(5, -40, 0), right_arm=((-95, -60, 10), (0, 0, -3)), left_arm=(-50, 20, 0), body=((0, 0, 0), (0, 0, -2)))),
        (0.7, pose(torso=(0, -20, 0), right_arm=(-70, -30, 10), left_arm=(-55, 20, 0))),
        (1.0, REST),
    ]),
    "knee_strike": (0.6, [
        (0.0, REST),
        (0.3, pose(right_arm=(-110, 0, 10), left_arm=(-110, 0, -10), right_leg=((20, 0, 0), (0, 0, 2)), torso=(-10, 0, 0))),
        (0.45, pose(right_arm=(-60, 0, 15), left_arm=(-60, 0, -15), right_leg=((-100, 0, 0), (0, 3, -3)),
                    torso=(15, 0, 0), body=((0, 0, 0), (0, 2, -2)))),
        (0.7, pose(right_arm=(-50, 0, 10), left_arm=(-50, 0, -10), right_leg=(-40, 0, 0))),
        (1.0, REST),
    ]),
    "headbutt": (0.6, [
        (0.0, REST),
        (0.35, pose(torso=(-25, 0, 0), head=(-20, 0, 0), right_arm=(-80, 0, 15), left_arm=(-80, 0, -15))),
        (0.5, pose(torso=(35, 0, 0), head=(25, 0, 0), right_arm=(-60, 0, 15), left_arm=(-60, 0, -15),
                   body=((0, 0, 0), (0, 0, -3)))),
        (0.75, pose(torso=(15, 0, 0), head=(10, 0, 0), right_arm=(-40, 0, 10), left_arm=(-40, 0, -10))),
        (1.0, REST),
    ]),
    "clinch_grab": (0.5, [
        (0.0, REST),
        (0.35, pose(right_arm=((-105, 0, 25), (0, 0, -3)), left_arm=((-105, 0, -25), (0, 0, -3)), torso=(10, 0, 0),
                    body=((0, 0, 0), (0, 0, -2)))),
        (0.6, pose(right_arm=(-85, 0, 10), left_arm=(-85, 0, -10), torso=(8, 0, 0))),
        (1.0, pose(right_arm=(-75, 0, 10), left_arm=(-75, 0, -10))),
    ]),
    "throw": (0.8, [
        (0.0, pose(right_arm=(-75, 0, 10), left_arm=(-75, 0, -10))),
        (0.3, pose(torso=(0, 40, 0), right_arm=(-90, 0, 30), left_arm=(-90, 0, -10), right_leg=(-20, 0, 0))),
        (0.55, pose(body=(0, -90, 0), torso=(20, -40, 0), right_arm=(-160, 0, 20), left_arm=(-140, 0, -20))),
        (0.75, pose(body=(0, -120, 0), torso=(30, -20, 0), right_arm=(-40, 0, 20), left_arm=(-40, 0, -20))),
        (1.0, REST),
    ]),
    "flying_knee": (0.7, [
        (0.0, REST),
        (0.25, pose(torso=(-10, 0, 0), right_arm=(30, 0, 10), left_arm=(30, 0, -10), right_leg=(15, 0, 0))),
        (0.45, pose(body=((0, 0, 0), (0, 6, -6)), torso=(10, 0, 0), right_arm=(40, 0, 20), left_arm=(-60, 0, -10),
                    right_leg=((-110, 0, 0), (0, 3, -3)), left_leg=(35, 0, 0))),
        (0.7, pose(body=((0, 0, 0), (0, 2, -3)), right_leg=(-40, 0, 0), left_leg=(15, 0, 0))),
        (1.0, REST),
    ]),
    # Gentle Fist (Jūken): wide, low stance, open palms.
    "palm_strike": (0.5, [
        (0.0, pose(right_leg=(-20, 0, 0), left_leg=(20, 0, 0))),
        (0.3, pose(torso=(0, 35, 0), right_arm=((-60, 0, 15), (0, 0, 3)), left_arm=(-80, 0, -10),
                   right_leg=(-25, 0, 0), left_leg=(25, 0, 0), body=((0, 0, 0), (0, -2, 0)))),
        (0.45, pose(torso=(5, -30, 0), right_arm=((-92, 0, 0), (0, 0, -7)), left_arm=(-20, 0, -20),
                    right_leg=(-30, 0, 0), left_leg=(30, 0, 0), body=((0, 0, 0), (0, -3, -2)))),
        (0.75, pose(right_arm=(-80, 0, 0), right_leg=(-20, 0, 0), left_leg=(20, 0, 0))),
        (1.0, REST),
    ]),
    "double_palm": (0.7, [
        (0.0, REST),
        (0.35, pose(right_arm=((-70, 0, 20), (0, 0, 3)), left_arm=((-70, 0, -20), (0, 0, 3)), torso=(-10, 0, 0),
                    right_leg=(-25, 0, 0), left_leg=(25, 0, 0), body=((0, 0, 0), (0, -2, 1)))),
        (0.5, pose(right_arm=((-95, 0, 5), (0, 0, -8)), left_arm=((-95, 0, -5), (0, 0, -8)), torso=(15, 0, 0),
                   right_leg=(-35, 0, 0), left_leg=(30, 0, 0), body=((0, 0, 0), (0, -3, -3)))),
        (0.8, pose(right_arm=(-85, 0, 5), left_arm=(-85, 0, -5))),
        (1.0, REST),
    ]),
    # Strong Fist (Gōken): Might Guy and Rock Lee's big, acrobatic kicks.
    "leaf_whirlwind": (0.8, [
        (0.0, REST),
        (0.2, pose(body=((0, 0, 0), (0, -2, 0)), right_arm=(-40, 0, 40), left_arm=(-40, 0, -40), left_leg=(-20, 0, 0))),
        (0.4, pose(body=((0, 120, 0), (0, 3, 0)), right_leg=(-10, 0, 85), right_arm=(0, 0, 70), left_arm=(0, 0, -70))),
        (0.55, pose(body=((0, 240, 0), (0, 4, 0)), right_leg=(-10, 0, 90), right_arm=(0, 0, 70), left_arm=(0, 0, -70))),
        (0.7, pose(body=((0, 360, 0), (0, 2, 0)), right_leg=(-10, 0, 60))),
        (1.0, pose(body=(0, 360, 0))),
    ]),
    "dynamic_entry": (0.9, [
        (0.0, REST),
        (0.2, pose(body=((0, 0, 0), (0, -3, 0)), torso=(20, 0, 0), right_arm=(40, 0, 15), left_arm=(40, 0, -15),
                   right_leg=(-30, 0, 0), left_leg=(30, 0, 0))),
        (0.45, pose(body=((-15, 0, 0), (0, 8, -10)), torso=(-10, 0, 0), right_arm=(50, 0, 30), left_arm=(-40, 0, -20),
                    right_leg=((-90, 0, 0), (0, 2, -4)), left_leg=(40, 0, 0))),
        (0.65, pose(body=((-10, 0, 0), (0, 5, -6)), right_leg=(-80, 0, 0), left_leg=(30, 0, 0), right_arm=(30, 0, 20))),
        (1.0, REST),
    ]),
    "leaf_rising_wind": (0.7, [
        (0.0, REST),
        (0.25, pose(body=((0, 0, 0), (0, -3, 0)), torso=(15, 0, 0), right_arm=(-30, 0, 20), left_arm=(-30, 0, -20),
                    right_leg=(10, 0, 0), left_leg=(-20, 0, 0))),
        (0.45, pose(body=((-10, 0, 0), (0, 4, 0)), torso=(-20, 0, 0), right_leg=((-170, 0, 0), (0, 2, -2)),
                    right_arm=(20, 0, 40), left_arm=(20, 0, -40))),
        (0.7, pose(torso=(-10, 0, 0), right_leg=(-90, 0, 0))),
        (1.0, REST),
    ]),
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, (length, keys) in ANIMS.items():
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(build(name, length, keys), f, indent=1)
        print("wrote", name)
