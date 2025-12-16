package logicExtend;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;

import mindustry.ai.types.LogicAI;
import mindustry.core.World;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.world.*;
import mindustry.type.*;
import mindustry.entities.units.BuildPlan;
import mindustry.ui.Styles;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.meta.BuildVisibility;
import static mindustry.logic.LCanvas.tooltip;
import static mindustry.Vars.*;
import static mindustry.content.Blocks.*;

public class FastUnitControl {
    
    // 原版中logicItemTransferRange已经是世界单位，直接使用Vars中的定义
    
    /** fastUnitControl指令的分支枚举 */
    public enum FastUnitControlType {
        itemTake("from", "item", "amount"),
        itemDrop("to", "amount"),
        payTake("takeUnits", "x", "y"),
        payDrop("x", "y"),
        assist("assister", "target");
        
        public final String[] params;
        
        FastUnitControlType(String... params) {
            this.params = params;
        }
    }
    
    /** 快速单位控制指令类 */
    public static class FastUnitControlStatement extends LStatement {
        public FastUnitControlType type = FastUnitControlType.itemTake;
        public String p1 = "0", p2 = "0", p3 = "0";
        
        @Override
        public void build(Table table) {
            rebuild(table);
        }
        
        private void rebuild(Table table) {
            table.clearChildren();
            table.left();
            
            table.add(" ");
            
            // 分支选择按钮
            table.button(b -> {
                b.label(() -> type.name());
                b.clicked(() -> showSelect(b, FastUnitControlType.values(), type, t -> {
                    type = t;
                    rebuild(table);
                }, 2, cell -> cell.size(120, 50)));
            }, Styles.logict, () -> {}).size(120, 40).color(table.color).left().padLeft(2);
            
            row(table);
            
            // 根据选择的分支显示不同的参数
            int c = 0;
            for(int i = 0; i < type.params.length; i++) {
                final int index = i;
                
                fields(table, type.params[i], index == 0 ? p1 : index == 1 ? p2 : p3, index == 0 ? v -> p1 = v : index == 1 ? v -> p2 = v : v -> p3 = v).width(100f);
                
                if(++c % 2 == 0) row(table);
            }
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public String toString() {
            return "fastUnitControl " + type.name() + " " + p1 + " " + p2 + " " + p3;
        }
        
        @Override
        public void write(StringBuilder builder) {
            builder.append("fastunitcontrol ").append(type.name())
                   .append(" ").append(p1)
                   .append(" ").append(p2)
                   .append(" ").append(p3);
        }
        
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar p1Var = p1 != null && !p1.isEmpty() ? builder.var(p1) : null;
            LVar p2Var = p2 != null && !p2.isEmpty() ? builder.var(p2) : null;
            LVar p3Var = p3 != null && !p3.isEmpty() ? builder.var(p3) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Object unitObj = exec.unit.obj();
                    // 调用原版的checkLogicAI方法，确保单位处于逻辑控制之下
                    LogicAI ai = LExecutor.UnitControlI.checkLogicAI(exec, unitObj);
                    
                    // 只有控制标准AI单位
                    if(unitObj instanceof Unit unit && ai != null){
                        // 更新控制计时器，保持逻辑控制状态
                        ai.controlTimer = LogicAI.logicControlTimeout;
                        
                        switch(type) {
                            case itemTake:
                                Building from = p1Var != null ? p1Var.building() : null;
                                Item item = p2Var != null ? p2Var.obj() instanceof Item ? (Item)p2Var.obj() : null : null;
                                int amount = p3Var != null ? (int)p3Var.numi() : 1;
                                
                                // 与原版一致的逻辑，只是去除了CD检查
                                if(from != null && from.team == unit.team && from.isValid() && from.items != null &&
                                   item != null && unit.within(from, logicItemTransferRange + from.block.size * tilesize/2f)){
                                    int taken = Math.min(from.items.get(item), Math.min(amount, unit.maxAccepted(item)));
                                    if(taken > 0) {
                                        Call.takeItems(from, item, taken, unit);
                                    }
                                }
                                break;
                                
                            case itemDrop:
                                Building to = p1Var != null ? p1Var.building() : null;
                                int dropAmount = p2Var != null ? (int)p2Var.numi() : 1;
                                
                                // 与原版一致的逻辑，只是去除了CD检查
                                if(unit.item() != null) {
                                    //clear item when dropping to @air
                                    if(p1Var.obj() == air) {
                                        //only server-side; no need to call anything, as items are synced in snapshots
                                        if(!net.client()) {
                                            unit.clearItem();
                                        }
                                    } else if(to != null && to.team == unit.team && to.isValid()) {
                                        int dropped = Math.min(unit.stack.amount, dropAmount);
                                        if(dropped > 0 && unit.within(to, logicItemTransferRange + to.block.size * tilesize/2f)) {
                                            int accepted = to.acceptStack(unit.item(), dropped, unit);
                                            if(accepted > 0) {
                                                Call.transferItemTo(unit, unit.item(), accepted, unit.x, unit.y, to);
                                            }
                                        }
                                    }
                                }
                                break;
                                
                            case payTake:
                                boolean takeUnits = p1Var != null ? p1Var.bool() : false;
                                float x = World.unconv(p2Var != null ? p2Var.numf() : World.conv(unit.x));
                                float y = World.unconv(p3Var != null ? p3Var.numf() : World.conv(unit.y));
                                
                                if(unit.within(x, y, unit.type.hitSize * 2f) && unit instanceof Payloadc pay) {
                                    if(takeUnits) {
                                        Unit result = mindustry.entities.Units.closest(unit.team, x, y, unit.type.hitSize * 2f, u -> 
                                            u.isAI() && u.isGrounded() && pay.canPickup(u) && u.within(x, y, u.hitSize + unit.hitSize * 1.2f));
                                        
                                        if(result != null) {
                                            Call.pickedUnitPayload(unit, result);
                                        }
                                    } else {
                                        Building build = world.buildWorld(x, y);
                                        
                                        if(build != null && build.team == unit.team) {
                                            Payload current = build.getPayload();
                                            if(current != null && pay.canPickupPayload(current)) {
                                                Call.pickedBuildPayload(unit, build, false);
                                            } else if(build.block.buildVisibility != BuildVisibility.hidden && build.canPickup() && pay.canPickup(build)) {
                                                Call.pickedBuildPayload(unit, build, true);
                                            }
                                        }
                                    }
                                }
                                break;
                                
                            case payDrop:
                                float dropX = World.unconv(p1Var != null ? p1Var.numf() : World.conv(unit.x));
                                float dropY = World.unconv(p2Var != null ? p2Var.numf() : World.conv(unit.y));
                                
                                if(unit instanceof Payloadc pay && pay.hasPayload()) {
                                    Call.payloadDropped(unit, dropX, dropY);
                                }
                                break;
                                
                            case assist:
                                // 获取协助者和目标单位
                                Object assisterObj = p1Var != null ? p1Var.obj() : null;
                                Object targetObj = p2Var != null ? p2Var.obj() : null;
                                
                                // 检查参数类型
                                if(assisterObj instanceof Unit assister && targetObj instanceof Unit target) {
                                    // 检查单位是否有效
                                    if(assister.isValid() && target.isValid()) {
                                        // 检查单位是否属于同一队伍
                                        if(assister.team() == target.team()) {
                                            // 设置unit.updateBuilding = true，允许移动时建造
                                            assister.updateBuilding = true;
                                            
                                            // 在execCache中存储协助目标
                                            ai.execCache.put("assistTarget", target);
                                            
                                            // 执行协助建造逻辑
                                            if(target.activelyBuilding()) {
                                                // 复制目标单位的建造计划，包括breaking状态
                                                assister.plans.clear();
                                                BuildPlan targetPlan = target.buildPlan();
                                                if(targetPlan != null) {
                                                    // 使用copy()方法复制BuildPlan，确保所有状态都被复制
                                                    BuildPlan assistPlan = targetPlan.copy();
                                                    // 显式复制breaking状态，确保正确
                                                    assistPlan.breaking = targetPlan.breaking;
                                                    assister.plans.addFirst(assistPlan);
                                                }
                                            }
                                        }
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }
            };
        }
    }
    
    /** 注册自定义指令 */
    public static void create() {
        // 注册fastunitcontrol指令解析器
        LAssembler.customParsers.put("fastunitcontrol", params -> {
            FastUnitControlStatement stmt = new FastUnitControlStatement();
            
            if (params.length > 1) {
                try {
                    stmt.type = FastUnitControlType.valueOf(params[1]);
                } catch (IllegalArgumentException e) {
                        stmt.type = FastUnitControlType.itemTake;
                    }
            }
            
            if (params.length >= 3) stmt.p1 = params[2];
            if (params.length >= 4) stmt.p2 = params[3];
            if (params.length >= 5) stmt.p3 = params[4];
            
            stmt.afterRead();
            return stmt;
        });
        
        // 将指令添加到逻辑IO的所有语句列表中
        LogicIO.allStatements.add(FastUnitControlStatement::new);
    }
}