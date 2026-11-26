#!/usr/bin/env python3
"""The text for foods, herbs, pills and the apothecary, in en_us, es_es and es_mx.

    python3 tools/pillgen/pill_lang.py

Edit the tables below and rerun: it merges these keys into the three lang files (other keys are left
alone). Spanish is shared by es_es and es_mx. The advancement texts live in
tools/advancements/gen_advancements.py.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LANG = os.path.join(ROOT, "src/main/resources/assets/noirm/lang")

# key: (english, spanish)
T = {
    # ------------------------------------------------------------ items: foods
    "item.noirm.rice": ("Rice", "Arroz"),
    "item.noirm.steamed_rice": ("Steamed Rice", "Arroz al vapor"),
    "item.noirm.onigiri": ("Onigiri", "Onigiri"),
    "item.noirm.umeboshi": ("Pickled Plums", "Ciruelas encurtidas"),
    "item.noirm.plum_onigiri": ("Plum Onigiri", "Onigiri de ciruela"),
    "item.noirm.ramen": ("Pork Miso Ramen", "Ramen de miso con cerdo"),
    "item.noirm.fish_ramen": ("Fish Ramen", "Ramen de pescado"),
    "item.noirm.beef_ramen": ("Spicy Beef Ramen", "Ramen picante de ternera"),
    "item.noirm.dango": ("Dango", "Dango"),
    "item.noirm.curry_rice": ("Curry Rice", "Arroz con curry"),
    "item.noirm.raw_fish_skewer": ("Raw Fish Skewer", "Brocheta de pescado cruda"),
    "item.noirm.grilled_fish": ("Grilled Fish Skewer", "Brocheta de pescado a la brasa"),
    "item.noirm.field_ration": ("Field Ration", "Ración de campaña"),
    "item.noirm.nutrient_bar": ("Nutrient Bar", "Barrita nutritiva"),
    # ------------------------------------------------------------ herbs
    "item.noirm.moonroot": ("Moonroot", "Raíz de luna"),
    "item.noirm.bloodleaf": ("Bloodleaf", "Hoja de sangre"),
    "item.noirm.ironmillet": ("Ironmillet", "Mijo de hierro"),
    "item.noirm.mistcap": ("Mistcap", "Capuchón de niebla"),
    "item.noirm.ashthorn": ("Ashthorn", "Espino de ceniza"),
    "item.noirm.moonroot_seeds": ("Moonroot Seeds", "Semillas de raíz de luna"),
    "item.noirm.bloodleaf_seeds": ("Bloodleaf Seeds", "Semillas de hoja de sangre"),
    "item.noirm.ironmillet_seeds": ("Ironmillet Seeds", "Semillas de mijo de hierro"),
    "item.noirm.mistcap_seeds": ("Mistcap Spores", "Esporas de capuchón de niebla"),
    "item.noirm.ashthorn_seeds": ("Ashthorn Seeds", "Semillas de espino de ceniza"),
    "block.noirm.rice_crop": ("Rice", "Arroz"),
    "block.noirm.moonroot_crop": ("Moonroot", "Raíz de luna"),
    "block.noirm.bloodleaf_crop": ("Bloodleaf", "Hoja de sangre"),
    "block.noirm.ironmillet_crop": ("Ironmillet", "Mijo de hierro"),
    "block.noirm.mistcap_crop": ("Mistcap", "Capuchón de niebla"),
    "block.noirm.ashthorn_crop": ("Ashthorn", "Espino de ceniza"),
    "block.noirm.apothecary": ("Apothecary", "Botica"),
    # ------------------------------------------------------------ pills
    "item.noirm.stamina_pill": ("Stamina Pill", "Píldora de aguante"),
    "item.noirm.stamina_pill.tip": ("Stamina back at once, faster stamina for a while.", "Recupera el aguante al instante y lo regenera más rápido un rato."),
    "item.noirm.soldier_pill": ("Soldier Pill", "Píldora del soldado"),
    "item.noirm.soldier_pill.tip": ("A big chakra and stamina refill. Then the crash: slow, weak and tired.",
                                    "Recarga mucho chakra y aguante. Luego el bajón: lento, débil y cansado."),
    "item.noirm.chakra_pill": ("Chakra Pill", "Píldora de chakra"),
    "item.noirm.chakra_pill.tip": ("A burst of chakra and faster chakra for a moment.", "Un estallido de chakra y más regeneración durante un momento."),
    "item.noirm.blood_pill": ("Blood Pill", "Píldora de sangre"),
    "item.noirm.blood_pill.tip": ("Heals you over time. Leaves you hungry.", "Te cura poco a poco. Te deja con hambre."),
    "item.noirm.antidote_pill": ("Antidote Pill", "Píldora antídoto"),
    "item.noirm.antidote_pill.tip": ("Washes out poison and keeps it out for a while.", "Elimina el veneno y lo mantiene fuera un rato."),
    "item.noirm.focus_pill": ("Focus Pill", "Píldora de concentración"),
    "item.noirm.focus_pill.tip": ("Sharper perception and chakra control for a minute. A dull head after.",
                                  "Más percepción y control del chakra durante un minuto. Después, la cabeza embotada."),
    "item.noirm.iron_bone_pill": ("Iron-Bone Pill", "Píldora de hueso de hierro"),
    "item.noirm.iron_bone_pill.tip": ("A body like iron: durability, vitality and posture. Heavy legs after.",
                                      "Un cuerpo de hierro: durabilidad, vitalidad y postura. Después, piernas pesadas."),
    "item.noirm.three_seal_pill": ("Three-Seal Pill", "Píldora de los tres sellos"),
    "item.noirm.three_seal_pill.tip": ("Every limit lifted for a minute. Then a backlash that lasts twice as long.",
                                       "Todos los límites desaparecen un minuto. Luego, un castigo que dura el doble."),
    "item.noirm.ruined_pill": ("Ruined Pill", "Píldora echada a perder"),
    "item.noirm.ruined_pill.tip": ("A failed brew. Good for nothing but a sick stomach.", "Una mezcla fallida. Solo sirve para revolverte el estómago."),
    "item.noirm.medical_scroll": ("Medical Scroll", "Pergamino médico"),
    "item.noirm.medical_scroll.tip": ("A medic's notes: read it to learn a pill recipe you don't know.",
                                      "Las notas de un médico: léelo para aprender una receta de píldora que no conozcas."),
    "pill.noirm.grade.crude": ("Crude", "Tosca"),
    "pill.noirm.grade.standard": ("Standard", "Normal"),
    "pill.noirm.grade.refined": ("Refined", "Refinada"),
    "pill.noirm.grade.perfect": ("Perfect", "Perfecta"),
    "pill.noirm.graded": ("%s %s", "%2$s %1$s"),
    "pill.noirm.grade_line": ("Grade: %s", "Calidad: %s"),
    "pill.noirm.cooldown_line": ("Your body needs %ss before another", "Tu cuerpo necesita %s s antes de otra"),
    "pill.noirm.effect.chakra_regen": ("chakra regen", "regeneración de chakra"),
    "pill.noirm.effect.stamina_regen": ("stamina regen", "regeneración de aguante"),
    "pill.noirm.effect.health_regen": ("healing", "curación"),
    "pill.noirm.effect.posture_recovery": ("posture recovery", "recuperación de postura"),
    "pill.noirm.effect.training_xp": ("training", "entrenamiento"),
    "pill.noirm.effect.perception": ("perception", "percepción"),
    "pill.noirm.effect.speed": ("speed", "velocidad"),
    "pill.noirm.effect.taijutsu": ("taijutsu", "taijutsu"),
    "pill.noirm.effect.vitality": ("vitality", "vitalidad"),
    "pill.noirm.effect.reserves": ("reserves", "reservas"),
    "pill.noirm.effect.control": ("chakra control", "control del chakra"),
    "pill.noirm.effect.willpower": ("willpower", "voluntad"),
    "pill.noirm.effect.durability": ("durability", "durabilidad"),
    "pill.noirm.effect.poison_ward": ("poison ward", "protección contra veneno"),
    "pill.noirm.effect.effect": ("status effect", "efecto de estado"),
    # ------------------------------------------------------------ messages
    "message.noirm.pill.cooldown": ("Your body isn't ready for another yet (%ss).", "Tu cuerpo aún no está listo para otra (%s s)."),
    "message.noirm.pill.nausea": ("Too many pills: your stomach turns.", "Demasiadas píldoras: se te revuelve el estómago."),
    "message.noirm.pill.sick": ("Too many pills: you feel sick and weak.", "Demasiadas píldoras: te sientes enfermo y débil."),
    "message.noirm.pill.overdose": ("Overdose! The pills poison you.", "¡Sobredosis! Las píldoras te envenenan."),
    "message.noirm.pill.crash": ("The %s wears off: the crash hits.", "El efecto de %s se acaba: llega el bajón."),
    "message.noirm.pill.ruined": ("Bitter ash. Nothing else.", "Ceniza amarga. Nada más."),
    "message.noirm.pill.mission_reward": ("Also in the pay: %s× %s.", "También en la paga: %s× %s."),
    "message.noirm.pill.supply": ("Tsubaki hands you %s soldier and blood pills each.", "Tsubaki te da %s píldoras del soldado y de sangre."),
    "message.noirm.pharmacology.up": ("Pharmacology %s", "Farmacología %s"),
    "message.noirm.apothecary.brewed": ("%s× %s %s", "%s× %3$s %2$s"),
    "message.noirm.apothecary.failed": ("The brew burns: a ruined pill.", "La mezcla se quema: una píldora echada a perder."),
    "message.noirm.apothecary.no_recipe": ("Nothing in the mortar makes a pill. Pick a recipe on the left.",
                                           "Lo que hay en el mortero no hace ninguna píldora. Elige una receta a la izquierda."),
    "message.noirm.apothecary.unknown": ("You don't know this recipe yet.", "Aún no conoces esta receta."),
    "message.noirm.apothecary.low_skill": ("You need pharmacology %s for this.", "Necesitas farmacología %s para esto."),
    "message.noirm.apothecary.full": ("Take your pills out first.", "Saca antes tus píldoras."),
    "message.noirm.apothecary.missing": ("You're missing some ingredients.", "Te faltan ingredientes."),
    "message.noirm.medical_scroll.learned": ("You learn to make the %s.", "Aprendes a preparar: %s."),
    "message.noirm.medical_scroll.nothing_new": ("Nothing new, but the notes sharpen your craft.", "Nada nuevo, pero las notas afinan tu oficio."),
    # ------------------------------------------------------------ the apothecary screen
    "screen.noirm.apothecary": ("Apothecary", "Botica"),
    "screen.noirm.apothecary.brewed": ("%s brewed", "%s preparadas"),
    "screen.noirm.apothecary.skill": ("PHARMACOLOGY %s", "FARMACOLOGÍA %s"),
    "screen.noirm.apothecary.loading": ("…", "…"),
    "screen.noirm.apothecary.level": ("Lv %s", "Nv %s"),
    "screen.noirm.apothecary.click_fill": ("Click to fill the mortar from your bag.", "Haz clic para llenar el mortero desde tu bolsa."),
    "screen.noirm.apothecary.needs": ("Needs:", "Necesita:"),
    "screen.noirm.apothecary.mortar": ("Mortar", "Mortero"),
    "screen.noirm.apothecary.grind": ("Grind", "Moler"),
    "screen.noirm.apothecary.why.empty": ("Put a recipe's ingredients in the mortar (click a recipe).",
                                          "Pon en el mortero los ingredientes de una receta (haz clic en una)."),
    "screen.noirm.apothecary.why.cant": ("You can't brew this one yet.", "Aún no puedes preparar esta."),
    "screen.noirm.apothecary.hint": ("Pick a recipe on the left.", "Elige una receta a la izquierda."),
    "screen.noirm.apothecary.odds": ("Success %s%% · ×%s", "Éxito %s%% · ×%s"),
    "screen.noirm.apothecary.grades": ("Grade odds", "Probabilidad de calidad"),
    "screen.noirm.apothecary.why.skill": ("Needs pharmacology %s.", "Necesita farmacología %s."),
    "screen.noirm.apothecary.why.scroll": ("Learn it from a medical scroll (old chests, strong rogues, scroll shops).",
                                           "Apréndela de un pergamino médico (cofres antiguos, renegados fuertes, tiendas de pergaminos)."),
    "screen.noirm.apothecary.why.perk.poison_lore": ("Tsubaki teaches it with Poison Lore (or a medical scroll).",
                                                     "Tsubaki la enseña con Saber de venenos (o un pergamino médico)."),
    "screen.noirm.apothecary.why.perk.pill_lore": ("Tsubaki teaches it with Pill Lore (or a medical scroll).",
                                                   "Tsubaki la enseña con Saber de píldoras (o un pergamino médico)."),
    "screen.noirm.apothecary.why.perk.high_grade_pills": ("Tsubaki teaches it to jōnin: High-Grade Pills (or a medical scroll).",
                                                          "Tsubaki la enseña a los jōnin: Píldoras de alto grado (o un pergamino médico)."),
    # ------------------------------------------------------------ HUD
    "hud.noirm.pill.active": ("%s %s", "%s %s"),
    "hud.noirm.pill.active_crash": ("%s %s, then crash", "%s %s, luego bajón"),
    "hud.noirm.pill.crash": ("Crash: %s %s", "Bajón: %s %s"),
    "hud.noirm.pill.crash_soon": ("Crash in %2$s (%1$s)", "Bajón en %2$s (%1$s)"),
    "hud.noirm.pill.cooldowns": ("Wait: %s", "Espera: %s"),
    "hud.noirm.pill.toxicity": ("TOXICITY %s", "TOXICIDAD %s"),
    # ------------------------------------------------------------ masters
    "perk.noirm.pill_lore": ("Pill Lore", "Saber de píldoras"),
    "perk.noirm.pill_lore.got": ("+5 pharmacology and the focus pill. An apothecary and herb seeds to start.",
                                 "+5 de farmacología y la píldora de concentración. Una botica y semillas para empezar."),
    "perk.noirm.high_grade_pills": ("High-Grade Pills", "Píldoras de alto grado"),
    "perk.noirm.high_grade_pills.got": ("+5 pharmacology and the iron-bone and three-seal recipes.",
                                        "+5 de farmacología y las recetas de hueso de hierro y de los tres sellos."),
    "service.noirm.pill_supply": ("Pill supply (2 soldier, 2 blood)", "Suministro de píldoras (2 del soldado, 2 de sangre)"),
    # ------------------------------------------------------------ guide
    "guide.noirm.tip.apothecary": ("Ninjas live on pills. Herbs grow wild (moonroot in forests, bloodleaf in jungles and swamps, "
                                   "ironmillet on the plains); craft an Apothecary (cobblestone, a bowl, a stick, planks) and brew your "
                                   "first pill. The Handbook's Medicine chapter has the rest.",
                                   "Los ninjas viven de píldoras. Las hierbas crecen silvestres (raíz de luna en los bosques, hoja de "
                                   "sangre en junglas y pantanos, mijo de hierro en las llanuras); fabrica una botica (roca, un cuenco, "
                                   "un palo, tablas) y prepara tu primera píldora. El capítulo de Medicina del manual cuenta el resto."),
    "handbook.noirm.medicine": ("Foods and Pills", "Comidas y píldoras"),
    "handbook.noirm.medicine.body": (
        "Good food does more than fill you. Onigiri and grilled fish quicken your stamina, pork ramen your chakra, spicy beef "
        "ramen makes training stick, plum onigiri and dango steady your posture, curry heals a little. Cook rice in a furnace, "
        "smoker or campfire; ramen shops and teahouses sell the rest.\n\n"
        "Pills are a medic's craft. Gather herbs: moonroot (forests and taiga), bloodleaf (jungles, swamps, dark forests), "
        "ironmillet (plains, savanna), mistcap (meadows, groves, windswept hills) and the rare ashthorn (badlands). Break a "
        "ripe one for the herb and seeds; they grow on grass, and fast on farmland. Florists sell seeds.\n\n"
        "Craft an Apothecary and right-click it. Click a recipe to fill the mortar from your bag, then Grind. Your "
        "pharmacology decides the success chance, the grade (crude, standard, refined, perfect) and how many you get. A "
        "failed brew leaves a ruined pill. Brewing trains pharmacology.\n\n"
        "Every pill has a cooldown and adds toxicity. Pills taken on top of others work less, and too many make you sick. "
        "Strong pills crash: the soldier pill leaves you slow and weak, the three-seal pill punishes you for twice as long "
        "as it helps.\n\n"
        "New recipes: Tsubaki the medic (Pill Lore, and High-Grade Pills for jōnin), medical scrolls from old chests, strong "
        "rogues and scroll shops. Shops and missions pay in pills too, and rogues carry a few: they will swallow them "
        "mid-fight.",
        "La buena comida hace más que llenarte. El onigiri y el pescado a la brasa aceleran tu aguante, el ramen de cerdo tu "
        "chakra, el ramen picante de ternera hace que el entrenamiento cunda, el onigiri de ciruela y el dango asientan tu "
        "postura, el curry cura un poco. Cuece el arroz en un horno, un ahumador o una hoguera; los puestos de ramen y las casas "
        "de té venden el resto.\n\n"
        "Las píldoras son oficio de médico. Recoge hierbas: raíz de luna (bosques y taiga), hoja de sangre (junglas, pantanos, "
        "bosques oscuros), mijo de hierro (llanuras, sabana), capuchón de niebla (praderas, arboledas, colinas ventosas) y el "
        "raro espino de ceniza (las mesetas). Rompe una madura para la hierba y sus semillas; crecen sobre hierba, y rápido en "
        "tierra de cultivo. Las floristerías venden semillas.\n\n"
        "Fabrica una botica y haz clic derecho. Haz clic en una receta para llenar el mortero desde tu bolsa y luego Moler. Tu "
        "farmacología decide la probabilidad de éxito, la calidad (tosca, normal, refinada, perfecta) y cuántas salen. Una "
        "mezcla fallida deja una píldora echada a perder. Preparar píldoras entrena la farmacología.\n\n"
        "Cada píldora tiene su espera y añade toxicidad. Las píldoras tomadas sobre otras funcionan menos, y demasiadas te "
        "enferman. Las fuertes tienen bajón: la del soldado te deja lento y débil, la de los tres sellos te castiga el doble "
        "de lo que te ayuda.\n\n"
        "Recetas nuevas: Tsubaki la médica (Saber de píldoras, y Píldoras de alto grado para jōnin), pergaminos médicos de "
        "cofres antiguos, renegados fuertes y tiendas de pergaminos. Las tiendas y las misiones también pagan en píldoras, y "
        "los renegados llevan algunas: se las tragan en pleno combate."),
    # ------------------------------------------------------------ commands
    "help.noirm.section.medicine": ("Foods, pills and pharmacology", "Comidas, píldoras y farmacología"),
    "help.noirm.medicine.status": ("Your pharmacology, toxicity, running effects and cooldowns",
                                   "Tu farmacología, toxicidad, efectos activos y esperas"),
    "help.noirm.medicine.recipes": ("Every pill recipe and whether you can brew it", "Todas las recetas de píldoras y si puedes prepararlas"),
    "help.noirm.medicine.give": ("Pills in your bag (grade: crude, standard, refined, perfect)",
                                 "Píldoras a tu bolsa (calidad: crude, standard, refined, perfect)"),
    "help.noirm.medicine.take": ("Swallow a pill at once, ignoring its cooldown", "Trágate una píldora al momento, sin esperar"),
    "help.noirm.medicine.pharmacology": ("Set your pharmacology", "Fija tu farmacología"),
    "help.noirm.medicine.learn": ("Learn a recipe (or all) as from a medical scroll", "Aprende una receta (o todas) como de un pergamino médico"),
    "help.noirm.medicine.clear": ("End every pill effect and crash; clear toxicity and cooldowns",
                                  "Acaba con todos los efectos y bajones; borra toxicidad y esperas"),
    "command.noirm.pill.status": ("Pharmacology %s (%s%%) · toxicity %s", "Farmacología %s (%s%%) · toxicidad %s"),
    "command.noirm.pill.effect_line": ("%s: %s, %ss", "%s: %s, %s s"),
    "command.noirm.pill.crash_line": ("%s crash: %s, %ss", "Bajón de %s: %s, %s s"),
    "command.noirm.pill.cooldown_line": ("%s: ready in %ss", "%s: lista en %s s"),
    "command.noirm.pill.recipes": ("Pill recipes", "Recetas de píldoras"),
    "command.noirm.pill.recipe_line": ("%s (pharmacology %s): %s%% success, %s", "%s (farmacología %s): %s%% de éxito, %s"),
    "command.noirm.pill.state.ready": ("ready", "lista"),
    "command.noirm.pill.state.low_skill": ("needs more skill", "necesita más oficio"),
    "command.noirm.pill.state.unknown": ("not learned", "sin aprender"),
    "command.noirm.pill.unknown": ("No pill called %s.", "No hay ninguna píldora llamada %s."),
    "command.noirm.pill.unknown_grade": ("No grade called %s (crude, standard, refined, perfect).",
                                         "No hay ninguna calidad llamada %s (crude, standard, refined, perfect)."),
    "command.noirm.pill.given": ("Gave %s× %s.", "Dadas %s× %s."),
    "command.noirm.pill.taken": ("Swallowed: %s (toxicity %s).", "Tragada: %s (toxicidad %s)."),
    "command.noirm.pill.pharmacology": ("Pharmacology is now %s.", "La farmacología es ahora %s."),
    "command.noirm.pill.learned": ("Learned %s recipe(s).", "Aprendidas %s receta(s)."),
    "command.noirm.pill.cleared": ("Every pill effect, crash, cooldown and the toxicity cleared.",
                                   "Borrados todos los efectos, bajones, esperas y la toxicidad."),
}


def main():
    for code, idx in (("en_us", 0), ("es_es", 1), ("es_mx", 1)):
        p = os.path.join(LANG, code + ".json")
        data = json.load(open(p, encoding="utf-8"))
        for k, v in T.items():
            data[k] = v[idx]
        with open(p, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("merged %d keys into 3 lang files" % len(T))


if __name__ == "__main__":
    main()
