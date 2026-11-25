# Clan research: canon facts, costs and game-changer mechanics

_Research pass for "Clans, round 3" (owner, 2026-11-25; see `WAVE_STYLES_CLANS.md`). Research only:
nothing here is built yet. The builder should read the summary table, the "How to read this" notes
and the section for the clan being built. Everything is in our own words; no text, art or numbers are
copied from NYDO._

## How to read this

**Source tags** (used everywhere below):

| Tag | Meaning |
|---|---|
| `ch. N` | *Naruto* manga chapter (page if known: `ch. 315 p11`) |
| `B-ch. N` / `B-ep. N` | *Boruto* manga chapter / *Boruto* anime episode |
| `ep. N` / `S-ep. N` | *Naruto* anime episode / *Naruto Shippūden* anime episode |
| `DB1`, `DB2`, `DB3`, `DB4` | Databooks: *Hiden Rin no Sho* (2002), *Tō no Sho* (2005), *Sha no Sho* (2008), *Jin no Sho* (2014) |
| `[anime-only]` | Not in the manga (filler or anime expansion). Still usable, but weaker canon |
| `[novel]` | Light novel (*Itachi Shinden*, *Kakashi Hiden*, *Gaara Hiden*, *Sasuke Retsuden*…) |
| `[game]` | Video game or mobile-game profile. Weakest source |
| `[fanon]` | Commonly believed by fans, not supported by a source we could find |
| `[unverified]` | From the researcher's own knowledge; the chapter was not re-checked this pass |
| `[ours]` | A NOIRM invention where canon is silent (all of section 5 in each clan is ours) |

**Where the facts come from.** Most chapter and databook citations were pulled from the Narutopedia
(naruto.fandom.com) article text, through its public MediaWiki API, which keeps the editors'
footnotes. The 1–5 stat tables come from the same articles, which reproduce databooks 1–3. Our
summaries paraphrase them. A few facts come from the researcher's own knowledge and are tagged
`[unverified]`.

**Numbers in NOIRM terms.** A normal player has 20 health, 100 chakra (+5 per Reserves point),
1 chakra/s regen, 0–100 Resolve and 0–100 Mangekyō strain. "Perk" means a value in
`data/noirm/clans/<id>.json`. "Existing" marks something NOIRM already has (see `CLANS.md`,
`POWERS_DESIGN.md`).

---

## Second pass: the bold reading (owner, 2026-11-25)

The owner asked for the **generous, maximal** reading of every clan: high-end feats, what others say
about the clan, rumours inside the story, implications, author comments, and what the names mean.
That reading is the design fuel for the **"Part II: Bold reading"** at the end of this file. It has one
section per clan, each with an **ultimate form**: how to unlock it, what it costs, and a huge payoff.
Extra tags for that part:

| Tag | Meaning |
|---|---|
| `[canon statement]` | Said or shown in a source (chapter, databook, episode) |
| `[implication]` | Not said outright, but follows from canon facts |
| `[name meaning]` | What the clan's name or kanji means, used as a theme |
| `[our interpretation]` | A bold reading or invention of ours |

**The rules of the bold pass.** Canon costs stay: blindness, illness, the Gates, madness, being
hunted. Payoffs become world-scale at the top: terrain-changing ultimates, ×100 pools, cheating death,
armies, beasts. Ultimates sit behind the hardest gates in the game (rare triggers, endgame relics,
bosses) and carry permanent prices, so nobody gets them by accident.

---

## Summary table

Tiers: **S** = world-changing (canon "god-tier" bloodlines). **A** = can win a fight alone against
most ninja. **B** = strong specialists who win with planning or teamwork. **C** = flavour clans whose
edge is a tool, a trick or a trade. The tiers balance through **unlock difficulty** and **cost**, not
through weaker power (see section "Power tiers and balance" at the end).

| Clan | Tier | Kekkei genkai / hiden | Signature game changers (proposed) | Canon costs → NOIRM costs | Unlock |
|---|---|---|---|---|---|
| **Uchiha** | S | Sharingan → Mangekyō → Eternal → (Rinnegan with Senju cells); Susanoo; Fire | Existing eye path, plus: fire coming-of-age rite, Izanagi/Izanami as eye-sacrificing kinjutsu, Curse of Hatred meter, tailed-beast control, Naka Shrine tablet, Rinnegan endgame | Blindness, eye loss per kinjutsu, Susanoo cell pain, hatred spiral, eye hunters, village distrust | Very hard (trauma triggers, transplants, Senju cells) |
| **Senju** | S | Wood Release (very rare), immense life force; medical, Sage Mode affinity | **Master of all things**: every nature at 1.0, invent jutsu (Part II); Wood that builds houses, forests and bridges; tame and command tailed beasts; Strength of a Hundred seal + Creation Rebirth; seal-less healing; instant Sage Mode | Wood is near-unique (1 roll in 10); Creation Rebirth burns lifespan; cells kill most outsiders | Very hard (Wood trial, seal charging takes days) |
| **Uzumaki** | S | Life force, longevity, fūinjutsu, Adamantine Chains (some members), healing bite (Karin), Mind's Eye of the Kagura (rare) | Own pool **unsealed in 7 stages, ×10 → ×100** (Part II); Chains that bind, wall off an area and pin tailed beasts; sealing jars; a jinchūriki who never loses control; heal an ally by letting them bite you; surviving killing blows | Hunted ("too dangerous to live", their village was razed), rough control, loud chakra, bite drain once per day | Hard (chains awaken in crisis; barrier needs sealing mastery) |
| **Kaguya** | S | Shikotsumyaku (rare in the clan) | A body that is an armoury: five clan "dances" (blade, spikes, whip, drill, bone forest), steel-hard bones, fortress body | Terminal illness that grows with use, bloodlust that won't retreat, only 1 member in 3 carries it | Hard (find the five dance scrolls) |
| **Hyūga** | A+ | Byakugan, Gentle Fist (Eight Trigrams), Caged Bird Seal | Huge sight range that grows (40 → 160 blocks), the neck blind spot, tenketsu sniping, main vs branch house, Rotation reinvention, late Tenseigan quest | Blind spot, weak at range, branch seal (main house can punish, eyes sealed on death), eye thieves | Medium (born with the eye; range and Rotation are trained) |
| **Might** (Lee line merged) | A | Eight Gates (taught, not inherited), taijutsu | Gate-by-gate unlock trials; leg weights; Night Guy as one 30 s god-mode that kills or cripples | Body tears at gates 4–7, gate 8 kills; no ninjutsu or genjutsu | Hard (gates need a master and pain trials) |
| **Jūgo's clan** | A | Natural-energy absorption, Sage Transformation | Shape-shifting limbs (piston, axe, boosters, cannons), bird scouts, cell gifting, curse-mark crafting | Uncontrollable frenzy when nature energy fills; needs a "calmer" | Medium |
| **Hoshigaki** | A− | Shark body, immense chakra, water from chakra | Make a sea anywhere; gills; living chakra-eating sword that can betray you | Feared and unwelcome; the sword picks the strongest chakra | Medium |
| **Kazekage line** | A− | Magnet Release (Iron Sand, Gold Dust), sand | Mineral reservoir in a gourd, auto sand shield, magnetise iron gear, sand flight | Slow, heavy, low stamina; a jinchūriki tradition with insomnia | Medium |
| **Yuki** | A− | Ice Release; Demonic Mirroring Ice Crystals | Ice-mirror domes you teleport between; snow aura; freeze water to walk on it; "false death" captures | Hunted as "cursed"; frail; mirrors cost lots of chakra | Medium |
| **Chinoike** | A− | Ketsuryūgan (blood-red eye) | Blood marking by cut, then blood control, iron control and mind reading | Exiled: villages and Uchiha hostile; self-destructive clan history | Medium |
| **Kurama** `[anime-only]` | A− | Genjutsu made real | Paintings that become area illusions with real damage | Frail body; a second personality that takes over | Medium |
| **Nara** | B+ | Shadow techniques (Yin), medicine book, deer | Shadow range from light level, multi-bind, strategy view, antler medicine | Frail, lazy (low stamina), binds drain chakra fast | Easy |
| **Akimichi** | B+ | Calorie Control, Multi-Size, Three Coloured Pills, Butterfly Mode | Calorie store that becomes chakra and size; pill ladder ending in Butterfly Mode | Chilli pill almost kills; slow | Easy (pills) / Medium (pill-free control) |
| **Aburame** | B+ | Kikaichū hive (body is a hive) | Colony that grows with feeding; chakra-eating domains; insect decoys and trackers; rare nano-poison | Fire; hot baths and alcohol upset the hive `[anime-only]`; rare insects hit allies too | Easy |
| **Hatake** | B+ | White Light Chakra Sabre (heirloom), ninken, genius | Heirloom sabre, ninken pack summons, best implant tolerance | Average reserves; implant drain | Easy |
| **Sarutobi** | B+ | Fire hiden, Monkey King Enma, Will of Fire | Learn every nature faster ("the Professor"), Enma staff that becomes a cage, chakra blades | None big (canon has none) | Easy |
| **Hōzuki** | B+ | Hydrification | Hydration meter, liquid dodge, muscle pump, Swordsmen heirlooms | Lightning ×2, dries out, jelly when knocked out | Easy |
| **Yamanaka** | B | Mind Body Switch, telepathy, sensing | Possess mobs (fly as a bird), crew telepathy, sensor barrier | Body left inert; possessed damage comes back | Easy |
| **Inuzuka** | B | Ninken, Four Legs, Fang Over Fang | Existing growing hound + Two-Headed Wolf fusion, scent trails | Hot-headed; weak genjutsu | Easy |
| **Yotsuki** | B | Lightning, loyalty (Raikage link is `[fanon]`) | Lightning armour, unbreakable loyalty | Rough control | Easy |
| **Shimura** | B | Wind, Root, implants | Safer implants, Root tongue seal, Reverse Four Symbols last stand | Distrusted | Easy |
| **Iburi** `[anime-only]` | B− | Smoke body | Smoke form, smoke possession | Wind can kill them outright | Medium |
| **Kamizuru** `[anime-only]` | C+ | Bees, giant bee summon | Hives, honey and wax, bee scouting | Fire; disgraced in Iwa | Easy |
| **Kurogane** `[anime-only]` | C+ | Samurai swordsmanship | Iaidō quickdraw, jutsu-cutting chakra blade | No ninjutsu | Easy |
| **Fūma** | C | Giant shuriken (manga); chakra threads, archery, Curse Mandala (anime) | Returning giant shuriken; police authority | None | Easy |
| *Candidates we lack* | C–B | Tsuchigumo (forbidden Earth), Karatachi (Mist jinchūriki line), Hōki (Suna medics), Shirogane (puppet walkers), Funato (pirates), Kohaku, Taketori | See "Clans we lack" | | |
| *Ōtsutsuki* | — (bosses) | Byakugan, Rinne Sharingan, All-Killing Ash Bones, Rinnegan | Not playable: endgame bosses and the root of every great bloodline | | |

---

## The family tree that ties the great clans together

Canon links the S-tier clans through one ancestor, and NOIRM can use that for cross-clan mechanics:

- **Kaguya Ōtsutsuki** had the Byakugan, the Rinne Sharingan and bone manipulation (DB4 p25). Her
  bone power survives, weakened, in the **Kaguya clan** (ch. 684 p16; DB4 pp25, 66–69, 275).
- Her son **Hamura** → the **Hyūga** (Byakugan), described as the line that kept the Ōtsutsuki traits
  most purely. Hamura's moon branch keeps the Tenseigan (*The Last* movie).
- Her son **Hagoromo** (Sage of Six Paths, Rinnegan) had two sons:
  - **Indra** → the **Uchiha** (eyes, "power"). The **Fūma** are distant Uchiha kin without the
    Sharingan (B-vol. 10 p92).
  - **Asura** → the **Senju** and the **Uzumaki** (life force, "love") (ch. 500 pp2–4).
- Mixing Indra's and Asura's chakra recreates Hagoromo's and awakens the **Rinnegan** (ch. 671 p6–7;
  Madara with Hashirama's cells, ch. 606 p13).

**Game hook `[ours]`:** an "Ōtsutsuki heritage" tag on these five clans, used for: (1) the
Rinnegan recipe (Uchiha eyes + Senju or Uzumaki life force); (2) Izanagi's duration (Uchiha +
Senju cells: a few seconds → a minute, ch. 479 p5); (3) endgame Ōtsutsuki bosses that react to
heritage (they take +25% damage from a matching-heritage attack and covet that player).

---

## Databook stat profiles (1–5 scale)

Columns: Nin, Tai, Gen, Int, Str, Spd, Sta, Seals, Total. Only databooks 1–3 have stat tables; DB4
dropped them, so Hashirama, Tobirama, Madara, Kushina, Mito, Nagato and most Part II characters have
**no official numbers**.

| Clan | Member (databook) | Nin | Tai | Gen | Int | Str | Spd | Sta | Seals | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| Uzumaki | Naruto (DB1) | 2 | 1.5 | 1 | 1 | 2 | 2 | **4** | 1 | 14.5 |
| Uzumaki | Naruto (DB2) | 3 | 2 | 1 | 1.5 | 3 | 3 | **4** | 1 | 18.5 |
| Uzumaki | Naruto (DB3) | 4 | 3.5 | 2 | 3 | 3.5 | 3.5 | **5** | 1.5 | 26 |
| Senju | Tsunade (DB3) | 5 | 5 | 3.5 | 5 | **5** | 3.5 | 4 | 4 | 35 |
| Senju cells | Yamato (DB3) | 4.5 | 4 | 3.5 | 4.5 | 3.5 | 4 | 3.5 | 3.5 | 31 |
| Uchiha | Itachi (DB3) | 5 | 4.5 | **5** | 5 | 3.5 | 5 | 2.5 | 5 | 35.5 |
| Uchiha | Sasuke (DB1) | 2.5 | 2.5 | 1.5 | 2 | 2 | 3 | 2 | 3 | 18.5 |
| Uchiha | Sasuke (DB3) | 5 | 3.5 | 4 | 3.5 | 3.5 | 4.5 | 3.5 | 4 | 31.5 |
| Uchiha | Obito, child (DB2) | 3 | 2 | 1.5 | 1 | 2 | 3 | 2.5 | 3 | 18 |
| Kaguya | Kimimaro (DB2) | 4 | **5** | 3 | 3.5 | 3 | 4.5 | 4.5 | 3 | 30.5 |
| Hyūga | Neji (DB1) | 2.5 | 3.5 | 2 | 2.5 | 2.5 | 3 | 2 | 3 | 21 |
| Hyūga | Neji (DB3) | 4 | 4.5 | 2 | 3 | 2.5 | 4.5 | 3.5 | 3 | 27 |
| Hyūga | Hinata (DB1) | 1.5 | 2.5 | 1 | 2.5 | 1 | 2 | 1.5 | 2 | 14 |
| Hyūga | Hinata (DB3) | 3 | 3.5 | 2.5 | 3.5 | 1.5 | 2.5 | 2 | 3 | 21.5 |
| Hyūga | Hiashi (DB2) | 3.5 | **5** | 2 | 3 | 3.5 | 4 | 4 | **5** | 30 |
| Might | Guy (DB1–3) | 3 | **5** | 3 | 3 | **5** | **5** | **5** | 2.5 | 31.5 |
| Lee | Rock Lee (DB1) | **0.5** | 3.5 | **0.5** | 1.5 | 3 | 4 | 3 | 1 | 17 |
| Lee | Rock Lee (DB3) | **0.5** | **5** | 1 | 2 | 4.5 | 4.5 | 3.5 | 1 | 22 |
| Nara | Shikamaru (DB1) | 2.5 | 1.5 | 2.5 | **5** | 1.5 | 2 | 1.5 | 2.5 | 19 |
| Nara | Shikamaru (DB3) | 3.5 | 2 | 3 | **5** | 2 | 2.5 | 3 | 3 | 24 |
| Akimichi | Chōji (DB1) | 3 | 2 | 0.5 | 0.5 | 3 | **1** | 2 | 1.5 | 13.5 |
| Akimichi | Chōji (DB3) | 3.5 | 3 | 1 | 2 | **4.5** | 2 | 3.5 | 1 | 20.5 |
| Yamanaka | Ino (DB1) | 2.5 | 1.5 | 1.5 | 2 | **0.5** | 2.5 | 2 | 2.5 | 15 |
| Yamanaka | Ino (DB3) | 3.5 | 1.5 | 3 | 3 | 2.5 | 2.5 | 2.5 | 2.5 | 21 |
| Aburame | Shino (DB1) | 3.5 | 1.5 | 2 | 3 | 1.5 | 1.5 | 2 | 2 | 17 |
| Aburame | Shino (DB3) | 4 | 1.5 | 2 | **4** | 1.5 | 3 | 3 | 2 | 21 |
| Inuzuka | Kiba (DB1) | 1.5 | 2.5 | 0.5 | 1 | 2.5 | **3.5** | 1.5 | 1.5 | 14.5 |
| Inuzuka | Kiba (DB3) | 3.5 | 3 | 2 | 2 | 3 | **4.5** | 2.5 | 1.5 | 22 |
| Hatake | Kakashi (DB1) | 5 | 4 | 4 | 4.5 | 3.5 | 4 | **3** | 5 | 33 |
| Hatake | Kakashi (DB3) | 5 | 4.5 | 4 | 5 | 3.5 | 4.5 | **3** | 5 | 34.5 |
| Sarutobi | Hiruzen (DB1) | 5 | 5 | 5 | 5 | 3 | 3 | 3 | 5 | 34 |
| Sarutobi | Asuma (DB3) | 4.5 | 4.5 | 3.5 | 4.5 | 4 | 4.5 | 3.5 | 3.5 | 32.5 |
| Yuki | Haku (DB1) | 3.5 | 1 | 1 | 4 | 1.5 | **4** | 2 | **4** | 21 |
| Hoshigaki | Kisame (DB3) | 4.5 | 4.5 | 2.5 | 3.5 | **5** | 4 | **5** | 3.5 | 32.5 |
| Kazekage | Gaara (DB1) | 3.5 | 1 | 2.5 | 2.5 | 1 | 2 | **4** | 3.5 | 20 |
| Kazekage | Gaara (DB3) | 5 | 2 | 3.5 | 4 | 2.5 | 3 | **5** | 4 | 29 |
| Kazekage | Temari (DB3) | 4 | 2.5 | 1.5 | 4 | 3.5 | 3 | 3 | 3 | 24.5 |
| Kazekage | Kankurō (DB3) | 4 | 3 | 2 | 3.5 | 3.5 | 2.5 | 4 | 4 | 26.5 |
| *(baseline)* | Kurenai (DB2) | 4 | 4 | **5** | 4 | 2 | 4 | 2 | 4.5 | 29.5 |
| *(baseline)* | Jiraiya (DB3) | 5 | 4.5 | 3 | 4.5 | 4.5 | 4.5 | 5 | 4.5 | 35.5 |
| *(baseline)* | Zabuza (DB1) | 4.5 | 4.5 | 2.5 | 3 | 4 | 4 | 4 | 4 | 30.5 |

