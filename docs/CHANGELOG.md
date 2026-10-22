# Changelog

Newest first. One entry per working session or PR.

## 2026-10-22 — Eyes with soul, black boxers, see-through fire, v0.12.1 (branch `claude/keen-cray-uc2d11`, PR #1)

The owner's feedback on v0.12.0 ("everything is peak", except these):
- **Eyes** were "soulless and way too bubbly": now narrow and intense: a heavy slanted upper lid
  cutting across a smaller iris (dark ring, lighter lower half, small pupil, one-pixel highlight),
  white on the outer side, a low inner corner, brows closer with a shadow on the lid. Styles: Steady
  (default), Sharp, Fierce, Narrow, Pale. Sharingan/Byakugan overlays fitted per eye style (the tomoe
  now show), the Mangekyō sized to each iris.
- **Boxers**, bigger (hips to mid-thigh) and **black**, are the new default underwear.
- **Fire jutsu** no longer hide what's behind them: soft particles (fire blobs, tongues, smoke, ash)
  don't write depth any more (`VfxParticle.SOFT`).
- Tests: 267/267 pass.

## 2026-10-22 — Our own HD art, harsher gore, fire and lightning, v0.12.0 (branch `claude/keen-cray-uc2d11`, PR #1)

From the owner's art direction ("our own designs, more pixels, start in underwear", "raw and tough",
reference images; see `docs/ART_STYLE.md`) and gore feedback ("worse, with blood on the ground, on the
character, on clothing").

- **HD skins at 4×** (256×256 for the 64×64 layout), all drawn by our generators (`tools/skingen/hdskin.py`,
  `anatomy.py`): bodies lit as rounded forms with carved muscles, anime eyes (almond shape, bold lid,
  tall iris, one highlight), clean nose/mouth/jaw shapes, chunky hair locks, real underwear (briefs,
  shorts, fundoshi, chest bindings). NPC ninjas share the style. Sharingan/Byakugan/Mangekyō redrawn to fit.
  Skins are composed in game by `client/SkinComposer` (cached per look, uploaded once).
- **New characters start in just underwear**; the creator's Clothes tab is gone (clothes come from
  gear). Old looks with base clothes load in underwear.
- **HD gear at 4×** (all 54 worn textures, 256×128; `tools/gear/hd.py`): folds with dark creases and lit
  ridges, seams, stitching, trims, frayed hems, dirt toward the feet, scratched metal plates.
- **Gore v2:** blood on the body (bleeds, splatters, drips, dries in 45 s, washes off in water/rain), on
  clothing and armor, and **on the ground** (splats, drips, trails from the badly hurt, pools under
  bodies and limbs; client-side decals, max 256, 2–3 min). Heavy blunt kills sever 85%, blasts 90%;
  bigger bursts and stump spurts; limbs last 15 s. Client setting `goreLevel` (off/mild/full).
- **Fire and lightning** redone from the owner's panels: cellular fire blobs, flame tongues, ash, smoke,
  shockwaves and debris; branching, ground-running lightning with spark bursts; Lightning Armor with
  crawling bolts. **New jutsu:** Burning Ash, Majestic Destroyer Flame, Flame Bomb, Dragon Flame
  Caterwaul, Chain Discharge (lightning jumps between up to 4 enemies). A particle budget (4,000) and
  distance thinning.
- Network protocol 15. Tests: 267/267 pass twice in a row (16 new); the merged build checked in the real client.

## 2026-10-22 — Character creation, movement feel, gore, v0.11.0 (branch `claude/keen-cray-uc2d11`, PR #1)

Built from the owner's answers on the blueprint (character creation: yes; movement: the blueprint's,
but light; gore: yes; no melee while forming seals).

- **Character creation** (`creator/`): on first join the screen opens and you're held in place until
  you press Begin. A turnable, zoomable 3D view; tabs for face, body (skin, muscle, scars,
  weathering, markings, underwear and colours, height 0.85–1.15× with the hitbox following), hair,
  clothes, and genetics: a clan from the world's list with five rolled stats (8–12, traits add a
  little), one free roll plus 3 rerolls, keep any of them. Your look is built from generated parts
  (`tools/skingen/gen_player_parts.py`) and shows for everyone, first person too. `/noirm creator`
  (look only), `creator reroll|skip|height`, gamerule `noirmCharacterCreation`.
- **Movement feel** (`movement/MovementFeel`): a short slide to a stop (4 ticks), a 2-tick momentum
  pivot, gravity ×1.2 with jumps boosted to keep their height, 1–3 ticks of landing recovery after
  3+ block falls (a dodge skips it). Ninjas keep normal gravity. Gamerule `noirmMovementFeel`.
