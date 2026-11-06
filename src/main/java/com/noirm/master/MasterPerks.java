package com.noirm.master;

import com.noirm.clan.Nature;
import com.noirm.medical.EyeSurgery;
import com.noirm.medical.MedicalItems;
import com.noirm.nature.NatureDrills;
import com.noirm.nature.NatureTraining;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * What masters teach besides jutsu: <b>perks</b> (lasting skills, learned once) and <b>services</b>
 * (something the master does for you, each time you ask and pay). A lesson names one with
 * {@code "perk"} or {@code "service"} in its master's JSON.
 *
 * <table>
 *   <tr><th>Perk / service</th><th>Who</th><th>What it does</th></tr>
 *   <tr><td>{@value #SWIFT_SEALS}</td><td>the ninjutsu master</td><td>hand seals take {@link #SWIFT_SEAL_FACTOR} of the time</td></tr>
 *   <tr><td>{@value #CHAKRA_EFFICIENCY}</td><td>the ninjutsu master</td><td>jutsu cost less: {@link #EFFICIENCY_PER_CTL} per point of
 *       chakra control (CTL), at most {@link #EFFICIENCY_MAX}</td></tr>
 *   <tr><td>{@value #NATURE_INSIGHT}</td><td>the ninjutsu master</td><td>puts {@link #INSIGHT_BOOST} of a nature drill behind you
 *       (your active drill, or your first hidden nature; never the last bit)</td></tr>
 *   <tr><td>{@value #POISON_LORE}</td><td>the medical ninja</td><td>poisons last half as long and hit one level weaker; your
 *       antidotes work (anyone else's come out botched); two antidotes to start</td></tr>
 *   <tr><td>{@value #EYE_TRANSPLANT} (service)</td><td>the medical ninja</td><td>a safe eye transplant ({@link EyeSurgery})</td></tr>
 * </table>
 */
public final class MasterPerks {
    public static final String SWIFT_SEALS = "swift_seals";
    public static final String CHAKRA_EFFICIENCY = "chakra_efficiency";
    public static final String NATURE_INSIGHT = "nature_insight";
    public static final String POISON_LORE = "poison_lore";
    public static final String EYE_TRANSPLANT = "eye_transplant";

    private static final Set<String> PERKS = Set.of(SWIFT_SEALS, CHAKRA_EFFICIENCY, NATURE_INSIGHT, POISON_LORE);
    private static final Set<String> SERVICES = Set.of(EYE_TRANSPLANT);

    // ---------------------------------------------------------------- tuning
    /** Swift Seals: the seal phase takes this share of its time. */
    public static final float SWIFT_SEAL_FACTOR = 0.6f;
    /** Chakra Efficiency: this much cheaper per point of CTL... */
    public static final float EFFICIENCY_PER_CTL = 0.008f;
    /** ...up to this (reached at CTL 31). */
    public static final float EFFICIENCY_MAX = 0.25f;
    /** Nature Insight: this share of a drill. */
    public static final float INSIGHT_BOOST = 0.5f;
    /** Poison Lore: antidotes you're handed when you learn it. */
    public static final int LORE_ANTIDOTES = 2;

    private MasterPerks() {}

    /** Whether the lesson's perk or service is one we know how to give. */
    public static boolean known(MasterDefinition.Lesson lesson) {
        return lesson.isService() ? SERVICES.contains(lesson.service()) : PERKS.contains(lesson.perk());
    }

    /** Whether {@code data}'s owner learned {@code perk} from any master. */
    public static boolean has(ShinobiData data, String perk) {
        if (data == null) {
            return false;
        }
        for (var entry : MasterData.all().entrySet()) {
            for (MasterDefinition.Lesson lesson : entry.getValue().lessons()) {
                if (perk.equals(lesson.perk()) && data.masters().learned(entry.getKey().toString(), lesson.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean has(LivingEntity entity, String perk) {
        return entity instanceof Player player && has(ShinobiCapability.get(player).orElse(null), perk);
    }

    // ---------------------------------------------------------------- effects (one hook each)

    /** The seal phase for {@code caster} ({@code ticks} without Swift Seals; called from {@code JutsuCaster}). */
    public static int sealTicks(LivingEntity caster, int ticks) {
        if (ticks <= 0 || !has(caster, SWIFT_SEALS)) {
            return ticks;
        }
        return Math.max(1, Math.round(ticks * SWIFT_SEAL_FACTOR));
    }

    /** The chakra-cost multiplier from Chakra Efficiency (1 without it; called from {@code JutsuCaster.cost}). */
    public static float costMultiplier(ShinobiData data) {
        return has(data, CHAKRA_EFFICIENCY) ? efficiency(data.getControl()) : 1f;
    }

    public static float efficiency(float control) {
        return 1f - Mth.clamp(control * EFFICIENCY_PER_CTL, 0f, EFFICIENCY_MAX);
    }

    // ---------------------------------------------------------------- lessons

    /** Why a perk can't be learned yet, beyond the usual requirements (null if it can). */
    public static Component blocker(ShinobiData data, MasterDefinition.Lesson lesson) {
        if (NATURE_INSIGHT.equals(lesson.perk()) && insightTarget(data) == null) {
            return Component.translatable("screen.noirm.master.why.insight");
        }
        return null;
    }

    /** Why a service can't be done now (null if it can). */
    public static Component serviceBlocker(Player player, ShinobiData data, MasterDefinition.Lesson lesson) {
        if (EYE_TRANSPLANT.equals(lesson.service())) {
            return EyeSurgery.blocker(player, data);
        }
        return null;
    }

    /** Performs a service. Returns whether it was done (it's paid for only then). */
    public static boolean perform(Player player, ShinobiData data, MasterDefinition master, MasterDefinition.Lesson lesson) {
        if (EYE_TRANSPLANT.equals(lesson.service())) {
            return EyeSurgery.byMedic(player, data, Component.translatable(master.name()));
        }
        return false;
    }

    /** A perk was just learned: what happens at once. */
    public static void granted(Player player, ShinobiData data, MasterDefinition.Lesson lesson) {
        switch (lesson.perk()) {
            case NATURE_INSIGHT -> {
                Nature nature = insightTarget(data);
                if (nature != null) {
                    NatureTraining t = data.natureTraining();
                    float needed = NatureDrills.secondsNeeded(nature);
                    t.setActive(nature);
                    t.setProgress(nature, Math.min(needed * NatureDrills.SCROLL_CAP, t.progress(nature) + needed * INSIGHT_BOOST));
                    t.markDirty();
                    player.sendSystemMessage(Component.translatable("message.noirm.perk.insight",
                            Component.translatable(nature.translationKey()), Math.round(NatureDrills.share(data, nature) * 100))
                            .withStyle(ChatFormatting.AQUA));
                }
            }
            case POISON_LORE -> {
                ItemStack antidotes = new ItemStack(MedicalItems.ANTIDOTE.get(), LORE_ANTIDOTES);
                if (!player.getInventory().add(antidotes)) {
                    player.drop(antidotes, false);
                }
            }
            default -> { }
        }
        player.sendSystemMessage(Component.translatable("perk.noirm." + lesson.perk() + ".got").withStyle(ChatFormatting.GRAY));
    }

    /**
     * The drill Nature Insight would speed up: the active drill (if you lack its nature), else the
     * first hidden nature you lack (once the chakra paper showed it). Null if there's nothing to
     * study, or it's as far as a teacher can take it.
     */
    public static Nature insightTarget(ShinobiData data) {
        NatureTraining t = data.natureTraining();
        Nature active = t.active();
        if (usable(data, active)) {
            return active;
        }
        if (t.revealed()) {
            for (Nature n : t.hidden()) {
                if (usable(data, n)) {
                    return n;
                }
            }
        }
        return null;
    }

    private static boolean usable(ShinobiData data, Nature nature) {
        return nature != null && data.identity().affinity(nature) <= 0f
                && data.natureTraining().progress(nature) < NatureDrills.secondsNeeded(nature) * NatureDrills.SCROLL_CAP;
    }

    /** The masters (ids) that offer a service (the medic for eye transplants). */
    public static boolean offers(MasterDefinition def, String service) {
        return def != null && def.lessons().stream().anyMatch(l -> service.equals(l.service()));
    }
}
