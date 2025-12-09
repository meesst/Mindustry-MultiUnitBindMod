package logicExtend;

import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.input.InputHandler;
import mindustry.mod.*;

/**
 * 可绑定的协助控制器，用于处理单位协助指令
 * 实现单位只协助发出指令的特定玩家功能
 */
public class BindableAssistController {
    
    /** 存储单位ID到玩家ID的映射 */
    private static final IntMap<Integer> unitToPlayerMap = new IntMap<>();
    
    /** 存储单位ID到自定义AI控制器的映射 */
    private static final IntMap<BindableBuilderAI> unitToAIMap = new IntMap<>();
    
    /**
     * 初始化绑定协助控制器
     * @param mod 当前mod实例
     */
    public static void init(Mod mod) {
        // 注册事件处理器
        registerEventHandlers();
    }
    
    /**
     * 注册事件处理器，拦截单位命令
     */
    private static void registerEventHandlers() {
        // 注册单位命令处理事件
        Events.on(EventType.PlayerUnitCommandEvent.class, event -> {
            handleUnitCommand(event.player, event.unitIds, event.command);
        });
        
        // 注册单位移除事件
        Events.on(EventType.UnitRemoveEvent.class, event -> {
            unitToPlayerMap.remove(event.unit.id);
            unitToAIMap.remove(event.unit.id);
        });
    }
    
    /**
     * 处理单位命令
     * @param player 发出命令的玩家
     * @param unitIds 受影响的单位ID数组
     * @param command 命令类型
     */
    private static void handleUnitCommand(Player player, int[] unitIds, UnitCommand command) {
        // 只处理协助命令
        if (command != UnitCommand.assistCommand) {
            return;
        }
        
        // 遍历所有受影响的单位
        for (int id : unitIds) {
            Unit unit = Groups.unit.getByID(id);
            if (unit != null && unit.team == player.team()) {
                // 存储单位与玩家的绑定关系
                unitToPlayerMap.put(unit.id, player.id);
            }
        }
    }
    
    /**
     * 获取或创建可绑定的建造者AI
     * @param unit 目标单位
     * @return 可绑定的建造者AI实例
     */
    public static BindableBuilderAI getOrCreateBindableAI(Unit unit) {
        BindableBuilderAI ai = unitToAIMap.get(unit.id);
        if (ai == null) {
            ai = new BindableBuilderAI();
            ai.onlyAssist = true;
            
            // 如果已经有绑定关系，设置绑定的玩家
            Integer playerId = unitToPlayerMap.get(unit.id);
            if (playerId != null) {
                Player boundPlayer = findPlayerById(playerId);
                if (boundPlayer != null) {
                    ai.setBoundPlayer(boundPlayer);
                }
            }
            
            unitToAIMap.put(unit.id, ai);
        }
        return ai;
    }
    
    /**
     * 根据ID查找玩家
     * @param id 玩家ID
     * @return 玩家实例，找不到返回null
     */
    private static Player findPlayerById(int id) {
        for (Player player : Groups.player) {
            if (player.id == id) {
                return player;
            }
        }
        return null;
    }
    
    /**
     * 替换默认的assist命令控制器
     */
    public static void replaceAssistCommandController() {
        // 使用反射替换默认的assistCommand的controller
        try {
            // 获取UnitCommand.assistCommand字段
            java.lang.reflect.Field assistField = UnitCommand.class.getField("assistCommand");
            assistField.setAccessible(true);
            
            // 获取当前的assistCommand实例
            UnitCommand assistCommand = (UnitCommand) assistField.get(null);
            
            // 创建一个新的UnitCommand实例，使用自定义的controller
            UnitCommand newAssistCommand = new UnitCommand("assist", "players", assistCommand.keybind, u -> {
                return getOrCreateBindableAI(u);
            });
            
            // 复制其他属性
            java.lang.reflect.Field switchToMoveField = UnitCommand.class.getField("switchToMove");
            switchToMoveField.setAccessible(true);
            newAssistCommand.switchToMove = switchToMoveField.getBoolean(assistCommand);
            
            java.lang.reflect.Field drawTargetField = UnitCommand.class.getField("drawTarget");
            drawTargetField.setAccessible(true);
            newAssistCommand.drawTarget = drawTargetField.getBoolean(assistCommand);
            
            java.lang.reflect.Field resetTargetField = UnitCommand.class.getField("resetTarget");
            resetTargetField.setAccessible(true);
            newAssistCommand.resetTarget = resetTargetField.getBoolean(assistCommand);
            
            java.lang.reflect.Field snapToBuildingField = UnitCommand.class.getField("snapToBuilding");
            snapToBuildingField.setAccessible(true);
            newAssistCommand.snapToBuilding = snapToBuildingField.getBoolean(assistCommand);
            
            java.lang.reflect.Field exactArrivalField = UnitCommand.class.getField("exactArrival");
            exactArrivalField.setAccessible(true);
            newAssistCommand.exactArrival = exactArrivalField.getBoolean(assistCommand);
            
            // 替换assistCommand
            assistField.set(null, newAssistCommand);
            
            // 替换其他相关字段引用
            java.lang.reflect.Field assistCommandStaticField = UnitCommand.class.getDeclaredField("assistCommand");
            assistCommandStaticField.setAccessible(true);
            assistCommandStaticField.set(null, newAssistCommand);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 单位命令事件类，用于拦截单位命令
     */
    private static class PlayerUnitCommandEvent {
        public Player player;
        public int[] unitIds;
        public UnitCommand command;
        
        public PlayerUnitCommandEvent(Player player, int[] unitIds, UnitCommand command) {
            this.player = player;
            this.unitIds = unitIds;
            this.command = command;
        }
    }
}