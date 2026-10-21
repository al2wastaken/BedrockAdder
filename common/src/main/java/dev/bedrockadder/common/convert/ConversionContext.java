package dev.bedrockadder.common.convert;

import dev.bedrockadder.common.ConversionLog;
import dev.bedrockadder.common.bedrock.BedrockPack;
import dev.bedrockadder.common.bedrock.Textures;
import dev.bedrockadder.common.java.JavaPack;
import dev.bedrockadder.common.java.ModelResolver;

/** Shared state of one conversion run. */
public record ConversionContext(
        JavaPack javaPack,
        ModelResolver models,
        Textures textures,
        BedrockPack bedrockPack,
        ConversionLog log,
        Options options
) {

    /**
     * @param iconSize size in pixels of inventory icons rendered for 3D items
     * @param fonts    convert font images (emojis/icons) to Bedrock glyph sheets
     * @param fontCellUnits Java font pixels that one Bedrock glyph cell stands for; higher makes glyphs smaller
     * @param guiOffsetX    horizontal position of GUI background glyphs relative to the title, in Java pixels
     *                      (ItemsAdder's {@code x_position_pixels}, -16 by default)
     */
    public record Options(int iconSize, boolean fonts, int fontCellUnits, int guiOffsetX) {

        public static final Options DEFAULT = new Options(64, true, 32, -16);
    }

    public static final String NAMESPACE = "bedrockadder";
}
