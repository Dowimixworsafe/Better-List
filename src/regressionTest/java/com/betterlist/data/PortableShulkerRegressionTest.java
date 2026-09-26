package com.betterlist.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

public final class PortableShulkerRegressionTest {
    private static final String FIRST = "shulker:00000000-0000-4000-8000-000000000001";
    private static final String SECOND = "shulker:00000000-0000-4000-8000-000000000002";
    private static final String START = "minecraft:overworld;-17, 64, 32";
    private static final String END = "minecraft:the_nether;80, 70, -48";

    public static void main(String[] args) {
        ContainerDataManager.clear();
        ContainerDataManager.setContainerMarkedSilent(START, true);
        ContainerDataManager.updateContainerItemsSilent(START, Map.of("minecraft:stone", 64));
        JsonObject initial = state(FIRST, "placed", START, 64);
        initial.addProperty("track", true);
        PortableShulkerManager.accept(initial);
        check(ContainerDataManager.getMarkedContainers().size() == 1, "migration must replace the position entry");
        check(total() == 64, "migration must not double counts");
        check(PortableShulkerManager.at(START).equals(FIRST), "placed identity");

        ChestHighlightManager.toggle(FIRST);
        PortableShulkerManager.accept(state(FIRST, "dropped", "", 64));
        check(total() == 64 && PortableShulkerManager.isTracked(FIRST), "breaking keeps tracking and contents");
        check(ChestHighlightManager.posOf(FIRST) == null, "breaking removes the old outline position");
        check(PortableShulkerManager.at(START).equals(START), "old position no longer identifies the shulker");

        PortableShulkerManager.accept(state(FIRST, "carried", "", 64));
        PortableShulkerManager.accept(state(FIRST, "placed", END, 32));
        check(total() == 32 && ContainerDataManager.getMarkedContainers().size() == 1, "moving and changing contents updates one entry");
        check("minecraft:the_nether".equals(ChestHighlightManager.dimensionOf(FIRST)), "outline follows dimensions");

        JsonObject sameContents = state(SECOND, "placed", START, 32);
        PortableShulkerManager.accept(sameContents);
        check(!PortableShulkerManager.isTracked(SECOND), "identical contents must not adopt tracking");
        sameContents.addProperty("track", true);
        PortableShulkerManager.accept(sameContents);
        check(total() == 64 && ContainerDataManager.getMarkedContainers().size() == 2, "distinct identical shulkers remain independent");

        JsonObject saved = JsonParser.parseString(PortableShulkerManager.snapshot().toString()).getAsJsonObject();
        ContainerDataManager.clear();
        PortableShulkerManager.merge(saved);
        PortableShulkerManager.merge(saved);
        check(total() == 64 && ContainerDataManager.getMarkedContainers().size() == 2, "full-state and JSON roundtrip deduplicate by identity");

        PortableShulkerManager.accept(state(FIRST, "carried", "", 0));
        check(total() == 32 && PortableShulkerManager.isTracked(FIRST), "empty updates keep tracking");
        PortableShulkerManager.setTracked(SECOND, false, false);
        PortableShulkerManager.accept(state(SECOND, "carried", "", 32));
        check(total() == 0 && !PortableShulkerManager.isTracked(SECOND), "late updates cannot resurrect an unmarked shulker");

        JsonObject invalid = state(FIRST, "placed", "PLAYER EQ", 900);
        invalid.addProperty("track", true);
        PortableShulkerManager.accept(invalid);
        check(total() == 0, "invalid location rejected");
        invalid = state(FIRST, "carried", "", -5);
        PortableShulkerManager.accept(invalid);
        check(total() == 0, "negative contents rejected");
        check(!PortableShulkerManager.isId("shulker:1-1-1-1-1"), "noncanonical UUID rejected");
        ContainerDataManager.clearAll();
        check(ContainerDataManager.getMarkedContainers().isEmpty(), "clear includes portable tracking");
        JsonObject live = state(FIRST, "carried", "", 64);
        live.addProperty("track", true);
        PortableShulkerManager.accept(live);
        JsonObject lost = state(FIRST, "lost", START, 64);
        lost.addProperty("reason", "fire");
        lost.addProperty("lostAt", 123456789L);
        PortableShulkerManager.accept(lost);
        check(total() == 0 && !PortableShulkerManager.isTracked(FIRST), "lost shulkers do not count");
        check(PortableShulkerManager.lostIds().contains(FIRST), "lost shulker remains in history");
        check(PortableShulkerManager.contents(FIRST).get("minecraft:stone") == 64, "history retains contents");
        PortableShulkerManager.accept(live);
        check(total() == 0, "stale live update cannot resurrect loss");
        JsonObject history = PortableShulkerManager.snapshot();
        ContainerDataManager.clear();
        PortableShulkerManager.merge(history);
        check(total() == 0 && PortableShulkerManager.lostIds().contains(FIRST), "history survives snapshot roundtrip");
        PortableShulkerManager.setTracked(FIRST, false, false);
        check(PortableShulkerManager.lostIds().isEmpty(), "history can be deleted");
        checkItemRoundtrip();
        System.out.println("Portable shulker regression checks passed.");
    }

    private static void checkItemRoundtrip() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.world.item.Items.SHULKER_BOX.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
        var block = net.minecraft.world.level.block.Blocks.SHULKER_BOX.defaultBlockState();
        var box = new net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity(net.minecraft.core.BlockPos.ZERO, block);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString(com.betterlist.server.PortableShulkerService.KEY, FIRST);
        box.setComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag)).build());
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
        com.betterlist.server.PortableShulkerService.stampDrop(box, stack);
        check(FIRST.equals(PortableShulkerManager.itemId(stack)), "Fabric dropped item carries identity");
        var placed = new net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity(net.minecraft.core.BlockPos.ZERO, block);
        placed.applyComponentsFromItemStack(stack);
        check(FIRST.equals(com.betterlist.server.PortableShulkerService.id(placed)), "placed block restores item identity");
        var creativeDrop = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
        creativeDrop.applyComponents(box.collectComponents());
        check(FIRST.equals(PortableShulkerManager.itemId(creativeDrop)), "creative drop retains the same identity");
        var pdc = new net.minecraft.nbt.CompoundTag();
        pdc.putString("betterlist:shulker_id", SECOND);
        var paperTag = new net.minecraft.nbt.CompoundTag();
        paperTag.put("PublicBukkitValues", pdc);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(paperTag));
        check(SECOND.equals(PortableShulkerManager.itemId(stack)), "Paper item identity is recognized for inventory deduplication");
        stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                net.minecraft.world.item.component.TypedEntityData.of(box.getType(), paperTag));
        check(SECOND.equals(PortableShulkerManager.itemId(stack)), "Paper block-entity identity survives item serialization");
    }

    private static int total() { return ContainerDataManager.getTotalItems().getOrDefault("minecraft:stone", 0); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static JsonObject state(String id, String kind, String location, int count) {
        JsonObject state = new JsonObject();
        state.addProperty("id", id);
        state.addProperty("state", kind);
        state.addProperty("location", location);
        state.addProperty("holder", "Tester");
        JsonObject items = new JsonObject();
        items.addProperty("minecraft:stone", count);
        state.add("items", items);
        return state;
    }
}
