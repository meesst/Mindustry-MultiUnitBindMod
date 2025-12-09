package logicExtend;

import arc.struct.Seq;
import arc.struct.ObjectMap;
import mindustry.gen.Unit;
import mindustry.gen.Building;
import mindustry.type.UnitType;
import mindustry.logic.LExecutor;
import mindustry.logic.LVar;
import mindustry.ai.types.LogicAI;
import mindustry.ai.types.CommandAI;

//单位绑定组指令执行器实现类
public class LUnitBindGroupRUN {
    //单位池数据结构
    public static class UnitPool {
        public Seq<Unit> units = new Seq<>();
        public boolean isUsed = false;
        public int currentIndex = 0;
        public UnitType type;
        public Building controller; // 控制者属性
    }
    
    // 使用执行器哈希值和instanceId作为复合key，存储每个指令实例的单位池
    private static final ObjectMap<Integer, ObjectMap<String, UnitPool>> executorPools = new ObjectMap<>();

    /** 执行单位绑定的核心逻辑 */
    public static void run(LExecutor exec, LVar type, LVar count, LVar unitVar, LVar indexVar, String instanceId) {
        // 获取单位类型和数量
        UnitType unitType = null;
        int bindCount = 1;
        
        if (type.isobj && type.obj() instanceof UnitType) {
            unitType = (UnitType) type.obj();
        } else {
            unitVar.setobj("无效单位类型");
            indexVar.setnum(-1);
            return;
        }
        
        try {
            bindCount = count.isobj ? Integer.parseInt(count.obj().toString()) : (int)count.num();
            if (bindCount < 1) bindCount = 1;
        } catch (NumberFormatException | ClassCastException e) {
            bindCount = 1;
        }
        
        // 获取或创建单位池
        ObjectMap<String, UnitPool> instancePools = executorPools.get(exec.hashCode(), ObjectMap::new);
        UnitPool pool = instancePools.get(instanceId, UnitPool::new);
        
        // 设置单位类型
        pool.type = unitType;
        
        // 单位池维护
        maintainUnitPool(exec, pool, unitType, bindCount);
        
        // 检查维护后单位池是否为空
        if (pool.units.isEmpty()) {
            pool.isUsed = false;
            unitVar.setobj("单位池为空");
            indexVar.setnum(-1);
            return;
        }
        
        // 执行索引处理逻辑
        handleIndexLogic(exec, pool, unitVar, indexVar);
    }
    
   //索引处理逻辑
    private static void handleIndexLogic(LExecutor exec, UnitPool pool, LVar unitVar, LVar indexVar) {
        // 确保计数器在有效范围内循环（防止索引越界）
        pool.currentIndex %= pool.units.size;
        if (pool.currentIndex < 0) pool.currentIndex += pool.units.size;
        
        // 获取当前索引对应的单位
        Unit unit = pool.units.get(pool.currentIndex);
        
        // 设置返回值
        unitVar.setobj(unit);
        indexVar.setnum(pool.currentIndex + 1); // 索引从1开始
        
        // 索引递增，下次执行时将返回下一个单位
        pool.currentIndex++;
    }
    
     //绑定方法：绑定指定类型（type），指定数量（count）到单位池里
    public static boolean bindUnits(LExecutor exec, UnitPool pool, UnitType type, int count) {
        
        // 检查单位类型是否可被逻辑控制
        if (!type.logicControllable) {
            return false;
        }
        
        // 获取同类型的所有单位
        Seq<Unit> allUnits = exec.team.data().unitCache(type);
        
        if (allUnits == null || allUnits.isEmpty()) {
            return false;
        }
        
        int boundCount = 0;
        
        // 遍历所有单位，按照绑定规则绑定
        for (Unit unit : allUnits) {
            if (boundCount >= count) break;
            
            // 检查单位是否可绑定
            if (isUnitBindable(exec, unit)) {
                // 预控制单位
                preControlUnit(exec, unit);
                pool.units.add(unit);
                boundCount++;
            }
        }
        
        return boundCount > 0;
    }
    
