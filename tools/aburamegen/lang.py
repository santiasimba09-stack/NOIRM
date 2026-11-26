"""Adds (or updates) the Aburame's text in en_us, es_es and es_mx.

    python3 tools/aburamegen/lang.py

Each entry is key: (English, Spanish). es_mx uses the Spanish text too.
"""
import json
import os
from collections import OrderedDict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LANG = os.path.join(ROOT, "src/main/resources/assets/noirm/lang")

T = {}


def add(key, en, es):
    T[key] = (en, es)


# ---------------------------------------------------------------- names
for k, en, es in [
    ("rank.nestling", "Nestling", "Cría"), ("rank.genin", "Genin hive", "Colmena genin"), ("rank.chunin", "Chūnin hive", "Colmena chūnin"),
    ("rank.jonin", "Jōnin hive", "Colmena jōnin"), ("rank.clan_master", "Clan Master", "Maestro del clan"), ("rank.living_hive", "Living Hive", "Colmena viva"),
    ("species.kikaichu", "Kikaichū", "Kikaichū"), ("species.kidaichu", "Kidaichū", "Kidaichū"), ("species.shokaichu", "Shōkaichū", "Shōkaichū"),
    ("species.rinkaichu", "Rinkaichū", "Rinkaichū"), ("species.kochu", "Kochū", "Kochū"),
]:
    add("aburame.noirm." + k, en, es)

WILD = [
    ("firefly", "Firefly", "Luciérnaga", "Swamps and rivers, at night", "Pantanos y ríos, de noche",
     "Scouts make targets glow for 30 s", "Los exploradores hacen brillar a los objetivos 30 s"),
    ("scarab", "Scarab", "Escarabajo sagrado", "Deserts", "Desiertos", "+10% fire resistance", "+10% de resistencia al fuego"),
    ("cave_cricket", "Cave Cricket", "Grillo de cueva", "Caves, below y=40", "Cuevas, por debajo de y=40",
     "Swarms see in the dark", "Los enjambres ven en la oscuridad"),
    ("water_strider", "Water Strider", "Zapatero", "Rivers, by day", "Ríos, de día", "Swarms cross water", "Los enjambres cruzan el agua"),
    ("moth", "Moth", "Polilla", "Dark forests", "Bosques oscuros", "Swarms under 1,000 are never noticed", "Los enjambres de menos de 1000 nunca se notan"),
    ("stag_beetle", "Stag Beetle", "Ciervo volante", "Forests", "Bosques", "Insect clones take 6+ damage to burst",
     "Los clones de insectos necesitan 6+ de daño para estallar"),
    ("jewel_beetle", "Jewel Beetle", "Escarabajo joya", "Jungles", "Selvas", "Hive capacity +10,000", "Capacidad de colmena +10 000"),
    ("snow_flea", "Snow Flea", "Pulga de nieve", "Snowy places", "Lugares nevados", "+15% cold resistance", "+15% de resistencia al frío"),
    ("mantis", "Mantis", "Mantis", "Plains and meadows", "Llanuras y prados", "Parasitic Swarm bites on arrival", "El Enjambre Parásito muerde al llegar"),
    ("dung_beetle", "Dung Beetle", "Escarabajo pelotero", "Savannas", "Sabanas", "Swarms digest better", "Los enjambres digieren mejor"),
    ("ironclad_weevil", "Ironclad Weevil", "Gorgojo acorazado", "Mountains", "Montañas", "Blades kill 20% fewer insects", "Las hojas matan un 20% menos de insectos"),
    ("locust", "Locust", "Langosta", "Badlands", "Tierras baldías", "Insect Tornado pulls from 6 blocks", "El Tornado de Insectos atrae desde 6 bloques"),
    ("bikochu", "Bikōchū", "Bikōchū", "Old-growth taiga, the day after rain", "Taiga antigua, el día después de la lluvia",
     "Tracks the first scent it smells", "Sigue el primer olor que huele"),
]
for k, en, es, wen, wes, sen, ses in WILD:
    add(f"aburame.noirm.wild.{k}", en, es)
    add(f"aburame.noirm.wild.{k}.where", wen, wes)
    add(f"aburame.noirm.wild.{k}.strain", sen, ses)

