# The Flying Raijin (Hiraishin no Jutsu)

The owner: "I LOVE THIS JUTSU, so take special attention… a lot of games and mods don't get the sense
of it and I want us to be the exception." This page is the research behind our version, then how ours
works. Everything is our own design and art (no franchise or mod assets).

## 1. What it is in canon (research, in our own words)

**Who.** A space-time ninjutsu created by the Second Hokage, Tobirama Senju, and made famous by the
Fourth, Minato Namikaze (the "Yellow Flash"). Later users include Minato's student line and, in a
cruder form, a few others; it's one of the rarest techniques in the series.

**How it works.**
- **The formula (jutsu-shiki).** The user writes a space-time seal formula onto something. That formula
  is an anchor: the user can, at will, travel to it.
- **Marks on anything.** On Minato's special **three-pronged kunai** (each handle wrapped with the
  formula), on surfaces (walls, the ground, a tree), and on **people**: a light touch is enough, so a
  struck enemy is a marked enemy. He also put his formula on allies (his wife's seal, a comrade's
  shoulder) so he could come to their side.
- **No travel time.** It isn't speed: the user is *at the mark*. There is no path, no dash, nothing to
  intercept. The marks persist until removed, and the user can keep many of them at once.
- **The look.** A sharp **yellow flash** (hence the epithet), sometimes a faint afterimage where he was,
  and the user simply standing at the mark, often already mid-strike. Stills often show the kunai first,
  then Minato holding it.
- **Bringing others.** Whatever the user touches can come along: allies carried out of danger, an enemy
  dragged into a trap, even a huge attack touched and sent to a far mark (Minato sent a tailed beast's
  bomb away this way). The **barrier variant** (with a team of four) teleports an attack or a large
  object; Minato's own refinement, **"Level 2"**, teleports an incoming attack *at* a mark on the enemy.
- **The Flying Thunder God Slash.** Throw a marked kunai past or at the enemy, then appear at the
  kunai while it's still in the air, behind or beside them, striking before they've turned. Tobirama's
  version: appear at the enemy's side and cut at once.
- **Chains.** With many kunai scattered round an enemy, the user flickers between them faster than the
  enemy can follow, striking from every side: the "unbeatable" feel of the Yellow Flash.
