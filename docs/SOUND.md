# NOIRM sound

Every NOIRM sound is **our own, made from scratch by a script**: noise, sine waves, envelopes,
filters and a synthetic room, mixed in Python. No recorded audio is copied, sampled or downloaded
from anywhere (no franchise sounds, no other mods, no sound libraries). That keeps the mod free of
licensing problems and lets us retune any sound by changing a number.

The mood follows `docs/ART_STYLE.md`: raw, tough, ink, high contrast. Hits are hard and short with a
solid low end, rooms are small and dark, the UI is barely there. Nothing chiptune.

Listen to a few right away: `docs/sound-samples/` has one `.ogg` per category (open them with any
media player).

## How the sounds are made

`tools/soundgen/` holds three files:

| File | What it does |
|---|---|
| `dsp.py` | The building blocks: white/pink/brown noise, sine, saw, square, FM, bubbles, "struck metal" (decaying partials), envelopes, FFT filters (low/high/band-pass and swept filters for whooshes), a one-pole IIR filter and a resonant biquad, saturation, a synthetic reverb (convolution with decaying noise). |
| `recipes.py` | One small function per sound. Each says in plain words what it should sound like, then builds it in layers. At the bottom, `SOUNDS` lists every sound: its recipe, how many variants, its loudness (peak in dB) and whether it loops. |
| `gen_sounds.py` | Runs every recipe, cleans the result (no DC offset, fades, normalised, never clipping after Vorbis), encodes mono 44.1 kHz Ogg Vorbis with ffmpeg, and writes `assets/noirm/sounds.json` (files, subtitles, how far each carries). |

Each sound has **2 to 4 variants** (different pitch, timing and random detail), and Minecraft picks
one at random, so a combo never sounds like a machine gun. The game also nudges the pitch a little on
every hit.

### Regenerate or tweak a sound

```
python3 tools/soundgen/gen_sounds.py                        # everything (about a minute)
python3 tools/soundgen/gen_sounds.py --only combat.parry    # just one sound, all its variants
python3 tools/soundgen/gen_sounds.py --report /tmp/sndcheck # + stats.json and a spectrogram per file
python3 tools/soundgen/gen_sounds.py --samples              # + refresh docs/sound-samples/
```

To change how something sounds, open `recipes.py`, find its function (e.g. `parry`) and change a
number: a frequency (`f0 = 520` → lower is heavier), a decay time (how long it rings), a layer's
gain. Then run it with `--only`. Louder or quieter overall: change the peak in the `SOUNDS` table.
More variants: raise the count there (the game test checks the files exist). Rebuild the mod, or in
game press F3+T to reload resources.

Since we can't trust our ears alone, `--report` checks each file numerically (length, peak, RMS,
DC offset, spectral centroid, clipping, how cleanly a loop joins) and draws a spectrogram PNG per
file plus contact sheets, so you can see a sound's shape.

### Size

About 3.3 MB for all 54 sounds (152 files), well under the 8 MB budget. Vorbis quality 4, mono.

## Volume, distance and sliders

