package com.noirm.entity;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/**
 * Two villagers who meet stop for a chat: they turn to face each other and take turns saying
 * something (a speech bubble over their head, {@link VillageMerchant#say}), then go on their way.
 * Only by day, never while trading, and a villager chats at most every half minute or so.
 */
public class VillagerChatGoal extends Goal {
    /** How close another villager must be to start a chat. */
    private static final double REACH = 4.0;
    private final VillageMerchant self;
    private int ticks;

    public VillagerChatGoal(VillageMerchant self) {
        this.self = self;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (self.chatPartner() != null) {
            return true; // someone stopped to talk to us
        }
        if (self.isTrading() || self.getRandom().nextInt(300) != 0 || !daytime() || self.chatCooldown() > 0) {
            return false;
        }
        List<VillageMerchant> near = self.level().getEntitiesOfClass(VillageMerchant.class, self.getBoundingBox().inflate(REACH),
                other -> other != self && other.chatPartner() == null && !other.isTrading() && other.chatCooldown() == 0);
        if (near.isEmpty()) {
            return false;
        }
        VillageMerchant partner = near.get(self.getRandom().nextInt(near.size()));
        int length = 100 + self.getRandom().nextInt(80);
        self.startChat(partner, length, true);
        partner.startChat(self, length, false);
        return true;
    }

    private boolean daytime() {
        long time = self.level().getDayTime() % 24000;
        return time < 12000 && !self.level().isRaining();
    }

    @Override
    public void start() {
        ticks = 0;
        self.getNavigation().stop();
    }

    @Override
    public boolean canContinueToUse() {
        VillageMerchant partner = self.chatPartner();
        return partner != null && partner.isAlive() && self.chatTicks() > 0 && !self.isTrading()
                && self.distanceToSqr(partner) < 36 && partner.chatPartner() == self;
    }

    @Override
    public void tick() {
        VillageMerchant partner = self.chatPartner();
        if (partner == null) {
            return;
        }
        self.getLookControl().setLookAt(partner, 30f, 30f);
        ticks++;
        // Turns: the one who started speaks first, then they alternate every two seconds or so.
        if (ticks % 45 == 5 && (ticks / 45 % 2 == 0) == self.chatStarter()) {
            self.sayLine(40);
        }
    }

    @Override
    public void stop() {
        self.endChat();
    }
}
