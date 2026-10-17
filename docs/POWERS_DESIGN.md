# Powers design: clans, jutsu, Sharingan, awakening, tailed beasts

_Living design doc. "Owner" marks the owner's decisions; everything else is a proposal that can
change after playtesting. All numbers are tunable (where is noted per section)._

## 1. Clans (owner: random, mostly custom, an open-ended list)

- **Every world grows its own clan list** (`WorldClans`, saved with the world). When someone
  needs a clan (a new player now; villages and NPC families later), they get:
  ~10% a **canon** clan (Uchiha, Hyūga, Uzumaki, Senju: `data/noirm/clans/*.json`), otherwise
  50/50 a **custom clan this world already knows** or a **brand-new generated one**. So you keep
  meeting both new and familiar clans; there's no fixed limit.
- A generated clan has a Japanese-style name, **1–2 natures** (one strong, sometimes a second),
  **2 traits** (out of 10), small speed/taijutsu leanings, and an **awakening path**.
- **Traits:** Deep Reserves (+30 chakra), Endurance (+30 stamina), Swift (+3 speed), Fierce (+3
  taijutsu), Iron Body (+30 posture), Keen Eyes (+10% perception), Iron Will (Resolve +50%,
  awakening +10%), Chakra Flow (jutsu −15% chakra), Quick Recovery (stamina +30%), Jutsu Prodigy
  (mastery ×2).
- Players roll their clan on first join (a chat message explains it). `/noirm clan` shows it again.

## 2. Natures (owner: clan gives a great bonus; others very hard to get)

- Affinity: **strong 1.5** (clan's main), **natural 1.0** (second), **trained 0.5** (future:
  long training), **imitation 0.4** (Sharingan-copied jutsu of a nature you lack).
- Power = 0.5 + 0.5 × affinity (strong 1.25×, trained 0.75×). Cost = 1.5 − 0.5 × affinity (strong
  0.75×, trained 1.25×). You can't use a nature jutsu without the nature (except imitation).
- Training a new nature isn't built yet (that's M4/M5); `/noirm nature add` simulates it.

## 3. Jutsu (owner: a 4-slot loadout, each jutsu with its own feel)

- In **combat mode**, keys **1–4** cast the jutsu in your 4 loadout slots (instead of switching
  hotbar slots). **J** opens the jutsu screen to change the loadout.
- Each jutsu is a JSON file (`data/noirm/jutsu/<nature>/<name>.json`) on a reusable behavior:

| Jutsu | Nature | Feel | What it does |
|---|---|---|---|
| Great Fireball | Fire | hold to charge, release | Flying ball of fire; explodes; bigger the longer you charge |
| Fire Stream | Fire | hold to channel | Flamethrower cone while held (not a starter: learn/copy) |
| Water Bullet | Water | tap | Fast projectile, big knockback and posture damage |
| Air Palm | Wind | tap | Cone blast: knockback, posture, turns enemy projectiles around |
| Earth Wall | Earth | tap | 5×3 wall of earth in front of you, crumbles after 10 s |
| Chidori | Lightning | charge (min 0.5 s), release | Dash forward and strike the first enemy: damage, stun, posture |
| Substitution | — | tap | For a moment, the next hit lands on a log; you appear behind the attacker, who is thrown off |
| Body Flicker | — | tap | Blink up to 7 blocks forward |
| Sharingan Genjutsu | Sharingan 2+ | tap | Stun and daze the enemy you look at; stronger minds may resist |

- **Starters:** your clan's natures' jutsu + Substitution + Body Flicker.
- **Mastery:** every cast earns XP; 5 levels; each level is +10% power, −8% cost and cooldown.
- Jutsu hits go through guard, parry, dodge and substitution like melee does.

## 3b. Nature tree and the jutsu list (owner: variety per nature and combination)

- **Combined natures** (kekkei genkai): Wood = Earth + Water, Ice = Wind + Water, Lava = Fire +
  Earth, Storm = Water + Lightning, Boil = Fire + Water, Explosion = Earth + Lightning, Magnet =
  Wind + Earth, Scorch = Fire + Wind. **Dust** (kekkei tōta) = Fire + Wind + Earth.
