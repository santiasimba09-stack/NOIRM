noirm creator skip
time set day
weather clear
gamerule doDaylightCycle false
gamerule doMobSpawning false
effect give @s minecraft:resistance 99999 2 true
effect give @s minecraft:saturation 99999 0 true
fill ~-12 199 ~-12 ~12 201 ~16 minecraft:glass
fill ~-11 200 ~-11 ~11 201 ~15 minecraft:air
fill ~-11 199 ~-11 ~11 199 ~15 minecraft:smooth_stone
noirm clan set uchiha
noirm sharingan 3
noirm jutsu learn all
noirm jutsu equip 1 noirm:katon/great_fireball
noirm jutsu equip 2 noirm:universal/shadow_clone
noirm jutsu equip 3 noirm:universal/rasengan
summon noirm:training_dummy ~ 200 ~2.5 {Rotation:[180f,0f]}
fill ~-3 200 ~5 ~3 202 ~5 minecraft:stone_bricks
summon noirm:sparring_ninja ~ 200 ~7 {Rotation:[180f,0f],Clan:"uchiha",NoAI:1b}
summon noirm:sparring_ninja ~3 200 ~4 {Rotation:[180f,0f],Clan:"hyuga",NoAI:1b}
give @s noirm:training_dummy 4
give @s noirm:sealing_scroll 2
tp @s ~ 200 ~ 0 0
tag @s add smoke_setup
