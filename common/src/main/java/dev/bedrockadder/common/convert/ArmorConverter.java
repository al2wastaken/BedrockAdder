package dev.bedrockadder.common.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bedrockadder.common.Json;
import dev.bedrockadder.common.ResourceId;
import dev.bedrockadder.common.bedrock.Textures;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Worn armor. Java 1.21.4+ describes armor textures in {@code equipment} assets; Bedrock draws them with
 * an attachable on the vanilla player armor geometry. Both use the same 64x32 armor layer layout.
 */
final class ArmorConverter {

    private record Slot(String geometry, String layerVariable, String javaLayer) {
    }

    private static final Map<String, Slot> SLOTS = Map.of(
            "head", new Slot("geometry.player.armor.helmet", "helmet_layer_visible", "humanoid"),
            "chest", new Slot("geometry.player.armor.chestplate", "chest_layer_visible", "humanoid"),
            "legs", new Slot("geometry.player.armor.leggings", "leg_layer_visible", "humanoid_leggings"),
            "feet", new Slot("geometry.player.armor.boots", "boot_layer_visible", "humanoid"));

    private final ConversionContext ctx;

    ArmorConverter(ConversionContext ctx) {
        this.ctx = ctx;
    }

    void attachable(String bedrockId, String flat, String equipSlot, String assetId) {
        Slot slot = SLOTS.get(equipSlot);
        if (slot == null) {
            return;
        }
        ResourceId asset = ResourceId.parse(assetId);
        JsonObject equipment = ctx.javaPack().json(asset.asset("equipment", ".json"));
        String textureId = layerTexture(equipment, slot.javaLayer());
        if (textureId == null) {
            ctx.log().warn("Armor " + bedrockId + ": equipment asset " + assetId + " has no '" + slot.javaLayer() + "' layer");
            return;
        }
        ResourceId texture = ResourceId.parse(textureId);
        String texturePath = "entity/equipment/" + slot.javaLayer() + "/" + texture.path();
        BufferedImage image = ctx.textures().load(texture.namespace() + ":" + texturePath);
        if (image == null) {
            return;
        }
        String bedrockTexture = "textures/" + ConversionContext.NAMESPACE + "/armor/" + flat;
        ctx.bedrockPack().file(bedrockTexture + ".png", Textures.png(image));

        JsonObject description = new JsonObject();
        description.addProperty("identifier", bedrockId);
        description.add("materials", ItemConverter.stringMap("default", "armor", "enchanted", "armor_enchanted"));
        description.add("textures", ItemConverter.stringMap("default", bedrockTexture, "enchanted", "textures/misc/enchanted_actor_glint"));
        description.add("geometry", ItemConverter.stringMap("default", slot.geometry()));
        description.add("scripts", ItemConverter.stringMap("parent_setup", "v." + slot.layerVariable() + " = 0.0;"));
        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render.armor");
        description.add("render_controllers", renderControllers);

        JsonObject attachable = new JsonObject();
        attachable.add("description", description);
        JsonObject file = new JsonObject();
        file.addProperty("format_version", "1.10.0");
        file.add("minecraft:attachable", attachable);
        ctx.bedrockPack().json("attachables/" + ConversionContext.NAMESPACE + "/" + flat + ".json", file);
    }

    private static String layerTexture(JsonObject equipment, String layer) {
        JsonObject layers = Json.object(equipment, "layers");
        JsonArray entries = Json.array(layers, layer);
        if (entries == null) {
            return null;
        }
        for (JsonElement entry : entries) {
            if (entry.isJsonObject()) {
                String texture = Json.string(entry.getAsJsonObject(), "texture", null);
                if (texture != null) {
                    return texture;
                }
            }
        }
        return null;
    }
}
