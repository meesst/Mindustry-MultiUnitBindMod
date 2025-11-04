package logicExtend;

import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.Label;
import arc.scene.ui.TextField;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.scene.style.TextureRegionDrawable;
import arc.math.geom.Vec2;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.UnitType;
import mindustry.game.Team;
import mindustry.Vars;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ai.types.LogicAI;

public class LUnitBindGroup {
    // 常量定义
    private static final float iconSmall = 24f;
    
    public static class UnitBindGroupStatement extends LStatement {
        public String unitType = "@poly", count = "10", unitVar = "currentUnit", indexVar = "unitIndex";
        
        // 实现tooltip方法，用于为标签添加悬浮提示
        private void tooltip(Cell<Label> labelCell, String text) {
            if (labelCell != null && labelCell.get() != null) {
                labelCell.tooltip(text);
            }
        }
        
        // 实现showSelectTable方法，用于显示单位类型选择对话框
        private void showSelectTable(Cell<?> button, UnitTypeSelectListener listener) {
            BaseDialog dialog = new BaseDialog("选择单位类型");
            dialog.addCloseButton();
            
            listener.build(dialog.cont, () -> dialog.hide());
            dialog.show();
        }
        
        // 接口定义，用于构建选择表格内容
        private interface UnitTypeSelectListener {
            void build(Table table, Runnable hide);
        }
        
        @Override
        public void build(Table table) {
            // 单位类型参数
            Cell<Label> typeLabel = table.add("type");
            // 移除错误的悬浮提示，与原始unit bind功能保持一致
            
            TextField field = field(table, unitType, str -> unitType = str).get();
            
            table.button(b -> {
                b.image(Icon.pencilSmall);
                b.clicked(() -> showSelectTable(b, (t, hide) -> {
                    t.row();
                    t.table(i -> {
                        i.left();
                        int c = 0;
                        for(UnitType item : Vars.content.units()){
                            if(!item.unlockedNow() || item.isHidden() || !item.logicControllable) continue;
                            i.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconSmall, () -> {
                                unitType = "@" + item.name;
                                field.setText(unitType);
                                hide.run();
                            }).size(40f);

                            if(++c % 6 == 0) i.row();
                        }
                    }).colspan(3).width(240f).left();
                }));
            }, Styles.logict, () -> {}).size(40f).padLeft(-2).color(table.color);
            
            // 数量参数
            Cell<Label> countLabel = table.add("count");
            tooltip(countLabel, "最大数量: 指定要绑定的最大单位数量");
            field(table, count, str -> count = str);
            
            // 单位变量参数
            Cell<Label> unitVarLabel = table.add("unitVar");
            tooltip(unitVarLabel, "单位变量: 存储当前选中单位的变量名");
            field(table, unitVar, str -> unitVar = str);
            
            // 索引变量参数
            Cell<Label> indexVarLabel = table.add("indexVar");
            tooltip(indexVarLabel, "索引变量: 存储当前单位索引的变量名（从1开始）");
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
        
        /** Anuken, if you see this, you can replace it with your own @RegisterStatement, because this is my last resort... **/
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
        
        @Override
        public void write(StringBuilder builder) {
            builder.append("ubindgroup ").append(unitType).append(" ").append(count).append(" ")
                   .append(unitVar).append(" ").append(indexVar);
        }
    }
    
    public static class UnitBindGroupInstruction implements LExecutor.LInstruction {
        private final LVar unitType;
        private final LVar count;
        private final LVar unitVar;
        private final LVar indexVar;
        
        // 存储每个逻辑控制器的单位组和当前索引
        private static final ObjectMap<Building, UnitGroupInfo> groups = new ObjectMap<>();
        
        public UnitBindGroupInstruction(LVar unitType, LVar count, LVar unitVar, LVar indexVar) {
            this.unitType = unitType;
            this.count = count;
            this.unitVar = unitVar;
            this.indexVar = indexVar;
        }
        
        @Override
        public void run(LExecutor exec) {
            // 获取当前逻辑控制器
            Building controller = exec.build;
            
            // 获取或创建单位组信息
            UnitGroupInfo info = groups.get(controller);
            if (info == null) {
                info = new UnitGroupInfo();
                groups.put(controller, info);
            }
            
            // 获取单位类型和数量参数
            Object typeObj = unitType.obj();
            int maxCount = Math.max(1, (int)count.num());
            
            // 确保单位组是最新的，传入控制器信息用于状态检查
            updateUnitGroup(info, typeObj, maxCount, exec.team, controller);
            
            // 循环遍历单位组
            if (!info.units.isEmpty()) {
                // 更新当前索引
                info.currentIndex = (info.currentIndex + 1) % info.units.size;
                
                // 获取当前单位
                Unit unit = info.units.get(info.currentIndex);
                
                // 写入返回变量
                unitVar.setobj(unit);
                
                // 如果指定了索引变量，写入单位索引
                if (indexVar != null) {
                    indexVar.setnum(info.currentIndex + 1); // 从1开始计数
                }
            } else {
                // 没有找到单位，清空返回变量
            unitVar.setobj(null);
            if (indexVar != null) {
                indexVar.setnum(0);
            }
            }
        }
        
