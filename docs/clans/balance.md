# Clan research: candidates, power tiers and balance

## Clans we lack (candidates)

| Clan | Canon (source) | Why add it | Mechanic idea `[ours]` | Tier |
|---|---|---|---|---|
| **Tsuchigumo** | Anime (S-ep. 144–151): a proud clan whose leader En no Gyōja made a **village-destroying forbidden technique**; a pact with the Third Hokage sealed it in exchange for protection; split into two groups | A "forbidden technique" clan that guards a doom weapon | A sealed kinjutsu scroll the clan must protect; a mountain fort structure with a barrier and traps; the technique as a one-use map-scale event | B |
| **Karatachi / Tachibana** | Kiri family: Yagura (Fourth Mizukage, Three-Tails host, "Bloody Mist"); Kagura (B-ep. 28; novel says the true name was Tachibana) | A jinchūriki dynasty and the Bloody Mist's guilt | Born-jinchūriki odds ×5 for the Three-Tails; Kiri respect penalty; Hiramekarei-style twin-sword heirloom | B |
| **Hōki family** | *Gaara Hiden* `[novel]`: a matrilineal Suna family of medics and information gatherers who work behind the scenes | A support clan for Suna | Medical +40%, poison craft, information network (reveal map targets) | C |
| **Shirogane** | Anime (S-ep. 290s, "Power" arc `[unverified]`): a weak Wind-border clan that built **puppet walkers** from abducted humans; sealed their Master Puppet in five Spirit Orbs; annihilated after a rebellion | A puppet clan (Suna's art is otherwise missing) | Puppets that act without a puppeteer nearby; the five Spirit Orbs as a quest; dark-secret reputation | B |
| **Funato** | *Boruto* anime (B-ep. 233–250): Land of Water pirates, one of the country's oldest clans, raids and kidnapping | Sea and island content (our worldgen has oceans) | Ship structures, sea raids, +water mobility; hostile NPC faction | C |
| **Kohaku** | Anime (Twelve Guardians arc): a clan village on the Fire/Rain border massacred by Furido's team; yin-yang arm tattoo | A tragic ruin for exploration | Ruined village structure with revenant clansmen | C |
| **Taketori** | Anime (S-ep. 390): politically close to the Hyūga; *Boruto*'s Hōki Taketori | A noble family tied to the Hyūga | Political marriage events with the Hyūga | C |
| **Kedōin** | Anime (Part I filler): copy anyone's appearance perfectly, even to ninken noses, for a few days | Infiltration | Disguise as a specific NPC for missions | C |

**Ōtsutsuki (bosses, not playable).** Kaguya (Byakugan, Rinne Sharingan, All-Killing Ash Bones,
dimension hopping), Hagoromo (Rinnegan, Six Paths), Hamura (Byakugan, moon), and the *Boruto* invaders.
Use them as the endgame: each boss drops a heritage relic (an Ash Bone that cures Bone Fever, a Byakugan
fragment for the Tenseigan, a Six Paths staff).

---

## Power tiers and balance

## The ranking

| Tier | Clans | Why (canon) |
|---|---|---|
| **S** | Uchiha, Senju, Uzumaki, Kaguya | Ōtsutsuki bloodlines whose best members define eras: Madara and Hashirama, a Sharingan line that ends in the Rinnegan, Uzumaki life force that survives beast extraction and wields the Rinnegan without drawback, Shikotsumyaku as "the ultimate taijutsu" that even its own clan feared. |
| **A** | Hyūga (A+), Might, Jūgo's clan, Hoshigaki, Kazekage line, Yuki, Chinoike, Kurama | One member can fight a squad: the "strongest clan in Konoha", the Eight Gates (Night Guy nearly killed Madara), the curse-mark source, a tailed beast without a tail, the strongest Kazekage, light-speed mirrors, a blood-controlling eye, genjutsu that kills. |
| **B** | Nara, Akimichi, Aburame, Hatake, Sarutobi, Hōzuki, Yamanaka, Inuzuka, Yotsuki, Shimura, Iburi | Strong specialists; win with planning, partners or tools. Hatake and Sarutobi have S-level individuals (Kakashi, Hiruzen), but from training, not blood. |
| **C** | Kamizuru, Kurogane, Fūma, and most candidates | A trade, a weapon or a trick. |

## How each tier balances (the owner's rule: power stays big; unlock and cost carry the balance)

| | S | A | B | C |
|---|---|---|---|---|
| Ladder stages | 5 | 4 | 3 | 2 |
| Time to the top (guide) | 40–80 h | 20–40 h | 10–20 h | 5–10 h |
| Gates on the ladder | trauma or crisis triggers, rare items (eyes, cells, scrolls), a master, structures to find | a trigger and training | training and a clan item | an item or quest |
| Luck at birth | yes (1 in 10 Wood Blood, 1 in 3 Bone Pulse, 60% Kagura chakra, Mangekyō by trauma) | some (Hyūga house) | rare sub-rolls (rinkaichū) | none |
| Permanent costs | yes: blindness, blind eyes per kinjutsu, lifespan (cell divisions), Bone Fever, Gate 8 death | soft: frenzy, exile, hunters | situational weaknesses | none |
| Hunted ("heat") | yes (Uzumaki, Uchiha eyes, Kaguya revenants) | Yuki, Hyūga main house, Chinoike exile | — | — |

Two more rules keep the S tier fun instead of mandatory:
- **Early game is not S-tier.** At creation an Uchiha has dormant eyes, a Senju has vitality but
  probably no Wood, an Uzumaki has a big pool but rough control and no chains, a Kaguya has a Camellia
  blade. Their peaks are earned.
- **Counters exist for every peak.** Susanoo doesn't stop genjutsu or sound; Chain Barrier blocks you
  in too; Wood is burned by Fire ×1.5 `[ours]`; the Clematis drill breaks Susanoo ribs; Lightning stops
  Hydrification; Wind kills Iburi; fire scatters insects; the Byakugan has a blind spot.

## Shared systems these designs rely on `[ours]`
1. **Clan ladder** (per clan JSON, `"ladder": [...]`): stages with triggers and rewards, shown in the
   creator's Clan tab and `/noirm clan`. Data-driven so new clans need no code.
2. **Heat** (hunted bloodlines): one hidden number per player, a dawn roll, hunter squads from an
   `encounters/` JSON per clan.
3. **Life-force debt / permanent costs** stored in `ShinobiData` sub-objects (cell divisions, Bone
   Fever, blind eyes), synced through `PowerPackets.SyncPowers`.
4. **Clan places**: one structure per S/A clan in `worldgen/` (Uzushio Ruins, Mask Temple, Senju shrine,
   Valley of the End, Naka Shrine, Bone Graveyard, Hyūga compound, Valley of Hell, Ryūchi Cave, Iburi
   caves, Kamizuru hives).
5. **Clan heirlooms**: White Light Chakra Sabre, living sword, gourd, Enma staff, Fūma shuriken, leg
   weights, Akimichi pills, sealing jars, the Shinigami mask.
6. **Bloodline NPC behaviour**: Kaguya never retreat, Uzumaki seal defeated jinchūriki, Uchiha police
   patrol, Hyūga branch defers to main, Jūgo frenzies, Root hunts eyes.

## Corrections to the current docs (found during this research)
- **100× (owner's ruling, adopted).** Naruto's own chakra is about 100× Kakashi's. The ch. 315 "4×"
  is the **sealed** figure, held down by the Nine-Tails' seal. The design follows the 100× reading:
  Uzumaki start at ×10 and unseal toward ×100 (see "Bold reading: Uzumaki"). The first pass called
  100× an error; that is withdrawn.
- `CLANS.md` research table: Chinoike as "Kagura's line" is wrong; the survivor is **Chino**
  (*Sasuke Retsuden*), and the Chinoike are from a novel and its anime adaptation, not filler.
- Yotsuki as "Raikage line (A, Killer B)" is **fanon**; only Kiyoi is a named Yotsuki.
- Fūma "distant Uchiha kin (games, filler)": now **manga canon** (Boruto vol. 10).
- Hatake chakra ×0.6: Kakashi's own reserves are "average"; the drain came from the implant.
- Senju "rough control": not canon (Hashirama did huge jutsu with one seal); it's a NOIRM trade-off.
- Kazekage line: the family name is unknown; avoid "Sabaku" as a surname.
- Kurama, Iburi, Kamizuru, Kurogane, Tsuchigumo are anime-only; flag them in the creator text.

## Hard to confirm this pass
- Exact chapters for: Night Guy (around ch. 669–670), Kotoamatsukami's ten-year cooldown, Root's tongue
  seal, Danzō's Reverse Four Symbols, Yamato as the lone survivor of 60, Gaara's insomnia, Kushina's
  kidnapping, Jūgo healing Sasuke with his flesh, Haku's one-handed seals. All tagged `[unverified]`.
- The Adamantine Chains' **A-rank** (from DB4 via fan sites; page not checked).
- Kisame's chakra comparison chapters (the article cites ch. 256 and 260 for the 30% clone).
- Episode numbers for anime arcs (Kamizuru ep. 136–141, Kurama ep. 203–207, Iburi S-ep. 349–361,
  Shirogane "Power" arc) are from memory plus the articles; check before quoting in game text.
- Fourth-databook stat tables don't exist, so no official 1–5 numbers for Hashirama, Madara, Kushina,
  Mito, Nagato, Tobirama, Jūgo, Suigetsu, Karin.

---
---

## Part II: Bold reading (second pass)

Each clan below gets: **the maximal reading** (tagged), **bold mechanics** on top of Part I, and an
**ultimate form** (unlock, payoff, cost). Numbers are starting points for tuning. "Pool ×N" always
means "N times a normal shinobi's chakra at the same Reserves".

---


## Part II: the smaller clans (quick bold hooks)

| Clan | Maximal reading | Bold ultimate `[our interpretation]` | Cost |
|---|---|---|---|
| **Shimura** | Madara was shocked they joined Konoha (ch. 625) `[canon]`; Danzō ran ten Izanagi | **Ten Lives**: up to 10 implanted eyes, ten deaths undone in one fight | each eye goes blind; Uchiha hate you |
| **Yotsuki** | "never sell out a comrade" `[canon statement]` | **Oath of Cloud**: while a crew mate lives, you can't drop below 1 health for 10 s | once per fight |
| **Iburi** | smoke that can still touch things `[anime]` | **Smoke Wraith**: possess a whole squad (3) at once | wind kills |
| **Kamizuru** | claimed the First Tsuchikage `[anime]`; **神鶴 "divine crane"** `[name meaning]` | **Queen's Swarm**: a giant queen bee mount whose sting paralyses | fire |
| **Fūma** | **風魔 "wind demon"** `[name meaning]`; Uchiha blood (B-vol. 10) `[canon]` | **Dormant blood**: 1 Fūma in 50 awakens a single Sharingan after a near-death | Uchiha hunters notice |
| **Kurogane** | **黒鉄 "black iron"** `[name meaning]` | **One Cut**: an iaidō draw that cuts any jutsu and any shield in a 10-block line | no ninjutsu ever |
