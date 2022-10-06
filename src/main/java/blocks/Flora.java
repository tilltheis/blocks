package blocks;

import com.simsilica.mathd.Vec3i;

public enum Flora {
  TREE(new Vec3i(5, 9, 5));

  public final Vec3i size;

  Flora(Vec3i size) {
    this.size = size;
  }
}
