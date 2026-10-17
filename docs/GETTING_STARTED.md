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
| **Sprint + click** | Dash attack (flying knee, Dynamic Entry…) |
| **Hold S + click** | Back attack (spinning back elbow, Leaf Whirlwind…) |
| **C** | Grab. Grabs beat guard; then click = knee, right click = headbutt, S + click = throw. Held? **Alt** breaks free |
| **H** | Switch fighting style: Academy, Brawler, Strong Fist (taijutsu 18), Gentle Fist (Byakugan) |
| **Alt** (+ WASD) | Dodge, costs stamina. Dodging right as a hit lands = **perfect dodge** |
| **Hold V** | Guard. Raising it right before a hit lands = **parry** |
| **Z** | Lock on to the enemy you're looking at |
| **F5** | Third-person view, to see the animations |
| **1–4** | Cast the jutsu in your loadout (hold for charge/channel jutsu) |
| **J** | Jutsu screen (change your loadout) |
| **B** | Tailed-beast cloak (if you're a jinchūriki) |
| **G** | Open your eyes: Sharingan or Byakugan (if you have one) |
| **J** | Jutsu screen: your jutsu by nature, the loadout, and the **Nature Tree** tab |

Strong hits slam people into walls behind them (and crack the wall); hits in the air keep a
launched enemy floating (a juggle). Destruction heals itself after a while: turn it off with
`/gamerule noirmTerrainDamage false`, or change how long it takes with `noirmTerrainRegen`.

**Crew:** beat a ninja below a quarter of their health and they yield (kneel). Right-click them to
invite them. Sneak + right-click a crew mate: wait here / follow me.
**Tailed beasts:** once subdued, use a Sealing Scroll on them, or keep hitting to kill them.

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

## How to test allies, beasts and gear (round 4)

**Allies:** `/noirm ninja uchiha` spawns an Uchiha (or use a clan spawn egg from the Spawn Eggs
tab). Beat them down until they yield, then right-click them to invite them into your crew.

| What | How |
|---|---|
| Ally screen | Right-click a crew mate: change their armor and what they hold, their order (follow, wait, train, meditate), style and look; teach jutsu you know and pick their 4 equipped jutsu |
| Quick wait / follow | Crouch + right-click a crew mate |
| Make them grow | Fight together, or order **Train** next to a training dummy, or **Meditate** (chakra) |
| Respect | Shown in the ally screen. It goes up when you win fights and give them gear, down when you hit them or let crew mates die. Too low and they leave |

**Tailed beast bond:** become a jinchūriki (`/noirm biju seal 9` or seal a beast; in creative a
Sealing Scroll works at any health). Then:

| What | How |
|---|---|
| Draw tails | Press **B** again and again: one more tail each press, up to what you've unlocked |
| End the cloak | Crouch + **B** |
| Answer the beast / ask for chakra | Crouch + **B** with the cloak off |
| See the bond and requests | `/noirm beast` (cheats: `/noirm beast bond 80`, `/noirm beast tails 9`) |

**Gear:** the Combat creative tab has forehead protectors, Senju battle armor, Uchiha, Hyūga and
Uzumaki clothes, flak vests and ANBU gear. Each piece gives armor points and has a recipe.

## How to find ninja villages

**See one right away:** stand on fairly open ground and type `/noirm village capital` (or `town`,
`outpost`). Add `leaf`, `sand`, `snow`, `mist`, `cloud` or `stone` to choose the look. A capital is ~545 blocks across, so
give it room. It's built a few chunks at a time (about a minute) and the chat tells you when it's
done. Fly up high to see it all.

**Find natural ones:** they're only built in **newly generated land**, so start a new world or
explore far away. Then `/locate structure noirm:hidden_village` (rare: they're thousands of blocks
apart), `noirm:ninja_town` or `noirm:ninja_outpost`, and `/tp` to the coordinates it prints.

**Things to try in a hidden village:**
- Walk through the great gate up the main avenue (street lamps, shops, signboards) to the Kage
  residence under the Kage Rock; climb the tower's ladder to the Kage's office on the balcony.
- Follow the river: red footbridges, stone bridges, steps down to the water, the clan district on
  its bank.
- Walk the lantern street (restaurants, paper lanterns strung across it) to the hot spring.
- Look for ninjas leaping across the rooftops, patrols on the wall walk, and neighbours stopping
  to chat (a speech bubble over their heads).
- Find the giant tree, the spires, the jōnin station by the Academy, the cemetery with its flame,
  koi ponds, bamboo groves and the little roadside shrines.
- Right-click shopkeepers to trade (each shop sells different things) and anyone else to chat.
- Stay until night: villagers go home and the street lamps switch on by themselves.
- If a zombie wanders in, the village ninjas deal with it (villagers run home).
- Watch for villagers sitting on benches and at tables.

**The other hidden villages** (they appear in other biomes):
- **Mist** (swamps, dark forests): islands between ring canals, moss-topped round houses, and mist
  that closes in when you walk in.
- **Cloud** (meadows, windswept hills): white houses with blue domes and rock spires you can climb
  (a ladder up the back), a dish platform with a bell, little houses on top.
- **Stone** (badlands): towers that narrow as they rise, joined by rope bridges at roof height.

Village ninjas leave you alone in combat mode unless you attack someone in the village. Then
every ninja nearby comes for you.

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
