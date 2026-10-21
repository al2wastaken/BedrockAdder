package dev.bedrockadder.paper;

import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

/** Converts whenever ItemsAdder builds its pack; falls back to the pack on disk after startup. */
final class ItemsAdderListener implements Listener {

    /**
     * How long to wait after ItemsAdder's first load before reading its pack from disk. ItemsAdder does not
     * rebuild an unchanged pack, so no pack event comes; if it does, the hash check makes the second run cheap.
     */
    private static final long STARTUP_FALLBACK_TICKS = 20L * 5;

    private final BedrockAdderPlugin plugin;
    private volatile boolean packEventSeen;

    ItemsAdderListener(BedrockAdderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackCompressed(ItemsAdderPackCompressedEvent event) {
        packEventSeen = true;
        if (!plugin.settings().autoConvert()) {
            return;
        }
        // Copy now: entries may be backed by streams that are only valid during the event.
        // The ItemsAdder jar is obfuscated without generics or inner-class metadata, hence the casts and 'var'.
        Map<String, byte[]> files = new HashMap<>();
        for (Object key : event.getEntries().keySet()) {
            var entry = event.getEntry((String) key);
            if (entry != null && !entry.isRemoved()) {
                files.put(entry.getPath(), entry.getBytes());
            }
        }
        if (plugin.getServer().isPrimaryThread()) {
            plugin.conversion().convert(files, false, null);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.conversion().convert(files, false, null));
        }
    }

    @EventHandler
    public void onLoadData(ItemsAdderLoadDataEvent event) {
        if (!"FIRST_LOAD".equals(String.valueOf(event.getCause())) || !plugin.settings().autoConvert()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!packEventSeen) {
                plugin.convertFromDisk(false, null);
            }
        }, STARTUP_FALLBACK_TICKS);
    }
}
