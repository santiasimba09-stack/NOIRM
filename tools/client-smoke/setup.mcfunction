time set day
weather clear
gamerule doDaylightCycle false
gamerule doMobSpawning false
effect give @s minecraft:resistance 99999 2 true
effect give @s minecraft:saturation 99999 0 true
fill ~-12 199 ~-12 ~12 201 ~12 minecraft:glass
fill ~-11 200 ~-11 ~11 201 ~11 minecraft:air
fill ~-11 199 ~-11 ~11 199 ~11 minecraft:smooth_stone
tp @s ~ 200 ~ 0 0
noirm clan set uchiha
noirm sharingan 3
noirm biju seal 9
summon noirm:sparring_ninja ~ 200 ~6 {Rotation:[180f,0f],Clan:"hyuga",NoAI:1b}
tag @s add smoke_setup
