package com.betterlist.input;

import com.betterlist.data.MaterialCacheManager;
import com.betterlist.data.ContainerDataManager;
import com.betterlist.gui.GuiBetterMaterialList;
import com.betterlist.config.ModConfig;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class InputHandler implements IKeybindProvider, IHotkeyCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterList");
    private static final InputHandler INSTANCE = new InputHandler();

    public static InputHandler getInstance() {
        return INSTANCE;
    }

    public void registerKeyCallbacks() {
        // Register listeners for all our keybinds.
        ModConfig.OPEN_GUI.getKeybind().setCallback(this);
        ModConfig.RELOAD_LIST.getKeybind().setCallback(this);
        ModConfig.OPEN_CONFIG.getKeybind().setCallback(this);
        ModConfig.OPEN_PARTY.getKeybind().setCallback(this);
        ModConfig.OPEN_CHESTS.getKeybind().setCallback(this);
        ModConfig.TOGGLE_HIGHLIGHT.getKeybind().setCallback(this);
        ModConfig.TOGGLE_HUD.getKeybind().setCallback(this);
        ModConfig.HUD_SCROLL_FWD.getKeybind().setCallback(this);
        ModConfig.HUD_SCROLL_BACK.getKeybind().setCallback(this);
    }

    public boolean onKeyAction(KeyAction action, IKeybind key) {

        // --- 1. Otwieranie Menu Konfiguracji ---
        if (key == ModConfig.OPEN_CONFIG.getKeybind()) {
            GuiBase.openGui(new com.betterlist.gui.GuiConfigs());
            return true;
        }

        if (key == ModConfig.OPEN_PARTY.getKeybind()) {
            GuiBase.openGui(new com.betterlist.gui.GuiParty());
            return true;
        }

        if (key == ModConfig.OPEN_CHESTS.getKeybind()) {
            GuiBase.openGui(new com.betterlist.gui.GuiBmlChests(buildPlacementLabel(
                    DataManager.getSchematicPlacementManager().getAllSchematicsPlacements())));
            return true;
        }

        if (key == ModConfig.TOGGLE_HIGHLIGHT.getKeybind()) {
            boolean on = com.betterlist.data.ChestHighlightManager.toggleAll();
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        on ? "§a" + com.betterlist.util.BmlLang.tr("bml.highlight.on")
                           : "§7" + com.betterlist.util.BmlLang.tr("bml.highlight.off")));
            }
            return true;
        }

        if (key == ModConfig.HUD_SCROLL_FWD.getKeybind()) {
            com.betterlist.data.HudOverlayManager.scrollForward();
            return true;
        }

        if (key == ModConfig.HUD_SCROLL_BACK.getKeybind()) {
            com.betterlist.data.HudOverlayManager.scrollBack();
            return true;
        }

        if (key == ModConfig.TOGGLE_HUD.getKeybind()) {
            boolean on = com.betterlist.data.HudOverlayManager.toggle();
            com.betterlist.party.FocusManager.save(); // persist the HUD flag across relogs
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        on ? "§a" + com.betterlist.util.BmlLang.tr("bml.hud.on")
                           : "§7" + com.betterlist.util.BmlLang.tr("bml.hud.off")));
            }
            return true;
        }

        // --- 2. Reload ONLY while the GUI is open ---
        if (key == ModConfig.RELOAD_LIST.getKeybind()) {
            // If the BML screen is NOT open, ignore this key entirely.
            // This keeps reload from firing in the background during normal play.
            if (!(Minecraft.getInstance().screen instanceof GuiBetterMaterialList)) {
                return false;
            }

            GuiBetterMaterialList currentGui = (GuiBetterMaterialList) Minecraft.getInstance().screen;

            // If the player clicked the search bar and wants to type e.g. "dirt",
            // pass the "r" through to the bar and do NOT reload the list.
            if (currentGui.isSearchFocused()) {
                return false;
            }

            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            List<SchematicPlacement> placements = manager.getAllSchematicsPlacements();

            if (placements != null && !placements.isEmpty()) {
                for (SchematicPlacement p : placements) {
                    if (p.isEnabled() && p.getMaterialList() != null) {
                        p.getMaterialList().setCompletionListener(currentGui);
                        p.getMaterialList().reCreateMaterialList();
                    }
                }
            }
            return true;
        }

        // --- 3. Open the main GUI ---
        if (key == ModConfig.OPEN_GUI.getKeybind()) {
            openMaterialList();
            return true;
        }

        // Key isn't ours.
        return false;
    }

    /**
     * Opens the main material-list GUI (fresh data or cache). Public because it's also
     * used by the "back" buttons in sub-screens (Config / Chests / Party).
     */
    public static void openMaterialList() {
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        List<SchematicPlacement> placements = manager.getAllSchematicsPlacements();

        List<MaterialListEntry> entriesToShow = collectEntriesWithCacheFallback(placements);
        String placementLabel = (placements != null && !placements.isEmpty())
                ? buildPlacementLabel(placements) : com.betterlist.util.BmlLang.tr("bml.list.no_schematic");
        boolean isCached = lastCollectWasCached;

        if (entriesToShow != null && !entriesToShow.isEmpty() && Minecraft.getInstance().player != null) {
            applyAvailableCounts(entriesToShow, Minecraft.getInstance().player);
        }

        GuiBase.openGui(new GuiBetterMaterialList(placementLabel, entriesToShow, isCached, placements));
    }

    // Set by collectEntriesWithCacheFallback: whether the last result came from cache.
    private static boolean lastCollectWasCached = false;

    // Last non-empty list; survives an in-place dimension swap when Litematica's live data is
    // briefly empty, so the GUI list and HUD don't blank out. Cleared on disconnect.
    private static List<MaterialListEntry> lastGoodEntries = null;

    /**
     * Material-list entries for the given placements, falling back to the on-disk cache and
     * then the in-memory last-good snapshot when Litematica's live data is empty (relog /
     * dimension swap). Fresh data is saved to the cache. Shared by the main GUI and the HUD.
     */
    public static List<MaterialListEntry> collectEntriesWithCacheFallback(List<SchematicPlacement> placements) {
        lastCollectWasCached = false;

        if (placements != null && !placements.isEmpty()) {
            List<MaterialListEntry> fresh = collectMaterialsFromPlacements(placements);
            String cacheKey = MaterialCacheManager.getCacheKey(placements);
            if (!fresh.isEmpty()) {
                MaterialCacheManager.saveCache(cacheKey, fresh);
                lastGoodEntries = copyEntries(fresh);
                return fresh;
            }
            List<MaterialListEntry> cached = MaterialCacheManager.loadCache(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                lastCollectWasCached = true;
                lastGoodEntries = copyEntries(cached);
                return cached;
            }
        }

        if (lastGoodEntries != null && !lastGoodEntries.isEmpty()) {
            lastCollectWasCached = true;
            return copyEntries(lastGoodEntries);
        }
        return new ArrayList<>();
    }

    // Deep copy so a stored snapshot can't be mutated by updateAvailableCounts.
    private static List<MaterialListEntry> copyEntries(List<MaterialListEntry> src) {
        List<MaterialListEntry> out = new ArrayList<>(src.size());
        for (MaterialListEntry e : src) {
            out.add(new MaterialListEntry(e.getStack().copy(),
                    e.getCountTotal(), e.getCountMissing(), e.getCountMismatched(), e.getCountAvailable()));
        }
        return out;
    }

    public static void clearLastGoodEntries() {
        lastGoodEntries = null;
    }

    // Quiet recounts we scheduled ourselves. Tracked individually so cancelling never touches
    // a count task the player started from Litematica's own material list GUI.
    private static final List<fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement>
            quietRecounts = new ArrayList<>();
    private static long quietRecountScheduledAt = 0L;
    // A recount that hasn't finished in this long is waiting for chunks that are not coming.
    private static final long QUIET_RECOUNT_TIMEOUT_MS = 30_000L;

    /**
     * A placement count that keeps itself off Litematica's info HUD.
     *
     * {@code TaskProcessChunkBase.init} registers every count task as an info-HUD renderer.
     * For an automatic background recount that meant "Material list … chunks remaining"
     * blinking on screen every 10 seconds — exactly the noise the quiet path exists to avoid.
     * Unregistering right after init leaves the counting itself untouched.
     */
    private static final class QuietCountTask
            extends fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement {

        QuietCountTask(SchematicPlacement placement, MaterialListBase materialList, boolean ignoreState) {
            super(placement, materialList, ignoreState);
        }

        @Override
        public void init() {
            super.init();
            fi.dy.masa.litematica.render.infohud.InfoHud.getInstance()
                    .removeInfoHudRenderer(this, false);
        }
    }

    public static void scheduleQuietRecount(List<SchematicPlacement> placements) {
        Minecraft mc = Minecraft.getInstance();
        if (placements == null || placements.isEmpty() || mc.player == null || mc.level == null) return;
        fi.dy.masa.litematica.scheduler.TaskScheduler scheduler =
                fi.dy.masa.litematica.scheduler.TaskScheduler.getInstanceClient();
        // Our own tasks are tracked by hand: TaskScheduler.hasTask compares getClass().equals,
        // so it does NOT see a subclass, and relying on it alone would let quiet recounts pile
        // up every 10 seconds. It still answers for a count the player started manually.
        if (!quietRecounts.isEmpty()) return;
        if (scheduler.hasTask(fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement.class)) return;
        boolean ignoreState =
                fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_IGNORE_STATE.getBooleanValue();
        for (SchematicPlacement p : placements) {
            if (!p.isEnabled()) continue;
            MaterialListBase mlb = p.getMaterialList();
            if (mlb == null) continue;
            net.minecraft.core.BlockPos origin = p.getOrigin();
            if (origin == null || !mc.level.hasChunkAt(origin)) continue;
            QuietCountTask task = new QuietCountTask(p, mlb, ignoreState);
            scheduler.scheduleTask(task, 20);
            quietRecounts.add(task);
        }
        if (!quietRecounts.isEmpty()) quietRecountScheduledAt = System.currentTimeMillis();
    }

    /**
     * Cancels the quiet recounts we scheduled, and with them their Litematica info-HUD line.
     *
     * {@code TaskScheduler.removeTask} calls the task's {@code stop()}, and
     * {@code TaskCountBlocksBase.onStop()} unregisters itself from the InfoHud unconditionally.
     * That is the ONLY thing that clears the "Material list … chunks remaining" overlay —
     * nothing in Litematica removes it on its own, so a task that never finishes leaves the
     * text on screen forever.
     */
    public static void cancelQuietRecounts() {
        if (quietRecounts.isEmpty()) return;
        fi.dy.masa.litematica.scheduler.TaskScheduler scheduler =
                fi.dy.masa.litematica.scheduler.TaskScheduler.getInstanceClient();
        for (var task : quietRecounts) scheduler.removeTask(task);
        quietRecounts.clear();
    }

    /**
     * Retires quiet recounts that are done or will never finish.
     *
     * Two ways one gets stuck: the placement it counts is deleted or disabled (its chunks stop
     * being processable), or the player walks away and the chunks unload. Both leave the task
     * parked in the scheduler — which also means the {@code hasTask} guard above silently
     * blocks every future recount, so the list quietly stops updating as well.
     */
    public static void tickQuietRecounts() {
        if (quietRecounts.isEmpty()) return;

        fi.dy.masa.litematica.scheduler.TaskScheduler scheduler =
                fi.dy.masa.litematica.scheduler.TaskScheduler.getInstanceClient();
        // A task the scheduler already dropped finished normally and cleaned up after itself.
        var running = scheduler.getAllTasks();
        quietRecounts.removeIf(task -> !running.contains(task));
        if (quietRecounts.isEmpty()) return;

        if (!hasEnabledPlacement()
                || System.currentTimeMillis() - quietRecountScheduledAt > QUIET_RECOUNT_TIMEOUT_MS) {
            cancelQuietRecounts();
        }
    }

    /**
     * Clears every pending placement count, including ones we did not schedule.
     *
     * Only safe to call when no placement is enabled: a count task always belongs to a
     * placement, so with none left every such task is orphaned by definition and its info-HUD
     * line can never go away on its own. This is what catches a stuck count started by the
     * ⟳ button or from Litematica's own material list GUI, since those go through
     * {@code reCreateMaterialList} and never pass through our scheduler bookkeeping.
     */
    public static void cancelOrphanedRecounts() {
        if (hasEnabledPlacement()) return;

        fi.dy.masa.litematica.scheduler.TaskScheduler scheduler =
                fi.dy.masa.litematica.scheduler.TaskScheduler.getInstanceClient();
        for (var task : scheduler.getAllTasks()) {
            if (task instanceof fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement) {
                scheduler.removeTask(task);
            }
        }
        quietRecounts.clear();
    }

    private static boolean hasEnabledPlacement() {
        try {
            List<SchematicPlacement> placements = fi.dy.masa.litematica.data.DataManager
                    .getSchematicPlacementManager().getAllSchematicsPlacements();
            if (placements == null) return false;
            for (SchematicPlacement p : placements) {
                if (p.isEnabled()) return true;
            }
        } catch (Exception ignored) {
            // Litematica not ready (or mid-teardown) — treat as "nothing to count".
        }
        return false;
    }

    public static void applyAvailableCounts(List<MaterialListEntry> entries, net.minecraft.world.entity.player.Player player) {
        if (entries == null) return;
        if (com.betterlist.config.ModConfig.COUNT_PLAYER_INVENTORY && player != null) {
            fi.dy.masa.litematica.materials.MaterialListUtils.updateAvailableCounts(entries, player);
        } else {
            for (MaterialListEntry entry : entries) entry.setCountAvailable(0);
        }
        addCachedContainerItems(entries);
    }

    public static void addCachedContainerItems(List<MaterialListEntry> entries) {
        Map<String, Integer> containerItems = ContainerDataManager.getTotalItems();
        if (!containerItems.isEmpty() && entries != null) {
            for (MaterialListEntry entry : entries) {
                Identifier id = BuiltInRegistries.ITEM.getKey(entry.getStack().getItem());
                String itemKey = id.toString();
                Integer containerCount = containerItems.get(itemKey);
                if (containerCount != null && containerCount > 0) {
                    entry.setCountAvailable(entry.getCountAvailable() + containerCount);
                }
            }
        }
    }

    /**
     * Collects materials using Litematica's already-computed data.
     * First tries DataManager.getMaterialList() (global),
     * then falls back to per-placement material lists.
     */
    public static List<MaterialListEntry> collectMaterialsFromPlacements(List<SchematicPlacement> placements) {
        // Strategy 1: Try getting the global material list from DataManager
        MaterialListBase globalList = DataManager.getMaterialList();
        if (globalList != null) {
            List<MaterialListEntry> globalEntries = globalList.getMaterialsAll();
            LOGGER.info("[BML] Got global material list with {} entries",
                    globalEntries != null ? globalEntries.size() : 0);
            if (globalEntries != null && !globalEntries.isEmpty()) {
                return new ArrayList<>(globalEntries);
            }
        }

        // Strategy 2: Try getting per-placement material lists
        LOGGER.info("[BML] Global list empty/null, trying per-placement lists...");
        Map<String, int[]> merged = new HashMap<>();
        Map<String, ItemStack> stacks = new HashMap<>();

        for (SchematicPlacement placement : placements) {
            if (!placement.isEnabled()) {
                continue;
            }
            try {
                MaterialListBase mlb = placement.getMaterialList();
                if (mlb == null) {
                    LOGGER.info("[BML] Placement '{}' has no material list", placement.getName());
                    continue;
                }
                List<MaterialListEntry> entries = mlb.getMaterialsAll();
                LOGGER.info("[BML] Placement '{}' material list: {} entries",
                        placement.getName(), entries != null ? entries.size() : 0);

                if (entries != null) {
                    for (MaterialListEntry entry : entries) {
                        Identifier id = BuiltInRegistries.ITEM.getKey(entry.getStack().getItem());
                        String key = id.toString();

                        stacks.putIfAbsent(key, entry.getStack().copy());

                        int[] counts = merged.computeIfAbsent(key, k -> new int[] { 0, 0, 0, 0 });
                        counts[0] += entry.getCountTotal();
                        counts[1] += entry.getCountMissing();
                        counts[2] += entry.getCountMismatched();
                        counts[3] += entry.getCountAvailable();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[BML] Error reading placement '{}': {}", placement.getName(), e.getMessage(), e);
            }
        }

        List<MaterialListEntry> result = new ArrayList<>();
        for (Map.Entry<String, int[]> e : merged.entrySet()) {
            ItemStack stack = stacks.get(e.getKey());
            int[] counts = e.getValue();
            if (stack != null && counts[0] > 0) {
                result.add(new MaterialListEntry(stack.copy(), counts[0], counts[1], counts[2], counts[3]));
            }
        }

        LOGGER.info("[BML] Per-placement collection returned {} entries", result.size());
        return result;
    }

    /**
     * Builds a human-readable label from placement names.
     */
    private static String buildPlacementLabel(List<SchematicPlacement> placements) {
        if (placements.size() == 1) {
            return placements.get(0).getName();
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (SchematicPlacement p : placements) {
            if (!p.isEnabled())
                continue;
            if (count > 0)
                sb.append(", ");
            sb.append(p.getName());
            count++;
            if (count >= 3) {
                int remaining = placements.size() - 3;
                if (remaining > 0) {
                    sb.append(" (+").append(remaining).append(" more)");
                }
                break;
            }
        }
        return sb.toString();
    }

    public void addKeysToMap(IKeybindManager manager) {
        Iterator var2 = ModConfig.HOTKEYS.iterator();
        while (var2.hasNext()) {
            IHotkey hotkey = (IHotkey) var2.next();
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("betterlist", "Better List", ModConfig.HOTKEYS);
    }
}