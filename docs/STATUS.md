# Project status

_Last updated: 2026-11-04 (NOIRM APX 0.1, v0.14.0) · branch `claude/keen-cray-uc2d11`_

## Where we are

**NOIRM APX 0.1 (v0.14.0) is out: the game is playable from a fresh character to a legend. 365/365
game tests are green twice in a row; every piece was checked in the real client.** The owner's
decisions are in `docs/APX.md`; the history is in `CHANGELOG.md`.

| APX 0.1 | State |
|---|---|
| **Stats:** gentler speed curve (cap at 50, perception past 35), Durability (up to 60% negation), speed trained in combat, kill XP to the stats you used | ✅ tested |
| **Natures:** chakra paper (hidden natures), drills, crew and master teachers, nature and jutsu scrolls | ✅ tested; checked in client |
| **Sage path:** Iwao the Stone Hermit, sanctuary, petrification, Sage Mode | ✅ tested; checked in client |
| **Ryo, shops, missions board (17 missions D–S), rank Academy → Kage candidate with exams** | ✅ tested; checked in client |
| **Classes D–S and the bingo book:** bounties, legendary ninjas with auras, entrances and boss bars | ✅ tested; checked in client |
| **Masters:** Might Guy, Rensai the Scholar, Tsubaki the medic; the Shinobi Handbook | ✅ tested; checked in client |
| **Follow-ups:** fewer wild ninjas, squads defend you, long held weapons, four new chakra flows | ✅ tested; checked in client |
| **UI redesign:** one UI kit (`client/ui`, rules in `docs/UI.md`), quiet HUD, every screen flat and animated in the same palette | ✅ checked in client at GUI scale auto and 2 (`ui-before-*` / `ui-after-*`) |

Before APX: v0.13.0 (personal jutsu sets, wild ninjas, blades, armor), v0.12 (our own HD art, gore v2,
fire and lightning), v0.11 (character creation, movement feel), v0.10 (crew overhaul, blueprint
Phase 1), round 6 and earlier: see `CHANGELOG.md`.

## In progress

Nothing. Waiting on the owner's playtest of APX 0.1.

## Next steps (in order)

