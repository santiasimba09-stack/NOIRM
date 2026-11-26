---
name: add-command
description: Add or change a NOIRM chat command (/noirm ...), including the command guide entry, lang texts in three languages, the generated docs/COMMANDS.md and the game test that parses every example. Use whenever a command is added, renamed or removed.
---

# Add a command

Every command lives under `/noirm`. Each `command/*Command.java` adds its own branch in a `RegisterCommandsEvent`
handler (`event.getDispatcher().register(Commands.literal("noirm").then(...))`; Brigadier merges the branches).

## Checklist
1. **Code:** add the branch to the matching class in `src/main/java/com/noirm/command/` (`HoundCommand`,
   `SpawnCommand`, `WorldCommand`, `PowersCommand`...). Shared helpers are in `Cmd`. Cheat-only commands:
   `.requires(s -> s.hasPermission(2))` (the classes use a `CHEAT_PERMISSION` constant). Feedback goes through
   translatable text (`Component.translatable("command.noirm.<...>", args)`), not hard-coded English.
2. **Guide:** add an entry in `CommandGuide`'s static block under the right `section("...")`:
   `.add("key", "/noirm thing <arg>", "/noirm thing 3", cheats)`. The **example must be a real, runnable command**:
   `CommandGameTests.everyHelpExampleParses` parses every example.
3. **Lang (all three files at once):**
   ```
   python3 tools/dev/lang.py add help.noirm.<section>.<key> "What it does." "Qué hace."
   python3 tools/dev/lang.py add command.noirm.<feedback_key> "Done: %s" "Hecho: %s"
   ```
   A new section also needs `help.noirm.section.<section>`.
4. **Docs:** `python3 tools/docs/gen_commands.py` rewrites `docs/COMMANDS.md`. Commit it.
5. **Test:** `tools/dev/test.sh commands` (the parse test plus `CommandGameTests`), and a behaviour test of your own
   if the command changes state.

## Reloading data
Don't add a command just to reload data: vanilla `/reload` already reloads every NOIRM data folder (every loader is a
`CodecJsonLoader` registered in `AddReloadListenerEvent`) and re-sends the jutsu list to clients
(`PowerEvents.datapackSync`). The config file reloads by itself when saved, and `/noirm config` sets options live.
Only blade stats, hair styles, drapes and ninja-look tables need a restart (read from the jar at start-up). See
`docs/TUNING.md`. A new data folder: use `CodecJsonLoader` in an `AddReloadListenerEvent` listener so `/reload` covers it,
and add a row to `docs/TUNING.md` if players may want to tune it.