- **No melee while forming seals** (`combat/SealLock`), players and ninjas; a one-time hint.
- **Gore** (`gore/`): bruises, cuts, soot and dirt build up by damage type and fade; a killing blade,
  crit, blast or heavy blow can sever an arm, leg or head, which tumbles, then burns away in blue
  chakra flame after 5 s (12 at most, unseen beyond 32 m). Visual only. Gamerule `noirmGore`,
  `/noirm world gore on|off`.
- The roof-leap test clears its flight path first (it flew into blocks earlier tests left behind).
- Network protocol 14. Tests: 251/251 pass twice in a row (18 new: creator, movement, seal lock, gore); the merged build checked in the real client.

## 2026-10-21 — Crew overhaul, meditation and blueprint Phase 1, v0.10.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **The owner's blueprint** is summarised in `docs/BLUEPRINT.md`. Rule: keep what we have, build what
  fits, ask before rebuilding a system.
- **Crew overhaul:**
  - **Follow fixed:** after Train, the dummy stayed their target and they never followed. Now
    `CrewBrain` drops dummies outside Train and fights far behind you, repaths twice a second, and jumps
    to your side when stuck or far away.
  - **Orders:** Follow, Wait, Guard and Patrol (around a post where you stand), Train, Meditate; Come and
    Fall back; crew-wide on the new **K** screen (keys 1–9).
  - **Jobs:** bodyguard, vanguard, scout (marks enemies), medic (heals with chakra), gatherer (brings
    loot). **Behaviours:** aggressive, defensive, passive.
  - **Easier teaching:** lessons queue up; they move at half pace under any order (full under
    Train/Meditate), ×2 meditating together; 2 days per basic nature; "Teach all"; a jutsu that needs a
    nature they lack queues the lesson and is learned when it ends. A new Job tab on the ally screen.
  - Crew mates on Meditate gather nature energy. New `/noirm crew role|behaviour|come|fallback|attack|lesson|teachjutsu`.
- **Player meditation** (N or `/noirm meditate`): sit cross-legged; chakra and stamina ×3, Reserves
  trains (to 30), **nature energy** fills to 100 (more near plants and water, at night) for Sage Mode
  later; it fades when you don't meditate. HUD bar; `/noirm me nature <v>`.
- **CTL and WIL stats** (chakra control, willpower): grown only by active play (CTL: spending chakra while
  low, water walking, holding modes in fights; WIL: surviving locked-in, trading hits with stronger foes,
  strain, holding extra tails). CTL cuts jutsu cost and water-walk drain; WIL cuts Mangekyō strain and adds
  safe tails. HUD row, `/noirm stat ctl|wil`.
- **Perception focus:** a heavy attack slowed by your perception greys and muffles the world (game speed
  untouched). A parry inside it is a **just-frame parry**: no damage, the attacker's posture breaks, and
  a 10-tick ×1.5 counter that guards can't stop.
- **Casting engine:** tap jutsu form their seals quickly first (2 ticks a seal, 12 at most; `quick`
  jutsu stay instant); you move at 80% while weaving; a hit pauses, slows or breaks the seals (a
  fizzle: black smoke, 30% of the cost lost, no cooldown); spamming a minor projectile loses power;
  projectiles **clash** mid-air by the nature wheel (`data/noirm/jutsu_clash/rules.json`).
- Tests: 233/233 pass twice in a row; 32 new game tests (crew mind, casting, stats and parry, meditation); the roof-leap test uses a
  fixed clan (random rolls made it flaky).

## 2026-10-17 — Commands for everything, in sections, v0.9.1 (branch `claude/keen-cray-uc2d11`, PR #1)

- **`/noirm help`:** the commands in 11 clickable sections (me, combat, clan, jutsu, eyes, beast, crew,
  spawn, kit, world, master); clicking a command types it, hovering shows an example. `/noirm` alone
  shows the sections. All 80 commands are in `docs/COMMANDS.md` (generated).
