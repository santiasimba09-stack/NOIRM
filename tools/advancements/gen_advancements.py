"""Writes the "Path of the Shinobi" advancement tab from the table below.

Run from the repository root after changing the tree: python3 tools/advancements/gen_advancements.py

It writes:
  src/main/resources/data/noirm/advancements/shinobi/**.json   (one file per advancement)
  src/main/resources/data/noirm/loot_tables/advancement/*.json  (the item and ryo rewards)
  the advancement.noirm.* titles and descriptions in en_us, es_es and es_mx

How an advancement is earned: its criterion names a NOIRM trigger (com.noirm.progress) and the
conditions it must match ("event", "key", "min"; see NoirmTrigger). The tree reads as a guide: each
description says where to go or whom to talk to. Frames: task (plain), goal (rounded) and
challenge (spiky, purple toast). Hidden ones only show once earned (clan-only eyes, workshop).
"""
import json
import os
import shutil

ROOT = "src/main/resources"
ADV = f"{ROOT}/data/noirm/advancements/shinobi"
LOOT = f"{ROOT}/data/noirm/loot_tables/advancement"
LANG = f"{ROOT}/assets/noirm/lang"

# Rewards (loot tables). Modest: a little ryo or a useful item, never a skip.
POUCH = lambda n: {"item": "noirm:ryo_pouch", "nbt": "{Ryo:%d}" % n}
REWARDS = {
    "ryo_50": [POUCH(50)],
    "ryo_100": [POUCH(100)],
    "ryo_200": [POUCH(200)],
    "ryo_300": [POUCH(300)],
    "ryo_500": [POUCH(500)],
    "ramen": [{"item": "noirm:ramen", "count": 2}],
    "ramen_crew": [{"item": "noirm:ramen", "count": 4}],
    "chakra_paper": [{"item": "noirm:chakra_paper", "count": 2}],
    "sealing_scroll": [{"item": "noirm:sealing_scroll", "count": 1}],
}

