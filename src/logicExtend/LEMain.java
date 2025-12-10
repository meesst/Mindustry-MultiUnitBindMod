package logicExtend;

import mindustry.mod.Mod;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class LEMain extends Mod {
    public LEMain() {}

    @Override
    public void init() {
        // 移除单位物品转移CD
        removeTransferDelay();
        
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
    }
    
    /** 使用反射移除单位物品转移CD */
    private void removeTransferDelay() {
        try {
            // 获取LogicAI类
            Class<?> logicAIClass = Class.forName("mindustry.ai.types.LogicAI");
            // 获取transferDelay字段
            Field transferDelayField = logicAIClass.getDeclaredField("transferDelay");
            
            // 设置字段可访问
            transferDelayField.setAccessible(true);
            
            // 关闭final修饰符检查
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(transferDelayField, transferDelayField.getModifiers() & ~Modifier.FINAL);
            
            // 修改前的值
            float oldValue = transferDelayField.getFloat(null);
            System.out.println("修改前 LogicAI.transferDelay = " + oldValue);
            
            // 修改CD为0
            transferDelayField.setFloat(null, 0f);
            
            // 修改后的值
            float newValue = transferDelayField.getFloat(null);
            System.out.println("修改后 LogicAI.transferDelay = " + newValue);
            
            // 确认修改成功
            if (newValue == 0f) {
                System.out.println("成功移除逻辑控制单位物品转移CD");
            } else {
                System.err.println("修改失败，LogicAI.transferDelay 仍为 " + newValue);
            }
            
            // 额外：检查LExecutor的unitTimeouts字段，确保它不会阻止无CD效果
            Class<?> lExecutorClass = Class.forName("mindustry.logic.LExecutor");
            Field unitTimeoutsField = lExecutorClass.getDeclaredField("unitTimeouts");
            unitTimeoutsField.setAccessible(true);
            System.out.println("LExecutor.unitTimeouts 类型：" + unitTimeoutsField.getType().getName());
            System.out.println("LExecutor.unitTimeouts 可访问：" + unitTimeoutsField.isAccessible());
            
        } catch (Exception e) {
            System.err.println("移除单位物品转移CD失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
