# Powers design: clans, jutsu, Sharingan, awakening, tailed beasts

_Living design doc. "Owner" marks the owner's decisions; everything else is a proposal that can
change after playtesting. All numbers are tunable (where is noted per section)._

## 1. Clans (owner: random, mostly custom, an open-ended list)

- **Every world grows its own clan list** (`WorldClans`, saved with the world). When someone
  needs a clan (a new player now; villages and NPC families later), they get:
  ~10% a **canon** clan (Uchiha, Hyūga, Uzumaki, Senju: `data/noirm/clans/*.json`), otherwise
  50/50 a **custom clan this world already knows** or a **brand-new generated one**. So you keep
  meeting both new and familiar clans; there's no fixed limit.
- A generated clan has a Japanese-style name, **1–2 natures** (one strong, sometimes a second),
  **2 traits** (out of 10), small speed/taijutsu leanings, and an **awakening path**.
- **Traits:** Deep Reserves (+30 chakra), Endurance (+30 stamina), Swift (+3 speed), Fierce (+3
  taijutsu), Iron Body (+30 posture), Keen Eyes (+10% perception), Iron Will (Resolve +50%,
  awakening +10%), Chakra Flow (jutsu −15% chakra), Quick Recovery (stamina +30%), Jutsu Prodigy
  (mastery ×2).
- Players roll their clan on first join (a chat message explains it). `/noirm clan` shows it again.

## 2. Natures (owner: clan gives a great bonus; others very hard to get)

- Affinity: **strong 1.5** (clan's main), **natural 1.0** (second), **trained 0.5** (future:
  long training), **imitation 0.4** (Sharingan-copied jutsu of a nature you lack).
- Power = 0.5 + 0.5 × affinity (strong 1.25×, trained 0.75×). Cost = 1.5 − 0.5 × affinity (strong
  0.75×, trained 1.25×). You can't use a nature jutsu without the nature (except imitation).
- Training a new nature isn't built yet (that's M4/M5); `/noirm nature add` simulates it.

## 3. Jutsu (owner: a 4-slot loadout, each jutsu with its own feel)

- In **combat mode**, keys **1–4** cast the jutsu in your 4 loadout slots (instead of switching
  hotbar slots). **J** opens the jutsu screen to change the loadout.
- Each jutsu is a JSON file (`data/noirm/jutsu/<nature>/<name>.json`) on a reusable behavior:

| Jutsu | Nature | Feel | What it does |
|---|---|---|---|
| Great Fireball | Fire | hold to charge, release | Flying ball of fire; explodes; bigger the longer you charge |
| Fire Stream | Fire | hold to channel | Flamethrower cone while held (not a starter: learn/copy) |
| Water Bullet | Water | tap | Fast projectile, big knockback and posture damage |
| Air Palm | Wind | tap | Cone blast: knockback, posture, turns enemy projectiles around |
| Earth Wall | Earth | tap | 5×3 wall of earth in front of you, crumbles after 10 s |
| Chidori | Lightning | charge (min 0.5 s), release | Dash forward and strike the first enemy: damage, stun, posture |
| Substitution | — | tap | For a moment, the next hit lands on a log; you appear behind the attacker, who is thrown off |
| Body Flicker | — | tap | Blink up to 7 blocks forward |
| Sharingan Genjutsu | Sharingan 2+ | tap | Stun and daze the enemy you look at; stronger minds may resist |

- **Starters:** your clan's natures' jutsu + Substitution + Body Flicker.
- **Mastery:** every cast earns XP; 5 levels; each level is +10% power, −8% cost and cooldown.
- Jutsu hits go through guard, parry, dodge and substitution like melee does.

## 4. Sharingan (owner: all four abilities)