- **New commands:**
  - **me:** a full profile (`/noirm me`), `me heal` (health, chakra, stamina, food, no cooldowns or bad
    effects), `me max`, `me stats <speed> <taijutsu> <vitality> <reserves>`;
  - **combat:** `combat on|off`, `style` (yours and the rest, click to switch), `style set`, `style moves`
    (which button opens with which move);
  - **natures and jutsu:** `nature list|remove|all`, `jutsu loadout`, `jutsu learn nature <nature>`,
    `jutsu forget <jutsu>|all`, `jutsu unequip <slot>|all`, `jutsu mastery all <level>`, `jutsu ready`;
  - **eyes:** `eyes` (at a glance), `eyes clear`;
  - **crew:** `crew` (everyone's health, respect, order, lessons), `crew order|range|jutsu`, `crew recruit`
    (the ninja you look at), `crew summon|heal|respect|teach|dismiss`;
  - **spawn:** `spawn ninja|rogue|ally|dummy|beast|master` (and `beast spawn`);
  - **kits:** `kit list` and 17 kits as data (`data/noirm/kits/`): tools, weapons, legendary blades,
    training, spawn eggs, and outfits that go straight on (Leaf shinobi, jōnin, ANBU-style, Kage, rogue,
    traveller, kimonos, Uchiha, Hyūga, Uzumaki, Senju, taijutsu fighter);
  - **world:** `world destruction on|off`, `world regen <seconds>`, `world cleanup [radius]` (never your
    crew, masters or villagers);
  - **masters:** `master learnall <master>`, `master reset`.
- Names default to the mod's namespace: `/noirm kit tools`, `/noirm style set strong_fist`.
- Tests: two new game tests (every help example parses and has text in all three languages; the main
  commands do what they say). Ally test ninjas now fight bare-handed (a rolled weapon made one flaky).

## 2026-10-15 — Playtest round 6: armor fit, footwork, gear, ally screen, Mangekyō, Might Guy, v0.9.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Armor fits the new body:** `BendyArmorModel` bends armor at the elbows, knees and waist with
  the animation (clothes no longer float off the body).
- **Footwork** (owner: the weakest spot): standing feet stay planted (two-bone leg IK; the hips
  drop for wide stances and lunges), and every strike sinks into the legs on the wind-up and drives
  through at contact.
- **More gear (39 pieces):** kimonos and hems in four colours, hakama, haori, the jōnin vest, Sand /
  Snow / Mist / Cloud / Stone vests, animal and oni masks and a cloth mask (they hide your name),
  Kage hats and robe, straw hat, travelling and rogue cloaks (our own crescent pattern, faster
  sprint), arm guards and hand wraps (more punch), tabi and straw sandals (softer falls). Guards,
  ANBU-style patrols, the Kage and rogues are dressed by role.
- **Special weapons:** four legendary blades dropped by rogues (Crescent Cleaver heals you,
  Sharkskin Greatsword drains chakra, Thunder Fangs stun, Reedcutter is the fastest), plus the
  craftable Gale Gunbai (guards and blows projectiles back) and Kusarigama (pulls targets in).
- **New ally screen:** five tabs (Overview with a turnable model, Gear, Jutsu, Natures, Orders),
  no more overlapping text. **Teach natures over time** (about 3 in-game days under Train or
  Meditate near you; faster when fighting together or if you've mastered it). New tactics:
  distance (close / mid / far) and jutsu use (free / conserve / none).
- **Mangekyō Sharingan:** awakens from losing a close ally, another player's death, or 95+ Resolve;
  each one has its own pattern, two abilities from Amaterasu, Tsukuyomi, Kamui, Kotoamatsukami
  and Izanagi, and a Susanoo in its own colour that grows in three stages. Every use strains the
  eyes toward blindness; the **Eternal Mangekyō** (transplanting another Mangekyō) ends that.
  **Eye transplants** with dropped eyes (a non-Uchiha can carry one implanted Sharingan). Some
  Uchiha ninjas have one. `/noirm mangekyo …` for testing.
- **Might Guy, the first master** (M4 part 1): masters are JSON (`data/noirm/masters/`). Talk to
  him, pass his spar (nobody dies), and he teaches the **Eight Gates** (Gate of Opening, Gate of
  Limit: much stronger, burns health), **Front Lotus**, **Morning Peacock** and **Daytime Tiger**.
  Passing grants the Strong Fist. He lives at the training ground of Leaf villages;
  `/noirm master spawn might_guy`.
- **My suggestions, built:** weapon trails behind swinging blades; hit reactions scale with the
  damage (a jab snaps the head, a heavy blow buckles the knees); **hand seals per jutsu** (30 canon
  sequences, named above the hotbar as you cast); **11 new taijutsu moves** (low sweep, push kick,
  turning backfist, body hook, hammer fist, spinning back kick, Leaf Great Flash, Leaf Gale, pivot
  palm, trigram sweep, lion palms), which ninjas use too.
- Tests: 199 game tests (29 new); several timing-sensitive tests made steady.
- Screenshots: `docs/screenshots/r9-*.jpg`.

## 2026-10-15 — Playtest round 5: our own animation engine, weapons, allies, water walking, Sharingan growth, v0.8.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Animations rebuilt from scratch** (the most urgent request):
  - our own engine replaces PlayerAnimator (so it no longer needs installing): players and NPC
    ninjas share one jointed body (elbows, knees, waist) where the **torso carries the arms and
    head** (before, a twisting punch left the shoulders behind);
  - living layers, in the spirit of Epic Fight: a real walk with knees and elbows, the **ninja
    run** when sprinting, knees tucking in jumps and sinking on landings, each style's own footwork,
    hits that throw the body away from where they came from, leaning into turns, hit-stop that
    freezes both fighters, and **springs** on every joint so limbs follow through and settle;
  - **51 new animations** (every old one replaced, the Boundless set too): each attack winds up,
    lands on the exact tick of the server's hit, follows through and settles; combos alternate
    sides; weapon movesets (kunai, staff, sword), throws, hand seals and a release push for jutsu;
  - **first-person arms:** in combat mode you see your real animated arms (fists up, punches,
    weapon swings);
  - `/noirm anim <name> [ticks] [targets]` previews any animation; `tools/animgen/motion.py` is
    the new authoring library.
- **Ninja weapons** (`docs/WEAPONS.md`): kunai, shuriken (sneak for a fan of 3), fūma shuriken,
  senbon, explosive tags and kunai (dig only with `noirmTerrainDamage`), bō staff, ninjatō, chakra
  blade (takes your nature). Throws stick in blocks and can be picked up; held weapons swap your
  combat moveset; ninjas carry and throw them; the village weapon shop sells them.
- **Allies:** weapons in hand count fully with their enchantments (Sharpness, Fire Aspect,
  Knockback, Sweeping…) for players and ninjas; allies shoot bows, block with shields; **feed**
  them by right-clicking with food (golden apples work, ramen restores chakra); a rogue now fights
  whoever engages it (a threat system) instead of chasing you.
- **Ninjas use jutsu much more,** chosen by range and situation, paying chakra, never through a
  crew mate.
- **Water walking:** stand and run on water with chakra; sneak to sink; ripples; allies follow.
- **Sharingan:** four ways to awaken (near death, a comrade's death, a long fight against a much
  stronger foe, built-up pressure with hints), growth only in real fights, progress in the HUD and
  jutsu screen; some NPC Uchiha awaken too.
- 26 new game tests (170 in all). Screenshots `docs/screenshots/r8-*.jpg`.

## 2026-10-14 — Villages, part 4: the Mist, Cloud and Stone villages, the canon Leaf wall, seats, mist, v0.7.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Three new hidden villages**, picked by biome:
  - **Mist** (swamps, dark forests): moss-topped stone drums on three islands, with two ring
    canals crossed by causeways and footbridges. Mist closes in when you walk in. The Mizukage.
  - **Cloud** (meadows, windswept hills): white walls, blue roofs and domes, and tall rock spires
    among the houses with a ladder up each (bare, a railed dish platform, or a little house on
    top). A ring of grey cliffs and spires, a light haze, and the Raikage's onion-domed tower.
  - **Stone** (badlands, gravelly hills): mud-brick rock towers 6–8 storeys high that taper as
    they rise, joined by rope bridges that end in doorways. A ring of grey cliffs, and the Tsuchikage's stepped crown.
  - `/noirm village capital mist|cloud|stone` builds one. Villages built with the command are
    now remembered with the world (`BuiltVillages`), so they get the mist, the haze and the
    no-pillagers rule too, not only the ones the world generates.
  - The Kage towers' eaves now break round the village-symbol medallion; one ran straight across
    the symbol (it made the Cloud's lightning bolt look like a pistol). The bolt is redrawn as a
    zig-zag too.
- **The Leaf village as in the series:**
  - a reddish-brown wall with a tiled top instead of battlements, round bastions, and red gates
    over the wall walk;
  - side gates with "a" and "un" painted on the doors;
  - zig-zag stairs up the Kage Rock and shelter tunnels into it;
  - the hospital gets a teal roof, a dome and a red-cross medallion.
- **Village life:** villagers sit on benches, stools and steps; they run home when a monster
  comes in; guards stay inside the area they look after.
- **Fixes from a code review of the village system:**
  - vanilla villages no longer overlap hidden villages, and their villagers and golems are
    removed from village chunks;
  - pillagers don't spawn inside villages;
  - a villager sitting down when the game saves is no longer lost;
  - beds and rugs no longer change colour at chunk borders;
  - leaves from nearby trees no longer spill into finished chunks;
  - speech bubbles no longer outlive their speaker;
  - `/noirm village` waits for chunks to load properly;
  - bars close the gap where the river runs under the wall;
  - bridges on slanted streets are whole;
  - caches are cleared when the server stops.
- Checked that a hidden village generated by the world itself (not the command) comes out whole
  across ~1,000 chunks.
- 7 new game tests (144 in all). Screenshots `docs/screenshots/r7-*.jpg`.

## 2026-10-14 — Villages v3: twice as wide, a real layout, lively streets, v0.6.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Hidden villages ~545 blocks across** (more than twice as wide as v2, about 4.6× the area),
  ~1,600 lots (~550 buildings, the rest yards, gardens and parks), up to 140 people.
  - **Bigger than one structure:** a core start plus one "ward" start per chunk, placed by our own
    structure placement (`WardPlacement`), all building from one shared plan. The site is measured
    once per village and remembered; the plan keeps a per-chunk index.
  - Towns and outposts keep clear of hidden villages; hidden villages are rarer (spacing 96).
  - `/noirm village` builds a few chunks per tick and tells you when it's done.
- **A layout like the series:** a fan of seven avenues from the Kage residence under the Kage
  Rock, six street arcs, alleys, a river with stone banks, landings and bridges (red arched
  footbridges, flat stone ones for avenues), and districts (market, shinobi quarter, clan district
  on the river, temple hill, lantern street, homes, outer ring). The desert village has spokes and
  ring streets round a central plaza.
- **Buildings that look lived in:**
  - every lot has its own colourway (walls, roof colour, trim, accent), neighbours differ;
  - façades: timber skirting and floor bands, pent eaves, sills, shutters, flower pots, mixed
    glass (clear, paper, warm lit);
  - roofs: coloured tile lips, water-tank clusters, roof gardens, washing lines, aerials;
  - round buildings with flared ring eaves; spires (cone, bottle, bell turret) in the skyline;
  - shop signboards, paper lanterns, lanterns strung across the lantern street with arched gates,
    avenue lamps that light at dusk, notice boards, power poles with sagging wires;
  - yards in every gap: kitchen gardens and fields, laundry, woodpiles, sheds, koi ponds, bamboo,
    roadside shrines, wells; a giant-tree park, a cemetery with a memorial stone and flame;
  - the jōnin standby station; the Kage residence with tiered eaves, round wing towers and a round
    village-symbol medallion.
- **Village life:** neighbours stop to chat (speech bubbles), villagers run from monsters, resident
  ninjas fight monsters that wander in (not creepers); cats, chickens and koi.
- Research notes in `docs/research-villages-v3.txt` (motifs only, nothing copied).
- 18 new game tests (137 in all), including every new building and the new life. Screenshots
  `docs/screenshots/r6-*.jpg`.

## 2026-10-13 — Villages v2: big hidden villages, towns, living villagers, v0.5.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Hidden villages ~250 blocks across** (3× wider, ~9× the area; about the most one structure
  can cover). Planned from a seed (`VillagePlan`/`VillagePlanner`), then built chunk by chunk.
  - **Landmarks:**
    - a great wall with a wall walk and lookouts;
    - the great gate (open painted doors);
    - a much bigger Kage tower (reception, archives, council room, office);
    - the Kage Rock: a real mountain with four carved faces (the desert village gets a ring of cliffs instead);
    - the Academy (classrooms, swing tree), the hospital, a clan district (Uchiha or Hyūga, with crest and koi pond);
    - a shrine with torii, a hot spring, two training grounds (one with a lake), the ramen shop.
  - **Streets and buildings:** ~130 lots of houses (1–4 storeys), apartment blocks with galleries,
    round towers, shops, restaurants, stalls and parks, along avenues and streets with lanterns,
    power lines and trees.
  - **Interiors:** kitchens, beds, tables, ladders, shop counters and shelves; chimneys with smoke
    and rooftop water tanks.
  - **Desert look:** vaulted and flat roofs.
- **Towns** (new, ~110 blocks): a chief, an inn, a shrine, shops and houses inside a fence. They
  share a placement set with outposts and keep away from capitals.
- **Living villagers:**
  - shopkeepers by shop (ramen, weapons with real gear, scrolls, flowers, teahouse);
  - villagers who go out by day and home at night;
  - children at the Academy, a village chief;
  - everyone has something to say.
- **Village ninjas:**
  - rooftop runners leap from roof to roof (and Body Flicker back if they fall);
  - patrols walk the wall walk;
  - trainees practise on dummies;
  - the clan district has its own clan.
- `/noirm village <capital|town|outpost> [leaf|sand|snow]` builds one where you stand.
- 6 new game tests (119 in all). Screenshots `docs/screenshots/r5-*.png`. Design in `docs/WORLDGEN.md`.

## 2026-10-13 — Playtest feedback round 4, v0.4.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Every jutsu is more dramatic:** vivid four-tone palettes, a cast flourish on every jutsu,
  double shockwave rings, camera-shaking explosions, spiral orbs, cones for breath jutsu, glinting
  auras. Genjutsu, binds, buffs, Body Flicker, Substitution, walls and Rotation got new effects.
- **Deep allies:** crew mates level their stats (from fights, training at a dummy, meditating),
  master the jutsu they use and equip up to 4 (picked in the ally screen). **Respect** (0–100)
  starts from how strong you are next to them and moves with what you do (winning fights, gifts of gear,
  hitting them, letting crew mates die); low respect means they disobey or leave. Each has a
  **trait** (brave, cautious, loyal, proud), a morale boost, retreats when hurt, and healers
  support. Right-click a crew mate for the ally screen: their gear and hands, orders
  (follow / wait / train / meditate), style, look, jutsu to teach and equip.
- **Tailed beasts, part 2:** in creative you can seal a beast at any health. Tail progression
  1 → 9: each press of the cloak key draws one more tail, each tail is stronger and harder to
  hold; tails unlock with use. A **bond** with your beast (−100…100): it makes requests (defeat a
  strong enemy, rest, let it out, offer chakra), may take over when the bond is low, gives you
  chakra when you're low, and you can ask for chakra (crouch + cloak key). Version 1 orange,
  version 2 crimson (burns), full-bond gold. `/noirm beast`, `/noirm beast bond <n>`,
  `/noirm beast tails <n>`.
- **NPCs fight with the player's animations:** a keyframe player on the client reads the same
  animation files, synced per move and dodge, with stances, rolls, guard and flinches,
  cross-faded. A new jointed NPC model with **elbows, knees and a bending waist**.
- **Real ninja gear:** 16 wearable pieces with armor points, recipes and their own art
  (forehead protectors, Senju red battle armor, Uchiha tunic, Hyūga robes, Uzumaki coat, flak
  vest, ANBU mask and armor…). Ninjas wear their clan's gear (5% drop).
- **Clean random looks:** each ninja's skin is built from 55 generated parts (hair, eyes, brows,
  marks, accessories…) with clan colors, so thousands of different faces instead of 23 skins.
- **Ninja villages:** a rare **hidden village** (wall and great gate, red Kage tower with the
  village symbol, Kage Rock, round houses with water tanks, food stalls, torii, training
  ground) with a **Kage** (the strongest ninja around), guards and wandering ninjas, and common
  **outposts**. Leaf, Sand and Snow looks by biome. Residents leave you alone unless you attack
  the village; **Village Merchants** trade for emeralds (kunai, ramen, scrolls…). See
  `docs/WORLDGEN.md`.
- **Spawn ninjas by clan:** a spawn egg per canon clan (never a surprise jinchūriki) and
  `/noirm ninja <clan> [tails] [strength]`.
- **Cleanup:** dead code removed, the game tests share their helpers (`TestKit`). Fixed: village
  merchants drew with a missing texture.
- 114 game tests, all green; checked in the real client (`docs/screenshots/r4-*.png`).

## 2026-10-13 — Playtest feedback round 3, v0.3.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Fixed:** the Great Fireball burst on its caster. Jutsu never hit their caster, clones or crew.
- **Lightning:** client-drawn crackling bolts on every raiton jutsu; 8 new jutsu (Kirin, Chidori
  Current, Chidori Senbon, Great Fire Annihilation, Fire Dragon, Great Waterfall, Earth Dragon,
  Pressure Damage) with new behaviors strike, wave and nova.
- **Lightning Armor** is far stronger; every mode grows 25% per mastery level.
- **Styles feel different:** stances, Strong Fist's own animations, per-style impact sounds/effects.
- **Crew:** beaten ninjas yield and can be invited; they follow, fight and never hit you.
- **Tailed beasts** can be killed once subdued (or sealed).
- **Shadow Clones** have real health and carry your jutsu, eyes, mode and cloak.
- **Outfits:** 23 original clan skins for ninjas.
- **Movement:** speed makes you run faster, jump higher, leap and step up blocks.
- **No cap:** stats grow without limit; power is proportional to stats, toughness to √vitality;
  the Nine-Tails and its Tailed Beast Bomb are on a different scale.

## 2026-10-13 — Playtest feedback round 2, v0.2.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Fights feel like fights:** three new styles (Brawler, Strong Fist, Gentle Fist) switched with
  H; sprint and back attacks; a grab (C) and clinch with knees, headbutt and throw (grabs beat
  guard, strikes beat grabs, dodge to escape); wall splats, knockdowns and juggles. Ninjas use
  them too. 11 new animations from our own generator (`tools/animgen`).
- **Attacks are dangerous:** jutsu power grows with reserves, mastery and charge; terrain
  destruction (craters, scorch marks, ice, spikes, smashed walls) that heals itself (gamerules
  `noirmTerrainDamage`, `noirmTerrainRegen`).
- **Canon scale and canon jutsu:** researched names and behavior (`docs/RESEARCH.md`); a huge
  Great Fireball, homing Phoenix Sage Fire and Water Dragon, Lightning Armor as a real mode;
  new Rasengan (Reserves 20), Shadow Clone (Reserves 15), Shadow Imitation, Water Formation Wall.
- **Yin, Yang and Yin–Yang** natures (Uchiha lean Yin, Senju Yang) with Mystical Palm,
  Partial Multi-Size, Demonic Illusion, Truth-Seeking Ball, Creation Rebirth.
- **Particles:** custom additive particles and layered presets per nature, on every jutsu and hit.
- **Byakugan ≠ Sharingan:** no prediction or perception boost; instead no blind spot, chakra
  points on bodies and a 360° chakra radar. Hyūga default to the Gentle Fist.
- 87 game tests; network protocol 5.

## 2026-10-12 — Playtest feedback round 1 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Fixed: combat animations never showed** (a PlayerAnimator layer with a modifier in front of
  the animation reported "inactive"). Combos and kicks now play in full.