add("entity.noirm.insect_swarm", "Insect Swarm", "Enjambre de insectos")
add("entity.noirm.insect_clone", "Insect Clone", "Clon de insectos")
add("entity.noirm.giant_insect", "Giant Insect", "Insecto gigante")
add("entity.noirm.wild_insect", "Wild Insect", "Insecto salvaje")
add("block.noirm.insect_hive", "Insect Hive", "Colmena de insectos")
add("block.noirm.breeding_jar", "Breeding Jar", "Tarro de cría")
add("block.noirm.pitcher_plant", "Pitcher Plant", "Planta jarra")
for k, en, es in [("insect_jar", "Insect Jar", "Tarro de insectos"), ("insect_gourd", "Insect Gourd", "Calabaza de insectos"),
                  ("insect_case", "Insect Case", "Caja de insectos"), ("bug_net", "Bug Net", "Red cazabichos"),
                  ("aburame_gloves", "Aburame Gloves", "Guantes Aburame"), ("kidaichu_egg", "Kidaichū Egg", "Huevo de kidaichū"),
                  ("queen_cocoon", "Queen Cocoon", "Capullo de reina"), ("bikochu_egg", "Bikōchū Egg", "Huevo de bikōchū"),
                  ("bikochu", "Bikōchū", "Bikōchū"), ("wild_grass_salad", "Wild-Grass Salad", "Ensalada de hierbas silvestres")]:
    add("item.noirm." + k, en, es)
add("item.noirm.insect_jar.count", "%s / %s %s", "%s / %s %s")
add("item.noirm.insect_jar.desc", "Use: fill from your body. Sneak + use: empty into it. Works from your inventory (two jars count).",
    "Usar: llenar desde tu cuerpo. Agachado + usar: vaciar. Funciona desde el inventario (cuentan dos tarros).")
add("item.noirm.insect_gourd.desc", "The big reserve (25,000), like Shibi's. Works from your inventory. An explosion can burst it.",
    "La gran reserva (25 000), como la de Shibi. Funciona desde el inventario. Una explosión puede reventarla.")
add("item.noirm.insect_case.desc", "Carry it to catch wild insects. Use it to see your collection.",
    "Llévala para atrapar insectos salvajes. Úsala para ver tu colección.")
add("item.noirm.bug_net.desc", "Use on a wild insect to catch it (you need an Insect Case).",
    "Úsala sobre un insecto salvaje para atraparlo (necesitas una caja de insectos).")
add("item.noirm.aburame_gloves.desc", "Keeps a rinkaichū host's poison in. Use to put on or take off.",
    "Contienen el veneno de un portador de rinkaichū. Usar para ponerlos o quitarlos.")
add("item.noirm.aburame_gloves.worn", "Worn", "Puestos")
add("item.noirm.aburame_gloves.off", "Not worn", "Sin poner")
add("item.noirm.kidaichu_egg.desc", "Hatch into your colony: kidaichū (needs a species slot and CTL 30).",
    "Incúbalo en tu colonia: kidaichū (necesita un hueco de especie y CTL 30).")
add("item.noirm.queen_cocoon.desc", "Bond with her to summon the Giant Insect.", "Vincúlate con ella para invocar al insecto gigante.")
add("item.noirm.bikochu_egg.desc", "Use it on a creature: its smell is the first the beetle knows.",
    "Úsalo sobre una criatura: su olor será el primero que conozca el escarabajo.")
add("item.noirm.bikochu.desc", "Use it: it points to the scent it knows (3 days).", "Úsalo: señala el olor que conoce (3 días).")
add("item.noirm.wild_grass_salad.desc", "Shino's favourite. +10 Vigour for an Aburame's colony.", "La favorita de Shino. +10 de vigor para la colonia de un Aburame.")
add("item.noirm.scout_map", "Scout's Map", "Mapa del explorador")
add("effect.noirm.drained", "Drained", "Drenado")
add("effect.noirm.exhausted", "Exhausted", "Agotado")
add("effect.noirm.cell_rot", "Cell Rot", "Putrefacción celular")
add("key.noirm.swarm", "Swarm (tap: recall, hold: send or park, double: colony)", "Enjambre (toque: llamar, mantener: enviar o aparcar, doble: colonia)")
add("perk.noirm.colony", "Insect colony", "Colonia de insectos")
add("perk.noirm.colony.desc", "The body is a hive: an Aburame insect colony that eats chakra, breeds and fights for you.",
    "El cuerpo es una colmena: una colonia de insectos Aburame que come chakra, se cría y lucha por ti.")
