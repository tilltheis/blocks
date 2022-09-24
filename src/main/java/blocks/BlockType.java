package blocks;

import com.jme3.math.ColorRGBA;

public enum BlockType {
  DIRT(ColorRGBA.Brown),
  GRASS(ColorRGBA.Green),
  WATER(ColorRGBA.Blue);

  public final ColorRGBA color;

  BlockType(ColorRGBA color) {
    this.color = color;
  }
}
