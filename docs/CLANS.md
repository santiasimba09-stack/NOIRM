# Clans: research and what each clan does in NOIRM

_Owner's brief, 2026-11-18 (`WAVE_STYLES_CLANS.md`, part 2): "make clans much more significant…
make their perks noticeable." Personal talents (part 3) are in `TRAITS.md`._

## How clans work now (plain version)

- Every clan file (`data/noirm/clans/<id>.json`) has **perks**: things the blood changes, each a
  named number (`"max_chakra": 60`, `"strain": -0.35`…). They are summed with your personal
  talents and read by the systems they touch: stats, regeneration, jutsu cost and power, eyes,
  pills and foods, sealing, the tailed beast bond, durability, perception, crew, NPCs.
- **Clan jutsu**: some clans are born knowing a secret technique. Only members can use it (an
  outsider who somehow learns it gets "Only that clan's blood can use this technique").
- **Looks**: each clan has default hair and eye colours. In the creator, choosing a roll of that
  clan sets your hair (and eyes) to them, and you can still change them. NPC ninjas of each canon
  clan have their own appearance table (`looks.json`, from `tools/skingen/gen_parts.py`).
- **Talent bias**: a clan makes some personal talents likelier (an Uzumaki is 5× likelier to
  roll Natural Sealer), but members still differ.
