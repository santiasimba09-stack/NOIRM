# The Inuzuka and their ninja hounds

The owner's brief (2026-11-18), with a follow-up the next day:
- **The growing hound.** You start with a puppy that grows bigger and stronger as you do. It looks like a
  dog or a wolf in Minecraft-style boxes, and its growth is easy to see.
- **No fixed stages.** "Make the dog also have traits and many different styles, and I don't want it
  to be fixed by the 4 stages of growth, just a progressive system where it keeps growing and
  becoming stronger."

This page has two parts: what the story tells us (written in our own words), then how NOIRM builds it.

---

## Part 1: research

### The clan
- The Inuzuka are a Leaf clan of trackers and hunters. Every member is paired with a ninja hound
  (*ninken*) in childhood, and the two grow up and fight as one unit.
- **Traits:**
  - red fang-shaped marks on the cheeks;
  - wild hair;
  - slit, animal-like eyes;
  - long canine teeth and sharp nails;
  - a loud, rough, pack-minded temper.
- **Senses:** their sense of smell is far beyond a normal person's. They tell people apart by scent,
  follow a trail for miles, and notice someone hiding or disguised by smell even when their eyes are
  fooled.
- **Beast Mimicry** (the Four Legs technique): the user drops onto all fours. Chakra makes them faster,
  sharper-clawed and more feral. Most of the clan's combat arts start from it.
- **Pills:** the clan feeds its hounds special ration pills in battle. One well-known scene: a soldier
  pill turns a white puppy's fur red and drives it into a frenzy of strength.

