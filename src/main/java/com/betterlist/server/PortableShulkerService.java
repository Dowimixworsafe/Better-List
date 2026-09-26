package com.betterlist.server;

import com.betterlist.network.BmlPackets;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortableShulkerService {
    public static final String KEY = "betterlist:shulker_id";
    private static java.nio.file.Path lossFile;
    private static final Map<String, JsonObject> states = new HashMap<>();
    private static final Map<String, Set<UUID>> watchers = new HashMap<>();
    private static final Map<String, String> locations = new HashMap<>();
    private static int ticks;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            lossFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/betterlist_lost_shulkers.json");
            loadLosses();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (++ticks % 20 == 0) tick(server); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { states.clear(); watchers.clear(); locations.clear(); ticks = 0; });
    }

    private static void loadLosses() {
        if (!java.nio.file.Files.exists(lossFile)) return;
        try {
            var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(lossFile)).getAsJsonObject();
            saved.entrySet().forEach(entry -> {
                if (valid(entry.getKey()) && entry.getValue().isJsonObject()
                        && "lost".equals(entry.getValue().getAsJsonObject().get("state").getAsString()))
                    states.put(entry.getKey(), entry.getValue().getAsJsonObject());
            });
        } catch (Exception e) { com.betterlist.ExampleMod.LOGGER.error("Failed to persist shulker history", e); }
    }

    private static void saveLosses() {
        if (lossFile == null) return;
        try {
            JsonObject saved = new JsonObject();
            states.forEach((id, state) -> { if ("lost".equals(state.get("state").getAsString())) saved.add(id, state); });
            java.nio.file.Files.createDirectories(lossFile.getParent());
            var temporary = lossFile.resolveSibling(lossFile.getFileName() + ".tmp");
            java.nio.file.Files.writeString(temporary, saved.toString());
            java.nio.file.Files.move(temporary, lossFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { com.betterlist.ExampleMod.LOGGER.error("Failed to persist shulker history", e); }
    }

    private static boolean valid(String id) {
        if (id == null || !id.startsWith("shulker:")) return false;
        try { return id.equals("shulker:" + UUID.fromString(id.substring(8))); }
        catch (IllegalArgumentException e) { return false; }
    }

    public static String id(ShulkerBoxBlockEntity box) {
        return box.components().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(KEY, "");
    }

    public static String id(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(KEY, "");
    }

    public static void stampDrop(ShulkerBoxBlockEntity box, ItemStack stack) {
        String id = id(box);
        if (valid(id)) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEY, id));
    }

    public static void placed(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box
                && valid(id(box))) publish(serverLevel.getServer(), state(box));
    }

    public static void handle(ServerPlayer player, JsonObject packet) {
        String type = packet.get("type").getAsString();
        if (type.equals(BmlPackets.SHULKER_BIND)) {
            BlockPos pos = parseLocation(player.level(), packet.get("location").getAsString());
            if (pos == null || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64
                    || !player.level().hasChunkAt(pos)
                    || !(player.level().getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)
                    || !(player.containerMenu instanceof ShulkerBoxMenu)
                    || player.containerMenu.getSlot(0).container != box) return;
            String id = id(box);
            if (!valid(id)) {
                id = "shulker:" + UUID.randomUUID();
                String assigned = id;
                CustomData data = box.components().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .update(tag -> tag.putString(KEY, assigned));
                box.setComponents(DataComponentMap.builder().addAll(box.components()).set(DataComponents.CUSTOM_DATA, data).build());
                box.setChanged();
            }
            JsonObject state = state(box);
            publish(player.level().getServer(), state);
            boolean track = packet.get("track").getAsBoolean();
            if (track) watchers.computeIfAbsent(id, k -> new HashSet<>()).add(player.getUUID());
            JsonObject reply = state.deepCopy();
            reply.addProperty("track", track);
            if (packet.has("request")) reply.add("request", packet.get("request"));
            send(player, reply);
        } else if (type.equals(BmlPackets.SHULKER_SUBSCRIBE)) {
            String id = packet.get("id").getAsString();
            if (!valid(id)) return;
            Set<UUID> members = watchers.computeIfAbsent(id, k -> new HashSet<>());
            if (!packet.get("subscribe").getAsBoolean()) { members.remove(player.getUUID()); return; }
            members.add(player.getUUID());
            if (packet.has("location")) {
                locations.putIfAbsent(id, packet.get("location").getAsString());
                for (ServerLevel level : player.level().getServer().getAllLevels()) {
                    BlockPos pos = parseLocation(level, packet.get("location").getAsString());
                    if (pos != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box
                            && id.equals(id(box)) && !(states.containsKey(id) && "lost".equals(states.get(id).get("state").getAsString()))) states.put(id, state(box));
                }
            }
            if (states.containsKey(id)) send(player, states.get(id));
        }
    }

    public static void lost(ItemEntity item, String reason) {
        if (!(item.level() instanceof ServerLevel level) || !valid(id(item.getItem()))) return;
        ItemStack stack = item.getItem();
        JsonObject contents = new JsonObject();
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().forEach(value -> add(contents, value));
        JsonObject lost = packet(id(stack), "lost", level.dimension().identifier() + ";" + item.blockPosition().toShortString(), "", contents);
        lost.addProperty("reason", reason);
        lost.addProperty("lostAt", System.currentTimeMillis());
        publish(level.getServer(), lost);
    }

    public static void disconnect(UUID player) { watchers.values().forEach(set -> set.remove(player)); }

    private static BlockPos parseLocation(ServerLevel level, String location) {
        String prefix = level.dimension().identifier() + ";";
        if (!location.startsWith(prefix)) return null;
        try {
            String[] xyz = location.substring(prefix.length()).replace("[", "").replace("]", "").split(",");
            if (xyz.length != 3) return null;
            return new BlockPos(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim()));
        } catch (NumberFormatException e) { return null; }
    }

    private static JsonObject items(Container container) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < container.getContainerSize(); i++) add(result, container.getItem(i));
        return result;
    }

    private static void add(JsonObject items, ItemStack stack) {
        if (stack.isEmpty()) return;
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        items.addProperty(item, stack.getCount() + (items.has(item) ? items.get(item).getAsInt() : 0));
    }

    private static JsonObject packet(String id, String state, String location, String holder, JsonObject items) {
        JsonObject result = new JsonObject();
        result.addProperty("type", BmlPackets.SHULKER_STATE);
        result.addProperty("v", BmlPackets.PROTOCOL_VERSION);
        result.addProperty("id", id);
        result.addProperty("state", state);
        result.addProperty("location", location);
        result.addProperty("holder", holder);
        result.add("items", items);
        return result;
    }

    private static JsonObject state(ShulkerBoxBlockEntity box) {
        return packet(id(box), "placed", box.getLevel().dimension().identifier() + ";" + box.getBlockPos().toShortString(), "", items(box));
    }

    private static void observe(Map<String, JsonObject> seen, ItemStack stack, String state, String holder) {
        String id = id(stack);
        if (!valid(id) || !watchers.containsKey(id)) return;
        JsonObject items = new JsonObject();
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().forEach(item -> add(items, item));
        seen.put(id, packet(id, state, "", holder, items));
    }

    private static void tick(MinecraftServer server) {
        if (watchers.values().stream().allMatch(Set::isEmpty)) return;
        Map<String, JsonObject> seen = new HashMap<>();
        for (var entry : locations.entrySet()) {
            String id = entry.getKey();
            if (watchers.getOrDefault(id, Set.of()).isEmpty()) continue;
            JsonObject old = states.get(id);
            for (ServerLevel level : server.getAllLevels()) {
                BlockPos pos = parseLocation(level, entry.getValue());
                if (pos == null || !level.hasChunkAt(pos)) continue;
                if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box && id.equals(id(box))) seen.put(id, state(box));
                else if (old != null && "placed".equals(old.get("state").getAsString()))
                    seen.put(id, packet(id, "unknown", "", "", old.getAsJsonObject("items").deepCopy()));
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item && !item.isRemoved()) observe(seen, item.getItem(), "dropped", "");
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) observe(seen, inventory.getItem(i), "carried", player.getGameProfile().name());
            observe(seen, player.containerMenu.getCarried(), "carried", player.getGameProfile().name());
            for (var slot : player.containerMenu.slots) {
                if (slot.container != inventory) observe(seen, slot.getItem(), "unknown", "");
            }
        }
        for (var entry : states.entrySet()) {
            JsonObject old = entry.getValue();
            if (!seen.containsKey(entry.getKey()) && Set.of("carried", "dropped").contains(old.get("state").getAsString()))
                seen.put(entry.getKey(), packet(entry.getKey(), "unknown", "", "", old.getAsJsonObject("items").deepCopy()));
        }
        seen.values().forEach(state -> publish(server, state));
    }

    private static void publish(MinecraftServer server, JsonObject state) {
        String id = state.get("id").getAsString();
        if (states.containsKey(id) && "lost".equals(states.get(id).get("state").getAsString())) return;
        if ("placed".equals(state.get("state").getAsString())) locations.put(id, state.get("location").getAsString());
        if (state.equals(states.put(id, state))) return;
        if ("lost".equals(state.get("state").getAsString())) saveLosses();
        for (UUID uuid : watchers.getOrDefault(id, Set.of())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) send(player, state);
        }
    }

    private static void send(ServerPlayer player, JsonObject state) {
        ServerPlayNetworking.send(player, new BmlPackets.BmlPayload(state.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
