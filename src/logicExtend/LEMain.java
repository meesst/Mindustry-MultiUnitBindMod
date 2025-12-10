package logicExtend;

import mindustry.mod.Mod;
import mindustry.ai.types.LogicAI;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class LEMain extends Mod {
    public LEMain() {}

    @Override
    public void init() {
        // 初始化可绑定的协助控制器，替换默认的assist命令
        BindableAssistController.init(this);
        BindableAssistController.replaceAssistCommandController();
        
        // 去除itemtake和itemdrop指令的内置CD
        try {
            Field field = LogicAI.class.getDeclaredField("transferDelay");
            field.setAccessible(true);
            
            // 移除final修饰符
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            
            // 将transferDelay设置为0，完全去除CD
            field.set(null, 0f);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    }
}
