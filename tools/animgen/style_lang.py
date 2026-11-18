#!/usr/bin/env python3
"""Adds the styles wave's text (style names and one-line descriptions, the picker, the style scroll,
messages) to the three lang files. Run from the repo root; existing keys are overwritten, others kept.

    python3 tools/animgen/style_lang.py
"""
import json
from collections import OrderedDict

LANG = "src/main/resources/assets/noirm/lang/%s.json"

# key: (en, es) — es_es and es_mx share the Spanish text.
T = {
    # ---- styles: name, then how it plays
    "style.noirm.drunken_fist": ("Drunken Fist", "Puño Borracho"),
    "style.noirm.drunken_fist.desc": ("Swaying and loose. Wind-ups never land on the same beat, so they're hard to read; a forgiving parry.",
                                      "Suelto y tambaleante. Sus amagos nunca caen al mismo ritmo y cuesta leerlos; un bloqueo perfecto generoso."),
    "style.noirm.iron_body": ("Iron Body", "Cuerpo de Hierro"),
    "style.noirm.iron_body.desc": ("Rooted and armoured. The guard keeps out almost everything and shrugs off posture, but you plod; heavy blows push through hits.",
                                   "Plantado y blindado. La guardia lo para casi todo y aguanta la postura, pero eres lento; golpes pesados que no se detienen."),
    "style.noirm.viper": ("Viper", "Víbora"),
    "style.noirm.viper.desc": ("Low and fast. Finger jabs at pressure points knock the breath out: every hit drains stamina. Slides to dodge.",
                               "Bajo y rápido. Golpes de dedos a los puntos vitales que cortan el aliento: cada golpe roba resistencia. Esquiva deslizándose."),
    "style.noirm.crane": ("Crane", "Grulla"),
    "style.noirm.crane.desc": ("High on one leg. Long kicks, a light guard with a wider parry, a hop for a dodge, and +35% damage from the air.",
                               "Alto, sobre una pierna. Patadas largas, guardia ligera con un bloqueo perfecto amplio, un salto para esquivar y +35% de daño en el aire."),
    "style.noirm.mountain": ("Mountain", "Montaña"),
    "style.noirm.mountain.desc": ("A deep, rooted stance. Slow on your feet, but the guard is a counter stance: a huge parry window that answers at once.",
                                  "Una postura honda y enraizada. Lento al moverte, pero la guardia es de contraataque: un bloqueo perfecto enorme que responde al instante."),
    "style.noirm.wind_step": ("Wind Step", "Paso del Viento"),
    "style.noirm.wind_step.desc": ("Bouncing and quick. You never block: guard is a sway out of the way. Light strikes that spring back out of reach.",
                                   "Saltarín y veloz. Nunca bloqueas: la guardia es un vaivén fuera de alcance. Golpes ligeros que rebotan lejos."),
    "style.noirm.shadow": ("Shadow", "Sombra"),
    "style.noirm.shadow.desc": ("Low, a hand hidden. Feint with guard during your wind-up, then strike through their guard; +60% from behind.",
                                "Bajo, con una mano oculta. Finta con la guardia durante tu amago y golpea a través de su guardia; +60% por la espalda."),
    "style.noirm.leaf_hurricane": ("Leaf Hurricane", "Huracán de la Hoja"),
    "style.noirm.leaf_hurricane.desc": ("Strong Fist's whirling cousin. Spinning kicks that build momentum with every hit of a string; flips to dodge.",
                                        "El primo giratorio del Puño Fuerte. Patadas en remolino que ganan impulso con cada golpe de la cadena; esquiva con volteretas."),
    "style.noirm.sumo": ("Sumo", "Sumo"),
    "style.noirm.sumo.desc": ("Wide and heavy. Open-hand thrusts that shove, a belt grab into lifts and throws, an armoured guard, charges that keep going.",
                              "Ancho y pesado. Empujones a mano abierta, agarre del cinturón para levantar y lanzar, guardia blindada y cargas imparables."),
    "style.noirm.monkey": ("Monkey", "Mono"),
    "style.noirm.monkey.desc": ("Crouched and twitchy. Swipes, low sweeps, cartwheels and backflip kicks; flips to dodge; the spinning sweep goes under a guard.",
                                "Agachado e inquieto. Zarpazos, barridos bajos, ruedas y patadas en voltereta; esquiva volteando; el barrido giratorio pasa bajo la guardia."),
    "style.noirm.blade_iai": ("Iai", "Iai"),
    "style.noirm.blade_iai.desc": ("Katana and sword: crouched, blade low. A counter stance with a wide parry answered by a drawing cut; draws slip through guards.",
                                   "Katana y espada: agachado, la hoja baja. Guardia de contraataque con un bloqueo perfecto amplio que responde con un corte al desenvainar."),
    "style.noirm.blade_dance": ("Blade Dance", "Danza de la Hoja"),
    "style.noirm.blade_dance.desc": ("Sword, tantō and kunai: light and flowing. Spinning cuts that build momentum, flips to dodge, deeper cuts from behind.",
                                     "Espada, tantō y kunai: ligera y fluida. Cortes giratorios que ganan impulso, volteretas para esquivar, cortes más hondos por la espalda."),
    "style.noirm.staff_wall": ("Bō Wall", "Muralla de Bō"),
    "style.noirm.staff_wall.desc": ("Staff: square and high. A guard like a wall, long thrusts and sweeps that push people away, an overhead crash.",
                                    "Bastón: de frente y alto. Una guardia como una muralla, estocadas largas y barridos que apartan, un golpe desde arriba."),
    "style.noirm.spear_dance": ("Spear Dance", "Danza de la Lanza"),
    "style.noirm.spear_dance.desc": ("Yari: bouncing, spear low and long. Hit and run: thrusts that spring back out of reach, vaults and hops.",
                                     "Yari: saltarín, la lanza baja y larga. Golpea y huye: estocadas que rebotan fuera de alcance, saltos con pértiga."),
    "style.noirm.basic.desc": ("The Academy's all-round style: jabs, kicks and a solid guard.", "El estilo completo de la Academia: golpes, patadas y una guardia sólida."),
    "style.noirm.brawler.desc": ("Elbows, knees and headbutts at close range; grabs into the clinch.", "Codos, rodillas y cabezazos de cerca; agarres al cuerpo a cuerpo."),
    "style.noirm.strong_fist.desc": ("Fast, powerful kicks and the Leaf techniques. Needs taijutsu 18.", "Patadas rápidas y potentes y las técnicas de la Hoja. Requiere taijutsu 18."),
    "style.noirm.gentle_fist.desc": ("Palms at the chakra network: they slip through guards and drain chakra. Needs the Byakugan.",
                                     "Palmas a la red de chakra: atraviesan guardias y roban chakra. Requiere el Byakugan."),
    "style.noirm.weapon_tanto": ("Tantō", "Tantō"),
    "style.noirm.weapon_katana": ("Katana", "Katana"),
    "style.noirm.weapon_greatsword": ("Nodachi", "Nodachi"),
    "style.noirm.weapon_tonfa": ("Tonfa", "Tonfa"),
    "style.noirm.weapon_spear": ("Yari", "Yari"),
    "style.noirm.weapon_claws": ("Tekko-kagi", "Tekko-kagi"),
    "style.noirm.weapon_fan": ("Tessen", "Tessen"),

    # ---- identity labels (the picker)
    "style.noirm.footwork.upright": ("upright", "erguido"), "style.noirm.footwork.low": ("low", "bajo"),
    "style.noirm.footwork.wide": ("wide", "abierto"), "style.noirm.footwork.bladed": ("bladed", "de perfil"),
    "style.noirm.footwork.bouncing": ("bouncing", "saltarín"), "style.noirm.footwork.rooted": ("rooted", "enraizado"),
    "style.noirm.footwork.swaying": ("swaying", "tambaleante"),
    "style.noirm.rhythm.steady": ("steady", "constante"), "style.noirm.rhythm.fast": ("fast jabs", "golpes rápidos"),
    "style.noirm.rhythm.heavy": ("heavy blows", "golpes pesados"), "style.noirm.rhythm.kicks": ("kicks", "patadas"),
    "style.noirm.rhythm.grapple": ("grapples", "agarres"), "style.noirm.rhythm.irregular": ("irregular", "irregular"),
    "style.noirm.rhythm.counter": ("counters", "contraataques"), "style.noirm.rhythm.hit_and_run": ("hit and run", "golpea y huye"),
    "style.noirm.rhythm.deceptive": ("feints", "fintas"), "style.noirm.rhythm.spinning": ("spinning kicks", "patadas giratorias"),
    "style.noirm.rhythm.acrobatic": ("acrobatic", "acrobático"), "style.noirm.rhythm.quick_draw": ("quick draw", "desenvaine rápido"),
    "style.noirm.rhythm.flowing": ("flowing", "fluido"), "style.noirm.rhythm.defensive": ("defensive", "defensivo"),
    "style.noirm.guard.standard": ("standard guard", "guardia normal"), "style.noirm.guard.high": ("high guard", "guardia alta"),
    "style.noirm.guard.parry": ("parry guard", "guardia de bloqueo perfecto"), "style.noirm.guard.evasive": ("no block: sways", "sin bloqueo: vaivén"),
    "style.noirm.guard.armoured": ("armoured guard", "guardia blindada"), "style.noirm.guard.counter": ("counter stance", "guardia de contraataque"),
    "style.noirm.dodge.roll": ("rolls", "rueda"), "style.noirm.dodge.step": ("steps", "pasos"), "style.noirm.dodge.hop": ("hops", "saltos"),
    "style.noirm.dodge.slide": ("slides", "deslizamientos"), "style.noirm.dodge.flip": ("flips", "volteretas"), "style.noirm.dodge.sway": ("sways", "vaivén"),
    "style.noirm.learned.master": ("taught by a master", "enseñado por un maestro"),
    "style.noirm.learned.scroll": ("learned from a scroll", "aprendido de un pergamino"),
    "style.noirm.learned.watch": ("picked up by watching", "aprendido observando"),
    "style.noirm.learned.clan": ("your clan's style", "el estilo de tu clan"),
    "style.noirm.learned.command": ("learned", "aprendido"),

    # ---- messages, item, commands, key
    "message.noirm.style.learned.master": ("Your master taught you a new fighting style: %s. Press I to choose styles.",
                                           "Tu maestro te enseñó un nuevo estilo de combate: %s. Pulsa I para elegir estilos."),
    "message.noirm.style.learned.scroll": ("The scroll teaches you a new fighting style: %s. Press I to choose styles.",
                                           "El pergamino te enseña un nuevo estilo de combate: %s. Pulsa I para elegir estilos."),
    "message.noirm.style.learned.watch": ("Watching that fight, something clicks: you picked up %s. Press I to choose styles.",
                                          "Viendo ese combate, algo encaja: has aprendido %s. Pulsa I para elegir estilos."),
    "message.noirm.style.learned.clan": ("Your clan passes its fighting style on to you: %s. Press I to choose styles.",
                                         "Tu clan te transmite su estilo de combate: %s. Pulsa I para elegir estilos."),
    "message.noirm.style.learned.command": ("You learned a fighting style: %s.", "Has aprendido un estilo de combate: %s."),
    "message.noirm.style.scroll_nothing": ("You already know every style a scroll could teach.", "Ya conoces todos los estilos que un pergamino podría enseñar."),
    "message.noirm.style.scroll_known": ("You already know %s.", "Ya conoces %s."),
    "item.noirm.style_scroll": ("Style Scroll", "Pergamino de Estilo"),
    "item.noirm.style_scroll.tip": ("A fighting style you don't know yet. Use it to read it.", "Un estilo de combate que aún no conoces. Úsalo para leerlo."),
    "item.noirm.style_scroll.tip_named": ("Teaches %s", "Enseña %s"),
    "command.noirm.style.needs.learn": ("(must be learned: masters, scrolls, watching)", "(hay que aprenderlo: maestros, pergaminos, observando)"),
    "command.noirm.style.learned": ("Learned %s new style(s).", "Has aprendido %s estilo(s) nuevo(s)."),
    "help.noirm.combat.style_learn": ("learn a fighting style (or all of them) as if a master taught it",
                                      "aprende un estilo de combate (o todos) como si te lo enseñara un maestro"),
    "key.noirm.style_picker": ("Fighting styles (picker)", "Estilos de combate (selector)"),

    # ---- the picker screen
    "screen.noirm.styles.title": ("Fighting styles", "Estilos de combate"),
    "screen.noirm.styles.known": ("%s of %s known", "%s de %s conocidos"),
    "screen.noirm.styles.tab.unarmed": ("Unarmed", "Sin armas"),
    "screen.noirm.styles.tab.weapons": ("Weapons", "Armas"),
    "screen.noirm.styles.active": ("in use", "en uso"),
    "screen.noirm.styles.use": ("Use", "Usar"),
    "screen.noirm.styles.own_moves": ("Own moves", "Movimientos propios"),
    "screen.noirm.styles.weapon_own": ("%s: its own moves", "%s: sus propios movimientos"),
    "screen.noirm.styles.for": ("for: %s", "para: %s"),
    "screen.noirm.styles.locked.learn": ("Not learned yet: masters, style scrolls in chests, or watching someone who fights this way.",
                                         "Aún no aprendido: maestros, pergaminos de estilo en cofres u observar a quien lucha así."),
    "screen.noirm.styles.locked.taijutsu": ("Needs taijutsu %s.", "Requiere taijutsu %s."),
    "screen.noirm.styles.locked.byakugan": ("Needs the Byakugan.", "Requiere el Byakugan."),
    "screen.noirm.styles.speed": ("speed ×%s", "velocidad ×%s"),
    "screen.noirm.styles.parry": ("parry ×%s", "bloqueo perfecto ×%s"),
    "screen.noirm.styles.hint": ("Click a style to use it. H cycles your unarmed styles.", "Haz clic en un estilo para usarlo. H cambia entre tus estilos sin armas."),
    "screen.noirm.styles.feint": ("feints", "fintas"),
    "screen.noirm.styles.backstab": ("backstab ×%s", "por la espalda ×%s"),
    "screen.noirm.styles.aerial": ("aerial ×%s", "aéreo ×%s"),
    "screen.noirm.styles.drain": ("drains stamina", "roba resistencia"),
    "screen.noirm.styles.momentum": ("momentum", "impulso"),
    "screen.noirm.styles.jitter": ("unreadable timing", "ritmo imprevisible"),
}


def main():
    for lang in ("en_us", "es_es", "es_mx"):
        path = LANG % lang
        data = json.load(open(path, encoding="utf-8"), object_pairs_hook=OrderedDict)
        for key, (en, es) in T.items():
            data[key] = en if lang == "en_us" else es
        with open(path, "w", encoding="utf-8") as f:
            f.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    print("wrote %d keys into 3 lang files" % len(T))


if __name__ == "__main__":
    main()