- A combination is **available** when you own all its parts and have a jutsu of each at **★2**;
  unlock it in the jutsu screen's Nature Tree. Its affinity is your weakest part's (max 1.0).
  8% of new clans carry a combination in their blood (with both parts).
- **Learning jutsu:** your clan's natures give their starters; the rest come by **mastery chains**
  (★2 on one jutsu teaches the next, shown on the card); combinations give theirs on unlock.

| Nature | Jutsu (★2 chain order) |
|---|---|
| Fire | Great Fireball → Phoenix Flower (5-shot fan) → Fire Stream |
| Water | Water Bullet → Water Dragon (charge, blast) → Water Prison (bind) |
| Wind | Air Palm → Vacuum Bullets (3-shot) → Great Breakthrough (huge cone) |
| Earth | Earth Wall → Earth Spikes (pillar) → Swamp of the Underworld (slowing area) |
| Lightning | Chidori → Lightning Armor (speed, strength, resistance) → False Darkness (beam) |
| Wood | Wood Binding (bind), Deep Forest Spears (pillar) |
| Ice | Ice Needles (7-shot, slows), Ice Prison (bind) |
| Lava | Lava Globs (3 burning blasts), Melting Apparition (channel) |
| Storm | Laser Circus (5 beams), Thunder Cloud (lightning rain) |
| Boil | Skilled Mist (weakening mist), Boiling Burst (cone) |
| Explosion | Explosive Clay (charge, big blast), Landmine Fist (pillar) |
| Magnet | Iron Sand Bind, Iron Sand Spears (4-shot) |
| Scorch | Scorching Orbs, Scorch Wave |
| Dust | Detachment of the Primitive World (charged beam), Dust Cube |

- New reusable behaviors: **bind**, **pillar**, **cloud**, **rain**, **beam**, **buff**; projectiles
  take `count`/`spread` (fans) and `effect` (e.g. `"slowness:1"`); `block` gives a jutsu its look.

## 4. Sharingan (owner: all four abilities)