- **Eyes against everything:** Sharingan (2+ tomoe) and Byakugan precognition now warn about
  vanilla mobs (melee reach, bow draw, creeper swell, spider leap) and projectiles; perception
  slows projectiles coming at you (arrows keep their damage).
- **A living Sharingan:** heartbeat warning that quickens as the hit nears, markers pointing at
  each threat, afterimages on attackers, an activation flash with spinning tomoe, a faint tint.
- **Two new stats:** Vitality (max health; trained by surviving hits) and Reserves (max chakra and
  regen; trained by spending chakra). The HUD shows all four.
- **Nature tree:** 9 combined natures (Wood, Ice, Lava, Storm, Boil, Explosion, Magnet, Scorch,
  Dust), unlocked by owning the parts and mastering a jutsu of each; 8% of new clans carry one.
- **39 jutsu:** mastery chains for the basic natures, 2 for each combination; new behaviors bind,
  pillar, cloud, rain, beam, buff; multi-shot projectiles with effects. Ninjas use them all.
- **New jutsu screen (J):** cards by nature with lock reasons and details, plus the nature tree
  with unlocking.
- Commands: `/noirm byakugan`, `/noirm jutsu mastery`, `/noirm stat vitality|reserves`.
- 13 new game tests (71 total), including one that casts every nature jutsu.

