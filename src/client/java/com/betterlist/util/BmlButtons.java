package com.betterlist.util;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Helpers for malilib button action listeners.
 *
 * malilib's {@code ButtonGeneric} forwards a click to its {@code IButtonActionListener} for
 * ANY mouse button (left/middle/right). We almost always want a button to react only to a
 * left-click, so middle-click (scroll-wheel press) and right-click don't accidentally trigger
 * the action. Wrap action listeners with {@link #leftClick} to enforce that.
 */
@Environment(EnvType.CLIENT)
public final class BmlButtons {

    private BmlButtons() {}

    /**
     * Returns a listener that runs {@code action} only on a left-click. Compare against
     * {@code InputConstants.MOUSE_BUTTON_LEFT}, never a literal: 26.3 moved from GLFW (left = 0)
     * to SDL (left = 1), and a hardcoded 0 silently disabled every button.
     */
    public static IButtonActionListener leftClick(Runnable action) {
        return (button, mouseButton) -> {
            if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
                action.run();
            }
        };
    }
}
