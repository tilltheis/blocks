package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector4f;
import com.jme3.shader.VarType;
import com.jme3.texture.Texture;

import java.util.HashMap;
import java.util.Map;

public class BlockMaterial {
  private static ColorRGBA hexColor(String hexCode) {
    return hexColor(hexCode, 1);
  }

  private static ColorRGBA hexColor(String hexCode, float alpha) {
    return new ColorRGBA()
        .setAsSrgb(
            Integer.valueOf(hexCode.substring(0, 2), 16) / 255f,
            Integer.valueOf(hexCode.substring(2, 4), 16) / 255f,
            Integer.valueOf(hexCode.substring(4, 6), 16) / 255f,
            alpha);
  }

  private final Map<BlockType, Material> materials;

  public BlockMaterial(AssetManager assetManager) {
    BlockType[] blockTypes = BlockType.values();
    materials = new HashMap<>(blockTypes.length, 1f);

    for (BlockType blockType : blockTypes) {
      Material material = new Material(assetManager, "BlockLighting.j3md");
      Texture texture = assetManager.loadTexture(new TextureKey("tile.png", true));
      texture.setWrap(Texture.WrapMode.Repeat);
      material.setTexture("DiffuseMap", texture);

      Vector4f[] gradient =
          switch (blockType) {
            case DIRT -> new Vector4f[] {
              hexColor("503926").toVector4f(), hexColor("6F4E37").toVector4f()
            };
            case GRASS -> new Vector4f[] {
              hexColor("125F2F").toVector4f(), hexColor("219149").toVector4f()
            };
            case LEAF -> new Vector4f[] {
              hexColor("006409", 0.95f).toVector4f(), hexColor("005909", 0.95f).toVector4f(),
              hexColor("005109", 0.95f).toVector4f(), hexColor("004607", 0.95f).toVector4f(),
              hexColor("003516", 1f).toVector4f(),
            };
            case ROCK -> new Vector4f[] {
              hexColor("514C49").toVector4f(),
              hexColor("777370").toVector4f(),
              hexColor("6A5C53").toVector4f()
            };
            case WATER -> new Vector4f[] {
              hexColor("0E0085", 0.8f).toVector4f(), hexColor("1E339A", 0.8f).toVector4f()
            };
            case WOOD -> new Vector4f[] {
              hexColor("503926").toVector4f(), hexColor("64412A").toVector4f(),
              hexColor("59311D").toVector4f(), hexColor("602D1C").toVector4f(),
              hexColor("60281B").toVector4f(),
            };
          };

      material.setParam("OverlayGradient", VarType.Vector4Array, gradient);
      material.setInt("OverlayGradientSteps", gradient.length);

      if (blockType == BlockType.WATER) {
        material.setBoolean("AnimateAsWater", true);
      }

      if (gradient[0].w < 1f) {
        material.setTransparent(true);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
      }

      materials.put(blockType, material);
    }
  }

  public Material forBlock(Block block) {
    return materials.get(block.type());
  }
}