### The hounds
- **Akamaru** (Kiba's partner) is the hound fans remember best, because we watch him grow.
  - As a tiny white puppy, he rides on top of Kiba's head or peeks out from inside his jacket. He is
    still a real fighter and the better nose of the two.
  - A few years later he is a huge dog, much taller than Kiba at the shoulder. Kiba rides him into
    battle, and he is too big to carry for good.
  - The bond is the point. They understand each other's barks and moods, and Kiba fights to protect
    him as much as the reverse.
- **Kuromaru** belongs to Tsume, Kiba's mother. He is a big black, wolf-like hound with a scarred,
  patched eye, gruff and experienced. A veteran partner can be battle-worn and old.
- **The Haimaru brothers** belong to Hana, Kiba's sister, a veterinary ninja. They are three grey
  hounds, triplets, who work as a pack. One handler can lead several dogs, and a hound can be a
  healer's helper as well as a fighter.
- Outside the clan, **Kakashi Hatake** summons a whole pack of ninja dogs through a summoning contract.
  They have different sizes and breeds, each with its own personality, all trackers. That's why our
  Hatake start with a contract instead of a puppy.

### Techniques
- **Fang Over Fang** (Gatsūga): owner and hound both spin into whirling drills and tear through the
  enemy from two sides, crossing on the target. Done alone, the same spin is **Passing Fang**
  (Tsūga).
- **Man Beast Clone** (Jūjin Bunshin): the hound transforms into a copy of its partner. Two "Kibas"
  confuse the enemy, and the copy fights like an animal.
- **Dynamic Marking**: the hound marks the enemy with its scent. From then on the target can't hide
  from either of them, wherever it runs. (In the story the mark is crude; ours is a scent mark.)
- **Two-Headed Wolf** (Sōtōrō): owner and hound fuse into one giant two-headed wolf. Its spinning
  attack is the much stronger **Fang Wolf Fang** (Garōga).
- **Tracking:** the hound finds hidden or fleeing people by scent and leads its partner there,
  waiting when the human falls behind.

---

## Part 2: our design

### Who gets a hound
| Who | How |
|---|---|
| **Inuzuka players** | A puppy at your side from the first moment (the clan perk `"hound": 1`). |
| **Hatake players** | A **Ninken Summoning Contract** in your bag (perk `"hound": 2`). Sign it in blood (a little health) and a puppy answers in smoke. Afterwards the contract dismisses your hound home or summons it back (15 chakra). |
| **Anyone else** | Rarely: at chūnin rank or higher, an Inuzuka crew mate who respects you a lot (respect 80+) and has a hound may give you a pup from its litter. We didn't add a kennel building, because villages and world generation belong to another part of the roadmap. A kennel or a master would be an easy follow-up. |
| **Inuzuka NPC ninjas** | Bring a hound of their own, at a level that fits their strength (at least level 6). It fights on their side and joins their Fang Over Fang. |
| **Commands** | `/noirm hound give [breed]` (cheats). |

### Growth: levels, not stages
A hound has a **level** that keeps rising. It has no cap: a hound can always get stronger. Each
level needs three things at once:

1. **Its own XP:**
   - 0.6 per point of damage its bites deal;
   - kills (6 plus a quarter of the victim's health);
   - 30% of the training XP you earn within 16 blocks of it;
   - hound ration pills;
   - the techniques it joins.
2. **Your bond:** 4 per level, which stops mattering once the bond is full at around level 26.
3. **Your own power** (the mean of your speed and taijutsu): 10 + 0.75 per level. **It grows as you
   do:** a strong hound needs a strong owner.

**What changes with each level** (the curves are in `hound/HoundGrowth`):
- **Size:** the shoulder height rises at every level along a smooth curve:

  | Level | 1 | 5 | 12 | 20 | 30 | 40 and up |
  |---|---|---|---|---|---|---|
  | Shoulder (blocks) | 0.30 | 0.42 | 0.95 | 1.65 (taller than a horse) | 2.15 | 2.40 (levels off) |

  The hitbox follows it, so the hound still fits through the world.
- **Proportions:** over the first 12 levels the body blends from the puppy recipe into its breed's
  adult recipe.
  - A puppy has a big head, a short muzzle, a round body, short legs on big paws and floppy ear tips.
  - The **legs grow first**, so a level-6 adolescent is gangly. It **grows into its head last.**
  - After level 12 it gets heavier in front (chest and mane) up to level 30.
- **Fur:**
  - the ruff, the shoulder mane and the cheek and leg tufts thicken from level 3 to level 30;
  - the coat's texture goes from soft puppy fluff to rough, ink-hard strands, with old scars on the
    muzzle;
  - the eyes narrow from round to a hard leaf shape;
  - the red clan scarf appears at level 12.
- **Strength:** health, bite, smell range and armour rise at every level, forever, with diminishing
  returns (for example, health is 12 + 5.5 × (level − 1)^0.85). Speed rises a little, then levels off.

**Milestones** are named stretches of the level curve, each with its unlocks. The panel shows each
ability as "unlocks at level N":

| Level | Name | Unlocks |
|---|---|---|
| 1 | Puppy | smell and tracking, Dynamic Marking, riding on your head or in your jacket (while its shoulder is under 0.47 blocks: about level 5, longer for a Runt) |
| 6 | Young | spins beside you in Fang Over Fang; Man Beast Clone (Inuzuka blood) |
| 12 | Grown | holds a post alone (the guard order); the clan scarf |
| 20 | Great hound | big enough to ride; Two-Headed Wolf (Inuzuka blood), and riding it turns Fang Over Fang into Fang Wolf Fang |
| 30 | Giant | near its full size; from here on it only gets stronger |

### Traits: every hound has its own nature
Each hound rolls **2 or 3 traits** when you bond (a 45% chance of a third), with **at most one
weakness**, the way players roll talents. They're data (`data/noirm/hound_traits/*.json`; the
numbers are multipliers), and the panel shows them, with weaknesses in red.

| Trait | Effect |
|---|---|
| Keen Nose | smells 40% farther |
| Loyal | the bond grows 50% faster |
| Fierce | bites 20% harder |
| Swift | 12% faster |
| Brave | bites a bit harder; knockouts are 25% shorter (never with Timid) |
| Tireless | almost no crash after a pill; shorter knockouts |
| Big-Boned | 15% bigger and 12% tougher, a little slower (never with Runt) |
| Quick Learner | 25% more XP |
| *Stubborn* (weakness) | ignores one order in five (a bit tougher for it) |
| *Gluttonous* (weakness) | begs and whines whenever you hold food; meals heal it 80% more |
| *Timid* (weakness) | breaks off a fight under 35% health and runs back to you (a slightly better nose) |
| *Runt* (weakness) | 22% smaller and frailer but quick; rides on your head a few levels longer |

### Looks: many different hounds
Each hound's look is rolled when you bond, kept in one number (`hound/HoundLook`, saved and synced),
so the same hound always looks the same:
- **Breed (5 body plans),** each a recipe file in `assets/noirm/hound/breeds/`:

  | Breed | Look | Size |
  |---|---|---|
  | **Wolfish** | lean, deep chest, long legs and muzzle | normal |
  | **Stocky** | square and sturdy | a little smaller |
  | **Sighthound** | very long thin legs, a tiny waist, a long narrow head | 10% faster |
  | **Spitz** | compact and fluffy, a huge ruff, a fox face, a curled tail | smaller |
  | **Mastiff** | a huge head with a short muzzle, thick legs, hanging ears | 12% bigger, slower |
- **Ears:** pricked, floppy (hanging down the sides of the head) or folded (the tips fold forward).
  Each breed has a usual ear shape, rolled 70% of the time.
- **Tail:** straight, curled (over the back) or bushy, again with each breed's usual shape 70% of the time.
- **Pattern:** solid, saddle (a dark back), mask (a dark muzzle and eye band), socks (white feet),
  spotted, or brindle (tiger stripes).
- **Colours:** a base and a second colour from 16 of our own dog and wolf shades: white, cream, wheat,
  fawn, red, ginger, tan, chestnut, brown, chocolate, grey, silver, blue-grey, charcoal, black and sable.
  Dark patterns always get the darker shade.
- **Markings** (each one 22% of the time): a white blaze, a white chest, a white tail tip, pale brows.
- **Eyes:** amber, brown, ice blue or gold.
- While it's still small (up to level 5), you can change its coat colour on the panel.

### Abilities
- **Orders:**
  - follow, sit, guard (from level 12; a younger one sits instead), come, attack (what you look at),
    track, pet, howl;
  - carry on your head or in your jacket (while small), ride (once big enough), dismiss or summon
    (contract hounds).
  - Use the panel (**O**), the crew order screen (**K**, your hound obeys crew orders too), shift +
    right-click (cycles follow, sit, guard), right-click (carry, ride or pet), or `/noirm hound order`.
- **Smell:**
  - Every second it sniffs out **hidden enemies** within its smell range (10 + 5.5 × ln(level)
    blocks, more with Keen Nose). Hidden means invisible, or an enemy player crouching.
  - It **marks** them with a glow everyone can see, growls, and tells you who and how far.
  - On the track order it follows an enemy's scent, waits when you fall behind, and leaves a faint
    trail.
- **The puppy rides on you:** on top of your head (it lies on its belly, legs splayed, looking around)
  or tucked in your jacket with only its head and front paws out. Blows meant for it hit you.
- **It fights beside you.** It defends you and attacks what you attack. Its bite knocks back harder
  as it grows.
- **Fang Over Fang** (the Inuzuka clan jutsu): you tip forward and spin along the ground like a drill
  (the `fang_drill` animation). From level 6 your hound spins beside you, and the two drills cross on
  your target. Alone, it's Passing Fang.
- **Dynamic Marking** (a jutsu): your hound dashes at the target and marks it. For a minute it glows,
  and both of you hit it 25% harder.
- **Man Beast Clone** (a jutsu, Inuzuka, level 6): the hound takes your shape as a clone of you that
  fights like a beast, lasting longer the higher its level. When the clone breaks, the hound
  reappears.
- **Two-Headed Wolf** (a jutsu, Inuzuka, level 20): you and your hound fuse into a giant two-headed
  wolf for 30 seconds.
  - You ride inside it.
  - It bites 70% harder and shrugs off half the damage (to you too).
  - Fang Over Fang becomes Fang Wolf Fang, one huge drill.
- **Riding** (level 20 on): right-click to climb on. Jump to leap. It runs a little faster than its
  own speed.
- **Food and pills:**
  - Meat from your hand heals it and grows the bond; other food heals it less.
  - A bone is a treat (bond and a happy pet).
  - A **soldier pill** or a **hound ration pill** sends it into a 30-second frenzy: its fur turns
    red and it bites 40% harder. A 20-second crash follows.
  - A ration pill also gives a little bond and XP.
  - Any food or pill wakes a knocked-out hound.

### The bond, and why a hound is never lost for good
- **The bond (0–100) grows from:**
  - time together (0.4 a minute within 16 blocks);
  - meals (1);
  - bones (2);
  - pills (1);
  - petting (1.5, once every 20 seconds);
  - kills together (0.6).

  Loyal hounds bond 50% faster. A strong bond makes the bite up to 25% stronger and knockouts up to
  20 seconds shorter.
- **Knocked out, not killed.** A blow that would kill the hound knocks it out instead. It lies on its
  side for 45 seconds (less with a strong bond), slowly healing, and then gets up. Food, a bone or a
  pill wakes it at once with half its health.
- **Why not permanent death?**
  - The hound is meant to be your partner for the whole game, raised from a puppy over many hours.
  - The owner is new to the genre. Losing it for good to one bad fight, a fall or lag would feel
    unfair and would make players leave it at home.
  - A knockout still costs you in a fight: your partner is out, you're alone, and you have to protect
    it or spend food.
- **Only the void or `/kill` destroy the body.** Even then the hound isn't lost. It comes back from
  your record after a 90-second rest.
- **Saved with you.** Everything about the hound lives in your own save (`HoundData` in
  `ShinobiData`): its level, XP, bond, look, traits, name and health.
  - When the hound is following you and ends up 64 blocks away or in another dimension, it reappears
    at your side, like a vanilla pet teleporting.
  - A hound told to sit or guard waits where you left it.

### The UI
- **The hound panel (O)** shows:
  - the live hound turned three-quarters, its health and its state (following, on your head,
    knocked out, wearing your shape…);
  - its look in a few words (hover for the full description);
  - its **traits**;
  - three growth bars against the next level (hound XP, bond, your power) and what's still missing;
  - its abilities, lit if unlocked and otherwise "· lv N";
  - its orders;
  - the coat colours while it's small.
- **HUD:** a small line under the status lines (top left): 犬, its name, level and bond, over a thin
  health bar. It turns red with a countdown when the hound is knocked out, and gold while fused. It
  only shows in combat mode, when the hound is hurt or busy, or for a moment after it grows.
- **Commands:** `/noirm hound` (status), `order`, `give [breed]`, `stage <milestone>`, `level <n>`,
  `bond <0-100>`, `xp <amount>`, `reroll`, `remove` (see docs/COMMANDS.md).

### How it's drawn (for whoever works on the art next)
- **The model is built in the game**, not loaded from a file (`client/hound/HoundRigBuilder`).
  - It reads the breed's adult recipe and the shared puppy recipe and blends them for the hound's
    level, with its ear and tail type on top (`assets/noirm/hound/shapes.json`, `breeds/*.json`).
    The result is jointed boxes with named bones.
  - It builds one body per breed, ears, tail and level (the shape stops changing at level 40) and
    keeps it.
  - The render scale puts the shoulder at the level's height.
- **Animations** (`client/hound/HoundModel`), posed by bone name:
  - a trot on diagonal leg pairs that blends into a gallop (spine flex and body rock), with longer
    strides as it grows;
  - sit (the body tips up on the hip until the front paws touch, the hind legs fold flat), howl
    (sitting, muzzle up);
  - lunge (the bite), sniff (nose down);
  - the Fang Over Fang spin (curled up while the renderer rolls it like a drill);
  - knocked out (limp, on its side), on your head, in your jacket;
  - always: breathing, panting, a wagging tail (more with a strong bond), and ears pinned back in a
    fight.
- **The fur** (`tools/houndgen`):
  - Every box role (chest, head, snout, ear…) has six fixed face rectangles on one atlas
    (`atlas.py`, `assets/noirm/hound/atlas.json`). The game stretches each face over that box,
    whatever its size at this level.
  - The generator paints layers instead of finished coats: fur light and shadow (young and old),
    countershading, the pattern and marking masks, and the details (eyes, nose, teeth, claws, inner
    ears, the scarf).
  - The game mixes each hound's coat from its look and fur age (`client/hound/HoundTextures`). One
    set of layers covers every breed, pattern and colour.
  - To change the art, edit `tools/houndgen/paint.py` and run `python3 tools/houndgen/gen_hound.py
    --swatches out.png` (the swatch sheet mixes six coats the same way the game does).
- **Model variants:** before the owner asked for many styles, three body plans were compared side by
  side (classic square wolf, lean wolf-hound, heavy guard dog), and the lean one read best as a ninja
  hound. Now all three live on as breeds (Wolfish, Stocky, Mastiff), joined by the Sighthound and
  the Spitz.

### Where the numbers live
- `hound/HoundGrowth`: size, proportion and strength curves, what each level costs, milestone checks.
- `hound/HoundStage`: milestone levels.
- `hound/NinjaHound`: knockout, frenzy, fusion and petting constants.
- `hound/Hounds`: XP and bond rates, recall distance, summon cost.
- `hound/HoundTechniques`: spin, mark, clone and fusion numbers.
- `data/noirm/hound_traits/`: traits.
- `assets/noirm/hound/`: shapes and breeds.
- `data/noirm/jutsu/hound/` and `jutsu/clan/fang_over_fang.json`: the techniques.

### Open questions for the owner
- Should other players be able to get a hound more often, for example from an Inuzuka kennel in
  villages?
- Should hounds have more milestones past level 30 (new techniques for very old hounds), or is
  "only stronger" enough?
- Akamaru's red-fur pill scene is in (the frenzy). Should the clan's own **Tunneling Fang** (digging
  drill) come next?
