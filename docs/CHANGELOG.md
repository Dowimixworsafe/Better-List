# Changelog

Release notes for Modrinth. Paste the relevant section into the version description when uploading.

---

## 1.4.0+26.2 (Minecraft 26.2)

### ⚠️ Update together

Party members should all move to 1.4.0. The sync protocol changed (version 5), and you will get a one-time in-game warning if someone in your party is on an older version.

On **Bukkit-family servers**, update the **BetterList Integration** plugin to **1.3.0** as well: it is what follows shulker boxes around once they leave their spot. With the old plugin everything else keeps working, shulkers are just tracked by position like a chest. On a Fabric server, the mod itself handles it.

### ✨ New

- **Config from any MaLiLib mod or Mod Menu.** Better List now shows up in the mod list of every MaLiLib config screen (Litematica, MiniHUD, Tweakeroo, …), and has a config button in **Mod Menu** if you use it. Back / ESC returns to the screen you came from.
- **The "open config" hotkey is now unbound by default**, so the mod no longer takes a key just for its settings. If you already had it bound, your binding is kept.
- **Tracked shulker boxes follow their shulker.** Pick one up, carry it, drop it or place it somewhere else, and it stays tracked with its contents, and the chest list shows who is carrying it or that it's lying on the ground. A carried shulker in your own inventory is not counted twice.
- **Lost shulkers are flagged, not silently dropped.** If a tracked shulker burns, falls into the void, despawns or is blown up, it stays on the chest list in red with a short line saying why it's gone. It no longer counts toward **Stored**; remove it with ✖ when you're done.
- **Real block icons in the chest list.** Each tracked container shows what it actually is (chest, barrel, a shulker in its color, and so on), including containers far away and ones a teammate tracked.

### 🐛 Fixed

- **Tracked chests and shulkers no longer untick themselves when you walk away.** Leaving the area made far-away containers look like they had been destroyed, so they were dropped from tracking for the whole party.
- **Joining a party now brings over the leader's tracked chests and checkboxes.** The request was never sent after accepting an invite, and a leader with no schematic loaded never answered it.
- **Hotkey settings are saved.** Options set with a hotkey's settings button (key order, extra keys allowed, exclusive, …) used to reset on restart, so compound keybinds didn't stick.

### 🔧 Under the hood

- Sync protocol version raised to 5 (new shulker-tracking packets; container sync now also carries the block type).

---

## 1.3.0+26.2 — Minecraft 26.2

Same release as 1.3.0, rebuilt for Minecraft 26.2 against MaLiLib 0.29.3 and Litematica 0.28.3. No feature or behaviour differences — see the 1.3.0 notes below for everything that changed.

The Minecraft 26.2 port started from a fork by **@m1kau**, opened as issue #2 by **@Leoruwer**.

---

## 1.3.0 — Minecraft 26.1.2

### ⚠️ Update together

Party members should all move to 1.3.0. This release teaches the mod to forget chests that no longer exist — but someone still on 1.2.0 never forgets them, and their progress sync will push destroyed chests back onto everyone else, where they keep inflating your **Stored** count. You will get a one-time in-game warning if someone in your party is on an older version.

The server plugin does **not** need updating — 1.2.0 works with this release.

### ✨ New

- **Find an item in your chests.** Left-click any row on the material list and every tracked chest holding that item lights up in the world. Search hits get their own pulsing outline, clearly distinct from the manual 💡 highlight, so you can tell at a glance which is which. The item tooltip now also tells you how many chests hold it and how many you have in total.
- Both chest outlines now pulse gently instead of sitting there as a flat box.

### 🐛 Fixed

- **The material list no longer scrambles at larger GUI scales.** Column headers stayed put while the rows shifted, so headers sat over the wrong columns and clicking one sorted by something else. Thanks to **@yqs112358** for finding this and sending the fix.
- **A broken chest is now forgotten.** Tracked chests were remembered by position only, so destroying one left its contents counting toward **Stored** forever — and building a new chest on the same spot gave you one that was already ticked and already "full" of the old items.
- **Double chests no longer lose track of themselves.** Marking a single chest and later extending it into a double could leave it listed as tracked while showing up unticked when you opened it.
- **Chest highlights turn off properly.** Unticking a chest anywhere other than the chest manager left it glowing in the world with no way left to switch it off.
- **Emptying a chest now reaches your teammates.** If someone cleared out a tracked chest, everyone else kept counting the items that were already gone.
- **Litematica's "chunks remaining" overlay no longer sticks around** after you remove a schematic, and no longer blinks on screen every 10 seconds while the list auto-refreshes. A stuck counting task also used to silently block every later refresh, so the list quietly stopped updating.
- The tracking control on chest screens is now a proper checkbox in the title bar instead of a blank button floating above the window.

### 🔧 Under the hood

- Sync protocol version raised to 3. Packet shapes are unchanged; the bump exists so mismatched party members get warned.