- **Generated clans** get two **perk packages** from `data/noirm/clan_perks/` (18 packages,
  some with a trade-off), so they have real identities too. Generated clans saved before this get
  a fixed package (seeded by the clan's id) when the world loads.
- **Old characters** get their clan's perks and roll talents once, on their next login.
- **See it**: the creator's new **Clan** tab, `/noirm clan perks [clan]`, and `/noirm me`.
- **Canon clans are still ~10% of newcomers** (the owner's rule); there are now 26 of them, picked
  by weight (Uchiha and Hyūga 3; Uzumaki, Senju, Nara, Akimichi, Yamanaka, Aburame, Inuzuka,
  Sarutobi 2; the rest 1).

The perk names, what each means and where it's used are listed in `traits/Perk.java`; the numbers
live in the JSON. "+35%" perks add to 100% (several sources are added, not multiplied).

## Research table

Sources (titles only): *Naruto* and *Naruto Shippūden* (manga by Masashi Kishimoto, and the
anime); *Boruto: Naruto Next Generations*; the official databooks (*Rin no Sho*, *Tō no Sho*,
*Sha no Sho*, *Jin no Sho*); *Narutopedia* clan articles. Summaries are our own words.

| Clan | Village | Kekkei genkai / hiden | Physical traits | Lifespan, chakra quirks | Typical natures | Role |
|---|---|---|---|---|---|---|
| Uzumaki | Whirlpool (Uzushio), scattered | Fūinjutsu (sealing) mastery; chakra chains (Adamantine Sealing Chains); healing chakra (bite healing) | Red hair, often round faces | Very long-lived; huge reserves and life force; heal fast; prized as jinchūriki | Wind, water | Sealers, jinchūriki, distant kin of the Senju |
| Uchiha | Leaf | Sharingan → Mangekyō → Eternal; Susanoo | Black hair, dark eyes, pale skin | Strong emotions feed the eyes (the "curse of hatred"); Mangekyō blinds with use | Fire, lightning; Yin | Police force, rivals of the Senju |
| Senju | Leaf | Wood Release (Hashirama); great life force | Brown or dark hair | Huge vitality and stamina, fast healing; medical talent (Tsunade) | Earth, water; Yang | Founders of the Leaf |
| Hyūga | Leaf | Byakugan; Gentle Fist; Eight Trigrams | Pale, pupil-less eyes; long dark hair | Fine chakra control; main and branch houses (the caged-bird seal) | Water (minor) | Taijutsu and dōjutsu clan |
| Nara | Leaf | Shadow techniques (Possession, Neck Bind, Stitching) | Dark hair in a high ponytail, narrow eyes | Famous intellect and laziness; keep deer | Yin, earth/fire | Strategists, jōnin commanders |
| Akimichi | Leaf | Expansion (Multi-Size, Human Boulder); calorie control; Butterfly Mode | Big build, auburn hair, spiral marks on the cheeks | Burn food for chakra; secret pills (spinach, curry, chili) | Earth, Yang | Front-line heavy fighters (Ino–Shika–Chō) |
| Yamanaka | Leaf | Mind Body Switch, Mind Transmission, sensing | Pale blond hair, pale blue-green eyes | Sensors; mind techniques leave the body exposed | Yin | Intelligence, interrogation, flower shop |
| Aburame | Leaf | Kikaichū hive (chakra-eating insects) | Dark glasses, high collars, dark hair | Host insects from birth; insects drain chakra and sense; poison-wise | Earth/fire (unclear) | Trackers, quiet specialists |
| Inuzuka | Leaf | Ninken partners; Fang Over Fang; Beast Mimicry | Red fang marks on the cheeks, wild brown hair, sharp eyes | Keen smell, feral speed | Earth (unclear) | Trackers, hunters |
| Hatake | Leaf | White Light Chakra Sabre (Sakumo); ninken summons (Kakashi) | Silver-white spiky hair; Kakashi's mask | Prodigies; Kakashi's chakra reserves are small | Lightning, earth | Small line of geniuses |
| Sarutobi | Leaf | Monkey King summon; broad ninjutsu ("the Professor") | Brown hair | Long careers, teachers; the Will of Fire | Fire, wind | Hokage line (Third Hokage, Asuma, Konohamaru) |
| Kaguya | Mist (wiped out) | Shikotsumyaku (Dead Bone Pulse): bones as weapons and armour | White hair, two red dots on the forehead, green eyes | Battle-hungry; Kimimaro's wasting illness | — (none needed) | Berserker warriors |
| Hōzuki | Mist | Hydrification (body turns to water); Water Gun | Pale blue-white hair, purple eyes, sharp teeth | Need water to stay strong; weak to lightning | Water | Swordsmen of the Mist (Mangetsu, Suigetsu) |
| Yuki | Mist (hunted) | Ice Release; Demonic Mirroring Ice Crystals | Long black hair, pale skin, fine features | Fast, precise | Wind + water → ice | Hunted bloodline (Haku) |
| Chinoike | Land of Fire (exiled) | Ketsuryūgan (Blood Dragon Eye): blood genjutsu, blood manipulation | Red eyes, dark hair | Feared and exiled; a terrible lineage | Yin | Exiled clan (Kagura's line, the anime) |
| Kurama | Leaf | Genjutsu so strong it feels and acts real | Usually frail | Weak bodies, prodigious illusions (Yakumo) | Yin | Illusionist house |
| Fūma | Land of Rice / Sound | Giant shuriken; distant Uchiha kin (games, filler) | Varied | Wandering mercenaries | Wind, fire | Mercenaries |
| Iburi | Land of Fire (underground) | Smoke body (unstable) | Grey, pale, reclusive | The body can scatter in strong wind | Fire | Hidden assassins (the anime) |
| Jūgo's clan | Unknown (hidden) | Absorb nature energy (the origin of the curse mark); Sage Transformation | Orange hair | Murderous fits; regenerate by taking in others' cells | — (sage chakra) | Feared berserkers |
| Kamizuru | Stone | Bee swarms (aerial insects, poison stingers) | Varied | Rivals of the Aburame | Earth | Insect fighters (the anime) |
| Yotsuki | Cloud | Lightning Release Armour; strong bodies | Dark skin, light hair | Physically powerful | Lightning | Raikage line (A, Killer B) |
| Kazekage line | Sand | Magnet Release (Iron Sand, Gold Dust); sand shields | Red or sandy hair (Gaara's family) | Kazekage bloodline; jinchūriki of Shukaku | Wind + earth → magnet | Sand's rulers |
| Kurogane | Land of Iron | Samurai swordsmanship (Tsubaki) | Topknots, armour | Samurai mould chakra into blades, not ninjutsu | — | Samurai lineage (Boruto) |
| Shimura | Leaf | Wind, Izanagi experiments (Danzō) | Bandages, a cane | Ruthless, secretive | Wind | Root (ANBU black ops) |
| Hoshigaki | Mist | Shark-like body; Samehada; huge chakra | Blue-grey skin, gills, sharp teeth | "A tailed beast without a tail" | Water | Swordsmen of the Mist (Kisame) |
| Might (taijutsu lineage) | Leaf | Eight Inner Gates; pure taijutsu | Bowl cut, thick brows, green jumpsuit | Almost no ninjutsu; endless stamina and heart | — | Taijutsu masters (Duy, Guy; Rock Lee is not family) |

Lee: Rock Lee has no clan; his style comes from Might Guy, so the Might line covers it.

## What each clan does in NOIRM

Green = helps, red = costs. "Start" stats are added once when the character is made.

| Clan | Perks (from its JSON) | Clan jutsu | Trade-off |
|---|---|---|---|
| **Uzumaki** | Max chakra +60, chakra pool +35%, chakra regen +60%, healing +0.15 HP/s, max health +2, poison and burns 30% resisted, Mangekyō strain −35%, pill toxicity −30%, sealing power +50%, sealing cost −35%, beast bond +50% | Sealing Art: Adamantine Chains (binds, then a chakra seal: no jutsu for 6 s) | Start CTL −2 (so much chakra is hard to control) |
| **Uchiha** | Perception +10%, genjutsu power +25%, Resolve +25%, mastery +15%; the Sharingan route as before | — (the eyes) | Mangekyō strain +25% (the curse) |
| **Hyūga** | Gentle Fist +25% (more chakra drained, more points closed), start CTL +2, jutsu cost −10%, taijutsu training +15%; the Byakugan | — (Rotation, 64 Palms as before) | Jutsu power −10% |
| **Senju** | Max health +6, healing +0.2 HP/s, max stamina +40, stamina regen +30%, chakra regen +25%, medical ninjutsu +20%, bloodline affinity +0.25 (Wood comes out stronger), start VIT +3 | — | Start CTL −2, jutsu cost +5% |
| **Nara** | Start CTL +3, control training +40%, mastery +30%, willpower training +15% | Nara Art: Shadow Possession (channel, 20 blocks, cheap) | Taijutsu training −20%, max stamina −10 |
| **Akimichi** | Food strength +60%, max health +6, start DUR +3, posture +20, max stamina +20, melee +8% | Akimichi Art: Multi-Size (a mode: ×1.8 damage, ×0.6 damage taken) | Start SPD −2, and Multi-Size slows you |
| **Yamanaka** | Perception +10%, senses chakra 24 blocks, genjutsu power +25%, genjutsu resistance +20%, start WIL +2 | Yamanaka Art: Mind Disturbance (catch a mind) | Max health −2 |
| **Aburame** | Immune to poison, poison and burns 30% resisted, senses chakra 16 blocks, insects drain 3 chakra per hit (half to you), start CTL +1 | Aburame Art: Parasitic Insect Swarm (a weakening cloud) | Fire damage +25% |
| **Inuzuka** | A ninja hound at your side from the start, senses (smell) 20 blocks, start SPD +2, speed training +20%, melee +8% | Inuzuka Art: Fang Over Fang (a flurry) | Start CTL −1 |
| **Hatake** | Mastery +25%, start SPD +2, perception +5%, melee +5%, a ninja hound | — | Max chakra −10 |
| **Sarutobi** | Mastery +20%, jutsu power +8%, crew respect +25%, Resolve +20%, willpower training +15% | — | — |
| **Kaguya** | Start DUR +6, max health +4, damage taken −8%, melee +12%, durability training +25% | Kaguya Art: Finger Bone Bullets | Poison and burns 20% *worse* (the illness) |
| **Hōzuki** | Liquid body: in water, in the rain or near a river, a blow or projectile passes through 60% of the time; max chakra +20; DoT −15%; speed training +10% | Hōzuki Art: Water Gun | Max health −2 |
| **Yuki** | Ice nature (with wind and water), start CTL +2, perception +5%, speed training +15%, DoT −10% | — (the Ice jutsu) | Max health −2 |
| **Chinoike** | Genjutsu power +40%, perception +10%, genjutsu resistance +15% | Chinoike Art: Blood Illusion (catch + blind) | Max health −2, strain +10% |
| **Kurama** | Genjutsu power +50%, genjutsu resistance +30%, start WIL +2 | — | Max health −4, max stamina −20 |
| **Fūma** | Melee +6%, perception +5%, start SPD +1, taijutsu training +10% | — | — |
| **Iburi** | Smoke body: anywhere, a blow or projectile passes through 25% of the time; genjutsu resistance +10%; speed training +10% | Iburi Art: Smoke Haze (blinding cloud) | Max health −4 |
| **Jūgo's clan** | Max health +6, melee +15%, healing +0.15 HP/s, taijutsu training +20% | Jūgo's Art: Sage Frenzy (a mode that burns health) | Start CTL −3, genjutsu resistance −20% |
| **Kamizuru** | Senses 12 blocks, DoT −20%, pill strength +10%, jutsu power +5% | Kamizuru Art: Bee Swarm (homing, poisoned) | Crew respect −10% |
| **Yotsuki** | Max health +4, melee +12%, start DUR +2, max stamina +20 | — | Start CTL −1 |
| **Kazekage line** | Magnet nature, start DUR +3, posture +20, jutsu power +5% | — (the Magnet jutsu) | Max stamina −10 |
| **Kurogane** | Melee +15%, posture +30, start DUR +2, taijutsu training +20% | — | Jutsu power −15% |
| **Shimura** | Genjutsu resistance +15%, start WIL +3, jutsu cost −5% | — | Crew respect −20% |
| **Hoshigaki** | Max chakra +80, chakra pool +25%, chakra regen +30%, melee +10%, max health +2 | — | Crew respect −15% |
| **Might** | Taijutsu training +50%, max stamina +50, stamina regen +40%, melee +12%, start DUR +2, Resolve +30%; no nature at all | — (the Gates come from Guy) | Jutsu power −30%, jutsu cost +20% |

The Uchiha, Hyūga, Senju and Uzumaki keep their old clan traits (Keen Eyes, Deep Reserves…) on
top. Talent biases are in each clan file (`talent_bias`).

### The Uzumaki and sealing, in detail

- **Chakra:** 100 → about 246 at the start (×1.35 on top of +60, plus Deep Reserves), and it
  regenerates 60% faster. It keeps growing with Reserves training.
- **Healing:** +0.15 health every second (9 a minute) while not starving.
- **Longevity** (NOIRM has no ageing, so it shows as slower wear): Mangekyō strain −35%, pill
  toxicity −30%, poison/wither/burning/bleeding damage −30%.
- **Sealing:** sealing jutsu (the clan's chains, and any jutsu with "seal" in its id) cost 35%
  less and hit 50% harder. With a Sealing Scroll: a beast sealed by an Uzumaki starts +15 bond, the
  scroll survives the sealing 30% of the time (reusable), and an Uzumaki (any sealing power
  ≥ 40%) can capture a beast that is merely under 25% health, not only a subdued one.
- **Jinchūriki:** every bond gain ×1.5.

### Clan jutsu (12)

All are JSON on existing behaviours (`data/noirm/jutsu/clan/`), with hand seals
(`data/noirm/jutsu_seals/clan/`). NPC ninjas of the clan learn them as signature techniques.

| Jutsu | Clan | Behaviour | What it does |
|---|---|---|---|
| Sealing Art: Adamantine Chains | Uzumaki | bind | Chains (chain-block look) bind the target 3.5 s, then the **chakra seal** (no jutsu for 6 s) and weakness |
| Nara Art: Shadow Possession | Nara | shadow_bind | Channelled shadow bind, 20 blocks, cheaper than the Yin version |
| Akimichi Art: Multi-Size | Akimichi | mode | Grow huge: ×1.8 damage, ×0.6 damage taken, slower |
| Yamanaka Art: Mind Disturbance | Yamanaka | genjutsu | Catch a mind: stunned, then dazed |
| Aburame Art: Parasitic Insect Swarm | Aburame | cloud | A dark swarm: damage, hunger and weakness |
| Inuzuka Art: Fang Over Fang | Inuzuka | flurry | A whirl of eight strikes in front of you |
| Kaguya Art: Finger Bone Bullets | Kaguya | projectile | Five fast bone bullets |
| Hōzuki Art: Water Gun | Hōzuki | projectile | One fast, hard-hitting water shot (stronger near water) |
| Chinoike Art: Blood Illusion | Chinoike | genjutsu | Catch a mind and blind it |
| Iburi Art: Smoke Haze | Iburi | cloud | A blinding, slowing smoke cloud |
| Kamizuru Art: Bee Swarm | Kamizuru | projectile | Six homing, poisoned bees |
| Jūgo's Art: Sage Frenzy | Jūgo's clan | mode | Nature energy floods in: fast and strong, but it burns health |

## Generated clans: perk packages

Two per clan, from `data/noirm/clan_perks/` (each also biases talents): Vast Chakra, Tough
Bloodline, Keen Senses, Swift Line, Fist Tradition, Chakra Masters, Elemental Devotion, Sealing
Scribes, Herbalists, Iron Minds, Long-lived, Hearty Eaters, Scholars, Beast Tamers, Illusionists,
Healers, and two with a cost: Hot-Tempered (melee +12%, Resolve +30%, jutsu cost +8%) and Frail
Geniuses (jutsu power +15%, mastery +20%, max health −4).

## NPC ninjas

- They wear their clan's look: red Uzumaki hair, pale Hyūga eyes, white Kaguya hair with forehead
  marks, Akimichi cheek spirals, Inuzuka fang marks, Aburame dark glasses, a Hatake mask, blue-grey
  Hoshigaki skin with gill stripes, the Might bowl cut and green suit, and so on (26 tables).
- They use the perks that make sense for them: max health, healing, damage dealt and taken, damage
  over time, fire weakness, poison immunity, liquid/smoke bodies, jutsu power, the Gentle Fist.
- They roll personal talents too (saved on the entity), except on the game-test server.

## Tuning

- Clan perks, clan jutsu, looks, biases: `data/noirm/clans/*.json`.
- Packages: `data/noirm/clan_perks/*.json`; two per clan: `Talents.PACKAGES_PER_CLAN`.
- Sealing scroll numbers: constants at the top of `SealingScrollItem`.
- Caps: `Perks.MIN_MULTIPLIER` (0.1), `Perks.MAX_DODGE` (0.8).
- NPC looks: `tools/skingen/gen_parts.py` (`CLANS`), then `python3 tools/skingen/gen_parts.py --looks-only`.
