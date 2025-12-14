package logicExtend;

import mindustry.mod.Mod;

public class LEMain extends Mod {
    public LEMain() {}

    @Override
    public void init() {
        // 初始化可绑定的协助控制器，替换默认的assist命令
        BindableAssistController.init(this);
        BindableAssistController.replaceAssistCommandController();
    }

    @Override
    public void loadContent() {
        LStringMerge.StringMergeStatement.create();
        LAmmo.CreateAmmoStatement.create();
        LAmmo.SetAmmoStatement.create();
        LFunction.LFunctionStatement.create();
        // 注册嵌套逻辑语句
        LNestedLogic.LNestedLogicStatement.create();
        // 注册单位绑定组指令
        LUnitBindGroupUI.UnitBindGroupStatement.create();
        // 注册单位协助指令
        LUnitAssist.create();
        
        // 注册快速单位控制指令
        FastUnitControl.create();
    }
}
