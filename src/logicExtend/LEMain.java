package logicExtend;

import arc.Core;
import arc.func.Cons;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.Styles;
import arc.scene.ui.layout.Table;

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
    }
}