        // 更新单位组
        private void updateUnitGroup(UnitGroupInfo info, Object typeObj, int maxCount, Team team, Building controller) {
            // 记录更新前的单位数量，用于检测变化
            int previousSize = info.units.size;
            
            // 彻底清理无效单位，确保只保留符合所有条件的单位
            Seq<Unit> validUnits = new Seq<>();
            for (Unit unit : info.units) {
                // 全面检查单位状态
                if (isValidAndNotControlled(unit, controller)) {
                    validUnits.add(unit);
                    // 重新锁定有效的单位，确保控制关系持续存在
                    lockUnit(unit, controller);
                }
            }
            info.units = validUnits;
            
            // 检查是否有单位数量减少或状态变化
            boolean needSupplementation = info.units.size < previousSize || info.units.size < maxCount;
            
            // 如果需要补充单位
            if (needSupplementation) {
                // 获取符合条件的所有可用单位
                Seq<Unit> availableUnits = collectAvailableUnits(typeObj, team, controller);
                
                // 从可用单位中补充到单位池
                int needed = maxCount - info.units.size;
                int added = 0;
                
                for (Unit unit : availableUnits) {
                    // 确保单位尚未在池中
                    if (!info.units.contains(unit)) {
                        info.units.add(unit);
                        // 锁定新加入的单位
                        lockUnit(unit, controller);
                        added++;
                        
                        if (added >= needed) break;
                    }
                }
            }
        }
        
        // 收集所有符合条件的可用单位
        private Seq<Unit> collectAvailableUnits(Object typeObj, Team team, Building controller) {
            Seq<Unit> result = new Seq<>();
            
            if (typeObj instanceof UnitType type && type.logicControllable) {
                // 针对特定单位类型
                Seq<Unit> units = team.data().unitCache(type);
                if (units != null) {
                    for (Unit unit : units) {
                        if (isValidAndNotControlled(unit, controller)) {
                            result.add(unit);
                        }
                    }
                }
            } else if (typeObj instanceof String && ((String)typeObj).equals("@poly")) {
                // 处理@poly类型，表示任意可控制单位
                for (Unit unit : team.data().units) {
                    if (unit.type.logicControllable && isValidAndNotControlled(unit, controller)) {
                        result.add(unit);
                    }
                }
            }
            
            return result;
        }
        
        // 检查单位是否有效且未被其他处理器控制
        private boolean isValidAndNotControlled(Unit unit, Building controller) {
            if (!unit.isValid() || unit.team != controller.team) return false;
            
            // 检查单位是否死亡（@dead）
            if (unit.dead) return false;
            
            // 检查单位是否被玩家附身
            if (unit.isPlayer()) return false;
            
            // 检查单位是否被玩家操控（处于编队中）
                if (unit.isPlayer()) return false;
            
            // 检查单位是否被其他处理器控制（@controlled）
            Building controlling = unit.controller() instanceof Building ? (Building)unit.controller() : null;
            return controlling == null || controlling == controller;
        }
        
        // 锁定单位，与ucontrol within指令效果相似
        private void lockUnit(Unit unit, Building controller) {
            if (!unit.isValid()) return;
            
            // 设置单位的控制器为当前处理器，与ucontrol指令效果一致
            // 使用LogicAI来控制单位，而不是直接使用Building
            if(unit.controller() instanceof LogicAI la){
                la.controller = controller;
            }else{
                var la = new LogicAI();
                la.controller = controller;
                
                unit.controller(la);
                //clear old state
                unit.mineTile = null;
                unit.clearBuilding();
            }
            
            // 设置单位的控制目标为处理器位置，模拟within区域锁定效果
            unit.command().commandPosition(new Vec2(controller.x, controller.y));
        }
    }
    
    // 单位组信息类
    private static class UnitGroupInfo {
        public Seq<Unit> units = new Seq<>();      // 单位列表
        public int currentIndex = -1;              // 当前单位索引
    }
}