**What the numbers say about each clan** (this is the evidence for the perk directions):

- **Uzumaki:** stamina is always the top stat (4 → 4 → 5), while hand seals (1–1.5) and genjutsu
  (1–2) stay the lowest of any main character. That is exactly "bottomless pool, rough control".
- **Uchiha:** Itachi has the only 5 in genjutsu among clan heirs and 5 in hand seals; Sasuke's
  growth from 18.5 to 31.5 (DB1 → DB3) is the fastest in the series (canon calls the Uchiha's growth
  rate "advanced", ch. 112 p7, ch. 224 p10). Itachi's 2.5 stamina is his illness.
- **Senju:** Tsunade's 5 strength and 5 intelligence with 35 total ties the Sannin.
- **Kaguya:** Kimimaro has 5 taijutsu and 4.5 speed and stamina; weak only in genjutsu and seals.
- **Hyūga:** taijutsu and hand seals high (Hiashi 5/5), genjutsu stuck at 2: precise chakra, few
  illusions.
- **Might / Lee:** Lee's ninjutsu 0.5 is the lowest score in the databooks; Guy has four 5s in body
  stats.
- **Nara:** intelligence 5 at age 12; strength and taijutsu bottom.
- **Akimichi:** strength rises to 4.5 while speed stays bottom (1–2).
- **Hatake:** Kakashi's stamina is his only weak stat (3), the source of "small reserves".
- **Hoshigaki / Kazekage:** stamina 5 (Kisame, Gaara): huge pools.

---

# S tier: the great clans

## Uzumaki (渦巻, "whirlpool") · Tier S

### 1. Canon facts

- **Identity.** Formerly a great clan of Uzushiogakure, famous for red hair, very strong life force
  and chakra (DB4 p84) and fūinjutsu. Descendants of Asura, so distant kin of the Senju, with
  intermarriage (Hashirama and Mito) (ch. 500 pp2–4). Konoha's flak jackets carry the Uzumaki swirl
  as a sign of that friendship, and the Uzumaki supplied Konoha with sealing work (DB4). Their Mask
  Storage Temple stands on Konoha's edge (ch. 618 p1).
- **The fall.** Uzushio was destroyed because others feared its sealing arts; survivors scattered
  (DB4 p36). Known survivors: Kushina (Konoha), Nagato (Amegakure), Karin (Kusa). The clan was
  formally recognised again 15 years after the war (B-ep. 26).
- **Life force and longevity.** Very long lives and slow ageing (ch. 579 p12). Mito, born before
  Konoha existed, lived into the Third Hokage's era with her hair still coloured. They survive and
  recover from wounds and exhaustion far faster than others, enough to survive having a tailed beast
  pulled out, which kills anyone else (Kushina, ch. 501 p11).
- **Red hair** is the standard mark of the blood (ch. 510 p17; *Naruto Quiz Book* p227).
- **Chakra quantity (owner's reading, adopted).** Naruto's **own** reserves are about **100×**
  Kakashi's. The **4×** line (ch. 315 p11) is the figure measured while the Nine-Tails' seal was still
  holding his chakra down and tangling it with the fox's. See "Bold reading: Uzumaki" for the
  evidence and for how the design unseals it in stages.
- **Chakra quality.** Kushina and Mito had an especially strong, special chakra that made them ideal
  jinchūriki (ch. 498 pp6–7; ch. 500 p4). Kushina bound the Nine-Tails inside her mind with her chains,
  so she **never once lost control** of it (ch. 501). Naruto's chakra is naturally compatible with the
  fox's (ch. 617 p5).
- **Adamantine Sealing Chains** (A-rank hiden per DB4 `[unverified page]`): chains of chakra, usually
  from the torso, strong enough that the Nine-Tails struggles against them (ch. 498; ch. 503 pp13–14).
  Several chains can be sent underground and split to cover an area (ch. 503). Between chains a near
  impregnable **barrier** forms: Kushina kept the Nine-Tails in and the Third Hokage out (ch. 504).
  Chains can appear anywhere the user's chakra is, even from inside someone else (Kushina's chakra in
  Naruto). In the anime they also nullify the target's chakra (S-ep. 408) `[anime-only]`. Karin shows
  an incomplete version that smashes a giant wooden statue (ch. 663 p8); DB4 calls hers only a
  "glimpse" of Kushina's.
- **Healing bite (Karin).** Anyone who bites her absorbs her chakra and heals (ch. 412 p6); she can
  bite herself to heal a fatal wound (ch. 663 pp9–10); her chakra refilled Tsunade's reserves
  (ch. 635 pp7–8). Using it more than once a day is dangerous to her (ch. 412 p15), and it leaves
  permanent bite marks.
- **Mind's Eye of the Kagura (Karin).** Effortless sensing of chakra over long distances, identifying
  individuals and even species (ch. 350 pp6–8; ch. 365 pp1–4); reads the size of a pool, detects lies
  through chakra fluctuations (ch. 457 p6), notices genjutsu (ch. 477 p4); can hide her own chakra
  completely, but then can't use it (ch. 461 p9).
- **Nagato.** With Uzumaki chakra he used both Rinnegan eyes as an untrained child without drawbacks,
  where Obito could barely handle one; his chakra was vast enough to run several heavy techniques in a
  day; he could shift his chakra frequency so sensors couldn't find him (ch. 441). He mastered all five
  natures plus Yang by age ten. His revival of Konoha's dead cost him his life (ch. 448–449
  `[unverified]`).
- **Signature techniques:** Adamantine Sealing Chains; Four Symbols / Eight Trigrams Seal; the Dead
  Demon Consuming Seal via the Shinigami mask (the temple's masks, ch. 618); Uzumaki Sealing (spiral
  seal crest); Karin's bite healing.

### 2. Databook profile
Naruto (above): stamina 4–5, seals 1–1.5, genjutsu 1–2. No 1–5 tables exist for Kushina, Mito,
Nagato or Karin.

### 3. Weaknesses and costs in canon
- **Hunted.** A whole village was wiped out out of fear; survivors hid. Kushina was kidnapped by Kumo
  for her chakra (*Naruto* ch. 498 `[unverified]`).
- **Rough control.** Naruto can't do a basic clone in the Academy and has the worst seal and genjutsu
  stats.
- **Jinchūriki confinement.** As a host Kushina had to stay inside a sealing barrier under guard, and
  leaving it let the fox's chakra leak (*Naruto Gaiden: Whirlwind in the Vortex*) `[novel]`.
- **Bite drain.** Karin's healing is dangerous more than once a day.
- **Not every Uzumaki gets the chains**: canon shows them only in Kushina and, weakly, Karin.

### 4. Progression in canon
Life force and red hair are inborn. Chains appear in members with special chakra; Karin's first
appear under extreme stress in battle. Sealing is study and teaching (Kushina taught Minato). The
peak forms come from **being a jinchūriki** and **holding Ōtsutsuki eyes** (Nagato).

### 5. Game-changer mechanics `[ours]`
Keep the existing perks (chakra ×10, regen ×3, healing 0.35/s, rough control, loud chakra, sealing
×2). Add:

1. **Whirlpool Tenacity (life force).** Once every 10 minutes, a blow that would kill you leaves
   you at 1 health instead, plus 3 s of Resistance II. This is separate from the Resolve awakening
   (which can trigger after it). Cost: 40 s of "Spent Life" (no regen, −30% speed). With **Vitality
   30** the cooldown drops to 6 minutes. Tuned from Kushina surviving extraction.
2. **The chain ladder** (the clan's main progression; only members with **Kagura chakra**, rolled
   at birth for 60% of Uzumaki; the rest get +25% sealing instead):
   | Stage | Unlock trigger | What you get |
   |---|---|---|
   | 0 Latent | born | nothing visible; chains "rattle" in the HUD at low health |
   | 1 Chain Lash | first time under 25% health while an ally is in danger within 16 blocks, **or** 60 s fighting a tailed beast or jinchūriki | 1 chain: 10-block lash, 6 damage, pulls the target 3 blocks to you (20 chakra) |
   | 2 Binding | Chain Lash ★3 | existing Adamantine Chains bind (3.5 s + chakra seal 6 s); now up to 2 targets |
   | 3 Chain Barrier | Binding ★3 + 10 sealing scrolls crafted | 4 chains sink into the ground and rise as a square barrier, 9×9 up to 15×15, 6 high, for 20 s: **nothing passes in or out** (players, mobs, projectiles, jutsu, bosses). 60 chakra + 3/s. One-way option: you can walk out. |
   | 4 Beast Restraint | subdue a tailed beast with chains wrapped on it | chains on a beast or boss hold it 8 s (bosses 4 s) and drain 20% of its chakra; sealing scrolls on a chained beast always succeed |
   | 5 Inner Chains | be a jinchūriki and reach Stage 4 | the beast **can't take over** (no burning red cloak; "the beast takes over" awakening becomes a controlled version with no self-damage), cloak lasts 2× longer |
3. **Healing Bite.** Interact with an ally with an empty hand while crouching: they heal **60% of
   max health and 40% of max chakra**. You lose 15% of max chakra and gain a "bite mark" (−1 max
   health until you sleep). A second bite inside one in-game day gives you Weakness II and −4 max
   health for the rest of the day; a third is refused ("you'd die"). Only Uzumaki with the
   `kagura_chakra` flag (Karin's line).
4. **Mind's Eye of the Kagura** (rare talent, 25% of Uzumaki): passive sensing 64 blocks; NPC dialogue
   marks lies; you see when a nearby player is in genjutsu; **Suppress Chakra** toggle: invisible to
   all sensing (Byakugan, sensors, the loud-chakra perk) but no jutsu while on.
5. **Sealing arts that only Uzumaki can do:**
   - **Sealing Jar** (clay pot + 4 paper + 1 ink): seal a subdued mob (any size up to a ravager) or a
     dropped item stack; release it later on your side. Up to 3 jars carried.
   - **Jutsu Absorbing Seal:** a seal tag on your arm that swallows the next jutsu that hits you
     (60 s window, 40 chakra) and can be thrown back once.
   - **Four Symbols Seal on a host:** when you seal a beast into someone (existing scroll), the host
     gets +50% beast-chakra control.
   - **Shinigami mask** (found only in the Mask Storage Temple): Dead Demon Consuming Seal. Seal half
     (or all) of any one creature, boss included, into yourself. **You die** (a true death: you
     respawn with all Vitality and Reserves XP of the last 3 levels lost). The ultimate last resort.
6. **Revival-level vitality in numbers.** Exhaustion after "LOCKED IN" lasts half as long; tailed-beast
   extraction (being "unsealed") leaves an Uzumaki at 1 health instead of dead.
7. **The cost: Whirlpool Heat** (the hunted clan). A hidden heat score rises with chains, sealing
   and jinchūriki use. Every in-game dawn there is a chance (heat × 0.5%, max 25%) that a hunter
   squad (3 ninja from Kiri, Kumo or Iwa) comes for you. Loud chakra already brings wild ninjas.
8. **Clan places and NPCs.** **Uzushio Ruins**: an island ocean structure (broken spiral gates,
   whirlpools in the surrounding water, a sealing archive whose lecterns teach the seals above).
   **Mask Storage Temple** near the Leaf: masks on the walls, one Shinigami mask in a sealed chest.
   Uzumaki NPC ninjas use chains and barriers and always try to seal a defeated jinchūriki.

### Sources
ch. 412, 457, 461, 477, 498, 500, 501, 503, 504, 510, 579, 617, 618, 635, 663; ch. 315 p11; DB4 pp36,
84; *Naruto Quiz Book* p227; B-ep. 26; S-ep. 408 `[anime-only]`; *Whirlwind in the Vortex* `[novel]`;
Narutopedia: *Uzumaki Clan*, *Kushina Uzumaki*, *Mito Uzumaki*, *Karin*, *Nagato*, *Adamantine
Sealing Chains*.

---

## Senju (千手, "a thousand hands/skills") · Tier S

### 1. Canon facts

- **Identity.** Co-founders of Konoha with their rivals the Uchiha, and the strongest clan of the
  Warring States period (DB3 p338). Called "the clan with a thousand skills" because they trained
  ninjutsu, taijutsu and genjutsu alike instead of one specialty (DB3). "The Senju of the forest"
  (ch. 398 p15). Their strength was said to come from love (ch. 619 p13). Three Senju became Hokage.
  The clan no longer exists as a group; its blood survives in academy teachers and in Tsunade
  (*Itachi Shinden* p50 `[novel]`).
- **Hashirama (First Hokage).** The "God of Shinobi". Madara admitted he was weaker even with the
  Eternal Mangekyō and the Nine-Tails (ch. 620–626). He captured eight of the nine tailed beasts alone
  (ch. 458 p3). Flaring his chakra makes shockwaves (ch. 620 p4). His reserves were about the size of
  what Naruto shared with the whole Allied army (ch. 644 pp9–11). He fought for **over 24 hours
  without slowing** (ch. 624 p15), healed without hand seals (ch. 576 pp19–21), regenerated most
  injuries without trace (ch. 578 p6), kept ten wood clones running while holding a Kage-level barrier
  (ch. 632 pp7–9), resisted the perfected Impure World Reincarnation (ch. 620 p11), and entered Sage
  Mode instantly. His cells give a transplant patient vitality, stamina, regeneration and possibly
  Wood Release.
- **Wood Release** (Earth + Water, ch. 316 p6). Converts chakra into living wood (DB3 p307). Hashirama
  raised the forest that Konoha was built in (DB2 p279), and could make cages, houses (ch. 286–287),
  pliable snares (ch. 494 p15), battering trunks (ch. 302 p7), sleep-pollen flowers, Tailed-Beast-Ball-proof
  walls (ch. 621 p4), giant hands (ch. 621 pp6–7), a thousand-armed statue whose punches carved the
  Valley of the End (ch. 626 pp4–9), and a wood golem matching Madara's Complete Susanoo (ch. 641 p5).
  Wood clones are indistinguishable from the user and can use jutsu (ch. 577 p13). **Wood suppresses
  and drains chakra on contact**, which is how Hashirama tamed tailed beasts (ch. 458 p3; ch. 609 p2;
  ch. 648 p11), plus a hand technique that puts a beast to sleep (ch. 626 pp5–6). **Hashirama is the
  only natural user** (ch. 297 p9); everyone else needs his cells (B-ep. 159).
- **Tobirama (Second Hokage).** Made huge amounts of water with no water source (ch. 120 p4); created
  the Flying Thunder God, Shadow Clone and Impure World Reincarnation techniques (ch. 122 p16); his will
  needed Hashirama's cells to control (ch. 620 p11).
- **Tsunade (Fifth Hokage).** Granddaughter of Hashirama and Mito (so half Uzumaki on that side).
  **Strength of a Hundred Seal**: chakra stored over a long time in a forehead diamond, then released
  to power techniques; Sakura needed **three years** to fill it and Shizune never managed the control
  (ch. 632 pp12–18); the seal also powers Katsuyu's mass healing (ch. 635 pp4–5); Tsunade also uses it
  to keep looking young. **Creation Rebirth**: forced cell division heals any wound instantly and
  the user can't die while it runs, but it **shortens the user's natural lifespan** because cells can
  divide only so many times (ch. 169 p16). Strength of a Hundred Technique combines both.
- **Yamato** is the only survivor of Orochimaru's Hashirama-cell experiments on infants
  (ch. 286–288 `[unverified: "59 of 60 died" is widely quoted]`).
- **Signature techniques:** Wood Release (Deep Forest Emergence, Wood Clone, Wood Dragon, Four-Pillar
  House, Wood Human, True Several Thousand Hands); Kakuan Bliss-Bringing Hands; Rashōmon (summoned
  gates); Water Dragon (Tobirama); Flying Thunder God; Creation Rebirth; Strength of a Hundred.

### 2. Databook profile
Tsunade DB3: 5/5/3.5/5/5/3.5/4/4 = 35, tied with the Sannin. Yamato (cells) 31. No tables for
Hashirama or Tobirama.

### 3. Weaknesses and costs in canon
- **Wood Release is one in a generation** (Hashirama alone). Cells kill most recipients.
- **Creation Rebirth burns lifespan.**
- **Strength of a Hundred** takes years of delicate control; filling it drains normal reserves, so
  surgery and fighting are risky meanwhile (S-ep. 408) `[anime-only]`.
- **Raw over finesse** is fan reading; canon actually shows Hashirama with excellent control (single
  seal for huge jutsu, S-ep. 369 `[anime-only]`). The "rough control" perk is a NOIRM choice, not canon.
- The clan **dissolved into the village**; no Senju name survives in the present day.

### 4. Progression in canon
Hashirama's Wood Release is a mutation of Asura's life force; it isn't taught. Sage Mode came easily
because of his huge reserves. Tsunade's powers are **medical training + years of storing chakra**.

### 5. Game-changer mechanics `[ours]`
Keep the existing perks (chakra ×4, health +14, healing 0.4/s, Wood affinity +0.5). Add:

1. **Wood Blood vs. Senju Blood.** At birth, **1 Senju in 10** rolls **Wood Blood** (Wood affinity
   1.5 and the Wood starter jutsu, like Hashirama). The other 9 have Senju Blood (the current perks)
   and must earn Wood through the **Forest Trial**: Earth and Water jutsu at ★3, plant and grow 64
   saplings to trees, then meditate in Sage Mode under one of them for 60 s. Pass: Wood affinity 1.0.
2. **Wood that builds** (Wood affinity ≥ 1.0):
   - **Four-Pillar House:** 40 chakra, a 7×7×5 wooden house rises from the ground in 3 s (data-driven
     blueprint in `data/noirm/wood_blueprints/`). Permanent blocks (real oak). One per minute.
   - **Wood Bridge / Wall / Stair:** draw a line up to 16 blocks; logs grow along it (2 chakra/block).
   - **Deep Forest Emergence** (existing) leaves real trees behind when cast with Wood Blood.
   - **Green Hands** (passive): crops and saplings within 8 blocks grow ×2 faster while you stand
     near them; bone-meal on demand for 5 chakra.
3. **Tailed-beast taming.** Wood Binding on a subdued beast drains 5% of its chakra per second;
   **Bliss-Bringing Hands** (touch a beast): asleep 30 s. With Wood ★5 you can **bind a beast as a
   companion** (not sealed: it follows and fights for you; one at a time; it breaks free if your chakra
   hits 0). Wood also drains 4 chakra per second from anything it binds.
4. **Wood Clone:** like a shadow clone, but it takes 3 hits, casts your jutsu at 60% power, and costs
   25% of max chakra; up to 2 (10 with Hashirama-level Reserves 40).
5. **Strength of a Hundred Seal** (Senju and Uzumaki with CTL ≥ 30; a medical-ninja master teaches
   it): turn it on and **1% of all chakra regen** flows into a forehead seal instead of your pool,
   cap 1000. Filling takes about 3 in-game days of normal play. A diamond mark shows on the forehead
   when full. **Release:** your jutsu cost 0 for 30 s and the stored chakra refills you, and you may
   follow up with…
6. **Creation Rebirth:** for 15 s you **can't die** and every wound closes instantly (full heal each
   second, debuffs removed). The cost: you have **10 "cell divisions"** for life; each use spends one;
   at 0 each further use permanently removes 2 max health. (A future aging system can read the same
   counter.)