for s, en, es in [("hum_small", "Insects buzz", "Zumban insectos"), ("hum_large", "A swarm roars", "Ruge un enjambre"),
                  ("itch", "Something itches", "Algo pica"), ("burst", "Insects pour out", "Brotan insectos"),
                  ("recall", "Insects return", "Vuelven los insectos")]:
    add("subtitles.noirm.insect." + s, en, es)
add("ally.noirm.reason.jar_of_poison", "used the forbidden Jar of Poison", "usó el prohibido Tarro de Veneno")

# ---------------------------------------------------------------- techniques
JUTSU = [
    ("parasitic_swarm", "swarm_send", "Parasitic Swarm", "Enjambre Parásito", "A homing swarm that clings and drains", "Un enjambre que persigue, se aferra y drena"),
    ("female_insect", "swarm_mark", "Female Insect", "Insecto Hembra", "Marks a creature you can track anywhere", "Marca a una criatura que puedes rastrear"),
    ("insect_clone", "swarm_clone", "Insect Clone", "Clon de Insectos", "A copy of you that bursts into a swarm", "Una copia tuya que estalla en enjambre"),
    ("insect_wall", "swarm_wall", "Insect Wall", "Muro de Insectos", "A wall that stops and eats jutsu", "Un muro que detiene y come jutsu"),
    ("scout_swarm", "swarm_scout", "Scout Swarm", "Enjambre Explorador", "Watches a far spot and reports", "Vigila un punto lejano e informa"),
    ("poison_draw", "poison_draw", "Poison Draw", "Extracción de Veneno", "Insects pull poison out", "Los insectos sacan el veneno"),
    ("insect_gathering", "insect_gathering", "Insect Gathering", "Reunión de Insectos", "Calls wild insects and asks them", "Llama a los insectos salvajes y les pregunta"),
    ("insect_cocoon", "insect_cocoon", "Insect Cocoon", "Capullo de Insectos", "Breeds the colony fast; you can't move", "Cría rápido la colonia; no puedes moverte"),
    ("insect_sphere", "swarm_sphere", "Insect Sphere", "Esfera de Insectos", "A ball around the target: drain ×3", "Una bola alrededor del objetivo: drenaje ×3"),
    ("insect_jamming", "swarm_jam", "Insect Jamming", "Interferencia de Insectos", "False chakra signals hide you", "Señales falsas de chakra te ocultan"),
    ("insect_jar", "swarm_dome", "Insect Jar", "Tarro de Insectos", "A spinning dome around you", "Una cúpula que gira a tu alrededor"),
    ("insect_tether", "swarm_tether", "Insect Tether", "Cuerda de Insectos", "A rope of insects pulls you", "Una cuerda de insectos tira de ti"),
    ("insect_tornado", "swarm_tornado", "Insect Tornado", "Tornado de Insectos", "Lifts, bites and drains the target", "Levanta, muerde y drena al objetivo"),
    ("bug_bite", "bug_bite", "Bug Bite", "Mordida de Insecto", "Kidaichū burrow in and eat", "Los kidaichū se meten y devoran"),
    ("chakra_gift", "chakra_gift", "Chakra Gift", "Regalo de Chakra", "Gives an ally your swarms' chakra", "Da a un aliado el chakra de tus enjambres"),
    ("poison_cloud", "poison_cloud", "Poison Cloud", "Nube Venenosa", "A cone of rinkaichū (hits allies too)", "Un cono de rinkaichū (también a aliados)"),
    ("giant_insect", "giant_insect", "Giant Insect", "Insecto Gigante", "A flying beetle you can ride", "Un escarabajo volador que puedes montar"),
    ("jar_of_poison", "jar_of_poison", "Jar of Poison", "Tarro de Veneno", "Forbidden: a poison storm over 48 blocks", "Prohibido: una tormenta de veneno de 48 bloques"),
    ("living_hive", "living_hive", "The Living Hive", "La Colmena Viva", "Your body becomes the swarm", "Tu cuerpo se vuelve el enjambre"),
    ("black_sea", "black_sea", "The Black Sea", "El Mar Negro", "Every insect you own floods the land", "Todos tus insectos inundan el lugar"),
    ("insect_legion", "insect_legion", "The Insect Legion", "La Legión de Insectos", "Five clones that fight", "Cinco clones que luchan"),
]
for path, beh, en, es, ben, bes in JUTSU:
    add(f"jutsu.noirm.aburame.{path}", en, es)
    add(f"screen.noirm.jutsu.behavior.{beh}", ben, bes)

