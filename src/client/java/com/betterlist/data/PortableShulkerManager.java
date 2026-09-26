package com.betterlist.data;

import com.betterlist.network.BmlClientNetworking;
import com.betterlist.network.BmlPackets;
import com.betterlist.party.PartyManager;
import com.betterlist.util.BmlLang;
import com.betterlist.util.BmlServerId;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public final class PortableShulkerManager {
    private static final Map<String, JsonObject> tracked = new HashMap<>();
    private static final Map<String, String> identities = new HashMap<>();
    private static final Set<String> pending = new HashSet<>();
    private static final Map<String, Long> requests = new HashMap<>();
    private static long requestSequence;
    private static boolean dirty;

    public static boolean isId(String id) {
        if (id == null || !id.startsWith("shulker:")) return false;
        try { return id.equals("shulker:" + UUID.fromString(id.substring(8))); }
        catch (IllegalArgumentException e) { return false; }
    }

    public static String at(String location) { return identities.getOrDefault(location, location); }
    public static boolean isTracked(String id) { return (tracked.containsKey(id) && !isLost(id)) || pending.contains(id); }
    public static boolean isLost(String id) {
        return tracked.containsKey(id) && "lost".equals(tracked.get(id).get("state").getAsString());
    }
    public static Set<String> ids() {
        Set<String> result = new HashSet<>(tracked.keySet());
        result.removeIf(PortableShulkerManager::isLost);
        return result;
    }
    public static Set<String> lostIds() {
        Set<String> result = new HashSet<>(tracked.keySet());
        result.removeIf(id -> !isLost(id));
        return result;
    }
    public static String lossReason(String id) {
        return isLost(id) ? BmlLang.tr("bml.shulker.lost." + tracked.get(id).get("reason").getAsString()) : "";
    }

    public static String location(String id) {
        JsonObject state = tracked.get(id);
        return state != null && "placed".equals(state.get("state").getAsString())
                ? state.get("location").getAsString() : null;
    }

    public static String label(String id) {
        JsonObject state = tracked.get(id);
        if (state == null) return BmlLang.tr("bml.shulker.unknown");
        String kind = state.get("state").getAsString();
        String holder = state.get("holder").getAsString();
        if (kind.equals("lost")) {
            kind = state.has("lastState") ? state.get("lastState").getAsString() : "dropped";
            holder = state.has("lastHolder") ? state.get("lastHolder").getAsString() : "";
            if (kind.equals("placed")) kind = "unknown";
        }
        if (kind.equals("placed")) return state.get("location").getAsString();
        if (kind.equals("carried") && !holder.isEmpty()) return BmlLang.tr("bml.shulker.carried", holder);
        return BmlLang.tr("bml.shulker." + (kind.equals("dropped") ? "dropped" : "unknown"));
    }

    public static Map<String, Integer> contents(String id) {
        Map<String, Integer> result = new HashMap<>();
        JsonObject state = tracked.get(id);
        if (state != null) state.getAsJsonObject("items").entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsInt()));
        return result;
    }

    public static void addTotals(Map<String, Integer> totals) {
        ids().forEach(id -> contents(id).forEach((item, count) -> totals.merge(item, count, Integer::sum)));
    }

    public static String itemId(net.minecraft.world.item.ItemStack stack) {
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        String id = tag.getStringOr("betterlist:shulker_id", "");
        if (!isId(id)) id = tag.getCompoundOrEmpty("PublicBukkitValues").getStringOr("betterlist:shulker_id", "");
        if (!isId(id)) {
            var blockData = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
            if (blockData != null) id = blockData.copyTagWithoutId().getCompoundOrEmpty("PublicBukkitValues")
                    .getStringOr("betterlist:shulker_id", "");
        }
        return id;
    }

    public static void removeInventoryDuplicates(java.util.List<fi.dy.masa.litematica.materials.MaterialListEntry> entries,
                                                  net.minecraft.world.Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty() || !isTracked(itemId(stack))) continue;
            var counts = fi.dy.masa.litematica.materials.MaterialListUtils.getStoredItemCounts(stack);
            for (var entry : entries) {
                var type = new fi.dy.masa.malilib.util.data.ItemType(entry.getStack(), true, false);
                entry.setCountAvailable(Math.max(0, entry.getCountAvailable() - counts.getInt(type)));
            }
        }
    }

    public static void identifyOpenShulker() {
        var mc = Minecraft.getInstance();
        var pos = ContainerDataManager.lastInteractedBlockPos;
        if (!BmlClientNetworking.portableShulkersSupported || mc.level == null || pos == null
                || !(mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) return;
        String location = mc.level.dimension().identifier() + ";" + pos.toShortString();
        identities.remove(location);
        bind(location, ContainerDataManager.isContainerMarked(location));
    }

    private static void bind(String location, boolean track) {
        JsonObject request = new JsonObject();
        request.addProperty("type", BmlPackets.SHULKER_BIND);
        request.addProperty("location", location);
        request.addProperty("track", track);
        request.addProperty("request", ++requestSequence);
        requests.put(location, requestSequence);
        if (track) pending.add(location); else pending.remove(location);
        BmlClientNetworking.sendRaw(request);
    }

    public static boolean toggleAt(String id, boolean marked) {
        if (!marked && pending.contains(id)) { bind(id, false); return true; }
        if (isId(id)) { setTracked(id, marked, true); return true; }
        var mc = Minecraft.getInstance();
        var pos = ContainerDataManager.lastInteractedBlockPos;
        if (!marked || !BmlClientNetworking.portableShulkersSupported || mc.level == null || pos == null
                || !(mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) return false;
        bind(mc.level.dimension().identifier() + ";" + pos.toShortString(), true);
        return true;
    }

    public static void accept(JsonObject state) {
        if (!validState(state)) return;
        String id = state.get("id").getAsString();
        String location = state.get("location").getAsString();
        if (isLost(id) && !"lost".equals(state.get("state").getAsString())) return;
        if (state.has("request")) {
            if (!java.util.Objects.equals(requests.get(location), state.get("request").getAsLong())) return;
            requests.remove(location);
        }
        identities.values().removeIf(id::equals);
        if ("placed".equals(state.get("state").getAsString())) identities.put(location, id);
        boolean requested = state.has("track") && state.get("track").getAsBoolean();
        pending.remove(location);
        if (!requested && !tracked.containsKey(id)) return;
        JsonObject clean = state.deepCopy();
        clean.remove("track");
        clean.remove("type");
        clean.remove("v");
        clean.remove("request");
        JsonObject before = tracked.get(id);
        if ("lost".equals(clean.get("state").getAsString()) && before != null && !"lost".equals(before.get("state").getAsString())) {
            clean.addProperty("lastState", before.get("state").getAsString());
            clean.addProperty("lastHolder", before.get("holder").getAsString());
        }
        JsonObject previous = tracked.put(id, clean);
        if (isLost(id) && ChestHighlightManager.isHighlighted(id)) ChestHighlightManager.toggle(id);
        if ("placed".equals(clean.get("state").getAsString())) ContainerDataManager.setContainerMarkedSilent(location, false);
        dirty |= !clean.equals(previous);
        if (requested && previous == null) sync(id, true, clean);
    }

    public static void receiveTracking(JsonObject packet) {
        String id = packet.get("id").getAsString();
        if (!isId(id)) return;
        if (!packet.get("marked").getAsBoolean()) { setTracked(id, false, false); return; }
        JsonObject state = packet.getAsJsonObject("snapshot").deepCopy();
        if (!validState(state) || !id.equals(state.get("id").getAsString())) return;
        if (!tracked.containsKey(id) || "lost".equals(state.get("state").getAsString())) {
            state.addProperty("track", true);
            tracked.put(id, state);
            accept(state);
        }
        subscribe(id, true);
    }

    public static void setTracked(String id, boolean marked, boolean broadcast) {
        if (!isId(id)) return;
        if (marked) {
            identities.entrySet().stream().filter(e -> id.equals(e.getValue())).findFirst()
                    .ifPresent(e -> bind(e.getKey(), true));
            return;
        }
        JsonObject previous = tracked.remove(id);
        if (previous == null) return;
        dirty = true;
        if (ChestHighlightManager.isHighlighted(id)) ChestHighlightManager.toggle(id);
        subscribe(id, false);
        if (broadcast) sync(id, false, previous);
    }

    private static void sync(String id, boolean marked, JsonObject state) {
        if (!PartyManager.isInParty()) return;
        JsonObject packet = new JsonObject();
        packet.addProperty("type", BmlPackets.SYNC_SHULKER_TRACK);
        packet.addProperty("partyId", PartyManager.getPartyId().toString());
        packet.addProperty("id", id);
        packet.addProperty("marked", marked);
        packet.add("snapshot", state.deepCopy());
        BmlClientNetworking.sendRaw(packet);
    }

    private static void subscribe(String id, boolean subscribe) {
        if (!BmlClientNetworking.portableShulkersSupported) return;
        JsonObject packet = new JsonObject();
        packet.addProperty("type", BmlPackets.SHULKER_SUBSCRIBE);
        packet.addProperty("id", id);
        packet.addProperty("subscribe", subscribe);
        if (tracked.containsKey(id)) packet.addProperty("location", tracked.get(id).get("location").getAsString());
        BmlClientNetworking.sendRaw(packet);
    }

    public static void subscribeAll() { new HashSet<>(tracked.keySet()).forEach(id -> subscribe(id, true)); }
    public static JsonObject snapshot() {
        JsonObject result = new JsonObject();
        tracked.forEach((id, state) -> result.add(id, state.deepCopy()));
        return result;
    }

    public static void merge(JsonObject states) {
        states.entrySet().forEach(e -> {
            JsonObject packet = new JsonObject();
            packet.addProperty("id", e.getKey());
            packet.addProperty("marked", true);
            packet.add("snapshot", e.getValue());
            receiveTracking(packet);
        });
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("betterlist_data")
                .resolve(BmlServerId.current() + "_shulkers.json");
    }

    private static boolean validState(JsonObject state) {
        try {
            if (!isId(state.get("id").getAsString())) return false;
            String kind = state.get("state").getAsString();
            if (!Set.of("placed", "carried", "dropped", "unknown", "lost").contains(kind)) return false;
            if (kind.equals("lost") && (!Set.of("fire", "void", "despawn", "destroyed").contains(state.get("reason").getAsString())
                    || state.get("lostAt").getAsLong() <= 0)) return false;
            String location = state.get("location").getAsString();
            if ((!location.isEmpty() || kind.equals("placed")) && !ContainerDataManager.isValidContainerId(location)) return false;
            if (state.get("holder").getAsString().length() > 16) return false;
            for (var entry : state.getAsJsonObject("items").entrySet()) {
                if (Integer.parseInt(entry.getValue().getAsString()) < 0) return false;
            }
            return true;
        } catch (RuntimeException e) { return false; }
    }

    public static void load() {
        clear();
        Path file = file();
        if (!Files.exists(file)) return;
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            saved.entrySet().forEach(e -> {
                if (isId(e.getKey())) {
                    JsonObject state = e.getValue().getAsJsonObject();
                    if (!validState(state) || !e.getKey().equals(state.get("id").getAsString()))
                        throw new IllegalArgumentException("Invalid shulker snapshot");
                    tracked.put(e.getKey(), state);
                    if ("placed".equals(state.get("state").getAsString())) identities.put(state.get("location").getAsString(), e.getKey());
                }
            });
        } catch (Exception e) {
            clear();
            try { Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception backupError) { e.addSuppressed(backupError); }
            com.betterlist.ExampleMod.LOGGER.error("Failed to load tracked shulkers", e);
        }
    }

    public static void flush() {
        if (!dirty) return;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()));
            dirty = false;
        } catch (Exception e) { com.betterlist.ExampleMod.LOGGER.error("Failed to save tracked shulkers", e); }
    }

    public static void clear() { tracked.clear(); identities.clear(); pending.clear(); requests.clear(); dirty = false; }

    public static void unmarkAll() { new HashSet<>(tracked.keySet()).forEach(id -> setTracked(id, false, true)); }
}