- **Sliders** (vanilla's Music & Sounds screen): jutsu, combat and weapons follow **Players** (or
  **Hostile Creatures** when an NPC fights), the UI follows **Master** like vanilla buttons, the
  village and sanctuary ambience follows **Ambient/Environment**. There's no separate NOIRM slider
  (that would need a new sound category, which Forge doesn't allow cleanly); turn Players down to
  quiet the fighting.
- **Loudness:** hits and impacts peak around -1 dB like vanilla's own hits; UI sounds sit 12 to
  20 dB lower; ambience is quiet and fades in and out.
- **Distance:** set per sound in `sounds.json` (`attenuation_distance`): jutsu 24 blocks, explosions
  32, S-rank moments 40 (vanilla's default is 16). Big jutsu also play at volume above 1, which
  carries them further still.
- **Gore:** there are no gore sounds; the gore setting is untouched.

## The sounds and where they play

| Sound id | What it sounds like / how it's made | Where it plays |
|---|---|---|
| `seal.clap` | A soft cloth-and-skin clap: a filtered noise slap, a tiny hollow thump, a rustle before | Once per hand seal while weaving (`JutsuSounds`, from `JutsuCaster`) |
| `seal.complete` | Chakra rushing up: a swept-noise whoosh over a rising low pulse | When the seals lock, just before a jutsu fires |
| `chakra.charge` | A low beating hum with breathing air (seamless 3 s loop) | Held under charging jutsu; stops when the charge ends |
| `chakra.release` | A punchy low thump and a blast of air sweeping down | Jutsu with no nature (`NoirmSounds.forNature(null, …)`) |
| `combat.hit_light` / `hit_heavy` | Thump falling in pitch, cloth slap, crunchy mid, hard click; heavy is deeper with a short dark room | Every landed blow (`CombatEffects`), Hyūga and taijutsu strikes |
| `combat.guard` | A dull forearm knock and leather | Blocked blows, shields, tessen deflects |
| `combat.parry` | A struck metal bar: inharmonic partials ringing out | Parries |
| `combat.just_frame` | A bright FM ring over the parry | Just-frame parries, perfect dodges |
| `combat.posture_break` | Heavy crunch, splintering cracks, a falling metallic groan | Guard broken |
| `combat.swing_light` / `swing_heavy` | Noise through a band-pass sweeping up and down | Every swing, dodges, grabs, jutsu sweeps |
| `combat.body_thud` | Low thud, cloth and a scatter of dirt | Knockdowns, Brawler hits |
| `combat.wall_splat` | Big impact, crumbling stone, a short boom | Wall splats |
| `weapon.throw` | Thin fast whoosh with a faint spinning whistle | Kunai, shuriken and every thrown weapon |
| `weapon.impact` | Sharp tick, woody thunk, tiny metal shiver | Thrown weapons hitting, blade-style hits |
| `weapon.clash` | Bright metal strike, scrape and long ring | Thrown weapons blocked, blades deflecting projectiles |
| `nature.fire_roar` / `fire_burst` / `fire_crackle` | Breathing brown-noise roar with crackle; whoomph and darkening blast; pops and snaps | Fire cast / impact; burning oil areas |
| `nature.water_surge` / `water_splash` | Rushing filtered noise with churning bubbles (pitch-rising sine "Minnaert" bubbles); slap, spray, plop | Water cast / impact |
| `nature.lightning_crackle` | Harsh buzz full of spitting sparks (seamless 2 s loop) | Held under Chidori and lightning charges |
| `nature.lightning_snap` | Whip-crack, falling zap, short roll | Lightning cast and impact, lightning rain, Lightning Armor |
| `nature.wind_gust` / `wind_whistle` | Swelling rush with a hollow whistle; a narrow shrieking whistle diving in pitch | Wind impact / cast, wind techniques |
| `nature.earth_rumble` / `earth_crack` | Deep shaking rumble with grinding stones; clustered rock cracks and a heavy thump | Earth cast / impact, walls, pillars |
| `nature.ice_shatter` | Glassy crack and a spray of tiny ringing shards | Ice Release |
| `nature.lava_hiss` | Thick hiss with gloopy low bubbles | Lava Release |
| `nature.boil_steam` | Pressure building into a scalding jet over bubbles | Boil Release; fire doused by water |
| `nature.storm_hum` | Pulsing electric drone with a high whine (seamless loop) | Storm Release cast; held under storm channels |
| `nature.explosion_boom` | Sub boom, blast front, debris, rolling tail | Explosion Release, explosive impacts, jutsu clashes |
| `nature.scorch_sizzle` | Angry frying sizzle with spits | Scorch Release |
| `nature.dust_hum` | Pure eerie chord rising, matter crumbling to powder | Dust Release |
| `nature.magnet_hiss` | Dense metallic pour of tiny grains | Magnet Release (iron sand) |
| `nature.wood_creak` / `wood_burst` | Stick-slip friction through wood resonances; splinters over a thump | Wood Release, wood clones, Substitution's log |
| `nature.yin_swell` | A low cluster in a big room, played backwards: sucked into silence | Yin jutsu |
| `nature.yang_swell` | A warm major chord blooming | Yang and Yin-Yang jutsu, Sage Mode |
| `nature.medical_chime` | A soft FM bell with a halo | Medical ninjutsu, healing, Gentle Fist hits (quietly) |
| `big.impact` | Colossal boom with a long sub tail | S-rank impacts: Great techniques, storm and lava finales, the Gates, wood colossus |
| `big.genjutsu` | Warped wavering tones, ring-modulated, with a reversed tail | Genjutsu (Sharingan, Mangekyō, spirit techniques) |
| `big.cloak` | A rising roaring surge that bursts into being | Susanoo forming, the beast cloak wrapping you (first tail), the beast taking over |
| `big.sharingan` | A sharp piercing metallic ring and a "shing" | Sharingan opening, growing a tomoe, awakening, Mangekyō |
| `ui.open` / `ui.close` / `ui.tab` | A soft paper slide up / down / a short slide and tick | Every screen through the UI kit (`ScreenMotion`) |
| `ui.hover` / `ui.click` | The tiniest wooden tick / a small firm wooden knock | Hovering (`HoverTracker`) / pressing buttons (`UiSounds.click`) |
| `ui.rank_up` | Two taiko hits and a rising chord crowned by a bell | Promotions (exams), unlocking a nature |
| `ui.mission_complete` | One taiko, a calm two-note resolution, a chime | Turning in a mission |
| `ambient.village` | Soft wind, leaves, distant birds, a hammer, a wind chime (24 s loop) | Inside any village (`AmbienceLoops`, client only) |
| `ambient.sanctuary` | A deep still drone, falling water, stone chimes (20 s loop) | In the sage sanctuary or near the Stone Hermit |

The one place that chooses a nature's sound is `NoirmSounds.forNature(nature, kind)` (kind = cast,
impact or held). Jutsu behaviours call it through `JutsuBehaviors.castSound` / `impactSound`, and
projectiles use it when they land. A few vanilla sounds stay where they already fit (heartbeats,
beast roars, armor rustle, book pages, the raid horn), and so do the vanilla sounds outside fights
(crew, shops, missions' small cues).

## Tests

`SoundGameTests`: every sound is registered and listed in `sounds.json` with all its files and a
subtitle in en_us, es_es and es_mx; every nature has our sound for every kind; casting Phoenix Flower
plays six seal claps, the seal lock and the fire roar, and nothing vanilla.

## Checking in the real client

There's no speaker in the test machine, but OpenAL has a silent "No Output" device:
`ALSOFT_DRIVERS=null SMOKE_SUBTITLES=true tools/client-smoke/run.sh` starts the sound engine on it
with subtitles on (see `tools/client-smoke/README.md`). Screenshots: `docs/screenshots/sv-sound-*.jpg`
(Great Fireball: "Hands form a seal", "Chakra gathers", "Flames roar", "Fire bursts"; punches:
"Strike whooshes", "Heavy swing"; the Sharingan: "Sharingan awakens"; the sanctuary loop shows as a
stream on the F3 screen). The log has no missing-sound warnings for NOIRM.