    //检查单位是否可绑定
    private static boolean isUnitBindable(LExecutor exec, Unit unit) {
        // 1. 单位必须是有效的（即未死亡且已添加到游戏世界中）
        if (!unit.isValid()) {
            return false;
        }
        
        // 2. 单位未受到任何有效控制
        // 检查是否受逻辑控制
        if (unit.controller() instanceof LogicAI) {
            return false;
        }
        
        // 检查是否受玩家控制
        if (unit.isPlayer()) {
            return false;
        }
        
        // 检查是否受命令系统控制，只有当CommandAI有命令时才判断为不可绑定
        if (unit.controller() instanceof CommandAI command && command.hasCommand()) {
            return false;
        }
        
        return true;
    }
    
    //预控制单位（将单位的控制方设置为当前逻辑处理器）
    private static void preControlUnit(LExecutor exec, Unit unit) {
        // 检查单位是否有效且可被逻辑控制
        if (unit.isValid() && unit.controller().isLogicControllable()) {
            LogicAI la;
            if (unit.controller() instanceof LogicAI) {
                la = (LogicAI) unit.controller();
            } else {
                la = new LogicAI();
                unit.controller(la);
                // 清除旧状态
                unit.mineTile = null;
                unit.clearBuilding();
            }
            
            // 设置控制器
            if (exec.build != null) {
                la.controller = exec.build;
            }
        }
    }
    
    
    //解绑方法：解绑指定单位
    public static void unbindUnit(Unit unit) {
        // 调用单位的resetController方法重置控制器，与Mindustry源码LExecutor.java中unbind操作保持一致
        if (unit != null) {
            unit.resetController();
        }
    }
    
    //重置方法：重置指定instanceId的单位池
    public static void resetUnitPool(String instanceId) {
        // 重置逻辑：遍历所有执行器的单位池，移除指定instanceId的单位池
        for (ObjectMap<String, UnitPool> instancePools : executorPools.values()) {
            UnitPool pool = instancePools.remove(instanceId);
            if (pool != null) {
                // 解绑所有单位
                for (Unit unit : pool.units) {
                    unbindUnit(unit);
                }
            }
        }
    }
    
    //单位池维护方法
    public static void maintainUnitPool(LExecutor exec, UnitPool pool, UnitType type, int count) {
        // 检查池中单位数量是否满足count要求，如果不足则补充单位
        if (pool.units.size < count) {
            bindUnits(exec, pool, type, count - pool.units.size);
        }
      
        
        // 创建一个临时列表用于存储需要移除的单位
        Seq<Unit> toRemove = new Seq<>();
        
        for (Unit unit : pool.units) {
            // 1. 单位存活判断
            if (!unit.isValid()) {
                toRemove.add(unit);
                continue;
            }
            
            // 2. 控制方判断 - 只检查是否为玩家控制，是则移除
            if (unit.isPlayer()) {
                toRemove.add(unit);
                continue;
            }
            
            // 3. 单位类型判断
            if (unit.type != type) {
                toRemove.add(unit);
            }
        }
        
        // 移除不符合条件的单位
        for (Unit unit : toRemove) {
            pool.units.remove(unit);
            unbindUnit(unit);
        }
        
        // 去重逻辑：使用单位ID去除重复的单位
        ObjectMap<Integer, Unit> uniqueUnits = new ObjectMap<>();
        for (Unit unit : pool.units) {
            uniqueUnits.put(unit.id(), unit);
        }
        
        // 用去重后的单位列表替换原列表
        pool.units.clear();
        pool.units.addAll(uniqueUnits.values());
        
        // 超出数量移除逻辑：如果单位数量超过count，移除多余的单位
        while (pool.units.size > count) {
            Unit unit = pool.units.pop(); // 从末尾移除
            unbindUnit(unit);
        }
        
        // 更新单位池使用状态和控制者
        if (pool.units.isEmpty()) {
            pool.isUsed = false;
            pool.controller = null;
        } else {
            pool.isUsed = true;
            pool.controller = exec.build;
        }
    }
}