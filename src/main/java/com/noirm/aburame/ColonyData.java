package com.noirm.aburame;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Your colony (docs/ABURAME.md, 2.2), saved with you ({@code ShinobiData}): how many insects of each
 * species live in your body, how many are out in the field, Vigour, generations, adaptation, strains
 * from bug collecting, female-insect marks, your hives, your hive rank and the deeds toward the
 * next one. Jars and the gourd keep their own count on the item (drop the gourd, lose its insects).
 */
public final class ColonyData {
    private boolean has;
    private final EnumMap<Species, Integer> body = new EnumMap<>(Species.class);
    /** Species you host (kikaichū first; slots open with hive rank). */
    private final Set<Species> hosted = new LinkedHashSet<>();
    /** Insects out in swarms right now (they come back unless killed). */
    private int field;
    private float vigour = ColonyGrowth.VIGOUR_START;
    private int generation;
    private float generationSeconds;
    private float breedRemainder;
    private int starveSeconds;
    private final EnumMap<Harm, Float> pressure = new EnumMap<>(Harm.class);
    private final EnumMap<Harm, Float> resist = new EnumMap<>(Harm.class);
    /** The Breeding Jar's chosen adaptation (null = let the colony choose). */
    private Harm focus;
    /** Wild insects bred into the colony (each gives a strain). */
    private final Set<String> strains = new LinkedHashSet<>();
    /** Wild insects caught into the Insect Case (anyone can collect). */
    private final Set<String> caught = new LinkedHashSet<>();
    private final List<Mark> marks = new ArrayList<>();
    private final List<HiveRef> hives = new ArrayList<>();
    private HiveRank rank = HiveRank.NESTLING;
    /** Deeds: chakra drained from opponents in total; a won fight with a mark; the master's trial; a legend drained. */
    private float drainedTotal;
    private boolean markWin;
    private boolean trialPassed;
    private boolean legendDrained;
    /** Born a rinkaichū host (the Torune/Shikuro line). */
    private boolean rinkaichuBorn;
    /** 0 calm (+Vigour), 1 restless (+10% breeding). */
    private int temperament;
    /** Generations crossed with rinkaichū toward the Antivenom strain, and whether it's done. */
    private int antivenomProgress;
    private boolean antivenom;
    /** Chakra your swarms ate and haven't digested (Chakra Gift gives it away). */
    private float banked;
    /** A Queen raised and bonded (the Giant Insect summon). */
    private boolean queen;
    /** The Living Hive mode, Swarm Body's cooldown, the Black Sea's day of regrowth. */
    private boolean livingHive;
    private long swarmBodyReadyAt;
    private long blackSeaRegrowUntil;
    /** Scouting missions flown (shōkaichū need 10). */
    private int scoutsFlown;
    /** Bikōchū quest: 0 locked, 1 open (full case), 2 egg found. */
    private int bikochuQuest;

    /** A female insect left on a creature. {@code until} is game time. */
    public record Mark(UUID target, long until, String name) {}

    /** One of your hives: where it is and how many it held when last seen. */
    public record HiveRef(String dimension, BlockPos pos, int count) {}

    public boolean has() {
        return has;
    }

    public void setHas(boolean has) {
        this.has = has;
        if (has && hosted.isEmpty()) {
            hosted.add(rinkaichuBorn ? Species.RINKAICHU : Species.KIKAICHU);
            if (rinkaichuBorn) {
                hosted.add(Species.KIKAICHU); // Torune kept ordinary kikaichū too
            }
        }
    }

    // ---------------------------------------------------------------- counts

    public int body(Species species) {
        return body.getOrDefault(species, 0);
    }

    public void setBody(Species species, int count) {
        body.put(species, Math.max(0, count));
    }

    public void addBody(Species species, int count) {
        setBody(species, body(species) + count);
    }

    /** Every insect in your body, all species. */
    public int bodyTotal() {
        int sum = 0;
        for (int n : body.values()) {
            sum += n;
        }
        return sum;
    }

    public Map<Species, Integer> bodyMap() {
        return body;
    }

    public Set<Species> hosted() {
        return hosted;
    }

    public boolean hosts(Species species) {
        return hosted.contains(species);
    }

    public int field() {
        return field;
    }

    public void setField(int field) {
        this.field = Math.max(0, field);
    }

    public void addField(int n) {
        setField(field + n);
    }

    // ---------------------------------------------------------------- health of the colony

    public float vigour() {
        return vigour;
    }

    public void setVigour(float vigour) {
        this.vigour = Math.max(0f, Math.min(100f, vigour));
    }

