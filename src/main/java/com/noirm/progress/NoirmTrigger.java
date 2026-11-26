package com.noirm.progress;

import com.google.gson.JsonObject;
import com.noirm.Noirm;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The shape every NOIRM advancement trigger shares ("Path of the Shinobi", {@code data/noirm/advancements/}).
 *
 * <p>A trigger is fired with three things: an <b>event</b> (what happened: {@code "cast"},
 * {@code "drill"}…), a <b>key</b> (which one: a rank, a stat, a master…) and a <b>value</b> (how
 * much: a count, a level, a class). In the advancement JSON each of them is an optional condition:
 * <pre>
 * "criteria": { "s_rank": { "trigger": "noirm:jutsu",
 *     "conditions": { "event": "learned", "key": "S" } } }
 * </pre>
 * {@code event} and {@code key} must be equal when given; {@code min} means "value at least this".
 * Each subclass has typed {@code fire…} methods that the game code calls; see {@link NoirmTriggers}.
 */
public abstract class NoirmTrigger extends SimpleCriterionTrigger<NoirmTrigger.Instance> {
    private final ResourceLocation id;

    protected NoirmTrigger(String path) {
        this.id = Noirm.id(path);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        String event = json.has("event") ? GsonHelper.getAsString(json, "event") : null;
        String key = json.has("key") ? GsonHelper.getAsString(json, "key") : null;
        int min = json.has("min") ? GsonHelper.getAsInt(json, "min") : Integer.MIN_VALUE;
        return new Instance(id, player, event, key, min);
    }

    /** Fires for {@code player} (server players only; anything else is ignored). */
    protected void fire(@Nullable Player player, @Nullable String event, @Nullable String key, int value) {
        if (player instanceof ServerPlayer server && !server.level().isClientSide() && server.getAdvancements() != null) {
            trigger(server, instance -> instance.matches(event, key, value));
        }
    }

    /** One criterion's conditions. */
    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final String event;
        private final String key;
        private final int min;

        public Instance(ResourceLocation id, ContextAwarePredicate player, @Nullable String event, @Nullable String key, int min) {
            super(id, player);
            this.event = event;
            this.key = key;
            this.min = min;
        }

        public boolean matches(@Nullable String event, @Nullable String key, int value) {
            return (this.event == null || this.event.equals(event))
                    && (this.key == null || this.key.equalsIgnoreCase(key == null ? "" : key))
                    && value >= min;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (event != null) {
                json.addProperty("event", event);
            }
            if (key != null) {
                json.addProperty("key", key);
            }
            if (min != Integer.MIN_VALUE) {
                json.addProperty("min", min);
            }
            return json;
        }
    }
}
