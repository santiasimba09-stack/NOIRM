# Clan research: overview (how to read, summary table, family tree, databook profiles)

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