- Uchiha only (the clan's `kekkei_genkai`). It's **awakened by the near-death awakening**, then
  grows from 1 to 3 tomoe through use (1 XP per second active; 300 / 1200 XP) or further
  awakenings. **G** toggles it; it drains 0.8 / 1.6 / 2.4 chakra per second (regen is 1/s).
- **1+ tomoe:** perception +25% per tomoe (enemy wind-ups look slower: the owner's perception idea).
- **2+ tomoe:** **precognition** (an attacker glows, plus a red flash and chime before their hit
  lands) and the **Sharingan genjutsu** jutsu.
- **3 tomoe:** **copy**: watch someone cast a jutsu (within 16 blocks, in view) and you learn it.
- Red glowing irises render on players (everyone sees them) and on Uchiha ninjas.

## 5. Awakening (owner: Resolve, identity-based)

- **Resolve** (0–100, visible in combat mode) builds from damage dealt (more against stronger
  enemies), damage taken, and especially perfect dodges and parries.
- When a hit would leave you under 20% health, the chance is Resolve × 0.85 (+10% Iron Will;
  needs ≥ 20 Resolve; at most once per 5 minutes). On success the hit leaves you at ≥ 1 health
  and you're **LOCKED IN** for 20 s: +25% health back, half chakra, full stamina, a brief
  invulnerability, Speed II, ×1.3 damage, perception and jutsu power. Then **exhausted** for 30 s
  (Slowness, Weakness, half chakra regen).
- What awakens depends on who you are:
  - **Uchiha:** the Sharingan awakens (or gains a tomoe) and switches on for free.
  - **Nature-surge clans:** their natures' jutsu cost nothing and hit ×1.5 while locked in.
  - **Jinchūriki (overrides the clan):** the beast takes over (see below).
  - **Others:** the will-to-live surge.
- Future: Might Guy's students open a gate (needs the master, M4).

## 6. Jinchūriki cloak (owner: start with the cloak)

- Become one by birth (3%), a Sealing Scroll (section 10), or `/noirm biju seal <1–9>`. **B** toggles the chakra
  cloak: 20 s + 2 s per tail, then a 60 s cooldown. Cloak: +20% speed, ×1.3 damage, ×1.15
  perception, ×1.2 jutsu power, ×3 chakra regen, ×1.5 stamina regen; an orange aura.
- **The beast takes over** (awakening): the red cloak for 20 s: +40% speed, ×1.6 damage, ×1.3
  perception, ×1.6 jutsu power, **no guarding**, and it burns you (1 damage every 2 s, never below
  2 health). Full tailed beasts (giant forms) need custom art and come later.

## 7. Sparring ninjas

They roll a clan like players (so they show the world's clans, e.g. "Sparring Ninja (Kazemori)"),
cast their clan's jutsu from range, sometimes escape with Substitution, and Uchiha ninjas fight
with an active Sharingan (1–3 tomoe). Choose with `/summon noirm:sparring_ninja ~ ~ ~ {Clan:"uchiha"}`.

## 8. Training (owner: the dummy, speed and taijutsu growth first)

- **Taijutsu XP:** a landed NOIRM hit gives 2 (heavy +1, blocked ×0.5); a parry gives 6.
  **Speed XP:** a dodge gives 1, a perfect dodge 6, sprinting 1 every 2 s.
- A point costs `20 + 8 × stat` XP (100 at 10, 260 at 30, 500 at 60); stats cap at 100.
- **Drills** (the dummy, plain dodges, running) only teach up to **30**. **Real opponents**
  (hostile mobs, ninjas, players, beasts) teach ×1.5 × their strength ÷ yours (clamped
  0.25–3). Animals teach nothing.
- **Speed moves you faster:** +0.4% movement per point above 10 (+36% at 100).
- The **training dummy** (crafted from 2 hay bales + 4 sticks) never dies, never moves, shows
  your combo and its damage, and is picked up by crouch-punching it outside combat mode.

## 9. Byakugan (owner: continue with it)

- **Hyūga are born with it**; G opens it (0.6 chakra/s). They start knowing Eight Trigrams Palm
  Rotation and 64 Palms.
- **Open:** chakra silhouettes of everything within 40 blocks, **through walls** (hostiles
  reddish); perception ×1.15; a warning for every attack aimed at you; **guard from any
  direction**.
- **Gentle Fist** (your melee with it open): posture damage ×1.4, blocked hits keep 50% damage
  (not 25%), each hit drains 4 chakra (heavy 8) and closes 2 chakra points (heavy 4). **24 closed
  points = no jutsu for 6 s.** Points reopen after 5 s without a Gentle Fist hit.
- **Eight Trigrams Palm Rotation** (A, tap, 30 chakra, 12 s): a 0.8 s spinning dome; throws
  everyone within 3.5 blocks back, turns projectiles around, nothing gets through.
- **Eight Trigrams 64 Palms** (A, tap, 35 chakra, 20 s): six waves on the enemy in front (2, 4, 8,
  16, 32, 64 palms), holding them in place, closing enough points to seal their jutsu; the last
  palm sends them flying.

## 10. Tailed beasts (owner: be born one, fight them, seal them into yourself or others)

- **Beasts** have 1–9 tails: health 100 + 50/tail, speed/taijutsu 14 + 5/tail, size ×(0.8 +
  0.2/tail). They use the combat system with the **beast style** (claw, back-claw, slam: long,
  readable wind-ups) and, from range, stop and charge a **Tailed Beast Ball** for 1.5 s.
- **Subdued, not killed:** at 10% health a beast lies down for 60 s, then gets up at 50%, angry.
- **Sealing Scroll** (6 paper + 2 ink + 1 gold → 2): use it on a subdued beast to capture it. A
  full scroll seals the beast **into whoever you use it on** (a player or a sparring ninja), or
  **into yourself** (use in the air). Crouch + use on the ground releases it (it attacks you).
  One beast per host.
- **Born jinchūriki:** 3% of newcomers get a random beast at birth.
- **Ninja jinchūriki:** 5% of sparring ninjas; below 35% health they take the cloak (×1.3
  damage, perception), and can throw a Tailed Beast Ball; **when one dies, its beast breaks free**
  and goes for the killer.
- **Tailed Beast Ball** (S, charge, 40 chakra, 30 s, needs the cloak): huge dark explosion.
  Every jinchūriki learns it when a beast is sealed in them.
- Names: Shukaku, Matatabi, Isobu, Son Gokū, Kokuō, Saiken, Chōmei, Gyūki, Kurama.

## Where to tune

- Clans: `data/noirm/clans/*.json`, `ClanGenerator`, `WorldClans` (chances).
- Jutsu: `data/noirm/jutsu/**.json`; behaviors in `JutsuBehaviors`; formulas in `JutsuCaster`.
- Sharingan: constants in `Sharingan`. Byakugan: `Byakugan`, `data/noirm/jutsu/byakugan/`.
  Awakening: `AwakeningSystem`. Cloak and jinchūriki chances: `BijuSystem`.
- Training: constants in `TrainingSystem`. Beasts: `TailedBeast`, `BeastFightGoal`,
  `data/noirm/combat/moves/beast/`, `data/noirm/jutsu/biju/`.
