---
name: add-entity
description: Add a new entity type to NOIRM (a mob, NPC, projectile or effect entity) with its registration, attributes, renderer, lang name, optional spawn egg and the checks that catch the usual crashes. Use whenever a new EntityType is needed.
---

# Add an entity

## Checklist
1. **Class:** `src/main/java/com/noirm/<feature>/<Name>.java` (server side; no `net.minecraft.client` imports).
   A living one gets `public static AttributeSupplier.Builder createAttributes()`.
2. **Register the type** in the feature's registry (`<feature>/<Feature>Registry`, e.g. `aburame/AburameRegistry`,
   `hound/HoundRegistry`) or `registry/NoirmEntities`:
   ```java
   public static final RegistryObject<EntityType<Name>> NAME = ENTITIES.register("name",
           () -> EntityType.Builder.<Name>of(Name::new, MobCategory.MISC).sized(0.6f, 1.8f)
                   .clientTrackingRange(10).build(Noirm.id("name").toString()));
   ```
   Add `.noSave()` for temporary entities (effects, clones).
3. **Attributes** (living entities only): `event.put(NAME.get(), Name.createAttributes().build())` in that registry's
   `EntityAttributeCreationEvent` listener. Missing attributes crash when it spawns.
4. **Renderer (required, even for invisible entities):** in `client/NoirmRenderers.registerRenderers`:
   `event.registerEntityRenderer(Registry.NAME.get(), MyRenderer::new);` or `NoopRenderer::new` for particle-only
   entities. Without it **the client crashes when it spawns**, and game tests can't catch this.
   - Renderer classes live in `com.noirm.client` (or `client/<feature>/`).
   - For a model: prefer a Blockbench model (skill `blockbench-model`): `BbModels.get(Noirm.id("name"))`, a fallback
     if it's null, `stack.mulPose(Axis.YP.rotationDegrees(180 - yaw))`, then `model.render(...)`.
5. **Lang:** `python3 tools/dev/lang.py add entity.noirm.name "English name" "Nombre"`.
6. **Spawn egg (optional):** `ForgeSpawnEggItem` in `registry/NoirmItems` (see `SPARRING_NINJA_SPAWN_EGG`), its item
   model `assets/noirm/models/item/<name>_spawn_egg.json` (`"parent": "minecraft:item/template_spawn_egg"`), the
   creative tab in `NoirmItems.creativeTabs`, and `item.noirm.<name>_spawn_egg` in the lang files.
7. **Summon command (optional):** `/noirm spawn ...` lives in `command/SpawnCommand` (skill `add-command`).
8. **Game test:** spawn it in a test of its own batch, check it ticks and does its job, then `discard()` it
   (skill `game-tests`).
9. **Client check (required):** `client-shot` skill: summon it in the smoke client and grep the log for exceptions.
10. Entity data that syncs (`SynchedEntityData`) doesn't change the protocol version; new packets do (`tools/dev/protocol.py bump`).

## Gotchas
- Forge names generated event-handler classes by simple class name + method + event: two subscriber classes with the
  same simple name and method in one package crash the client at launch. Give subscriber classes distinct names.
- Sizes are in blocks; `clientTrackingRange` is in chunks.
- Remove test entities that leave the 3x3 test area, or later tests break.
