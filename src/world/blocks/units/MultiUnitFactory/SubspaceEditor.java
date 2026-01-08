package world.blocks.units.MultiUnitFactory;

import arc.*;
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
import mindustry.ui.dialogs.*;
import mindustry.world.*;

/**
 * 亚空间编辑器类，用于在亚空间内编辑建筑布局
 */
public class SubspaceEditor {
    private MultiUnitFactory factory;
    private MultiUnitFactory.MultiUnitFactoryBuild build;
    private BaseDialog dialog;
    
    /**
     * 构造亚空间编辑器
     * @param factory 多单位工厂
     * @param build 工厂建筑实例
     */
    public SubspaceEditor(MultiUnitFactory factory, MultiUnitFactory.MultiUnitFactoryBuild build) {
        this.factory = factory;
        this.build = build;
        
        // 初始化对话框
        initDialog();
    }
    
    /**
     * 初始化对话框
     */
    private void initDialog() {
        dialog = new BaseDialog("Subspace Editor");
        
        // 简化的对话框内容
        dialog.cont.add("Subspace Editor is under development!");
        dialog.cont.row();
        dialog.button("OK", dialog::hide).size(100, 50);
        
        // 设置对话框大小
        dialog.setSize(500, 300);
    }
    
    /**
     * 显示亚空间编辑器
     */
    public void show() {
        dialog.show();
    }
}