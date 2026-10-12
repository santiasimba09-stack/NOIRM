# Getting started (no experience needed)

This is how to run NOIRM on your own computer.

## 1. Install the tools (one time)

1. **Java 17 (JDK).** Download "Temurin 17 (LTS)" from https://adoptium.net and install it.
2. **IntelliJ IDEA Community Edition** (free) from https://www.jetbrains.com/idea/. This is the
   editor where the code lives.
3. **Git**, to download and update the project: https://git-scm.com/downloads.

## 2. Get the project

```
git clone https://github.com/santiasimba09-stack/NOIRM.git
```

In IntelliJ: **File → Open** → choose the `NOIRM` folder → **Trust project**. The first time,
IntelliJ downloads Minecraft and Forge and sets everything up. That takes about 5–15 minutes,
and you can watch progress in the bottom bar.

## 3. Play your mod

Open the **Gradle** panel on the right side of IntelliJ, then go to
**Tasks → forgegradle runs** and double-click **runClient**. A Minecraft window opens with NOIRM
installed. You'll find it under *Mods* on the title screen.

You can also run it from a terminal in the project folder:

```
./gradlew runClient        # macOS / Linux
gradlew.bat runClient      # Windows
```

## How to test combat (milestone 2)

1. Run `runClient` and create a world with **cheats on**.
2. Open the creative inventory → Spawn Eggs → **Sparring Ninja**, and place one.
3. Press **R** to enter combat mode (the ninja accepts the challenge), then:

| Key | Action |
|---|---|
| **R** | Combat mode on/off |
| **Left click** | Light attack (press again quickly to combo) |
| **Right click** (empty hand or sword) | Heavy attack |
| **Shift + right click** | Launcher (uppercut) |
| **Alt** (+ WASD) | Dodge, costs stamina. Dodging right as a hit lands = **perfect dodge** |
| **Hold V** | Guard. Raising it right before a hit lands = **parry** |
| **Z** | Lock on to the enemy you're looking at |
| **F5** | Third-person view, to see the animations |
| **1–4** | Cast the jutsu in your loadout (hold for charge/channel jutsu) |
| **J** | Jutsu screen (change your loadout) |
| **B** | Tailed-beast cloak (if you're a jinchūriki) |
| **G** | Open your eyes: Sharingan or Byakugan (if you have one) |
| **J** | Jutsu screen: your jutsu by nature, the loadout, and the **Nature Tree** tab |

Try `/noirm stat speed 20` and `/noirm stat taijutsu 20`: the ninja's wind-ups get slower
(perception). Or summon a strong one:
`/summon noirm:sparring_ninja ~ ~ ~3 {Speed:25,Taijutsu:25}`.

## How to test powers (milestone 3)

When you first join a world, chat tells you which clan you were born into, with its natures and
traits. In combat mode, **keys 1–4** cast the jutsu in your loadout; **J** opens the jutsu
screen. Shortcuts for testing (cheats on):

| Command | What it does |
|---|---|
| `/noirm clan` · `/noirm clan list` | Your clan · all clans this world knows |
| `/noirm clan set uchiha` (or `hyuga`, `uzumaki`, `senju`) | Become a canon clan |
| `/noirm clan new` · `/noirm clan reroll` | Join a brand-new generated clan · roll like a newcomer |
| `/noirm jutsu learn all` | Learn every jutsu (then equip them with J) |
| `/noirm sharingan 3` | Get the Sharingan with 3 tomoe (press **G** to turn it on) |
| `/noirm resolve 100` | Full Resolve: the next near-death hit will probably awaken you |
| `/noirm awaken` | Awaken right now (LOCKED IN) |
| `/noirm biju seal 9` | Become a jinchūriki (press **B** for the cloak) |
| `/summon noirm:sparring_ninja ~ ~ ~3 {Clan:"uchiha"}` | An Uchiha ninja (Sharingan, fire jutsu) |

Ideas to try: get the Sharingan to 3 tomoe, turn it on, and watch a ninja cast a jutsu (you
copy it). Let a ninja beat you down to low health with full Resolve. Use the cloak and fight
with it on.

## How to test training, the Byakugan and tailed beasts

**Training:** craft a **Training Dummy** (a hay bale on top, a hay bale in the middle with sticks
on both sides, a stick below) or take it from the Combat tab, place it, press **R** and hit it.
Watch the combo counter, and the Taijutsu/Speed bars in the HUD. You get faster as speed rises.
Past 30, the dummy stops teaching: fight ninjas (stronger ones teach more). Crouch-punch the
dummy with combat mode off to pick it up.

**Byakugan:** `/noirm byakugan` (or `/noirm clan set hyuga`), then **G**. Look around: ninjas and mobs glow through
walls. Fight with it open (Gentle Fist), and equip Rotation and 64 Palms with **J**.

**Tailed beasts:**

| What | How |
|---|---|
| Summon a beast | Spawn egg (Spawn Eggs tab) or `/summon noirm:tailed_beast ~ ~ ~5 {Tails:9}` |
| Seal it | Beat it down until it lies down (subdued), then use a **Sealing Scroll** on it |
| Become its jinchūriki | Use the full scroll in the air, then press **B** for the cloak |
| Seal it into someone else | Use the full scroll on a ninja or another player |
| Let it out again | Crouch + use the full scroll on the ground (it will attack you) |
| Tailed Beast Ball | Equip it with **J**, turn on the cloak (**B**), hold its key to charge |
| A jinchūriki ninja | `/summon noirm:sparring_ninja ~ ~ ~3 {Tails:4}`: beat it and its beast breaks free |

## How to test the nature tree and the new jutsu

1. `/noirm clan set senju` (Earth + Water).
2. `/noirm jutsu mastery noirm:doton/earth_wall 2` and `/noirm jutsu mastery noirm:suiton/water_bullet 2`.
   Mastering them also teaches Earth Spikes and Water Dragon (mastery chains).
3. Press **J** → **Nature Tree**: Wood glows gold. Click it, then **Unlock**. You learn Wood
   Binding and Deep Forest Spears.
4. To try every jutsu: `/noirm nature add <nature>` for each nature you want, then
   `/noirm jutsu learn all`, and equip with **J**.

**Stats:** `/noirm stat vitality 30` (more hearts), `/noirm stat reserves 30` (double chakra).
**Eyes vs mobs:** `/noirm sharingan 3`, **G**, and let a skeleton shoot at you.

## Useful commands

| Command | What it does |
|---|---|
| `gradlew runClient` | Starts Minecraft with the mod |
| `gradlew runGameTestServer` | Runs NOIRM's automated in-game tests, then exits |
| `gradlew build` | Builds the mod file into `build/libs/noirm-<version>.jar` |

To play the built jar in a normal Minecraft install, use Forge 1.20.1 (47.4.10 or newer) and
put the jar in your `mods` folder.

## Where things are

```
src/main/java/com/noirm/        the mod's code
src/main/resources/             assets (models, textures, lang) and data (recipes, worldgen…)
docs/                           roadmap and guides
reference/nydo/                 NYDO's assets, for inspiration only (not part of our mod)
```
