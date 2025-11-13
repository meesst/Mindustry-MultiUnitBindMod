package logicExtend;

import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.logic.LExecutor.*;
import mindustry.type.*;
import mindustry.world.blocks.logic.*;
import mindustry.game.Team;
// LogicAI类不存在，已移除导入
import java.util.Objects;
import java.util.Iterator;

import static mindustry.Vars.*;

public class LUnitBindGroup{
    // 单位组信息类
    public static class UnitGroupInfo {
        // 单位列表
        public Seq<Unit> units = new Seq<>();
        // 当前索引
        public int currentIndex = 0;
        // 最大数量
        public int maxCount = 1;
        public UnitType lastType = null;
        // 是否公开组
        public boolean isPublic = false;
    }
    
    // 共享组配置类，用于存储共享组的初始参数
    public static class GroupConfig {
        public final Object unitType;
        public final int count;
        public final int mode;
        
        public GroupConfig(Object unitType, int count, int mode) {
            this.unitType = unitType;
            this.count = count;
            this.mode = mode;
        }
    }
    
    // 公共方法 - 创建新组
    public static void createNewGroup(String groupName) {
        sharedGroups.put(groupName, new UnitGroupInfo());
        sharedGroupMaxCounts.put(groupName, Integer.MAX_VALUE); // 默认无限制
    }
    
    // 公共方法 - 删除组
    public static void deleteGroup(String groupName) {
        sharedGroups.remove(groupName);
        sharedGroupMaxCounts.remove(groupName);
        sharedGroupConfigs.remove(groupName);
    }
    
    // 参数缓存类，用于存储上次执行时的参数值
    public static class ParamCache {
        public Object unitType;
        public int count;
        public String groupName;
        public int mode; // 添加模式字段
        public String unitVar;
        public String indexVar;

        public boolean hasChanged(Object newUnitType, int newCount, String newGroupName, int newMode) {
            return !Objects.equals(unitType, newUnitType) || 
                   count != newCount || 
                   !Objects.equals(groupName, newGroupName) ||
                   mode != newMode;
        }
        
        public void update(Object newUnitType, int newCount, String newGroupName, int newMode) {
            this.unitType = newUnitType;
            this.count = newCount;
            this.groupName = newGroupName;
            this.mode = newMode;
        }
        
        // 更新所有参数，包括unitVar和indexVar
        public void update(Object newUnitType, int newCount, String newGroupName, int newMode, String newUnitVar, String newIndexVar) {
            update(newUnitType, newCount, newGroupName, newMode);
            this.unitVar = newUnitVar;
            this.indexVar = newIndexVar;
        }
    }
    
    // 存储所有单位组信息
    private static final ObjectMap<String, UnitGroupInfo> groupMap = new ObjectMap<>();
    // 清理定时器
    private static final Interval cleanupTimer = new Interval(1);
    private static final int cleanupFrequency = 60 * 30; // 每30秒清理一次
    
    // 存储按控制器独立的单位组和当前索引
    private static final ObjectMap<Building, UnitGroupInfo> individualGroups = new ObjectMap<>();
    // 存储按组名共享的单位组和当前索引
    private static final ObjectMap<String, UnitGroupInfo> sharedGroups = new ObjectMap<>();
    // 记录处理器与共享组的关联
    private static final ObjectMap<Building, String> buildingToGroupName = new ObjectMap<>();
    // 存储处理器的参数缓存，用于检测参数变化
    private static final ObjectMap<Building, ParamCache> paramCaches = new ObjectMap<>();
    // 用于存储共享组的最大count值
    private static final ObjectMap<String, Integer> sharedGroupMaxCounts = new ObjectMap<>();
    // 用于存储共享组的初始配置
    private static final ObjectMap<String, GroupConfig> sharedGroupConfigs = new ObjectMap<>();
    
    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /** 未指定错误 */
        NONE("none", "未指定错误"),
        /** 处理器无效 */
        INVALID_CONTROLLER("invalid_controller", "处理器无效"),
        /** 模式无效 */
        INVALID_MODE("invalid_mode", "模式无效"),
        /** 共享组不存在 */
        GROUP_NOT_EXIST("group_not_exist", "共享组不存在"),
        /** 组名冲突 */
        GROUP_CONFLICT("group_conflict", "组名冲突"),
        /** 无可用单位 */
        NO_UNITS_AVAILABLE("no_units_available", "无可用单位"),
        /** 组为空 */
        EMPTY_GROUP("empty_group", "组为空"),
        /** 数量无效 */
        INVALID_COUNT("invalid_count", "数量无效"),
        /** 单位类型未找到 */
        UNIT_TYPE_NOT_FOUND("unit_type_not_found", "单位类型未找到");
        
