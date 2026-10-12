time set day
weather clear
gamerule doDaylightCycle false
gamerule doMobSpawning false
effect give @s minecraft:resistance 99999 2 true
effect give @s minecraft:saturation 99999 0 true
fill ~-16 199 ~-12 ~16 201 ~24 minecraft:glass
fill ~-15 200 ~-11 ~15 201 ~23 minecraft:air
fill ~-15 199 ~-11 ~15 199 ~23 minecraft:smooth_stone
noirm clan set hyuga
summon noirm:training_dummy ~ 200 ~2.5 {Rotation:[180f,0f]}
fill ~-3 200 ~5 ~3 202 ~5 minecraft:stone_bricks
summon noirm:sparring_ninja ~ 200 ~7 {Rotation:[180f,0f],Clan:"uchiha",NoAI:1b}
summon noirm:sparring_ninja ~3 200 ~4 {Rotation:[180f,0f],Clan:"hyuga",NoAI:1b}
summon noirm:tailed_beast ~-9 200 ~14 {Tails:5,NoAI:1b,Rotation:[180f,0f]}
summon noirm:tailed_beast ~8 200 ~14 {Tails:9,Subdued:1b,SubduedTicks:24000}
give @s noirm:training_dummy 4
give @s noirm:sealing_scroll 2
tp @s ~-3 200 ~ facing ~ 201 ~7
tag @s add smoke_setup
