package com.noirm.gametest;

import com.noirm.Noirm;
import com.noirm.network.SyncShinobiDataPacket;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests for chakra and stamina ({@link ShinobiData}) and how players receive it. */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShinobiGameTests {
    private static final float EPSILON = 0.001f;

    private ShinobiGameTests() {}

    @GameTest(template = "empty")
    public static void playersGetFullShinobiData(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        helper.assertTrue(data != null, "Players should have shinobi data");
        assertNear(helper, ShinobiData.DEFAULT_MAX_CHAKRA, data.getChakra(), "starting chakra");
        assertNear(helper, ShinobiData.DEFAULT_MAX_STAMINA, data.getStamina(), "starting stamina");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dataSurvivesSaveAndLoad(GameTestHelper helper) {
        ShinobiData original = new ShinobiData();
        original.setMaxChakra(250f);
        original.setChakra(123.5f);
        original.setMaxStamina(80f);
        original.setStamina(40f);

        ShinobiData loaded = new ShinobiData();
        loaded.load(original.save());

        assertNear(helper, 250f, loaded.getMaxChakra(), "max chakra");
        assertNear(helper, 123.5f, loaded.getChakra(), "chakra");
        assertNear(helper, 80f, loaded.getMaxStamina(), "max stamina");
        assertNear(helper, 40f, loaded.getStamina(), "stamina");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cannotSpendMoreThanYouHave(GameTestHelper helper) {
        ShinobiData data = new ShinobiData();
        data.setChakra(10f);
        helper.assertFalse(data.tryConsumeChakra(20f), "Spending 20 of 10 chakra should fail");
        assertNear(helper, 10f, data.getChakra(), "chakra after failed spend");
        helper.assertTrue(data.tryConsumeChakra(4f), "Spending 4 of 10 chakra should work");
        assertNear(helper, 6f, data.getChakra(), "chakra after spend");
        helper.assertFalse(data.tryConsumeStamina(-5f), "Negative costs should be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chakraRegeneratesOverTime(GameTestHelper helper) {
        ShinobiData data = new ShinobiData();
        data.setChakra(0f);
        tick(data, 20);
        assertNear(helper, ShinobiData.CHAKRA_REGEN_PER_TICK * 20, data.getChakra(), "chakra after 1 second");
        data.setChakra(data.getMaxChakra() - 0.01f);
        tick(data, 5);
        assertNear(helper, data.getMaxChakra(), data.getChakra(), "chakra should stop at max");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staminaWaitsBeforeRegenerating(GameTestHelper helper) {
        ShinobiData data = new ShinobiData();
        data.tryConsumeStamina(50f);
        tick(data, ShinobiData.STAMINA_REGEN_DELAY_TICKS);
        assertNear(helper, 50f, data.getStamina(), "stamina during the regen delay");
        tick(data, 10);
        assertNear(helper, 50f + ShinobiData.STAMINA_REGEN_PER_TICK * 10, data.getStamina(), "stamina after the delay");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loweringMaxClampsCurrent(GameTestHelper helper) {
        ShinobiData data = new ShinobiData();
        data.setMaxChakra(40f);
        assertNear(helper, 40f, data.getChakra(), "chakra clamped to new max");
        data.setMaxStamina(0f);
        assertNear(helper, 1f, data.getMaxStamina(), "max stamina has a floor of 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void syncPacketKeepsItsValues(GameTestHelper helper) {
        SyncShinobiDataPacket sent = new SyncShinobiDataPacket(12.5f, 150f, 33f, 90f, 14f, 0.25f, 11f, 0.5f, 12f, 0.1f, 15f, 0.75f);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        SyncShinobiDataPacket received = SyncShinobiDataPacket.decode(buf);
        helper.assertTrue(sent.equals(received), "Packet changed in transit: " + sent + " -> " + received);
        helper.assertTrue(buf.readableBytes() == 0, "Decoder should read every byte the encoder wrote");
        helper.succeed();
    }

    private static void tick(ShinobiData data, int ticks) {
        for (int i = 0; i < ticks; i++) {
            data.tick();
        }
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < EPSILON, what + ": expected " + expected + ", got " + actual);
    }
}
