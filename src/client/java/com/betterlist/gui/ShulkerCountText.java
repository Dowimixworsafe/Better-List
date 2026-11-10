package com.betterlist.gui;

import fi.dy.masa.malilib.render.GuiContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Count text that can carry an inline shulker-box icon (issue #8): "12000 = 6 [icon] + 25 x 64 + 32".
 * {@link WidgetBetterMaterialListEntry#stackBreakdown} puts {@link #ICON} where the icon goes;
 * always measure with {@link #width} and draw with {@link #draw}, never plain font calls,
 * or the marker shows up as a missing-glyph box.
 */
@Environment(EnvType.CLIENT)
final class ShulkerCountText {

    private ShulkerCountText() {}

    /** Private-use char marking the icon position. */
    static final char ICON = '';

    private static final int ICON_PX = 9;          // drawn size, a bit above the 8px text height
    private static final int ICON_ADVANCE = ICON_PX + 1;
    private static final ItemStack SHULKER = new ItemStack(Items.SHULKER_BOX);

    static int width(Font font, String text) {
        int icons = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == ICON) icons++;
        if (icons == 0) return font.width(text);
        return font.width(text.replace(String.valueOf(ICON), "")) + icons * ICON_ADVANCE;
    }

    static void draw(GuiContext ctx, Font font, String text, int x, int y, int color) {
        int start = 0;
        int cx = x;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != ICON) continue;
            if (i > start) {
                String part = text.substring(start, i);
                ctx.drawString(font, part, cx, y, color, false);
                cx += font.width(part);
            }
            if (i < text.length()) {
                float scale = ICON_PX / 16.0F;
                ctx.pose().pushMatrix();
                ctx.pose().translate(cx, y - 1);
                ctx.pose().scale(scale, scale);
                ctx.item(SHULKER, 0, 0);
                ctx.pose().popMatrix();
                cx += ICON_ADVANCE;
            }
            start = i + 1;
        }
    }
}
