package com.noirm.mixin;

import com.noirm.gametest.TestFilter;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * The quick test runner: when {@code -Dnoirm.tests=...} is set, the list of game tests the test server runs is
 * cut down to the matching ones ({@link TestFilter}). Without the property nothing changes. Only used in
 * development (game tests never run in the real game), so the injection is optional ({@code require = 0}).
 */
@Mixin(GameTestRegistry.class)
public abstract class GameTestRegistryMixin {
    @Inject(method = {"getAllTestFunctions()Ljava/util/Collection;", "m_127658_()Ljava/util/Collection;"},
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void noirm$filterTests(CallbackInfoReturnable<Collection<TestFunction>> info) {
        if (TestFilter.active()) {
            info.setReturnValue(TestFilter.apply(info.getReturnValue()));
        }
    }
}
