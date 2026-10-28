package com.noirm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.noirm.Noirm;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The map of every {@code /noirm} command, by section, and the clickable {@code /noirm help}.
 *
 * <p>Each entry has the syntax shown to the player, a concrete example (a game test parses every
 * example, so this list can't drift from the real commands) and a description in the lang files
 * ({@code help.noirm.<section>.<key>}). Add a command here when you add one to a command class.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CommandGuide {
    /** One documented command. */
    public record Entry(String section, String key, String syntax, String example, boolean cheats) {
        public String descriptionKey() {
            return "help.noirm." + section + "." + key;
        }
    }

    private static final Map<String, List<Entry>> SECTIONS = new LinkedHashMap<>();

    static {
        section("me")
                .add("profile", "/noirm me", "/noirm me", false)
                .add("heal", "/noirm me heal", "/noirm me heal", true)
                .add("max", "/noirm me max", "/noirm me max", true)
                .add("meditate", "/noirm meditate", "/noirm meditate", false)
                .add("nature", "/noirm me nature <0-100>", "/noirm me nature 50", true)
                .add("stats", "/noirm me stats <speed> <taijutsu> <vitality> <reserves> [<ctl> <wil>]", "/noirm me stats 30 40 25 20 15 15", true)
                .add("stat", "/noirm stat <speed|taijutsu|vitality|reserves|ctl|wil> <1-100>", "/noirm stat ctl 50", true)
                .add("stat_dur", "/noirm stat dur <1-10000>", "/noirm stat dur 60", true)
                .add("bars", "/noirm stats", "/noirm stats", false)
                .add("set", "/noirm set <chakra|stamina> <value>", "/noirm set chakra 80", true)
                .add("max_bar", "/noirm max <chakra|stamina> <value>", "/noirm max chakra 300", true)
                .add("refill", "/noirm refill", "/noirm refill", true)
                .add("resolve", "/noirm resolve <0-100>", "/noirm resolve 100", true)
                .add("awaken", "/noirm awaken", "/noirm awaken", true)
                .add("creator", "/noirm creator", "/noirm creator", false)
                .add("creator_height", "/noirm creator height <0.85-1.15>", "/noirm creator height 1.1", false)
                .add("creator_reroll", "/noirm creator reroll", "/noirm creator reroll", true)
                .add("creator_skip", "/noirm creator skip", "/noirm creator skip", true);
        section("combat")
                .add("mode", "/noirm combat <on|off>", "/noirm combat on", false)
                .add("style", "/noirm style", "/noirm style", false)
                .add("style_set", "/noirm style set <style>", "/noirm style set strong_fist", true)
                .add("moves", "/noirm style moves [style]", "/noirm style moves brawler", false)
                .add("anim", "/noirm anim <animation> [ticks] [targets]", "/noirm anim noirm:quick_jab 30", true);
        section("clan")
                .add("show", "/noirm clan", "/noirm clan", false)
                .add("list", "/noirm clan list", "/noirm clan list", false)
                .add("set", "/noirm clan set <clan>", "/noirm clan set uchiha", true)
                .add("reroll", "/noirm clan reroll", "/noirm clan reroll", true)
                .add("new", "/noirm clan new", "/noirm clan new", true)
                .add("natures", "/noirm nature list", "/noirm nature list", true)
                .add("nature_add", "/noirm nature add <nature>", "/noirm nature add water", true)
                .add("nature_remove", "/noirm nature remove <nature>", "/noirm nature remove water", true)
                .add("nature_all", "/noirm nature all", "/noirm nature all", true)
                .add("drills", "/noirm drills", "/noirm drills", false)
                .add("nature_train", "/noirm nature train <nature> <0-100>", "/noirm nature train wind 90", true)
                .add("nature_hidden", "/noirm nature hidden <nature>", "/noirm nature hidden earth", true);
        section("jutsu")
                .add("list", "/noirm jutsu list", "/noirm jutsu list", false)
                .add("loadout", "/noirm jutsu loadout", "/noirm jutsu loadout", false)
                .add("learn", "/noirm jutsu learn <jutsu|all>", "/noirm jutsu learn noirm:katon/great_fireball", true)
                .add("learn_nature", "/noirm jutsu learn nature <nature>", "/noirm jutsu learn nature fire", true)
                .add("forget", "/noirm jutsu forget <jutsu|all>", "/noirm jutsu forget noirm:katon/great_fireball", true)
                .add("equip", "/noirm jutsu equip <slot> <jutsu>", "/noirm jutsu equip 1 noirm:katon/great_fireball", true)
                .add("unequip", "/noirm jutsu unequip <slot|all>", "/noirm jutsu unequip 1", true)
                .add("mastery", "/noirm jutsu mastery <jutsu> <0-5>", "/noirm jutsu mastery noirm:katon/great_fireball 3", true)
                .add("mastery_all", "/noirm jutsu mastery all <0-5>", "/noirm jutsu mastery all 5", true)
                .add("ready", "/noirm jutsu ready", "/noirm jutsu ready", true);
        section("eyes")
                .add("show", "/noirm eyes", "/noirm eyes", false)
                .add("clear", "/noirm eyes clear", "/noirm eyes clear", true)
                .add("sharingan", "/noirm sharingan <0-3>", "/noirm sharingan 3", true)
                .add("sharingan_xp", "/noirm sharingan xp <amount>", "/noirm sharingan xp 300", true)
                .add("pressure", "/noirm sharingan pressure <0-100>", "/noirm sharingan pressure 50", true)
                .add("byakugan", "/noirm byakugan [remove]", "/noirm byakugan", true)
                .add("mangekyo", "/noirm mangekyo <info|awaken|clear|eternal>", "/noirm mangekyo info", true)
                .add("mangekyo_strain", "/noirm mangekyo strain <0-100>", "/noirm mangekyo strain 0", true)
                .add("mangekyo_pattern", "/noirm mangekyo pattern <0-7>", "/noirm mangekyo pattern 2", true)
                .add("mangekyo_ability", "/noirm mangekyo ability <left|right> <ability>", "/noirm mangekyo ability left amaterasu", true)
                .add("mangekyo_items", "/noirm mangekyo <eye <1-3>|eyes>", "/noirm mangekyo eyes", true)
                .add("mangekyo_npc", "/noirm mangekyo npc", "/noirm mangekyo npc", true);
        section("beast")
                .add("show", "/noirm beast", "/noirm beast", false)
                .add("seal", "/noirm biju seal <1-9>", "/noirm biju seal 9", true)
                .add("release", "/noirm biju release", "/noirm biju release", true)
                .add("bond", "/noirm beast bond <-100-100>", "/noirm beast bond 100", true)
                .add("tails", "/noirm beast tails <1-9>", "/noirm beast tails 4", true)
                .add("spawn", "/noirm beast spawn <1-9>", "/noirm beast spawn 1", true);
        section("crew")
                .add("list", "/noirm crew", "/noirm crew", false)
                .add("recruit", "/noirm crew recruit", "/noirm crew recruit", true)
                .add("order", "/noirm crew order <follow|wait|guard|patrol|train|meditate>", "/noirm crew order guard", false)
                .add("come", "/noirm crew <come|fallback>", "/noirm crew come", false)
                .add("attack", "/noirm crew attack", "/noirm crew attack", false)
                .add("role", "/noirm crew role <none|bodyguard|vanguard|scout|medic|gatherer>", "/noirm crew role medic", false)
                .add("behaviour", "/noirm crew behaviour <aggressive|defensive|passive>", "/noirm crew behaviour aggressive", false)
                .add("lesson", "/noirm crew lesson <nature>", "/noirm crew lesson fire", false)
                .add("teachjutsu", "/noirm crew teachjutsu <jutsu|all>", "/noirm crew teachjutsu all", false)
                .add("range", "/noirm crew range <close|mid|far>", "/noirm crew range mid", false)
                .add("jutsu", "/noirm crew jutsu <free|conserve|none>", "/noirm crew jutsu conserve", false)
                .add("summon", "/noirm crew summon", "/noirm crew summon", true)
                .add("heal", "/noirm crew heal", "/noirm crew heal", true)
                .add("respect", "/noirm crew respect <0-100>", "/noirm crew respect 80", true)
                .add("teach", "/noirm crew teach <nature>", "/noirm crew teach water", true)
                .add("dismiss", "/noirm crew dismiss", "/noirm crew dismiss", true);
        section("spawn")
                .add("ninja", "/noirm spawn ninja <clan> [tails] [strength]", "/noirm spawn ninja uchiha 0 40", true)
                .add("rogue", "/noirm spawn rogue [strength]", "/noirm spawn rogue 30", true)
                .add("ally", "/noirm spawn ally <clan>", "/noirm spawn ally hyuga", true)
                .add("encounter", "/noirm spawn encounter <type> [strength]", "/noirm spawn encounter squad 30", true)
                .add("dummy", "/noirm spawn dummy", "/noirm spawn dummy", true)
                .add("beast", "/noirm spawn beast <1-9>", "/noirm spawn beast 2", true)
                .add("master", "/noirm spawn master <master>", "/noirm spawn master might_guy", true);
        section("kit")
                .add("list", "/noirm kit list", "/noirm kit list", false)
                .add("give", "/noirm kit <kit>", "/noirm kit tools", true);
        section("world")
                .add("village", "/noirm village <capital|town|outpost> [look]", "/noirm village outpost leaf", true)
                .add("destruction", "/noirm world destruction <on|off>", "/noirm world destruction off", true)
                .add("regen", "/noirm world regen <seconds>", "/noirm world regen 60", true)
                .add("gore", "/noirm world gore <on|off>", "/noirm world gore on", true)
                .add("cleanup", "/noirm world cleanup [radius]", "/noirm world cleanup 32", true);
        section("master")
                .add("list", "/noirm master list", "/noirm master list", false)
                .add("spawn", "/noirm master spawn <master>", "/noirm master spawn might_guy", true)
                .add("pass", "/noirm master pass <master>", "/noirm master pass might_guy", true)
                .add("learnall", "/noirm master learnall <master>", "/noirm master learnall might_guy", true)
                .add("reset", "/noirm master reset", "/noirm master reset", true);
        section("career")
                .add("ryo", "/noirm ryo", "/noirm ryo", false)
                .add("ryo_withdraw", "/noirm ryo withdraw <amount>", "/noirm ryo withdraw 100", false)
                .add("ryo_pay", "/noirm ryo pay <player> <amount>", "/noirm ryo pay @p 50", false)
                .add("ryo_change", "/noirm ryo <give|take|set> <amount>", "/noirm ryo give 500", true)
                .add("mission", "/noirm mission", "/noirm mission", false)
                .add("mission_list", "/noirm mission list", "/noirm mission list", false)
                .add("mission_abandon", "/noirm mission abandon", "/noirm mission abandon", false)
                .add("mission_start", "/noirm mission start <mission>", "/noirm mission start d_bandit_hunt", true)
                .add("rank", "/noirm rank", "/noirm rank", false)
                .add("rank_set", "/noirm rank set <rank>", "/noirm rank set chunin", true)
                .add("rank_points", "/noirm rank points <points>", "/noirm rank points 150", true)
                .add("rank_exam", "/noirm rank exam", "/noirm rank exam", true);
        section("sage")
                .add("status", "/noirm sage", "/noirm sage", false)
                .add("on", "/noirm sage <on|off>", "/noirm sage on", false)
                .add("rumour", "/noirm sage rumour", "/noirm sage rumour", false)
                .add("learn", "/noirm sage learn", "/noirm sage learn", true)
                .add("energy", "/noirm sage energy <0-100>", "/noirm sage energy 60", true)
                .add("petrify", "/noirm sage petrify <0-99>", "/noirm sage petrify 60", true)
                .add("sanctuary", "/noirm sage sanctuary", "/noirm sage sanctuary", true);
    }

    private CommandGuide() {}

    public static Map<String, List<Entry>> sections() {
        return SECTIONS;
    }

    public static List<Entry> all() {
        List<Entry> all = new ArrayList<>();
        SECTIONS.values().forEach(all::addAll);
        return all;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .executes(ctx -> overview(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> overview(ctx.getSource()))
                        .then(Commands.argument("section", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SECTIONS.keySet(), builder))
                                .executes(ctx -> section(ctx.getSource(), StringArgumentType.getString(ctx, "section"))))));
    }

    /** {@code /noirm help}: the sections, each clickable. */
    static int overview(CommandSourceStack source) {
        MutableComponent text = Component.translatable("help.noirm.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (Map.Entry<String, List<Entry>> section : SECTIONS.entrySet()) {
            String run = "/noirm help " + section.getKey();
            text.append("\n ").append(Component.literal("▸ " + section.getKey()).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, run))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(run)))))
                    .append(Component.literal(" (" + section.getValue().size() + ") ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("help.noirm.section." + section.getKey()).withStyle(ChatFormatting.GRAY));
        }
        text.append("\n").append(Component.translatable("help.noirm.footer").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        source.sendSuccess(() -> text, false);
        return SECTIONS.size();
    }

    /** {@code /noirm help <section>}: its commands; clicking one puts it in the chat box. */
    static int section(CommandSourceStack source, String name) {
        List<Entry> entries = SECTIONS.get(name);
        if (entries == null) {
            return Cmd.fail(source, "help.noirm.unknown", name);
        }
        MutableComponent text = Component.translatable("help.noirm.section_title", Component.translatable("help.noirm.section." + name))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (Entry entry : entries) {
            String typed = prefill(entry.syntax());
            text.append("\n ").append(Component.literal(entry.syntax()).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, typed))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("help.noirm.example", entry.example())))))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(entry.descriptionKey()).withStyle(ChatFormatting.GRAY));
            if (entry.cheats()) {
                text.append(" ").append(Component.translatable("help.noirm.cheats").withStyle(ChatFormatting.DARK_RED));
            }
        }
        source.sendSuccess(() -> text, false);
        return entries.size();
    }

    /** What clicking a command types: the syntax up to its first argument. */
    static String prefill(String syntax) {
        int cut = syntax.length();
        for (char c : new char[] {'<', '['}) {
            int at = syntax.indexOf(c);
            if (at >= 0) {
                cut = Math.min(cut, at);
            }
        }
        return syntax.substring(0, cut);
    }

    private static Builder section(String name) {
        return new Builder(SECTIONS.computeIfAbsent(name, k -> new ArrayList<>()), name);
    }

    private record Builder(List<Entry> list, String section) {
        Builder add(String key, String syntax, String example, boolean cheats) {
            list.add(new Entry(section, key, syntax, example, cheats));
            return this;
        }
    }
}