# ---------------------------------------------------------------- messages
M = {
    "antivenom": ("Your colony has bred the Antivenom strain: poison no longer takes hold of you.", "Tu colonia ha criado la cepa Antiveneno: el veneno ya no te afecta."),
    "bikochu_found": ("A bikōchū egg! And someone else wanted it too...", "¡Un huevo de bikōchū! Y alguien más lo quería..."),
    "bikochu_imprinted": ("The bikōchū hatches and knows the scent of %s.", "El bikōchū nace y conoce el olor de %s."),
    "bikochu_lost": ("The bikōchū can't find the scent here.", "El bikōchū no encuentra el olor aquí."),
    "bikochu_points": ("%s: %s blocks %s", "%s: %s bloques al %s"),
    "bikochu_tired": ("The bikōchū has lost interest in that scent.", "El bikōchū ha perdido el interés en ese olor."),
    "caught_again": ("Another %s (%s/%s in the case)", "Otro %s (%s/%s en la caja)"),
    "caught_new": ("New for the case: %s (%s/%s)", "Nuevo para la caja: %s (%s/%s)"),
    "drained": ("Drained! Your chakra is gone.", "¡Drenado! No te queda chakra."),
    "drawing": ("Your insects draw the poison out...", "Tus insectos extraen el veneno..."),
    "entomologist": ("A full case! You are an Entomologist. The bikōchū quest is open: old-growth taiga, the day after rain.",
                     "¡Caja completa! Eres entomólogo. La misión del bikōchū está abierta: taiga antigua, el día después de la lluvia."),
    "gathering_quiet": ("The bugs saw no one nearby.", "Los bichos no vieron a nadie cerca."),
    "gathering_tells": ("The bugs saw %s: %s blocks %s.", "Los bichos vieron a %s: %s bloques al %s."),
    "gift": ("You give %s chakra to %s.", "Das %s de chakra a %s."),
    "gloves_off": ("Gloves off.", "Guantes fuera."),
    "gloves_on": ("Gloves on.", "Guantes puestos."),
    "gourd_burst": ("Your gourd bursts! %s insects die.", "¡Tu calabaza revienta! Mueren %s insectos."),
    "hive_deposit": ("Moved %s insects into the hive (%s/%s).", "Trasladaste %s insectos a la colmena (%s/%s)."),
    "hive_fed": ("Hive: %s/%s, flowers %s, honey %s", "Colmena: %s/%s, flores %s, miel %s"),
    "hive_honey": ("The hive will breed faster for a day.", "La colmena criará más rápido durante un día."),
    "hive_limit": ("You can keep at most %s hives.", "Puedes tener como máximo %s colmenas."),
    "hive_not_yours": ("That isn't yours.", "Eso no es tuyo."),
    "hive_refill": ("Your hives refilled your body: +%s insects.", "Tus colmenas rellenaron tu cuerpo: +%s insectos."),
    "hive_status": ("Hive: %s/%s", "Colmena: %s/%s"),
    "human_cocoon": ("A waterproof cocoon: you'll wake here.", "Un capullo impermeable: despertarás aquí."),
    "jar_begins": ("The Jar of Poison begins. In 20 seconds it bursts.", "Comienza el Tarro de Veneno. En 20 segundos estalla."),
    "jar_busy": ("The jar is already raising something.", "El tarro ya está criando algo."),
    "jar_cross_nosource": ("Crossing on, but you need rinkaichū: a rinkaichū jar, your own, or a rinkaichū crew mate.",
                           "Cruce activado, pero necesitas rinkaichū: un tarro, los tuyos o un compañero con rinkaichū."),
    "jar_cross_off": ("Crossing stopped.", "Cruce detenido."),
    "jar_cross_on": ("Crossing kikaichū with rinkaichū: one step each generation.", "Cruzando kikaichū con rinkaichū: un paso cada generación."),
    "jar_done": ("Your Breeding Jar is done: %s", "Tu tarro de cría ha terminado: %s"),
    "jar_egg_needs": ("A Kidaichū Egg needs 10,000 kikaichū and CTL 30.", "Un huevo de kidaichū necesita 10 000 kikaichū y CTL 30."),
    "jar_emptied": ("Emptied %s insects into your body.", "Vaciaste %s insectos en tu cuerpo."),
    "jar_filled": ("Filled the jar with %s insects.", "Llenaste el tarro con %s insectos."),
    "jar_focus": ("Breeding focus set.", "Enfoque de cría elegido."),
    "jar_needs": ("The Jar of Poison needs rotting corpses inside the barrier: %s/%s.", "El Tarro de Veneno necesita cadáveres podridos en la barrera: %s/%s."),
    "jar_of_poison": ("The poison storm spreads. Your crew will never forget this.", "La tormenta de veneno se extiende. Tu equipo nunca lo olvidará."),
    "jar_queen_needs": ("Raising a Queen needs Clan Master rank.", "Criar una reina requiere el rango de Maestro del clan."),
    "jar_started": ("Raising it: one day.", "Criando: un día."),
    "jar_strain": ("A new strain in your colony.", "Una nueva cepa en tu colonia."),
    "jar_strain_missing": ("Catch that insect first.", "Atrapa primero ese insecto."),
    "living_hive_off": ("The Living Hive settles.", "La Colmena Viva se calma."),
    "living_hive_on": ("The Living Hive: your skin crawls.", "La Colmena Viva: tu piel hormiguea."),
    "mark_miss": ("Get close enough to touch.", "Acércate lo bastante para tocar."),
    "marked": ("A female insect rides on %s.", "Un insecto hembra va sobre %s."),
    "need_case": ("You need an Insect Case to keep it.", "Necesitas una caja de insectos para guardarlo."),
    "needs_queen": ("You need a bonded Queen.", "Necesitas una reina vinculada."),
    "no_ally": ("No ally in reach.", "Ningún aliado al alcance."),
    "no_target": ("No target.", "Sin objetivo."),
    "none": ("You have no insect colony.", "No tienes colonia de insectos."),
    "parked": ("%s swarms wait there.", "%s enjambres esperan allí."),
    "queen": ("The Queen accepts you. You can summon the Giant Insect.", "La reina te acepta. Puedes invocar al insecto gigante."),
    "rank_up": ("Your colony grows: %s", "Tu colonia crece: %s"),
    "recalled": ("%s swarms fly home.", "%s enjambres vuelven a casa."),
    "scout_report": ("Scout: %s ninjas (%s), natures %s; %s beasts, %s civilians, %s chests.",
                     "Explorador: %s ninjas (%s), naturalezas %s; %s bestias, %s civiles, %s cofres."),
    "scout_spy": ("Spy: %s, class %s, knows %s.", "Espía: %s, clase %s, conoce %s."),
    "sea_regrowing": ("Your colony is still regrowing: %s more minutes.", "Tu colonia aún se regenera: %s minutos más."),
    "sent": ("%s swarms go for %s.", "%s enjambres van a por %s."),
    "sever_or_die": ("The rot spreads up your arm! Sneak and use a blade within %s s to cut it off.",
                     "¡La putrefacción sube por tu brazo! Agáchate y usa una hoja en %s s para cortarlo."),
    "species_added": ("Your colony now hosts %s.", "Tu colonia ahora alberga %s."),
    "species_already": ("You already host that species.", "Ya albergas esa especie."),
    "species_birth": ("Rinkaichū come only by birth.", "Los rinkaichū solo llegan por nacimiento."),
    "species_control": ("Kidaichū need CTL 30.", "Los kidaichū necesitan CTL 30."),
    "species_no_slot": ("No free species slot (they open with hive rank).", "No hay hueco de especie libre (se abren con el rango de colmena)."),
    "species_root": ("The Root path comes later.", "El camino de Raíz llegará más adelante."),
    "species_scouts": ("Shōkaichū need 10 scouting flights first.", "Los shōkaichū necesitan antes 10 vuelos de exploración."),
    "swarm_body": ("Swarm Body: you dissolve and re-form.", "Cuerpo de enjambre: te disuelves y te recompones."),
    "technique": ("New insect technique: %s", "Nueva técnica de insectos: %s"),
    "tether_miss": ("Nothing to hold on to.", "Nada a lo que agarrarse."),
    "too_far": ("Too far: touch them, or cover them with a swarm first.", "Demasiado lejos: tócalo o cúbrelo antes con un enjambre."),
    "too_many_swarms": ("Too many swarms out: recall some.", "Demasiados enjambres fuera: llama a algunos."),
    "wrong_dose": ("The wrong dose: your kidaichū bite you!", "Dosis equivocada: ¡tus kidaichū te muerden!"),
}
for k, (en, es) in M.items():
    add("message.noirm.colony." + k, en, es)
