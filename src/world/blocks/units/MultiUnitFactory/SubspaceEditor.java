package world.blocks.units.MultiUnitFactory;

import arc.struct.*;
import mindustry.editor.*;
import mindustry.game.*;
import mindustry.world.*;

public class SubspaceEditor extends MapEditor {
    private int subspaceSize;
    private boolean active = false;

    public SubspaceEditor(int subspaceSize) {
        this.subspaceSize = subspaceSize;
    }

    public void enterSubspace() {
        active = true;
        beginEdit(subspaceSize, subspaceSize);
    }

    public void exitSubspace() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public int getSubspaceSize() {
        return subspaceSize;
    }

    public void setSubspaceSize(int subspaceSize) {
        this.subspaceSize = subspaceSize;
    }

    public Schematic saveSchematic(String name) {
        // 保存当前编辑的建筑布局为Schematic
        Seq<Tile> tiles = new Seq<>();
        for (int x = 0; x < subspaceSize; x++) {
            for (int y = 0; y < subspaceSize; y++) {
                Tile tile = world.tile(x, y);
                if (tile != null && tile.block() != Blocks.air) {
                    tiles.add(tile);
                }
            }
        }

        // 转换为Stile列表
        Seq<Schematic.Stile> stileSeq = new Seq<>();
        for (Tile tile : tiles) {
            stileSeq.add(new Schematic.Stile(tile.block(), tile.x, tile.y, tile.build != null ? tile.build.config() : null, (byte) tile.rotation()));
        }

        // 创建并返回Schematic
        return new Schematic(stileSeq, new StringMap().put("name", name), subspaceSize, subspaceSize);
    }

    public void loadSchematic(Schematic schematic) {
        // 清空当前编辑区域
        for (int x = 0; x < subspaceSize; x++) {
            for (int y = 0; y < subspaceSize; y++) {
                world.tile(x, y).setBlock(Blocks.air);
            }
        }

        // 加载Schematic中的建筑布局
        for (Schematic.Stile stile : schematic.tiles) {
            if (stile.x >= 0 && stile.x < subspaceSize && stile.y >= 0 && stile.y < subspaceSize) {
                world.tile(stile.x, stile.y).setBlock(stile.block, Team.sharded, stile.rotation, () -> {
                    Building build = stile.block.newBuilding();
                    build.tileX = stile.x;
                    build.tileY = stile.y;
                    build.rotation(stile.rotation);
                    if (stile.config != null) {
                        build.config(stile.config);
                    }
                    return build;
                });
            }
        }
    }
}