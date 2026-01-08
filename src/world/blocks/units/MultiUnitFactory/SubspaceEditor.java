package world.blocks.units.MultiUnitFactory;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
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
import mindustry.world.blocks.environment.*;

/**
 * 亚空间编辑器类，用于在亚空间内编辑建筑布局
 * 使用Schematic系统实现，避免直接修改游戏世界
 */
public class SubspaceEditor implements Disposable {
    private MultiUnitFactory factory;
    private MultiUnitFactory.MultiUnitFactoryBuild build;
    private Player player;
    
    private boolean active = false;
    private Schematic tempSchematic;
    private Block selectedBlock = Blocks.copperWall;
    
    // 编辑器属性
    private int gridSize = 15;
    private float tileSize = 32f;
    private Vec2 cameraPos = new Vec2();
    private float zoom = 1f;
    
    // UI组件
    private Table editorUI;
    private Table blockPalette;
    private Table statsTable;
    private TextField designNameField;
    
    // 建筑选择列表
    private Seq<Block> availableBlocks;
    
    /**
     * 构造亚空间编辑器
     * @param factory 多单位工厂
     * @param build 工厂建筑实例
     */
    public SubspaceEditor(MultiUnitFactory factory, MultiUnitFactory.MultiUnitFactoryBuild build) {
        this.factory = factory;
        this.build = build;
        
        // 初始化编辑器
        init();
    }
    
    /**
     * 初始化编辑器
     */
    private void init() {
        // 初始化空蓝图
        tempSchematic = createEmptySchematic();
        
        // 初始化建筑列表
        availableBlocks = getAvailableBlocks();
        
        // 创建UI组件
        createUI();
    }
    
    /**
     * 创建空蓝图
     */
    private Schematic createEmptySchematic() {
        Seq<Stile> tiles = new Seq<>();
        StringMap labels = new StringMap();
        return new Schematic(tiles, labels, gridSize, gridSize);
    }
    
    /**
     * 创建UI组件
     */
    private void createUI() {
        // 创建编辑器主UI
        editorUI = new Table() {
            @Override
            public void draw() {
                super.draw();
                
                if (active) {
                    drawEditor();
                }
            }
        };
        
        editorUI.setFillParent(true);
        editorUI.visible(() -> active);
        
        // 添加事件监听器
        editorUI.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (active && button == KeyCode.mouseLeft) {
                    placeBlock(x, y);
                    return true;
                } else if (active && button == KeyCode.mouseRight) {
                    removeBlock(x, y);
                    return true;
                }
                return false;
            }
            