add("message.noirm.jutsu.needs_colony", "Needs a colony, the hive rank and the insects to send", "Necesita colonia, el rango de colmena y los insectos")
add("message.noirm.jutsu.drained", "Drained: no chakra to mould", "Drenado: no hay chakra que moldear")
add("message.noirm.sharingan.needs_colony", "No seals to read: that technique needs a colony", "No hay sellos que leer: esa técnica necesita una colonia")

# ---------------------------------------------------------------- screens
S = {
    "screen.noirm.colony": ("Colony", "Colonia"),
    "screen.noirm.colony.none": ("You host no insects. The Aburame are born with them.", "No albergas insectos. Los Aburame nacen con ellos."),
    "screen.noirm.colony.species": ("Species", "Especies"),
    "screen.noirm.colony.health": ("Health", "Salud"),
    "screen.noirm.colony.vigour": ("Vigour", "Vigor"),
    "screen.noirm.colony.upkeep": ("Upkeep", "Mantenimiento"),
    "screen.noirm.colony.growth": ("Growth", "Crecimiento"),
    "screen.noirm.colony.generation": ("Generation", "Generación"),
    "screen.noirm.colony.restless": ("Starving: restless!", "Hambrientos: ¡inquietos!"),
    "screen.noirm.colony.where": ("Where they live", "Dónde viven"),
    "screen.noirm.colony.body": ("Body", "Cuerpo"),
    "screen.noirm.colony.jars": ("Jars", "Tarros"),
    "screen.noirm.colony.field": ("Field", "Fuera"),
    "screen.noirm.colony.hives": ("Hives", "Colmenas"),
    "screen.noirm.colony.rank": ("Hive rank", "Rango de colmena"),
    "screen.noirm.colony.rank_top": ("The Living Hive: nothing above it.", "La Colmena Viva: no hay nada por encima."),
    "screen.noirm.colony.next": ("Next: %s, colony %s/%s", "Siguiente: %s, colonia %s/%s"),
    "screen.noirm.colony.deed.genin": ("Drain 300 chakra from opponents (%s so far)", "Drena 300 de chakra a rivales (%s hasta ahora)"),
    "screen.noirm.colony.deed.chunin": ("Chūnin rank, and win a fight with a female-insect mark", "Rango chūnin y ganar una pelea con una marca de insecto hembra"),
    "screen.noirm.colony.deed.jonin": ("Jōnin rank, and 3 adaptations at 10%+", "Rango jōnin y 3 adaptaciones al 10%+"),
    "screen.noirm.colony.deed.clan_master": ("Beat Shibi's trial", "Supera la prueba de Shibi"),
    "screen.noirm.colony.deed.living_hive": ("A full Insect Case, and a jinchūriki or legend drained to 0", "Una caja de insectos completa y un jinchūriki o leyenda drenado a 0"),
    "screen.noirm.colony.adaptation": ("Adaptation", "Adaptación"),
    "screen.noirm.colony.no_adaptation": ("None yet: the colony learns what kills it.", "Ninguna aún: la colonia aprende lo que la mata."),
    "screen.noirm.colony.strain.fire": ("Heat-hardened %s%%", "Endurecidos al calor %s%%"),
    "screen.noirm.colony.strain.explosion": ("Blast-hardened %s%%", "Endurecidos a explosiones %s%%"),
    "screen.noirm.colony.strain.lightning": ("Spark-proof %s%%", "A prueba de chispas %s%%"),
    "screen.noirm.colony.strain.cold": ("Frost-hardy %s%%", "Resistentes al frío %s%%"),
    "screen.noirm.colony.strain.poison": ("Venom-tough %s%%", "Duros al veneno %s%%"),
    "screen.noirm.colony.strain.wind": ("Wind-clinging %s%%", "Aferrados al viento %s%%"),
    "screen.noirm.colony.strains": ("Strains (case %s/%s)", "Cepas (caja %s/%s)"),
    "screen.noirm.colony.antivenom": ("Antivenom", "Antiveneno"),
    "screen.noirm.colony.hive_list": ("Hives", "Colmenas"),
    "screen.noirm.colony.marks": ("Marks", "Marcas"),
    "screen.noirm.case.count": ("%s / %s", "%s / %s"),
    "screen.noirm.case.unknown": ("Not yet caught", "Aún sin atrapar"),
    "screen.noirm.case.bred": ("Bred into your colony", "Criado en tu colonia"),
    "screen.noirm.case.entomologist": ("Entomologist", "Entomólogo"),
    "screen.noirm.jar.generation": ("Generation %s", "Generación %s"),
    "screen.noirm.jar.focus": ("Breed an adaptation (×2)", "Criar una adaptación (×2)"),
    "screen.noirm.jar.focus_tip": ("Each generation adapts to this twice as fast.", "Cada generación se adapta a esto el doble de rápido."),
    "screen.noirm.jar.cross": ("Cross", "Cruce"),
    "screen.noirm.jar.cross_on": ("Crossing with rinkaichū", "Cruzando con rinkaichū"),
    "screen.noirm.jar.cross_off": ("Cross kikaichū × rinkaichū", "Cruzar kikaichū × rinkaichū"),
    "screen.noirm.jar.cross_tip": ("%s generations of crossing breed the Antivenom strain: poison immunity (Shino's route).",
                                   "%s generaciones de cruce crían la cepa Antiveneno: inmunidad al veneno (el camino de Shino)."),
    "screen.noirm.jar.raise": ("Raise", "Criar"),
    "screen.noirm.jar.egg": ("Kidaichū Egg", "Huevo de kidaichū"),
    "screen.noirm.jar.egg_tip": ("Needs 10,000 kikaichū and CTL 30; one day.", "Necesita 10 000 kikaichū y CTL 30; un día."),
    "screen.noirm.jar.queen": ("Queen", "Reina"),
    "screen.noirm.jar.queen_tip": ("Clan Master; one day. She lets you summon the Giant Insect.", "Maestro del clan; un día. Te deja invocar al insecto gigante."),
    "screen.noirm.jar.task_egg": ("Egg: %s min left", "Huevo: quedan %s min"),
    "screen.noirm.jar.task_queen": ("Queen: %s min left", "Reina: quedan %s min"),
    "screen.noirm.jar.strains": ("Breed in a caught insect", "Criar un insecto atrapado"),
    "screen.noirm.jar.no_strains": ("Catch wild insects (Insect Case + Bug Net) to breed them in.", "Atrapa insectos salvajes (caja + red) para criarlos."),
    "creator.noirm.nest.line": ("The Nest: %s %s, %s", "El Nido: %s %s, %s"),
    "creator.noirm.nest.rare": ("1 in 12", "1 de 12"),
    "creator.noirm.nest.temper.0": ("calm", "tranquilos"),
    "creator.noirm.nest.temper.1": ("restless", "inquietos"),
}
for k, v in S.items():
    add(k, *v)
