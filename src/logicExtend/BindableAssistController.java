package logicExtend;

import mindustry.ai.UnitCommand;
import mindustry.ai.types.*;
import mindustry.gen.*;
import mindustry.mod.*;

/**
 * 可绑定的协助控制器，用于处理单位协助指令
 * 实现单位只协助发出指令的特定玩家功能
 */
public class BindableAssistController {
    
    /**
     * 初始化绑定协助控制器
     * @param mod 当前mod实例
     */
    public static void init(Mod mod) {
        // 不需要事件处理，直接替换命令控制器
    }
    
    /**
     * 替换默认的assist命令控制器
     */
    public static void replaceAssistCommandController() {
        // 使用反射替换默认的assistCommand的controller
        try {
            // 获取UnitCommand.assistCommand字段
            java.lang.reflect.Field assistField = UnitCommand.class.getDeclaredField("assistCommand");
            assistField.setAccessible(true);
            
            // 获取当前的assistCommand实例
            UnitCommand assistCommand = (UnitCommand) assistField.get(null);
            
            // 创建一个新的UnitCommand实例，使用自定义的controller
            UnitCommand newAssistCommand = new UnitCommand("assist", "players", assistCommand.keybind, u -> {
                BindableBuilderAI ai = new BindableBuilderAI();
                ai.onlyAssist = true;
                return ai;
            });
            
            // 复制其他属性
            java.lang.reflect.Field switchToMoveField = UnitCommand.class.getDeclaredField("switchToMove");
            switchToMoveField.setAccessible(true);
            newAssistCommand.switchToMove = switchToMoveField.getBoolean(assistCommand);
            
            java.lang.reflect.Field drawTargetField = UnitCommand.class.getDeclaredField("drawTarget");
            drawTargetField.setAccessible(true);
            newAssistCommand.drawTarget = drawTargetField.getBoolean(assistCommand);
            
            java.lang.reflect.Field resetTargetField = UnitCommand.class.getDeclaredField("resetTarget");
            resetTargetField.setAccessible(true);
            newAssistCommand.resetTarget = resetTargetField.getBoolean(assistCommand);
            
            java.lang.reflect.Field snapToBuildingField = UnitCommand.class.getDeclaredField("snapToBuilding");
            snapToBuildingField.setAccessible(true);
            newAssistCommand.snapToBuilding = snapToBuildingField.getBoolean(assistCommand);
            
            java.lang.reflect.Field exactArrivalField = UnitCommand.class.getDeclaredField("exactArrival");
            exactArrivalField.setAccessible(true);
            newAssistCommand.exactArrival = exactArrivalField.getBoolean(assistCommand);
            
            // 替换assistCommand
            assistField.set(null, newAssistCommand);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}