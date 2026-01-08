package world.blocks.units.MultiUnitFactory;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.editor.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;

/**
 * 亚空间编辑器类，用于在亚空间内编辑建筑布局
 */
public class SubspaceEditor {
    private MultiUnitFactory factory;
    private MultiUnitFactory.MultiUnitFactoryBuild build;
    private BaseDialog dialog;
    private boolean isEditing = false;
    private String currentDesignName = "New Design";
    private Schematic tempSchematic;
    
    /**
     * 构造亚空间编辑器
     * @param factory 多单位工厂
     * @param build 工厂建筑实例
     */
    public SubspaceEditor(MultiUnitFactory factory, MultiUnitFactory.MultiUnitFactoryBuild build) {
        this.factory = factory;
        this.build = build;
        // 初始化空蓝图
        this.tempSchematic = createEmptySchematic();
        
        // 初始化对话框
        initDialog();
    }
    
    /**
     * 创建空蓝图
     */
    private Schematic createEmptySchematic() {
        Seq<Stile> tiles = new Seq<>();
        StringMap labels = new StringMap();
        int width = factory.subspaceSize;
        int height = factory.subspaceSize;
        return new Schematic(tiles, labels, width, height);
    }
    
    /**
     * 初始化对话框
     */
    private void initDialog() {
        dialog = new BaseDialog("Subspace Editor") {
            // 移除@Override注解和错误的方法调用，使用默认实现
        };
        
        // 使用正确的方式监听对话框显示和隐藏事件
        dialog.shown(() -> {
            isEditing = true;
        });
        
        dialog.hidden(() -> {
            isEditing = false;
        });
        
        // 设置对话框大小
        dialog.setSize(1000, 800);
        
        // 初始化对话框内容
        initDialogContent();
    }
    
