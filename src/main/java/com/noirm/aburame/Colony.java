package com.noirm.aburame;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.traits.Perk;
import com.noirm.traits.Perks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The colony as a living resource (docs/ABURAME.md, 2.2): who has one, how many insects there are and
 * where, what techniques commit and what comes home, what the colony eats every second, how it breeds,
 * starves, ages a generation and adapts, and the hive rank with its deeds. Players keep their colony
 * in {@link ColonyData} (on {@code ShinobiData}); NPC ninjas keep one in {@link NpcColony}.
 */
public final class Colony {
    private Colony() {}

    // ================================================================ who has one

    /** {@code entity}'s colony, or null if it has none. */
    public static ColonyData of(LivingEntity entity) {
        if (entity instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            return data != null && data.colony().has() ? data.colony() : null;
        }
        if (entity instanceof SparringNinja ninja && !(entity instanceof InsectClone)) {
            return NpcColony.get(ninja);
        }
        return null;
    }

    public static boolean has(LivingEntity entity) {
        return of(entity) != null;
    }

    /** Something changed: sync a player's panel, save an NPC's colony. */
    public static void changed(LivingEntity entity) {
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
        } else if (entity instanceof SparringNinja ninja) {
            NpcColony.save(ninja);
        }
    }

    /**
     * Gives {@code player} a colony (the implantation, off-screen at birth): the nest's insects, its
     * species and temperament, and the Nestling techniques. Does nothing if they already have one.
     */
    public static void grant(Player player, ShinobiData data, NestRoll nest) {
        ColonyData colony = data.colony();
        if (colony.has()) {
            return;
        }
        colony.clear();
        colony.setRinkaichuBorn(nest.rinkaichu());
        colony.setTemperament(nest.temperament());
        colony.setHas(true);
        colony.setVigour(ColonyGrowth.VIGOUR_START + (nest.temperament() == 0 ? ColonyGrowth.VIGOUR_CALM_BONUS : 0));
        colony.setBody(Species.KIKAICHU, nest.colony());
        if (nest.rinkaichu()) {
            colony.setBody(Species.RINKAICHU, nest.colony() / 4);
        }
        learnRankJutsu(player, colony.rank());
        data.markPowersDirty();
    }

    /** The nest for {@code player}: from their creator roll if they made one, otherwise from their UUID. */
    public static NestRoll nestFor(Player player, ShinobiData data) {
        com.noirm.creator.GeneticRoll roll = data.creator().chosenRoll();
        if (roll != null && "aburame".equals(roll.clanId())) {
            return NestRoll.of(roll.clanId(), roll.stats(), roll.talents());
        }
        return NestRoll.of(player.getUUID().getLeastSignificantBits());
    }

    // ================================================================ counts

    /** How many insects the body can hold (×2 in the Living Hive). */
    public static int capacity(LivingEntity entity) {
        int base;
        if (entity instanceof Player player) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            base = data == null ? 0 : ColonyGrowth.bodyCapacity(data.getReserves(), data.getControl());
        } else {
            base = NpcColony.capacity(entity);
        }
        ColonyData colony = of(entity);
        return colony != null && colony.livingHive() ? base * 2 : base;
    }

    /** Kikaichū in your counted jars and gourd. */
    public static int stored(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 0;
        }
        int sum = 0;
        for (ItemStack stack : InsectJarItem.worn(player)) {
            sum += InsectJarItem.count(stack);
        }
        return sum;
    }

    /** Room in the counted jars and gourd. */
    public static int storeCapacity(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 0;
        }
        int sum = 0;
        for (ItemStack stack : InsectJarItem.worn(player)) {
            sum += ((InsectJarItem) stack.getItem()).capacity();
        }
        return sum;
    }

    /** The whole colony: body + jars + gourd + hives + swarms out. Hive rank reads this. */
    public static int total(LivingEntity entity) {
        ColonyData colony = of(entity);
        return colony == null ? 0 : colony.bodyTotal() + stored(entity) + colony.hiveTotal() + colony.field();
    }

    /** Insects of {@code species} that a technique can send right now (kikaichū may come out of jars). */
    public static int available(LivingEntity entity, Species species) {
        ColonyData colony = of(entity);
        if (colony == null) {
            return 0;
        }
        return colony.body(species) + (species == Species.KIKAICHU ? stored(entity) : 0);
    }

    // ================================================================ techniques

    /** Can {@code caster} use this insect technique now: a colony, the hive rank, the species and enough insects? */
    public static boolean canCast(LivingEntity caster, JutsuDefinition def) {
        ColonyData colony = of(caster);
        if (colony == null || def.hiden().isEmpty()) {
            return false;
        }
        JutsuDefinition.Hiden hiden = def.hiden().get();
        if (colony.rank().level() < hiden.rank() && !(caster instanceof Player p && p.getAbilities().instabuild)) {
            return false;
        }
        Species species = Species.byKey(hiden.species());
        if (species == null) {
            return true; // a technique that sends no insects
        }
        if (species != Species.KIKAICHU && !colony.hosts(species)) {
            return false;
        }
        int need = Math.min(needed(caster, def, 1f), Math.max(1, hiden.insects()));
        return hiden.insects() <= 0 || available(caster, species) >= need / 2; // half a swarm is still a swarm
    }

    /** Insects a technique wants to commit: its number or its share of the body, whichever is more; ×1.5 when restless. */
    public static int needed(LivingEntity caster, JutsuDefinition def, float power) {
        ColonyData colony = of(caster);
        if (colony == null || def.hiden().isEmpty()) {
            return 0;
        }
        JutsuDefinition.Hiden hiden = def.hiden().get();
        float n = Math.max(hiden.insects(), hiden.share() * colony.bodyTotal());
        if (colony.restless()) {
            n *= ColonyGrowth.RESTLESS_COST;
        }
        return Math.round(n);
    }

    /** What a technique committed: how many and whether they came out of a jar (a jar swarm is faster). */
    public record Commit(int count, boolean fromJar) {
        public static final Commit NONE = new Commit(0, false);
    }

    /** Takes up to {@code n} insects of {@code species} out of the body (then jars) and counts them as out in the field. */
    public static Commit commit(LivingEntity entity, Species species, int n) {
        ColonyData colony = of(entity);
        if (colony == null || n <= 0) {
            return Commit.NONE;
        }
        int fromBody = Math.min(n, colony.body(species));
        colony.addBody(species, -fromBody);
        int got = fromBody;
        boolean jar = false;
        if (got < n && species == Species.KIKAICHU && entity instanceof Player player) {
            for (ItemStack stack : InsectJarItem.worn(player)) {
                if (InsectJarItem.species(stack) != species) {
                    continue;
                }
                int take = Math.min(n - got, InsectJarItem.count(stack));
                InsectJarItem.set(stack, InsectJarItem.count(stack) - take, species);
                got += take;
                jar |= take > 0;
                if (got >= n) {
                    break;
                }
            }
        }
        colony.addField(got);
        ShibiTrial.onStudentSend(entity, got);
        changed(entity);
        return new Commit(got, jar);
    }

    /**
     * Insects come home: they rejoin the body (any that don't fit go to the jars and gourd; past
     * that they're lost), and what they ate is digested: +6 insects per chakra eaten.
     */
    public static int returnHome(LivingEntity entity, Species species, int n, float eatenToDigest) {
        ColonyData colony = of(entity);
        if (colony == null) {
            return 0;
        }
        colony.addField(-n);
        int born = Math.round(eatenToDigest * ColonyGrowth.INSECTS_PER_CHAKRA_EATEN);
        colony.setBanked(colony.banked() - eatenToDigest);
        int arriving = n + born;
        int room = Math.max(0, capacity(entity) - colony.bodyTotal());
        if (species == Species.KIDAICHU) {
            room = Math.min(room, Species.KIDAICHU_MAX - colony.body(Species.KIDAICHU));
        }
        int toBody = Math.min(room, arriving);
        colony.addBody(species, toBody);
        int left = arriving - toBody;
        if (left > 0 && species == Species.KIKAICHU && entity instanceof Player player) {
            for (ItemStack stack : InsectJarItem.worn(player)) {
                if (InsectJarItem.count(stack) > 0 && InsectJarItem.species(stack) != species) {
                    continue;
                }
                int put = Math.min(left, InsectJarItem.room(stack));
                InsectJarItem.set(stack, InsectJarItem.count(stack) + put, species);
                left -= put;
            }
        }
        changed(entity);
        return arriving - Math.max(0, left);
    }

    /** {@code n} of your insects out in the field died to {@code harm}: they're gone, and the colony feels it. */
    public static void killed(LivingEntity owner, int n, Harm harm) {
        ColonyData colony = of(owner);
        if (colony == null || n <= 0) {
            return;
        }
        colony.addField(-n);
        colony.addPressure(harm, n * ColonyGrowth.PRESSURE_PER_INSECT);
        ShibiTrial.onStudentLoss(owner, n);
        changed(owner);
    }

    /** Insects killed per damage point of {@code harm} for this owner (adaptation and strains lower it). */
    public static float killRate(LivingEntity owner, Harm harm) {
        ColonyData colony = owner == null ? null : of(owner);
        float rate = harm.perDamage;
        if (colony == null) {
            return rate;
        }
        float resist = colony.resist(harm);
        if (harm == Harm.FIRE && colony.hasStrain(WildKind.SCARAB)) {
            resist = Math.min(ColonyGrowth.RESIST_CAP_WITH_STRAIN, resist + 0.10f);
        }
        if (harm == Harm.COLD && colony.hasStrain(WildKind.SNOW_FLEA)) {
            resist = Math.min(ColonyGrowth.RESIST_CAP_WITH_STRAIN, resist + 0.15f);
        }
        if (harm == Harm.MELEE && colony.hasStrain(WildKind.IRONCLAD_WEEVIL)) {
            rate *= 0.8f;
        }
        return rate * (1f - resist);
    }

    // ================================================================ the second-by-second life

    /**
     * One second of the colony's life (players): upkeep, breeding, starving, Vigour, generations and
     * adaptation, marks expiring, and the hive rank.
     */
    public static void tickSecond(ServerPlayer player, ShinobiData data) {
        ColonyData colony = data.colony();
        float upkeep = ColonyGrowth.upkeep(colony.bodyTotal()) * (colony.livingHive() ? 2f : 1f);
        boolean fed = player.getAbilities().instabuild || data.tryConsumeChakra(upkeep);
        if (!fed) {
            data.setChakra(0f);
        }
        if (data.getChakra() <= 0.01f && !player.getAbilities().instabuild) {
            starve(player, colony);
        } else {
            colony.setStarveSeconds(0);
            colony.setVigour(colony.vigour() + (colony.vigour() < ColonyGrowth.VIGOUR_REST ? ColonyGrowth.VIGOUR_FED_PER_SEC : 0f));
            breed(player, data, colony, 1f);
            age(player, colony, 1f);
        }
        if (nearInsectEater(player)) {
            colony.setVigour(colony.vigour() + ColonyGrowth.VIGOUR_EATER_PER_SEC);
        }
        if (colony.livingHive() && player.level() instanceof ServerLevel level) {
            // The Living Hive: your skin crawls, dark specks drifting off you.
            level.sendParticles(SwarmFx.DUST, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.35, 0.6, 0.35, 0.02);
        }
        long now = player.level().getGameTime();
        colony.marks().removeIf(m -> m.until() < now);
        if (player.tickCount % 100 < 20) {
            checkRank(player, colony);
        }
        data.markPowersDirty();
    }

    /** Breeding for one second ×{@code speed} (the Insect Cocoon breeds ×6). */
    public static void breed(Player player, ShinobiData data, ColonyData colony, float speed) {
        int body = colony.bodyTotal();
        int cap = capacity(player);
        if (body >= cap || data.getChakra() < data.getMaxChakra() * ColonyGrowth.BREED_MIN_CHAKRA && !player.getAbilities().instabuild) {
            return;
        }
        float rate = ColonyGrowth.breedPerSecond(colony.body(Species.KIKAICHU)) * ColonyGrowth.vigourBreeding(colony.vigour()) * speed;
        if (colony.temperament() == 1) {
            rate *= NestRoll.RESTLESS_BREEDING;
        }
        float exact = colony.breedRemainder() + rate;
        int born = Math.min(cap - body, (int) exact);
        colony.setBreedRemainder(exact - (int) exact);
        if (born > 0) {
            colony.addBody(Species.KIKAICHU, born);
            if (!player.getAbilities().instabuild) {
                data.setChakra(Math.max(0f, data.getChakra() - born * ColonyGrowth.BREED_CHAKRA_PER_INSECT));
            }
            // Other hosted species breed slowly alongside (a tenth of the kikaichū's pace).
            for (Species s : colony.hosted()) {
                if (s != Species.KIKAICHU && colony.body(s) > 0 && player.getRandom().nextInt(10) == 0) {
                    int cap2 = s == Species.KIDAICHU ? Species.KIDAICHU_MAX : Integer.MAX_VALUE;
                    colony.setBody(s, Math.min(cap2, colony.body(s) + Math.max(1, born / 2)));
                }
            }
        }
    }

    /** Starving: 1% every 10 s, restless, and after a minute they start to leave the body. */
    static void starve(Player player, ColonyData colony) {
        int s = colony.starveSeconds() + 1;
        colony.setStarveSeconds(s);
        colony.setVigour(colony.vigour() + ColonyGrowth.VIGOUR_STARVE_PER_SEC);
        if (s % ColonyGrowth.STARVE_STEP_SECONDS == 0) {
            float loss = ColonyGrowth.STARVE_LOSS + (s >= ColonyGrowth.LEAVE_AFTER_SECONDS ? ColonyGrowth.LEAVE_LOSS : 0f);
            for (Species sp : Species.values()) {
                int n = colony.body(sp);
                colony.setBody(sp, n - Math.max(n > 0 ? 1 : 0, Math.round(n * loss)));
            }
            if (s >= ColonyGrowth.LEAVE_AFTER_SECONDS && player.level() instanceof ServerLevel level) {
                SwarmFx.leaving(level, player); // a little black cloud drifts off and dies
            }
        }
    }

    /** Generations pass while fed; each one adapts to the leading harm. {@code speed}: ×3 in the cocoon. */
    public static void age(Player player, ColonyData colony, float speed) {
        float s = colony.generationSeconds() + speed;
        if (s < ColonyGrowth.GENERATION_SECONDS) {
            colony.setGenerationSeconds(s);
            return;
        }
        colony.setGenerationSeconds(s - ColonyGrowth.GENERATION_SECONDS);
        generation(player, colony);
    }

    /** One generation: adaptation, the Antivenom cross, and the Breeding Jar's work. */
    public static void generation(Player player, ColonyData colony) {
        colony.setGeneration(colony.generation() + 1);
        adapt(colony);
        if (BreedingJarBlockEntity.crossing(player)) {
            colony.setAntivenomProgress(colony.antivenomProgress() + 1);
            if (colony.antivenomProgress() >= BreedingJarBlockEntity.ANTIVENOM_GENERATIONS && !colony.antivenom()) {
                colony.setAntivenom(true);
                player.sendSystemMessage(Component.translatable("message.noirm.colony.antivenom").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** Spends pressure of the leading harm for resistance (the Breeding Jar's focus adapts twice as fast). */
    public static void adapt(ColonyData colony) {
        Harm lead = null;
        float best = ColonyGrowth.PRESSURE_PER_STEP - 0.001f;
        for (Harm h : Harm.values()) {
            float p = colony.pressure(h) + (h == colony.focus() ? ColonyGrowth.PRESSURE_PER_STEP : 0f);
            if (h.adaptable && p > best && colony.resist(h) < ColonyGrowth.ADAPT_CAP) {
                best = p;
                lead = h;
            }
        }
        if (lead == null) {
            return;
        }
        colony.setPressure(lead, colony.pressure(lead) - (lead == colony.focus() ? 0f : ColonyGrowth.PRESSURE_PER_STEP));
        colony.setResist(lead, colony.resist(lead) + ColonyGrowth.ADAPT_STEP * (lead == colony.focus() ? 2f : 1f));
    }

    /** Frogs, chickens, parrots or a pitcher plant within 3 blocks: they eat insects, and the colony hates it. */
    static boolean nearInsectEater(LivingEntity entity) {
        return !entity.level().getEntities(entity, entity.getBoundingBox().inflate(3), Swarms::eatsInsects).isEmpty()
                || PitcherPlantBlock.near(entity.level(), entity.blockPosition(), 3);
    }

    // ================================================================ marks

    /** Leaves a female insect on {@code target} (up to 3 marks; the oldest goes). */
    public static boolean mark(LivingEntity owner, Entity target, int ticks) {
        ColonyData colony = of(owner);
        if (colony == null || target == owner) {
            return false;
        }
        long until = owner.level().getGameTime() + ticks;
        colony.marks().removeIf(m -> m.target().equals(target.getUUID()));
        while (colony.marks().size() >= MAX_MARKS) {
            colony.marks().remove(0);
        }
        colony.marks().add(new ColonyData.Mark(target.getUUID(), until, target.getName().getString()));
        changed(owner);
        return true;
    }

    public static final int MAX_MARKS = 3;
    /** A mark lasts 20 in-game minutes. */
    public static final int MARK_TICKS = 20 * 60 * 20;

    public static boolean marked(LivingEntity owner, Entity target) {
        ColonyData colony = of(owner);
        return colony != null && colony.marks().stream().anyMatch(m -> m.target().equals(target.getUUID())
                && m.until() >= owner.level().getGameTime());
    }

    // ================================================================ hive rank

    /** Whether the deed for {@code rank} is done (2.10). */
    public static boolean deedDone(Player player, ColonyData colony, HiveRank rank) {
        com.noirm.mission.PlayerRank career = ShinobiCapability.get(player).map(d -> d.career().rank()).orElse(com.noirm.mission.PlayerRank.ACADEMY);
        return switch (rank) {
            case NESTLING -> true;
            case GENIN -> colony.drainedTotal() >= DEED_DRAIN;
            case CHUNIN -> career.ordinal() >= com.noirm.mission.PlayerRank.CHUNIN.ordinal() && colony.markWin();
            case JONIN -> career.ordinal() >= com.noirm.mission.PlayerRank.JONIN.ordinal()
                    && colony.resistMap().values().stream().filter(v -> v >= 0.10f - 1e-4f).count() >= 3;
            case CLAN_MASTER -> colony.trialPassed();
            case LIVING_HIVE -> colony.caught().size() >= WildKind.collection().length && colony.legendDrained();
        };
    }

    /** Genin hive: drain this much chakra in total from opponents. */
    public static final float DEED_DRAIN = 300f;

    /** Climbs as far as colony and deeds allow, teaching each new rank's techniques. */
    public static void checkRank(Player player, ColonyData colony) {
        HiveRank next = colony.rank().next();
        while (next != null && total(player) >= next.colony && deedDone(player, colony, next)) {
            colony.setRank(next);
            player.sendSystemMessage(Component.translatable("message.noirm.colony.rank_up",
                    Component.translatable(next.translationKey())).withStyle(ChatFormatting.GOLD));
            learnRankJutsu(player, next);
            next = next.next();
        }
    }

    /** Teaches every technique up to {@code rank} (skipping rinkaichū ones for others). */
    public static void learnRankJutsu(Player player, HiveRank rank) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        ColonyData colony = data.colony();
        for (HiveRank r : HiveRank.values()) {
            if (r.ordinal() > rank.ordinal()) {
                break;
            }
            for (ResourceLocation id : r.jutsu()) {
                JutsuDefinition def = com.noirm.jutsu.JutsuData.get(id);
                if (def == null) {
                    continue;
                }
                if (def.hiden().map(h -> "rinkaichu".equals(h.species())).orElse(false) && !colony.rinkaichuBorn()) {
                    continue; // the rinkaichū's techniques are the Torune line's alone
                }
                if (data.jutsu().learn(id, false)) {
                    data.jutsu().equipIfRoom(id);
                    player.sendSystemMessage(Component.translatable("message.noirm.colony.technique",
                            Component.translatable("jutsu.noirm." + id.getPath().replace('/', '.'))).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
        }
        data.markPowersDirty();
    }

    /** Sets the rank directly (the command), learning its techniques. */
    public static void setRank(Player player, ColonyData colony, HiveRank rank) {
        colony.setRank(rank);
        learnRankJutsu(player, rank);
        changed(player);
    }

    // ================================================================ hives

    /** Records (or updates) one of your hives. */
    public static void noteHive(Player player, String dim, BlockPos pos, int count) {
        ColonyData colony = of(player);
        if (colony == null) {
            return;
        }
        List<ColonyData.HiveRef> hives = colony.hives();
        hives.removeIf(h -> h.dimension().equals(dim) && h.pos().equals(pos));
        hives.add(new ColonyData.HiveRef(dim, pos, count));
        changed(player);
    }

    public static void forgetHive(Player player, String dim, BlockPos pos) {
        ColonyData colony = of(player);
        if (colony != null && colony.hives().removeIf(h -> h.dimension().equals(dim) && h.pos().equals(pos))) {
            changed(player);
        }
    }

    /** A technique's id inside {@code data/noirm/jutsu/aburame/}. */
    public static ResourceLocation jutsu(String path) {
        return Noirm.id("aburame/" + path);
    }

    /** Whether {@code entity}'s colony carries poison immunity (Antivenom or rinkaichū birth). */
    public static boolean poisonImmune(LivingEntity entity) {
        ColonyData colony = of(entity);
        return colony != null && colony.antivenom();
    }

    /** The perk flag that marks an Aburame body (the gate for all of this). */
    public static boolean blood(Player player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        return data != null && Perks.get(data, Perk.COLONY) > 0f;
    }
}
