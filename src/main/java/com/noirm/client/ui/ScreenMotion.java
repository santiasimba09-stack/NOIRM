package com.noirm.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A screen's motion: it fades in and rises a few pixels when it opens, fades out (and sinks) when
 * it closes, and cross-fades its content when you switch tabs. All time-based.
 *
 * <pre>
 *   motion.begin(g);          // whole screen: alpha + slide
 *     ...draw the frame...
 *     motion.beginContent(g); // tab content: also the cross-fade
 *       ...draw the tab...
 *     motion.endContent(g);
 *   motion.end(g);
 * </pre>
 * Closing: {@code onClose()} calls {@link #close(Runnable)} with the real close, and the screen's
 * {@code tick()} calls {@link #tick()}, which runs it once the fade has finished.
 */
public final class ScreenMotion {
    /** How far the screen slides (GUI pixels). */
    public static final float SLIDE = 5f;

    private long openedAt = Long.MIN_VALUE / 2;
    private long closingAt = -1;
    private long tabAt = Long.MIN_VALUE / 2;
    private Runnable onClosed;
    private boolean depth;

    /** Starts the opening animation (call from {@code init()} the first time). */
    public void open() {
        openedAt = Anim.now();
        closingAt = -1;
        onClosed = null;
    }

    /** True once {@link #open()} has run. */
    public boolean started() {
        return openedAt > Long.MIN_VALUE / 2;
    }

    /** 0 (hidden) → 1 (fully shown), eased. */
    public float visibility() {
        if (closingAt >= 0) {
            return 1f - Anim.easeInCubic(Anim.progress(closingAt, Anim.CLOSE_MS));
        }
        return Anim.easeOutCubic(Anim.progress(openedAt, Anim.OPEN_MS));
    }

    public boolean closing() {
        return closingAt >= 0;
    }

    /** Plays the closing fade, then runs {@code done} (from {@link #tick()}). A second call does nothing. */
    public void close(Runnable done) {
        if (closingAt >= 0) {
            return;
        }
        closingAt = Anim.now();
        onClosed = done;
    }

    /** Call every client tick: finishes a close whose fade is over. */
    public void tick() {
        if (closingAt >= 0 && onClosed != null && Anim.now() - closingAt >= Anim.CLOSE_MS) {
            Runnable done = onClosed;
            onClosed = null;
            done.run();
        }
    }

    /** Restart the content cross-fade (a tab was switched). */
    public void tabChanged() {
        tabAt = Anim.now();
    }

    public float contentAlpha() {
        return Anim.easeOutCubic(Anim.progress(tabAt, Anim.TAB_MS));
    }

    /** The dim layer behind the screen, faded with it. */
    public void scrim(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, Theme.fade(Theme.SCRIM, visibility()));
    }

    public void begin(GuiGraphics g) {
        float v = visibility();
        g.pose().pushPose();
        g.pose().translate(0f, (1f - v) * SLIDE, 0f);
        depth = true;
        g.setColor(1f, 1f, 1f, v);
    }

    public void beginContent(GuiGraphics g) {
        g.setColor(1f, 1f, 1f, visibility() * contentAlpha());
    }

    public void endContent(GuiGraphics g) {
        g.setColor(1f, 1f, 1f, visibility());
    }

    public void end(GuiGraphics g) {
        g.setColor(1f, 1f, 1f, 1f);
        if (depth) {
            g.pose().popPose();
            depth = false;
        }
    }
}