## 2026-10-12 — Training, the Byakugan and tailed beasts (branch `claude/keen-cray-uc2d11`, PR #1)

- **Training:** a training dummy (item + recipe: hay bales and sticks) that never dies, stays
  planted and shows your combo and damage. Landing hits and parrying train taijutsu; dodging
  and sprinting train speed; each point costs more. Solo drills stop at 30, real opponents teach
  more the stronger they are. Speed now also makes you move faster. Stat bars in the combat HUD.
- **Byakugan (Hyūga):** born with it, G opens it. Everyone nearby is drawn as a chakra
  silhouette through walls; +15% perception; warned of every attack; guard from any direction.
  Gentle Fist: more posture damage, half damage through guard, drains chakra, closes chakra
  points (24 closed = no jutsu for 6 s). Eight Trigrams Palm Rotation and 64 Palms. Hyūga
  ninjas use them.
- **Tailed beasts:** 1–9 tails (bigger, tougher and stronger with more tails), fighting with the
  combat system (claw combo, slam) and a charged Tailed Beast Ball. Beaten down, they are
  subdued; a Sealing Scroll captures them, then seals them into you, another player or a ninja.
  3% of newcomers are born jinchūriki; 5% of sparring ninjas secretly are (cloak at low health,
  and the beast breaks free when they fall). Jinchūriki learn the Tailed Beast Ball (cloak only).
