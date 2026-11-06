# Ninja weapons

_The first set of ninja tools (weapons workstream, 2026-10-14). Numbers live in code:
`weapon/ThrowStats` (throws), `weapon/NinjaWeapons` (melee), and the move JSONs under
`data/noirm/combat/moves/{kunai,staff,sword}/`._

## What the series uses, and what we picked

Ninja in the series carry a pouch of cheap metal tools and one or two real weapons:

- **Kunai**: a leaf-shaped dagger with a ring on the end. Everyone carries several; it's a
  knife, a thrown blade and a climbing tool. → **in**, as both a melee weapon and a throwable.
- **Shuriken**: small throwing stars, thrown in volleys. → **in**, with a fan-of-three throw.
- **Fūma shuriken**: a huge folding four-bladed star, thrown spinning (Sasuke vs Zabuza). → **in**,
  slow and heavy, cuts through several enemies.
- **Senbon**: thin needles aimed at pressure points (Haku). Low damage, they numb. → **in**.
- **Explosive tag** (paper bomb): a paper seal that explodes. Stuck on walls as traps or tied to a
  kunai. → **in**: slap it on a block, or craft it onto a kunai.
- **Bō staff / rod**: a long staff (the Monkey King's Adamantine Staff is the famous one). → **in**,
  with its own moveset: reach, wide sweeps, knockback.
- **Ninjatō**: the straight, short ninja sword (ANBU carry one on the back). → **in**, plus all
  vanilla swords use the same sword moveset.
- **Chakra blades**: Asuma's trench knives that carry his wind chakra. → **in**: they carry the
  wielder's nature.
- **Kusarigama** (sickle on a chain) and a **war fan (gunbai)**: → **in** as special weapons (below).
- **The Seven Swordsmen's swords** and other famous blades: → **in** as four legendary blades of
  our own design and names (below), inspired by the canon tools, never copied.
- Not yet: wire strings, smoke bombs, flying thunder god kunai. Good next candidates (smoke bomb and
  wire traps fit the explosive tag code).

## The weapons

Damage is in half-hearts (vanilla numbers: an iron sword does 6). Thrown damage grows a little
with the thrower's strength: ×(1 + 0.3 × (power − 1)), where power is 1 at speed/taijutsu 10.

| Weapon | Use | Damage | Speed / cooldown | Other | Recipe |
|---|---|---|---|---|---|
| **Kunai** (stack 16) | melee + throw | melee 4, thrown 5 | 2 swings/s; throw every 0.5 s | kunai moveset; sticks in blocks, pick it back up | iron ingot over string → 4 |
| **Shuriken** (stack 32) | throw | 3 | every 0.3 s | spins flat, flies fast and flat; **sneak = fan of 3** (double cooldown) | 4 nuggets around an ingot → 8 |
| **Fūma shuriken** (1) | throw | 10 | every 1.5 s | huge, spins flat, barely drops, **cuts through 3 enemies** and pushes them | 4 ingots around a stick |
| **Senbon** (stack 64) | throw | 1.5 | every 0.2 s | fastest; **numbs: Slowness II 3 s**; stays in the body | 2 iron nuggets → 8 |
| **Explosive tag** (stack 16) | use on a block | blast 2.2 (TNT is 4) | — | burns 3 s, then explodes | paper + gunpowder + ink sac → 2 |
| **Explosive kunai** (stack 16) | throw | 3 + blast | every 1 s | blows up 1.5 s after sticking (0.6 s after hitting someone) | kunai + explosive tag |
| **Bō staff** (300 uses) | melee | 5 | 1.4 swings/s | **+1 block reach**; staff moveset | ingot cap + 2 sticks, diagonal |
| **Ninjatō** (350 uses) | melee | 6 | 1.8 swings/s | sword moveset | 2 ingots + stick, diagonal |
| **Chakra blade** (450 uses) | melee | 5 | 1.9 swings/s | kunai moveset; **your nature on every hit** (below) | ingots + lapis + stick |

**Chakra blades** use the wielder's strongest basic nature, for 1.5 chakra a hit (ninjas pay
nothing): wind ×1.35 damage, fire sets on fire 3 s, lightning stuns 6 ticks, water pushes,
earth +12 posture damage.