for h, en, es in [("fire", "Fire", "Fuego"), ("explosion", "Blast", "Explosión"), ("lightning", "Lightning", "Rayo"), ("cold", "Cold", "Frío"),
                  ("poison", "Poison", "Veneno"), ("wind", "Wind", "Viento")]:
    add(f"screen.noirm.jar.harm.{h}", en + " %s%%", es + " %s%%")

# ---------------------------------------------------------------- commands
C = {
    "status": ("Body %s/%s, jars %s, out %s, hives %s; Vigour %s, generation %s; %s", "Cuerpo %s/%s, tarros %s, fuera %s, colmenas %s; vigor %s, generación %s; %s"),
    "next": ("Next: %s at %s/%s; deed: %s %s", "Siguiente: %s con %s/%s; hazaña: %s %s"),
    "granted": ("You now host a colony of %s kikaichū.", "Ahora albergas una colonia de %s kikaichū."),
    "set": ("Body: %s %s", "Cuerpo: %s %s"),
    "rank": ("Hive rank: %s", "Rango de colmena: %s"),
    "vigour": ("Vigour: %s", "Vigor: %s"),
    "adapt": ("Resistance to %s: %s%%", "Resistencia a %s: %s%%"),
    "bug": ("A %s appears.", "Aparece un %s."),
    "generation": ("Generation %s", "Generación %s"),
}
for k, v in C.items():
    add("command.noirm.aburame." + k, *v)