    /**
     * 初始化对话框内容
     */
    private void initDialogContent() {
        Table cont = dialog.cont;
        
        // 顶部工具栏
        Table toolbar = new Table();
        cont.add(toolbar).top().growX().row();
        
        // 保存按钮
        toolbar.button("Save", Icon.save, () -> {
            saveDesign();
        }).size(120, 60).pad(5);
        
        // 取消按钮
        toolbar.button("Cancel", Icon.cancel, () -> {
            dialog.hide();
        }).size(120, 60).pad(5);
        
        // 设计名称输入框
        toolbar.add("Design Name: ").left();
        TextField nameField = toolbar.field(currentDesignName, text -> {
            currentDesignName = text;
        }).width(200).get();
        
        // 当前选中的建筑
        final Block[] selectedBlock = {Blocks.copperWall};
        
        // 中间主区域，分为左侧建筑选择和右侧编辑区域
        Table mainArea = new Table();
        cont.add(mainArea).center().grow().row();
        
        // 左侧建筑选择面板
        Table buildPalette = new Table(Tex.pane);
        mainArea.add(buildPalette).left().width(200).growY().padRight(10);
        
        // 建筑选择标题
        buildPalette.add("Buildings").color(Color.valueOf("47e4ff")).row();
        buildPalette.image().fillX().height(2).color(Color.valueOf("47e4ff")).row();
        
        // 简单的建筑列表
        Seq<Block> availableBlocks = getAvailableBlocks();
        Table blockList = new Table();
        ScrollPane scrollPane = new ScrollPane(blockList, Styles.defaultPane);
        blockList.defaults().size(180, 40).pad(5);
        
        ButtonGroup<Button> buttonGroup = new ButtonGroup<>();
        for (Block block : availableBlocks) {
            blockList.button(block.localizedName, () -> {
                selectedBlock[0] = block;
            }).update(button -> {
                button.setChecked(selectedBlock[0] == block);
            }).group(buttonGroup).row();
        }
        
        buildPalette.add(scrollPane).grow().row();
        
        // 右侧编辑区域
        Table editorTable = new Table(Tex.pane);
        mainArea.add(editorTable).grow();
        
        // 编辑区域标题
        editorTable.add("Editing Area (" + factory.subspaceSize + "x" + factory.subspaceSize + ")").color(Color.valueOf("47e4ff")).row();
        editorTable.image().fillX().height(2).color(Color.valueOf("47e4ff")).row();
        
        // 编辑区域控制栏
        Table controlBar = new Table();
        editorTable.add(controlBar).growX().height(30).padBottom(10);
        
        // 清除按钮
        controlBar.button("Clear All", Icon.trash, () -> {
            // 清除所有建筑
            tempSchematic.tiles.clear();
        }).size(120, 30).left().padLeft(10);
        
        // 编辑区域网格
        Table editGrid = new Table();
        editGrid.background(Tex.whiteui);
        editGrid.setColor(Color.darkGray);
        editorTable.add(editGrid).grow().pad(10);
        
        // 创建网格按钮
        final int cellSize = 40;
        Table gridTable = new Table();
        editGrid.add(gridTable).center();
        
        // 填充网格
        for (int y = 0; y < factory.subspaceSize; y++) {
            gridTable.row();
            for (int x = 0; x < factory.subspaceSize; x++) {
                final int finalX = x;
                final int finalY = y;
                
                // 创建网格单元格
                Table cell = new Table();
                cell.background(Tex.whiteui);
                cell.setColor(Color.gray);
                // 使用width和height方法设置Table大小，而不是size方法
                cell.width(cellSize).height(cellSize);
                
                // 检查当前位置是否有建筑
                Stile existingTile = findTileAt(finalX, finalY);
                if (existingTile != null) {
                    cell.add(existingTile.block.name).color(Color.white).fontScale(0.5f);
                }
                
                // 添加点击事件
                cell.clicked(() -> {
                    // 检查当前位置是否有建筑
                    Stile tile = findTileAt(finalX, finalY);
                    if (tile != null) {
                        // 移除建筑
                        tempSchematic.tiles.remove(tile);
                    } else {
                        // 放置建筑
                        // 根据Mindustry Stile构造函数的正确参数顺序：Block block, int x, int y, Object config, byte rotation
                        Stile newTile = new Stile(selectedBlock[0], finalX, finalY, null, (byte)0);
                        tempSchematic.tiles.add(newTile);
                    }
                });
                
                gridTable.add(cell);
            }
        }
        
        // 定时更新网格
        editGrid.update(() -> {
            // 重新生成网格
            gridTable.clear();
            
            for (int y = 0; y < factory.subspaceSize; y++) {
                gridTable.row();
                for (int x = 0; x < factory.subspaceSize; x++) {
                    final int finalX = x;
                    final int finalY = y;
                    
                    // 创建网格单元格
                    Table cell = new Table();
                    cell.background(Tex.whiteui);
                    cell.setColor(Color.gray);
                    // 使用width和height方法设置Table大小，而不是size方法
                    cell.width(cellSize).height(cellSize);
                    
                    // 检查当前位置是否有建筑
                    Stile existingTile = findTileAt(finalX, finalY);
                    if (existingTile != null) {
                        cell.add(existingTile.block.name).color(Color.white).fontScale(0.5f);
                    }
                    
                    // 添加点击事件
                    cell.clicked(() -> {
                        // 检查当前位置是否有建筑
                        Stile tile = findTileAt(finalX, finalY);
                        if (tile != null) {
                            // 移除建筑
                            tempSchematic.tiles.remove(tile);
                        } else {
                            // 放置建筑
                            // 根据Mindustry Stile构造函数的正确参数顺序：Block block, int x, int y, Object config, byte rotation
                            Stile newTile = new Stile(selectedBlock[0], finalX, finalY, null, (byte)0);
                            tempSchematic.tiles.add(newTile);
                        }
                    });
                    
                    gridTable.add(cell);
                }
            }
        });
        
        // 右下角属性预览
        Table statsTable = new Table(Tex.pane);
        cont.add(statsTable).bottom().right().width(300).height(400).pad(10);
        
        // 初始化属性预览
        updateStatsPreview(statsTable);
        
        // 定时更新属性预览
        dialog.update(() -> {
            if (isEditing) {
                updateStatsPreview(statsTable);
            }
        });
    }
    
