package logicExtend;

import mindustry.mod.Mod;
import mindustry.ai.types.LogicAI;
import mindustry.logic.LExecutor;
import arc.struct.IntFloatMap;
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
        removeTransferDelay();
    }
    
    /** 去除itemtake和itemdrop指令的内置CD */
    private void removeTransferDelay() {
        @SuppressWarnings("unchecked")
        try {
            // 主方案：替换unitTimeouts对象
            Field unitTimeoutsField = LExecutor.class.getDeclaredField("unitTimeouts");
            unitTimeoutsField.setAccessible(true);
            
            // 创建自定义的IntFloatMap，总是返回0
            IntFloatMap customMap = new IntFloatMap() {
                @Override
                public float get(int key) {
                    return 0f; // 总是返回0，使timeoutDone总是返回true
                }
                
                @Override
                public float get(int key, float defaultValue) {
                    return 0f; // 总是返回0，处理所有get调用
                }
            };
            
            // 设置自定义的IntFloatMap，覆盖原版的unitTimeouts
            unitTimeoutsField.set(null, customMap);
            System.out.println("[MultiUnitBindMod] Successfully removed transfer delay for itemtake and itemdrop commands");
            
        } catch (Exception e) {
            System.err.println("[MultiUnitBindMod] Failed to replace unitTimeouts, falling back to original method");
            e.printStackTrace();
            
            // 备选方案：修改transferDelay常量
            try {
                Field transferDelayField = LogicAI.class.getDeclaredField("transferDelay");
                transferDelayField.setAccessible(true);
                
                // 移除final修饰符
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(transferDelayField, transferDelayField.getModifiers() & ~Modifier.FINAL);
                
                // 将transferDelay设置为0，完全去除CD
                transferDelayField.set(null, 0f);
                System.out.println("[MultiUnitBindMod] Successfully removed transfer delay using fallback method");
                
            } catch (Exception ex) {
                System.err.println("[MultiUnitBindMod] Failed to remove transfer delay using fallback method");
                ex.printStackTrace();
            }
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
