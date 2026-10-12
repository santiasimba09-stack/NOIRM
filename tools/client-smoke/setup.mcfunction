time set day
weather clear
gamerule doDaylightCycle false
gamerule doMobSpawning false
effect give @s minecraft:resistance 99999 2 true
effect give @s minecraft:saturation 99999 0 true
fill ~-16 199 ~-12 ~16 201 ~24 minecraft:glass
fill ~-15 200 ~-11 ~15 201 ~23 minecraft:air
fill ~-15 199 ~-11 ~15 199 ~23 minecraft:smooth_stone
noirm clan set senju
noirm jutsu mastery noirm:doton/earth_wall 2
noirm jutsu mastery noirm:suiton/water_bullet 2
noirm sharingan 3
summon noirm:training_dummy ~ 200 ~2.5 {Rotation:[180f,0f]}
fill ~-3 200 ~5 ~3 202 ~5 minecraft:stone_bricks
summon noirm:sparring_ninja ~ 200 ~7 {Rotation:[180f,0f],Clan:"uchiha",NoAI:1b}
summon noirm:sparring_ninja ~3 200 ~4 {Rotation:[180f,0f],Clan:"hyuga",NoAI:1b}
give @s noirm:training_dummy 4
give @s noirm:sealing_scroll 2
tp @s ~ 200 ~ 0 0
tag @s add smoke_setup
