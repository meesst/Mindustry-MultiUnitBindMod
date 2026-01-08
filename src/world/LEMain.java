package world;

import arc.Core;
import arc.func.Cons;
import mindustry.content.*;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.Styles;
import arc.scene.ui.layout.Table;
import world.blocks.units.MultiUnitFactory.MultiUnitFactory;
import world.logicExtend.LStringMerge;
import world.logicExtend.LAmmo;
import world.logicExtend.LNestedLogic;
import world.logicExtend.LUnitBindGroupUI;
import world.logicExtend.FastUnitControl;
import world.logicExtend.LELog;

public class LEMain extends Mod {
    public LEMain() {}

    @Override
    public void init() {
        // 添加设置界面
        addSettings();
    }

    private void addSettings() {
        // 设置构建器
        Cons<SettingsMenuDialog.SettingsTable> builder = settingsTable -> {
            SettingsMenuDialog.SettingsTable settings = new SettingsMenuDialog.SettingsTable();
            
            // 添加日志开关选项，使用checkPref方法的正确参数（3个参数）
            settings.checkPref("lnestedlogic-debug-log", false, value -> {
                LELog.debugLog = value ? 1 : 0;
            });
            
            settingsTable.add(settings);
        };
        
        // 添加设置类别
        mindustry.Vars.ui.settings.getCategories().add(
            new SettingsMenuDialog.SettingsCategory(
                Core.bundle.get("lnestedlogic.settings.title", "Logic Extend Mod"),
                new arc.scene.style.TextureRegionDrawable(Core.atlas.find("clear")), // 使用默认图标
                builder
            )
        );
    }

    @Override
    public void loadContent() {
        LStringMerge.StringMergeStatement.create();
        LAmmo.CreateAmmoStatement.create();
        LAmmo.SetAmmoStatement.create();

        // 注册嵌套逻辑语句
        LNestedLogic.LNestedLogicStatement.create();
        // 注册单位绑定组指令
        LUnitBindGroupUI.UnitBindGroupStatement.create();
        
        // 注册快速单位控制指令
        FastUnitControl.create();
        
        // 初始化debugLog值
        LELog.debugLog = Core.settings.getBool("lnestedlogic-debug-log") ? 1 : 0;
        
        // 注册多单位工厂
        MultiUnitFactory multiUnitFactory = new MultiUnitFactory("multi-unit-factory");
        // 所有属性已在MultiUnitFactory构造函数中设置
        
        // 在Mindustry中，建筑注册通过添加到contentBlocks来实现
        // 注意：由于这是一个MOD，建筑注册机制可能与核心游戏不同
        // 这里暂时注释，使用Mindustry MOD推荐的注册方式
        // mindustry.content.Blocks.content.add(multiUnitFactory);
    }
}