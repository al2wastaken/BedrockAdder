package dev.bedrockadder.paper;

import dev.bedrockadder.common.input.IaBlock;
import dev.bedrockadder.common.input.IaItem;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.FoodComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Snapshots the ItemsAdder registry into plain records. Must run on the server thread. */
final class IaRegistryReader {

    /** Vanilla blocks ItemsAdder uses for "real" custom blocks; Geyser can swap their states. */
    private static final Set<Material> STATE_BLOCKS = EnumSet.of(
            Material.NOTE_BLOCK, Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK,
            Material.MUSHROOM_STEM, Material.CHORUS_PLANT, Material.TRIPWIRE);

    private final Logger logger;

    IaRegistryReader(Logger logger) {
        this.logger = logger;
    }

    List<IaItem> items() {
        List<IaItem> items = new ArrayList<>();
        // Raw Set: the obfuscated ItemsAdder jar has no generic signatures.
        for (Object rawId : CustomStack.getNamespacedIdsInRegistry()) {
            String id = (String) rawId;
            try {
                CustomStack stack = CustomStack.getInstance(id);
                if (stack != null) {
                    items.add(item(id, stack));
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not read ItemsAdder item " + id, e);
            }
        }
        return items;
    }

    private static IaItem item(String id, CustomStack stack) {
        ItemStack item = stack.getItemStack();
        Material type = item.getType();
        ItemMeta meta = item.getItemMeta();
        String material = type.getKey().toString();

        String itemModel = meta.hasItemModel() ? meta.getItemModel().toString() : null;
        if (material.equals(itemModel)) {
            itemModel = null; // the vanilla model is not a custom one
        }
        Integer customModelData = meta.hasCustomModelData() ? meta.getCustomModelData() : null;

        Integer maxStackSize = meta.hasMaxStackSize() && meta.getMaxStackSize() != type.getMaxStackSize()
                ? meta.getMaxStackSize() : null;
        Integer maxDamage = meta instanceof Damageable damageable && damageable.hasMaxDamage()
                && damageable.getMaxDamage() != type.getMaxDurability() ? damageable.getMaxDamage() : null;

        String slot = null;
        String armorAsset = null;
        if (meta.hasEquippable()) {
            EquippableComponent equippable = meta.getEquippable();
            slot = slotName(equippable.getSlot());
            armorAsset = equippable.getModel() == null ? null : equippable.getModel().toString();
        } else if (type.isItem()) {
            slot = slotName(type.getEquipmentSlot());
        }

        IaItem.Food food = null;
        if (meta.hasFood()) {
            FoodComponent component = meta.getFood();
            food = new IaItem.Food(component.getNutrition(), component.getSaturation(), component.canAlwaysEat());
        }

        return new IaItem(id, material, itemModel, customModelData, ChatColor.stripColor(stack.getDisplayName()),
                maxStackSize, maxDamage, slot, armorAsset, armorPoints(meta), food,
                stack.isBlock(), CustomStack.isFurniture(item));
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case FEET -> "feet";
            default -> null;
        };
    }

    private static int armorPoints(ItemMeta meta) {
        if (!meta.hasAttributeModifiers()) {
            return 0;
        }
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.ARMOR);
        double total = 0;
        if (modifiers != null) {
            for (AttributeModifier modifier : modifiers) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    total += modifier.getAmount();
                }
            }
        }
        return (int) Math.round(total);
    }

    /** @param skipped receives ids of blocks that are not state-based (entity/furniture blocks) */
    List<IaBlock> blocks(List<String> skipped) {
        List<IaBlock> blocks = new ArrayList<>();
        for (Object rawId : CustomBlock.getNamespacedIdsInRegistry()) {
            String id = (String) rawId;
            try {
                CustomBlock block = CustomBlock.getInstance(id);
                BlockData data = block == null ? null : block.getBaseBlockData();
                if (data == null || !STATE_BLOCKS.contains(data.getMaterial())) {
                    skipped.add(id);
                    continue;
                }
                blocks.add(new IaBlock(id, data.getAsString(), ChatColor.stripColor(block.getDisplayName()),
                        hardness(block), block.getOriginalLightLevel()));
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not read ItemsAdder block " + id, e);
            }
        }
        return blocks;
    }

    private static float hardness(CustomBlock block) {
        ConfigurationSection section = block.getConfigSectionCopy();
        if (section != null) {
            for (String path : List.of("specific_properties.block.hardness", "behaviours.block.hardness", "block.hardness")) {
                if (section.isSet(path)) {
                    return (float) section.getDouble(path);
                }
            }
        }
        return 1.0f;
    }
}
