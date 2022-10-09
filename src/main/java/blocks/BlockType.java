package blocks;

import com.jme3.math.ColorRGBA;

public enum BlockType {
  DIRT(ColorRGBA.Brown),
  GRASS(ColorRGBA.Green),
  LEAF(ColorRGBA.Green),
  ROCK(ColorRGBA.DarkGray),
  WATER(ColorRGBA.Blue.clone().setAlpha(0.8f)),
  WOOD(ColorRGBA.Brown);

  public final ColorRGBA color;

  BlockType(ColorRGBA color) {
    this.color = color;
  }
}
