package com.betterlist.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A vanilla-styled checkbox whose visual size is controlled by the caller.
 *
 * <p>The vanilla {@code Checkbox} always renders a 17 px square in 26.2. That is
 * appropriate for option screens, but it overwhelms the title area of a compact
 * container screen.</p>
 *
 * <p>The checked state is read from a supplier on every render and every click rather
 * than cached in a field: the owning state can change under an open screen (a party
 * member unmarking the same chest arrives as a silent sync), and a cached copy would
 * then render stale and send the wrong value on the next click.</p>
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

    private final BooleanSupplier selectedState;
    private final Consumer<Boolean> onValueChange;

    public CompactCheckbox(
            int x,
            int y,
            int size,
            Component message,
            BooleanSupplier selectedState,
            Consumer<Boolean> onValueChange) {
        super(x, y, size, size, message);
        this.selectedState = selectedState;
        this.onValueChange = onValueChange;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onValueChange.accept(!this.selected());
    }

    public boolean selected() {
        return this.selectedState.getAsBoolean();
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        Identifier sprite;
        if (this.selected()) {
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
                    Component.translatable(this.selected()
                            ? "narration.checkbox.usage.focused.uncheck"
                            : "narration.checkbox.usage.focused.check"));
        }
    }
}
