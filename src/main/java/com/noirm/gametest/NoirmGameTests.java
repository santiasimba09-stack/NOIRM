package com.noirm.gametest;

import com.noirm.Noirm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-game automated tests. Run them all with {@code ./gradlew runGameTestServer}: it starts a
 * headless server, runs every test here, and exits with an error if any fails.
 *
 * <p>Tests use the shared empty 3x3x3 arena {@code data/noirm/structures/empty.nbt}
 * ({@code template = "empty"}) unless they need a purpose-built structure.
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NoirmGameTests {
    private NoirmGameTests() {}

    @GameTest(template = "empty")
    public static void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(Noirm.MOD_ID), "NOIRM should be loaded");
        helper.succeed();
    }
}
