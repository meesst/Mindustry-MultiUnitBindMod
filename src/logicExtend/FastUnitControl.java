package logicExtend;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;

import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.world.*;
import mindustry.type.*;
import mindustry.ui.Styles;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.meta.BuildVisibility;
import static mindustry.logic.LCanvas.tooltip;
import static mindustry.Vars.*;
import static mindustry.content.Blocks.*;

public class FastUnitControl {
    
    // 固定半径值
    private static final float FIXED_RADIUS = 5f;
    // 游戏中的物品转移范围 - 与原版一致
    private static final float logicItemTransferRange = 3f;
    
    /** fastUnitControl指令的分支枚举 */
    public enum FastUnitControlType {
        itemTake("from", "item", "amount"),
        itemDrop("to", "amount"),
        payTake("takeUnits", "x", "y"),
        payDrop("x", "y");
        
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
                            float x = p2Var != null ? p2Var.numf() : unit.x;
                            float y = p3Var != null ? p3Var.numf() : unit.y;
                            
                            if(unit.within(x, y, FIXED_RADIUS) && unit instanceof Payloadc pay) {
                                if(takeUnits) {
                                    Unit result = mindustry.entities.Units.closest(unit.team, x, y, unit.type.hitSize * 2f, u -> 
                                        u.isAI() && u.isGrounded() && pay.canPickup(u) && u.within(x, y, FIXED_RADIUS));
                                    
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
                            float dropX = p1Var != null ? p1Var.numf() : unit.x;
                            float dropY = p2Var != null ? p2Var.numf() : unit.y;
                            
                            if(unit.within(dropX, dropY, FIXED_RADIUS) && unit instanceof Payloadc pay && pay.hasPayload()) {
                                Call.payloadDropped(unit, dropX, dropY);
                            }
                            break;
                        default:
                            break;
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