# (id, parent, icon, frame, hidden, trigger, conditions, xp, reward,
#  en title, en description, es title, es description)
T, G, C = "task", "goal", "challenge"
TREE = [
    # ------------------------------------------------------------------ root
    ("root", None, "noirm:forehead_protector_navy", T, False, "minecraft:tick", {}, 0, None,
     "Path of the Shinobi",
     "From an Academy student in plain clothes to Kage. Follow the branches: each step says where to go next.",
     "El camino del shinobi",
     "De estudiante de la Academia en ropa sencilla hasta Kage. Sigue las ramas: cada paso te dice adónde ir."),

    # ------------------------------------------------------------------ beginnings
    ("beginnings/character", "root", "minecraft:name_tag", T, False, "noirm:beginnings", {"event": "created"}, 0, None,
     "A New Shinobi",
     "Make your character: keep a roll and choose your look. Your clan gives your first natures and jutsu.",
     "Un nuevo shinobi",
     "Crea tu personaje: quédate con una tirada y elige tu aspecto. Tu clan te da tus primeras naturalezas y jutsus."),
    ("beginnings/handbook", "beginnings/character", "noirm:shinobi_handbook", T, False, "noirm:beginnings", {"event": "handbook"}, 10, "ramen",
     "Read the Handbook",
     "Right-click the Shinobi Handbook in your bag. Lost it? Craft one from a book and an ink sac.",
     "Lee el manual",
     "Haz clic derecho con el Manual del Shinobi de tu bolsa. ¿Lo perdiste? Fabrica otro con un libro y un saco de tinta."),
    ("beginnings/training", "beginnings/handbook", "noirm:training_dummy", T, False, "noirm:beginnings", {"event": "training"}, 10, None,
     "Hit the Dummy",
     "Craft a Training Dummy (hay bales and sticks), place it, press R for combat mode and strike it.",
     "Golpea el muñeco",
     "Fabrica un muñeco de entrenamiento (fardos de heno y palos), colócalo, pulsa R para el modo combate y golpéalo."),
    ("beginnings/stat_15", "beginnings/training", "minecraft:iron_ingot", T, False, "noirm:stat", {"min": 15}, 20, "ryo_50",
     "Growing Stronger",
     "Raise any stat to 15. Dummy hits train taijutsu, sprinting trains speed, meditating (N) trains reserves.",
     "Cada vez más fuerte",
     "Sube cualquier estadística a 15. Golpear el muñeco entrena taijutsu, correr entrena velocidad y meditar (N), reservas."),
    ("beginnings/stat_30", "beginnings/stat_15", "minecraft:gold_ingot", G, False, "noirm:stat", {"min": 30}, 50, None,
     "Nothing Left to Learn",
     "Raise a stat to 30, where solo drills stop teaching. From here only real opponents make you stronger.",
     "Nada más que aprender",
     "Sube una estadística a 30: ahí el entrenamiento en solitario deja de enseñar. Desde ahora solo los rivales de verdad te hacen más fuerte."),
    ("beginnings/stat_50", "beginnings/stat_30", "minecraft:diamond", C, False, "noirm:stat", {"min": 50}, 150, None,
     "Peak Condition",
     "Raise a stat to 50 by beating stronger and stronger foes.",
     "En plena forma",
     "Sube una estadística a 50 derrotando a rivales cada vez más fuertes."),

    # ------------------------------------------------------------------ jutsu
    ("jutsu/first_cast", "beginnings/handbook", "noirm:jutsu_scroll_c", T, False, "noirm:jutsu", {"event": "cast"}, 10, None,
     "First Jutsu",
     "Press J and put a jutsu in your loadout, then in combat mode (R) press 1-4 to cast it.",
     "Primer jutsu",
     "Pulsa J y pon un jutsu en tu equipo; luego, en modo combate (R), pulsa 1-4 para lanzarlo."),
    ("jutsu/first_star", "jutsu/first_cast", "minecraft:glowstone_dust", T, False, "noirm:jutsu", {"event": "mastery", "min": 1}, 20, "chakra_paper",
     "One Star",
     "Keep casting a jutsu until it reaches ★1. At ★2 it teaches the next jutsu of its line.",
     "Una estrella",
     "Sigue lanzando un jutsu hasta que llegue a ★1. Con ★2 te enseña el siguiente jutsu de su línea."),
    ("jutsu/a_rank", "jutsu/first_star", "noirm:jutsu_scroll_a", G, False, "noirm:jutsu", {"event": "learned", "key": "A"}, 60, None,
     "A-Rank Arsenal",
     "Learn an A-rank jutsu: through a mastery chain, an A-rank scroll (Reserves 25, jōnin) or a master.",
     "Arsenal de rango A",
     "Aprende un jutsu de rango A: con una cadena de maestría, un pergamino de rango A (Reservas 25, jōnin) o un maestro."),
    ("jutsu/s_rank", "jutsu/a_rank", "noirm:jutsu_scroll_s", C, False, "noirm:jutsu", {"event": "learned", "key": "S"}, 150, None,
     "Forbidden Knowledge",
     "Learn an S-rank jutsu. S-rank scrolls need Reserves 40 and the ANBU rank; legends sometimes carry them.",
     "Conocimiento prohibido",
     "Aprende un jutsu de rango S. Los pergaminos de rango S piden Reservas 40 y el rango ANBU; a veces los llevan las leyendas."),
    ("jutsu/create_own", "jutsu/a_rank", "minecraft:writable_book", C, True, "noirm:create_jutsu", {}, 100, None,
     "A Jutsu of Your Own",
     "Design your own jutsu in the jutsu workshop.",
     "Un jutsu propio",
     "Diseña tu propio jutsu en el taller de jutsus."),

    # ------------------------------------------------------------------ natures
    ("natures/paper", "beginnings/handbook", "noirm:chakra_paper", T, False, "noirm:nature", {"event": "revealed"}, 15, None,
     "Paper Talk",
     "Hold right-click with Chakra Paper (2 paper + 1 lapis, or a scroll shop): a faint second reaction shows a hidden nature.",
     "Lo que dice el papel",
     "Mantén el clic derecho con papel de chakra (2 papeles + 1 lapislázuli, o una tienda de pergaminos): una segunda reacción débil revela una naturaleza oculta."),
    ("natures/first_drill", "natures/paper", "minecraft:oak_leaves", T, False, "noirm:nature", {"event": "drill"}, 50, "ryo_200",
     "The Long Drill",
     "Meditate (N) in a nature's setting until the drill is done: hold leaves for wind, sit by a flame, by running water, under a lightning rod or deep in stone.",
     "El largo entrenamiento",
     "Medita (N) en el entorno de una naturaleza hasta acabar: sostén hojas para el viento, siéntate junto a una llama, al agua que corre, bajo un pararrayos o rodeado de piedra."),
    ("natures/second_nature", "natures/first_drill", "minecraft:blaze_powder", T, False, "noirm:nature", {"event": "owned", "min": 2}, 30, None,
     "Two Natures",
     "Own two natures. Rensai in a village Academy makes drills twice as fast; crew mates who have the nature help too.",
     "Dos naturalezas",
     "Ten dos naturalezas. Rensai, en la Academia de una aldea, hace que los entrenamientos vayan el doble de rápido; los compañeros que la tienen también ayudan."),
    ("natures/combined", "natures/second_nature", "minecraft:packed_ice", G, False, "noirm:nature", {"event": "combined", "min": 1}, 100, None,
     "Kekkei Genkai",
     "Bring a jutsu of each of two natures to ★2, then open J → Nature Tree and unlock their combination.",
     "Kekkei genkai",
     "Lleva un jutsu de cada una de dos naturalezas a ★2; luego abre J → Árbol de naturalezas y desbloquea su combinación."),
    ("natures/fuse_alone", "natures/combined", "minecraft:magma_block", C, True, "noirm:fuse_natures", {}, 200, None,
     "Born of Nothing",
     "Fuse two natures into a kekkei genkai alone, with no clan gift and no teacher.",
     "Nacido de la nada",
     "Fusiona tú solo dos naturalezas en un kekkei genkai, sin don del clan ni maestro."),

    # ------------------------------------------------------------------ missions, rank and money
    ("missions/first", "beginnings/handbook", "noirm:mission_board", T, False, "noirm:mission", {"event": "completed", "min": 1}, 15, "ryo_50",
     "Reporting for Duty",
     "Find the missions board in a village, take a D-rank mission and report back to a board when it's done.",
     "A sus órdenes",
     "Busca el tablón de misiones de una aldea, acepta una misión de rango D y vuelve a un tablón para informar cuando termines."),
    ("missions/ryo_1000", "missions/first", "noirm:ryo_pouch", T, False, "noirm:ryo", {"min": 1000}, 20, None,
     "Pocket Money",
     "Hold 1,000 ryo in your wallet. Missions and bounties pay; spend it in village shops and on masters' lessons.",
     "Dinero de bolsillo",
     "Ten 1.000 ryo en tu cartera. Las misiones y las recompensas pagan; gástalos en las tiendas de la aldea y en las lecciones de los maestros."),
    ("missions/ryo_10000", "missions/ryo_1000", "minecraft:gold_block", G, False, "noirm:ryo", {"min": 10000}, 60, None,
     "Wealthy Shinobi",
     "Hold 10,000 ryo. Bingo-book bounties pay the most.",
     "Shinobi adinerado",
     "Ten 10.000 ryo. Las recompensas del libro bingo son las que más pagan."),
    ("rank/genin", "missions/first", "noirm:forehead_protector_navy", T, False, "noirm:rank", {"key": "genin"}, 30, "ryo_100",
     "Genin",
     "With 30 rank points, start the genin test from a missions board and pass it.",
     "Genin",
     "Con 30 puntos de rango, empieza la prueba de genin desde un tablón de misiones y apruébala."),
    ("rank/chunin", "rank/genin", "noirm:flak_vest", T, False, "noirm:rank", {"key": "chunin"}, 50, "ryo_200",
     "Chūnin",
     "Reach 150 rank points with C- and B-rank missions, then pass the chūnin exam at a board.",
     "Chūnin",
     "Llega a 150 puntos de rango con misiones C y B y aprueba el examen chūnin en un tablón."),
    ("rank/jonin", "rank/chunin", "noirm:jonin_vest", G, False, "noirm:rank", {"key": "jonin"}, 80, "ryo_300",
     "Jōnin",
     "Reach 500 rank points and pass the jōnin trial.",
     "Jōnin",
     "Llega a 500 puntos de rango y supera la prueba jōnin."),
    ("rank/anbu", "rank/jonin", "noirm:anbu_mask", G, False, "noirm:rank", {"key": "anbu"}, 100, "ryo_500",
     "ANBU",
     "Reach 1,000 rank points with three A-rank missions done: the village invites you into the ANBU.",
     "ANBU",
     "Llega a 1.000 puntos de rango con tres misiones de rango A hechas: la aldea te invita a la ANBU."),
    ("missions/s_rank", "rank/anbu", "noirm:mission_scroll", G, False, "noirm:mission", {"event": "completed", "key": "S", "min": 1}, 100, None,
     "S-Rank Mission",
     "As ANBU, take an S-rank mission from a board and come back alive.",
     "Misión de rango S",
     "Como ANBU, acepta una misión de rango S en un tablón y vuelve con vida."),
    ("rank/kage_candidate", "missions/s_rank", "noirm:kage_hat_red", C, False, "noirm:rank", {"key": "kage_candidate"}, 500, "ryo_500",
     "Kage Candidate",
     "Reach 2,500 rank points with two S-rank missions done. The Kage summons you: the village's future is in your hands.",
     "Candidato a Kage",
     "Llega a 2.500 puntos de rango con dos misiones de rango S hechas. El Kage te llama: el futuro de la aldea está en tus manos."),

    # ------------------------------------------------------------------ combat
    ("combat/first_defeat", "beginnings/training", "minecraft:iron_sword", T, False, "noirm:defeat", {"event": "defeat"}, 20, "ramen",
     "First Victory",
     "Defeat a ninja. Wild ones roam the land after your first minutes; bring one below a quarter of its health and it yields.",
     "Primera victoria",
     "Derrota a un ninja. Los salvajes recorren el mundo pasados tus primeros minutos; déjalo por debajo de un cuarto de su vida y se rendirá."),
    ("combat/parry", "combat/first_defeat", "minecraft:shield", G, False, "noirm:combat_feat", {"event": "just_frame_parry"}, 50, None,
     "Perfect Timing",
     "Raise your guard (V) the instant a heavy attack slowed by your perception lands: a just-frame parry. Then counter.",
     "Sincronía perfecta",
     "Sube la guardia (V) justo cuando llega un ataque pesado que tu percepción ralentiza: un bloqueo perfecto. Luego contraataca."),
    ("combat/class_c", "combat/first_defeat", "minecraft:stone_sword", T, False, "noirm:defeat", {"event": "defeat", "min": 1}, 20, None,
     "C-Class",
     "Defeat a C-class ninja. A ninja's class letter shows by their name; foes grow stronger farther from spawn.",
     "Clase C",
     "Derrota a un ninja de clase C. La letra de su clase aparece junto a su nombre; los enemigos son más fuertes lejos del punto de inicio."),
    ("combat/class_b", "combat/class_c", "minecraft:iron_sword", T, False, "noirm:defeat", {"event": "defeat", "min": 2}, 40, None,
     "B-Class",
     "Defeat a B-class ninja.",
     "Clase B",
     "Derrota a un ninja de clase B."),
    ("combat/class_a", "combat/class_b", "minecraft:diamond_sword", G, False, "noirm:defeat", {"event": "defeat", "min": 3}, 80, "ryo_300",
     "A-Class",
     "Defeat an A-class ninja. The bingo book (sold in villages) shows where A-class rogues were last seen.",
     "Clase A",
     "Derrota a un ninja de clase A. El libro bingo (se vende en las aldeas) muestra dónde se vio por última vez a los renegados de clase A."),
    ("combat/bounty", "combat/class_a", "noirm:bingo_book", G, False, "noirm:defeat", {"event": "bounty"}, 60, None,
     "Bounty Hunter",
     "Bring down a rogue listed in the bingo book and collect the bounty.",
     "Cazarrecompensas",
     "Acaba con un renegado del libro bingo y cobra la recompensa."),
    ("combat/class_s", "combat/class_a", "minecraft:netherite_sword", C, False, "noirm:defeat", {"event": "defeat", "min": 4}, 200, "ryo_500",
     "S-Class",
     "Defeat an S-class ninja.",
     "Clase S",
     "Derrota a un ninja de clase S."),
    ("combat/legend", "combat/class_s", "minecraft:dragon_head", C, False, "noirm:defeat", {"event": "defeat", "min": 5}, 500, "ryo_500",
     "Living Legend",
     "Defeat one of the bingo book's legends (S+). They live far from spawn and leave you alone in your first hours.",
     "Leyenda viva",
     "Derrota a una de las leyendas del libro bingo (S+). Viven lejos del punto de inicio y no te buscan en tus primeras horas."),

    # ------------------------------------------------------------------ crew
    ("crew/recruit", "combat/first_defeat", "minecraft:lead", T, False, "noirm:crew", {"event": "size", "min": 1}, 20, "ramen_crew",
     "Strength in Numbers",
     "When a beaten ninja yields (kneels), right-click them to invite them into your crew.",
     "La unión hace la fuerza",
     "Cuando un ninja derrotado se rinda (se arrodilla), haz clic derecho sobre él para invitarlo a tu equipo."),
    ("crew/full", "crew/recruit", "minecraft:bell", G, False, "noirm:crew", {"event": "size", "min": 3}, 60, None,
     "A Full Squad",
     "Lead three crew mates at once.",
     "Escuadra completa",
     "Lidera a tres compañeros a la vez."),
    ("crew/teach", "crew/recruit", "minecraft:book", G, False, "noirm:crew", {"event": "taught"}, 60, None,
     "Sensei",
     "Teach a crew mate one of your natures: pick a lesson in their screen, then train or meditate near them.",
     "Sensei",
     "Enseña a un compañero una de tus naturalezas: elige una lección en su pantalla y entrena o medita cerca de él."),

    # ------------------------------------------------------------------ masters
    ("masters/might_guy", "beginnings/stat_15", "minecraft:lime_dye", G, False, "noirm:master", {"key": "might_guy"}, 50, None,
     "Youthful Spirit",
     "Find Might Guy at a Leaf capital's training ground and pass his spar: 6 clean hits or a guard break in 90 seconds.",
     "Espíritu juvenil",
     "Busca a Might Guy en el campo de entrenamiento de una capital de la Hoja y supera su combate: 6 golpes limpios o romper su guardia en 90 segundos."),
    ("masters/scholar_rensai", "jutsu/first_cast", "noirm:jutsu_scroll_b", G, False, "noirm:master", {"key": "scholar_rensai"}, 50, None,
     "The Scholar's Duel",
     "Find Rensai in a hidden village's Academy and land 4 jutsu hits on him in 120 seconds.",
     "El duelo del erudito",
     "Busca a Rensai en la Academia de una aldea oculta y alcánzalo con 4 jutsus en 120 segundos."),
    ("masters/medic_tsubaki", "missions/first", "noirm:antidote", G, False, "noirm:master", {"key": "medic_tsubaki"}, 50, None,
     "Steady Hands",
     "Find Tsubaki in a village hospital and keep her patient alive for 40 seconds: crouch beside them with combat mode off.",
     "Manos firmes",
     "Busca a Tsubaki en el hospital de una aldea y mantén con vida a su paciente 40 segundos: agáchate a su lado con el modo combate apagado."),
    ("masters/stone_hermit", "natures/first_drill", "minecraft:mossy_cobblestone", G, False, "noirm:master", {"key": "stone_hermit"}, 80, None,
     "The Hermit's Pool",
     "Follow the rumours (/noirm sage rumour) to the Stone Hermit's sanctuary and meditate by his pool while spirits attack.",
     "El estanque del ermitaño",
     "Sigue los rumores (/noirm sage rumour) hasta el santuario del Ermitaño de Piedra y medita junto a su estanque mientras atacan los espíritus."),
    ("sage/sage_mode", "masters/stone_hermit", "minecraft:moss_block", C, False, "noirm:sage", {"event": "mode"}, 150, None,
     "Sage Mode",
     "Gather nature energy by meditating still, then enter Sage Mode (/noirm sage on). Take in too much and you turn to stone.",
     "Modo sabio",
     "Reúne energía natural meditando sin moverte y entra en Modo sabio (/noirm sage on). Si absorbes demasiada, te conviertes en piedra."),

    # ------------------------------------------------------------------ eyes (clan-only: hidden until earned)
    ("eyes/byakugan", "beginnings/character", "minecraft:ender_eye", T, True, "noirm:eyes", {"key": "byakugan"}, 20, None,
     "All-Seeing",
     "Hyūga only: press G to open the Byakugan and see chakra through walls.",
     "Lo ve todo",
     "Solo Hyūga: pulsa G para abrir el Byakugan y ver el chakra a través de las paredes."),
    ("eyes/sharingan", "combat/first_defeat", "noirm:sharingan_eye", G, True, "noirm:eyes", {"key": "sharingan", "min": 1}, 50, None,
     "The Eyes Awaken",
     "An Uchiha's Sharingan wakes at the edge of death, at a comrade's fall, or in a long fight against someone far stronger.",
     "Despiertan los ojos",
     "El Sharingan de un Uchiha despierta al borde de la muerte, al caer un compañero o en una larga pelea contra alguien mucho más fuerte."),
    ("eyes/three_tomoe", "eyes/sharingan", "minecraft:redstone", G, True, "noirm:eyes", {"key": "sharingan", "min": 3}, 80, None,
     "Three Tomoe",
     "Grow the Sharingan to three tomoe in real fights.",
     "Tres tomoe",
     "Haz crecer el Sharingan hasta tres tomoe en combates de verdad."),
    ("eyes/mangekyo", "eyes/three_tomoe", "noirm:mangekyo_eyes", C, True, "noirm:eyes", {"key": "mangekyo"}, 200, None,
     "Mangekyō",
     "With three tomoe, live through a deep loss: a devoted crew mate falling beside you.",
     "Mangekyō",
     "Con tres tomoe, sobrevive a una gran pérdida: un compañero leal que cae a tu lado."),

    # ------------------------------------------------------------------ tailed beasts
    ("beasts/subdue", "combat/class_b", "minecraft:fire_charge", G, False, "noirm:beast", {"event": "subdued"}, 80, "sealing_scroll",
     "Beast Tamer",
     "Beat a tailed beast down until it lies still. A jinchūriki ninja's beast breaks free when you beat them.",
     "Domador de bestias",
     "Derriba a una bestia con colas hasta que se quede quieta. La bestia de un ninja jinchūriki se libera cuando lo vences."),
    ("beasts/seal", "beasts/subdue", "noirm:sealing_scroll", G, False, "noirm:beast", {"event": "sealed"}, 100, None,
     "Sealed Away",
     "Use a Sealing Scroll on a subdued beast before it rises again.",
     "Sellado",
     "Usa un pergamino de sellado sobre una bestia sometida antes de que se levante."),
    ("beasts/jinchuriki", "beasts/seal", "minecraft:nether_star", C, False, "noirm:beast", {"event": "jinchuriki"}, 200, None,
     "Jinchūriki",
     "Use the full scroll in the air to seal the beast into yourself, then press B to wear its cloak.",
     "Jinchūriki",
     "Usa el pergamino lleno al aire para sellar la bestia en ti y pulsa B para vestir su manto."),
]


