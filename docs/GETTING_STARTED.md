# Getting started (no experience needed)

This is how to run NOIRM on your own computer.

## 1. Install the tools (one time)

1. **Java 17 (JDK).** Download "Temurin 17 (LTS)" from https://adoptium.net and install it.
2. **IntelliJ IDEA Community Edition** (free) from https://www.jetbrains.com/idea/. This is the
   editor where the code lives.
3. **Git**, to download and update the project: https://git-scm.com/downloads.

## 2. Get the project

```
git clone https://github.com/santiasimba09-stack/NOIRM.git
```

In IntelliJ: **File → Open** → choose the `NOIRM` folder → **Trust project**. The first time,
IntelliJ downloads Minecraft and Forge and sets everything up. That takes about 5–15 minutes,
and you can watch progress in the bottom bar.

## 3. Play your mod

Open the **Gradle** panel on the right side of IntelliJ, then go to
**Tasks → forgegradle runs** and double-click **runClient**. A Minecraft window opens with NOIRM
installed. You'll find it under *Mods* on the title screen.

You can also run it from a terminal in the project folder:

```
./gradlew runClient        # macOS / Linux
gradlew.bat runClient      # Windows
```

## Useful commands

| Command | What it does |
|---|---|
| `gradlew runClient` | Starts Minecraft with the mod |
| `gradlew runGameTestServer` | Runs NOIRM's automated in-game tests, then exits |
| `gradlew build` | Builds the mod file into `build/libs/noirm-<version>.jar` |

To play the built jar in a normal Minecraft install, use Forge 1.20.1 (47.4.10 or newer) and
put the jar in your `mods` folder.

## Where things are

```
src/main/java/com/noirm/        the mod's code
src/main/resources/             assets (models, textures, lang) and data (recipes, worldgen…)
docs/                           roadmap and guides
reference/nydo/                 NYDO's assets, for inspiration only (not part of our mod)
```
