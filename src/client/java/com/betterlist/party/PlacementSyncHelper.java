package com.betterlist.party;

import com.betterlist.network.BmlClientNetworking;
import com.betterlist.network.BmlPackets;
import com.google.gson.JsonObject;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Handles syncing Litematica schematic placement/orientation within a party.
 *
 * CO JEST SYNCHRONIZOWANE:
 *   - Nazwa pliku .litematic i nazwa placement
 *   - Origin coordinates (X, Y, Z)
 *   - Rotation i Mirror (jako stringi enum)
 *
 * CO NIE JEST SYNCHRONIZOWANE:
 *   - The .litematic file itself (too large, must be shared manually)
 *
 * On receipt: if the receiver has the file locally, we show instructions to load it.
 * Auto-creating a placement via the API is unstable across Litematica versions,
 * so we limit ourselves to a notification with ready-to-copy data.
 */
@Environment(EnvType.CLIENT)
public class PlacementSyncHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("BML-PlacementSync");

    /**
     * Everything the party syncs about a placement. Autosync compares this, so rotating or
     * mirroring is detected as a change too, not only moving.
     */
    public static String stateKey(SchematicPlacement p) {
        net.minecraft.core.BlockPos o = p.getOrigin();
        return o.getX() + "," + o.getY() + "," + o.getZ() + "|" + p.getRotation().name() + "|" + p.getMirror().name();
    }

    /** Litematica wants a message sink for setRotation/setMirror; route it to the log. */
    private static final fi.dy.masa.malilib.gui.interfaces.IMessageConsumer LOG_MESSAGES =
            new fi.dy.masa.malilib.gui.interfaces.IMessageConsumer() {
                @Override
                public void addMessage(fi.dy.masa.malilib.gui.Message.MessageType type, String key, Object... args) {
                    LOGGER.info("[BML-PlacementSync] Litematica: {} {}", type, key);
                }

                @Override
                public void addMessage(fi.dy.masa.malilib.gui.Message.MessageType type, int lifeTime, String key, Object... args) {
                    addMessage(type, key, args);
                }
            };

    /**
     * Sends data about ALL active schematics to the party.
     * Called from the Party GUI after clicking "Sync Schematics".
     */
    public static void sendAllPlacements() {
        if (!PartyManager.isInParty()) return;

        SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
        List<SchematicPlacement> allPlacements = mgr.getAllSchematicsPlacements();

        if (allPlacements == null || allPlacements.isEmpty()) {
            LOGGER.info("[BML-PlacementSync] No placements to sync.");
            return;
        }

        int sent = 0;
        for (SchematicPlacement placement : allPlacements) {
            if (!placement.isEnabled()) continue;
            sendSinglePlacement(placement, null);
            sent++;
        }
        LOGGER.info("[BML-PlacementSync] Sent {} placements to party.", sent);

        // Notify our own chat.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                Component.literal("§a" + com.betterlist.util.BmlLang.tr("bml.sync.sent_placements", sent)));
        }
    }

    public static void sendSelectedPlacements(java.util.Collection<String> selectedNames) {
        if (!PartyManager.isInParty() || selectedNames == null || selectedNames.isEmpty()) return;

        SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
        List<SchematicPlacement> all = mgr.getAllSchematicsPlacements();
        if (all == null) return;

        int sent = 0;
        for (SchematicPlacement placement : all) {
            if (!selectedNames.contains(placement.getName())) continue;
            sendSinglePlacement(placement, null);
            sent++;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                Component.literal("§a" + com.betterlist.util.BmlLang.tr("bml.party.project_sent", sent)));
        }
        LOGGER.info("[BML-PlacementSync] Sent {} selected placement(s) as the party project.", sent);
    }

    /** Autosync: sends only the named placements (the ones that moved locally). */
    public static void sendChangedPlacements(java.util.Collection<String> names) {
        if (!PartyManager.isInParty() || names == null || names.isEmpty()) return;
        List<SchematicPlacement> all = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        if (all == null) return;
        int sent = 0;
        for (SchematicPlacement placement : all) {
            if (!placement.isEnabled() || !names.contains(placement.getName())) continue;
            sendSinglePlacement(placement, null);
            sent++;
        }
        Minecraft mc = Minecraft.getInstance();
        if (sent > 0 && mc.player != null) {
            mc.player.sendSystemMessage(
                Component.literal("§a" + com.betterlist.util.BmlLang.tr("bml.sync.sent_placements", sent)));
        }
    }

    private static void reportMissingSchematic(String schematicName) {
        if (!PartyManager.isInParty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("type", BmlPackets.SYNC_SCHEMATIC_MISSING);
        payload.addProperty("partyId", PartyManager.getPartyId().toString());
        payload.addProperty("player", mc.player.getGameProfile().name());
        payload.addProperty("schematicName", schematicName);
        BmlClientNetworking.sendRaw(payload);
    }

    private static void sendSinglePlacement(SchematicPlacement placement, String targetNick) {
        try {
            net.minecraft.core.BlockPos origin = placement.getOrigin();
            String schematicFileName = getSchematicFileName(placement);

            JsonObject payload = new JsonObject();
            payload.addProperty("type", BmlPackets.SYNC_PLACEMENT);
            payload.addProperty("partyId", PartyManager.getPartyId().toString());
            payload.addProperty("placementName", placement.getName());
            payload.addProperty("schematicName", schematicFileName);
            payload.addProperty("originX", origin.getX());
            payload.addProperty("originY", origin.getY());
            payload.addProperty("originZ", origin.getZ());
            payload.addProperty("rotation", placement.getRotation().name());
            payload.addProperty("mirror", placement.getMirror().name());
            
            if (targetNick != null) {
                payload.addProperty("targetNick", targetNick);
            }

            BmlClientNetworking.sendRaw(payload);
        } catch (Exception e) {
            LOGGER.error("[BML-PlacementSync] Error building placement packet for '{}': {}",
                placement.getName(), e.getMessage());
        }
    }

    /**
     * Prosi konkretnego gracza o wyslanie swoich placementow.
     */
    public static void requestPlacementsFromPlayer(String targetNick) {
        if (!PartyManager.isInParty()) return;
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", BmlPackets.SYNC_PLACEMENT_REQUEST);
            payload.addProperty("partyId", PartyManager.getPartyId().toString());
            payload.addProperty("targetNick", targetNick);
            BmlClientNetworking.sendRaw(payload);
            LOGGER.info("[BML-PlacementSync] Requested placements from player {}", targetNick);
        } catch (Exception e) {
            LOGGER.error("[BML-PlacementSync] Error requesting placements: {}", e.getMessage());
        }
    }

    /**
     * Zostalismy poproszeni o odeslanie naszych placementow do "requestingNick".
     */
    public static void handlePlacementRequest(JsonObject json) {
        if (!json.has("requestingNick")) return;
        String requestingNick = json.get("requestingNick").getAsString();
        
        SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
        List<SchematicPlacement> allPlacements = mgr.getAllSchematicsPlacements();

        if (allPlacements != null) {
            for (SchematicPlacement placement : allPlacements) {
                if (!placement.isEnabled()) continue;
                // Send directly to that player
                sendSinglePlacement(placement, requestingNick);
            }
        }
        // Also push full state (stored + checks) so the joining player doesn't have to
        // scan chests from scratch.
        BmlClientNetworking.sendFullStateTo(requestingNick);
        LOGGER.info("[BML-PlacementSync] Responded to placement request from {}", requestingNick);
    }

    /**
     * Stosuje odebrany pakiet SYNC_PLACEMENT.
     * Called by BmlClientNetworking on the game thread.
     *
     * Instead of auto-creating a placement via the API (unstable across Litematica versions),
     * we show the player clear instructions with ready-to-use data.
     */
    public static void applyPlacement(JsonObject json) {
        String schematicName  = json.get("schematicName").getAsString();
        String placementName  = json.get("placementName").getAsString();
        int ox = json.get("originX").getAsInt();
        int oy = json.get("originY").getAsInt();
        int oz = json.get("originZ").getAsInt();
        String rotation = json.has("rotation") ? json.get("rotation").getAsString() : "NONE";
        String mirror   = json.has("mirror")   ? json.get("mirror").getAsString()   : "NONE";

        LOGGER.info("[BML-PlacementSync] Received placement '{}' (file: {}) at {},{},{} rot={} mir={}",
            placementName, schematicName, ox, oy, oz, rotation, mirror);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Check whether the player has the schematic file locally.
        java.io.File file = findSchematicFile(schematicName);

        if (file == null) {
            // File missing — ask the user to copy it manually.
            mc.player.sendSystemMessage(
                Component.literal("§c" + com.betterlist.util.BmlLang.tr("bml.sync.missing_schematic", schematicName)));
            reportMissingSchematic(schematicName);

        } else {
            try {
                // Remove an existing placement with the same name (avoid duplicates).
                SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
                List<SchematicPlacement> existing = mgr.getAllSchematicsPlacements();
                for (SchematicPlacement p : existing) {
                    if (p.getName().equals(placementName)) {
                        mgr.removeSchematicPlacement(p);
                        break;
                    }
                }

                fi.dy.masa.litematica.schematic.LitematicaSchematic schematic =
                    fi.dy.masa.litematica.schematic.LitematicaSchematic.createFromFile(file.getParentFile().toPath(), file.getName());
                if (schematic != null) {
                    net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(ox, oy, oz);
                    SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, placementName, true, true);
                    // Rotation and mirror were always sent but never applied, so a rotated
                    // placement showed up unrotated for teammates.
                    placement.setRotation(parseEnum(net.minecraft.world.level.block.Rotation.class, rotation,
                            net.minecraft.world.level.block.Rotation.NONE), LOG_MESSAGES);
                    placement.setMirror(parseEnum(net.minecraft.world.level.block.Mirror.class, mirror,
                            net.minecraft.world.level.block.Mirror.NONE), LOG_MESSAGES);

                    fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);
                    // Mark as in sync so our autosync doesn't echo it back to the sender.
                    com.betterlist.ExampleModClient.notePlacementFromParty(placementName, stateKey(placement));
                    
                    mc.player.sendSystemMessage(
                        Component.literal("§a" + com.betterlist.util.BmlLang.tr("bml.sync.auto_loaded", placementName)));
                } else {
                     LOGGER.error("Schematic returned null during createFromFile.");
                }
            } catch (Exception e) {
                LOGGER.error("[BML-PlacementSync] Auto-placement error: {}", e.getMessage());
                mc.player.sendSystemMessage(
                    Component.literal("§c" + com.betterlist.util.BmlLang.tr("bml.sync.load_error", schematicName)));
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String name, T fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.warn("[BML-PlacementSync] Unknown {} '{}', using {}", type.getSimpleName(), name, fallback);
            return fallback;
        }
    }

    /** Returns the schematic file name (basename with extension only). */
    private static String getSchematicFileName(SchematicPlacement placement) {
        try {
            java.nio.file.Path schematicPath = placement.getSchematicFile();
            if (schematicPath != null) return schematicPath.toFile().getName();
        } catch (Exception ignored) {}
        return placement.getName() + ".litematic";
    }

    /**
     * Szuka pliku .litematic:
     *   1. {gamedir}/schematics/{fileName}
     *   2. {gamedir}/{fileName}
     */
    private static File findSchematicFile(String fileName) {
        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        File candidate = new File(gameDir, "schematics" + File.separator + fileName);
        if (candidate.exists()) return candidate;
        candidate = new File(gameDir, fileName);
        if (candidate.exists()) return candidate;
        return null;
    }
}
