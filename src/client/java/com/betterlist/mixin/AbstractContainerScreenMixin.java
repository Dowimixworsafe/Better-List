package com.betterlist.mixin;

import com.betterlist.data.ContainerDataManager;
import com.betterlist.gui.CompactCheckbox;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends net.minecraft.client.gui.screens.Screen {

    @Unique
    private static final Component BML_TRACKING_TOOLTIP = Component.literal("Track in Material List");

    @Unique
    private static final int BML_TRACKING_CHECKBOX_SIZE = 9;

    @Unique
    private static final int BML_TRACKING_ICON_SIZE = 10;

    @Unique
    private static final int BML_TRACKING_CONTROL_GAP = 2;

    @Unique
    private static final int BML_TRACKING_RIGHT_INSET = 7;

    @Unique
    private static final int BML_TRACKING_TOP_INSET = 5;

    @Unique
    protected CompactCheckbox bml_TrackingCheckboxInstance;

    @Unique
    private int bml_trackingIconX;

    @Unique
    private int bml_trackingIconY;

    @Accessor("leftPos")
    protected abstract int bml_getLeftPos();

    @Accessor("topPos")
    protected abstract int bml_getTopPos();

    @Accessor("imageWidth")
    protected abstract int bml_getImageWidth();

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    // Builds a unique chest id: dimension + coordinates (e.g. minecraft:overworld;[10, 64, -20]).
    // Merges double chests into a single ID!
    private String getContainerId() {
        if (ContainerDataManager.lastInteractedBlockPos == null || Minecraft.getInstance().level == null) {
            return null;
        }

        net.minecraft.core.BlockPos pos = ContainerDataManager.lastInteractedBlockPos;
        net.minecraft.world.level.block.state.BlockState state = Minecraft.getInstance().level.getBlockState(pos);

        // If it is a chest (ChestBlock).
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
            net.minecraft.world.level.block.state.properties.ChestType chestType = state
                    .getValue(net.minecraft.world.level.block.ChestBlock.TYPE);

            // If it is not a single chest.
            if (chestType != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                net.minecraft.core.BlockPos otherHalfPos = pos;

                // Compute the other half's position from the facing.
                if (chestType == net.minecraft.world.level.block.state.properties.ChestType.RIGHT) {
                    otherHalfPos = pos.relative(facing.getCounterClockWise());
                } else if (chestType == net.minecraft.world.level.block.state.properties.ChestType.LEFT) {
                    otherHalfPos = pos.relative(facing.getClockWise());
                }

                // Always pick the smaller BlockPos (smaller X, ties broken by smaller
                // Z)
                // so both halves produce an identical ID.
                if (otherHalfPos.getX() < pos.getX()
                        || (otherHalfPos.getX() == pos.getX() && otherHalfPos.getZ() < pos.getZ())) {
                    pos = otherHalfPos;
                }
            }
        }

        return Minecraft.getInstance().level.dimension().identifier().toString() + ";" + pos.toShortString();
    }

    // Only chests / shulker boxes / hoppers are trackable containers. The player's own
    // inventory, crafting tables, furnaces, anvils, etc. must NEVER be scanned — their slots
    // would otherwise be written into the last-clicked chest's id and wipe its real contents.
    @Unique
    private boolean bml_isTrackableContainer() {
        Object screen = (Object) this;
        return screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen ||
                screen instanceof net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen ||
                screen instanceof net.minecraft.client.gui.screens.inventory.HopperScreen;
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInit(CallbackInfo ci) {
        // Avoid injecting into furnaces, anvils, crafting tables, etc.
        if (!bml_isTrackableContainer())
            return;

        String cid = getContainerId();
        if (cid == null)
            return;

        // Keep the paper icon at the right and let the larger checkbox extend to
        // the left, matching the original title-strip proportions.
        int controlWidth =
                BML_TRACKING_CHECKBOX_SIZE + BML_TRACKING_CONTROL_GAP
                        + BML_TRACKING_ICON_SIZE;
        int controlX =
                bml_getLeftPos() + bml_getImageWidth()
                        - controlWidth - BML_TRACKING_RIGHT_INSET;
        int controlY = bml_getTopPos() + BML_TRACKING_TOP_INSET;

        this.bml_trackingIconX =
                controlX + BML_TRACKING_CHECKBOX_SIZE + BML_TRACKING_CONTROL_GAP;
        this.bml_trackingIconY =
                controlY + (BML_TRACKING_CHECKBOX_SIZE - BML_TRACKING_ICON_SIZE) / 2;

        this.bml_TrackingCheckboxInstance = new CompactCheckbox(
                controlX,
                controlY,
                BML_TRACKING_CHECKBOX_SIZE,
                BML_TRACKING_TOOLTIP,
                () -> ContainerDataManager.isContainerMarked(cid),
                selected -> ContainerDataManager.setContainerMarked(cid, selected));
        this.bml_TrackingCheckboxInstance.setTooltip(Tooltip.create(BML_TRACKING_TOOLTIP));
        this.addRenderableWidget(this.bml_TrackingCheckboxInstance);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void onRender(
            net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        if (this.bml_TrackingCheckboxInstance != null
                && this.bml_TrackingCheckboxInstance.visible) {
            float iconScale = BML_TRACKING_ICON_SIZE / 16.0F;
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(this.bml_trackingIconX, this.bml_trackingIconY);
            guiGraphics.pose().scale(iconScale, iconScale);
            guiGraphics.item(new ItemStack(Items.PAPER), 0, 0);
            guiGraphics.pose().popMatrix();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    public void onRemoved(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        // Same guard as onInit: closing the player inventory / a crafting table / furnace
        // must not scan its slots into the last-clicked chest's id (would zero it out).
        if (!bml_isTrackableContainer())
            return;

        String cid = getContainerId();
        if (cid == null)
            return;

        // Save only if the chest is currently marked for tracking.
        if (!ContainerDataManager.isContainerMarked(cid)) {
            return;
        }

        Map<String, Integer> contents = new HashMap<>();
        var slots = screen.getMenu().slots;

        // Guard (Math.max) to avoid errors with weird custom GUIs
        // from other mods.
        // Drop the last 36 slots since they always belong to the player inventory
        // (we don't want to count them as chest contents).
        int containerSlotsEnd = Math.max(0, slots.size() - 36);

        for (int i = 0; i < containerSlotsEnd; i++) {
            Slot slot = slots.get(i);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                contents.put(key, contents.getOrDefault(key, 0) + stack.getCount());
            }
        }

        ContainerDataManager.updateContainerItems(cid, contents);
    }
}
