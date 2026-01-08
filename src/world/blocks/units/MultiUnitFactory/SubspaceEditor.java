package world.blocks.units.MultiUnitFactory;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.editor.*;
import mindustry.game.*;
import mindustry.gen.*;
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
    private Schematic tempSchematic;
    private BaseDialog dialog;
    private MapEditor editor;
    
    private boolean isEditing = false;
    private String currentDesignName = "New Design";
    
    /**
     * 构造亚空间编辑器
     * @param factory 多单位工厂
     * @param build 工厂建筑实例
     */
    public SubspaceEditor(MultiUnitFactory factory, MultiUnitFactory.MultiUnitFactoryBuild build) {
        this.factory = factory;
        this.build = build;
        this.tempSchematic = new Schematic();
        
        // 初始化编辑器
        initEditor();
        // 初始化对话框
        initDialog();
    }
    
    /**
     * 初始化地图编辑器
     */
    private void initEditor() {
        // 创建临时地图编辑器
        editor = new MapEditor() {
            @Override
            public void beginEdit() {
                super.beginEdit();
                isEditing = true;
            }
            
            @Override
            public void endEdit() {
                super.endEdit();
                isEditing = false;
                // 更新临时蓝图
                updateTempSchematic();
            }
        };
        
        // 设置编辑器属性
        editor.setMapSize(factory.subspaceSize, factory.subspaceSize);
        editor.setTeam(build.team);
        editor.setBuildingType(Blocks.air);
        
        // 限制编辑区域大小
        editor.setMaxSize(factory.subspaceSize, factory.subspaceSize);
    }
    
    /**
     * 初始化对话框
     */
    private void initDialog() {
        dialog = new BaseDialog("Subspace Editor") {
            @Override
            public void show() {
                super.show();
                // 开始编辑
                editor.beginEdit();
            }
            
            @Override
            public void hide() {
                super.hide();
                // 结束编辑
                editor.endEdit();
            }
        };
        
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
        
        // 中间编辑区域
        Table editorTable = new Table();
        cont.add(editorTable).center().grow().row();
        
        // 右下角属性预览
        Table statsTable = new Table(Styles.grayPanel);
        cont.add(statsTable).bottom().right().width(300).height(400).pad(10);
        
        // 初始化属性预览
        updateStatsPreview(statsTable);
        
        // 定时更新属性预览
        dialog.update(() -> {
            if (isEditing) {
                updateTempSchematic();
                updateStatsPreview(statsTable);
            }
        });
    }
    
    /**
     * 更新临时蓝图
     */
    private void updateTempSchematic() {
        // 从编辑器获取建筑布局
        Seq<EditTile> tiles = editor.getTiles();
        tempSchematic.tiles.clear();
        
        // 转换为蓝图格式
        for (EditTile tile : tiles) {
            if (tile.block != Blocks.air) {
                tempSchematic.tiles.add(new Schematic.Stile(tile.x, tile.y, tile.block, tile.rotation, tile.config));
            }
        }
        
        // 更新蓝图尺寸
        tempSchematic.width = factory.subspaceSize;
        tempSchematic.height = factory.subspaceSize;
    }
    
    /**
     * 更新属性预览
     * @param table 显示属性的表格
     */
    private void updateStatsPreview(Table table) {
        table.clear();
        
        table.add("Design Stats").color(Pal.accent).colspan(2).row();
        table.image().fillX().height(2).color(Pal.accent).colspan(2).row();
        
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
        
        table.image().fillX().height(1).color(Pal.gray).colspan(2).pad(5).row();
        
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
        
        table.image().fillX().height(1).color(Pal.gray).colspan(2).pad(5).row();
        
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