- **Counterplay in canon.** Someone with extraordinary reaction speed or a Sharingan can read the
  flash and prepare (the Fourth Raikage reacting on raw speed; Obito's Sharingan and phasing). Kamui
  and sealing the user's chakra beat it. The mark has to be placed first: no mark, no jump.

## 2. What games and mods get wrong

- **It's a slow dash.** Many versions animate the user travelling at high speed along a line. That's
  the Body Flicker, not the Flying Raijin. There must be no travel.
- **No mark system.** It's often a teleport to where you look, or to the enemy. The heart of the jutsu is
  *preparation*: where you put your marks decides the fight.
- **No yellow flash**, or a generic purple portal / particle cloud.
- **No strategy.** Marks you can't see, can't count, can't recall, can't place on allies or bodies.
- **Weak.** A long cooldown and a big cost make it a gimmick; or it's a cinematic super move with no
  control. The real feel is a fast, cheap, repeatable tool in a master's hands.

## 3. How ours works

### Learning it (a long road)
1. **Space-Time Formula** (`noirm:space_time/formula_mark`, B rank): read a **Space-Time Formula Scroll**
   (crafted: paper, gold, ink sacs and an eye of ender) with **CTL 30** and **Reserves 25**. You can now
   write marks, but not travel to them. Every *new* mark is practice for the formula.
2. **Flying Raijin** (`noirm:space_time/flying_raijin`, S rank): it comes by itself once the formula
   reaches **★3** (about 60 marks written), with **CTL 45** and **Reserves 40**. A title and a flash
   tell you.
3. **Mastery** (★0–★5, from jumps): the wait between jumps falls 15% per ★; the one seal is dropped from
   ★3; allies touching you come along from ★2 (1 at ★2, 2 at ★3, 4 at ★4+); from ★4 the **chain**: a
   slash resets the wait to 3 ticks; +2 marks at ★4.
4. **Clans:** `HiraishinAffinity.register("<clan id>", new Bonus(statDiscount, extraMarks, cost×, cooldown×))`
   is the hook for an Uzumaki/Namikaze-like sealing clan (nothing registered yet: the clan system decides).

### The marked kunai
`noirm:hiraishin_kunai`: our own three-pronged design (`tools/weapongen/gen_weapons.py`), a long centre
blade, two swept prongs and a grip wrapped in formula paper. Crafted from a kunai, 2 iron nuggets and
paper. A dagger in melee like the kunai. Thrown by someone who knows the formula, it carries their mark
for as long as it exists (in flight, stuck in a wall or lying on the ground). Hitting someone marks them.

### Marks
| Placed by | Kind | Lasts |
|---|---|---|
| Throwing a marked kunai | kunai | as long as the kunai |
| A landed melee blow (you know the Flying Raijin, or hold a marked kunai), or a marked kunai's hit | body | 60 s |
| The mark key (**Y**) or the formula jutsu, on the block you look at (6 blocks) | seal | 10 min, +50% per ★ of the formula |
| The mark key on an ally within touch (3 blocks) | ally | 5 min |

At most **8** marks (+2 at ★4, + a clan bonus); the oldest burns out first. A kunai picked up takes its
mark with it; a body that dies loses it. Marks are saved with the world. **Sneak + Y** recalls every
thrown marked kunai to you. **U** picks the next mark.

You (and only you) see your marks as faint gold **seal glyphs, through walls**, out to 64 blocks; the one
you're aiming at burns bright yellow and turns. The HUD (bottom right) shows marks held and the cap, and
your three nearest marks with their distance (▶ marks the aimed one).

### The jump (X, or the jutsu in a loadout slot)
- **Which mark:** the one nearest your crosshair (within 22°, up to 256 blocks), else the selected one.
- **Instant:** you're there the same tick. Where you land: standing on a seal written on the ground,
  in front of one on a wall, at a kunai holding it, behind a marked enemy, beside a marked ally. A safe
  spot is found so you're never inside a block.
- **Momentum:** reset at standing marks; landing on a kunai still in flight passes on 30% of its speed
  (and you catch it). No fall damage for 2 s after a jump.
- **Look:** a sharp yellow-white flash at both ends, an **afterimage** of you lit gold left standing where
  you were for half a second, one streak of light between for 4 ticks, the seal flaring on the ground
  where you land, a white-gold flash on your own screen and a tiny punch of the field of view. No travel.
- **Sound:** `space.hiraishin`, our own: a razor transient, a bright metal glint gliding down and a tiny
  air snap, gone in a blink (`tools/soundgen/recipes.py: hiraishin_flash`).
- **Cost:** 18 chakra a jump (×0.6 within 1.2 s of the last one: the flow of a chain); wait 30 ticks at
  ★0, 8 at ★5. Below ★3 one quick seal (4 ticks); from ★3 none.

### Combat tricks
- **Flying Thunder God Slash:** jump to a kunai (or any mark) within 4 blocks of an enemy and you land
  behind them, facing their back (a kunai further out than 1.3 blocks puts you on *its* side of them,
  so a ring of kunai means blows from every side). Your next blow within 1 s is a **counter** (can't be
  guarded, ×1.5, the dodge-counter system) and hits **×1.3** more.
- **The chain (★4+):** each slash resets the wait to 3 ticks: throw, jump, cut, jump, cut.
- **Bring someone:** an enemy you're grabbing always comes with you (into your trap); allies touching
  you come from ★2.
- **Guiding Thunder (redirect):** press the jump with an enemy projectile about to hit you (within 7
  blocks, arriving within 0.7 s) and the *projectile* goes to your aimed mark instead, now yours; a mark
  on an enemy sends it straight at them.
- **Recall:** sneak + Y brings every thrown marked kunai back.

### Counterplay
An enemy with an **open Sharingan**, or **perception ×1.3** yours, reads the flash: they see the seal
flare where you'll land and an arrow at the edge of their screen (an NPC turns and guards), and the
slash's free counter doesn't open. Byakugan chakra-point sealing blocks the jutsu like any other.

### NPCs
The lightning legends of the bingo book (`Legendary.Kind.LIGHTNING`) carry it (`HiraishinNpc`). At the
start of a fight they write a mark where they stand; then a marked kunai is thrown past you and they're
behind you before it lands, already swinging, and at full strength they chain it; every blow they land
marks you, so running only puts them at your back again; projectiles are sent elsewhere; hurt and
pressed, they vanish to their mark. `/noirm hiraishin legend` summons one to try.

### Commands and keys
Keys (Controls → NOIRM): **Y** mark (sneak: recall), **X** jump, **U** next mark. Commands:
`/noirm hiraishin` (your progress), `marks`, `clear`, `give` (cheats: learn both, 16 kunai), `legend` (cheats).

### Code
`hiraishin/`: `HiraishinMark(s)` (the saved marks), `HiraishinMarking` (placing, recall, syncing),
`HiraishinJump` (the jump, slash, carry, redirect, counterplay), `HiraishinLearning`, `HiraishinAffinity`,
`HiraishinItems`, `HiraishinNpc`, `HiraishinFx`, `HiraishinPackets`, `SpaceTimeInput`. Client:
`HiraishinClient` (keys, glyphs, HUD), `SpaceTimeFx` (afterimages, flashes, streaks), `SpaceTimeDraw`.
Textures: `tools/vfxgen/gen_spacetime.py` (the formula glyph, glows). Hooks in shared files: a cost and
cooldown line in `JutsuCaster`, the behaviours in `JutsuBehaviors`, `NpcJutsuSets.NOT_FOR_NPCS`,
`Legendary.apply`. Network protocol 28.

Tests: `gametest/SpaceTimeGameTests` (marks by throw, hit, key and on an ally; the instant jump to the
aimed mark; the cap and decay; the slash's backstab and a seer reading it; carrying an ally; redirecting
an arrow; chakra, cooldown and mastery through the real cast; a legend's throw-and-slash).

Screenshots (client smoke test, first person unless noted): `docs/screenshots/gr-hiraishin-marks-through-wall.jpg`
(four seals behind a wall, the aimed one bright), `gr-hiraishin-slash-strip.jpg` (throw, the enemy marked,
the flash, behind them), `gr-hiraishin-flash-strip.jpg` (the white-gold flash, then the gold afterimage left
where you were), `gr-hiraishin-chain-strip.jpg` (seals round an enemy: strike, strike, then an Uchiha who
reads it), `gr-hiraishin-read-strip.jpg` (a Sharingan user turning to meet the jump).
