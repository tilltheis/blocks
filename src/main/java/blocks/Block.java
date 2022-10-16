package blocks;

import com.jme3.math.ColorRGBA;

public record Block(BlockType type, ColorRGBA color, boolean isTransparent) {}
