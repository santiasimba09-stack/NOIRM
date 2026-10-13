package com.noirm.entity;

import com.noirm.registry.NoirmItems;
import com.noirm.world.village.VillageResidents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * A shopkeeper who lives in a ninja village (the ramen stand, the weapon shop…). Right-click to
 * trade with emeralds, like a vanilla villager: kunai, ramen, training dummies and sealing scrolls
 * for sale, and raw materials bought back. Merchants don't fight; when someone hurts one, the
 * village's ninjas come running (see {@link VillageResidents#raiseAlarm}).
 */
public class VillageMerchant extends AbstractVillager {
    public VillageMerchant(EntityType<? extends VillageMerchant> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 0.6));
        goalSelector.addGoal(2, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.45));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** Keeps the merchant near their stall: they stroll, but never far from {@code home}. */
    public void setHome(BlockPos home, int radius) {
        restrictTo(home, radius);
        setPersistenceRequired();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isTrading() || isBaby()) {
            return super.mobInteract(player, hand);
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.awardStat(Stats.TALKED_TO_VILLAGER);
        }
        if (getOffers().isEmpty()) {
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!level().isClientSide()) {
            setTradingPlayer(player);
            openTradingScreen(player, getDisplayName(), 1);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /**
     * The shop's stock. It's always the same list, so players can count on it; each offer restocks
     * when the merchant's offers are rebuilt (their stock is generous instead).
     */
    @Override
    protected void updateTrades() {
        MerchantOffers offers = getOffers();
        // What they sell.
        offers.add(sell(2, named(new ItemStack(Items.IRON_SWORD), "item.noirm.kunai"), 8));
        offers.add(sell(1, new ItemStack(NoirmItems.RAMEN.get(), 2), 16));
        offers.add(sell(4, new ItemStack(NoirmItems.TRAINING_DUMMY.get()), 8));
        offers.add(sell(10, new ItemStack(NoirmItems.SEALING_SCROLL.get()), 4));
        offers.add(sell(1, new ItemStack(Items.ARROW, 16), 16));
        offers.add(sell(6, named(new ItemStack(Items.LEATHER_CHESTPLATE), "item.noirm.flak_vest"), 4));
        // What they buy.
        offers.add(buy(new ItemStack(Items.WHEAT, 20)));
        offers.add(buy(new ItemStack(Items.IRON_INGOT, 4)));
        offers.add(buy(new ItemStack(Items.COAL, 12)));
        offers.add(buy(new ItemStack(Items.PAPER, 16)));
        offers.add(buy(new ItemStack(Items.STRING, 14)));
    }

    private static ItemStack named(ItemStack stack, String key) {
        stack.setHoverName(Component.translatable(key));
        return stack;
    }

    private static MerchantOffer sell(int emeralds, ItemStack result, int maxUses) {
        return new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), result, maxUses, 1, 0.05f);
    }

    private static MerchantOffer buy(ItemStack cost) {
        return new MerchantOffer(cost, new ItemStack(Items.EMERALD), 16, 1, 0.05f);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        // Merchants don't level up; trading just pays out a little experience to the player.
        if (offer.shouldRewardExp() && level() instanceof ServerLevel server) {
            server.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(server, getX(), getY() + 0.5, getZ(), 1 + random.nextInt(3)));
        }
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide()) {
            VillageResidents.raiseAlarm(this, source.getEntity());
        }
        return hurt;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null; // merchants don't breed
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hasRestriction()) {
            tag.putLong("Home", getRestrictCenter().asLong());
            tag.putInt("HomeRadius", (int) getRestrictRadius());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) {
            restrictTo(BlockPos.of(tag.getLong("Home")), Math.max(2, tag.getInt("HomeRadius")));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.WANDERING_TRADER_YES;
    }
}