    public int generation() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }

    public float generationSeconds() {
        return generationSeconds;
    }

    public void setGenerationSeconds(float s) {
        generationSeconds = s;
    }

    public float breedRemainder() {
        return breedRemainder;
    }

    public void setBreedRemainder(float r) {
        breedRemainder = r;
    }

    public int starveSeconds() {
        return starveSeconds;
    }

    public void setStarveSeconds(int s) {
        starveSeconds = s;
    }

    public boolean restless() {
        return starveSeconds > 0;
    }

    // ---------------------------------------------------------------- adaptation

    public float pressure(Harm harm) {
        return pressure.getOrDefault(harm, 0f);
    }

    public void addPressure(Harm harm, float amount) {
        if (harm.adaptable) {
            pressure.put(harm, pressure(harm) + amount);
        }
    }

    public void setPressure(Harm harm, float amount) {
        pressure.put(harm, Math.max(0f, amount));
    }

    /** Bred resistance to {@code harm}, 0 to {@link ColonyGrowth#ADAPT_CAP}. */
    public float resist(Harm harm) {
        return resist.getOrDefault(harm, 0f);
    }

    public void setResist(Harm harm, float value) {
        resist.put(harm, Math.max(0f, Math.min(ColonyGrowth.ADAPT_CAP, value)));
    }

    public Map<Harm, Float> resistMap() {
        return resist;
    }

    public Harm focus() {
        return focus;
    }

    public void setFocus(Harm focus) {
        this.focus = focus;
    }

    // ---------------------------------------------------------------- collecting

    public Set<String> strains() {
        return strains;
    }

    public boolean hasStrain(WildKind kind) {
        return strains.contains(kind.key());
    }

    public Set<String> caught() {
        return caught;
    }

    // ---------------------------------------------------------------- marks and hives

    public List<Mark> marks() {
        return marks;
    }

    public List<HiveRef> hives() {
        return hives;
    }

    public int hiveTotal() {
        int sum = 0;
        for (HiveRef h : hives) {
            sum += h.count();
        }
        return sum;
    }

    // ---------------------------------------------------------------- rank and deeds

    public HiveRank rank() {
        return rank;
    }

    public void setRank(HiveRank rank) {
        this.rank = rank;
    }

    public float drainedTotal() {
        return drainedTotal;
    }

    public void addDrained(float amount) {
        drainedTotal += amount;
    }

    public void setDrainedTotal(float v) {
        drainedTotal = v;
    }

    public boolean markWin() {
        return markWin;
    }

    public void setMarkWin(boolean v) {
        markWin = v;
    }

    public boolean trialPassed() {
        return trialPassed;
    }

    public void setTrialPassed(boolean v) {
        trialPassed = v;
    }

    public boolean legendDrained() {
        return legendDrained;
    }

    public void setLegendDrained(boolean v) {
        legendDrained = v;
    }

    // ---------------------------------------------------------------- birth

    public boolean rinkaichuBorn() {
        return rinkaichuBorn;
    }

    public void setRinkaichuBorn(boolean v) {
        rinkaichuBorn = v;
    }

    public int temperament() {
        return temperament;
    }

    public void setTemperament(int t) {
        temperament = t;
    }

    public int antivenomProgress() {
        return antivenomProgress;
    }

    public void setAntivenomProgress(int p) {
        antivenomProgress = p;
    }

    /** Poison immunity, earned by breeding (Shino's route) or born with rinkaichū. */
    public boolean antivenom() {
        return antivenom || rinkaichuBorn;
    }

    public void setAntivenom(boolean v) {
        antivenom = v;
    }

    public float banked() {
        return banked;
    }

    public void setBanked(float b) {
        banked = Math.max(0f, b);
    }

    public boolean queen() {
        return queen;
    }

    public void setQueen(boolean q) {
        queen = q;
    }

    public boolean livingHive() {
        return livingHive;
    }

    public void setLivingHive(boolean on) {
        livingHive = on;
    }

    public long swarmBodyReadyAt() {
        return swarmBodyReadyAt;
    }

    public void setSwarmBodyReadyAt(long t) {
        swarmBodyReadyAt = t;
    }

    public long blackSeaRegrowUntil() {
        return blackSeaRegrowUntil;
    }

    public void setBlackSeaRegrowUntil(long t) {
        blackSeaRegrowUntil = t;
    }

    public int scoutsFlown() {
        return scoutsFlown;
    }

    public void setScoutsFlown(int n) {
        scoutsFlown = n;
    }

    public int bikochuQuest() {
        return bikochuQuest;
    }

    public void setBikochuQuest(int q) {
        bikochuQuest = q;
    }

    /** Forget the colony entirely. */
    public void clear() {
        load(new CompoundTag());
    }

    // ---------------------------------------------------------------- saving

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Has", has);
        CompoundTag counts = new CompoundTag();
        body.forEach((s, n) -> counts.putInt(s.key(), n));
        tag.put("Body", counts);
        ListTag species = new ListTag();
        hosted.forEach(s -> species.add(StringTag.valueOf(s.key())));
        tag.put("Hosted", species);
        tag.putInt("Field", field);
        tag.putFloat("Vigour", vigour);
        tag.putInt("Generation", generation);
        tag.putFloat("GenSeconds", generationSeconds);
        tag.putFloat("BreedRem", breedRemainder);
        tag.putInt("Starve", starveSeconds);
        CompoundTag p = new CompoundTag();
        pressure.forEach((h, v) -> p.putFloat(h.key(), v));
        tag.put("Pressure", p);
        CompoundTag r = new CompoundTag();
        resist.forEach((h, v) -> r.putFloat(h.key(), v));
        tag.put("Resist", r);
        if (focus != null) {
            tag.putString("Focus", focus.key());
        }
        tag.put("Strains", strings(strains));
        tag.put("Caught", strings(caught));
        ListTag markList = new ListTag();
        for (Mark m : marks) {
            CompoundTag mt = new CompoundTag();
            mt.putUUID("Target", m.target());
            mt.putLong("Until", m.until());
            mt.putString("Name", m.name());
            markList.add(mt);
        }
        tag.put("Marks", markList);
        ListTag hiveList = new ListTag();
        for (HiveRef h : hives) {
            CompoundTag ht = new CompoundTag();
            ht.putString("Dim", h.dimension());
            ht.putLong("Pos", h.pos().asLong());
            ht.putInt("Count", h.count());
            hiveList.add(ht);
        }
        tag.put("Hives", hiveList);
        tag.putInt("Rank", rank.ordinal());
        tag.putFloat("Drained", drainedTotal);
        tag.putBoolean("MarkWin", markWin);
        tag.putBoolean("Trial", trialPassed);
        tag.putBoolean("Legend", legendDrained);
        tag.putBoolean("Rinkaichu", rinkaichuBorn);
        tag.putInt("Temperament", temperament);
        tag.putInt("Antivenom", antivenomProgress);
        tag.putBoolean("AntivenomDone", antivenom);
        tag.putFloat("Banked", banked);
        tag.putBoolean("Queen", queen);
        tag.putBoolean("LivingHive", livingHive);
        tag.putLong("SwarmBody", swarmBodyReadyAt);
        tag.putLong("BlackSea", blackSeaRegrowUntil);
        tag.putInt("Scouts", scoutsFlown);
        tag.putInt("Bikochu", bikochuQuest);
        return tag;
    }

    public void load(CompoundTag tag) {
        has = tag.getBoolean("Has");
        body.clear();
        CompoundTag counts = tag.getCompound("Body");
        for (Species s : Species.values()) {
            if (counts.contains(s.key())) {
                body.put(s, counts.getInt(s.key()));
            }
        }
        hosted.clear();
        tag.getList("Hosted", Tag.TAG_STRING).forEach(t -> {
            Species s = Species.byKey(t.getAsString());
            if (s != null) {
                hosted.add(s);
            }
        });
        field = tag.getInt("Field");
        vigour = tag.contains("Vigour") ? tag.getFloat("Vigour") : ColonyGrowth.VIGOUR_START;
        generation = tag.getInt("Generation");
        generationSeconds = tag.getFloat("GenSeconds");
        breedRemainder = tag.getFloat("BreedRem");
        starveSeconds = tag.getInt("Starve");
        pressure.clear();
        resist.clear();
        CompoundTag p = tag.getCompound("Pressure");
        CompoundTag r = tag.getCompound("Resist");
        for (Harm h : Harm.values()) {
            if (p.contains(h.key())) {
                pressure.put(h, p.getFloat(h.key()));
            }
            if (r.contains(h.key())) {
                resist.put(h, r.getFloat(h.key()));
            }
        }
        focus = tag.contains("Focus") ? Harm.byKey(tag.getString("Focus")) : null;
        strains.clear();
        tag.getList("Strains", Tag.TAG_STRING).forEach(t -> strains.add(t.getAsString()));
        caught.clear();
        tag.getList("Caught", Tag.TAG_STRING).forEach(t -> caught.add(t.getAsString()));
        marks.clear();
        tag.getList("Marks", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag mt = (CompoundTag) t;
            if (mt.hasUUID("Target")) {
                marks.add(new Mark(mt.getUUID("Target"), mt.getLong("Until"), mt.getString("Name")));
            }
        });
        hives.clear();
        tag.getList("Hives", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag ht = (CompoundTag) t;
            hives.add(new HiveRef(ht.getString("Dim"), BlockPos.of(ht.getLong("Pos")), ht.getInt("Count")));
        });
        rank = HiveRank.of(tag.getInt("Rank"));
        drainedTotal = tag.getFloat("Drained");
        markWin = tag.getBoolean("MarkWin");
        trialPassed = tag.getBoolean("Trial");
        legendDrained = tag.getBoolean("Legend");
        rinkaichuBorn = tag.getBoolean("Rinkaichu");
        temperament = tag.getInt("Temperament");
        antivenomProgress = tag.getInt("Antivenom");
        antivenom = tag.getBoolean("AntivenomDone");
        banked = tag.getFloat("Banked");
        queen = tag.getBoolean("Queen");
        livingHive = tag.getBoolean("LivingHive");
        swarmBodyReadyAt = tag.getLong("SwarmBody");
        blackSeaRegrowUntil = tag.getLong("BlackSea");
        scoutsFlown = tag.getInt("Scouts");
        bikochuQuest = tag.getInt("Bikochu");
    }

    private static ListTag strings(Set<String> set) {
        ListTag list = new ListTag();
        set.forEach(s -> list.add(StringTag.valueOf(s)));
        return list;
    }
}
