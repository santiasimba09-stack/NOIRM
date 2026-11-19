# Genjutsu scenes and the Body Flicker (owner's brief, 2026-11-18)

The owner: "make the genjutsu moves feel like a genjutsu: make a scene in the affected player or mob's
mind, like a real genjutsu… make Body Flicker much better and more accurate."

## Genjutsu: a scene in the victim's mind

Being caught used to be a violet tint and ink at the edges. Now the victim's own client draws a
**scene** around them, and nothing in the real world changes (no blocks are touched; only the victim
sees it).

### What you see and hear
- **The world fades:** the fog closes in to a few blocks in the scene's colour, and a false sky (a dome
  from horizon to zenith, maybe a moon) replaces the real one.
- **The ground** becomes ink, still water or blood, rippling out from your feet.
- **Figures:** crows wheeling and swooping past your face, copies of the caster standing round you, ink
  phantoms closing in, your own crew turned to shadows, a tree growing round your body, ink hands rising
  to bind you, bare posts, a huge eye opening in the sky.
- **Sound:** the real world goes dull and slow (quieter, lower); a drone, your heartbeat, wordless
  whispers, crows (all our own, `tools/soundgen`: `genjutsu.*`).
- **Your HUD stutters.** The Tsukuyomi turns the world into a red-and-black negative and shows a clock:
  a few seconds outside, days inside.
- **Breaking out** shatters it: the last frame breaks into glass shards that fall away, with a crack of
  light and the `genjutsu.shatter` sound.

### The scenes (`data/noirm/genjutsu/*.json`)
| Scene | Jutsu | What it is |
|---|---|---|
| `demonic_illusion` | Demonic Illusion: Hell Viewing | Binding: a violet-black void, an ink floor, eight ink hands rising round you, your enemy's ink phantom walking in. |
| `false_surroundings` | False Surroundings | Wrong positions: the real world almost unchanged, but the caster is hidden and you see them standing elsewhere (where your attacks will go), with two more phantoms circling. |
| `tree_binding_death` | Tree Binding Death | A tree grows round you from behind, branches spiralling round your body; a dark green dusk; the phantom rises. |
| `sharingan_crows` | Sharingan Genjutsu | A blood-red sky, a black mirror of water with red ripples, a storm of crows swooping at your face, three copies of the caster round you, an eye opening in the sky. |
| `tsukuyomi` | Tsukuyomi | The world in negative washed red and black, a striped moon, bare wooden posts in a ring (no one on them), five copies of the caster circling, time slowed to a quarter, and the clock: "Inside: 71h 40m / Outside: 3.2 s". |
| `kotoamatsukami` | Kotoamatsukami (on a player) | Subtle: the world looks normal, but your allies look like dark red enemies. |

### A scene file
`jutsu` (which jutsu open it), `duration`, `sky` {`top`, `horizon`, `fog` (`"none"` keeps the real one),
`fog_start`, `fog_end`, `moon`, `moon_size`, `stripes`}, `ground` {`kind` ink/water/blood/none, `color`,
`rim`, `radius`}, `tint` + `tint_alpha`, `figures` [{`type` crow/caster/phantom/crew/tree/tendril/post/eye,
`count`, `radius`, `height`, `motion` circle/swarm/close_in/stand, `color`}], `sounds` {`drone`,
`heartbeat`, `whispers`, `crows` (periods in ticks), `muffle`, `pitch`}, `hud_flicker`, `time_scale`,
`post` (`invert_red` for the negative), `mob`, `swap_allies`, `hide_caster`, `shatter` (glass colour).
The server sends the whole definition to the victim (packet `GenjutsuPackets.Scene`), so a data pack can
add or change scenes. `/noirm genjutsu test <scene> [targets]` tries one.