- **Client smoke test found and fixed:** a launch crash from two event subscribers named
  `Registration` with the same handler (Forge's generated class names collided). The smoke tool
  can now type chat commands (cheats on in the copied world).
- **Tests:** tests where fighters swing or knock things back now run in their own batches and
  clean up what leaves the test area. 21 new game tests (58 total, all passing).

## 2026-10-12 — Milestone 3: Identity & powers (branch `claude/keen-cray-uc2d11`, PR #1)

- **Clans:** four canon clans (JSON) plus procedurally generated custom clans in a growing,
  per-world registry. Newcomers roll ~10% canon, otherwise a known or brand-new custom clan.
  Clans give natures (with affinities), 10 possible traits, stat leanings and an awakening path.
- **Jutsu:** data-driven definitions on reusable behaviors; a 4-slot loadout cast with keys 1–4
  in combat mode (tap / charge / channel); the J screen; nature affinity, mastery and cooldowns;
  9 jutsu.
- **Sharingan:** 1–3 tomoe, toggle (G), chakra drain, perception boost, precognition, genjutsu,
  copy at 3 tomoe, and glowing red irises.
- **Awakening:** the Resolve meter and near-death "locked in" state, with identity paths (Sharingan,
  nature surge, beast, will), followed by exhaustion.
- **Jinchūriki:** the chakra cloak (B), and the beast taking over on awakening.
- **Sparring ninjas** get clans, cast jutsu and (Uchiha) use the Sharingan.
- **Test commands:** `/noirm clan | nature | jutsu | sharingan | resolve | awaken | biju`.
- **Client smoke test found and fixed:** a crash on the first jutsu cast (missing projectile
  renderer); the fireball trail is now a dense ball.
- 17 new game tests (37 total, all passing).

## 2026-10-12 — Milestone 2: Combat feel v1 (branch `claude/keen-cray-uc2d11`, PR #1)

- Combat system: switchable combat mode; data-driven moves and styles
  (`data/noirm/combat/`); combo routes (L-L-L, L-L-H finisher, H, crouch+H launcher);
  10-tick input buffer; cancel windows; hit-stop; per-move knockback, lift and lunge.
- Defence: guard with posture and parry (5-tick window), posture break, dodge with i-frames,
  perfect dodge, and focus moments that slow nearby fighters.
- Perception, the owner's idea: wind-ups scale with the strength ratio, capped at 2.5×. Added
  speed and taijutsu stats, plus `/noirm stat`.
- Client: keys (R, Alt, V, Z), mouse buttons become attacks, lock-on, camera shake, focus FOV
  pulse, posture/combat HUD, and a target bar.
- PlayerAnimator dependency, with 8 MIT Boundless animations (with attribution).
- Sparring ninja: an enemy using the same combat system, with telegraph poses.
- Fixes found by the off-screen client smoke test: the mixin refmap remap (the client crashed
  at launch), lowercase resource names, and vanilla knockback stacking on combo hits.
- `tools/client-smoke/` to run and drive the real client without a monitor; screenshots are in
  `docs/screenshots/`.
- 12 new game tests (20 total, all passing).

## 2026-10-12 — Combat design ideas (branch `claude/keen-cray-uc2d11`, PR #1)

- Added `docs/COMBAT_DESIGN.md`, recording the owner's perception idea (stronger fighters see
  attacks slower), the near-death awakening, staged teaching by masters, and the proposed
  combat-feel checklist. Open questions are listed in `STATUS.md`.