    /**
     * 查找指定位置的建筑
     */
    private Stile findTileAt(int x, int y) {
        for (Stile tile : tempSchematic.tiles) {
            if (tile.x == x && tile.y == y) {
                return tile;
            }
        }
        return null;
    }
    
    /**
     * 获取可用的建筑列表
     */
    private Seq<Block> getAvailableBlocks() {
        Seq<Block> blocks = new Seq<>();
        // 添加一些常用建筑
        blocks.add(Blocks.copperWall);
        blocks.add(Blocks.titaniumWall);
        blocks.add(Blocks.plastaniumWall);
        blocks.add(Blocks.thoriumWall);
        blocks.add(Blocks.duo);
        blocks.add(Blocks.scatter);
        blocks.add(Blocks.scorch);
        blocks.add(Blocks.hail);
        blocks.add(Blocks.arc);
        blocks.add(Blocks.wave);
        return blocks;
    }
    
    /**
     * 更新属性预览
     * @param table 显示属性的表格
     */
    private void updateStatsPreview(Table table) {
        table.clear();
        
        // 标题
        table.add("Design Stats").color(Color.valueOf("47e4ff")).colspan(2).row();
        table.image().fillX().height(2).color(Color.valueOf("47e4ff")).colspan(2).row();
        
        // 计算设计属性
        MultiUnitFactory.SubspaceDesign design = factory.calculateDesignStats(tempSchematic);
        
        // 显示属性
        table.add("Health: ").right();
        table.add(Strings.fixed(design.health, 0)).left().color(Color.green).row();
        
        table.add("Speed: ").right();
        table.add(Strings.fixed(design.speed, 2)).left().color(Color.blue).row();
        
        table.add("Size: ").right();
        table.add(Strings.fixed(design.size, 2)).left().color(Color.yellow).row();
        
        table.add("Build Time: ").right();
        table.add(Strings.fixed(design.time / 60f, 1) + "s").left().color(Color.purple).row();
        
        table.image().fillX().height(1).color(Color.gray).colspan(2).pad(5).row();
        
        // 显示存储和生产属性
        table.add("Item Capacity: ").right();
        table.add(Strings.fixed(design.itemCapacity, 0)).left().color(Color.orange).row();
        
        table.add("Liquid Capacity: ").right();
        table.add(Strings.fixed(design.liquidCapacity, 1)).left().color(Color.cyan).row();
        
        table.add("Power Production: ").right();
        table.add(Strings.fixed(design.powerProduction, 2)).left().color(Color.lime).row();
        
        table.add("Power Consumption: ").right();
        table.add(Strings.fixed(design.powerConsumption, 2)).left().color(Color.red).row();
        
        table.add("Weapon Count: ").right();
        table.add(Strings.fixed(design.weaponCount, 0)).left().color(Color.pink).row();
        
        table.add("Production Capacity: ").right();
        table.add(Strings.fixed(design.productionCapacity, 1)).left().color(Color.teal).row();
        
        table.image().fillX().height(1).color(Color.gray).colspan(2).pad(5).row();
        
        // 显示资源需求
        table.add("Requirements:").left().colspan(2).row();
        
        if (design.requirements.length > 0) {
            for (ItemStack stack : design.requirements) {
                table.image(stack.item.uiIcon).size(16).right();
                table.add(stack.amount + " " + stack.item.localizedName).left().row();
            }
        } else {
            table.add("None").color(Color.lightGray).colspan(2).row();
        }
    }
    
    /**
     * 保存设计
     */
    private void saveDesign() {
        // 检查设计名称
        if (currentDesignName.trim().isEmpty()) {
            currentDesignName = "New Design";
        }
        
        // 保存设计
        factory.saveSubspaceDesign(currentDesignName, tempSchematic);
        
        // 显示保存成功提示
        Vars.ui.showInfo("Design saved successfully!");
        
        // 隐藏对话框
        dialog.hide();
    }
    
    /**
     * 显示亚空间编辑器
     */
    public void show() {
        dialog.show();
    }
}