- Uchiha only (the clan's `kekkei_genkai`). Born **dormant**; it awakens by one of four routes
  (section 4b), then grows from 1 to 3 tomoe through use in real fights (300 / 1200 XP) or
  further near-death awakenings. **G** toggles it; it drains 0.8 / 1.6 / 2.4 chakra per second
  (regen is 1/s).
- **1+ tomoe:** perception +25% per tomoe (enemy wind-ups look slower: the owner's perception idea).
- **2+ tomoe:** **precognition** (an attacker glows, plus a red flash and chime before their hit
  lands) and the **Sharingan genjutsu** jutsu.
- **3 tomoe:** **copy**: watch someone cast a jutsu (within 16 blocks, in view) and you learn it.
- Red glowing irises render on players (everyone sees them) and on Uchiha ninjas.
- **Against everything (owner):** precognition (2+ tomoe, or the Byakugan) also reads vanilla
  mobs: a melee mob in reach, a skeleton drawing, a creeper swelling, a spider about to leap, and
  any projectile on a collision course. Perception (same ratio rule) slows projectiles within 8
  blocks; arrows keep their damage (`ThreatSense`).
- **Alive (owner):** the warning is a heartbeat (lub-dub edge pulse + sound) that speeds up as the
  hit nears, with markers pointing at each threat; attackers leave afterimages; opening the eyes
  flashes and spins three tomoe; a faint red tint while active (`EyeEffects`).

## 4b. Sharingan awakening and growth (owner, round 5: "a clearer way")

**Awakening** (dormant Uchiha only; `SharinganAwakening`):

| Route | What it takes |
|---|---|
| Near death | The Resolve awakening (section 5), as before: locked in + the Sharingan |
| A comrade falls | Seeing a crew mate (or another player) die within 24 blocks, in sight, at someone else's hand |
| A long, hard fight | 60 s of trading blows with an opponent of at least 1.5× your power (pauses up to 5 s keep it going; 15 s ends it) |
| Pressure | 0–100, built by every real fight: damage taken (0.6/pt) and dealt (0.3/pt) × opponent strength (0.5–2), perfect dodges/parries (+3), hard fights (+0.5/s). When full, the **next intense moment** (a perfect dodge or parry, a hit of 20%+ of your health, or dropping below 40%) opens the eyes |

- **Hints** as the pressure crosses 25 / 50 / 75 / 100: an action-bar line, a red flicker at the
  edges of vision, a heartbeat from 75. Halfway through a hard fight: "your eyes ache…".
- The moment: title + subtitle per route, red flash and ring, the eyes open and stay open for
  10 s without draining chakra.

**Growth** (only while open, only in real fights; the dummy is a drill):

| Source | XP |
|---|---|
| Each second in a real fight | 1 × opponent strength (0.5–3×) |
| Each hit you land on a real opponent | 2 × opponent strength |
| Perfect dodge / parry | 10, **+15** if the eyes foresaw that attack (precognition, 2+ tomoe) |
| Reading a jutsu cast in front of you (1–2 tomoe, in a fight, once per 5 s) | 15 |
| Seeing a comrade fall (already awakened) | 150 |
| The training dummy (hits, time) | 1 each, **at most 100 in total** |

- Copying happens at 3 tomoe (the top), so it gives no XP for now (a future Mangekyō could use it).
- Each new tomoe: a title ("Sharingan: 2 tomoe"), the eyes flare open (free for 10 s), and the
  screen spins that many tomoe.
- **Shown** in the jutsu screen (sidebar, when no jutsu is hovered or selected: tomoe and an XP
  bar, or how the dormant eyes feel) and as a thin bar next to the Sharingan line on the HUD.
- **NPC Uchiha:** 35% of world-rolled Uchiha ninjas are dormant; they awaken when a fight leaves
  them under 30% health, or when a crew mate falls nearby.
- Cheats: `/noirm sharingan <0-3>`, `/noirm sharingan xp <n>`, `/noirm sharingan pressure <0-100>`.
- Tuning: constants at the top of `Sharingan` (growth) and `SharinganAwakening` (routes, pressure).

## 4c. The Mangekyō Sharingan (owner: "eye transplants, special abilities for each one")

Code: `com.noirm.dojutsu` (`Mangekyo`, `MangekyoAbility`, `MangekyoAbilities`, `Susanoo`,
`MangekyoTransplant`, `SharinganEyeItem`, `MangekyoNpcs`), client `MangekyoEffects` and the
pattern quads in `SharinganEyesLayer`. Jutsu JSON in `data/noirm/jutsu/mangekyo/`.

**Awakening** (born Uchiha with 3 tomoe; rare and dramatic):

| Route | What it takes |
|---|---|
| A bond broken | A crew mate with respect 60+ dies within 16 blocks, in sight, at someone else's hand |
| A death before your eyes | Another player dies within 16 blocks, in sight |
| The edge of death | The near-death (Resolve) awakening with 95+ Resolve |

The moment: a dark red title and subtitle per route, the Elder Guardian's curse, a heartbeat, a
black-and-red ring and a quake; on screen a dark red flash and the eye's pattern revealed, huge
and turning. The eyes stay open for free for 15 s.

**Every Mangekyō is unique:** one of 8 original patterns (Five Petals, Twin Moons, Whirlpool,
Thorn Crown, Lattice, Eclipse, Tide, Broken Halo; drawn by `tools/eyegen/gen_eyes.py`), two eye
abilities rolled (weighted) from the pool below, and **Susanoo** in a colour of its own.

**How they're used:** the abilities are **jutsu**. The awakening teaches the two eye abilities and
Susanoo and puts them in free loadout slots (otherwise the jutsu screen equips them). They need
the Mangekyō open (G), as the Sharingan genjutsu needs 2 tomoe. No new keys, and NPCs use them
through the same jutsu AI. A Sharingan can't copy them.

| Ability | What it does | Strain |
|---|---|---|
| **Amaterasu** (weight 3) | Black flames where you look (24 blocks): on a creature, or a patch on the ground that lights whoever enters (8 s). 3 dmg/s × power for 10 s; water doesn't put them out | 6 |
| **Tsukuyomi** (3) | Eye contact (12 blocks; a player must be facing you): frozen 4 s, 25% of max health torn from the mind (× power, at most 60), weakened 10 s; a player sees the world turn red and black. An open Mangekyō resists | 8 |
| **Kamui** (2) | Looking at someone: hurt and warped 8–14 blocks away. At an incoming projectile: swallowed. Otherwise (or crouching): intangible for 2 s (hits and projectiles pass through) | 4–5 |
| **Kotoamatsukami** (1) | A creature (not a player) fights for you for 60 s: it can't target you or your crew and hunts your enemies. 3-minute cooldown | 12 |
| **Izanagi** (1) | Armed for 10 s: a death in that window doesn't happen; you're back where you cast it, at the health you had (at least 30%). 5-minute cooldown | 3, +20 when it triggers |
| **Susanoo** (both eyes) | Grows every 3 s while kept up: ribcage (damage ×0.6) → torso and arms (×0.45; the key now swipes: 12 dmg, 4.5 blocks) → complete (×0.3; 18 dmg, 6.5 blocks; slower). 25 chakra to summon, 10 per swipe, 3/5/7 chakra a second; crouch + key dismisses; out of chakra it collapses. Drawn as a shell of particles | 4, +0.1/s per stage |

**The cost: blindness.** Strain (0–100) never heals by itself. Hints at 25 / 50 / 70 / 85 / 100;
the client darkens the edges of vision from 10 on, more at each step. From 70 the abilities come
out at 75%, from 85 at 50%. At 100 you're **blind**: the eyes close and won't open (no Sharingan
either), no eye techniques, and the screen stays dark around a dim centre.

**The Eternal Mangekyō** cures it: transplant another Mangekyō's eyes. Strain goes to 0 and never
builds again, abilities ×1.25, perception +50% (a Mangekyō gives +25%), the eyes drain half the
chakra and Susanoo grows twice as fast and drains 40% less; the donor's pattern is drawn over your
own (merged).

**Eye transplants.** Eyes are items (`Sharingan Eye`, `Mangekyō Eyes`) that remember tomoe,
pattern, abilities, Susanoo colour, strain and donor (NBT). Hold use for 3 s (the "surgery"):

| You | The eye | Result |
|---|---|---|
| Uchiha with your own Mangekyō | Mangekyō | **Eternal Mangekyō** |
| Uchiha without one | Mangekyō | the donor's Mangekyō (pattern, abilities, colour, strain) |
| Uchiha | Sharingan with more tomoe | your eyes reach its tomoe |
| Not an Uchiha | Sharingan | **one implanted eye** (the Kakashi case): always open, can't be closed, 0.5 chakra/s for good (with none left it costs hunger) |
| Not an Uchiha | Mangekyō | one implanted Mangekyō eye: only its first ability, no Susanoo, strain ×1.5 |

Refused (the eye is kept): a weaker Sharingan, a plain eye for a Mangekyō, anything for an
Eternal Mangekyō, any Sharingan for a Byakugan user.

**Where eyes come from:** a Mangekyō ninja always leaves its eyes; a fallen crew Uchiha always
leaves its Sharingan; any other Uchiha ninja beaten by you or your crew leaves an eye 12% of the time.

**NPCs:** 8% of 3-tomoe Uchiha ninjas (rolled once) carry a Mangekyō (it shows in their eyes).
The jutsu AI uses Amaterasu, Tsukuyomi and Kotoamatsukami at range, Kamui and Susanoo up close,
Susanoo as an opener, and Izanagi or Susanoo when hurt. Their Mangekyō is kept in the entity's
Forge persistent data; they don't strain.

- Cheats: `/noirm mangekyo` (info) · `awaken` · `clear` · `eternal` · `strain <0-100>` ·
  `pattern <0-7>` · `ability <left|right> <name>` · `eye <1-3>` · `eyes` · `npc`.
- Tuning: constants at the top of `Mangekyo` (awakening, strain, power), `MangekyoAbilities`,
  `Susanoo`, `MangekyoNpcs`; per-ability numbers in each jutsu JSON.
- Not yet: a medical-ninja ally doing the surgery; taking a player's eyes; Kamui's own dimension;
  Susanoo as a real model.

## 5. Awakening (owner: Resolve, identity-based)

- **Resolve** (0–100, visible in combat mode) builds from damage dealt (more against stronger
  enemies), damage taken, and especially perfect dodges and parries.
- When a hit would leave you under 20% health, the chance is Resolve × 0.85 (+10% Iron Will;
  needs ≥ 20 Resolve; at most once per 5 minutes). On success the hit leaves you at ≥ 1 health
  and you're **LOCKED IN** for 20 s: +25% health back, half chakra, full stamina, a brief
  invulnerability, Speed II, ×1.3 damage, perception and jutsu power. Then **exhausted** for 30 s
  (Slowness, Weakness, half chakra regen).
- What awakens depends on who you are:
  - **Uchiha:** the Sharingan awakens (or gains a tomoe) and switches on for free.
  - **Nature-surge clans:** their natures' jutsu cost nothing and hit ×1.5 while locked in.
  - **Jinchūriki (overrides the clan):** the beast takes over (see below).
  - **Others:** the will-to-live surge.
- Future: Might Guy's students open a gate (needs the master, M4).

## 6. Jinchūriki cloak (owner: start with the cloak)

- Become one by birth (3%), a Sealing Scroll (section 10), or `/noirm biju seal <1–9>`. **B** toggles the chakra
  cloak: 20 s + 2 s per tail, then a 60 s cooldown. Cloak: +20% speed, ×1.3 damage, ×1.15
  perception, ×1.2 jutsu power, ×3 chakra regen, ×1.5 stamina regen; an orange aura.
- **The beast takes over** (awakening): the red cloak for 20 s: +40% speed, ×1.6 damage, ×1.3
  perception, ×1.6 jutsu power, **no guarding**, and it burns you (1 damage every 2 s, never below
  2 health). Full tailed beasts (giant forms) need custom art and come later.

## 7. Sparring ninjas

They roll a clan like players (so they show the world's clans, e.g. "Sparring Ninja (Kazemori)"),
cast their clan's jutsu from range, sometimes escape with Substitution, and Uchiha ninjas fight
with an active Sharingan (1–3 tomoe). Choose with `/summon noirm:sparring_ninja ~ ~ ~ {Clan:"uchiha"}`.

## 8. Training (owner: the dummy, speed and taijutsu growth first)

- **Taijutsu XP:** a landed NOIRM hit gives 2 (heavy +1, blocked ×0.5); a parry gives 6.
  **Speed XP:** a dodge gives 1, a perfect dodge 6, sprinting 1 every 2 s.
- A point costs `20 + 8 × stat` XP (100 at 10, 260 at 30, 500 at 60); stats cap at 100.
- **Drills** (the dummy, plain dodges, running) only teach up to **30**. **Real opponents**
  (hostile mobs, ninjas, players, beasts) teach ×1.5 × their strength ÷ yours (clamped
  0.25–3). Animals teach nothing.
- **Speed moves you faster:** +0.4% movement per point above 10 (+36% at 100).
- The **training dummy** (crafted from 2 hay bales + 4 sticks) never dies, never moves, shows
  your combo and its damage, and is picked up by crouch-punching it outside combat mode.

## 9. Byakugan (owner: continue with it)

- **Hyūga are born with it**; G opens it (0.6 chakra/s). They start knowing Eight Trigrams Palm
  Rotation and 64 Palms.
- **Open:** chakra silhouettes of everything within 40 blocks, **through walls** (hostiles
  reddish); perception ×1.15; a warning for every attack aimed at you; **guard from any
  direction**.
- **Gentle Fist** (your melee with it open): posture damage ×1.4, blocked hits keep 50% damage
  (not 25%), each hit drains 4 chakra (heavy 8) and closes 2 chakra points (heavy 4). **24 closed
  points = no jutsu for 6 s.** Points reopen after 5 s without a Gentle Fist hit.
- **Eight Trigrams Palm Rotation** (A, tap, 30 chakra, 12 s): a 0.8 s spinning dome; throws
  everyone within 3.5 blocks back, turns projectiles around, nothing gets through.
- **Eight Trigrams 64 Palms** (A, tap, 35 chakra, 20 s): six waves on the enemy in front (2, 4, 8,
  16, 32, 64 palms), holding them in place, closing enough points to seal their jutsu; the last
  palm sends them flying.

## 10. Tailed beasts (owner: be born one, fight them, seal them into yourself or others)

- **Beasts** have 1–9 tails: health 100 + 50/tail, speed/taijutsu 14 + 5/tail, size ×(0.8 +
  0.2/tail). They use the combat system with the **beast style** (claw, back-claw, slam: long,
  readable wind-ups) and, from range, stop and charge a **Tailed Beast Ball** for 1.5 s.
- **Subdued, not killed:** at 10% health a beast lies down for 60 s, then gets up at 50%, angry.
- **Sealing Scroll** (6 paper + 2 ink + 1 gold → 2): use it on a subdued beast to capture it. A
  full scroll seals the beast **into whoever you use it on** (a player or a sparring ninja), or
  **into yourself** (use in the air). Crouch + use on the ground releases it (it attacks you).
  One beast per host.
- **Born jinchūriki:** 3% of newcomers get a random beast at birth.
- **Ninja jinchūriki:** 5% of sparring ninjas; below 35% health they take the cloak (×1.3
  damage, perception), and can throw a Tailed Beast Ball; **when one dies, its beast breaks free**
  and goes for the killer.
- **Tailed Beast Ball** (S, charge, 40 chakra, 30 s, needs the cloak): huge dark explosion.
  Every jinchūriki learns it when a beast is sealed in them.
- Names: Shukaku, Matatabi, Isobu, Son Gokū, Kokuō, Saiken, Chōmei, Gyūki, Kurama.

## 11. Vitality and Reserves (owner: health and chakra grow too)

- **Vitality:** +0.5 max health per point above 10 (+5 hearts at 30). XP = 2 per damage taken;
  real opponents × their strength, falls/fire/etc. are drills (cap 30).
- **Reserves:** +5 max chakra and +2% chakra regen per point above 10 (double chakra at 30).
  XP = 0.25 per chakra spent (jutsu, eyes).

## 12. Water walking (chakra control)

- Everyone (players and NPC ninjas, crew mates included) walks and runs on any water's surface:
  still, flowing, rivers, ocean. Bubble columns stay as they are. Lava walking exists but is off
  (`LAVA_WALKING`).
- Needs 2+ chakra. Standing costs 1 chakra/s (cancels the base regen), walking 1.6/s, running
  2.4/s; run dry and you sink. **Sneak** to sink or dive; **hold jump** near the surface (10+
  chakra) to spring back onto it. NPCs can always do it unless beaten, and spring back up if
  knocked in.
- Falls onto water stay harmless (as plain water always was), costing 1 chakra per block past 3:
  you land on your feet on the surface with a splash.
- How: a mixin gives the top water block a thin solid skin (just above the visible water) for
  whoever can walk on it; the rule only reads what the client also knows, so movement agrees.
- Tuning: constants at the top of `chakra/WaterWalking`.

## Where to tune

- Clans: `data/noirm/clans/*.json`, `ClanGenerator`, `WorldClans` (chances).
- Jutsu: `data/noirm/jutsu/**.json`; behaviors in `JutsuBehaviors`; formulas in `JutsuCaster`.
- Sharingan: constants in `Sharingan`. Byakugan: `Byakugan`, `data/noirm/jutsu/byakugan/`.
  Awakening: `AwakeningSystem`. Cloak and jinchūriki chances: `BijuSystem`.
- Training: constants in `TrainingSystem`. Beasts: `TailedBeast`, `BeastFightGoal`,
  `data/noirm/combat/moves/beast/`, `data/noirm/jutsu/biju/`.

## Round 2 changes (2026-10-13)

- **Byakugan vs Sharingan (owner):** the Byakugan no longer predicts or raises perception. It has
  no blind spot (threats from behind, outside 70° of your view, are shown; guard works in any
  direction), shows chakra points on bodies within 16 blocks and a 360° radar of chakra
  signatures. Its fighting style is the Gentle Fist. Prediction and copying stay Sharingan-only.
- **Yin, Yang, Yin–Yang:** non-elemental natures. Uchiha lean Yin, Senju Yang; Yin–Yang unlocks
  through the nature tree like any combination. Bloodline generation only uses elemental mixes.
- **Jutsu power** = affinity × water bonus × reserves (+2%/point above 10) × mastery (+15%/level)
  × states; charging costs up to 75% more chakra. Stat unlocks: `"unlock": {"stat": "reserves", "value": 20}`.
- **Canon techniques** (`CanonTechniques`): dome, heal, rasengan, shadow_bind, shadow_clone.
  Modes (`ModeSystem`): toggle jutsu with a chakra drain and multipliers.
