package blocks;

import java.util.Optional;

public interface BlockAtFunction {
  Optional<Block> apply(int x, int y, int z);
}
