package blocks;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class Block {
  BlockType type;

  public Block(BlockType type) {
    this.type = type;
  }
}
