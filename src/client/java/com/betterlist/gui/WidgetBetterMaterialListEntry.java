package com.betterlist.gui;

import com.betterlist.data.MaterialStateManager;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import com.betterlist.party.FocusManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import java.util.List;

@Environment(EnvType.CLIENT)
public class WidgetBetterMaterialListEntry extends WidgetListEntryBase<MaterialListEntryPair> {
    private final GuiBetterMaterialList parent;
    private ButtonGeneric leftCheckbox;
    private ButtonGeneric rightCheckbox;
    private String leftItemName;
    private String rightItemName;

    public WidgetBetterMaterialListEntry(int x, int y, int width, int height, boolean isOdd,
            MaterialListEntryPair entryPair, int listIndex,
            GuiBetterMaterialList parent) {
        super(x, y, width, height, entryPair, listIndex);
        this.parent = parent;

        GuiBetterMaterialList.LayoutMode mode = parent.getLayoutMode();
        LayoutMetrics.Section leftSection = LayoutMetrics.section(x, width, mode, 0);

        setupLeftEntry(entryPair.getLeft(), leftSection.x(), y, leftSection.width(), height);
        if (entryPair.getRight() != null) {
            LayoutMetrics.Section rightSection = LayoutMetrics.section(x, width, mode, 1);
            setupRightEntry(
                    entryPair.getRight(), rightSection.x(), y, rightSection.width(), height);
        }
    }

    private void setupLeftEntry(MaterialListEntry entry, int x, int y, int width, int height) {
        if (entry == null) return;
        ItemStack stack = entry.getStack();
        this.leftItemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        boolean userChecked = MaterialStateManager.isChecked(parent.getChecklistKey(), this.leftItemName);
        int actuallyMissing = Math.max(0, entry.getCountMissing() - entry.getCountAvailable());
        // Three-state glyph: green = user-checked, yellow = auto-fulfilled, red = missing
        String glyph = userChecked ? "§a✔" : (actuallyMissing <= 0 ? "§e✔" : "§c✖");

        LayoutMetrics.Columns columns = parent.getLayoutMetrics().columns(x, width);
        int bx = columns.checkboxStart();
        int by = y + (height - BmlLayoutConstants.CHECKBOX_WIDTH) / 2;
        this.leftCheckbox = new ButtonGeneric(bx, by, BmlLayoutConstants.CHECKBOX_WIDTH, BmlLayoutConstants.CHECKBOX_WIDTH, glyph, new String[0]);
        this.addButton(this.leftCheckbox, com.betterlist.util.BmlButtons.leftClick(() -> {
            boolean newState = !MaterialStateManager.isChecked(parent.getChecklistKey(),
                    WidgetBetterMaterialListEntry.this.leftItemName);
            MaterialStateManager.setChecked(parent.getChecklistKey(),
                    WidgetBetterMaterialListEntry.this.leftItemName, newState);
            WidgetBetterMaterialListEntry.this.leftCheckbox.setDisplayString(newState ? "§a✔" : "§c✖");
        }));
    }

    private void setupRightEntry(MaterialListEntry entry, int x, int y, int width, int height) {
        if (entry == null) return;
        ItemStack stack = entry.getStack();
        this.rightItemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        boolean userChecked = MaterialStateManager.isChecked(parent.getChecklistKey(), this.rightItemName);
        int actuallyMissing = Math.max(0, entry.getCountMissing() - entry.getCountAvailable());
        String glyph = userChecked ? "§a✔" : (actuallyMissing <= 0 ? "§e✔" : "§c✖");

        LayoutMetrics.Columns columns = parent.getLayoutMetrics().columns(x, width);
        int bx = columns.checkboxStart();
        int by = y + (height - BmlLayoutConstants.CHECKBOX_WIDTH) / 2;
        this.rightCheckbox = new ButtonGeneric(bx, by, BmlLayoutConstants.CHECKBOX_WIDTH, BmlLayoutConstants.CHECKBOX_WIDTH, glyph, new String[0]);
        this.addButton(this.rightCheckbox, com.betterlist.util.BmlButtons.leftClick(() -> {
            boolean newState = !MaterialStateManager.isChecked(parent.getChecklistKey(),
                    WidgetBetterMaterialListEntry.this.rightItemName);
            MaterialStateManager.setChecked(parent.getChecklistKey(),
                    WidgetBetterMaterialListEntry.this.rightItemName, newState);
            WidgetBetterMaterialListEntry.this.rightCheckbox.setDisplayString(newState ? "§a✔" : "§c✖");
        }));
    }

    static String stackBreakdown(int n) {
        if (n <= 0) return String.valueOf(n);
        int stacks = n / 64;
        int rem = n % 64;
        if (stacks == 0) return String.valueOf(n);
        if (rem == 0) return n + " = " + stacks + " x 64";
        return n + " = " + stacks + " x 64 + " + rem;
    }

