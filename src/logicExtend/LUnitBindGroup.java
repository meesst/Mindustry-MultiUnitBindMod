package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.UnitType;

public class LUnitBindGroup {
    
    public static class UnitBindGroupStatement extends LStatement {
        public String unitType = "@poly", count = "10", unitVar = "currentUnit", indexVar = "unitIndex";
        
        @Override
        public void build(Table table) {
            table.add("type");
            field(table, unitType, str -> unitType = str);
            
            table.add("count");
            field(table, count, str -> count = str);
            
            table.add("unitVar");
            field(table, unitVar, str -> unitVar = str);
            
            table.add("indexVar");
            field(table, indexVar, str -> indexVar = str);
        }
        
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new UnitBindGroupInstruction(
                builder.var(unitType),
                builder.var(count),
                builder.var(unitVar),
                indexVar.isEmpty() ? null : builder.var(indexVar)
            );
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        public static void create() {
            LAssembler.customParsers.put("ubindgroup", params -> {
                UnitBindGroupStatement stmt = new UnitBindGroupStatement();
                if (params.length >= 2) stmt.unitType = params[1];
                if (params.length >= 3) stmt.count = params[2];
                if (params.length >= 4) stmt.unitVar = params[3];
                if (params.length >= 5) stmt.indexVar = params[4];
                stmt.afterRead();
                return stmt;
            });
            LogicIO.allStatements.add(UnitBindGroupStatement::new);
        }
    }
    
    public static class UnitBindGroupInstruction implements LExecutor.LInstruction {
        private final LVar unitType;
        private final LVar count;
        private final LVar unitVar;
        private final LVar indexVar;
        
        // 存储每个逻辑控制器的单位组和当前索引
        private static final ObjectMap<LogicControllable, UnitGroupInfo> groups = new ObjectMap<>();
        
        public UnitBindGroupInstruction(LVar unitType, LVar count, LVar unitVar, LVar indexVar) {
            this.unitType = unitType;
            this.count = count;
            this.unitVar = unitVar;
            this.indexVar = indexVar;
        }
        
        @Override
        public void run(LExecutor exec) {
            // 获取当前逻辑控制器
            LogicControllable controller = exec.build;
            
            // 获取或创建单位组信息
            UnitGroupInfo info = groups.get(controller);
            if (info == null) {
                info = new UnitGroupInfo();
                groups.put(controller, info);
            }
            
            // 获取单位类型和数量参数
            Object typeObj = unitType.obj();
            int maxCount = Math.max(1, (int)count.num());
            
            // 确保单位组是最新的
            updateUnitGroup(info, typeObj, maxCount, exec.team);
            
            // 循环遍历单位组
            if (!info.units.isEmpty()) {
                // 更新当前索引
                info.currentIndex = (info.currentIndex + 1) % info.units.size;
                
                // 获取当前单位
                Unit unit = info.units.get(info.currentIndex);
                
                // 写入返回变量
                exec.setVariable(unitVar.name, unit);
                
                // 如果指定了索引变量，写入单位索引
                if (indexVar != null) {
                    exec.setVariable(indexVar.name, info.currentIndex + 1); // 从1开始计数
                }
            } else {
                // 没有找到单位，清空返回变量
                exec.setVariable(unitVar.name, null);
                if (indexVar != null) {
                    exec.setVariable(indexVar.name, 0);
                }
            }
        }
        
        // 更新单位组
        private void updateUnitGroup(UnitGroupInfo info, Object typeObj, int maxCount, Team team) {
            // 清除无效单位并重新填充
            info.units.clear();
            
            if (typeObj instanceof UnitType type && type.logicControllable) {
                Seq<Unit> units = team.data().unitCache(type);
                if (units != null) {
                    for (Unit unit : units) {
                        if (unit.isValid() && unit.team == team) {
                            info.units.add(unit);
                            if (info.units.size >= maxCount) break;
                        }
                    }
                }
            }
        }
    }
    
    // 单位组信息类
    private static class UnitGroupInfo {
        public Seq<Unit> units = new Seq<>();      // 单位列表
        public int currentIndex = -1;              // 当前单位索引
    }
}