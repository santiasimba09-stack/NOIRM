package com.noirm.master;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A master, loaded from {@code data/<ns>/masters/<id>.json}. A master is a ninja who tests you
 * with a spar and then teaches lessons. A new master is just a new file.
 *
 * @param name     translation key of their name
 * @param greeting translation key of what they say when you talk to them
 * @param style    the fighting style they fight with (and grant when you pass their test)
 * @param look     a ninja look key ({@code "<folder>:<hex seed>"}, see {@code NinjaLook})
 * @param gear     what they wear: slot ({@code head}, {@code chest}, {@code legs}, {@code feet}) → item id
 * @param stats    how strong they are when not holding back
 * @param test     the spar you must pass before they teach you
 * @param grantsStyle passing the test lets you use their style even below its taijutsu requirement
 * @param lessons  what they teach, in order
 * @param trial    the kind of test: {@code "spar"} (the default), or {@code "meditation"} (sit still by them while
 *                 spirits try to break your focus, see {@code senjutsu.SageTrial}; {@code test.hits} is then the
 *                 seconds of stillness needed, within {@code test.seconds})
 */
public record MasterDefinition(String name, String greeting, Optional<ResourceLocation> style, String look,
                               Map<String, ResourceLocation> gear, Stats stats, Test test, boolean grantsStyle, List<Lesson> lessons,
                               String trial) {

    public static final Codec<MasterDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(MasterDefinition::name),
            Codec.STRING.optionalFieldOf("greeting", "").forGetter(MasterDefinition::greeting),
            ResourceLocation.CODEC.optionalFieldOf("style").forGetter(MasterDefinition::style),
            Codec.STRING.optionalFieldOf("look", "").forGetter(MasterDefinition::look),
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).optionalFieldOf("gear", Map.of()).forGetter(MasterDefinition::gear),
            Stats.CODEC.optionalFieldOf("stats", Stats.DEFAULT).forGetter(MasterDefinition::stats),
            Test.CODEC.optionalFieldOf("test", Test.DEFAULT).forGetter(MasterDefinition::test),
            Codec.BOOL.optionalFieldOf("grants_style", true).forGetter(MasterDefinition::grantsStyle),
            Lesson.CODEC.listOf().optionalFieldOf("lessons", List.of()).forGetter(MasterDefinition::lessons),
            Codec.STRING.optionalFieldOf("trial", "spar").forGetter(MasterDefinition::trial)
    ).apply(i, MasterDefinition::new));

    public Optional<Lesson> lesson(String id) {
        return lessons.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    /** Speed, taijutsu and vitality (health) at full strength. */
    public record Stats(float speed, float taijutsu, float vitality) {
        public static final Stats DEFAULT = new Stats(30, 30, 30);
        public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(1, 10000).optionalFieldOf("speed", 30f).forGetter(Stats::speed),
                Codec.floatRange(1, 10000).optionalFieldOf("taijutsu", 30f).forGetter(Stats::taijutsu),
                Codec.floatRange(1, 10000).optionalFieldOf("vitality", 30f).forGetter(Stats::vitality)
        ).apply(i, Stats::new));
    }

    /**
     * The spar. You pass by landing {@code hits} clean (unguarded) hits, or breaking their posture
     * {@code posture_breaks} times, within {@code seconds}. They fight at {@code level} of their
     * strength (but never weaker than you), and nobody dies: whoever drops to {@code end_health} of
     * their max health ends the spar (if it's the master, you pass).
     *
     * @param minTaijutsu taijutsu you need before they'll spar with you at all
     */
    public record Test(int hits, int postureBreaks, int seconds, float level, float endHealth, float minTaijutsu) {
        public static final Test DEFAULT = new Test(6, 1, 90, 0.3f, 0.2f, 0f);
        public static final Codec<Test> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 1000).optionalFieldOf("hits", 6).forGetter(Test::hits),
                Codec.intRange(0, 100).optionalFieldOf("posture_breaks", 1).forGetter(Test::postureBreaks),
                Codec.intRange(5, 3600).optionalFieldOf("seconds", 90).forGetter(Test::seconds),
                Codec.floatRange(0.05f, 1f).optionalFieldOf("level", 0.3f).forGetter(Test::level),
                Codec.floatRange(0.05f, 0.9f).optionalFieldOf("end_health", 0.2f).forGetter(Test::endHealth),
                Codec.floatRange(0, 10000).optionalFieldOf("min_taijutsu", 0f).forGetter(Test::minTaijutsu)
        ).apply(i, Test::new));
    }

    /**
     * One lesson: the jutsu it teaches and what you need first.
     *
     * @param taijutsu  taijutsu stat needed
     * @param speed     speed stat needed
     * @param test      whether you must have passed this master's spar
     * @param after     lessons (ids, this master's) you must have learned first
     * @param mastery   practice: jutsu you must have used enough to reach a mastery level (★)
     * @param control   chakra control (CTL) needed
     */
    public record Lesson(String id, ResourceLocation jutsu, float taijutsu, float speed, boolean test, List<String> after,
                         Map<ResourceLocation, Integer> mastery, float control) {
        public static final Codec<Lesson> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Lesson::id),
                ResourceLocation.CODEC.fieldOf("jutsu").forGetter(Lesson::jutsu),
                Codec.floatRange(0, 10000).optionalFieldOf("taijutsu", 0f).forGetter(Lesson::taijutsu),
                Codec.floatRange(0, 10000).optionalFieldOf("speed", 0f).forGetter(Lesson::speed),
                Codec.BOOL.optionalFieldOf("test", true).forGetter(Lesson::test),
                Codec.STRING.listOf().optionalFieldOf("after", List.of()).forGetter(Lesson::after),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(1, 5)).optionalFieldOf("mastery", Map.of()).forGetter(Lesson::mastery),
                Codec.floatRange(0, 10000).optionalFieldOf("control", 0f).forGetter(Lesson::control)
        ).apply(i, Lesson::new));
    }
}