def key(path, part):
    return "advancement.noirm.shinobi.%s.%s" % (path.replace("/", "."), part)


def main():
    shutil.rmtree(ADV, ignore_errors=True)
    os.makedirs(LOOT, exist_ok=True)
    for name, items in REWARDS.items():
        entries = []
        for it in items:
            functions = []
            if it.get("count", 1) > 1:
                functions.append({"function": "minecraft:set_count", "count": it["count"]})
            if "nbt" in it:
                functions.append({"function": "minecraft:set_nbt", "tag": it["nbt"]})
            entry = {"type": "minecraft:item", "name": it["item"]}
            if functions:
                entry["functions"] = functions
            entries.append(entry)
        table = {"type": "minecraft:advancement_reward", "pools": [{"rolls": 1, "entries": entries}]}
        json.dump(table, open(f"{LOOT}/{name}.json", "w"), indent=2)
        open(f"{LOOT}/{name}.json", "a").write("\n")

    lang = {"en_us": {}, "es": {}}
    ids = {row[0] for row in TREE}
    for (path, parent, icon, frame, hidden, trigger, conditions, xp, reward, en_t, en_d, es_t, es_d) in TREE:
        assert parent is None or parent in ids, (path, parent)
        display = {
            "icon": {"item": icon},
            "title": {"translate": key(path, "title")},
            "description": {"translate": key(path, "description")},
            "frame": frame,
            "show_toast": parent is not None,
            "announce_to_chat": parent is not None,
            "hidden": hidden,
        }
        if parent is None:
            display["background"] = "minecraft:textures/block/deepslate_tiles.png"
        adv = {"display": display}
        if parent is not None:
            adv["parent"] = "noirm:shinobi/" + parent
        criterion = {"trigger": trigger}
        if conditions:
            criterion["conditions"] = conditions
        adv["criteria"] = {"done": criterion}
        rewards = {}
        if xp:
            rewards["experience"] = xp
        if reward:
            assert reward in REWARDS, reward
            rewards["loot"] = ["noirm:advancement/" + reward]
        if rewards:
            adv["rewards"] = rewards
        out = f"{ADV}/{path}.json"
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8") as f:
            json.dump(adv, f, indent=2, ensure_ascii=False)
            f.write("\n")
        lang["en_us"][key(path, "title")] = en_t
        lang["en_us"][key(path, "description")] = en_d
        lang["es"][key(path, "title")] = es_t
        lang["es"][key(path, "description")] = es_d

    for code, texts in (("en_us", lang["en_us"]), ("es_es", lang["es"]), ("es_mx", lang["es"])):
        p = f"{LANG}/{code}.json"
        data = json.load(open(p, encoding="utf-8"))
        for k in [k for k in data if k.startswith("advancement.noirm.shinobi.") and k not in texts]:
            del data[k]  # removed from the tree
        data.update(texts)
        with open(p, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("wrote %d advancements, %d reward tables" % (len(TREE), len(REWARDS)))


if __name__ == "__main__":
    main()
