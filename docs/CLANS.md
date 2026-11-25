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
- **Clan styles**: a clan file can name a fighting style (`"style"`): Hyūga `gentle_fist`, Akimichi
  `sumo`, Might `leaf_hurricane`. Members are meant to get it through `StyleSystem.grant` (the styles
  wave); until that is merged in, `ClanSystem.assign` holds a marked TODO.
- **Generated clans** get two **perk packages** from `data/noirm/clan_perks/` (18 packages,
  some with a trade-off), so they have real identities too. Generated clans saved before this get
  a fixed package (seeded by the clan's id) when the world loads.
- **Old characters** get their clan's perks and roll talents once, on their next login.
- **See it**: the creator's new **Clan** tab, `/noirm clan perks [clan]`, and `/noirm me`.
- **Canon clans are the normal roll: 80% of newcomers** (v0.3, owner's ask; it was 10%). The 26 canon
  clans are picked from the odds table `data/noirm/clan_odds/canon.json` (see "Round 3" below); the
  other 20% are this world's generated clans.

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
| Hatake | Leaf | White Light Chakra Sabre (Sakumo); ninken summons (Kakashi) | Silver-white spiky hair; Kakashi's mask | Prodigies; Kakashi's own reserves are average (the Sharingan implant drained him) | Lightning, earth | Small line of geniuses |
| Sarutobi | Leaf | Monkey King summon; broad ninjutsu ("the Professor") | Brown hair | Long careers, teachers; the Will of Fire | Fire, wind | Hokage line (Third Hokage, Asuma, Konohamaru) |
| Kaguya | Mist (wiped out) | Shikotsumyaku (Dead Bone Pulse): bones as weapons and armour | White hair, two red dots on the forehead, green eyes | Battle-hungry; Kimimaro's wasting illness | — (none needed) | Berserker warriors |
| Hōzuki | Mist | Hydrification (body turns to water); Water Gun | Pale blue-white hair, purple eyes, sharp teeth | Need water to stay strong; weak to lightning | Water | Swordsmen of the Mist (Mangetsu, Suigetsu) |
| Yuki | Mist (hunted) | Ice Release; Demonic Mirroring Ice Crystals | Long black hair, pale skin, fine features | Fast, precise | Wind + water → ice | Hunted bloodline (Haku) |
| Chinoike | Land of Fire (exiled) | Ketsuryūgan (Blood Dragon Eye): blood genjutsu, blood manipulation | Red eyes, dark hair | Feared and exiled; a terrible lineage | Yin | Exiled clan (the survivor is Chino, *Sasuke Retsuden*; not Kagura's line) |
| Kurama | Leaf | Genjutsu so strong it feels and acts real | Usually frail | Weak bodies, prodigious illusions (Yakumo) | Yin | Illusionist house |
| Fūma | Land of Rice / Sound | Giant shuriken; distant Uchiha kin (manga canon, *Boruto* vol. 10) | Varied | Wandering mercenaries | Wind, fire | Mercenaries |
| Iburi | Land of Fire (underground) | Smoke body (unstable) | Grey, pale, reclusive | The body can scatter in strong wind | Fire | Hidden assassins (the anime) |
| Jūgo's clan | Unknown (hidden) | Absorb nature energy (the origin of the curse mark); Sage Transformation | Orange hair | Murderous fits; regenerate by taking in others' cells | — (sage chakra) | Feared berserkers |
| Kamizuru | Stone | Bee swarms (aerial insects, poison stingers) | Varied | Rivals of the Aburame | Earth | Insect fighters (the anime) |
| Yotsuki | Cloud | Lightning Release Armour; strong bodies | Dark skin, light hair | Physically powerful | Lightning | Cloud fighters (only Kiyoi is a named Yotsuki; "Raikage line" is fanon) |
| Kazekage line | Sand | Magnet Release (Iron Sand, Gold Dust); sand shields | Red or sandy hair (Gaara's family) | Kazekage bloodline; jinchūriki of Shukaku | Wind + earth → magnet | Sand's rulers |
| Kurogane | Land of Iron | Samurai swordsmanship (Tsubaki) | Topknots, armour | Samurai mould chakra into blades, not ninjutsu | — | Samurai lineage (Boruto) |
| Shimura | Leaf | Wind, Izanagi experiments (Danzō) | Bandages, a cane | Ruthless, secretive | Wind | Root (ANBU black ops) |
| Hoshigaki | Mist | Shark-like body; Samehada; huge chakra | Blue-grey skin, gills, sharp teeth | "A tailed beast without a tail" | Water | Swordsmen of the Mist (Kisame) |
| Might (taijutsu lineage) | Leaf | Eight Inner Gates; pure taijutsu | Bowl cut, thick brows, green jumpsuit | Almost no ninjutsu; endless stamina and heart | — | Taijutsu masters (Duy, Guy; Rock Lee is not family) |

Lee: Rock Lee has no clan; his style comes from Might Guy, so the Might line covers it.

## Canon statements and feats (the deeper pass, owner follow-up 2026-11-18)

The owner asked for much bigger gaps between clans, drawbacks included. This is what canon says about
each clan's strengths and weaknesses, in our own words. Sources are titles only (see the research table).

- **Uzumaki.** Kushina and Mito sealed the Nine-Tails because Uzumaki life force and chakra are
  exceptional. Their chakra chains could hold the Nine-Tails down. Karin's chakra heals whoever bites
  her. The clan was famously long-lived, and their village fell because others feared their sealing
  arts. In Part 2, Kakashi tells Naruto that Naruto's chakra is about **four times** his; the owner's
  reading (adopted, v0.3) is that this was measured while the Nine-Tails' seal held Naruto's own chakra
  down, and his own pool is about **a hundred times** a normal ninja's. Naruto's poor chakra control
  is a running thread: he can't do the basic clone, his chakra overflows, and genjutsu never suits
  him. Sensors (Karin, the Hyūga, the Akatsuki) pick him out from far away.
- **Might (and Rock Lee, who isn't family).** Lee can't mould chakra into ninjutsu or genjutsu at all.
  He makes up for it with relentless taijutsu training, extreme stamina and the will to keep going.
  Guy opens all Eight Gates. Lee opens five as a genin. The Gates tear the body apart.
- **Senju.** Hashirama's chakra and life force are without equal. He heals without hand seals and
  tames tailed beasts with Wood Release. Tsunade's Creation Rebirth comes from the same vitality.
  Their chakra is as vast as the Uzumaki's (distant kin), and they rely on raw power over finesse.
- **Uchiha.** The Sharingan copies techniques and reads movement. Their love turns to hatred when they
  lose someone, and that drives the Mangekyō, which goes blind with use. Fire runs in the clan. The
  village grew to distrust them.
- **Hyūga.** The Byakugan sees nearly all around, through walls, and shows the chakra points. The
  Gentle Fist closes them. They have exquisite chakra control but little elemental ninjutsu, and
  their rigid house rules (main and branch) hold them back.
- **Nara.** Shikamaru's IQ is over 200. The clan is lazy but brilliant, and their shadow techniques
  need fine control and planning. Hard physical work isn't their thing.
- **Akimichi.** They turn calories into chakra, grow huge (Multi-Size, Butterfly Mode), and are very
  tough. They're slow.
- **Yamanaka.** Mind transfer, sensing (Inoichi's Sensor Technique) and medical work. In mind
  techniques the body is left open, and they are lighter fighters.
- **Aburame.** Their bodies are hives of chakra-eating insects from birth. The insects drain, track
  and sense. They're poison-wise, and fire burns the insects.
- **Inuzuka.** Their sense of smell rivals a dog's. They fight beside a ninken (Akamaru) with
  Fang Over Fang. They're wild, fast and impulsive.
- **Hatake.** Sakumo was the White Fang. Kakashi is a genius who mastered a thousand jutsu. He often
  collapses after battle, but that's the Sharingan implant: his own reserves are average.
- **Sarutobi.** Hiruzen, "the Professor", knew every jutsu in the village and led it the longest. The
  family teaches and leads, and carries the Will of Fire.
- **Kaguya.** Kimimaro turns bones into blades and armour that are nearly unbreakable. He died of an
  incurable illness. The clan was bloodthirsty.
- **Hōzuki.** Hydrification turns the body to water: blows pass through. They need water to stay
  strong and are weak to lightning.
- **Yuki.** Haku's Ice Release and mirrors. They're incredibly fast and precise, and frail.
- **Chinoike.** The Ketsuryūgan: blood genjutsu and blood control. They were feared and exiled.
- **Kurama (the house of Yakumo).** Genjutsu so strong they can hurt in reality. Their bodies are
  weak.
- **Jūgo's clan.** They soak up nature energy (the origin of the curse mark). Their bodies regenerate
  by taking others' cells, and they have murderous fits they can't control.
- **Hoshigaki.** Kisame is "a tailed beast without a tail": immense chakra, Samehada, a shark body.
  People feared him.
- **Yotsuki.** Cloud fighters with strong bodies and lightning. (Calling them the Raikage line is fanon.)
- **Kazekage line.** Magnet Release (Iron Sand, Gold Dust) and a sand shield. Strong defence, slower
  bodies.
- **Kurogane.** Samurai of the Land of Iron: swordsmanship and posture, no ninjutsu.
- **Shimura.** Danzō: wind, an iron will, and the Root's reputation. Ruthless and distrusted.
- **Iburi.** Smoke bodies that blows pass through. They're fragile, reclusive, and scattered by wind.
- **Kamizuru.** Bee swarms that sense, sting and poison. They're rivals of the Aburame. Fire burns
  the bees.

### How the "100×" maps to NOIRM

NOIRM measures chakra as a pool that grows with Reserves (100 at the start, +5 per Reserves point).
A pool **doesn't make each jutsu stronger**. Jutsu power follows the Reserves *stat*, mastery and the
nature. The pool is how long you can keep going. That's exactly how canon treats Naruto: his jutsu
aren't stronger per cast, but he can make hundreds of shadow clones and never runs dry.

_Updated for v0.3 (the owner's ruling: the Uzumaki are simply that overpowered)._

- An **Uzumaki has ×100 the pool of a normal shinobi at the same Reserves** (10,000 at the start
  against 100, 20,000 against 200 at Reserves 30). **An Uzumaki jinchūriki has ×150.**
- **Regeneration grows with the pool.** Chakra regen is multiplied by the blood's pool multiplier, so
  every pool refills from empty in the same time; `chakra_regen` then makes it faster. A normal ninja
  refills in 100 s at the start, an Uzumaki (×3 regen) in about 33 s: 300 chakra a second.
- **Training stays fair.** Chakra spent trains Reserves divided by √(pool multiplier) (an Uzumaki: ÷10),
  so a pool that never runs dry doesn't train Reserves, and so jutsu power, a hundred times faster.
- **Shadow clones.** Each tenfold of pool adds 2 clones per cast and to the cap (an Uzumaki: 2 + 4 per
  cast, up to 8; a Senju ×40: +3). Chakra is still split between you and the clones.
- **The HUD** shows big pools as "12.4k/20k"; `/noirm me` writes them out ("9,876/10,000").
- **NPC ninjas** of a big-pool clan get their clan's pool capped at ×3 (`Perks.NPC_POOL_CAP`): an Uzumaki
  ninja casts more often than others but still runs dry.
- **A Hatake has ×1** now (Kakashi's own reserves are average).
- The price stays: **rough control** (jutsu cost up to 40% more and genjutsu lose up to 80% at low CTL,
  fading out by CTL 50) and a **loud chakra** (wild ninjas find them twice as often, mobs and beasts
  notice them from twice as far).

## Round 3 (v0.3): canon-strength clans, canon clans as the normal roll, more rolls

_Owner's ask, 2026-11-25 (`WAVE_V03.md` items 3–5): simple boosts (stats and multipliers only) so every
clan feels as strong as in canon; the great clans very strong, the Uzumaki "simply that overpowered".
Canon costs stay. The deep mechanics (`CLAN_RESEARCH.md` Part II) come after v0.3. The Aburame are left
to their own wave (`ABURAME.md`)._

### Old → new numbers

×N is the chakra pool against a normal shinobi at the same Reserves. Two new perks: **nature training**
(`nature_xp`, nature drills move faster) and **Sharingan growth** (`sharingan_xp`).

| Clan | What changed (old → new) | Canon costs kept |
|---|---|---|
| **Uzumaki** | pool ×10 → **×100**; jinchūriki ×20 → **×150**; regen ×3 now on a pool-scaled base (refill 33 s); healing 0.35 → 0.5 HP/s; health +6 → +10; VIT +2 → +4; vitality training +30% (new); DoT resistance 50% → 60%; sealing power ×2 → ×2.5, sealing cost −50% → −60%; beast bond ×2 → ×2.5 | rough control 40%, CTL −4, control training −25%; loud chakra +75% → +100% |
| **Senju** | pool ×4 → **×40**; health +14 → +20; healing 0.4 → 0.6 HP/s; stamina +100 → +150, stamina regen +60% → +100%; VIT +6 → +8, vitality training +50% → +80%; medical +50% → +80%; beast bond +30% → +50%; **nature training ×2** (new: "a thousand skills"); jutsu mastery +30% (new); **rough control and CTL −3 removed** (not canon) | loud chakra +40% → +50% |
| **Uchiha** | pool ×1.5 → ×3; **fire affinity 1.5 → 2.0**; perception +25% → +40%; genjutsu +60% → **+100%**; genjutsu resistance +30% → +40%; Resolve +60% → +80%; mastery +40% → +60%; jutsu power +10% → +20%; **Sharingan growth +50%** (new) | Mangekyō strain +60%, crew respect −15%, health −2 |
| **Kaguya** | DUR +10 → **+15**; health +8 → +12; damage taken −15% → **−25%**; melee +25% → **+40%**; durability training +60% → +100%; taijutsu training +50% (new); posture +40 → +60 | the illness: DoT +50%, −0.05 HP/s; jutsu power −20% |
| **Hyūga** | Gentle Fist +80% → +100%; CTL +5 → +6; taijutsu training +50% → +60%; perception +15% → +30%; genjutsu resistance +25% → +30%; melee +10% (new) | jutsu power −30%, mastery −20% |
| **Might** | TAI +4 → +5; stamina +150 → +200 | jutsu power −60%, cost +60%, genjutsu −80%, pool ×0.5 |
| **Jūgo's clan** | pool ×2 → ×4; health +10 → +14; melee +30% → +35%; healing 0.3 → 0.4 HP/s | CTL −6, rough control 30%, genjutsu resistance −50%, crew −30% |
| **Hoshigaki** | pool ×6 → **×30** ("a tailed beast without a tail"); melee +20% → +25%; health +6 → +8 | rough control 25%, crew −30%, loud chakra +50% |
| **Kazekage line** | pool ×1.5 → ×3; DUR +6 → +8; posture +40 → +50; jutsu power +15% → +20%; damage taken −10% → −15% | stamina −30, speed training −20% |
| **Yuki** | CTL +4 → +5; SPD +2 (new); speed training +40% → +50%; jutsu power +15% → +25%; perception +15% → +20% | health −4, fire +30% |
| **Chinoike** | genjutsu ×2 → ×2.2; perception +20% → +30% | health −4, strain +30%, crew −30% |
| **Kurama** | genjutsu ×2.2 → ×2.5 | health −6, stamina −50, melee −15% |
| **Nara** | CTL +6 → +7; genjutsu resistance +20% → +30% | taijutsu −40%, speed −30%, stamina −30, stamina regen −20% |
| **Akimichi** | health +12 → +14; melee +20% → +25%; DUR +6 → +7 | SPD −4, speed training −35% |
| **Hatake** | **pool ×0.6 → ×1, regen −20% removed** (average reserves); mastery +60% → +70% | none (canon has none) |
| **Sarutobi** | pool ×1.5 → ×2; mastery +50% → +60%; jutsu power +25% → +30%; nature training +50% (new: "the Professor") | speed training −20% |
| **Hōzuki** | pool ×1.5 → ×2 | health −4 |
| **Yamanaka** | sensing 40 → 48 blocks; genjutsu +60% → +70% | health −3, melee −10% |
| **Inuzuka** | melee +20% → +25%; SPD +4 → +5 | CTL −3, control training −30%, genjutsu −40% |
| **Yotsuki** | health +8 → +10; melee +20% → +25% | CTL −3, genjutsu −30% |
| **Shimura** | WIL +6 → +7 | crew −40% |
| Aburame, Fūma, Iburi, Kamizuru, Kurogane | unchanged (Aburame: its own wave) | |

**Not in v0.3:** longevity (there is no ageing yet), lightning weakness for the Hōzuki, the Uzumaki's
chains and bite, and every other Part II mechanic.

### The clan roll table (`data/noirm/clan_odds/canon.json`)

80% of every roll (players' creator rolls, NPC and wild ninjas, exam opponents, bingo-book targets) is a
canon clan; 20% is a generated clan (half of those a clan this world already knows, half brand new).
The weights are per thousand rolls, so each one reads as a share of all rolls:

| Group | Clans (share of all rolls) | Sum |
|---|---|---|
| Great clans | Uchiha 3.5%, Hyūga 3.5%, Senju 2.5%, Uzumaki 2.5%, Kaguya 2.0% | 14% |
| Leaf noble and common clans | Nara 6%, Akimichi 6%, Yamanaka 6%, Inuzuka 6%, Aburame 5%, Sarutobi 4%, Hatake 2.5%, Might 2% | 37.5% |
| Other villages and rare lines | Yotsuki 3.5%, Fūma 3.5%, Hōzuki 3%, Kamizuru 3%, Kurogane 3%, Shimura 2.5%, Yuki 2%, Kurama 1.5%, Iburi 1.5%, Kazekage line 1.4%, Chinoike 1.2%, Hoshigaki 1.2%, Jūgo's clan 1.2% | 28.5% |
| Generated clans | | 20% |

Why: the common Leaf clans are big families, so they're the usual roll; the great clans are rare but
reachable (about 1 roll in 30–50 each, so the 5 creator rolls give about an even chance of seeing at
least one of the five). Hunted, exiled or near-extinct lines (Yuki, Chinoike, Hoshigaki, Jūgo's,
Kaguya) and the tiny Kazekage family are the rarest.

### Rolls in the creator

- **5 clan rolls** in all (the first, then 4 rerolls; it was 1 + 3). Each shows a new clan, stats and talents.
- **5 talent rerolls**, apart from the clan rolls. Each gives the roll you're keeping a new set of
  talents; its clan and stats stay. Every set stays too: the arrows on the card (and on the Clan tab)
  switch back to any set you've seen. Why five: a set has a negative talent about 1 time in 6, so five
  rerolls almost always shake it off; seeing a four-talent set or one particular talent is about a coin
  flip (54% and 42%), so you can hope for luck but not fish for a perfect set.
- The Genetics tab shows two counters ("Clan rerolls: 4 of 4 left", "Talent rerolls: 5 of 5 left") and
  two buttons; the Clan tab shows the talent rerolls left beside your talents.

### Simulator, before and after round 3 (hours of play; `--clans`, 60 h)

| Clan | Chūnin before → after | Jōnin before → after | Class S before → after | Beat class S before → after | Fight × (player) |
|---|---|---|---|---|---|
| no clan | 10.2 | 20.7 | 51.0 | 53.6 | 1.00 |
| Uzumaki | 7.3 → 5.6 | 15.0 → 11.4 | 41.4 → 35.0 | 39.6 → 31.6 | 1.10 → 1.15 |
| Senju | 4.9 → 3.3 | 9.9 → 7.2 | 33.4 → 27.4 | 29.6 → 21.6 | 1.21 → 1.29 |
| Kaguya | 3.6 → 2.5 | 8.1 → 4.4 | 32.2 → 23.0 | 25.6 → 15.6 | 1.28 → 1.49 |
| Uchiha | 8.9 → 8.3 | 18.4 → 17.4 | 49.4 → 47.0 | 51.6 → 46.4 | 1.02 → 1.06 |
| Hyūga | 8.7 → 7.6 | 18.8 → 17.0 | 47.4 → 44.2 | 51.6 → 46.4 | 0.96 → 0.99 |
| Jūgo's clan | 4.7 → 3.6 | 9.5 → 7.3 | 32.6 → 29.8 | 27.6 → 23.6 | 1.25 → 1.33 |
| Akimichi | 4.7 → 4.1 | 10.3 → 8.9 | 38.2 → 34.2 | 29.6 → 25.6 | 1.28 → 1.33 |
| Hoshigaki | 6.3 → 5.6 | 12.1 → 11.2 | 37.4 → 37.5 | 33.6 → 30.4 | 1.16 → 1.21 |
| Yotsuki | 5.4 → 4.7 | 11.9 → 10.3 | 37.8 → 33.4 | 34.4 → 29.6 | 1.17 → 1.21 |
| Kazekage line | 8.1 → 7.3 | 16.9 → 15.2 | 47.0 → 44.2 | 45.6 → 41.6 | 1.07 → 1.11 |
| Yamanaka | 10.2 → 10.9 | 22.6 → 22.3 | 55.7 → 54.2 | — → 59.6 | 0.96 → 0.97 |
| Kurama | 11.6 → 11.2 | 25.0 → 23.2 | 58.2 → 55.4 | — → — | 0.93 → 0.95 |

The model is melee-centred and can't see pools, genjutsu, sensing, sealing or the eyes, so the Uchiha
and the Uzumaki play far stronger than this table says. Every clan still reaches every rank and beats
class S (Kurama past 60 h). **NPC mix:** with 80% canon NPCs the average NPC fights ×1.04 (was ×1.00 at
10% canon), because NPC combat perks count at half.

## What each clan does in NOIRM (the first and second waves, old → new)

"Start" stats are added once when the character is made. ×N is the chakra pool against a normal
shinobi at the same Reserves.

| Clan | Before (first wave) | Now: strengths | Now: drawbacks |
|---|---|---|---|
| **Uzumaki** | chakra +60 then ×1.35, regen +60%, healing 0.15/s, health +2, DoT −30%, strain −35%, pills −30%, sealing +50% / cost −35%, bond +50%; CTL −2 | **chakra ×10 (×20 as a jinchūriki)**, regen ×3, healing 0.35 HP/s, health +6, VIT +2, Reserves training +50%, DoT −50%, strain −60%, pill toxicity −50%, sealing ×2 power / −50% cost, beast bond ×2, **Flying Raijin gift** (formula and jump ask 10 fewer CTL/Reserves, +4 marks, jumps −30% chakra, −20% cooldown) | **rough control 40%** (jutsu +40% cost, genjutsu −80% at low CTL, gone at CTL 50), CTL −4, control training −25%, **loud chakra +75%** |
| **Might** (Lee/Guy line) | taijutsu training +50%, stamina +50, stamina regen +40%, melee +12%, DUR +2, Resolve +30%; jutsu power −30%, cost +20% | **taijutsu training ×4**, speed training +50%, **willpower training ×2, WIL +8**, TAI +4 (+3 clan bonus), **stamina +150, stamina regen ×2**, melee +30%, DUR +4, Resolve +80%, **Gate mastery 50%** (the Gates burn half the health and close at half the line), born knowing the Gate of Opening | **jutsu power −60%, jutsu cost +60%, genjutsu −80%**, chakra ×0.5, no nature at all |
| **Senju** | health +6, healing 0.2/s, stamina +40, stamina regen +30%, chakra regen +25%, medical +20%, Wood +0.25, VIT +3; CTL −2, cost +5% | **chakra ×4**, chakra regen ×2, **health +14**, healing 0.4 HP/s, stamina +100, stamina regen +60%, VIT +6, vitality training +50%, medical +50%, Wood/combinations +0.5 affinity, beast bond +30% | rough control 25%, CTL −3, loud chakra +40% |
| **Uchiha** | perception +10%, genjutsu +25%, Resolve +25%, mastery +15%; strain +25% | perception +25%, **genjutsu +60%**, genjutsu resistance +30%, Resolve +60%, **mastery +40%**, chakra ×1.5, jutsu power +10% | **Mangekyō strain +60%**, crew respect −15%, health −2 |
| **Hyūga** | Gentle Fist +25%, CTL +2, cost −10%, taijutsu training +15%; jutsu power −10% | **Gentle Fist +80%**, CTL +5, control training +50%, jutsu cost −20%, taijutsu training +50%, perception +15%, genjutsu resistance +25% | **jutsu power −30%**, mastery −20% |
| **Nara** | CTL +3, control training +40%, mastery +30%, WIL training +15%; taijutsu training −20%, stamina −10 | **CTL +6, control training ×2**, mastery +60%, willpower training +40%, WIL +3, genjutsu resistance +20% | taijutsu training −40%, speed training −30%, stamina −30, stamina regen −20% |
| **Akimichi** | food +60%, health +6, DUR +3, posture +20, stamina +20, melee +8%; SPD −2 | **food ×2.5**, health +12, DUR +6, posture +50, stamina +60, melee +20%, chakra ×2 (calories), damage taken −5% | **SPD −4, speed training −35%** |
| **Yamanaka** | perception +10%, sensing 24, genjutsu +25%, genjutsu resistance +20%, WIL +2; health −2 | perception +20%, **sensing 40 blocks**, genjutsu +60%, genjutsu resistance +40%, WIL +4, willpower training +40%, medical +20% | health −3, melee −10% |
| **Aburame** | poison-immune, DoT −30%, sensing 16, insects drain 3, CTL +1; fire +25% | poison-immune, **DoT −60%**, sensing 32, **insects drain 8 chakra per hit**, CTL +3, chakra ×1.3 | **fire damage +60%**, speed training −20% |
| **Inuzuka** | hound, smell 20, SPD +2, speed training +20%, melee +8%; CTL −1 | hound (unchanged hook), **smell 40 blocks**, SPD +4, speed training +50%, melee +20%, perception +10% | CTL −3, control training −30%, genjutsu −40% |
| **Hatake** | mastery +25%, SPD +2, perception +5%, melee +5%, hound; chakra −10 | **mastery +60%**, SPD +3, speed training +30%, perception +15%, melee +10%, hound (unchanged hook) | **chakra ×0.6, regen −20%** (Kakashi's small reserves) |
| **Sarutobi** | mastery +20%, jutsu +8%, crew respect +25%, Resolve +20%, WIL training +15% | mastery +50%, jutsu power +25%, **crew respect +60%**, Resolve +40%, willpower training +40%, chakra ×1.5 | speed training −20% |
| **Kaguya** | DUR +6, health +4, damage taken −8%, melee +12%, DUR training +25%; DoT +20% | **DUR +10**, health +8, damage taken −15%, melee +25%, durability training +60%, posture +40 | **DoT +50% (the illness)**, loses 0.05 HP/s, jutsu power −20% |
| **Hōzuki** | 60% liquid body near water, chakra +20, DoT −15%, speed training +10%; health −2 | **75% liquid body** near water, chakra ×1.5, DoT −30%, speed training +20% | health −4 |
| **Yuki** | Ice, CTL +2, perception +5%, speed training +15%, DoT −10%; health −2 | Ice, CTL +4, **speed training +40%**, perception +15%, DoT −20%, jutsu power +15% | health −4, fire +30% |
| **Chinoike** | genjutsu +40%, perception +10%, genjutsu resistance +15%; health −2, strain +10% | **genjutsu ×2**, perception +20%, genjutsu resistance +40%, WIL +3 | health −4, strain +30%, crew respect −30% |
| **Kurama** | genjutsu +50%, genjutsu resistance +30%, WIL +2; health −4, stamina −20 | **genjutsu ×2.2**, genjutsu resistance +60%, WIL +5 | **health −6, stamina −50**, melee −15% |
| **Fūma** | melee +6%, perception +5%, SPD +1, taijutsu training +10% | melee +15%, perception +10%, SPD +2, taijutsu training +25% | jutsu power −10% |
| **Iburi** | 25% smoke body, genjutsu resistance +10%, speed training +10%; health −4 | **40% smoke body**, genjutsu resistance +20%, speed training +20% | health −6, damage taken +10% |
| **Jūgo's clan** | health +6, melee +15%, healing 0.15/s, taijutsu training +20%; CTL −3, genjutsu resistance −20% | health +10, melee +30%, healing 0.3 HP/s, taijutsu training +50%, chakra ×2 (nature energy) | **CTL −6, rough control 30%, genjutsu resistance −50%**, crew respect −30% |
| **Kamizuru** | sensing 12, DoT −20%, pills +10%, jutsu +5%; crew −10% | sensing 24, DoT −40%, pills +25%, jutsu power +10% | crew respect −15%, fire +30% |
| **Yotsuki** | health +4, melee +12%, DUR +2, stamina +20; CTL −1 | health +8, melee +20%, DUR +5, stamina +50, taijutsu training +30% | CTL −3, genjutsu −30% |
| **Kazekage line** | Magnet, DUR +3, posture +20, jutsu +5%; stamina −10 | Magnet, DUR +6, posture +40, jutsu power +15%, chakra ×1.5, damage taken −10% | stamina −30, speed training −20% |
| **Kurogane** | melee +15%, posture +30, DUR +2, taijutsu training +20%; jutsu −15% | **melee +40%, posture +60**, DUR +5, taijutsu training +40%, genjutsu resistance +20% | **jutsu power −40%, cost +30%** |
| **Shimura** | genjutsu resistance +15%, WIL +3, cost −5%; crew −20% | genjutsu resistance +30%, WIL +6, willpower training +40%, jutsu cost −10% | crew respect −40% |
| **Hoshigaki** | chakra +80 then ×1.25, melee +10%, health +2; crew −15% | **chakra ×6**, chakra regen ×2, melee +20%, health +6, Reserves training +30% | rough control 25%, crew respect −30%, loud chakra +50% |

Old clan traits (Keen Eyes, Deep Reserves…) of the Uchiha, Hyūga, Senju and Uzumaki still apply on top.
The Inuzuka and Hatake `hound` perk hooks are unchanged; a separate helper is building the growing ninja
hound that will use them.

### How it stays playable

- **Pools are sustain, not power.** Nothing in NOIRM scales damage with max chakra. Jutsu power
  follows the Reserves stat and mastery, and chakra reinforcement follows (Reserves + CTL) / 2.
  Both are stats the clan perks don't touch (only the Uzumaki and Hoshigaki train Reserves a little
  faster). The HUD shows the pool as a number and a bar, and big pools read as "12.4k".
- **Raw combat perks are capped by design** (melee, health, damage taken). The strongest bruisers
  fight about 1.2–1.5× as well as a clanless player of the same stats (the Kaguya, since v0.3, ~1.5×). The big gaps are in
  identity: pools, training speed, control, genjutsu, the Gates, the eyes.
- **NPC ninjas** of a clan count their raw combat perks (health, healing, damage dealt and taken,
  jutsu and genjutsu power) at **half** (`Perks.NPC_COMBAT_SHARE`). Their identity perks (liquid and
  smoke bodies, poison immunity, resistances, weaknesses) count in full. So a Kaguya ninja feels like
  a Kaguya without beating every exam's difficulty, and since v0.3 most NPCs (80%) are canon-clan ninjas.
- **The simulator** (`tools/balance/simulate.py --clans`) plays the same normal player born into each
  clan; see the table below.

### Simulator, before and after (hours of play; `--clans`, 60 h)

The model is melee-centred (power weighs taijutsu and speed). It can't see genjutsu, sensing or
sealing, so the genjutsu clans look slower here than they play.

| Clan | Chūnin before → after | Jōnin before → after | Class S before → after | Beat class S before → after |
|---|---|---|---|---|
| no clan | 10.2 | 20.7 | 51.0 | 53.6 |
| Uzumaki | 8.9 → 7.3 | 18.3 → 15.0 | 47.4 → 41.4 | 48.4 → 39.6 |
| Might | 8.7 → 6.3 | 18.3 → 13.9 | 46.2 → 34.9 | 49.6 → 41.6 (TAI 109 → 152 at 60 h) |
| Senju | 7.6 → 4.9 | 15.2 → 9.9 | 43.0 → 33.4 | 41.6 → 29.6 |
| Kaguya | 6.0 → 3.6 | 12.5 → 8.1 | 38.6 → 32.2 | 35.6 → 25.6 |
| Jūgo's clan | 5.8 → 4.7 | 12.6 → 9.5 | 38.6 → 32.6 | 35.6 → 27.6 |
| Akimichi | 7.1 → 4.7 | 14.6 → 10.3 | 41.0 → 38.2 | 38.4 → 29.6 |
| Hyūga | 8.9 → 8.7 | 19.8 → 18.8 | 51.4 → 47.4 | 55.6 → 51.6 |
| Uchiha | 9.4 → 8.9 | 19.8 → 18.4 | 50.2 → 49.4 | 53.6 → 51.6 |
| Nara | 10.2 → 10.2 | 19.6 → 21.0 | 51.0 → 54.3 | 53.6 → 54.4 |
| Kurama | 12.1 → 11.6 | 24.9 → 25.0 | 57.8 → 58.2 | — → 65.6 (90 h run) |
| Yamanaka | 11.2 → 10.2 | 22.6 → 22.6 | 53.4 → 55.7 | 58.4 → 61.6 (90 h run) |

Every clan still reaches every rank and beats class S. No walls or dead ends. The bruisers (Kaguya,
Jūgo, Senju, Akimichi, Yotsuki) climb about twice as fast in this model. The frail genjutsu clans
are about 20% slower, before counting the genjutsu and sensing the model can't see.

### Clan jutsu (12, plus the Gate of Opening the Might line is born knowing)

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
  Raw combat perks (health, healing, damage dealt and taken, jutsu and genjutsu power) count at half
  for NPCs, talents included (`Perks.NPC_COMBAT_SHARE`).
- They roll personal talents too (saved on the entity), except on the game-test server.

## Tuning

- Clan perks, clan jutsu, looks, biases: `data/noirm/clans/*.json`.
- Which clans newcomers roll: `data/noirm/clan_odds/canon.json` (`canon_share`, weights per thousand).
- Creator rolls: `CharacterCreator.MAX_ROLLS` (5) and `MAX_TALENT_REROLLS` (5).
- Big pools: regen scales with the pool (`ShinobiData.chakraRegenMultiplier`), Reserves XP per chakra
  spent ÷ pool^`TrainingSystem.SPEND_XP_POOL_EXPONENT` (0.5), clones `ShadowClone.BONUS_PER_TENFOLD` (2,
  at most `MAX_BONUS` 4), NPC pools `Perks.NPC_POOL_CAP` (×3).
- Packages: `data/noirm/clan_perks/*.json`; two per clan: `Talents.PACKAGES_PER_CLAN`.
- Sealing scroll numbers: constants at the top of `SealingScrollItem`.
- Caps: `Perks.MIN_MULTIPLIER` (0.1), `Perks.MAX_DODGE` (0.8).
- Rough control: `Perks.ROUGH_FULL_BELOW` (10) and `ROUGH_GONE_AT` (50), genjutsu ×`ROUGH_GENJUTSU` (2).
  NPC share: `Perks.NPC_COMBAT_SHARE` (0.5). The Uzumaki's Flying Raijin gift: constants in `traits/ClanHiraishin`.
- Compare clans: `python3 tools/balance/simulate.py --clans` (and `--clan <id>` for one clan's full timeline).
- NPC looks: `tools/skingen/gen_parts.py` (`CLANS`), then `python3 tools/skingen/gen_parts.py --looks-only`.
