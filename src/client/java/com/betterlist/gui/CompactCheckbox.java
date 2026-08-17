package com.betterlist.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * A vanilla-styled checkbox whose visual size is controlled by the caller.
 *
 * <p>The vanilla {@code Checkbox} always renders a 17 px square in 26.2. That is
 * appropriate for option screens, but it overwhelms the title area of a compact
 * container screen.</p>
 */
public final class CompactCheckbox extends AbstractButton {
    private static final Identifier SELECTED_HIGHLIGHTED_SPRITE =
            Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");
    private static final Identifier SELECTED_SPRITE =
            Identifier.withDefaultNamespace("widget/checkbox_selected");
    private static final Identifier HIGHLIGHTED_SPRITE =
            Identifier.withDefaultNamespace("widget/checkbox_highlighted");
    private static final Identifier SPRITE =
            Identifier.withDefaultNamespace("widget/checkbox");

    private final Consumer<Boolean> onValueChange;
    private boolean selected;

    public CompactCheckbox(
            int x,
            int y,
            int size,
            Component message,
            boolean selected,
            Consumer<Boolean> onValueChange) {
        super(x, y, size, size, message);
        this.selected = selected;
        this.onValueChange = onValueChange;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.selected = !this.selected;
        this.onValueChange.accept(this.selected);
    }

    public boolean selected() {
        return this.selected;
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        Identifier sprite;
        if (this.selected) {
            sprite = this.isHoveredOrFocused() ? SELECTED_HIGHLIGHTED_SPRITE : SELECTED_SPRITE;
        } else {
            sprite = this.isHoveredOrFocused() ? HIGHLIGHTED_SPRITE : SPRITE;
        }

        guiGraphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                sprite,
                this.getX(),
                this.getY(),
                this.getWidth(),
                this.getHeight());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active) {
            output.add(
                    NarratedElementType.USAGE,
                    Component.translatable(this.selected
                            ? "narration.checkbox.usage.focused.uncheck"
                            : "narration.checkbox.usage.focused.check"));
        }
    }
}