H = {
    "status": ("your colony: counts, Vigour, rank, what the next rank needs", "tu colonia: números, vigor, rango, lo que pide el siguiente"),
    "recall": ("call every swarm home (the Swarm key's tap)", "llama a casa a todos los enjambres (el toque de la tecla Enjambre)"),
    "grant": ("give yourself a colony, whatever your clan", "date una colonia, sea cual sea tu clan"),
    "set": ("set how many insects of a species are in your body", "fija cuántos insectos de una especie hay en tu cuerpo"),
    "rank": ("set your hive rank (and learn its techniques)", "fija tu rango de colmena (y aprende sus técnicas)"),
    "vigour": ("set your colony's Vigour", "fija el vigor de tu colonia"),
    "adapt": ("set a bred resistance", "fija una resistencia criada"),
    "species": ("host a species", "alberga una especie"),
    "bug": ("a wild insect beside you", "un insecto salvaje a tu lado"),
    "strain": ("catch and breed in a wild insect's strain", "atrapa y cría la cepa de un insecto salvaje"),
    "generation": ("pass generations (adaptation, crosses)", "pasa generaciones (adaptación, cruces)"),
}
add("help.noirm.section.aburame", "Your insect colony (Aburame)", "Tu colonia de insectos (Aburame)")
for k, v in H.items():
    add("help.noirm.aburame." + k, *v)