7. **Seal-less healing:** medical jutsu cost −50% and can be cast while moving and fighting
   (Hashirama healed without seals).
8. **Instant Sage Mode:** a Senju who has learned Sage Mode enters it with no gathering time
   (Hashirama), and Sage Mode drains 30% less.
9. **Hashirama Cells** (rare item: from a Wood Clone remains, the Valley of the End boss, or a hidden
   Root lab): a 10 s "surgery". Outsiders: **10% gain Wood affinity 0.5**, 60% gain +4 max health and
   +20% healing, 30% suffer "cell rejection" (−8 max health for 3 in-game days, then nothing). Uchiha
   with the Eternal Mangekyō who take cells start the **Rinnegan clock** (see Uchiha).
10. **Costs.** Loud chakra (existing), Creation Rebirth lifespan, Wood Blood is luck. Being the
    village's "founding clan" also draws Uchiha grudges: Uchiha NPCs start with −20 respect.
11. **Places and NPCs.** **Senju forest shrine** (giant wood-grown pavilion, honey-wine offering; DB:
    the Senju gave honey wine as a wedding gift, *Konoha Hiden* p86 `[novel]`). **Valley of the End**:
    two giant statues facing across a waterfall, an endgame arena where a Wood Human boss and a Susanoo
    boss fight. Senju NPC ninjas are medics and Wood users who heal allies mid-fight.

### Sources
ch. 120, 122, 169, 286–288, 297, 302, 316, 398, 458, 494, 500, 576–578, 609, 619–626, 632, 635, 641,
644, 648; DB2 p279; DB3 pp307, 338; B-ep. 159; S-ep. 369, 408 `[anime-only]`; *Itachi Shinden*,
*Konoha Hiden* `[novel]`; Narutopedia: *Senju Clan*, *Hashirama Senju*, *Wood Release*, *Strength of
a Hundred Seal*, *Creation Rebirth*, *Tobirama Senju*.

---

## Uchiha (うちは, "fan") · Tier S

### 1. Canon facts

- **Identity.** Co-founders of Konoha; descendants of Indra. Natural talent in every form of combat,
  strong chakra (ch. 398 p29) and a fast growth rate (ch. 112 p7; ch. 224 p10; ch. 623 p11). "The clan
  that controls fire" (DB2 pp176, 305): an Uchiha isn't an adult until they can perform the **Great
  Fireball** (ch. 223 p9), and the best need several water users to put out their flames (ch. 560 p5;
  ch. 577 p4). They favour shuriken. They ran the Konoha Military Police, planned a coup, and were
  massacred by Itachi (ch. 224).
- **Curse of Hatred.** Tobirama: the Uchiha love deeply, and when they lose that love it turns into
  hatred instantly, and the Sharingan grows with those emotions (ch. 619 pp13–16). Sasuke's power and
  chakra grew in battle from hatred (ch. 460 pp14–15; ch. 478 pp8–9). Many ninja think it wiser to flee
  than face an Uchiha alone (ch. 257 pp5–6).
- **Sharingan.** Two broad powers, "Eye of Insight" and "Eye of Hypnotism" (ch. 26 p13): sees and
  colours chakra (ch. 361 p17), sees through some obstacles (ch. 579), reads lips (ch. 246) and hand
  seals at any speed (ch. 333; ch. 361), predicts movement from muscle tension (ch. 230 pp8–10), and
  copies ninjutsu, genjutsu and taijutsu. Genjutsu by eye contact up to full body control (ch. 26;
  ch. 501 p8), including **tailed beasts** and their jinchūriki (ch. 309 p2), several at once, weaker
  when divided (ch. 567 p7). Usually 1 tomoe at first, matures to 3 through intense experiences;
  maturing mid-fight changes the user's movement. Full potential needs both eyes (ch. 602 p15).
  New users' timing can be thrown off by the sharper perception (S-ep. 454) `[anime-only]`. Awakening
  can blur vision first (B-ep. 19–20) `[anime-only]`.
- **Implanted Sharingan** in non-Uchiha can't be turned off and drain much more chakra; Uchiha need
  little to keep it open (ch. 459 p15). Eyes from the same Uchiha stay linked (ch. 604; ch. 651).
- **Mangekyō** (DB3 pp274–275; first ch. 142): awakened by the trauma of losing someone dear
  (DB4 p248). The clan wrongly believed you had to kill them, so some murdered loved ones (ch. 386
  p13). Unique techniques per eye (Amaterasu, Tsukuyomi, Kagutsuchi, Kamui, Kotoamatsukami…). Awakening
  it matures the Sharingan automatically (ch. 605 p4). **Both eyes → Susanoo** (ch. 464 p16). Using it
  drains a lot of chakra, hurts, and **degrades sight until blindness** (ch. 385 p15).
- **Eternal Mangekyō:** transplanting a close relative's Mangekyō (ideally a sibling's; both Uchiha)
  cures and prevents blindness, keeps the original eye's techniques, merges the patterns, and reduces
  the body strain to almost nothing (ch. 386 pp7–9; ch. 488 p17).
- **Susanoo** (ch. 464 p16 onward). A chakra giant around the user that blocks physical attacks;
  harder to break as it completes; the user can cast jutsu out of it (ch. 477 p6), bring others in
  (ch. 647 p14), and step out (ch. 552). Doesn't regenerate damage except by advancing a stage or
  re-forming; **doesn't stop visual or sound attacks** (ch. 580; ch. 585); weak from below until the
  legs form (ch. 560 pp12–13); costs huge chakra and, for Sasuke, **pain in every cell** that grows with
  use and stage.
- **Izanagi.** Makes reality an illusion for a few seconds so injuries or death are undone
  (ch. 479 pp2–4); based on Hagoromo's Creation of All Things (ch. 510 pp10–11). **The eye used goes
  permanently blind**, so the clan made it kinjutsu (ch. 478 p17; ch. 681 p14). Duration grows with
  Senju cells (Danzō: about a minute, and ten implanted eyes for ten uses, ch. 479 p5); non-Uchiha
  lose lots of chakra per use (ch. 479 p12). Madara even timed one to revive him after death.
- **Izanami.** The counter to Izanagi: memorise a moment of shared bodily sensation, recreate it, and
  the target's mind loops between the two until they accept the truth; can't be undone even by
  Izanagi (ch. 581 pp15–17; ch. 586–587). The eye used goes blind.
- **Rinnegan** from Indra + Asura chakra (ch. 671 pp6–7). Madara's eyes became Rinnegan **decades
  after** he implanted Hashirama's flesh, near the end of his life, which also restored the eye blinded
  by Izanagi (ch. 606 p13; ch. 681 pp13–14). Sasuke got one from Hagoromo's chakra (DB4 p230).
- **Stone tablet** (Naka Shrine): readable further with each eye stage (Sharingan < Mangekyō <
  Rinnegan).
- **Kotoamatsukami** (Shisui): rewrites a mind invisibly; the eye can't use it again for about a
  decade (ch. 590 `[unverified]`), so Shisui's eyes were destroyed to keep them from enemies.

### 2. Databook profile
Itachi 35.5 (genjutsu 5, seals 5, stamina 2.5 from illness); Sasuke 18.5 → 31.5; Obito (child) 18.
No table for Madara.

### 3. Weaknesses and costs in canon
Blindness (Mangekyō); a blind eye per Izanagi/Izanami; Susanoo cell pain; huge chakra drain; the
hatred spiral (Sasuke, Obito, Madara); eye theft (Danzō, Kabuto, Root); village distrust ending in
massacre. Itachi's illness is unrelated to the eyes in canon (*Itachi Shinden*) `[novel]`.

### 4. Progression in canon
Sharingan: strong emotion or danger (often around ages 6–13). Tomoe: intense experiences. Mangekyō:
loss of a loved one. Eternal: a relative's Mangekyō. Rinnegan: Asura's chakra (Senju cells, decades) or
Hagoromo's gift.

### 5. Game-changer mechanics `[ours]`
NOIRM already has: Sharingan awakening (4 routes), tomoe growth, copy, precognition, Mangekyō with
rolled abilities (Amaterasu, Tsukuyomi, Kamui, Kotoamatsukami, Izanagi), Susanoo, strain and
blindness, Eternal by transplant, eye items. Additions:

1. **Coming of age: the Great Fireball rite.** A young Uchiha's Fire affinity starts at 1.0. Cast
   Great Fireball at ★1 on a lake shore (the canon pier) while an Uchiha NPC or player watches → Fire
   affinity 1.5, the clan crest on your back, and Uchiha NPCs treat you as an adult (+20 respect).
   Uchiha fire jutsu can't be put out by rain, and need 2 water jutsu to cancel in a clash.
2. **Curse of Hatred** meter (0–100, visible only to Uchiha). Rises when a crew mate (+25) or bonded
   NPC (+40) dies, when you're betrayed, or when you lose to a rival. Effects: Sharingan and Mangekyō
   power +0.3% per point (up to +30%), Mangekyō strain +0.2% per point. Above 70: crew respect decays
   −1/day and the village guards eye you. At 100, a "fall to darkness" choice: leave your village
   (become rogue, +50% eye power for 1 day) or let it go (reset to 30, gain Resolve). Love lowers it:
   time with your crew and sparring with a rival bring it down 1/hour.
3. **Izanagi and Izanami as kinjutsu** for any 3-tomoe Uchiha (not only a Mangekyō roll):
   - **Izanagi:** armed for 4 s (Uchiha), 60 s with Senju cells. A death in that window is undone.
     **Cost: one eye goes blind for good** (a blind eye halves Sharingan perception and stops copy;
     two blind eyes = no Sharingan). Only the Rinnegan or an eye transplant restores it.
   - **Izanami:** mark a moment (hit the target, 10 s window), later recreate it (hit them again with
     the same move within 30 s) → the target loops: frozen 8 s, jutsu reset, and if it's an NPC, it
     surrenders. Cost: the eye goes blind. An Izanagi user caught in Izanami loses their Izanagi.
4. **Tailed-beast genjutsu.** 3 tomoe: control a subdued beast for 30 s; Mangekyō: take an unsealed
   beast as a companion for 5 minutes (once per day), or force a jinchūriki's beast to take over.
   An Uzumaki with Inner Chains (Stage 5) is immune.
5. **Susanoo additions:** a 4th stage **Complete Body** (legs; no attacks from below; ×0.2 damage
   taken; needs EMS or 5 minutes of total Susanoo time); **Perfect Susanoo** (wings, flight, 30 dmg
   swing) only with EMS + Rinnegan or EMS + 30 Reserves. **Cell pain:** after 20 s of continuous
   Susanoo you lose 0.5 health/s (EMS: after 60 s). Visual (genjutsu) and sound attacks go through.
6. **Rinnegan, the endgame.** EMS + Hashirama Cells starts a clock: after **5 in-game days** *and*
   the next near-death awakening, one or both eyes become Rinnegan. It restores every blind eye and
   unlocks the Six Paths (a separate jutsu set: Deva push/pull, Preta absorbs jutsu, Human reads and
   rips a soul, Animal summons, Naraka heals, Outer Path chains). Huge drain: 3 chakra/s open.
   Uzumaki can **hold** a transplanted Rinnegan without drain penalties (Nagato).
7. **Naka Shrine and the stone tablet.** A hidden shrine under the Uchiha district. Reading the
   tablet with Sharingan teaches a Fire jutsu chain; with Mangekyō, Susanoo advice (Susanoo grows 25%
   faster); with Rinnegan, the location of an Ōtsutsuki boss.