## 2026-10-12 — Milestone 1: Shinobi core (branch `claude/keen-cray-uc2d11`, PR #1)

- Chakra and stamina (`ShinobiData`) attached to every player through a Forge capability. They're
  saved in the player file, carried over on respawn (refilled after a death) and on dimension
  change.
- Server-side regen: chakra at 1 per second; stamina at 20 per second after a 1-second pause
  once spent.
- `SyncShinobiDataPacket` sends the values to the client when they change. HUD bars sit in the
  top-left corner.
- `/noirm stats | set | max | refill` debug command.
- Text in English and Spanish (`assets/noirm/lang/`).
- 7 new game tests (8 total, all passing). Added the `Noirm.id()` helper.
- Recorded the owner's decisions: combat is switchable, and Might Guy is the first taijutsu master.

## 2026-10-12 — Foundation (branch `claude/keen-cray-uc2d11`, PR #1)

- Reorganized the uploaded NydoMod 5.5 assets into `reference/nydo/` by content type (1,209
  files moved, 37 exact duplicates removed). Wrote `reference/nydo/README.md` covering the
  inventory, the reconstructed game systems and licensing.
- Created the NOIRM Forge 1.20.1 project from the official MDK (Forge 47.4.10): mod id
  `noirm`, main class `com.noirm.Noirm`.
- Added the first game test (`com.noirm.gametest.NoirmGameTests.modIsLoaded`) and the shared
  empty test arena `data/noirm/structures/empty.nbt`. `runGameTestServer` passes.
- Added the docs: `ROADMAP.md`, `GETTING_STARTED.md`, `STATUS.md`, this changelog, and
  `CLAUDE.md` for session context.
