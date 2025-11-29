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
        public String groupId;
        public Building controller; // 控制者属性
    }
    
    //共享单位池存储
    private static final ObjectMap<String, UnitPool> sharedPools = new ObjectMap<>();
    
    //独立单位池存储
    private static final ObjectMap<Integer, ObjectMap<String, UnitPool>> standalonePools = new ObjectMap<>();

    /** 执行单位绑定的核心逻辑 */
    public static void run(LExecutor exec, LVar type, LVar count, LVar mode, LVar unitVar, LVar indexVar, LVar group) {
        // 获取参数值
        String modeStr = mode.isobj ? (mode.obj() != null ? mode.obj().toString() : "") : String.valueOf(mode.num());
        String groupStr = group.isobj ? (group.obj() != null ? group.obj().toString() : "") : String.valueOf(group.num());
        

        // 根据mode分流处理
        if ("visiting-unit".equals(modeStr)) {
            // visiting-unit模式
            handleVisitingUnitMode(exec, groupStr, unitVar, indexVar);
        } else if ("Capture-unit".equals(modeStr)) {
            // Capture-unit模式
            handleCaptureUnitMode(exec, type, count, groupStr, unitVar, indexVar);
        } else {
            // 无效模式
            unitVar.setobj("无效模式: " + modeStr);
            indexVar.setnum(-1);
        }
    }
    
    //处理visiting-unit模式
    private static void handleVisitingUnitMode(LExecutor exec, String groupStr, LVar unitVar, LVar indexVar) {
        // 查找指定group的单位池
        UnitPool pool = getUnitPool(exec, groupStr);
        
        if (pool == null) {
            // 单位池不存在
            unitVar.setobj("组未被使用");
            indexVar.setnum(-1);
            return;
        }
        
        // 检查组的使用状态
        if (!pool.isUsed) {
            unitVar.setobj("组未被使用");
            indexVar.setnum(-1);
            return;
        }
        
        // 检查单位池是否为空
        if (pool.units.isEmpty()) {
            unitVar.setobj("单位池为空");
            indexVar.setnum(-1);
            return;
        }
        
        // 执行索引处理逻辑
        handleIndexLogic(pool, unitVar, indexVar);
    }
    
    //处理Capture-unit模式
    private static void handleCaptureUnitMode(LExecutor exec, LVar type, LVar count, String groupStr, LVar unitVar, LVar indexVar) {
        // 处理单位池创建
        UnitPool pool = getOrCreateUnitPool(exec, groupStr);
        
        // 检查共享单位池是否已被使用（仅针对非stand-alone组）
        if (!"stand-alone".equals(groupStr) && pool.isUsed) {
            // 检查控制者是否是当前逻辑处理器
            if (pool.controller != exec.build) {
                unitVar.setobj("组已被使用");
                indexVar.setnum(-1);
                return;
            }
        }
        
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
        
        // 设置单位类型
        pool.type = unitType;
        pool.groupId = groupStr;
        
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
        handleIndexLogic(pool, unitVar, indexVar);
    }
    
   //索引处理逻辑
    private static void handleIndexLogic(UnitPool pool, LVar unitVar, LVar indexVar) {
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
    

   //获取单位池
    private static UnitPool getUnitPool(LExecutor exec, String groupStr) {
        if ("stand-alone".equals(groupStr)) {
            // 获取独立单位池
            ObjectMap<String, UnitPool> execPools = standalonePools.get(exec.hashCode());
            if (execPools != null) {
                return execPools.get(groupStr);
            }
        } else {
            // 获取共享单位池
            return sharedPools.get(groupStr);
        }
        return null;
    }
    
    // 获取或创建单位池
    private static UnitPool getOrCreateUnitPool(LExecutor exec, String groupStr) {
        if ("stand-alone".equals(groupStr)) {
            // 处理独立单位池
            ObjectMap<String, UnitPool> execPools = standalonePools.get(exec.hashCode(), ObjectMap::new);
            UnitPool pool = execPools.get(groupStr, UnitPool::new);
            execPools.put(groupStr, pool);
            standalonePools.put(exec.hashCode(), execPools);
            return pool;
        } else {
            // 处理共享单位池
            UnitPool pool = sharedPools.get(groupStr, UnitPool::new);
            sharedPools.put(groupStr, pool);
            return pool;
        }
    }
    
     //绑定方法：绑定指定类型（type），指定数量（count）到指定组索引（group）的单位池里
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
        
        // 检查是否受命令系统控制
        if (unit.controller() instanceof CommandAI) {
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
    

    //重置方法：重置指定组索引的单位池
    public static void resetUnitPool(String groupId) {
        UnitPool pool = sharedPools.get(groupId);
        if (pool != null) {
            // 解绑所有单位
            for (Unit unit : pool.units) {
                unbindUnit(unit);
            }
            // 清空单位池并设置为未使用状态
            pool.units.clear();
            pool.isUsed = false;
            pool.controller = null;
            pool.currentIndex = 0;
        }
    }
    
    //添加方法：通过指定组索引，创建一个单位池
    public static void addUnitPool(String groupId) {
        if (!sharedPools.containsKey(groupId) && !"stand-alone".equals(groupId)) {
            UnitPool newPool = new UnitPool();
            newPool.groupId = groupId;
            newPool.isUsed = false;
            newPool.controller = null;
            sharedPools.put(groupId, newPool);
        }
    }
    
    //删除方法：删除指定组索引的单位池
    public static void deleteUnitPool(String groupId) {
        UnitPool pool = sharedPools.get(groupId);
        if (pool != null && !"stand-alone".equals(groupId)) {
            // 解绑所有单位
            for (Unit unit : pool.units) {
                unbindUnit(unit);
            }
            // 从共享池移除
            sharedPools.remove(groupId);
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
            
            // 2. 控制方判断
            if (!(unit.controller() instanceof LogicAI) || 
                exec.build == null || 
                ((LogicAI)unit.controller()).controller != exec.build) {
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