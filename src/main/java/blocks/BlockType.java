package blocks;

import com.jme3.math.ColorRGBA;

public enum BlockType {
  DIRT(ColorRGBA.Brown),
  GRASS(ColorRGBA.Green),
  LEAF(ColorRGBA.Green.clone().mult(0.7f)),
  ROCK(ColorRGBA.DarkGray),
  WATER(new ColorRGBA(0, 0.1f, 1, 0.9f)),
  WOOD(ColorRGBA.Brown);

  public final ColorRGBA color;

  BlockType(ColorRGBA color) {
    this.color = color;
  }
}