8. **Eye hunters.** Root agents (Shimura NPCs) ambush awakened Uchiha 1 dawn in 10; they try to take
   an eye (if they win, you lose one eye's tomoe, not your life).
9. **Village distrust.** Uchiha players in Konoha-type villages pay 10% more for services until
   respect is high, and police NPCs follow them around at night; joining the Uchiha police (a quest)
   lifts it.

### Sources
ch. 26, 112, 142, 223, 224, 230, 246, 257, 309, 333, 361, 385, 386, 398, 459, 460, 464, 477–479, 488,
501, 510, 552, 560, 567, 577, 579–581, 585–587, 602, 604–606, 619, 623, 647, 651, 671, 681; DB2 pp176,
305; DB3 pp274–275; DB4 pp230, 248; S-ep. 454, B-ep. 19–20 `[anime-only]`; *Itachi Shinden* `[novel]`;
Narutopedia: *Uchiha Clan*, *Sharingan*, *Mangekyō Sharingan*, *Susanoo*, *Izanagi*, *Izanami*,
*Rinnegan*, *Curse of Hatred*, *Madara Uchiha*.

---

## Kaguya (かぐや) · Tier S (with the heaviest cost)

### 1. Canon facts

- **Identity.** An extinct, savage, battle-hungry clan descended from Kaguya Ōtsutsuki. Only a
  **select few** inherit **Shikotsumyaku**, a weaker derivative of Kaguya's All-Killing Ash Bones
  (ch. 684 p16; DB4 pp25, 66–69, 275). The power was so strong that even the war-loving clan feared it.
- **The end.** They attacked Kirigakure with no strategy, just to show their ferocity, and fought to
  the last even when surrounded; all died except Kimimaro, who didn't share their lust for slaughter
  (ch. 216 pp14–15). Orochimaru adopted him.
- **How Shikotsumyaku works** (DB2 pp230–231): control of osteoblasts, osteoclasts and the calcium
  density of bone (ch. 212 p2), usable **without limit as long as the body doesn't tire**. Grow bones
  anywhere, make them denser (stronger than tempered steel; resists cutting and crushing, ch. 215 p14;
  ch. 531 p10) or hollow (a flute, ch. 585 p8), push them through the skin and pull or fire them out.
  Skin heals right after (ch. 206 p6). A removed bone leaves that body part misshapen until replaced
  (ch. 211 p15). Called the ultimate taijutsu ability by Orochimaru, who wanted Kimimaro's body for it
  (ch. 201 pp16–17).
- **Feats.** Survived burial under about 200 m of compressed sand; shrugged off most of a
  chakra-enhanced blade; cut through shuriken; grew a forest of giant bone blades and travelled inside
  one to ambush from below; fought Naruto, Lee and Gaara in turn while dying; the only person before
  Sasuke who could calm Jūgo (ch. 351 p7); held the Cursed Seal of Earth (level 2: a bone-tailed,
  bone-spined form strong enough to tackle through Gaara's sand).
- **The five dances** (passed down in the clan, *One Decade, One Hundred Ninja* p18 `[guidebook]`):
  Dance of the Camellia (an arm bone as a sword), Dance of the Willow (bone blades out of the whole body
  while spinning), Dance of the Larch (spikes everywhere at once), Dance of the Clematis: Vine (the spine
  as a whip) and Flower (a huge drill bone on the arm), Dance of the Seedling Fern (a forest of bone
  spikes over a huge area). Plus **Ten-Finger Drilling Bullets** (fingertip bones fired as bullets).
- **Illness.** Kimimaro died of an incurable illness; Kabuto couldn't treat it because his body was too
  different from normal and there was no medical data on it.
- **Look.** White hair, green eyes, two red dots on the forehead (Heian-court eyebrows, per a Kishimoto
  interview).

### 2. Databook profile
Kimimaro DB2: 4/**5**/3/3.5/3/4.5/4.5/3 = 30.5, at age 15, above every Part I genin and level with
Zabuza.

### 3. Weaknesses and costs in canon
Terminal illness; the clan's blind bloodlust killed it; only a few carry the power; bones run out if
the body tires; genjutsu and ninjutsu are not their strength.

### 4. Progression in canon
Innate in the few who carry it, refined into the clan's five dances. The cursed seal amplified it.
Kabuto and Orochimaru later copied it from his cells.

### 5. Game-changer mechanics `[ours]`
Keep the current perks (DUR +10, health +8, damage taken −15%, melee +25%, illness DoT). Add:

1. **Bone Pulse roll.** 1 Kaguya in 3 carries Shikotsumyaku. The rest are **Kaguya warriors**: the
   bloodlust and durability perks, no bone powers. (Players who want it can reroll in the creator; NPCs
   follow the ratio.)
2. **The body is the armoury.** With an empty hand, the "pull bone" key (or a clan jutsu slot) makes a
   bone item that weighs nothing and can't be dropped or traded (it crumbles when dropped):
   | Bone | Unlock | Stats |
   |---|---|---|
   | Camellia blade | born | sword, 8 damage, never breaks, 1.4 attack speed; crumbles after 5 min |
   | Finger bullets | born (existing jutsu) | 5 fast bullets |
   | Willow | dance scroll 1 | 4 s spin: every hit you take or give deals 3 back; can't be grabbed |
   | Larch | scroll 2 | instant burst of spikes, 3-block radius, 7 damage, blocks the next hit fully |
   | Clematis Vine | scroll 3 | spine whip: 7-block reach, 6 damage, pulls or wraps the target 2 s |
   | Clematis Flower | scroll 4 | drill on the arm: hold to charge 1.5 s, 18 damage, ignores guard and 70% of armour, breaks Susanoo ribcage and earth walls |
   | Seedling Fern | scroll 5 | a forest of bone spikes rises over a 12-block radius in 1.5 s: 10 damage, roots everything for 3 s, the spikes stay 15 s as cover; you can dive into one and come up anywhere inside it |
3. **Steel bones.** Toggle **Fortress Body**: damage taken −40%, knockback immune, but speed −30% and
   no sprinting. Falls under 40 blocks deal no damage while it's on.
4. **Calcium cost.** Each bone costs 2 hunger, or 1 bone meal from the inventory (eaten first). With
   no hunger left, bones cost health. That's "as long as the body doesn't tire".
5. **The illness: Bone Fever.** A hidden counter rises 1 per bone made beyond the Camellia and 5 per
   Seedling Fern. Stages at 200 / 500 / 900 / 1400:
   1. a cough (once a minute you lose 0.5 s of action);
   2. −0.05 health/s (the current perk, moved here);
   3. max health −25%, stamina regen −30%;
   4. max health −50%, and every bone beyond the Camellia costs 1 health.
   It never goes down on its own. A **medical ninja master** can slow it (halve gains for 3 days) and a
   Senju's **Hashirama Cells** or an Uzumaki's **Healing Bite** pushes it back one stage once. A cursed
   seal or Sage Mode lets you ignore its penalties for 60 s (Kimimaro fought while dying). No cure:
   that's the price of S-tier taijutsu.
6. **Bloodlust.** In a fight, each hit you land stacks +2% damage (max +30%), but leaving a fight
   before the enemy is dead or fled gives "Shamed Blood" (Weakness I, 60 s). Kaguya NPCs never retreat.
7. **Dance scrolls and places.** **The Bone Graveyard**: Kaguya ruins on a misty island (white bone
   arches, ribs as walls, a burial pit). The five dance scrolls are spread across it, each guarded by a
   Kaguya revenant (an undead bone warrior who uses that dance). Reading a scroll teaches the dance.
8. **Calming Jūgo.** A Kaguya near a Jūgo's-clan player halves their frenzy build-up (ch. 351).

### Sources
ch. 201, 206, 211, 212, 215, 216, 351, 531, 585, 684; DB2 pp230–231; DB4 pp25, 66–69, 275;
*One Decade, One Hundred Ninja* p18; Kishimoto interview (forehead marks); Narutopedia: *Kaguya Clan*,
*Shikotsumyaku*, *Kimimaro*.

---

# A tier

## Hyūga (日向) · Tier A+

### 1. Canon facts
- **Identity.** Konoha's oldest and most outstanding bloodline (DB1 p265), described as one of, if not
  the, strongest clans in the village; Kumo had a hard time against them in an earlier war (ch. 614 p3).
  Descendants of Hamura, the line that kept the Ōtsutsuki traits most purely. **Every** member is born
  with the Byakugan.
- **Byakugan.** Near-360° vision with a **blind spot** behind the neck above the first thoracic vertebra
  (B-ch. 53 p13; Neji's blind spot is exploited in ch. 196–197 `[unverified page]`); sees through solid
  objects; sees the chakra network and all **361 tenketsu**. Range varies and **grows with training**:
  Neji ~50 m in Part I (ch. 195 p9) → 800 m in Part II; Hinata ~1 km in Part I (ch. 58 p7) → 10 km in
  Part II (ch. 396 p6) → 20 km in *The Last*. Kaguya's covers much of the globe.
- **Gentle Fist.** Strikes tenketsu to seal or force open chakra points, giving control of the target's
  chakra; it damages internal organs. Weak against long-range fighters (Kidōmaru vs Neji). Signature:
  Eight Trigrams Palm Rotation (a main-house secret that Neji developed on his own), 64 Palms, 128 Palms,
  Vacuum Palm, Protecting 64 Palms (Hinata, ep. 136–141 `[anime-only]`), Twin Lion Fists (Hinata,
  `[anime-only]`).
- **Main and branch houses.** The branch protects the main. Branch members are branded with the
  **Caged Bird Seal** on the heir's third birthday: the main house can destroy their brain cells or cause
  pain with a hand seal, and the seal **destroys the Byakugan when the bearer dies** so no one can steal
  it (ch. 102). The Hyūga Affair: Kumo tried to steal the eyes; Hizashi died in his twin's place
  (ch. 103 pp8–20; ch. 197).
- **Tenseigan** (Hamura's moon line, Toneri) `[movie: The Last]`.

### 2. Databook profile
Neji 21 → 27, Hinata 14 → 21.5, Hiashi 30 (taijutsu 5, seals 5). Genjutsu stays at 2.

### 3. Weaknesses and costs in canon
Blind spot; weak at range; little elemental ninjutsu; the caged bird oppresses the branch; eyes are
coveted by Kumo and others.

### 4. Progression in canon
Byakugan from birth; range and precision are trained; Rotation is taught in the main house (Neji
reinvented it); a branch member's seal is permanent.

### 5. Game-changer mechanics `[ours]`
Existing: Byakugan (40-block silhouettes through walls, guard from any direction), Gentle Fist with
points, Rotation, 64 Palms. Add:

1. **Growing range.** Byakugan range starts at **24 blocks** and grows with time open in real fights:
   24 → 40 → 64 → 96 → 160 (eye XP 0 / 300 / 1200 / 3000 / 6000). A **Far Sight** mode (crouch with the
   eye open): look along your view for up to 4× range, but you can't move.
2. **The blind spot.** A 30° cone behind the head: attacks from there get no warning and can't be
   guarded by the "any direction" rule. Enemies who know the clan (jōnin NPCs) aim for it. Training to
   Byakugan level 5 shrinks it to 15°.
3. **Tenketsu sniping.** Hold the attack key with the eye open to aim at a point: a heavy palm closes
   8 points at once (24 = jutsu sealed). **Opening a point** on an ally: +25% chakra regen for 30 s
   (or forcibly open an enemy's to make them overflow: −15 chakra/s for 3 s).
4. **Main or branch.** At birth, 25% main house, 75% branch.
   - **Main:** Rotation known from the start; a "punish" seal command over branch NPCs; −10%
     taijutsu training (pampered).
   - **Branch:** the **Caged Bird Seal**: your eyes are sealed when you die (they never drop as items
     and can't be stolen), main-house NPCs can stun you once a day if you defy them, **+30% taijutsu
     training** (Neji). You must **reinvent Rotation**: 200 perfect guards against attacks from behind
     or the side, then it's learned.
   - Story choice: a branch player can later have the seal removed (a sealing master; Uzumaki can do it).
5. **128 Palms** at Gentle Fist mastery; **Vacuum Palm** (a ranged palm, 8 blocks, posture damage) to
   cover the range weakness.
6. **Eye theft.** Kumo agents target Hyūga without the caged bird (main house) 1 dawn in 12.
7. **Endgame (movie lore):** the **Tenseigan**: a Hyūga with an Uzumaki parent who defeats a moon
   Ōtsutsuki boss can awaken it (orbiting truth-seeking spheres, gravity push and pull). Mark as
   `[movie-only]`.
8. **Place.** Hyūga compound: a walled estate with a branch-house wing, a koi pond, and a training yard
   of wooden posts ("Rotation dojo") that counts your guard drills.

### Sources
ch. 58, 102, 103, 195–197, 396, 614; B-ch. 53; DB1 p265; ep. 136–141 `[anime-only]`; *The Last*
`[movie]`; Narutopedia: *Hyūga Clan*, *Byakugan*, *Gentle Fist*.

---

## Might (and the Lee line) · Tier A

### 1. Canon facts
- **The Eight Gates** are tenketsu that limit chakra flow; normally the body uses at most 20% of its
  potential (ch. 52, 85). Opening them gives more strength and speed at the body's expense (fatigue,
  torn muscles, broken bones, ch. 84, 87); **the eighth kills** the user. Likened to kinjutsu.
- **Gate effects** (ch. 84, 507, 512; DB3 p224): 1 Opening (Front Lotus) · 2 Healing (strength,
  recovers exhaustion) · 3 Life (red skin, Reverse Lotus) · 4 Pain (speed; muscles tear) · 5 Limit
  (strength; limbs fracture after) · 6 View (all abilities up, Morning Peacock: punches so fast they
  burn the air) · 7 Wonder (blue sweat aura, Daytime Tiger; afterwards muscle fibres rupture) · 8 Death
  (heart at maximum, ~100× normal power, red "Steam of Blood", Evening Elephant and Night Guy).
- **Who.** Might Duy needed 20 years to open all eight (ch. 668); Guy learned from him; Lee opened five
  at 13 (Kakashi called it impossible by hard work alone), six in Part II (ch. 669), all eight as an
  adult (B-ep. 70 `[anime-only]`); Metal Lee opens the first subconsciously (B-ep. 70). Kakashi can open
  the first. Guy needed six gates against a 30% Kisame clone (ch. 258 p11) and seven against Kisame
  (ch. 506 p13). Night Guy nearly killed Madara; Naruto saved Guy with Six Paths Yang power, but Guy
  ended up in a wheelchair (ch. 670–700 `[unverified chapters]`).
- **Rock Lee** can't use ninjutsu or genjutsu at all; his clan (the **Lee clan**, with Metal Lee) is
  separate from the Might family, which canon doesn't call a clan.

### 2. Databook profile
Guy 3/5/3/3/5/5/5/2.5 (31.5); Lee ninjutsu 0.5 and genjutsu 0.5–1 with taijutsu 5.

### 3–4. Costs and progression
Each gate is learned with a master and brutal training; costs scale from fatigue to death. No
inheritance: it's a lineage of teaching.

### 5. Game-changer mechanics `[ours]`
Existing: taijutsu training ×4, Gate mastery, born with the Gate of Opening, no ninjutsu. Add a full
gate ladder (gates need **Guy-type master** NPC approval and a trial each):

| Gate | Trial (examples) | While open | Afterwards |
|---|---|---|---|
| 1 Opening | born | +15% damage, Front Lotus | tired: −20% speed 10 s |
| 2 Healing | 500 hits on a dummy in one session | +20% damage, +50% stamina regen, removes exhaustion | — |
| 3 Life | survive 3 min vs a stronger sparring ninja | +30% speed, Reverse Lotus, red skin | 2 damage |
| 4 Pain | run 5 km with leg weights | +40% speed, 0.5 health/s burn | muscles torn: −20% speed 60 s |
| 5 Limit | TAI 60 | +60% damage | fractures: −2 max health until you sleep |
| 6 View | TAI 80, Resolve 80 | ×2 damage, Morning Peacock (fire-punch barrage), water around you is pushed back | −4 max health until you sleep |
| 7 Wonder | defeat a class-S enemy with gates only | ×3 damage, Daytime Tiger (air-cannon fist, 30 blocks) | 60 s of Weakness III, touch hurts |
| 8 Death | your master teaches it at the end of the Might line quest | 30 s: ×10 damage and speed, Evening Elephant, Night Guy (one dash strike: 200 damage) | **you die** when it ends, unless a Six Paths Yang healer (rare ally) saves you; then you live with −40% speed for 7 in-game days |

Plus: **leg weights** (item: −20% speed, taijutsu XP ×1.5; dropping them gives +40% speed for 30 s);
**Eternal Rival**: name a rival; each duel (win or lose) gives Resolve and XP ×2 that day; and the **Lee
line**: a Lee-clan member has ninjutsu locked forever but starts with gate 2 and trains taijutsu ×5.

### Sources
ch. 52, 84, 85, 87, 258, 506, 507, 512, 667–669; DB3 p224; B-ep. 70 `[anime-only]`; Narutopedia:
*Eight Gates*, *Might Guy*, *Rock Lee*, *Lee Clan*.

---

## Jūgo's clan (unnamed) · Tier A

### 1. Canon facts
- An unnamed clan whose bodies **absorb natural energy constantly**, which causes **sudden,
  uncontrollable fits of murderous rage**. Their bodies secrete fluids that allow drastic physical
  changes. Jūgo went to Orochimaru to be cured; studying him led Orochimaru to Ryūchi Cave and senjutsu,
  and Jūgo's enzyme is the source of all **cursed seals** (ch. 579 p17).
- **Sage Transformation:** instant, partial or full: piston arm, axe arm, arm boosters to throw allies
  or punch harder, back boosters for speed, stretching arms, a shield arm, and the Multiple Lotus
  cannons on his back (ch. 351; ch. 462; S-ep. 143). In full form he loses his mind (dark skin, black
  sclera) but took the Raikage's lightning lariat through arm and chest and struck back (ch. 462 pp3–5).
- **Talks to animals** (birds scout for him, ch. 365 p10; ch. 457 p3); transfers senjutsu chakra into
  others (ch. 648 pp1–2); gave his flesh to heal Sasuke (ch. 413–414 `[unverified]`); absorbs chakra and
  heals by touch (B-ep. 99 `[anime-only]`).
- Only Kimimaro and later Sasuke's Sharingan could calm him (ch. 351 p7).

### 2–4. Profile, costs, progression
No databook table for Jūgo. Cost: madness. Progression: none needed; it's constant, and stronger with
age (stronger after the war).

### 5. Game-changer mechanics `[ours]`
1. **Nature meter** (0–100): fills 1/s outdoors (×2 in forests and mountains, ×0.3 indoors or
   underground). Sage Transformation jutsu spend it. At **100** you **frenzy** for 15 s (existing Sage
   Frenzy): ×1.5 damage, you attack the nearest creature, **allies included**, and can't use items.
   Lowered by: meditation (−5/s, still), a **calmer** within 12 blocks (an open Sharingan, a Kaguya, or a
   crew mate with 80+ respect: fill rate halved), or the **calming seal** item (a sealing master).
2. **Shape-shift slots** (cost nature): Piston Arm (charged punch, 14 damage, knockback 8),
   Axe Arm (cleave 3-block arc), Boosters (dash 12 blocks, or throw an ally 20), Shield Arm
   (blocks one jutsu), Cannon Back (5 explosive blasts; needs full transformation).
3. **Bird scouts:** release a bird that flies 96 blocks and marks every creature it sees for 30 s.
4. **Cell gift:** heal an ally fully; you shrink (visibly), lose 4 max health and half your nature,
   recovering over an in-game day.
5. **Curse-mark crafting:** a Jūgo's-clan player with Sage Mode can brand another player with a cursed
   seal (level 1: +25% all stats for 30 s on demand, then −10% for 60 s; risk of the nature meter for
   them too).
6. **Place:** a hidden prison cell in an Orochimaru-style lair where the clan's last NPC is kept;
   Ryūchi Cave (snake sages) as the Sage Mode trainer for this clan.

### Sources
ch. 351, 365, 413–414, 457, 462, 485, 579, 648; S-ep. 143, B-ep. 99 `[anime-only]`; Narutopedia:
*Jūgo's Clan*, *Jūgo*.

---

## Hoshigaki · Tier A−

### 1. Canon facts
- A Kiri clan with shark features (pointed teeth, gill marks). In the peaceful Sixth Mizukage era the
  name is no longer honoured because of the family's bloodthirsty reputation (B-ep. 28).
- **Kisame:** chakra well above the other Akatsuki (ch. 255 p13); even a clone with 30% of his chakra
  compared well with Naruto's (ch. 256 p2; ch. 260 p16). Nickname "the **Tailed Beast without a
  Tail**". Absorbs chakra (ch. 506 pp4–6). Beat his master and took **Samehada**, a living sword that
  shaves flesh and eats chakra; overpowered Asuma and Guy in melee (ch. 141; ch. 258); captured Rōshi
  with ease (ch. 353); survived a Version 2 lariat and Daytime Tiger. Water from his own chakra (a sea
  out of nothing: Exploding Water Shockwave), Water Prison Shark Dance, and **fusion with Samehada**
  into a shark-man that swims and breathes in his water. Samehada later chose Killer B over Kisame
  because it liked B's chakra better (ch. 506 `[unverified]`).

### 2. Databook profile
Kisame 32.5: strength 5 and stamina 5.

### 3–4. Costs and progression
Feared and shunned; the sword is loyal only to chakra it likes. No inherited awakening; the power is
raw chakra plus a sword.

### 5. Game-changer mechanics `[ours]`
1. **Gills:** water breathing for good; in water +30% speed and damage, and jutsu −25% cost.
2. **Sea from chakra:** Exploding Water Shockwave fills a 10-block radius with temporary water
   (60 s, then it drains) — turn any battlefield into your home ground. 80 chakra.
3. **Living sword heirloom** ("Scale-Shaver", our own design, not Samehada's art): absorbs 10 chakra
   per hit and gives it to you; held up, it eats the next chakra jutsu aimed at you (40 chakra cap);
   it must be fed 20 chakra per in-game hour or it **refuses you** (falls from your hand) and may bond
   with a stronger-chakra holder who picks it up.
4. **Shark fusion:** with the sword, inside your water: a shark form (swim at sprint speed, bite heals).
5. **Cost:** village NPCs −30 respect (existing crew penalty); Kiri hunter-nin pursue rogue Hoshigaki.

### Sources
ch. 141, 255, 256, 258, 260, 353, 506; B-ep. 28; Narutopedia: *Hoshigaki Clan*, *Kisame Hoshigaki*,
*Samehada*.

---

## Kazekage line (true surname unknown) · Tier A−

### 1. Canon facts
- An influential Suna family that produced the Fourth and Fifth Kazekage; its real surname is unknown
  (DB1 p52; *Gaara Hiden* `[novel]`). "Sabaku" is a fan surname from "Gaara of the Desert" `[fanon]`.
- **Magnet Release** (ch. 546 p1), likely Wind + Earth (Narutopedia's inference). The **Third
  Kazekage**, Suna's strongest of his era (DB3 p64; ch. 266, 268), created **Iron Sand** by imitating
  Shukaku's host. **Rasa** (Fourth) used **Gold Dust**, heavier than Gaara's sand, made to counter
  Shukaku (ch. 546 pp9–10). Gaara inherited Magnet Release and uses a metallic sand (*Gaara Hiden*).
  Shinki uses Iron Sand in *Boruto*.
- **Gaara:** Shukaku sealed in him before birth; an automatic **sand shield**, sand armour, Sand Coffin
  and Funeral, sand flight; insomnia for fear of Shukaku taking over `[unverified chapter]`.

### 2. Databook profile
Gaara 20 → 29 (stamina 5, ninjutsu 5, taijutsu 1–2). Temari 24.5, Kankurō 26.5 (puppets).

### 5. Game-changer mechanics `[ours]`
1. **Gourd reservoir** item: holds up to 512 "grains" (sand or iron sand blocks ground down). Jutsu
   spend grains instead of half their chakra.
2. **Absolute shield:** with the gourd worn, one hit per 5 s is blocked automatically by sand (not
   guard-breaking hits or genjutsu).
3. **Iron Sand:** magnetise metal: pull an enemy in iron armour 6 blocks; 20% chance to rip an iron
   weapon from their hand; spikes and a crushing coffin.
4. **Gold Dust** (gold nuggets as grains): ×2 damage against sand and iron sand users, slows them.
5. **Sand cloud:** fly slowly (½ walk speed) at 2 grains/s.
6. **Jinchūriki tradition:** Kazekage-line NPCs are 3× likelier to be Shukaku hosts; a host can't
   sleep through the night without a seal (Phantom insomnia: no "rested" bonus).
7. **Cost:** stamina −30, speed training −20% (existing); the gourd slows you 10%.

### Sources
ch. 266, 268, 546; DB1 p52; DB3 p64; *Gaara Hiden* `[novel]`; Narutopedia: *Kazekage Clan*,
*Magnet Release*, *Third Kazekage*, *Gaara*.

---

## Yuki (雪, "snow") · Tier A−

### 1. Canon facts
- A Land of Water clan with **Ice Release** (Wind + Water, ch. 316 p8). After a civil war, kekkei genkai
  holders were persecuted; the Yuki hid (ch. 29 pp8–10). Still called "cursed" during the war
  (ch. 522 p2). Haku's mother was killed by her husband when her power was revealed.
- **Haku:** **Demonic Mirroring Ice Crystals** (the clan's ultimate, DB1 p221): a dome of ice mirrors
  he moves between almost instantly (DB1 calls it light speed), throwing senbon from every side; the
  mirrors don't melt under ordinary fire (ch. 25 p8); costly in chakra (ch. 27 pp4–5). One-handed hand
  seals `[unverified]`. Ice users chill their surroundings so snow falls (ch. 522 p7). He used senbon to
  put Zabuza into false death (ch. 20s `[unverified]`), and knew medicinal herbs.

### 2. Databook profile
Haku 21: speed 4, seals 4, intelligence 4, strength 1.5, taijutsu 1.

### 5. Game-changer mechanics `[ours]`
1. **Snow aura:** while Ice is active, snow layers form within 4 blocks; water you walk on freezes.
2. **Mirror Dome:** 8–16 mirrors around a target (70 chakra + 4/s); you **teleport between them**
   instantly by looking at one and pressing the key; senbon from a mirror deal +50%; breaking a mirror
   costs the caster 5 chakra.
3. **False death (senbon):** a precise senbon knocks an NPC unconscious instead of killing it, for
   capture missions or bounties alive.
4. **Hunted:** 1 dawn in 15, Kiri hunter-nin come (the hunter mask is a lootable cosmetic).
5. **Cost:** frail (health −4), fire +30% (existing); dry biomes (desert) halve ice power.

### Sources
ch. 25, 27, 29, 316, 522; DB1 p221; Narutopedia: *Yuki Clan*, *Ice Release*, *Haku*, *Demonic
Mirroring Ice Crystals*.

---

## Chinoike (血の池, "pool of blood") · Tier A−

### 1. Canon facts
Source: the novel *Sasuke Retsuden* (2019) and its *Boruto* anime adaptation (B-ep. 282–287); not in
the main manga. `[novel]`
- A Warring States mercenary clan in the Land of Lightning. One of them married the daimyō; when he
  died, his first wife blamed the clan, and the **Uchiha were hired to exile them** to the "Valley of
  Hell" in the Land of Hot Water. They survived there for decades but finally destroyed themselves in a
  single night of infighting (in the anime, over whether to take revenge on the Uchiha). Survivors: En
  Oyashiro and his daughter Chino.
- **Ketsuryūgan:** red eyes that cast genjutsu by gaze or by skin contact, can search memories, and
  control **anything rich in iron**, including a living target's blood, after the user infuses their
  chakra into it (Chino cut Sasuke's wrist to do so). Compared to the Three Great Dōjutsu.
- **Correction for `CLANS.md`:** it calls the Chinoike "Kagura's line". Kagura Karatachi is unrelated
  (a Kiri family); the Chinoike survivor is **Chino**.

### 5. Game-changer mechanics `[ours]`
1. **Blood mark:** any damaging hit with a blade marks the target for 30 s.
2. **Blood control** on a marked target: freeze 2 s, force a step toward you, or a bleed (1 dmg/s, 8 s).
   On a marked NPC: read its mind (reveals its loot table and mission intel).
3. **Iron control:** move iron blocks and pull iron weapons (weaker than Magnet Release).
4. **Eye genjutsu:** by gaze (existing genjutsu ×2 perk) or by touch (bypasses "facing you").
5. **Cost:** exile: hidden villages refuse to trade with you until respect 40; Uchiha NPCs hostile;
   the Valley of Hell (red-water canyon structure, our design) is your only safe home.

### Sources
*Sasuke Retsuden* `[novel]`; B-ep. 282–287 `[anime]`; Narutopedia: *Chinoike Clan*, *Ketsuryūgan*,
*Chino*.

---

## Kurama (鞍馬) · Tier A− · `[anime-only]`

### 1. Canon facts
- Anime-only (ep. 203–207). A once-prominent Konoha clan of genjutsu specialists with a kekkei genkai.
  Every few generations, one member is born whose illusions make the brain **turn what happens in the
  genjutsu into real injuries**, so they can kill with genjutsu.
- **Yakumo Kurama:** frail body; illusions cast through **paintings**; once put **all of Konoha** under
  one genjutsu; only equals in genjutsu can escape; kikaichū are immune. Her power created a second
  personality ("Ido") that took over; the Third Hokage sealed her ability.

### 5. Game-changer mechanics `[ours]`
1. **Painted illusions:** craft a canvas (paper + dyes), "paint" a scene (choose: fire, falling rocks,
   a cage), then unveil it: a 16-block area genjutsu where damage is real (3/s for 6 s, or roots for
   cage), once per minute.
2. **Ido** meter: rises with each painted illusion; at 100 the second self takes over for 20 s (you hit
   allies too). A sealer (Uzumaki/Sarutobi NPC) can seal it, which halves painting power.
3. **Insect immunity:** Aburame and Kamizuru insects ignore your illusions.
4. **Cost:** health −6, stamina −50 (existing).

### Sources
ep. 203–207 `[anime-only]`; Narutopedia: *Kurama Clan*, *Yakumo Kurama*.

---

# B tier

## Nara (奈良) · Tier B+

### 1. Canon facts
- Konoha clan known for tending deer and manipulating shadows with Yin Release. Only Nara (and guests
  with permission) may enter the Nara Clan Forest without the deer attacking. They keep a generations-old
  **medicine book**, which includes the Akimichi's secret pills; the clan's research let Tsunade save
  Chōji. Anime: a mountain research lab full of medicines (ep. 146) `[anime-only]`.
- **Ino–Shika–Chō**: a hereditary trio with the Yamanaka and Akimichi; a Sarutobi gives each new
  generation's heads earrings when they become chūnin (ch. 533 p12).
- **Shikamaru:** IQ over 200, found by Asuma through games; poor grades from laziness (ch. 107 p14).
  **Shikaku:** jōnin commander and the Allied Forces' strategist. Shadow Imitation (bind), Shadow Neck
  Bind, Shadow Sewing, Shadow Gathering; Black Spider Lily `[anime-only]`. Shadow range depends on the
  shadow available (light, other shadows); Shikamaru extends his through other shadows (ch. 107 onward).
  Upon marrying Temari, the Kazekage blood entered the clan.

### 2. Databook profile
Shikamaru intelligence 5 (DB1–3), strength and taijutsu 1.5–2.

### 5. Game-changer mechanics `[ours]`
1. **Shadow range from light:** base 8 blocks + 1 per point of darkness at your feet (light 15 → +0,
   light 0 → +15) + the length of any entity or block shadow you chain through. At night, a Nara is
   terrifying; at noon, close-range.
2. **Multi-bind:** Shadow Possession on up to 3 targets (drain 3 chakra/s per target).
3. **Strategy view** ("IQ 200"): in combat mode, a crouch-hold shows enemy stats, current posture, the
   next attack's type, and weak points; and a **plan marker** on the map that your crew's NPCs follow.
4. **Deer forest:** Nara deer only; their shed antlers craft pills (antidote, chakra pill, Akimichi
   pill cure).
5. **Cost:** stamina −30, taijutsu training −40% (existing); a bind breaks if you're hit by someone else.

### Sources
ch. 107, 533; ep. 146 `[anime-only]`; Narutopedia: *Nara Clan*, *Shikamaru Nara*, *Shikaku Nara*,
*Shadow Imitation Technique*.

---

## Akimichi (秋道) · Tier B+

### 1. Canon facts
- Konoha clan whose techniques change body weight and size through Yang Release; 15 heads so far
  (First Fanbook pp28–29). Great physical strength; they **convert calories into chakra** quickly,
  their techniques burn chakra fast, so they eat a lot. Kanji for "food" on their clothes; cheek
  markings; plate armour; a bō that lengthens with their size.
- **Calorie Control** is the base of every clan technique; beginners need the **Three Coloured Pills**,
  which convert *all* calories; masters regulate it themselves.
- **Three Coloured Pills:** green Spinach (ch. 188 p18), yellow Curry, red Chilli (ch. 189 p5). Each
  poisons the body; the Chilli supposedly multiplies power a hundredfold, burns all fat, and death
  follows. Tsunade saved Chōji using Nara research (ch. 534 p1).
- **Butterfly Mode:** chakra wings, huge power (Butterfly Bullet Bombing); risk of death from weight
  loss unless done through Calorie Control. Anime: done wrong, it suppresses calories instead
  (B-ep. 67, 156) `[anime-only]`.
- Techniques: Multi-Size, Human Boulder, Partial Expansion, Super Multi-Size.

### 2. Databook profile
Chōji strength 3 → 4.5, speed 1 → 2.

### 5. Game-changer mechanics `[ours]`
1. **Calorie store** (0–200): eating when already full stores calories instead of wasting them.
   Expansion jutsu spend calories before chakra (1 calorie = 2 chakra).
2. **Pill ladder** (Akimichi-only items, recipes in the clan's shop):
   | Pill | Effect (90 s) | Cost |
   |---|---|---|
   | Spinach | Multi-Size free, +30% damage | poison 0.5/s for 10 s |
   | Curry | ×2 damage, giant form | Hunger to 0, Weakness after |
   | Chilli | **Butterfly Mode** 60 s: flight, wing gusts, ×3 damage | then 1 health, max health −50%, and **death in 5 minutes** unless you eat a Nara antler cure or reach a medic |
3. **Calorie Control mastery:** after 10 Butterfly Modes survived, you can enter it without pills at
   the cost of all stored calories (no death clock).
4. **Yakiniku Q** (BBQ restaurant structure): a meal with crew gives everyone +XP and a full calorie store.
5. **Cost:** slow (existing).

### Sources
ch. 188, 189, 534; First Fanbook; B-ep. 67, 156 `[anime-only]`; Narutopedia: *Akimichi Clan*,
*Calorie Control*, *Three Coloured Pills*, *Butterfly Mode*.

---

## Aburame (油女) · Tier B+

### 1. Canon facts
- At birth, members are offered as a nest to special insects that live under the skin for life
  (First Fanbook). Body = a hive of **tens of thousands**; insects leave through pores, feed on chakra,
  obey, and let the host use "ninjutsu" without seals. Dark glasses, covered bodies, reserved manner.
- **Kikaichū** (ch. 70 p6): spread to surround and drain a target's chakra (ch. 395 pp2–7); can **eat
  chakra-based jutsu** (ch. 126 p14; ch. 427 p13); latch onto surfaces; **remove poison** (ch. 137 p11);
  form shields and decoy clones (ch. 126 p4); a female left on a target is tracked by the males' smell;
  overflow lives in an insect jar (ch. 517 p10). Anime: insects leave if the host takes a hot bath
  (S-ep. 236), alcohol breaks control (B-ep. 50), they can give stolen chakra to allies (S-ep. 304).
- Rare insects: **rinkaichū** (nano poison insects; Torune's **Jar of Poison** kills a large area);
  kidaichū. In the anime they defeated the bee-using **Kamizuru**.

### 2. Databook profile
Shino ninjutsu 3.5–4, intelligence 3–4, taijutsu 1.5.

### 5. Game-changer mechanics `[ours]`
1. **Colony size** (1,000 → 50,000): grows by feeding chakra (spend chakra while idle = growth).
   Jutsu scale with colony: drain per hit, swarm size, decoy count.
2. **Hive domain:** a 6-block dome of insects that drains 6 chakra/s from everything inside and eats
   incoming chakra jutsu (consumes colony).
3. **Insect clone** decoy and **tracking bug** (mark a target; see its position for 10 minutes).
4. **Poison purge:** remove poison from yourself or an ally.
5. **Rinkaichū** (1 in 10 Aburame): a Jar of Poison cloud (8 blocks, 4 dmg/s) that **also hurts allies
   and you** unless you hold an antidote.
6. **Cost:** fire +60% (existing); a hot spring or alcohol makes the colony scatter (no insect jutsu
   for 60 s).

### Sources
ch. 70, 126, 137, 395, 427, 517; S-ep. 236, 304, B-ep. 50 `[anime-only]`; Narutopedia: *Aburame
Clan*, *Kikaichū*, *Torune Aburame*.

---

## Hatake (はたけ, "farmland") · Tier B+

### 1. Canon facts
- Two known members: **Sakumo**, "Konoha's White Fang", whose fame overshadowed all three Sannin
  (ch. 240 p17) and who fought with the **White Light Chakra Sabre**, a tantō that left a white streak of
  chakra; and **Kakashi**, the Copy Ninja and Sixth Hokage. The sabre broke in the Third War (repaired
  in the anime, S-ep. 349).
- Kakashi: genius, a thousand copied jutsu, Lightning Blade, eight ninken summons; his own chakra was
  **average** by his own admission; the implanted Sharingan (which he can't close) drains him, and he
  often collapses after using it.

### 2. Databook profile
Kakashi 33 → 34.5: almost all 4–5, stamina 3.

### 3. Note for the current design
`CLANS.md` gives the Hatake chakra ×0.6. Canon says "average"; the small reserves come from the
**implanted Sharingan's drain**. Suggest chakra ×0.85 and make the Hatake gift **implant tolerance**
instead (below).

### 5. Game-changer mechanics `[ours]`
1. **White Light Chakra Sabre** heirloom (Hatake start item or quest): tantō, 7 damage, swings leave a
   white chakra trail (+1 reach), cuts through 30% of armour, and parries jutsu projectiles.
2. **Implant tolerance:** an implanted Sharingan drains half (0.25 chakra/s) and can be covered (a
   forehead-protector toggle closes it).
3. **Ninken pack:** summon up to 8 small tracking dogs (existing hound perk becomes "pack": scouts that
   pin a target for 3 s).
4. **Genius:** mastery +60% (existing), and copying via Sharingan costs no XP.
5. **The White Fang's choice** (story): a mission event where saving your crew fails the mission; choose
   comrades → +crew respect, village −respect (Sakumo's tragedy).

### Sources
ch. 240; S-ep. 349 `[anime-only]`; Narutopedia: *Hatake Clan*, *Sakumo Hatake*, *Kakashi Hatake*,
*White Light Chakra Sabre*.

---

## Sarutobi (猿飛, "monkey jump") · Tier B+

### 1. Canon facts
- Among the first clans to join Konoha with the Shimura (ch. 625 p8); famous for high willpower,
  exceptional strength and Fire hiden ninjutsu (DB4 p316). They present the Ino–Shika–Chō earrings
  (ch. 533 p12). Sarutobi clansmen helped burn the Ten-Tails' cement shell (ch. 612 pp12–13).
- **Hiruzen:** "God of Shinobi" and "the Professor", mastered every skill set and all five natures; his
  summon **Monkey King Enma** becomes the indestructible Adamantine Nyoi staff; used the Dead Demon
  Consuming Seal on Orochimaru at the cost of his life. **Asuma:** Wind chakra trench knives, Ash Pile
  Burning. **Konohamaru:** Rasengan and Enma's line.

### 2. Databook profile
Hiruzen 34 (5s in nin, tai, gen, int, seals); Asuma 32.5.

### 5. Game-changer mechanics `[ours]`
1. **The Professor:** learning a new nature takes half the time; affinity cap for trained natures 0.75
   (others 0.5).
2. **Enma staff** (summon contract quest): unbreakable staff that extends to 10 blocks, and turns into
   the **Adamantine Prison** (a cage around one target, 6 s).
3. **Chakra blades:** Wind-flow trench knives (+1 reach, +25% damage).
4. **Will of Fire aura:** crew within 16 blocks gain +20% Resolve gain.
5. **Cost:** none in canon; keep speed training −20% (existing).

### Sources
ch. 533, 612, 625; DB4 p316; Narutopedia: *Sarutobi Clan*, *Hiruzen Sarutobi*, *Monkey King: Enma*.

---

## Hōzuki (鬼灯) · Tier B+

### 1. Canon facts
- Kiri clan. **Hydrification**: any part of the body becomes liquid, often automatically, so blows pass
  through; used to sneak in, to **pump water into muscles** for strength (ch. 348 p15), to shoot water
  from the fingers (ch. 556 p9). They must stay hydrated (water bottles, ch. 347 p12; ch. 523 p14);
  **very vulnerable to Lightning** (ch. 463 p10); knocked out, they turn to jelly (ch. 414 p15); sealed
  in a container they can't escape.
- **Mangetsu** mastered all seven swords of the Swordsmen of the Mist; **Gengetsu** was the Second
  Mizukage (water, fire, lightning, earth, yin, yang; steam-clam genjutsu); **Suigetsu** uses water jutsu
  without seals (B-ep. 74).

### 5. Game-changer mechanics `[ours]`
1. **Hydration meter** (0–100): drains 1/min, faster in deserts; refill by drinking or standing in water.
   The liquid body (existing 75% near water) instead uses hydration: dodge chance = hydration × 0.75%.
2. **Muscle pump:** spend 20 hydration for 10 s of ×1.5 melee and a bulked arm.
3. **Water gun** (existing) costs hydration, not chakra.
4. **Swordsman heir:** Hōzuki learn any Seven-Swords-style heavy blade at double speed.
5. **Cost:** lightning ×2 damage and **stops the liquid body for 5 s**; at 0 hydration you're dry
   (−4 health, no liquid body).

### Sources
ch. 347, 348, 414, 463, 523, 556; B-ep. 74; Narutopedia: *Hōzuki Clan*, *Hydrification Technique*,
*Mangetsu Hōzuki*, *Gengetsu Hōzuki*, *Suigetsu Hōzuki*.

---

## Yamanaka (山中) · Tier B

### 1. Canon facts
- Mind techniques for intelligence, espionage and interrogation; sensing; telepathy; they run a flower
  shop and lead the Konoha Barrier Team. Clan flower: bush clover ("positive love", ch. 616).
- **Mind Body Switch:** the user's consciousness travels slowly in a straight line; if it misses, it
  takes **several minutes** to return; on a hit, the user controls the body, can use their own jutsu
  (and sensing) through it, can talk through it; strong wills can expel them; the user's body lies inert
  and damage to the host body hurts the user too (ch. 42, 55, 72–73, 332, 523, 533). That's why they team
  with the Nara and Akimichi.

### 2. Databook profile
Ino 15 → 21; strength starts at 0.5.

### 5. Game-changer mechanics `[ours]`
1. **Possess mobs:** fire the slow mind projectile (8 blocks/s); on hit, play as that mob for 60 s
   (fly as a bird or phantom, swim as a fish, open doors as a villager). Miss: you're stunned for 20 s.
2. **Inert body:** your own body stays behind; a crew mate (Nara) can carry or shadow-move it.
3. **Crew telepathy:** chat and map pings to your whole crew at any range.
4. **Sensor barrier:** place a 32-block barrier that alerts you when anyone crosses it.
5. **Flower shop:** flower bundles give small buffs (language of flowers) and are the clan's trade.
6. **Cost:** frail (existing), host damage comes back at 50%.

### Sources
ch. 42, 55, 72–73, 332, 523, 533, 616; Narutopedia: *Yamanaka Clan*, *Mind Body Switch Technique*.

---

## Inuzuka (犬塚) · Tier B

### 1. Canon facts
- Red fang markings; a ninken partner given at a certain age; inseparable; cooperation jutsu (Passing
  Fang, Fang Over Fang); they understand dogs; enhanced senses, especially smell, sharpened further with
  chakra (ch. 58 p7; ch. 364 pp13–14); fierce, reckless temperament (*One Decade* pp30, 78); Four Legs
  Technique, Beast Human Clone, Two-Headed Wolf (Kiba and Akamaru, `[unverified chapter]`). Tsume's
  partner Kuromaru talks.

### 2. Databook profile
Kiba speed 3.5 → 4.5, genjutsu 0.5 → 2.

### 5. Game-changer mechanics `[ours]`
Existing: the growing hound (`INUZUKA.md`). Add **Two-Headed Wolf** (hound level 30+: you and the hound
merge into one giant wolf for 20 s: Fang Over Fang becomes a rolling AoE), **scent trails** (a target
you sniffed leaves a visible trail for 5 minutes, 200 blocks), **Beast Human Clone** (the hound becomes
a copy of you, confusing NPC targeting).

### Sources
ch. 58, 364; *One Decade, One Hundred Ninja*; Narutopedia: *Inuzuka Clan*, *Fang Over Fang*.

---

## Yotsuki (四月) · Tier B

### 1. Canon facts
- A Kumo clan given land in the Valley of Clouds and Lightning after swearing loyalty to the First
  Raikage (DB4 p17). **Kiyoi Yotsuki** refused to betray Killer B even under threat; the clan never
  willingly sells out comrades (Sasuke used genjutsu to make him talk).
- **The Raikage line (A, Killer B) is not stated to be Yotsuki in any source we found** `[fanon / game
  convention]`. Lightning Release Chakra Mode is the Raikage's technique, not a clan hiden.

### 5. Game-changer mechanics `[ours]`
Keep the bruiser perks, and add: **unbreakable loyalty** (immune to interrogation genjutsu; crew mates
near you +10% genjutsu resistance) and **Lightning Armour** as a learnable Kumo technique with a Yotsuki
discount (−30% cost). Flag the "Raikage line" wording in `CLANS.md` as fanon.

### Sources
DB4 p17; ch. 412 `[unverified]`; Narutopedia: *Yotsuki Clan*, *Lightning Release Chakra Mode*.

---

## Shimura (志村) · Tier B

### 1. Canon facts
- Joined Konoha early with the Sarutobi; well known enough that Madara was shocked (ch. 625 p8). Danzō's
  father and grandfather died in battle (ch. 481 p7).
- **Danzō:** Wind Release; founder of Root (agents carry a curse seal on the tongue that stops them
  talking about Root `[unverified]`); an arm with **ten Sharingan** and **Hashirama's cells** for
  Izanagi (about a minute each, ch. 479 p5) and to control tailed beasts; Shisui's eye with
  Kotoamatsukami; died using the **Reverse Four Symbols Sealing** to drag his killers into the seal
  (ch. 481 `[unverified]`).

### 5. Game-changer mechanics `[ours]`
1. **Implant specialist:** Shimura take Sharingan and Hashirama Cells with half the rejection risk; an
   arm can hold up to 5 implanted Sharingan (each one is one Izanagi, then goes blind).
2. **Root seal:** immune to interrogation and mind reading (Yamanaka, Chinoike).
3. **Reverse Four Symbols:** on death, everyone within 6 blocks is pulled in: 40 damage and bound 5 s.
4. **Cost:** crew respect −40% (existing); Uchiha NPCs hate you.

### Sources
ch. 479, 481, 625; Narutopedia: *Shimura Clan*, *Danzō Shimura*.

---

## Iburi (燻) · Tier B− · `[anime-only]`

### 1. Canon facts
Anime-only (S-ep. 349–361, Kakashi's Anbu arc). A secluded clan living underground in the Land of Fire.
They turn into **smoke** at will and can still touch things, but the power is unstable (unwanted
transformations); **wind disperses a smoke-form member permanently**, so they live short lives in
windless caves. They plant trees instead of gravestones. The ability is stronger the younger the user.
Orochimaru killed the whole clan except **Yukimi**, who can **possess a person** in smoke form
(S-ep. 352–354).

### 5. Game-changer mechanics `[ours]`
1. **Smoke form** toggle (2 chakra/s): melee passes through you (existing 40% smoke body becomes 100%
   while toggled), you fly slowly, you can slip through 1-block gaps.
2. **Smoke possession:** enter a target for 8 s, steering its movement (it keeps its mind).
3. **Wind kills:** Wind jutsu deal ×3 to a smoke-form Iburi, and at under 25% health they **disperse**
   (true death). Strong weather (rain, thunder) forces smoke form off.
4. **Underground village:** a cave settlement with a grove of memorial trees.

### Sources
S-ep. 349–361 `[anime-only]`; Narutopedia: *Iburi Clan*, *Yukimi*.

---

# C tier

## Kamizuru (神鶴) · Tier C+ · `[anime-only]`
**Canon** (ep. 136–141): a once-revered Iwa clan of **bee users** (claimed the First Tsuchikage was one
of them); led an Iwa raid on Konoha and was crushed by the Aburame, losing their status; the last three
sought the First Tsuchikage's scroll of insect kinjutsu via the bikōchū beetle and lost to Hinata's
Protecting 64 Palms and Naruto. They summon with a unique gesture (thumb into palm) and use a **giant
bee**. *Konoha Hiden* hints a beekeeper descendant `[novel]`.
**Mechanics `[ours]`:** clan **beehives** (craft; bees follow you as scouts and stingers); **honey**
for chakra pills (+20% pill effect); **wax armour** jutsu (blocks 1 hit); a **giant bee mount** at the
end of a clan quest; fire +30% (existing). **Sources:** ep. 136–141; Narutopedia *Kamizuru Clan*.

## Kurogane (黒鉄) · Tier C+ · `[anime-only]`
**Canon:** **Tsubaki Kurogane** (Boruto anime, B-ep. 220s): a samurai of the Land of Iron trained by
its general; judged chūnin-level (B-ep. 221); quickdraw strikes, dual sword and scabbard, a chakra
tornado through her blade. Land of Iron samurai channel chakra into blades instead of ninjutsu (ch. 457
`[unverified]`, Mifune).
**Mechanics `[ours]`:** **Iaidō**: sheathed, the first strike is instant and crits (×2); **chakra
blade** cuts through jutsu projectiles; **scabbard parry**; no ninjutsu (existing −40%). **Sources:**
B-ep. 221; Narutopedia *Tsubaki Kurogane*.

## Fūma (風魔) · Tier C
**Canon (manga):** a renowned, aggressive clan distantly related to the Uchiha but without the Sharingan
(B-vol. 10 p92); famous for the giant four-bladed **Fūma shuriken**; one member became a Pain body; after
the Uchiha massacre they were given authority over the Konoha Military Police (Kōtarō Fūma heads it).
**Canon (anime, a different clan):** the Land of Rice Fields Fūma (Sasame, Arashi, Hanzaki) use
**chakra threads**, archery and the **Curse Mandala**; some were tricked into Orochimaru's experiments.
`[anime-only]`
**Note for `CLANS.md`:** the Uchiha kinship is now manga canon (Boruto), not only games and filler.
**Mechanics `[ours]`:** a returning **giant shuriken** (thrown, boomerangs, 10 damage, +1 per Fūma
mastery); **police authority** (can arrest NPC criminals for bounties in Konoha-type villages); anime
variant: chakra-thread traps. **Sources:** B-vol. 10; Narutopedia *Fūma Clan*, *Fūma Clan (Land of Sound)*.

---

# Clans we lack (candidates)

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

# Power tiers and balance

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

# Part II: Bold reading (second pass)

Each clan below gets: **the maximal reading** (tagged), **bold mechanics** on top of Part I, and an
**ultimate form** (unlock, payoff, cost). Numbers are starting points for tuning. "Pool ×N" always
means "N times a normal shinobi's chakra at the same Reserves".

---

## Bold reading: Uzumaki · the maelstrom that never stops

**The maximal reading**
- **About 100× Kakashi's chakra, held down by a seal.** `[owner's ruling; our interpretation of canon]`
  The ch. 315 "4×" was measured while the Four Symbols Seal was still holding Naruto's chakra down and
  slowly mixing the fox's into it. Supporting canon: the seal fed the fox's chakra into his over the
  years `[implication]`; an extra seal (Orochimaru's Five Elements Seal) wrecked his chakra control
  until Jiraiya removed it (ch. 90–91 `[unverified page]`); his chakra became compatible with
  Kurama's (ch. 617 p5). Later he gave the whole Allied army a share of his and Kurama's chakra, which
  Hashirama said almost matched his own reserves (ch. 644).
- **渦巻 "whirlpool / maelstrom"** `[name meaning]`: something that pulls everything in and never runs
  down. Their seals are spirals. Their crest is on **every Konoha flak jacket** (DB4) `[canon
  statement]`, so the whole village literally wears the Uzumaki seal `[our interpretation: Konoha
  stands on Uzumaki sealing]`.
- **"Too powerful to be allowed to exist"**: why Uzushio was razed (DB4 p36) `[canon statement]`.
  Three great nations had to unite to kill one small clan `[implication]`.
- **Life force that outlasts death:** they survive having a tailed beast ripped out (ch. 501)
  `[canon]`. Nagato used the Rinnegan, the strongest eye, with no drawback as a child, and brought a
  village's dead back to life (ch. 448–449) `[canon, unverified page]`. Karin's chakra **is medicine**,
  enough to refill a Hokage (ch. 635) `[canon]`.
- **Chakra that cages gods:** Kushina's chains held the Nine-Tails, and her chakra alone kept it from
  ever taking her over (ch. 501, 503) `[canon]`. Mito's forehead diamond looks like a Strength of a
  Hundred seal `[implication]`.
- **Asura's heirs:** Naruto is Asura's reincarnation, and Six Paths power came to him (ch. 671–672)
  `[canon]`.

**Bold mechanics `[our interpretation]`**
1. **The Seven Seals of the Maelstrom.** Every Uzumaki is born with their own pool **sealed**. Pool
   ×10 at birth (current). Each broken seal raises it, and the 7th gives **×100**:
   | Seal broken | Trial | Pool | Regen |
   |---|---|---|---|
   | 1 | hit 0 chakra in a real fight and keep fighting for 30 s | ×15 | ×3 |
   | 2 | 100 shadow clones out at once | ×20 | ×4 |
   | 3 | chain ladder stage 3 (Chain Barrier) | ×30 | ×5 |
   | 4 | survive a killing blow through Whirlpool Tenacity 10 times | ×45 | ×6 |
   | 5 | seal a tailed beast (into anyone) | ×60 | ×7 |
   | 6 | meditate 10 minutes at the Uzushio whirlpool shrine | ×80 | ×8 |
   | 7 **Maelstrom Unbound** | defeat a jinchūriki, a Mangekyō Uchiha **and** a Wood user (any order) | **×100** | **×10** |
   Each seal broken adds +10% loud chakra and +heat, and rough control returns (−5 CTL per seal,
   trained away again). The pool never makes single jutsu stronger, so ×100 is endless *quantity*:
   thousands of clones and Rasengan spam, as in canon.
2. **A body that refuses to die.** Chakra at 0 never knocks an Uzumaki out. Whirlpool Tenacity
   (Part I) gains a second layer: when you would die anyway, **refuse it**: 5 s collapsed, then you rise
   with 30% health. Chance = 20% + 1% per Vitality point above 10 (max 50%), once per in-game day.
3. **Shadow Clone legions.** Uzumaki clones cost 1/20 of the pool each (others: a fixed 30). With ×100
   that means **hundreds** of clones (NPC cap 64 active; the rest "wait in the smoke"). Clones that
   dispel give their XP back (canon training trick): training ×(clones/10), capped ×5.
4. **The village wears our seal.** In a village whose flak jackets carry the swirl (Konoha-type), an
   Uzumaki can **link the village barrier**: see everything that crosses it, and lock its gates for 60 s
   once per day.
5. **Seal anything.** Sealing Jars become **Sealing Scrolls of Everything**: seal a whole 9×9×9
   structure into a scroll and unseal it elsewhere (portable houses, captured shrines), a weather
   effect (carry a storm), or a boss below 10% health (bosses fight back: 30 s seal duel).
6. **Living medicine.** Healing Bite works on the dying: an ally who "dies" next to you can be bitten
   within 5 s and stands up at 30% health (counts as your daily bite, and costs you 3 max health for the day).

**Ultimate form: Asura's Heir** `[our interpretation]`
- **Unlock:** Maelstrom Unbound (seal 7) + a tailed beast sealed in you at full bond + chain stage 5.
- **Payoff (90 s):** a golden chakra cloak; **share chakra with every ally within 64 blocks** (each
  gets your cloak's buffs and a full pool, as Naruto did for the army); **Yang Healing** that revives
  any downed ally and regrows lost max health (Naruto saved Guy and gave Kakashi an eye); a **beast
  avatar** (a 15-block chakra beast you fight inside); Tailed Beast Balls cost nothing.
- **Cost:** afterwards the beast is exhausted (no cloak for one in-game day), heat ×2 for 3 days, and
  every hunter faction in the world learns where you are.
- **Second ultimate (with a Rinnegan only): Samsara of Heavenly Life.** Bring back every crew mate and
  village NPC who died in the last 10 minutes within 64 blocks. **Cost:** a normal shinobi dies; an
  Uzumaki lives, but at **half max health for good** (Nagato's withered body) and with seal 7 re-locked.

---

## Bold reading: Senju · the clan of a thousand skills

**The maximal reading**
- **千手 "a thousand hands"** `[name meaning]` → Hashirama's thousand-armed statue (ch. 626) `[canon]`.
  Also **"the clan with a thousand skills"**, masters of ninjutsu, taijutsu and genjutsu alike (DB3)
  `[canon statement]`. **The strongest clan of the Warring States** (DB3 p338) `[canon]`.
- **Wood = chakra turned into life** (DB3 p307) `[canon]` → the Senju are life force made flesh `[our
  interpretation]`. Hashirama raised a forest that became a village (DB2 p279). His cells grew an army
  of White Zetsu and woke Madara's Rinnegan (ch. 606) `[canon]`. His flesh is still alive on Danzō's arm
  and in Yamato `[canon]`.
- **"God of Shinobi"**: eight tailed beasts captured alone (ch. 458) `[canon]`. Madara, with the EMS
  and the Nine-Tails, never beat him `[canon]`. He resisted the perfected Impure World Reincarnation
  (ch. 620) `[canon]`.
- **Inventors of the forbidden:** Tobirama created the Shadow Clone, Impure World Reincarnation and
  Flying Thunder God (ch. 122, 641) `[canon]` → Senju genius invents jutsu `[implication]`.
- **Love is their strength** (ch. 619) `[canon statement]`: their power grows protecting others `[our
  interpretation]`. Senju often have immense physical strength (Naruto Mobile) `[game]`.

**Bold mechanics `[our interpretation]`**
1. **Master of all things.** A Senju can learn **every nature**: all five basic natures plus Yin and
   Yang, at natural affinity **1.0** (other clans reach 0.5 by training). Jutsu mastery XP ×2. A jutsu
   seen cast 3 times can be **studied** (an hour of practice at a dummy) and learned, with no Sharingan.
   Every taijutsu style is learned at half cost. Combination natures (kekkei genkai) can be unlocked
   from any two parts they own.
2. **Jutsu Forge (Tobirama's gift).** A Senju with 5+ jutsu at ★5 can **invent** one: pick a base
   behaviour plus a nature plus a modifier (size, homing, multi-cast, delay) and save it as a
   new data jutsu with your name on it. NPC ninjas of your village can learn it from you.
3. **Life made flesh.** Any land a Senju lives on blooms: within 32 blocks of your bed, crops grow ×2
   and animals breed ×2. Hashirama's cells (Part I) can be **planted**: a "Sap Guardian" grows over an
   in-game day (a white wooden humanoid that guards an area; up to 6). Echo of the Zetsu army.
4. **Hokage's will.** Immune to mind control (Kotoamatsukami, Yamanaka possession, Impure World
   control); genjutsu last half as long.
5. **Love grows the power.** +4% jutsu power per crew mate within 16 blocks (max +20%). Healing jutsu
   on allies are ×2.
6. **Tsunade's line:** a "Hundred Strength" punch (chakra-loaded fist) that cracks a 6-block crater and
   sends large mobs flying; **Mitotic Regeneration** on allies.
7. **Tobirama's line:** Water Release with no water nearby at full power; Flying Thunder God unlocked
   10 CTL earlier.

**Ultimate form: Sage of the Thousand Hands** `[our interpretation]`
- **Unlock:** Wood Blood (born or earned) + Wood ★5 + instant Sage Mode + defeat a tailed beast using
  only Wood jutsu.
- **Payoff:** in Sage Mode, raise **True Several Thousand Hands**: a 60-block-tall thousand-armed wooden
  statue (a multi-block entity) that you ride on its head. 20 s of **Top Hands Strike**: a rain of giant
  fists across a 40-block area, each one cratering the ground. Or the **Wood Human** (a 30-block wooden
  golem) for 60 s, made to wrestle a Complete Susanoo or a tailed beast. **Bliss-Bringing Hands** puts
  every tailed beast within 64 blocks to sleep.
- **Cost:** all chakra, Sage Mode ends, one cell division, a 24-hour cooldown, and **the land stays
  scarred**: the strike leaves a permanent forest of giant trees and craters (a new Valley of the End in
  your world).

---

## Bold reading: Uchiha · the fan that feeds the flames

**The maximal reading**
- **うちは from 団扇 uchiwa, "paper fan"** `[name meaning]`: a fan stokes fire, and their crest is a fan.
  Madara's war fan sent jutsu back (ch. 560s `[unverified page]`) `[canon]`. "The clan that controls
  fire" (DB2) `[canon]`.
- **"Heavenly eyes that see the truth of all creation"** (the Mangekyō, DB3 pp274–275) `[canon
  statement]`.
- **"Better to flee than fight an Uchiha one-on-one"** (ch. 257) `[canon statement]`.
- **Power grows with emotion and loss** (ch. 619); Sasuke grew stronger mid-fight from hatred
  (ch. 460, 478) `[canon]`. → their power has **no ceiling, only a price** `[our interpretation]`.
- **They command tailed beasts** with their eyes (ch. 309, 501) `[canon]`. A Mangekyō ability can be
  **programmed** to fire after the user's death (ch. 681) `[canon]`. Izanagi **rewrites reality**
  `[canon]`.
- **Madara with the Rinnegan** dropped meteors and cut down mountains with Perfect Susanoo (ch. 590s
  `[unverified pages]`) `[canon]`. **Indra's descendants**: the "power" side of the Sage's legacy
  `[canon]`.

**Bold mechanics `[our interpretation]`**
1. **Grief made permanent.** Every bonded ally (respect 80+) who dies at someone else's hand
   **permanently** adds +5% to all eye abilities (cap +50%) and a new title ("Who lost N"). Loss is
   power, for good.
2. **Rage surge.** Under 30% health with the Curse of Hatred above 50: Sharingan drain is free, and
   your next 3 jutsu hit ×2 (Sasuke's mid-fight surges).
3. **The war fan (gunbai) heirloom.** Swing to **reflect** an incoming jutsu back (Uchiha Return),
   once per 8 s; fanning your own fire jutsu doubles its size.
4. **Programmed eyes.** Put one of your Mangekyō abilities into an ally, a crow or an item with a
   trigger ("when my brother sees Madara's face", "when this crow is hurt"). It fires once, even after
   you die.
5. **Beast tamer of the eye.** Mangekyō: hold a tailed beast (unsealed) as a companion indefinitely
   while the eye is open (drain 3/s). EMS: the hold ignores the eye drain.
6. **Copy wheel unbound.** With the EMS, copied jutsu are learned at your own mastery level, not ★1.

**Ultimate form: Ascension of Indra** `[our interpretation]`
- **Unlock:** EMS + Rinnegan (Part I's Senju-cell recipe).
- **Payoff:** **Perfect Susanoo**: a 40-block winged armoured giant; its sword cuts a **30-block line
  through terrain** (mountains included). **Indra's Arrow**: a lightning bow shot that erases everything
  in a 40-block line. **Heavenly Meteor** (Rinnegan): summon a meteor that craters a 20-block area, then
  a second if the first is stopped. **Six Paths powers** (Part I).
- **Cost:** 10 chakra/s and cell pain from second 1; each terrain-cutting swing costs 2 max health
  until you sleep; your **destined rival** spawns: an Asura-heritage NPC (Senju or Uzumaki) who hunts you,
  grows with you and returns every time you ascend. And you're the world's most wanted eyes.

---

## Bold reading: Kaguya · a body that is a weapon forge

**The maximal reading**
- **Descendants of Kaguya Ōtsutsuki**, the progenitor of chakra itself (ch. 684; DB4) `[canon]`. Her
  **All-Killing Ash Bones** turn whatever they pierce to dust (DB4 p25) `[canon]`. The clan's power is a
  "weaker" version (DB4 p275), and **weaker than a goddess's weapon is still terrifying**
  `[implication]`.
- **"The ultimate taijutsu ability"** (Orochimaru) `[canon statement]`. Feared **even by its own
  war-crazed clan** `[canon]`. Usable **without limit** while the body holds out (ch. 212) `[canon]`.
  Kimimaro survived 200 m of compressed sand `[canon]`. The clan believed itself **invincible**, and with
  Shikotsumyaku "rarely meets real opposition" `[canon statement]`.
- **かぐや "radiant night"**, the moon princess of *The Tale of the Bamboo Cutter* `[name meaning]`.
  The forehead dots are Heian nobility marks (Kishimoto interview) `[canon statement]`: royal blood
  `[our interpretation]`.
- Orochimaru wanted that body; Kabuto and Orochimaru later took the power from his cells (ch. 585)
  `[canon]` → **Kaguya bones are a legacy others will kill to harvest** `[implication]`.

**Bold mechanics `[our interpretation]`**
1. **The bone forge.** Craft gear from your own skeleton (costs health, regrows in 60 s):
   **Bone armour** (four pieces, diamond-level, repairs itself 1 durability/s, can't be broken by
   anything below netherite), **bone tools** (a pickaxe that mines obsidian like stone), **infinite bone
   arrows**, a **hollow bone flute** (sound genjutsu, Kabuto's trick, ch. 585). Bone gear can't be
   stolen: it crumbles in anyone else's hands.
2. **Build with your bones.** A Seedling Fern cast while crouching leaves its spikes **permanently**
   as bone blocks (walls, towers, a fortress). A Kaguya can raise a bone keep in a minute.
3. **Reshape the skeleton.** Toggles: longer arm bones (+1.5 reach), spinal spring (jump +3 blocks),
   ribcage shield (an immune-to-crit chest), and so on; three at a time.
4. **The fortress body** (Part I) becomes **unbreakable** at Bone Fever stage 0–1: knockback,
   crushing (falling blocks, anvils, sand coffins) and suffocation deal nothing.
5. **Royal blood calls.** Kaguya NPCs and revenants obey a Kaguya player with all five dances.

**Ultimate form: Ash Bone Awakening** `[our interpretation]`
- **Unlock:** all five dances + defeat the Kaguya Ōtsutsuki boss and take her **Ash Bone relic**.
- **Payoff:** **All-Killing Ash Bones**: bones that, on hit, start turning the target to ash: 4% of max
  health per second for 10 s, which can't be healed, and a target that dies to it leaves **nothing**
  (no drops, no revival, no Izanagi). Bosses take 25% total. The relic also **halts Bone Fever** for good
  (it can't rise any more).
- **Cost:** every Ōtsutsuki enemy and every bone-hunter (Orochimaru-style NPCs) now hunts you; each ash
  bone costs 5 health.
- **Without the relic: The Last Dance.** At Bone Fever stage 4, once in your life: 3 minutes of every
  bone free, ×2 damage and speed, no pain. Then you **die for real** (character death: the clan's
  ending, Kimimaro's last battle). The game asks twice.

---

## Bold reading: Hyūga · eyes toward the sun

**The maximal reading**
- **日向 "toward the sun / a sunlit place"** `[name meaning]`: their sight reaches everywhere, like
  sunlight.
- **Hamura's line, keeping the Ōtsutsuki traits most purely** (official video) `[canon statement]`.
  Konoha's **oldest and most outstanding bloodline** (DB1 p265) and its strongest clan `[canon]`.
- **Range has no clear ceiling:** Hinata 1 km → 10 km → 20 km (ch. 58, 396; *The Last*) `[canon]`.
  Kaguya's Byakugan covered **much of the world** `[canon]`. → with Ōtsutsuki purity, a Hyūga's sight
  can approach that `[our interpretation]`.
- **Gentle Fist gives complete control of the opponent's chakra** `[canon]`. The **Eight Gates are
  tenketsu** (ch. 85), and the Byakugan sees every tenketsu → a Hyūga could open their own gates with
  precision instead of years of pain `[implication]`.
- **The Byakugan + Hamura's chakra = Tenseigan**, which could move the moon `[movie]`.

**Bold mechanics `[our interpretation]`**
1. **Near-total sight.** Legendary Byakugan range: **512 blocks**. **Sun's Eye** (crouch, eye open):
   a map view of everything with chakra in range: players, mobs, NPCs, and **chest contents**. Ores show
   as "dead spots" (no chakra) through stone. Nothing alive hides from a Hyūga.
2. **Tenketsu dominion.** Heavy Gentle Fist blows **lock** an enemy's jutsu for 60 s (not 6), or force
   their tenketsu open so their chakra **pours out** for 10 s. On an ally: open their tenketsu for ×2
   regen.
3. **Gentle Gates.** A Hyūga with Gentle Fist mastery can strike their **own** gates open (gates 1–5)
   with half the self-damage of the Might line (they see exactly where to hit). Gates 6–8 stay Might-only.
4. **Absolute defence.** Rotation grows to an 8-block **Heavenly Spin** dome; Hiashi-level masters can
   hold it 3 s and reflect projectiles back at 2× speed.
5. **Air Palm sniping.** Vacuum Palm reaches 24 blocks at full Byakugan.

**Ultimate form: Tenseigan** `[movie-based; our interpretation]`
- **Unlock:** legendary Byakugan + Hamura's chakra (from the moon Ōtsutsuki boss) + an Uzumaki-blooded
  parent or crew partner at full bond (Hinata and Naruto's line).
- **Payoff:** **Tenseigan Chakra Mode**: flight, 8 orbiting truth-seeking spheres (block anything,
  including jutsu), **gravity** (pull every creature within 24 blocks to a point, or push them 30 blocks),
  and the **Golden Wheel Rebirth Explosion**: a gravity collapse that levels a 20-block area.
- **Cost:** 6 chakra/s; branch members can't reach it while sealed; eye thieves target you
  constantly; the moon boss returns to take the eyes back.

---

## Bold reading: Might · a youth that burns the heavens

**The maximal reading**
- **Gate 8 = about 100× normal power, beyond the Five Kage** `[canon statement]`. Madara, after Night
  Guy, said no one had ever matched Guy in taijutsu (ch. 670 `[unverified page]`) `[canon]`, and Night
  Guy bent space itself as it travelled `[canon, unverified page]`.
- **Lee can't use ninjutsu at all, and became a Kage-level fighter anyway** `[canon]` → effort alone can
  break a bloodline's ceiling `[our interpretation]`. Duy took 20 years for all eight (ch. 668) `[canon]`.

**Bold mechanics `[our interpretation]`**
1. **Gate stacking for the team:** a Might at gate 6+ gives crew within 12 blocks +20% damage
   ("Youth is contagious").
2. **Morning Peacock** sets the area on fire; **Daytime Tiger** is a 30-block air-cannon that knocks down
   trees and walls; **Evening Elephant** is five steps of vacuum punches that hold a boss in place.
3. **Hard work breaks blood:** a Might or Lee who beats an S-tier clan member in a 1v1 (no gates
   above 7) gets a permanent +5 TAI and +5 SPD once per clan.

**Ultimate form: Night Guy** (Part I's gate 8, made world-scale)
- **Payoff:** one kick dash through **80 blocks** that bends space around it (a visible warp trail),
  dealing 500 damage to the target and 100 to everything along the path; it ignores Susanoo, shields and
  intangibility.
- **Cost:** death, or crippled for 7 days if a Six Paths Yang healer saves you (Part I).

---

## Bold reading: Akimichi · giants of the harvest road

**The maximal reading**
- **秋道 "autumn road"** `[name meaning]`: the harvest. Their power is food turned into chakra and mass.
- **The Chilli Pill: "a hundred times the power"** (ch. 189) `[canon statement]`.
- Chōza and Chōji grew to giant size in the war, big enough to grapple Madara-era threats `[canon,
  unverified pages]`. **Butterfly Mode** made Chōji fly and strike at Kage level `[canon]`.

**Bold mechanics `[our interpretation]`**
1. **True giants.** Super Multi-Size scales you up to **20 blocks tall** (the entity's scale): you step
   over walls, crush leaves and fences, wrestle tailed beasts (grapple: hold a beast 5 s), and carry your
   whole crew in your hands. Cost: 30 calories/s at full size.
2. **Feast power:** eating at a feast (8+ dishes) stores up to 500 calories (a "Harvest Store").
3. **Human Boulder** rolls through blocks softer than stone, flattening a path.

**Ultimate form: Butterfly of the Harvest** `[our interpretation]`
- **Unlock:** Calorie Control mastery (Part I) + a Harvest Store of 500.
- **Payoff:** Butterfly Mode with no death clock: flight, wing gusts, and **Butterfly Bullet Bombing**:
  one fist the size of a house, ×5 damage, knocking any boss 30 blocks.
- **Cost:** the whole store, then 3 minutes at Hunger 0 and Slowness II; without mastery, the Chilli
  pill death clock (Part I).

---

## Bold reading: Nara · shadows that own the night

**The maximal reading**
- **奈良**, Japan's ancient capital `[name meaning]`: an old, wise house. **IQ over 200** (ch. 107)
  `[canon statement]`. Shikaku planned the Allied war; Shikamaru later became acting Hokage `[canon]`.
- **A shadow can stretch through any other shadow** (ch. 107 onward) `[canon]` → **at night, every
  shadow is theirs** `[our interpretation]`.

**Bold mechanics `[our interpretation]`**
1. **The night belongs to the Nara.** At light level ≤ 4, shadow techniques reach anywhere within 32
   blocks that's dark, without a line; the bind holds 3 targets for free.
2. **Shadow Gathering:** shadows rise as solid tendrils that grab items, block projectiles and
   carry allies.
3. **Shōgi mind.** Once per fight, crouch-hold to **freeze time for 3 s** (single-player; in
   multiplayer: everything slows to 20% for 3 s around you) and queue up to 3 actions that play out at
   once.

**Ultimate form: Lord of Shadows** `[our interpretation]`
- **Unlock:** IQ trials (a clan puzzle chain in the deer forest) + Shadow mastery.
- **Payoff:** turn the area dark (32 blocks, 30 s): every creature without a light source is bound,
  then caught by Shadow Neck Bind (6 dmg/s).
- **Cost:** 8 chakra/s, and you can't move during it.

---

## Bold reading: Yamanaka · a mind that holds the army

**The maximal reading**
- **山中 "in the mountains"** `[name meaning]`. During the war, Inoichi and Ino linked the minds of the
  whole Allied army so everyone could hear Naruto at once (ch. 559–614 `[unverified pages]`) `[canon]`.
  They run Konoha's barrier team (sensing the whole village) `[canon]`.

**Bold mechanics `[our interpretation]`**
1. **Mind network:** link up to 20 allies: shared vision of the linked player's targets, shared
   precognition (if one ally has a Sharingan or Byakugan open, all get its warnings).
2. **Possess anything**, bosses included, for 5 s (they shake you off; then you're stunned 10 s).
3. **Body swap:** trade bodies with a beaten NPC for 2 minutes (walk into an enemy camp as one of them).

**Ultimate form: Mind of the Village** `[our interpretation]`
- **Payoff:** for 60 s, every allied NPC and player within 128 blocks fights as one mind: +30%
  accuracy, no friendly fire, and focus-fire on your target.
- **Cost:** your body lies inert the whole time; if it's hit, everyone linked takes the damage.

---

## Bold reading: Aburame · the body is a hive

**The maximal reading**
- **油女** `[name meaning]` (literally "oil woman"). Their insects eat chakra, **even jutsu** (ch. 126,
  427) `[canon]`. Torune's nano insects could wipe out large numbers at once (Jar of Poison) `[canon]`.
  The Aburame crushed the Kamizuru `[anime]`.

**Bold mechanics `[our interpretation]`**
1. **Hive Lord:** place insect hives that grow colonies on their own; at 3 hives, your total colony
   can reach **500,000**.
2. **Swarm body.** Killing blow? Become a swarm: escape as insects and re-form 10 blocks away
   (60 s cooldown, needs 50% colony).
3. **Eat a tailed beast:** your insects drain a beast's chakra cloak, cancelling it for 10 s.

**Ultimate form: The Black Sea of Insects** `[our interpretation]`
- **Payoff:** a 24-block storm of insects for 20 s: drains 10 chakra/s from everything inside, eats every
  jutsu cast into it, and leaves anyone at 0 chakra paralysed.
- **Cost:** 70% of your colony (it regrows over an in-game day).

---

## Bold reading: Inuzuka · the pack

**The maximal reading:** **犬塚 "dog mound"** `[name meaning]`. Kiba could track scents that ninja dogs
couldn't (ch. 364) `[canon]`. **Ultimate: Fang of the Wild Wolf.** You, your hound and all your pack (up
to 4 summoned ninken) merge into a **10-block two-headed wolf** for 30 s whose Fang Over Fang bores
through stone. Cost: the hound is exhausted for an in-game day. Bold extra: **pack alpha** (wolves in
your area follow you).

## Bold reading: Hatake · the white fang

**The maximal reading:** **畑 "field"** `[name meaning]`. Sakumo **overshadowed all three Sannin**
(ch. 240) `[canon]`. Kakashi copied a thousand jutsu and became Sixth Hokage `[canon]`. In the last
battle, Obito's spirit lent him **both Mangekyō eyes and a full Susanoo** for a few minutes (ch. 690s
`[unverified pages]`) `[canon]`. **Ultimate: Borrowed Perfect Eyes.** When a crew mate you're bonded with
dies holding Sharingan eyes, once in your life their spirit lends you a **double Mangekyō and a
complete Susanoo for 5 minutes**. Cost: you lose all implanted eyes afterwards. Bold extra: the
**White Light Chakra Sabre** at full mastery cuts jutsu clean in half.

## Bold reading: Sarutobi · the professor

**The maximal reading:** **猿飛 "monkey jump"**, after Sarutobi Sasuke, the folklore ninja hero
`[name meaning]`. Hiruzen was "God of Shinobi" and was said to know every jutsu in Konoha `[canon]`.
**Ultimate: God of Shinobi (Professor's Library).** A Sarutobi can **learn any jutsu taught in their
village** without the prerequisite chain, and fight with all five natures. Their ultimate is **Enma's
Adamantine Prison + Five-Element Barrage**: the staff cages a boss for 8 s while you cast one jutsu
of each nature for free. Cost: Hiruzen's death seal is the last resort (`[canon]` lineage): you can learn
the Dead Demon Consuming Seal without the Uzumaki mask, and it kills you.

## Bold reading: Jūgo's clan · the source of sage power

**The maximal reading:** they are **the origin of every cursed seal**, and studying them led Orochimaru
to senjutsu (ch. 579) `[canon]`. Jūgo took the Raikage's lariat and struck back (ch. 462) `[canon]`.
→ **They are natural sages without training** `[our interpretation]`. **Ultimate: Sage Colossus.** Full
Sage Transformation with the nature meter full and controlled (needs a calmer): you grow to 6 blocks, all
shape-shifts at once, cannons and boosters, regenerating 1 health/s, for 45 s. Cost: when it ends, roll
a 30% chance of a frenzy anyway. Bold extra: **curse-seal smith**: brand up to 3 crew mates with level-2
seals (Part I).

## Bold reading: Hoshigaki · a tailed beast without a tail

**The maximal reading:** "**A tailed beast without a tail**" `[canon statement]`; chakra above all
Akatsuki (ch. 255) `[canon]`. **干柿 "dried persimmon"** `[name meaning]`. → **Hoshigaki are walking
tailed beasts** `[our interpretation]`. **Bold:** pool ×15 (up from ×6) with no jinchūriki needed; they
can **absorb a sealing**: a Sealing Scroll used on them stores the chakra instead of failing.
**Ultimate: Great Shark Sea.** A 30-block dome of water out of nothing for 60 s; inside, you're a shark
(swim at 3× speed, bite drains 20 chakra), and every enemy drowns slowly unless they can breathe water.
Cost: all chakra, the living sword sleeps for an in-game day.

## Bold reading: Kazekage line · the desert answers

**The maximal reading:** the **Third Kazekage was Suna's strongest** (DB3 p64) `[canon]`; Gaara became
Kage at 15 and moved enough sand to stop Deidara's bombs over the village `[canon]`. **Ultimate: Desert
Imperial Funeral.** Turn the ground within 40 blocks to sand (temporary), then crush everything standing
on it, or raise a sand **tsunami** that buries a village quarter. With Iron Sand: **World Order**
(spiked iron structures that grow across the area). Cost: the gourd's whole reservoir, 30 s of no
shield, and the desert stays (the area remains sand until you sleep).

## Bold reading: Yuki · endless winter

**The maximal reading:** Haku's mirrors move "at the speed of light" (DB1) `[canon statement]`; a Yuki's
presence makes snow fall (ch. 522) `[canon]`; **雪 "snow"** `[name meaning]`. **Ultimate: Endless
Winter.** The biome around you turns to snow for 5 minutes (64 blocks): water freezes, enemies slow 30%,
and you **teleport between any two ice surfaces** you can see. Cost: frail, and you can't heal while it
lasts.

## Bold reading: Hōzuki · the second coming of the demon

**The maximal reading:** "**Second Coming of the Demon**" (Mangetsu, Suigetsu) `[canon statement]`;
Gengetsu was the **Second Mizukage** `[canon]`; **鬼灯 "demon lantern"** `[name meaning]`. **Ultimate:
Great Water Arm / Ocean Body.** Become a 10-block water giant for 30 s: blows pass through entirely,
your fists are tidal waves. Cost: all hydration; lightning instantly ends it and stuns you 5 s.

## Bold reading: Chinoike · the pool of blood

**The maximal reading:** **血の池 "pool of blood"**, also the name of a Buddhist hell `[name meaning]`;
the Ketsuryūgan is compared to the **Three Great Dōjutsu** `[canon statement]`. **Ultimate: Blood Hell.**
Every bleeding creature within 16 blocks is under your control for 6 s (move, freeze, or turn their blood
into spikes from inside). Cost: you bleed too (−4 max health until you sleep), and every Uchiha NPC in
the world becomes hostile.

## Bold reading: Kurama · genjutsu made real `[anime-only]`

**The maximal reading:** Yakumo put **all of Konoha** under one illusion `[anime canon]`; **鞍馬**, the
mountain of the tengu `[name meaning]`. **Ultimate: The Painted World.** A 64-block genjutsu where you
repaint reality: lava rivers, falling mountains, cages, all doing real damage, for 20 s. Cost: Ido takes
over for 60 s afterwards.

## Bold reading: the smaller clans (quick bold hooks)

| Clan | Maximal reading | Bold ultimate `[our interpretation]` | Cost |
|---|---|---|---|
| **Shimura** | Madara was shocked they joined Konoha (ch. 625) `[canon]`; Danzō ran ten Izanagi | **Ten Lives**: up to 10 implanted eyes, ten deaths undone in one fight | each eye goes blind; Uchiha hate you |
| **Yotsuki** | "never sell out a comrade" `[canon statement]` | **Oath of Cloud**: while a crew mate lives, you can't drop below 1 health for 10 s | once per fight |
| **Iburi** | smoke that can still touch things `[anime]` | **Smoke Wraith**: possess a whole squad (3) at once | wind kills |
| **Kamizuru** | claimed the First Tsuchikage `[anime]`; **神鶴 "divine crane"** `[name meaning]` | **Queen's Swarm**: a giant queen bee mount whose sting paralyses | fire |
| **Fūma** | **風魔 "wind demon"** `[name meaning]`; Uchiha blood (B-vol. 10) `[canon]` | **Dormant blood**: 1 Fūma in 50 awakens a single Sharingan after a near-death | Uchiha hunters notice |
| **Kurogane** | **黒鉄 "black iron"** `[name meaning]` | **One Cut**: an iaidō draw that cuts any jutsu and any shield in a 10-block line | no ninjutsu ever |