**Enchanting:** throwables stack, so they have no durability; a *single* one (not a stack)
takes Sharpness, Smite, Bane of Arthropods, Knockback and Fire Aspect (kunai also Looting),
and they apply when it's thrown too. The staff, ninjatō and chakra blades enchant like swords
(the staff not Sweeping Edge).

**Picking up:** a player's throws stick in blocks like arrows and are picked up by walking over
them (creative throws can't be picked up, like arrows). They vanish after 2 minutes on the ground.
Explosives and ninjas' throws can't be picked up.

## Special weapons

_Code: `weapon/SpecialWeapons` (items and every number), `SpecialWeaponItem` (stats, moveset,
reach, the gunbai's guard), `SpecialWeaponEvents` (effects, server side, on every melee hit:
vanilla swings and combat moves alike)._

| Weapon | Inspired by | Moveset | Damage | Speed | Reach | Special | How to get |
|---|---|---|---|---|---|---|---|
| **Crescent Cleaver** | Kubikiribōchō | sword | 10 | 0.9 swings/s | +0.5 | **heals you 20% of the damage dealt** (max 3 a hit) and **mends 2 durability a hit** | legendary drop |
| **Sharkskin Greatsword** | Samehada | sword | 9 | 1.0 | +0.5 | **eats 8 chakra** from a player target and gives it to you; a ninja target loses 10 posture instead (a ninja wielder heals 1) | legendary drop |
| **Thunder Fangs** | Kiba | kunai | 7 | 1.8 | — | **lightning stuns 6 ticks** a hit (players pay 2 chakra; without it, a plain hit) | legendary drop |
| **Reedcutter** | Kusanagi | sword | 6 | 2.6 | +0.5 | the fastest blade there is | legendary drop |
| **Gale Gunbai** (war fan) | Madara's gunbai | staff | 5 | 1.2 | +0.5 | **hits blow the target back** (push 1.1, lift 0.25); **guards like a shield** (hold right click, or the combat guard) and **turns projectiles from the front back at the shooter** (arrows, thrown weapons, fireballs; not jutsu) | paper fan on an iron frame + stick |
| **Kusarigama** | — | staff | 5 | 1.4 | **+2** | **drags a target more than 2.5 blocks away towards you** (no knockback away) | 2 iron + stick + 2 chains |

Legendary blades are diamond-tough (1561 uses, repaired with diamonds), very enchantable (22),
fireproof and epic rarity. They have no recipe: **rogue swordsmen carry them**. A wandering
(non-village) ninja who would carry a ninjatō carries a random legendary blade instead 15% of the
time (about 1 rogue in 85), always dresses as a rogue (cloak), and drops it 30% of the time when
defeated. Any ninja may also carry a kusarigama (1.5%) or a gunbai (1%). Icons:
`tools/weapongen/gen_weapons.py`.

## More blades (v0.13)

_Code: `weapon/NinjaBlades` (the items), `NinjaBladeItem`, `BladeStats` (reads the numbers),
`BladeEvents` (traits, bleeding, chakra flow, the tessen's swat; trait numbers at the top).
**Damage, speed, reach, durability, moveset, trait and chakra flow are data:**
`data/noirm/weapons/stats.json` (read when the game starts: a change needs a restart). Moves:
`data/noirm/combat/moves/<moveset>/`. Both are written by `tools/weapongen/gen_moves.py`;
animations by `tools/weapongen/gen_blade_anims.py` (timed to the moves). Try them: `/noirm kit arsenal`._

Seven weapons, picked so each **feels** different, not just bigger numbers: a very fast one
(tantō), a strong clean one (katana), a slow crowd-clearer (nodachi), a two-armed blunt one
(tonfa), a long-reach one (yari), a bleeding one (tekko-kagi) and a defensive one (tessen). A kama
was left out because the kusarigama already hooks and drags; chakra knuckles were left out because
the chakra blades already are Asuma's trench knives.

| Weapon | Moveset | Damage | Speed | Reach | Durability | What makes it different | How to get |
|---|---|---|---|---|---|---|---|
| **Tantō** (ANBU short blade) | tantō | 4.5 | 2.4 swings/s | — | 300 | the fastest moveset (2-tick wind-ups); **a hit in the back does ×1.6** | iron + black wool; shop (4 emeralds); ANBU patrols (65%) |
| **Katana** | katana (two hands) | 7 | 1.5 | +0.5 | 600 | big clean cuts; heavy = **iaidō drawing cut** (35% through guard); **lightning flow** | iron ×2, leather, black wool; shop (9) |
| **Nodachi** (greatsword) | greatsword | 10 | 0.85 | +1 | 700 | 200° sweeps, a 360° spin, an overhead cleave that floors; **every hit shoves** | iron + iron block + stick + wool; rogues carry it (and look like rogues) |
| **Tonfa** (a pair) | tonfa (both arms) | 4 | 2.3 | — | 400 | alternating left/right blows; **+8 posture a blow** (guards break fast); blunt (no cutting gore) | sticks + iron; shop (4) |
| **Yari** (spear) | spear | 6 | 1.3 | +1.75 | 350 | thrusts reach **4.8–5.2 blocks**, narrow; heavy = wide shaft sweep | iron, string, stick (diagonal); shop (5) |
| **Tekko-kagi** (claws) | claws (both hands) | 4 | 2.2 | — | 350 | fast rakes; **bleeding: 1 damage a second for 3 s**; **wind flow** | 3 nuggets, iron, leather |
| **Tessen** (iron war fan) | fan | 4 | 2.0 | +0.25 | 400 | quick snaps, a spinning flourish, heavy = gust (knockback 1.6); **swung or in guard, it swats arrows and thrown weapons from the front out of the air**; blunt | iron + paper ×2 rows + stick; shop (5) |

**Chakra flow** (two blades): if the wielder has the nature (a player's clan or trained natures, a
ninja's clan), their chakra runs into the blade. It **glows** (a full-bright layer in the item
model, in hand and in the hotbar) and every hit costs a little chakra (ninjas pay nothing):

- **Katana + Lightning:** ×1.25 damage and a 5-tick stun, 2 chakra a hit (Sasuke-style).
- **Tekko-kagi + Wind:** ×1.3 damage and 2 s longer bleeding, 1.5 chakra a hit.

Without the nature, or out of chakra, they're plain steel. (The chakra blades still take
whatever nature you have; these two take one nature but hit harder with it.)

**More flows (APX follow-ups, 2026-10-28):** four more blades take a nature, each with its own effect
(numbers in `stats.json` under `flow`, effect numbers at the top of `BladeEvents`):

- **Nodachi + Fire:** ×1.15 damage and the target burns 4 s; enemies within 1.75 blocks of it catch
  for 2 s (never the wielder or their side). 2.5 chakra a hit.
- **Yari + Wind:** ×1.15 damage, **+1 block reach** while flowing, and a **wind lance** carries 2.5
  blocks past the target, cutting up to 2 enemies standing in line behind it for 60% of the hit.
  2 chakra a hit.
- **Tonfa + Earth:** ×1.15 damage, +8 more posture a blow and Slowness II for 1.5 s. 1.5 chakra a hit.
- **Tantō + Lightning** (the White Fang's glowing blade): ×1.1 damage and a stab **in the back
  paralyses** (14-tick stun). 1.5 chakra a hit.

Each glows with its own 64×64 layer (`nodachi_glow` flames, `yari_glow` wind, `tonfa_glow` glowing
cracks in stone, `tanto_glow` white light). Tooltips are per blade: `tooltip.noirm.blade.flow.<id>`.

**Long held models:** the yari (2.2 blocks), nodachi (1.55) and bō staff (1.75) are held at full
length in first and third person. `tools/weapongen/gen_held.py` draws a straight 16×128 "held"
texture for each (`textures/item/held/`, same shaders as the icons, plus a glow for the flows) and
writes the item models: Forge's `separate_transforms` uses the long texture in the hands and the
usual icon everywhere else (inventory, ground, frames). The hand transforms are computed so the grip
sits where vanilla holds a sword's handle and the weapon points the same way, so every sword
animation holds them right. Weapon trails match (spear 1.45, greatsword 1.25 blocks).

**Ninjas:** about 1 ninja in 10 now holds one of these (tantō 3%, katana 2%, yari 2%, tonfa 1.5%,
tekko-kagi 1%, tessen 0.8%; the nodachi 1.2%, wanderers only). They fight with the blade's
moveset like the player does. ANBU-style patrols usually carry a tantō.

## In combat mode

Holding a weapon in the main hand swaps your fighting style for the weapon's moveset (your own
style comes back when you put it away). The weapon's stance replaces the style's.

| Moveset | Weapons | Light route | Heavy | Launcher | Dash | Back |
|---|---|---|---|---|---|---|
| **Kunai** | kunai, chakra blades, Thunder Fangs | slash → backhand → slash → finishing stab | stab | rising cut | lunging stab | light: backhand; **heavy (S + right click): throw the kunai** |
| **Staff** | bō staff, Gale Gunbai, kusarigama | sweep → return sweep → spinning sweep (360°) | thrust (4.2 reach) → overhead smash (knockdown) | rising sweep | lunging thrust | heavy: spinning sweep |
| **Sword** | ninjatō, every vanilla sword, Crescent Cleaver, Sharkskin Greatsword, Reedcutter | slash → cross → finishing slash | thrust (gets 25% through guard) | rising cut | dash thrust | heavy: finishing slash |
| **Tantō** | tantō | slash → backhand → cut → spinning flurry | stab (30% through guard) | rising cut | shadow-step stab | light: backhand |
| **Katana** | katana | slash → cross cut → overhead finisher | iaidō drawing cut | rising cut | dash cut | heavy: overhead finisher |
| **Greatsword** | nodachi | sweep → return sweep → full spin | overhead cleave (knockdown) | rising sweep | charging cut | heavy: spin |
| **Tonfa** | tonfa | left → right → left hook → double hammer | spinning strike | uppercut | rush | light: left hook |
| **Spear** | yari | thrust → thrust → lunging thrust | shaft sweep (180°) | vault | charge | heavy: sweep |
| **Claws** | tekko-kagi | rake → back rake → rake → shred | maul | rising rake | pounce | light: back rake |
| **Fan** | tessen | snap → snap back → spinning flourish | gust | rising snap | glide | heavy: flourish |

Shuriken, senbon and the fūma always throw on right click, in combat mode too.

## Ninjas

- When a ninja first joins its clan: 22% hold a kunai, 8% a ninjatō, 6% a staff, 2% chakra
  blades; 40% keep throwables in the off hand (mostly shuriken or kunai, some senbon, rarely a
  fūma). Village guards and patrols always carry a kunai (or a ninjatō) and shuriken.
- They throw from 4.5 to 16 blocks while closing in (`entity/NinjaThrowGoal`), now and then
  (about every 2–3 s at most), sometimes a fan of shuriken. **Their supply never runs out**, and
  their thrown copies can't be picked up and vanish 5 s after landing. Crew mates you hand a
  kunai or shuriken (ally screen) do the same; crew mates never throw explosives, and no one's
  throws hit their own crew.
- Weapons drop rarely (6%). The weapon shop in villages sells kunai, shuriken, senbon, explosive
  tags, ninjatō and staffs.

## Explosions and the world

An explosive tag hurts and knocks back everything nearby (the thrower too), but never breaks
blocks the vanilla way. It digs a small crater through `Destruction.crater` only when the
`noirmTerrainDamage` game rule is on, and, for a tag thrown or placed by anyone but a player,
when `mobGriefing` is on too. Craters heal like jutsu craters (`noirmTerrainRegen`).

## Art

Every weapon texture is **HD (64×64, 4×)** in the ink style of `docs/ART_STYLE.md`, drawn by
`tools/weapongen/gen_weapons.py` (`--scale` sets the resolution; shapes are described in 16-unit
icon space): a thick ink silhouette that still reads at 16 px in the hotbar, carved two-tone
shading with light from the top left, forged steel with a bone-white edge line, a temper line on
the katana and nodachi, nicks bitten out of edges, scratches and grit, cord-wrapped grips
(diamond wraps with red or ivory showing through), spiral cloth and leather wraps, lacquer with a
hard shine, rope and tassels, blood red as the accent. `katana_glow` and `tekko_kagi_glow` are the
chakra-flow layers. Long weapons lie on the diagonal, tip to the top right, like a vanilla sword;
thrown weapons are drawn with the same icons (`client/ThrownWeaponRenderer`): kunai and senbon
point along their flight, stars spin flat. Throw animations: `noirm:throw_kunai`,
`noirm:throw_shuriken` (also senbon), `noirm:throw_fuma`; movesets use `noirm:<moveset>_*` and the
stances `noirm:stance_<moveset>`.