1. **Owner playtest of APX 0.1** and the open questions below (legend difficulty, prices and
   rewards, drill length, Sage numbers, the HUD's quietness).
2. **Balance pass** from that feedback (all numbers are constants or JSON).
3. **Chūnin exam arena** (a built place for the tournament instead of a cleared field).
4. **Wood Release at epic scale** and detailed tailed beasts (ideas in `ART_STYLE.md`).
5. **Guy, part 2:** Reverse Lotus, the later gates, technique animations.

## Code map

| Package / path | What lives there |
|---|---|
| `com.noirm` | `Noirm`: entry point; `id()` / `vanillaId()` helpers |
| `com.noirm.training` | `TrainingSystem` (4 stats: XP sources, curve, drill cap, speed → movement, vitality → health, reserves → chakra), `TrainingData`, `TrainingDummyItem` |
| `com.noirm.player` | `ShinobiData` (the hub of all player data: resources, stats and the powers sub-objects), capability, `ShinobiEvents` (resources), `PowerEvents` (powers: first-join clan, ticking, sync, Resolve and awakening hooks), `PowerVisuals` |
| `com.noirm.combat` | `CombatSystem` (rules + constants, clinch, wall splat, juggles), `StyleSystem` (choosing styles), `CombatState`, `Perception` (incl. eye/state multipliers), `CombatData`, `CombatEffects`, `CombatEvents` |
| `com.noirm.clan` | `Clan`, `Nature` (basic + combined), `NatureSystem` (tree: owned/available/locked, unlock, mastery unlocks), `ClanTrait`, `AwakeningPath`, `ClanGenerator`, `WorldClans` (per-world registry), `ClanData` (canon), `ClanIdentity` (on the player), `ClanSystem` (joining) |
| `com.noirm.jutsu` | `JutsuDefinition`/`JutsuData` (JSON), `JutsuBehaviors` (what jutsu do), `HyugaTechniques` (Rotation, 64 Palms), `ElementalBehaviors` (bind, pillar, cloud, rain, beam, buff), `CanonTechniques` (dome, heal, Rasengan, shadow bind, shadow clone), `GreatTechniques` (strike, wave, nova), `ModeSystem` (toggle modes like Lightning Armor), `JutsuAreas` (lingering areas), `JutsuCaster` (casting, affinity, power, cost), `JutsuBook` (known, mastery, loadout), `JutsuProjectile`, `Substitution`, `EarthWallBlock`, `JutsuUser` (NPCs) |
| `com.noirm.dojutsu` | `Sharingan` (growth), `SharinganAwakening` (four routes, pressure), `Byakugan` (incl. Gentle Fist, chakra points), `ThreatSense` (eyes vs vanilla mobs/projectiles), `EyeData` |
| `com.noirm.awakening` | `AwakeningSystem`, `AwakeningData` |
| `com.noirm.biju` | `BijuSystem` (cloak and tails, sealing, born jinchūriki, beast names), `BijuData`, `BeastBond` (bond, requests, takeovers, chakra), `SealingScrollItem` |
| `com.noirm.data` | `CodecJsonLoader` (generic JSON data loader) |
| `com.noirm.network` | `NoirmNetwork` (protocol 22), `VfxPackets`, `JutsuCatalogPacket`, `CombatInputPackets`, `CombatClientPackets`, `PowerPackets`, `SyncShinobiDataPacket` |
| `com.noirm.client` | combat (`CombatKeys`, `CombatInput`, `LockOn`, `CombatCamera`, `PlayerAnimations`, `ClientCombat`), powers (`PowerKeys`, `ClientPowers`, `PowerHud`, `JutsuScreen`, `SharinganEyesLayer`), `ShinobiHudOverlay`, `VillageFog` (mist and haze inside villages), `NoirmRenderers`, `SparringNinjaRenderer`, `TrainingDummyRenderer`, `TailedBeastRenderer`, `ShadowCloneRenderer`, `BoltRenderer` (lightning), `ByakuganVision` (x-ray silhouettes, chakra points, radar), `EyeEffects` (heartbeat warning, afterimages, activation), `JutsuScreen` (cards + nature tree), `ClientJutsuCatalog` |
| `com.noirm.client.ui` | The UI kit every screen and the HUD use (`docs/UI.md`): `Theme` (colours, spacing), `Ui` (panel, header and seal, card, button, toggle, segment, slider, bar, row, chip, scrollbar, text cut with "…"), `TabStrip` (text tabs, sliding underline), `ScreenMotion` (open/close/tab fades), `HoverTracker`, `Smooth`, `Anim`, `HudKit`, `HudLayout`, `NoirmStyled` + `TooltipStyle` (kit tooltips) |
| `com.noirm.client.anim` | The animation engine: `HumanoidAnimator` (players and NPCs: gait, ninja run, air and landing, stance, moves, guard, flinch, lean, springs, hit-stop), `Rig` (the hierarchy: torso carries head and arms; plain Java), `KeyframeAnimation` (reads `player_animation/*.json`; easings incl. back/expo and Catmull-Rom), `SkeletonPose`, `NpcAnimations` (reload listener), `WeaponStances`. Drawn by `client/BendyModel`, `client/NoirmPlayerRenderer` (swapped in for players), `client/FirstPersonArms` |
| `com.noirm.weapon` | Ninja weapons (`NinjaWeapons` registry, `ThrowingWeaponItem`, `StaffItem`, `ChakraBladeItem`, `ExplosiveTagItem`, `ThrownWeapon`, `Throwing`, `Moveset` + `WeaponMoves`, `NinjaArsenal`); see `docs/WEAPONS.md` |
| `com.noirm.chakra` + `com.noirm.mixin` | Water walking (`WaterWalking`, `WaterWalker`; `BlockStateBaseMixin` gives the water surface a skin; config `noirm.mixins.json`) |
| `com.noirm.ally` | `Crew` (yield, recruit, sides, crew rules), `CrewBrain` (the crew mate's mind every tick: targets by behaviour/job/order, following, posts, duties; tuning constants at the top), `AllyMind` (respect, morale, retreat, support), `AllyTraining` (stat XP), `AllyFood` (feeding), `NatureTeaching` (lessons, queue), `Trait`, `Order`, `Occupation`, `Behaviour`, `AllyState` (saved crew data), `AllyMenu` + `AllyPackets` (the ally screen), `CrewPackets` (crew-wide orders, K screen) |
| `com.noirm.creator` | Character creation: `CharacterCreator` (rules, rolls, holding newcomers), `PlayerLook` (part ids, colours, height), `GeneticRoll`, `CreatorData`, `CreatorPackets`, `CreatorEvents`; client side `CreatorScreen`, `PlayerSkins` (skin from parts), `HairVolumeLayer` |
| `com.noirm.movement` | `MovementFeel` (slide, pivot, gravity, landing recovery; constants at the top); client side `MovementFeelClient` |
| `com.noirm.gore` | `Gore` (marks, severing, constants), `GoreMarks`, `GorePackets`, `SeveredLimb`; client side `GoreMarksLayer`, `SeveredLimbRenderer` |
| `com.noirm.world.wild` | Wild ninjas: `EncounterType`/`Encounters` (data in `data/noirm/encounters/`), `WildSpawner` (when/where, caps, danger), `WildNinjas` (groups, despawn), `WildTravelGoal`, `WildTargetGoal` |
| `com.noirm.senjutsu` | `Meditation` (rules and constants; `isMeditating`, `natureEnergy`), `MeditationData` (saved), `MeditationEvents` (ticking, what ends it) |
| `com.noirm.gear` | `NoirmGear` (wearable pieces), `GearItem`, `GearMaterial`, `ClanOutfits` (who wears what) |
| `com.noirm.vfx` | `Vfx` (palettes, emitters, presets), `VfxOptions`/`VfxKind`, `NoirmParticles`; client side `VfxParticle` |
| `com.noirm.world` | `Destruction` (craters, scorch, freeze, wall smash… and the regen queue; gamerules) |
| `com.noirm.world.village` | `NinjaVillageStructure` (where villages go; the site check; a hidden village's core start), `VillageWardStructure` + `WardPlacement` (one start per chunk of a hidden village), `VillagePiece` (saved per chunk), `VillagePlan` + `VillagePlanner` (the layout from a seed: streets and river rasters, districts, lots, props, people; cached, indexed per chunk), `VillageBuilder` (ground, streets, street furniture; draws lots per chunk), `VillageBuildQueue` (`/noirm village`, a few chunks per tick), `Waterways` (river, canals, banks, bridges), `Fortifications` (walls and bastions, gates, Kage Rock with stairs and tunnels, cliff and spire rings, fences), `VillageAtmosphere` (tells the client which village it's in), `VillageSpawns` (no raiders inside), `LotFrame` (draw a lot front-to-the-south), `Buildings` (homes, shops, façades, rooftops), `RoundBuildings` (spires, jōnin station, rock spires), `Yards` (yards, giant tree, cemetery), `Landmarks` (Kage residence, Academy, hospital, clan district, shrine, training grounds, hot spring), `Shapes` (drums, ring eaves, medallions), `Materials` (block mixes, colourways), `VillageCanvas`, `VillagePalette` (Leaf/Sand/Snow), `VillageKind`, `VillageResidents` (spawning, alarm), `VillageRoutineGoal` (rooftop runners, patrols, trainees) |
| `com.noirm.entity` | `SparringNinja` (+ jinchūriki, style, village resident flag, fights monsters in its village), `VillageMerchant` (villagers: shop roles, civilians, children, chief; day/night routine; speech bubbles), `VillagerChatGoal`, `VillagerSitGoal` + `SeatEntity`, `VillagerShelterGoal`, `ShadowClone`, `NinjaFightGoal` (melee, bows, jutsu by range), `NinjaThrowGoal`, `Threat` (who a fighter targets), `TrainingDummy`, `TailedBeast`, `BeastFightGoal` |
| `com.noirm.registry` | `NoirmBlocks`, `NoirmEntities`, `NoirmItems` (incl. ramen), `NoirmStructures` (structure + piece types) |
| `com.noirm.command` | `CommandGuide` (every command by section, `/noirm help`; the source of `docs/COMMANDS.md`), `Cmd` (shared helpers), `MeCommand`, `CombatCommand` (combat mode, styles), `PowersCommand` + `JutsuExtraCommand` (clan, natures, jutsu), `EyesCommand`, `MangekyoCommand`, `CrewCommand`, `SpawnCommand`, `KitCommand` (kits from `data/noirm/kits/`), `WorldCommand`, `MasterCommand`, `NoirmCommand` (bars, stats), `AnimCommand` |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests`, `CombatGameTests`, `PowersGameTests`, `TrainingGameTests`, `ByakuganGameTests`, `BijuGameTests`, `ThreatSenseGameTests`, `NatureGameTests`, `DangerGameTests`, `CanonGameTests`, `StyleGameTests`, `CrewGameTests`, `GreatGameTests`, `VillageGameTests`, `VillageLayoutGameTests`, `VillageWorldgenGameTests`, `VillageLifeGameTests`, `TestClans`, `TestKit` (shared helpers) |
| `data/noirm/` | `combat/` (moves, styles: basic, brawler, strong_fist, gentle_fist, beast), `clans/` (canon), `jutsu/` (by nature, `byakugan/`, `biju/`), `recipes/`, `structures/empty.nbt`, `worldgen/structure` + `structure_set` (villages), `tags/worldgen/biome/has_structure/` |
| `tools/skingen/` | `gen_parts.py` + `looks.py`: the skin parts and look tables behind random ninja looks |
| `tools/gear/` | `gen_gear.py`: the gear textures and icons |
| `tools/animgen/` | `gen_anims.py` + `motion.py`: author and generate every animation (timed to the moves); `check_uv.py`: checks the bendy model's skin UVs |
| `tools/weapongen/` | `gen_weapons.py` (weapon icons), `gen_moves.py` (weapon moves and styles) |
| `tools/client-smoke/` | Run the real client off-screen (Hyūga player, dummy, ninja behind a wall, a beast, a subdued Nine-Tails) |

## Decisions made

- **Forge 1.20.1**, official mappings, single-player first.
- **Pillars:** combat feel, training and masters, villages and world gen, exploration. Missions
  are secondary. Squads (recruited allies) come later.
- **No copying NYDO assets.** MIT Boundless animations and the CC-BY katana are the exceptions.
- **Art:** placeholders and vanilla textures until the mechanics are fun.
- **Combat:** switchable (R); launcher = crouch + heavy; NOIRM hits cancel vanilla knockback;
  the server is authoritative.
- **Perception:** proportional to the strength ratio (cap 2.5×); from speed + taijutsu, boosted by
  the Sharingan, Keen Eyes, being locked in and the cloak. It applies both ways.
- **Clans (owner):** random at first join; mostly custom generated clans in an **open-ended,
  growing per-world list** (canon ~10%); villages will reuse the same list.
- **Natures (owner):** the clan gives a great bonus; others are very hard to get (training, M3.5).
  Sharingan-copied jutsu of a missing nature work at an imitation penalty (0.4).
- **Jutsu casting (owner):** a 4-slot loadout, each jutsu with its own feel (tap / charge /
  channel). Hand seals and wheels were rejected.
- **Sharingan (owner):** all four of perception, precognition, copy and genjutsu. It is awakened
  only through the near-death awakening, then grows with use.
- **Awakening (owner):** Resolve-based, identity-dependent (Uchiha → Sharingan, jinchūriki → beast
  takes over, nature-surge clans, will). Guy's students → gates (M4).
- **Biju (owner):** all three routes to becoming a jinchūriki: born one (3%), defeating and
  sealing a beast, or someone sealing one into you. Beasts can be sealed into yourself, other
  players or ninjas. Beasts are subdued first, then sealed or finished. Placeholder art (scaled zoglin).
- **Beast bond (round 4):** tails are drawn one at a time; how many you can hold safely depends
  on the bond (−100…100). Version 1 orange, version 2 crimson, full bond gold.
- **Allies (round 4):** respect starts from relative power and moves with your actions; below 5
  they leave. Crew mates never hurt their side, and their jutsu don't hurt you.
- **Byakugan (owner):** continue with it for the Hyūga (born with it, no awakening needed).
- **Training (owner):** first part = the dummy plus speed and taijutsu growth. Solo drills cap at 30;
  past that only real opponents teach (stronger = more).
- Game tests use fixed clans (`TestClans`) so random rolls never change timings; tests that
  trigger focus or eye effects run in their own batches.
- **Natures (owner):** a tree like the owner's reference image, in our own style: 5 basic natures,
  8 two-part combinations, Dust (three parts). A combination is available once you own both parts
  and have a jutsu of each at ★2; basic natures you lack need very hard training (not built yet).
- **Jutsu learning:** clan starters, then mastery chains (★2 on one teaches the next).
- **Eyes vs the world (owner):** the same rules apply to vanilla mobs and projectiles.
- **Stats (owner):** four trained stats: taijutsu, speed, vitality (health), reserves (chakra).
- **Animation (round 5):** our own engine (`client/anim/HumanoidAnimator`) drives players and NPC
  ninjas alike on one jointed model (`client/BendyModel`: elbows, knees, waist; the torso carries
  the head and arms). PlayerAnimator is gone (one less mod to install). Animations are generated by
  `tools/animgen/gen_anims.py` and timed to each move's windup. See `docs/COMBAT_DESIGN.md`,
  "Animation engine (round 5)". `/noirm anim <name> [ticks] [targets]` previews any animation.
- **Weapons (round 5):** see `docs/WEAPONS.md`. A weapon in hand picks its moveset in combat mode.
- **Sharingan (round 5):** awakened by near death, a comrade's death, a long fight against a much
  stronger foe, or built-up pressure; grows only in real fights. See `docs/POWERS_DESIGN.md` §4b.
- **Water walking (owner, round 6):** stays free for everyone with chakra. See `docs/POWERS_DESIGN.md` §12.
- **Sharingan pace (owner, round 6):** the awakening routes and XP pace feel right; keep them.
- **Footwork (owner, round 6):** the feet stay on the ground and are the base of every attack
  (leg IK in `HumanoidAnimator.plantFeet`; strikes sink and drive through `motion.strike`).
- **Hand seals (round 6):** canon sequences per jutsu in `data/noirm/jutsu_seals/`, written with
  their animations by `tools/animgen/seals.py`; charged and channelled jutsu play them, every cast
  names them. (Seals still don't gate casting: the owner rejected seal input.)
- **Mangekyō (round 6):** strain never heals (canon) until the Eternal Mangekyō; abilities are
  jutsu that need the Mangekyō open. See `docs/POWERS_DESIGN.md` §4c.
- **Commands (v0.9.1):** every command lives under `/noirm`, in sections (`/noirm help`); names
  default to the mod's namespace (`/noirm kit tools`, `style set strong_fist`). Every command is
  listed in `CommandGuide` with an example that a game test parses; `docs/COMMANDS.md` is generated
  from it (`tools/docs/gen_commands.py`).
- **Masters (round 6):** a master is a JSON file in `data/noirm/masters/`; Might Guy is the first.
  Passing his spar grants the Strong Fist even below taijutsu 18. The Eight Gates burn health.
- **Allies learn natures (crew overhaul):** you must own the nature; 2 in-game days of full-pace
  study near you (Train/Meditate), half pace under other orders; faster fighting together,
  meditating together (×2) or if you mastered it. More lessons wait in a queue.
- **Crew mates (crew overhaul):** one mind per tick (`CrewBrain`); a training dummy is never a
  target outside Train; far behind or stuck, they jump to your side.
- **Blueprint (owner):** keep what we have; build Phase 1 where it fits; ask before rebuilding a
  system (see `docs/BLUEPRINT.md`).
- **Blueprint answers (owner, after v0.10.0):** the **character creation screen replaces the random
  first-join clan** (build it, with placeholder art); **movement** gets the blueprint's inertia and
  heavier gravity, but kept light so combat stays smooth; **gore: yes**; perception focus and the
  just-frame parry stay **heavy attacks only** (the counter window is untested by the owner yet);
  meditation keeps training Reserves; **no melee while forming seals**.
- **Art direction (owner, after v0.11.0):** our own designs, not plain Minecraft-resolution skins:
  **HD textures at 4× (256×256 for the 64×64 skin layout)** for players, ninjas and worn gear,
  drawn by our generator scripts. New characters **start in just underwear** (clothes come from gear).
- **Owner answers (v0.11.0):** creator rolls (1 + 3 rerolls, keep any) stay; movement is fine;
  the just-frame counter window (0.5 s) looks good; **gore should be harsher with real blood**: on the
  ground, on the character and on clothing.
- Text is in English (`en_us`) and Spanish (`es_es`, `es_mx`).
- **Villages (round 4):** built procedurally in Java (no NBT files), `top_layer_modification`
  step so trees are cleared. Residents never start fights with players; attacking one alarms the
  village.
- **Villages v3:** a hidden village is ~545 blocks across, built as a core start plus one ward
  start per chunk (the 8-chunk structure reach can't be raised); spacing 96 / separation 48 chunks.
  Everything from vanilla blocks; the research (`docs/research-villages-v3.txt`) is motifs only.

- **UI:** one kit for every screen (`client/ui`, `docs/UI.md`): flat ash panels, one hairline, bone
  text, the blood-red seal for primary actions and the active tab, gold only for "you can do this
  now" and money. Screens keep a colour of their own only where it means something (the masters'
  leaf green, class and rank letters, a legend's gold). Time-based motion only.
- **Game tests:** the test world is a normal world and the tests sit about 60 blocks underground, in
  deepslate. The framework only clears a small space round each 3×3 test, so a floor bigger than that
  uses `TestKit.openFloor` (stone floor + air above), and a test that counts or fights what's around
  it calls `TestKit.clearStrays` first (a failed earlier test leaves its fighters behind).

## Open questions for the owner

- **APX 0.1:** legends one-shot beginners: too hard? Legends kept 1,000 blocks from spawn and away
  for your first 2 hours: right? Shop prices, mission rewards and masters' prices? Nature drills
  take 3 in-game days: too long? Sage numbers, and should the petrified statue mean death? Kill-XP
  pool size? NPC Durability equal to their strength? Yin/Yang drills? A real chūnin exam arena?
  Jutsu knockback? Should becoming a jinchūriki be gated? Art for the missions board?
- **UI:** is the HUD too quiet now (numbers only while they change, always in combat mode)? The ally
  Orders tab is chakra blue instead of purple: OK?

- **UI (phase B):** the shop now has a 店 seal like the other screens; masters keep a leaf-green seal.
  OK? Is anything from the old look missed (the green Buy/Sell buttons, the gold titles)?

- **Jutsu sets:** strength tiers 20/40/100 right? Weak Hyūga can't know Rotation (A rank): OK? Crew
  self-learning (one jutsu per mastery milestone) too fast or slow?
- **Wild ninjas:** about one group every 1.5 minutes, max 2 groups / 8 ninjas near you: busier or quieter?
  Should squads defend you against rogues that attack you?
- **Weapons:** the yari and nodachi look short in hand (custom long models later?). More chakra-flow
  weapons (fire nodachi)? Balance of the nodachi, tantō back-stab, claw bleed.
- **Armor:** full samurai plate −12% speed right? Assassin shadow only while sneaking? Medics as a village role?

- **Art:** do the new eyes have soul now? Names "Steady"/"Fierce" OK? Byakugan glow softer?
  Gear wear level (dirt/fraying) right, also on clean robes? Folds deep enough? Item icons stay 16×16?
- **Gore v2:** enough blood? Bigger pools? Drying 45 s / stains 2–3 min right? An in-game gore setting?
- **Fire/lightning:** fire like the panels? Less smoke? Should lightning really light the area (temporary
  light blocks)? Majestic Destroyer Flame too strong? Unlock chains right?

- **Seals:** show the "hands busy" hint on every blocked press, not just once per cast?

- **Casting:** should combined natures (ice, lava, storm…) join the clash wheel?

- **Animations:** which moves still feel weak? First person: are the arms the right size and place?
  Should the ninja run happen on every sprint, or only above a certain speed stat?
- **Mangekyō:** should strain ever heal slowly? Are ~15 uses before weakening and ~20 to blindness
  too harsh? Should a transplant need a medical ninja or a scroll? Should players drop their eyes
  on death? Is 8% of 3-tomoe Uchiha ninjas having one right?
- **Might Guy:** is the test (6 clean hits or a guard break in 90 s) fair? Gate health drain? Guy
  appears in every Leaf village: fine, or only one per world?
- **Ally teaching:** is ~2 in-game days per nature (half pace when not training) right?
- **Meditation:** it trains Reserves (capped at 30 like other drills); the blueprint says "no
  passive grinding". Keep it? Should nature energy be lost on death?
- **Gear:** do the new clothes look right on the body (hats, kimono hems, cloaks only reach the waist)?
- **Weapons:** damage and throw cooldowns; should shields fully block guarded hits (players too)?

- Ally respect: too quick or too slow to change? Should allies ever betray you?
- Do the NPCs' bent limbs look right? Should the player bend too (needs bendy-lib, an extra mod)?
- How do the animations feel now that they show? Which moves need new or different ones?
- Jutsu balance: costs, cooldowns, how fast mastery chains unlock; which natures need more jutsu?
- How hard should training a basic nature you weren't born with be (time, a master, an item)?
- Should Yin, Yang and Yin-Yang join the tree (genjutsu and medical / body jutsu)?
- Where should tailed beasts live in the world?
- Villages: does the ~545-block capital feel big enough, or too big to walk? Denser or more open?
  Capitals are rare (spacing 96 chunks, often 2,000+ blocks apart): more often? Should the Kage
  teach or give missions?