            @Override
            public void mouseDragged(InputEvent event, float x, float y, int pointer) {
                if (active && pointer == 0) {
                    cameraPos.add(event.deltaX, event.deltaY);
                }
            }
        });
        
        // 添加滚轮缩放支持
        editorUI.addListener(new ScrollListener() {
            @Override
            public void scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (active) {
                    zoom = Mathf.clamp(zoom - amountY * 0.1f, 0.5f, 3f);
                }
            }
        });
        
        // 创建顶部工具栏
        Table toolbar = new Table();
        toolbar.background(Tex.button);
        
        // 设计名称输入框
        toolbar.add("Design Name: ");
        designNameField = toolbar.field("New Design", text -> {}).width(200f).get();
        
        // 保存按钮
        toolbar.button("Save", Icon.save, () -> {
            saveDesign();
        }).size(120f, 40f).padLeft(10f);
        
        // 取消按钮
        toolbar.button("Cancel", Icon.cancel, () -> {
            close();
        }).size(120f, 40f).padLeft(10f);
        
        // 清除按钮
        toolbar.button("Clear All", Icon.trash, () -> {
            tempSchematic.tiles.clear();
        }).size(120f, 40f).padLeft(10f);
        
        // 添加工具栏到编辑器UI
        editorUI.add(toolbar).top().growX().row();
        
        // 创建底部主区域，分为左侧建筑选择和右侧编辑区域
        Table mainArea = new Table();
        editorUI.add(mainArea).grow().row();
        
        // 创建左侧建筑选择面板
        blockPalette = new Table(Tex.pane);
        blockPalette.setSize(200f, 0f);
        
        // 建筑选择标题
        blockPalette.add("Buildings").color(Pal.accent).row();
        blockPalette.image().fillX().height(2f).color(Pal.accent).row();
        
        // 创建建筑列表滚动面板
        ScrollPane scrollPane = new ScrollPane(new Table(), Styles.defaultPane);
        Table blockList = scrollPane.getContent();
        blockList.defaults().size(180f, 40f).pad(5f);
        
        // 创建建筑选择按钮组
        ButtonGroup<Button> buttonGroup = new ButtonGroup<>();
        for (Block block : availableBlocks) {
            ImageButton button = blockList.button(block.localizedName, Styles.togglet, () -> {
                selectedBlock = block;
            }).update(b -> {
                b.setChecked(selectedBlock == block);
            }).group(buttonGroup).get();
            
            // 为按钮添加图标
            button.getImageCell().size(32f).padRight(5f).setActor(new Image(block.uiIcon));
            blockList.row();
        }
        
        blockPalette.add(scrollPane).grow().row();
        
        // 添加建筑选择面板到主区域
        mainArea.add(blockPalette).left().growY().padRight(10f);
        
        // 创建右侧编辑区域（这里主要用于绘制，UI组件较少）
        Table editArea = new Table();
        mainArea.add(editArea).grow();
        
        // 创建右下角属性预览面板
        statsTable = new Table(Tex.pane);
        statsTable.setSize(300f, 400f);
        editorUI.add(statsTable).bottom().right().pad(10f);
        
        // 初始化属性预览
        updateStatsPreview();
        
        // 定时更新属性预览
        editorUI.update(() -> {
            if (active) {
                updateStatsPreview();
            }
        });
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
        blocks.add(Blocks.battery);
        blocks.add(Blocks.powerNode);
        blocks.add(Blocks.copperGenerator);
        blocks.add(Blocks.siliconSmelter);
        blocks.add(Blocks.mechanicalDrill);
        return blocks;
    }
    
    /**
     * 绘制编辑器
     */
    private void drawEditor() {
        // 计算编辑器中心位置
        float centerX = Core.graphics.getWidth() / 2f;
        float centerY = Core.graphics.getHeight() / 2f;
        
        // 绘制背景
        Draw.color(0.1f, 0.1f, 0.15f);
        Draw.rect("black", centerX, centerY, Core.graphics.getWidth(), Core.graphics.getHeight());
        Draw.color();
        
        // 绘制网格
        drawGrid(centerX, centerY);
        
        // 绘制建筑
        drawBlocks(centerX, centerY);
        
        // 绘制选择指示器
        drawSelectionIndicator(centerX, centerY);
    }
    
    /**
     * 绘制网格
     */
    private void drawGrid(float centerX, float centerY) {
        Draw.color(Color.gray, 0.3f);
        Lines.stroke(1f);
        
        float gridPixelSize = tileSize * zoom;
        float halfGridSize = gridSize * gridPixelSize / 2f;
        
        // 计算可见范围
        float startX = centerX - halfGridSize + cameraPos.x;
        float startY = centerY - halfGridSize + cameraPos.y;
        float endX = startX + gridSize * gridPixelSize;
        float endY = startY + gridSize * gridPixelSize;
        
        // 绘制垂直线
        for (int x = 0; x <= gridSize; x++) {
            float posX = startX + x * gridPixelSize;
            Lines.line(posX, startY, posX, endY);
        }
        
        // 绘制水平线
        for (int y = 0; y <= gridSize; y++) {
            float posY = startY + y * gridPixelSize;
            Lines.line(startX, posY, endX, posY);
        }
        
        Draw.color();
    }
    
    /**
     * 绘制建筑
     */
    private void drawBlocks(float centerX, float centerY) {
        float gridPixelSize = tileSize * zoom;
        float halfGridSize = gridSize * gridPixelSize / 2f;
        float startX = centerX - halfGridSize + cameraPos.x;
        float startY = centerY - halfGridSize + cameraPos.y;
        
        for (Stile stile : tempSchematic.tiles) {
            if (stile.block == Blocks.air) continue;
            
            // 计算建筑位置
            float x = startX + stile.x * gridPixelSize + gridPixelSize / 2f;
            float y = startY + stile.y * gridPixelSize + gridPixelSize / 2f;
            
            // 绘制建筑图标
            Draw.rect(stile.block.uiIcon, x, y, gridPixelSize, gridPixelSize);
            
            // 绘制建筑名称
            Draw.color(Color.white);
            Fonts.outline.draw(stile.block.localizedName, x, y + gridPixelSize / 2f + 10f, 
                              Align.center, 0.5f, false);
            Draw.color();
        }
    }
    
    /**
     * 绘制选择指示器
     */
    private void drawSelectionIndicator(float centerX, float centerY) {
        // 获取鼠标在编辑器中的位置
        float mouseX = Core.input.mouseX();
        float mouseY = Core.input.mouseY();
        
        // 计算网格位置
        float gridPixelSize = tileSize * zoom;
        float halfGridSize = gridSize * gridPixelSize / 2f;
        float startX = centerX - halfGridSize + cameraPos.x;
        float startY = centerY - halfGridSize + cameraPos.y;
        
        int gridX = Mathf.clamp((int)((mouseX - startX) / gridPixelSize), 0, gridSize - 1);
        int gridY = Mathf.clamp((int)((mouseY - startY) / gridPixelSize), 0, gridSize - 1);
        
        // 绘制选择框
        float boxX = startX + gridX * gridPixelSize;
        float boxY = startY + gridY * gridPixelSize;
        
        Draw.color(Color.blue, 0.5f);
        Draw.rect("white", boxX + gridPixelSize / 2f, boxY + gridPixelSize / 2f, 
                 gridPixelSize, gridPixelSize);
        Draw.color(Color.blue);
        Lines.stroke(2f);
        Lines.rect(boxX, boxY, gridPixelSize, gridPixelSize);
        Lines.stroke(1f);
        Draw.color();
        
        // 绘制当前选中建筑的预览
        if (selectedBlock != null) {
            Draw.color(Color.white, 0.7f);
            Draw.rect(selectedBlock.uiIcon, boxX + gridPixelSize / 2f, boxY + gridPixelSize / 2f, 
                     gridPixelSize, gridPixelSize);
            Draw.color();
        }
    }
    
    /**
     * 放置建筑
     */
    private void placeBlock(float x, float y) {
        // 计算网格位置
        float gridPixelSize = tileSize * zoom;
        float halfGridSize = gridSize * gridPixelSize / 2f;
        float startX = Core.graphics.getWidth() / 2f - halfGridSize + cameraPos.x;
        float startY = Core.graphics.getHeight() / 2f - halfGridSize + cameraPos.y;
        
        int gridX = Mathf.clamp((int)((x - startX) / gridPixelSize), 0, gridSize - 1);
        int gridY = Mathf.clamp((int)((y - startY) / gridPixelSize), 0, gridSize - 1);
        
        // 检查当前位置是否已有建筑
        Stile existingTile = findTileAt(gridX, gridY);
        if (existingTile != null) {
            // 移除现有建筑
            tempSchematic.tiles.remove(existingTile);
        }
        
        // 放置新建筑
        Stile newTile = new Stile(selectedBlock, gridX, gridY, null, (byte)0);
        tempSchematic.tiles.add(newTile);
    }
    
    /**
     * 移除建筑
     */
    private void removeBlock(float x, float y) {
        // 计算网格位置
        float gridPixelSize = tileSize * zoom;
        float halfGridSize = gridSize * gridPixelSize / 2f;
        float startX = Core.graphics.getWidth() / 2f - halfGridSize + cameraPos.x;
        float startY = Core.graphics.getHeight() / 2f - halfGridSize + cameraPos.y;
        
        int gridX = Mathf.clamp((int)((x - startX) / gridPixelSize), 0, gridSize - 1);
        int gridY = Mathf.clamp((int)((y - startY) / gridPixelSize), 0, gridSize - 1);
        
        // 检查当前位置是否有建筑
        Stile tile = findTileAt(gridX, gridY);
        if (tile != null) {
            // 移除建筑
            tempSchematic.tiles.remove(tile);
        }
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
     * 更新属性预览
     */
    private void updateStatsPreview() {
        statsTable.clear();
        
        // 标题
        statsTable.add("Design Stats").color(Pal.accent).colspan(2).row();
        statsTable.image().fillX().height(2f).color(Pal.accent).colspan(2).row();
        
        // 计算设计属性
        MultiUnitFactory.SubspaceDesign design = factory.calculateDesignStats(tempSchematic);
        
        // 显示属性
        statsTable.add("Health: ").right();
        statsTable.add(Strings.fixed(design.health, 0)).left().color(Color.green).row();
        
        statsTable.add("Speed: ").right();
        statsTable.add(Strings.fixed(design.speed, 2)).left().color(Color.blue).row();
        
        statsTable.add("Size: ").right();
        statsTable.add(Strings.fixed(design.size, 2)).left().color(Color.yellow).row();
        
        statsTable.add("Build Time: ").right();
        statsTable.add(Strings.fixed(design.time / 60f, 1) + "s").left().color(Color.purple).row();
        
        statsTable.image().fillX().height(1f).color(Color.gray).colspan(2).pad(5f).row();
        
        // 显示存储和生产属性
        statsTable.add("Item Capacity: ").right();
        statsTable.add(Strings.fixed(design.itemCapacity, 0)).left().color(Color.orange).row();
        
        statsTable.add("Liquid Capacity: ").right();
        statsTable.add(Strings.fixed(design.liquidCapacity, 1)).left().color(Color.cyan).row();
        
        statsTable.add("Power Production: ").right();
        statsTable.add(Strings.fixed(design.powerProduction, 2)).left().color(Color.lime).row();
        
        statsTable.add("Power Consumption: ").right();
        statsTable.add(Strings.fixed(design.powerConsumption, 2)).left().color(Color.red).row();
        
        statsTable.add("Weapon Count: ").right();
        statsTable.add(Strings.fixed(design.weaponCount, 0)).left().color(Color.pink).row();
        
        statsTable.add("Production Capacity: ").right();
        statsTable.add(Strings.fixed(design.productionCapacity, 1)).left().color(Color.teal).row();
        
        statsTable.image().fillX().height(1f).color(Color.gray).colspan(2).pad(5f).row();
        
        // 显示资源需求
        statsTable.add("Requirements:").left().colspan(2).row();
        
        if (design.requirements.length > 0) {
            for (ItemStack stack : design.requirements) {
                statsTable.image(stack.item.uiIcon).size(16f).right();
                statsTable.add(stack.amount + " " + stack.item.localizedName).left().row();
            }
        } else {
            statsTable.add("None").color(Color.lightGray).colspan(2).row();
        }
    }
    
    /**
     * 保存设计
     */
    private void saveDesign() {
        // 检查设计名称
        String designName = designNameField.getText().trim();
        if (designName.isEmpty()) {
            designName = "New Design";
        }
        
        // 保存设计
        factory.saveSubspaceDesign(designName, tempSchematic);
        
        // 显示保存成功提示
        Vars.ui.showInfo("Design saved successfully!");
        
        // 关闭编辑器
        close();
    }
    
    /**
     * 打开编辑器
     */
    public void open(Player player) {
        this.player = player;
        active = true;
        
        // 添加UI到场景
        Core.scene.add(editorUI);
        
        // 捕获输入
        Core.scene.setKeyboardFocus(editorUI);
    }
    
    /**
     * 关闭编辑器
     */
    public void close() {
        active = false;
        
        // 移除UI
        editorUI.remove();
    }
    
    @Override
    public void dispose() {
        close();
    }
}