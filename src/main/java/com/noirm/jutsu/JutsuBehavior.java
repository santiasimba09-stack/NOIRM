package com.noirm.jutsu;

/** What a kind of jutsu does. Implementations live in {@link JutsuBehaviors}. */
public interface JutsuBehavior {
    /** TAP / CHARGE: the jutsu happens. CHANNEL: called once when it starts. */
    void cast(JutsuContext context);

    /** CHANNEL only: called every tick while it's held ({@code tick} counts from 1). */
    default void channel(JutsuContext context, int tick) {}
}
