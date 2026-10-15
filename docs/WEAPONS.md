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
- Not yet: wire strings, smoke bombs, kusarigama, the Seven Swordsmen's swords, flying thunder god
  kunai. Good next candidates (smoke bomb and wire traps fit the explosive tag code).

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

## In combat mode

Holding a weapon in the main hand swaps your fighting style for the weapon's moveset (your own
style comes back when you put it away). The weapon's stance replaces the style's.

| Moveset | Weapons | Light route | Heavy | Launcher | Dash | Back |
|---|---|---|---|---|---|---|
| **Kunai** | kunai, chakra blades | slash → backhand → slash → finishing stab | stab | rising cut | lunging stab | light: backhand; **heavy (S + right click): throw the kunai** |
| **Staff** | bō staff | sweep → return sweep → spinning sweep (360°) | thrust (4.2 reach) → overhead smash (knockdown) | rising sweep | lunging thrust | heavy: spinning sweep |
| **Sword** | ninjatō, every vanilla sword | slash → cross → finishing slash | thrust (gets 25% through guard) | rising cut | dash thrust | heavy: finishing slash |

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

No artist yet: the 16×16 icons are drawn by `tools/weapongen/gen_weapons.py` (original pixel
art, vanilla-like palette), long weapons along the diagonal like a vanilla sword. Thrown weapons
are drawn with the same icons (`client/ThrownWeaponRenderer`): kunai and senbon point along their
flight, stars spin flat. Throw animations: `noirm:throw_kunai`, `noirm:throw_shuriken` (also
senbon), `noirm:throw_fuma`; movesets use `noirm:kunai_*`, `noirm:staff_*`, `noirm:sword_*` and
the stances `noirm:stance_kunai|staff|sword`.
