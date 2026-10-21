package dev.bedrockadder.paper;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Makes ItemsAdder furniture visible on Bedrock.
 *
 * <p>ItemsAdder draws furniture with {@code item_display} entities, which Bedrock does not have. For every
 * display that shows an ItemsAdder item, this keeps an invisible marker armor stand wearing the same item,
 * shown only to Bedrock players. Geyser renders the item on the stand's head through the item's attachable,
 * whose "furniture" pose (see the converter) draws the model like the display would.
 */
final class FurnitureMirror implements Listener {

    private static final String TAG = "bedrockadder_furniture_mirror";
    private static final long SYNC_TICKS = 10;
    /** Displays get their item a moment after spawning; keep checking new ones for this many syncs. */
    private static final int PENDING_ATTEMPTS = 6;

    private final BedrockAdderPlugin plugin;
    private final BedrockPlayers bedrock;
    private final float yawOffset;
    private final double yOffset;
    private final Map<UUID, ArmorStand> mirrors = new HashMap<>();
    private final Map<UUID, Integer> pending = new HashMap<>();
    private BukkitTask task;

    FurnitureMirror(BedrockAdderPlugin plugin, BedrockPlayers bedrock, float yawOffset, double yOffset) {
        this.plugin = plugin;
        this.bedrock = bedrock;
        this.yawOffset = yawOffset;
        this.yOffset = yOffset;
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove(); // left over from a crash; mirrors are never saved, but be safe
                } else if (entity instanceof ItemDisplay) {
                    pending.put(entity.getUniqueId(), 0);
                }
            }
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sync, 1, SYNC_TICKS);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        HandlerList.unregisterAll(this);
        mirrors.values().forEach(Entity::remove);
        mirrors.clear();
        pending.clear();
    }

    int count() {
        return mirrors.size();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ItemDisplay) {
            pending.put(event.getEntity().getUniqueId(), 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof ItemDisplay) {
            pending.remove(event.getEntity().getUniqueId());
            ArmorStand mirror = mirrors.remove(event.getEntity().getUniqueId());
            if (mirror != null) {
                mirror.remove();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Floodgate may need a tick to register the session.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && bedrock.isBedrock(player)) {
                for (ArmorStand mirror : mirrors.values()) {
                    player.showEntity(plugin, mirror);
                }
            }
        }, 5);
    }

    private void sync() {
        Iterator<Map.Entry<UUID, Integer>> pendingIterator = pending.entrySet().iterator();
        while (pendingIterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = pendingIterator.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
                pendingIterator.remove();
            } else if (isItemsAdderDisplay(display)) {
                createMirror(display);
                pendingIterator.remove();
            } else if (entry.getValue() >= PENDING_ATTEMPTS) {
                pendingIterator.remove();
            } else {
                entry.setValue(entry.getValue() + 1);
            }
        }

        Iterator<Map.Entry<UUID, ArmorStand>> iterator = mirrors.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArmorStand> entry = iterator.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            ArmorStand mirror = entry.getValue();
            if (!(entity instanceof ItemDisplay display) || !display.isValid() || !mirror.isValid()) {
                mirror.remove();
                iterator.remove();
                continue;
            }
            update(display, mirror);
        }
    }

    private static boolean isItemsAdderDisplay(ItemDisplay display) {
        ItemStack item = display.getItemStack();
        return item != null && !item.getType().isAir() && CustomStack.byItemStack(item) != null;
    }

    private void createMirror(ItemDisplay display) {
        if (mirrors.containsKey(display.getUniqueId())) {
            return;
        }
        ArmorStand mirror = display.getWorld().spawn(target(display), ArmorStand.class, stand -> {
            stand.setVisibleByDefault(false); // Java players never see it
            stand.setPersistent(false);       // never saved with the chunk
            stand.addScoreboardTag(TAG);
            stand.setInvisible(true);
            stand.setMarker(true);            // no hitbox: clicks reach ItemsAdder's own hitbox entities
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setBasePlate(false);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                try {
                    stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                } catch (IllegalArgumentException ignored) {
                    // slot that armor stands do not have
                }
            }
        });
        mirrors.put(display.getUniqueId(), mirror);
        update(display, mirror);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (bedrock.isBedrock(player)) {
                player.showEntity(plugin, mirror);
            }
        }
    }

    private void update(ItemDisplay display, ArmorStand mirror) {
        ItemStack item = display.getItemStack();
        if (!Objects.equals(mirror.getEquipment().getHelmet(), item)) {
            mirror.getEquipment().setHelmet(item);
        }
        float scale = display.getTransformation().getScale().y();
        AttributeInstance scaleAttribute = mirror.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null && Math.abs(scaleAttribute.getBaseValue() - scale) > 0.001) {
            scaleAttribute.setBaseValue(Math.max(0.0625, Math.min(16, scale)));
        }
        Location target = target(display);
        Location current = mirror.getLocation();
        if (current.distanceSquared(target) > 1.0E-6 || Math.abs(current.getYaw() - target.getYaw()) > 0.01f) {
            mirror.teleport(target);
        }
    }

    /**
     * Where the stand's feet go: the furniture pose draws the model centre half a block (times the stand's
     * scale) above the feet, and an item_display draws it at its position plus the transformation translation.
     */
    private Location target(ItemDisplay display) {
        Location location = display.getLocation();
        Transformation transformation = display.getTransformation();
        // The translation is in the display's local frame, which is turned by the entity's yaw.
        Vector3f translation = new Vector3f(transformation.getTranslation())
                .rotateY((float) Math.toRadians(-location.getYaw()));
        Vector3f angles = transformation.getLeftRotation().getEulerAnglesYXZ(new Vector3f());
        float scale = transformation.getScale().y();

        Location target = location.clone().add(translation.x(), translation.y() + yOffset - 0.5 * scale, translation.z());
        target.setYaw(location.getYaw() - (float) Math.toDegrees(angles.y()) + yawOffset);
        target.setPitch(0);
        return target;
    }
}