    @Override
    public void render(GuiContext guiContext, int mouseX, int mouseY, boolean selected) {
        int bgColor = (this.listIndex % 2 == 0) ? 0x20FFFFFF : 0x30FFFFFF;
        guiContext.fill(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        super.render(guiContext, mouseX, mouseY, selected);

        MaterialListEntryPair pair = (MaterialListEntryPair) this.entry;
        GuiBetterMaterialList.LayoutMode mode = parent.getLayoutMode();
        LayoutMetrics.Section leftSection = LayoutMetrics.section(this.x, this.width, mode, 0);

        if (pair.getLeft() != null) {
            renderHalf(
                    guiContext,
                    pair.getLeft(),
                    leftSection.x(),
                    this.y,
                    leftSection.width(),
                    this.height);
        }

        if (mode != GuiBetterMaterialList.LayoutMode.SINGLE) {
            LayoutMetrics.Section rightSection = LayoutMetrics.section(this.x, this.width, mode, 1);
            int dividerX = rightSection.x() - 1;
            guiContext.fill(dividerX, this.y, dividerX + 1, this.y + this.height, 0x40FFFFFF);

            // Hover detection for tooltip (2-col modes only)
            boolean overLeft  = pair.getLeft()  != null
                    && mouseX >= leftSection.x()
                    && mouseX < leftSection.x() + leftSection.width()
                    && mouseY >= this.y && mouseY < this.y + this.height;
            boolean overRight = pair.getRight() != null
                    && mouseX >= rightSection.x()
                    && mouseX < rightSection.x() + rightSection.width()
                    && mouseY >= this.y && mouseY < this.y + this.height;
            if (overLeft)       parent.setHoveredEntry(pair.getLeft(),  mouseX, mouseY);
            else if (overRight) parent.setHoveredEntry(pair.getRight(), mouseX, mouseY);
        }

        if (pair.getRight() != null) {
            LayoutMetrics.Section rightSection = LayoutMetrics.section(this.x, this.width, mode, 1);
            renderHalf(
                    guiContext,
                    pair.getRight(),
                    rightSection.x(),
                    this.y,
                    rightSection.width(),
                    this.height);
        }

        // Hover tracking for right-click targeting in SINGLE mode
        if (mode == GuiBetterMaterialList.LayoutMode.SINGLE && pair.getLeft() != null) {
            if (mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height)
                parent.setHoveredEntry(pair.getLeft(), mouseX, mouseY);
        }
    }

    private void renderHalf(GuiContext guiContext, MaterialListEntry matEntry, int x, int y, int width, int height) {
        ItemStack stack = matEntry.getStack();
        Font font = Minecraft.getInstance().font;

        String name = stack.getHoverName().getString();
        int total = matEntry.getCountTotal();
        int missingInWorld = matEntry.getCountMissing();
        int available = matEntry.getCountAvailable();
        int actuallyMissing = Math.max(0, missingInWorld - available);
        int placed = Math.max(0, total - missingInWorld);
        int textY = y + (height - 8) / 2;

        int iconX = x + BmlLayoutConstants.ICON_PADDING;
        int iconY = y + (height - BmlLayoutConstants.ICON_SIZE) / 2;
        guiContext.renderItem(stack, iconX, iconY);
        guiContext.renderItemDecorations(font, stack, iconX, iconY);

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Border for MY targets — always, regardless of party/focus mode (right-click = target).
        if (FocusManager.isLocalPlayerTargetingRaw(itemId) && Minecraft.getInstance().player != null) {
            int c = FocusManager.getColor(Minecraft.getInstance().player.getGameProfile().name());
            guiContext.fill(iconX - 1, iconY - 1, iconX + 17, iconY,      c); // top
            guiContext.fill(iconX - 1, iconY + 16, iconX + 17, iconY + 17, c); // bottom
            guiContext.fill(iconX - 1, iconY,      iconX,      iconY + 16, c); // left
            guiContext.fill(iconX + 16, iconY,     iconX + 17, iconY + 16, c); // right
        }

        // Heads of other players targeting this item — only in a party and when focus is visible.
        if (com.betterlist.party.PartyManager.isInParty() && FocusManager.isFocusVisible()) {
            List<FocusManager.PlayerFocus> others = FocusManager.getTargetersWithNames(itemId);
            for (int di = 0; di < Math.min(others.size(), 3); di++) {
                parent.addFaceRenderRequest(others.get(di).nick(), iconX + 9, iconY + di * 7, 7);
            }
        }

        boolean isSingle = parent.getLayoutMode() == GuiBetterMaterialList.LayoutMode.SINGLE;
        LayoutMetrics.Columns columns = parent.getLayoutMetrics().columns(x, width);
        int maxNameWidth = columns.maxNameWidth();

        String displayName = name;
        if (maxNameWidth <= 0) {
            displayName = "";
        } else if (font.width(displayName) > maxNameWidth) {
            while (font.width(displayName + "...") > maxNameWidth && displayName.length() > 1) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName = font.width(displayName + "...") <= maxNameWidth
                    ? displayName + "..."
                    : "";
        }
        guiContext.drawString(font, displayName, columns.nameStart(), textY, 0xFFFFFFFF, false);

        String totalStr = isSingle ? stackBreakdown(total) : String.valueOf(total);
        guiContext.drawString(font, totalStr, columns.totalStart(), textY, 0xFFAAAAAA, false);

        int colIconY = y + (height - 16) / 2;

        guiContext.renderItem(new ItemStack(Items.GRASS_BLOCK), columns.placedStart(), colIconY);
        guiContext.drawString(font, String.valueOf(placed), columns.placedStart() + 18, textY, 0xFFFFFFFF, false);

        guiContext.renderItem(new ItemStack(Items.CHEST), columns.availableStart(), colIconY);
        guiContext.drawString(font, String.valueOf(available), columns.availableStart() + 18, textY, 0xFFFFFFFF, false);

        int missColor = actuallyMissing > 0 ? 0xFFFF5555 : 0xFF55FF55;
        guiContext.drawString(font, String.valueOf(actuallyMissing), columns.missingStart() + 2, textY, missColor, false);
    }
}