How it's drawn (client, `client/GenjutsuSceneRenderer`): fog and fog colour overrides, a vertex-coloured
dome after the sky, a rippling disc for the ground, billboards for crows and the eye, square tubes for
the tree, hands and posts, real entity models for copies and flat-colour silhouettes for phantoms
(`SpaceTimeDraw`), GUI-blend tricks for the tint and the red negative (no shader files needed), and a
framebuffer copy cut into falling shards for the shatter. It's all a few thousand vertices a frame.

### Creatures and NPCs caught
They visibly act on what they see (`GenjutsuMinds`), and a faint violet swirl turns over their head so
the caster knows it worked:
- `freeze` (Hell Viewing, the Tree, the Tsukuyomi): rooted, trembling, looking wildly about;
- `wander` (the crows): walking off in random directions, looking for a way out;
- `attack_phantoms` (False Surroundings): swinging at things that aren't there, or at the false image;
- `turn_on_allies` (Kotoamatsukami): turning on its own side.

### Counterplay (unchanged, and it all breaks the scene)
Kai (even stunned; on yourself or a crew mate within 5 blocks), a friend's slap or use, an NPC crew mate
beside you, a strong mind (it can also **tear free on its own**: every second, 3% per 10% your mind is
above the caster's), an open Sharingan (sees through the Yin genjutsu; the Sharingan's own genjutsu is
eye against eye, a contest of minds).

## The Body Flicker, done right

Canon: the Body Flicker (Shunshin) is a burst of speed too fast to see, a blur and often a puff of smoke
or a swirl of leaves, and the user is simply at the destination; used to close in, escape and reposition.
Good game dashes have a clear start and end frame, never rubber-band, and help you aim at a locked-on
target. Ours (`jutsu/BodyFlicker`):

- **Where:** locked on (or an NPC's target) within reach: **behind** them (sneaking: **beside** them).
  Otherwise the point your crosshair rests on: a wall (you stop in front of it), a roof, the ground, or
  the air at full range. Range 8 blocks, +5% per point of strength above 10 (at most +5).
- **Walls stop you:** the body is swept along the path in quarter-block steps and halts at the first
  obstacle; a one-block step is taken in stride. Never inside a block.
- **Server-authoritative, no rubber-band:** the server moves you (an authoritative teleport the client
  acknowledges, so no snap-back), and the client only draws it.
- **Look:** a puff of grey smoke and a spin of leaves at both ends, a pale afterimage where you were and
  a stretched trail of three fading copies along the path, two thin blur streaks; on your screen the field
  of view kicks wide and speed lines rush in from the edges. Sound: `space.flicker`, a hard snap of air.
- **Rules:** 10 chakra, 14 ticks' wait, and at most **3 in 3 seconds**, each after the first costing +50%
  more; the fourth winds you (nothing spent).
- **Flicker-strike:** landing by an enemy opens the dodge-counter window: your next blow comes from behind
  them, can't be guarded and hits ×1.5.
- **NPCs** flicker in behind you (their target is their lock-on) and away when retreating.

Screenshots (client smoke test, the victim's view): `docs/screenshots/gr-genjutsu-demonic-illusion.jpg`,
`gr-genjutsu-false-surroundings.jpg`, `gr-genjutsu-tree-binding.jpg`, `gr-genjutsu-sharingan-crows.jpg`,
`gr-genjutsu-tsukuyomi.jpg`, `gr-genjutsu-kotoamatsukami.jpg` (an ally as an enemy shadow),
`gr-genjutsu-shatter-strip.jpg` (Kai: the scene breaks into falling glass), `gr-genjutsu-mob-caught.jpg`
(a husk frozen in Hell Viewing, the swirl over its head), `gr-flicker-strip.jpg` (Body Flicker: locked on,
the puff of smoke and leaves, behind the target).

Tests: `gametest/SpaceTimeGameTests` (scenes load, a caught zombie wanders and another swings at
phantoms, Kai shatters a scene, flicker range, wall-stop, behind-target with the counter window and
the chain limit).