add("master.noirm.shibi", "Shibi Aburame, Head of the Clan", "Shibi Aburame, cabeza del clan")
add("master.noirm.shibi.greeting",
    "Even a tiny insect has half a soul. Because of that, never underestimate an opponent, however small. Show me you can drain me before I drain you, and lose no more than half of what you send.",
    "Hasta un insecto diminuto tiene media alma. Por eso, nunca subestimes a un rival, por pequeño que sea. Demuéstrame que puedes drenarme antes de que yo te drene, sin perder más de la mitad de lo que envíes.")
add("master.noirm.shibi.test", "The test: insects only", "La prueba: solo insectos")
add("master.noirm.shibi.test.rules", "Drain Shibi to 0 chakra within %3$s s, keeping half your insects alive",
    "Drena a Shibi a 0 de chakra en %3$s s, manteniendo vivos la mitad de tus insectos")
add("master.noirm.shibi.test.safe", "Blades and jutsu don't touch him. Only your swarms' drain counts. If you're Drained, you fail.",
    "Las hojas y los jutsu no le afectan. Solo cuenta el drenaje de tus enjambres. Si te drenan, suspendes.")
add("master.noirm.shibi.spar", "Begin", "Empezar")
add("master.noirm.shibi.spar.tip", "Start the insect duel now", "Empieza ahora el duelo de insectos")
for k, en, es in [("trial_start", "%s releases his insects. Drain him first.", "%s libera sus insectos. Drénalo primero."),
                  ("trial_fail", "The trial is over.", "La prueba ha terminado."),
                  ("trial_drained", "%s's insects drained you first.", "Los insectos de %s te drenaron primero."),
                  ("trial_pass", "%s nods: \"Because you respected even the smallest.\" You are a Clan Master's equal.",
                   "%s asiente: \"Porque respetaste incluso a los más pequeños.\" Estás a la altura de un maestro del clan."),
                  ("trial_careless", "%s: \"You won, but you threw your insects away.\"", "%s: \"Ganaste, pero desperdiciaste tus insectos.\"")]:
    add("message.noirm.colony." + k, en, es)


add("message.noirm.colony.crew_scout", "%s sends a scout swarm ahead.", "%s envía un enjambre explorador por delante.")
add("message.noirm.colony.crew_draw", "%s's insects draw the poison out of you.", "Los insectos de %s te sacan el veneno.")


add("command.noirm.aburame.incoming", "A %s jutsu flies at you.", "Un jutsu de %s vuela hacia ti.")
add("help.noirm.aburame.incoming", "a jutsu projectile flies at you (try a wall, a dome or Devour)", "un jutsu vuela hacia ti (prueba un muro, una cúpula o Devorar)")


def main():
    for code, idx in (("en_us", 0), ("es_es", 1), ("es_mx", 1)):
        p = os.path.join(LANG, code + ".json")
        with open(p, encoding="utf-8") as f:
            data = json.load(f, object_pairs_hook=OrderedDict)
        for k, v in T.items():
            data[k] = v[idx]
        with open(p, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("lang:", len(T), "keys in 3 files")


if __name__ == "__main__":
    main()
