package com.noirm.entity;

import com.noirm.gear.NoirmGear;
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
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * The ordinary people of a ninja village (they don't fight; hurting one brings the village's
 * ninjas, see {@link VillageResidents#raiseAlarm}). Each has a {@link Role}:
 * <ul>
 *   <li><b>Merchants</b> keep a shop and trade for emeralds; what they sell depends on the shop
 *       ({@link #SHOP_RAMEN ramen stand}, weapons, scrolls, flowers, teahouse or general goods).</li>
 *   <li><b>Villagers</b> spend the day out in the village and go home when it gets dark or rains.</li>
 *   <li><b>Children</b> spend the day at the Academy (or the plaza) and go home at night.</li>
 *   <li>The <b>village chief</b> of a town.</li>
 * </ul>
 * Right-click one who isn't trading and they'll say something.
 */
public class VillageMerchant extends AbstractVillager {
    public enum Role { MERCHANT, CIVILIAN, CHILD, CHIEF }

    public static final int SHOP_GENERAL = 0, SHOP_RAMEN = 1, SHOP_WEAPONS = 2, SHOP_SCROLLS = 3, SHOP_FLOWERS = 4, SHOP_TEAHOUSE = 5;
    private static final String[] SHOP_KEYS = {"merchant", "ramen_chef", "weaponsmith", "scroll_seller", "florist", "teahouse_keeper"};
    /** How many things each kind of person can say (lang keys {@code villager.noirm.line.<kind>.<n>}). */
    private static final int ADULT_LINES = 10, CHILD_LINES = 6, CHIEF_LINES = 4;

    private Role role = Role.MERCHANT;
    private int shop = SHOP_GENERAL;
    private BlockPos home;
    private BlockPos work;
    private int workRadius = 8;

    public VillageMerchant(EntityType<? extends VillageMerchant> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 0.6));
        goalSelector.addGoal(2, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.55));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------ who they are

    public BlockPos home() {
        return home;
    }

    public BlockPos work() {
        return work;
    }

    public Role role() {
        return role;
    }

    public int shop() {
        return shop;
    }

    /** Sets up a villager: their role, their shop (merchants), where they live and where they spend the day. */
    public void setup(Role role, int shop, BlockPos home, BlockPos work, int workRadius) {
        this.role = role;
        this.shop = shop;
        this.home = home;
        this.work = work;
        this.workRadius = workRadius;
        if (role == Role.CHILD) {
            setAge(-24000);
        }
        restrictTo(home, role == Role.MERCHANT ? Math.max(2, workRadius) : 2);
        setPersistenceRequired();
    }

    /** Keeps the merchant near their stall: they stroll, but never far from {@code home}. */
    public void setHome(BlockPos home, int radius) {
        setup(Role.MERCHANT, shop, home, home, radius);
    }

    @Override
    protected Component getTypeName() {
        String key = switch (role) {
            case MERCHANT -> SHOP_KEYS[Math.floorMod(shop, SHOP_KEYS.length)];
            case CIVILIAN -> "villager";
            case CHILD -> "child";
            case CHIEF -> "village_chief";
        };
        return Component.translatable("entity.noirm.villager." + key);
    }

    // ------------------------------------------------------------ the day

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 40 == 0) {
            followSchedule();
        }
    }

    /** By day, out to work (or school); at night and in the rain, home. (Runs every two seconds.) */
    public void followSchedule() {
        if (home == null) {
            return;
        }
        if (role == Role.CHILD && !isBaby()) {
            setAge(-24000); // children here stay children
        }
        if (role == Role.MERCHANT || work == null) {
            return;
        }
        long time = level().getDayTime() % 24000;
        boolean night = time > 12500 && time < 23400;
        boolean inside = night || level().isRaining();
        if (inside) {
            restrictTo(home, 1);
        } else {
            restrictTo(work, workRadius);
        }
    }

    // ------------------------------------------------------------ talking and trading

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isTrading()) {
            return super.mobInteract(player, hand);
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.awardStat(Stats.TALKED_TO_VILLAGER);
        }
        if (role != Role.MERCHANT || getOffers().isEmpty()) {
            if (!level().isClientSide() && hand == InteractionHand.MAIN_HAND) {
                String kind = role == Role.CHILD ? "child" : role == Role.CHIEF ? "chief" : "adult";
                int count = role == Role.CHILD ? CHILD_LINES : role == Role.CHIEF ? CHIEF_LINES : ADULT_LINES;
                Component line = Component.translatable("villager.noirm.line." + kind + "." + getRandom().nextInt(count));
                player.displayClientMessage(Component.translatable("villager.noirm.says", getDisplayName(), line), false);
                playSound(getAmbientSound(), 1f, isBaby() ? 1.5f : 1f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!level().isClientSide()) {
            setTradingPlayer(player);
            openTradingScreen(player, getDisplayName(), 1);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /**
     * The shop's stock. It's always the same list for a kind of shop, so players can count on it;
     * stock is generous instead of restocking.
     */
    @Override
    protected void updateTrades() {
        if (role != Role.MERCHANT) {
            return;
        }
        MerchantOffers offers = getOffers();
        switch (shop) {
            case SHOP_RAMEN -> {
                offers.add(sell(1, new ItemStack(NoirmItems.RAMEN.get(), 2), 32));
                offers.add(sell(3, new ItemStack(NoirmItems.RAMEN.get(), 8), 16));
                offers.add(sell(1, new ItemStack(Items.COOKED_SALMON, 3), 16));
                offers.add(sell(1, named(new ItemStack(Items.COOKED_PORKCHOP, 3), "item.noirm.barbecue"), 16));
                offers.add(buy(new ItemStack(Items.WHEAT, 18)));
                offers.add(buy(new ItemStack(Items.EGG, 12)));
                offers.add(buy(new ItemStack(Items.CARROT, 20)));
            }
            case SHOP_WEAPONS -> {
                offers.add(sell(2, named(new ItemStack(Items.IRON_SWORD), "item.noirm.kunai"), 8));
                offers.add(sell(1, named(new ItemStack(Items.ARROW, 16), "item.noirm.senbon"), 16));
                offers.add(sell(5, new ItemStack(NoirmGear.FOREHEAD_PROTECTOR_NAVY.get()), 4));
                offers.add(sell(8, new ItemStack(NoirmGear.FLAK_VEST.get()), 4));
                offers.add(sell(4, new ItemStack(NoirmGear.SHINOBI_PANTS.get()), 4));
                offers.add(sell(3, new ItemStack(NoirmGear.SHINOBI_SANDALS.get()), 4));
                offers.add(sell(4, new ItemStack(NoirmItems.TRAINING_DUMMY.get()), 8));
                offers.add(buy(new ItemStack(Items.IRON_INGOT, 4)));
                offers.add(buy(new ItemStack(Items.COAL, 12)));
            }
            case SHOP_SCROLLS -> {
                offers.add(sell(10, new ItemStack(NoirmItems.SEALING_SCROLL.get()), 4));
                offers.add(sell(1, new ItemStack(Items.PAPER, 12), 16));
                offers.add(sell(2, new ItemStack(Items.BOOK, 2), 16));
                offers.add(sell(1, new ItemStack(Items.INK_SAC, 4), 16));
                offers.add(buy(new ItemStack(Items.PAPER, 20)));
                offers.add(buy(new ItemStack(Items.FEATHER, 16)));
            }
            case SHOP_FLOWERS -> {
                for (Item flower : new Item[] {Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.ALLIUM, Items.PEONY, Items.ROSE_BUSH, Items.LILY_OF_THE_VALLEY}) {
                    offers.add(sell(1, new ItemStack(flower, 4), 16));
                }
                offers.add(sell(1, new ItemStack(Items.FLOWER_POT, 3), 16));
                offers.add(buy(new ItemStack(Items.BONE_MEAL, 16)));
            }
            case SHOP_TEAHOUSE -> {
                offers.add(sell(1, named(new ItemStack(Items.HONEY_BOTTLE, 2), "item.noirm.green_tea"), 16));
                offers.add(sell(1, named(new ItemStack(Items.COOKIE, 6), "item.noirm.dango"), 16));
                offers.add(sell(1, new ItemStack(Items.PUMPKIN_PIE, 2), 16));
                offers.add(sell(1, new ItemStack(NoirmItems.RAMEN.get(), 2), 16));
                offers.add(buy(new ItemStack(Items.SUGAR, 16)));
                offers.add(buy(new ItemStack(Items.SWEET_BERRIES, 20)));
            }
            default -> {
                offers.add(sell(2, named(new ItemStack(Items.IRON_SWORD), "item.noirm.kunai"), 8));
                offers.add(sell(1, new ItemStack(NoirmItems.RAMEN.get(), 2), 16));
                offers.add(sell(4, new ItemStack(NoirmItems.TRAINING_DUMMY.get()), 8));
                offers.add(sell(10, new ItemStack(NoirmItems.SEALING_SCROLL.get()), 4));
                offers.add(sell(1, new ItemStack(Items.ARROW, 16), 16));
                offers.add(sell(6, new ItemStack(NoirmGear.FLAK_VEST.get()), 4));
                offers.add(buy(new ItemStack(Items.WHEAT, 20)));
                offers.add(buy(new ItemStack(Items.IRON_INGOT, 4)));
                offers.add(buy(new ItemStack(Items.COAL, 12)));
                offers.add(buy(new ItemStack(Items.PAPER, 16)));
                offers.add(buy(new ItemStack(Items.STRING, 14)));
            }
        }
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
        return null; // villagers here don't breed
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", role.name());
        tag.putInt("Shop", shop);
        if (home != null) {
            tag.putLong("HomePos", home.asLong());
        }
        if (work != null) {
            tag.putLong("WorkPos", work.asLong());
            tag.putInt("WorkRadius", workRadius);
        }
        if (hasRestriction()) {
            tag.putLong("Home", getRestrictCenter().asLong());
            tag.putInt("HomeRadius", (int) getRestrictRadius());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            role = tag.contains("Role") ? Role.valueOf(tag.getString("Role")) : Role.MERCHANT;
        } catch (IllegalArgumentException e) {
            role = Role.MERCHANT;
        }
        shop = tag.getInt("Shop");
        if (tag.contains("HomePos")) {
            home = BlockPos.of(tag.getLong("HomePos"));
        }
        if (tag.contains("WorkPos")) {
            work = BlockPos.of(tag.getLong("WorkPos"));
            workRadius = Math.max(2, tag.getInt("WorkRadius"));
        }
        if (tag.contains("Home")) {
            BlockPos restrict = BlockPos.of(tag.getLong("Home"));
            restrictTo(restrict, Math.max(1, tag.getInt("HomeRadius")));
            if (home == null) {
                home = restrict; // merchants saved before villagers had homes
            }
        }
    }

    // ------------------------------------------------------------ sounds

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