        private final String key;
        private final String defaultMessage;
        
        ErrorType(String key, String defaultMessage) {
            this.key = key;
            this.defaultMessage = defaultMessage;
        }
        
        /**
         * 获取错误消息
         */
        public String getMessage() {
            try {
                // 使用Core.bundle获取本地化消息，如果失败则返回默认消息
                return Core.bundle.get("ubindgroup.error." + key, defaultMessage);
            } catch (Exception e) {
                return defaultMessage;
            }
        }
    }
    
    // 设置错误信息的方法
    private static void setError(LExecutor exec, LVar unitVar, LVar indexVar, ErrorType errorType) {
        // 设置单位变量为null
        if (unitVar != null) {
            unitVar.setobj(null);
        }
        
        // 设置索引变量为负数，表示错误状态
        if (indexVar != null) {
            // 使用不同的负数表示不同的错误类型，便于区分
            indexVar.setnum(-1 - errorType.ordinal());
        }
        
        // 在控制台输出错误信息（可选）
        if (errorType != ErrorType.NONE) {
            Log.err("LUnitBindGroup Error: " + errorType.name());
        }
    }
    
    // 静态初始化块，注册UI解析器
    static {
        // 延迟初始化UI类，避免循环依赖
        try {
            Class.forName("logicExtend.LUnitBindGroupUI");
        } catch (ClassNotFoundException e) {
            Log.warn("LUnitBindGroupUI class not found, skipping UI registration.");
        }
    }
    
    // 公共方法，提供对共享组的访问
    public static ObjectMap<String, UnitGroupInfo> getSharedGroups() {
        return sharedGroups;
    }
    
    public static ObjectMap<String, Integer> getSharedGroupMaxCounts() {
        return sharedGroupMaxCounts;
    }
    
    public static ObjectMap<String, GroupConfig> getSharedGroupConfigs() {
        return sharedGroupConfigs;
    }
    
    /** 静态绑定方法，供逻辑指令调用 */
    public static void bindGroup(LExecutor exec, LVar unitType, LVar count, LVar unitVar, LVar indexVar, Object groupName, int mode) {
        // 定期执行清理操作
        if(cleanupTimer.get(0, cleanupFrequency)) {
            cleanupGroups();
            periodicCleanup(); // 添加定期清理无效控制器和组的操作
        }
        
        // 错误检查 - 添加对执行器和必要变量的验证
        if (exec == null) {
            Log.err("LUnitBindGroup: 执行器为空");
            if (indexVar != null) indexVar.setnum(-1);
            if (unitVar != null) unitVar.setobj(null);
            return;
        }
        
        if (unitVar == null) {
            Log.err("LUnitBindGroup: 单位变量为空");
            if (indexVar != null) indexVar.setnum(-2);
            return;
        }
        
        // 获取控制器
        Building controller = exec.building();
        if(controller == null) {
            setError(exec, unitVar, indexVar, ErrorType.INVALID_CONTROLLER);
            return;
        }
        
        // 获取参数值
        String group = null;
        if (groupName instanceof String) {
            group = (String)groupName;
        } else if (groupName != null) {
            Object groupObj = groupName instanceof LVar ? exec.var((LVar)groupName) : groupName;
            group = groupObj instanceof String ? (String)groupObj : null;
        }
        
        // 根据模式执行不同逻辑
        if(mode == 1) {
            executeMode1(exec, group, unitType, count, unitVar, indexVar);
        } else if(mode == 2) {
            executeMode2(exec, group, unitVar, indexVar);
        } else {
            setError(exec, unitVar, indexVar, ErrorType.INVALID_MODE);
        }
    }
    
    /** 抓取模式实现 */
    private static void executeMode1(LExecutor exec, String group, LVar unitTypeVar, LVar countVar, LVar unitVar, LVar indexVar) {
        // 获取单位类型和最大数量
        Object typeObj = unitTypeVar != null ? exec.var(unitTypeVar) : null;
        UnitType type = typeObj instanceof UnitType ? (UnitType)typeObj : null;
        int maxCount = countVar != null ? Math.max(0, Math.round(countVar.numval())) : 1;

        // 获取控制器
        Building controller = exec.building();
        if(controller == null) {
            setError(exec, unitVar, indexVar, ErrorType.INVALID_CONTROLLER);
            return;
        }

        // 检查数量是否有效
        if(maxCount <= 0) {
            setError(exec, unitVar, indexVar, ErrorType.INVALID_COUNT);
            return;
        }

        // 检查是否指定了单位类型但类型无效
        if(unitTypeVar != null && type == null) {
            setError(exec, unitVar, indexVar, ErrorType.UNIT_TYPE_NOT_FOUND);
            return;
        }

        // 检查是否有其他逻辑块已在抓取模式下使用了相同的组名
        if(group != null && !group.isEmpty()) {
            // 遍历所有建筑-组映射
            for(ObjectMap.Entry<Building, String> entry : buildingToGroupName.entries()) {
                // 检查是否有其他有效的控制器正在使用相同的组名
                if(entry.value != null && entry.value.equals(group) && 
                   entry.key != null && entry.key.isValid() && 
                   entry.key != controller) {
                    // 冲突：另一个逻辑块已在使用此组名
                    setError(exec, unitVar, indexVar, ErrorType.GROUP_CONFLICT);
                    return;
                }
            }
        }

        // 获取或创建单位组
        UnitGroupInfo info = getOrCreateGroup(group);

        // 记录建筑与组的映射关系（在确认无冲突后）
        if(group != null && !group.isEmpty()) {
            buildingToGroupName.put(controller, group);
        }

        // 检查参数变化并重新抓取单位
        if(checkAllParamsChanged(controller, type, maxCount, group, 1)) {
            // 更新单位组
            updateUnitGroup(info, type, maxCount, controller.team, controller);
        } else {
            // 维护单位池
            maintainUnitPool(info);
        }

        // 设置单位变量
        if(!info.units.isEmpty()) {
            Unit unit = info.units.get(info.currentIndex);
            unitVar.setobj(unit);
            if(indexVar != null) indexVar.setnum(info.currentIndex);

            // 更新当前索引，确保循环有效
            info.currentIndex = (info.currentIndex + 1) % info.units.size;
        } else {
            setError(exec, unitVar, indexVar, ErrorType.NO_UNITS_AVAILABLE);
        }
    }
    
    /** 访问模式实现 */
    private static void executeMode2(LExecutor exec, String group, LVar unitVar, LVar indexVar) {
        // 访问模式：不需要抓取，只访问现有单位组
        UnitGroupInfo info = group != null ? sharedGroups.get(group) : null;

        // 维护单位池，确保单位有效性
        if(info != null) {
            maintainUnitPool(info);
        }

        // 设置单位变量
        if(info != null && !info.units.isEmpty()) {
            // 确保索引在有效范围内
            if(info.currentIndex >= info.units.size) {
                info.currentIndex = 0;
            }
            
            Unit unit = info.units.get(info.currentIndex);
            unitVar.setobj(unit);
            if(indexVar != null) indexVar.setnum(info.currentIndex);

            // 更新当前索引，确保循环有效
            info.currentIndex = (info.currentIndex + 1) % info.units.size;
        } else {
            // 使用setError方法处理错误情况
            if(group == null || info == null) {
                setError(exec, unitVar, indexVar, ErrorType.GROUP_NOT_EXIST);
            } else {
                setError(exec, unitVar, indexVar, ErrorType.EMPTY_GROUP);
            }
        }
    }
    
    /** 获取或创建单位组 */
    private static UnitGroupInfo getOrCreateGroup(String name) {
        // 不再从Vars.player获取控制器，而是依赖调用方传递正确的控制器
        
        if (name != null && !name.isEmpty()) {
            // 共享组
            UnitGroupInfo info = sharedGroups.get(name);
            if (info == null) {
                info = new UnitGroupInfo();
                sharedGroups.put(name, info);
                sharedGroupMaxCounts.put(name, Integer.MAX_VALUE);
                
                // 注意：控制器与组名的映射关系已在executeMode1中检查冲突后记录
            }
            return info;
        } else {
            // 独立组 - 这里需要控制器，但当前方法无法获取，需要调用方确保
            return new UnitGroupInfo();
        }
    }
    
    // 独立组清理操作
    private static void cleanupPrivateGroup(UnitGroupInfo info) {
        // 如果当前逻辑块绑定了单位池
        if (info != null) {
            // 如果单位池中有单位对象
            if (info.units != null && info.units.size > 0) {
                // 解绑所有单位对象
                for (Unit unit : info.units) {
                    unbindUnit(unit);
                }
                // 清空单位列表
                info.units.clear();
            }
        }
    }
    
    // 公开组清理操作
    private static void cleanupPublicGroup(UnitGroupInfo info, int mode) {
        // 如果是访问模式，直接跳过清理操作
        if (mode == 2) {
            return;
        }
        
        // 如果是抓取模式且当前逻辑块绑定了单位池
        if (info != null) {
            // 如果单位池中有单位对象
            if (info.units != null && info.units.size > 0) {
                // 解绑所有单位对象
                for (Unit unit : info.units) {
                    unbindUnit(unit);
                }
                // 清空单位列表
                info.units.clear();
            }
        }
    }
    
    /** 单位池维护逻辑：使用流水线设计，包含单位有效性检查、无效单位解绑和单位池容量维护 */
    private static void cleanupUnits(UnitGroupInfo info) {
        // 1. 单位有效性检查
        for(int i = info.units.size - 1; i >= 0; i--) {
            if(!isUnitValid(info.units.get(i))) {
                // 2. 解绑无效单位
                Unit unit = info.units.get(i);
                info.units.remove(i);
                unbindUnit(unit);
                // 如果删除的是当前索引之前的单位，需要调整当前索引
                if(i < info.currentIndex) {
                    info.currentIndex--;
                }
            }
        }
        
        // 确保索引在有效范围内
        if(info.currentIndex >= info.units.size && !info.units.isEmpty()) {
            info.currentIndex = 0;
        }
    }
    
    /** 清理所有过期的独立组 */
    private static void cleanupGroups() {
        // 只清理独立组（没有公开标记的组）
        ObjectMap<String, UnitGroupInfo> toRemove = new ObjectMap<>();
        
        groupMap.each((name, info) -> {
            // 如果是独立组且没有有效单位，标记为删除
            if(!info.isPublic && info.units.isEmpty()) {
                toRemove.put(name, info);
            } else {
                // 清理所有组中的无效单位
                cleanupUnits(info);
            }
        });
        
        // 删除标记的组
        toRemove.each((name, info) -> {
            groupMap.remove(name);
        });
    }
    
    /** 抓取符合条件的单位 */
    private static void fetchUnits(LExecutor exec, UnitGroupInfo info, UnitType type, int maxCount) {
        // 计算还需要抓取的单位数量
        int needed = maxCount - info.units.size;
        if(needed <= 0) return;
        
        // 获取可用单位列表
        Seq<Unit> availableUnits = new Seq<>();
        
        // 根据类型筛选单位
        if(type != null) {
            // 从单位缓存中获取指定类型的单位
            Seq<Unit> cache = exec.team.data().unitCache(type);
            if(cache != null) {
                availableUnits.addAll(cache);
            }
        } else {
            // 如果没有指定类型，获取所有可控制的单位
            availableUnits.addAll(exec.team.data().units.copy());
        }
        
        // 使用流水线设计进行单位筛选和抓取
        for(Unit unit : availableUnits) {
            // 检查单位有效性、是否已经在组中、是否可控制，并且只抓取未被控制的单位
            if(isUnitValid(unit) && !info.units.contains(unit) && canControlUnit(exec, unit) && !isUnitControlled(unit)) {
                info.units.add(unit);
                needed--;
                
                if(needed <= 0) break;
            }
        }
    }
    
    // 判断单位是否被控制
    private static boolean isUnitControlled(Unit unit) {
        return unit.controller() != null;
    }
    
    /** 检查单位是否有效 */
    private static boolean isUnitValid(Unit unit) {
        return unit != null && !unit.dead && unit.type.logicControllable;
    }
    
    /** 检查是否可以控制该单位 */
    private static boolean canControlUnit(LExecutor exec, Unit unit) {
        return unit.team == exec.team || exec.privileged;
    }
    
    /** 解绑指定单位 */
    public static void unbindUnit(Unit unit) {
        // 解绑控制器，参考游戏源代码中的UnitControlI类中unbind控制类型的处理逻辑
        if (unit != null && !unit.dead) {
            unit.resetController();
        }
        
        // 从所有组中移除该单位
        groupMap.each((name, info) -> {
            info.units.remove(unit);
            // 调整索引
            if(info.currentIndex >= info.units.size && !info.units.isEmpty()) {
                info.currentIndex = 0;
            }
        });
    }
    
        // 统一参数检查方法，检查所有参数变化
    private static boolean checkAllParamsChanged(Building controller, Object unitTypeObj, int countVal, String groupNameStr, int mode) {
        // 获取参数缓存
        ParamCache cache = paramCaches.get(controller, ParamCache::new);
        
        // 检查所有参数是否变化（包括单位类型、数量、组名和模式）
        return cache.hasChanged(unitTypeObj, countVal, groupNameStr, mode);
    }
    
    // 清理无效控制器
    private static void cleanupInvalidController(Building controller) {
        if (controller != null) {
            // 移除控制器关联的组
            String groupName = buildingToGroupName.get(controller);
            if (groupName != null) {
                cleanupUnusedGroup(groupName);
                buildingToGroupName.remove(controller);
            }
            // 移除参数缓存
            paramCaches.remove(controller);
            // 清理独立组
            individualGroups.remove(controller);
        }
    }
    
    // 清理未使用的组
    private static void cleanupUnusedGroup(String groupName) {
        if (groupName != null && sharedGroups.containsKey(groupName)) {
            // 检查是否有其他有效控制器使用该组
            boolean isInUse = false;
            for (ObjectMap.Entry<Building, String> entry : buildingToGroupName.entries()) {
                if (entry.value != null && entry.value.equals(groupName) && 
                    entry.key != null && entry.key.isValid()) {
                    isInUse = true;
                    break;
                }
            }
            
            if (!isInUse) {
                // 解绑所有单位并清理组
                UnitGroupInfo info = sharedGroups.get(groupName);
                if (info != null && info.units != null) {
                    for (Unit unit : info.units) {
                        unbindUnit(unit);
                    }
                }
                sharedGroups.remove(groupName);
                sharedGroupMaxCounts.remove(groupName);
                sharedGroupConfigs.remove(groupName);
            }
        }
    }
    
    // 定期清理
    private static void periodicCleanup() {
        // 清理无效控制器
        ObjectMap<Building, UnitGroupInfo> invalidControllers = new ObjectMap<>();
        individualGroups.each((controller, info) -> {
            if (controller == null || !controller.isValid()) {
                invalidControllers.put(controller, info);
            }
        });
        
        invalidControllers.each((controller, info) -> {
            cleanupInvalidController(controller);
        });
        
        // 清理无效的建筑-组映射
        ObjectMap<Building, String> invalidMappings = new ObjectMap<>();
        buildingToGroupName.each((controller, groupName) -> {
            if (controller == null || !controller.isValid()) {
                invalidMappings.put(controller, groupName);
            }
        });
        
        invalidMappings.each((controller, groupName) -> {
            buildingToGroupName.remove(controller);
            cleanupUnusedGroup(groupName);
        });
        
        // 清理未使用的组
        Seq<String> unusedGroups = new Seq<>();
        sharedGroups.each((name, info) -> {
            boolean isUsed = false;
            for (String usedGroupName : buildingToGroupName.values()) {
                if (usedGroupName != null && usedGroupName.equals(name)) {
                    isUsed = true;
                    break;
                }
            }
            if (!isUsed && info.units.isEmpty()) {
                unusedGroups.add(name);
            }
        });
        
        unusedGroups.each(groupName -> {
            sharedGroups.remove(groupName);
            sharedGroupMaxCounts.remove(groupName);
            sharedGroupConfigs.remove(groupName);
        });
    }
    
    // 解绑所有单位
    private static void unbindAllUnits(Building controller, String groupName) {
        if (groupName != null && !groupName.isEmpty()) {
            UnitGroupInfo info = sharedGroups.get(groupName);
            if (info != null && info.units != null) {
                for (Unit unit : info.units) {
                    unbindUnit(unit);
                }
                info.units.clear();
            }
        } else {
            UnitGroupInfo info = individualGroups.get(controller);
            if (info != null && info.units != null) {
                for (Unit unit : info.units) {
                    unbindUnit(unit);
                }
                info.units.clear();
            }
        }
    }
    
    // 维护单位池
    private static void maintainUnitPool(UnitGroupInfo info) {
        if(info == null || info.units == null) return;
        
        // 1. 单位有效性检查
        for(int i = info.units.size - 1; i >= 0; i--) {
            Unit unit = info.units.get(i);
            if(!isUnitValid(unit)) {
                // 2. 解绑无效单位
                info.units.remove(i);
                unbindUnit(unit);
                // 3. 索引调整 - 确保索引不会越界
                if(i < info.currentIndex) {
                    info.currentIndex--;
                }
            }
        }

        // 确保索引在有效范围内
        if(info.units.isEmpty()) {
            info.currentIndex = 0; // 空组重置索引为0
        } else if(info.currentIndex >= info.units.size) {
            info.currentIndex = 0; // 索引超出范围，重置为0
        } else if(info.currentIndex < 0) {
            info.currentIndex = 0; // 防止负索引
        }
    }
    
    // 流水线设计的抓取方法
    private static void captureUnits(Seq<Unit> source, UnitGroupInfo info, UnitType type, int maxCount, Team team) {
        int needed = maxCount - info.units.size;
        if(needed <= 0) return;
        
        // 流水线设计，不回跳
        for(Unit unit : source) {
            if(needed <= 0) break;
            
            // 检查单位有效性、类型匹配、团队匹配、是否可控制，并且只抓取未被控制的单位
            if(isUnitValid(unit) && 
               (type == null || unit.type == type) && 
               unit.team == team && 
               unit.type.logicControllable && 
               !info.units.contains(unit) && 
               !isUnitControlled(unit)) {
                
                info.units.add(unit);
                needed--;
            }
        }
    }
    
    // 更新单位组
    private static void updateUnitGroup(UnitGroupInfo info, Object typeObj, int maxCount, Team team, Building controller) {
        UnitType type = typeObj instanceof UnitType ? (UnitType)typeObj : null;
        
        // 维护单位池
        maintainUnitPool(info);
        
        // 如果单位数量不足，尝试抓取新单位
        if(info.units.size < maxCount) {
            Seq<Unit> source = type != null ? 
                              team.data().unitCache(type) : 
                              team.data().units.copy();
            
            captureUnits(source, info, type, maxCount, team);
        }
    }
    
    /**
     * 单位绑定组指令类 - 继承LInstruction类
     */
    public static class UnitBindGroupInstruction implements LInstruction {
        private LVar unitTypeVar;
        private LVar countVar;
        private LVar unitVar;
        private LVar indexVar;
        private String group;
        private int mode;
        
        public UnitBindGroupInstruction(LVar unitTypeVar, LVar countVar, LVar unitVar, LVar indexVar, String group, int mode) {
            this.unitTypeVar = unitTypeVar;
            this.countVar = countVar;
            this.unitVar = unitVar;
            this.indexVar = indexVar;
            this.group = group;
            this.mode = mode;
        }
        
        @Override
        public void run(LExecutor exec) {
            bindGroup(exec, unitTypeVar, countVar, unitVar, indexVar, group, mode);
        }
        
        @Override
        public boolean isControlFlow() {
            return false;
        }
    }
    
    // UI界面相关代码保持不变（此处省略具体实现）
    // ...
}