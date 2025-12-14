package logicExtend;

import arc.struct.ObjectMap;
import mindustry.logic.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.type.*;

public class FastUnitControl {
    
    // 固定半径值
    private static final float FIXED_RADIUS = 5f;
    
    /** 注册自定义指令 */
    public static void create() {
        // 注册fastUnitControl指令解析器
        LAssembler.customParsers.put("fastunitcontrol", params -> {
            // 默认分支是itemtake
            String branch = params.length > 1 ? params[1] : "itemtake";
            
            switch(branch.toLowerCase()) {
                case "itemdrop":
                    return new FastItemDropStatement(params);
                case "paytake":
                    return new FastPayTakeStatement(params);
                case "paydrop":
                    return new FastPayDropStatement(params);
                default: // itemtake (默认)
                    return new FastItemTakeStatement(params);
            }
        });
    }
    
    /** 快速拿取物品指令 */
    static class FastItemTakeStatement extends LStatement {
        String fromVar, itemVar, amountVar;
        
        public FastItemTakeStatement(String[] params) {
            if (params.length >= 3) fromVar = params[2];
            if (params.length >= 4) itemVar = params[3];
            if (params.length >= 5) amountVar = params[4];
        }
        
        @Override
        public void build(arc.scene.ui.layout.Table table) {
            // 简单的UI构建
            table.add("Fast Item Take:").left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public String toString() {
            return "fastUnitControl itemtake " + (fromVar != null ? fromVar : "") + " " + (itemVar != null ? itemVar : "") + " " + (amountVar != null ? amountVar : "");
        }
        
        @Override
        public LInstruction build(LAssembler builder) {
            int fromIndex = fromVar != null ? builder.var(fromVar) : -1;
            int itemIndex = itemVar != null ? builder.var(itemVar) : -1;
            int amountIndex = amountVar != null ? builder.var(amountVar) : -1;
            
            return new LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.unit();
                    Building from = fromIndex != -1 ? exec.vars[fromIndex].building() : null;
                    Item item = itemIndex != -1 ? exec.vars[itemIndex].obj() instanceof Item ? (Item)exec.vars[itemIndex].obj() : null : null;
                    int amount = amountIndex != -1 ? (int)exec.vars[amountIndex].numi() : Integer.MAX_VALUE;
                    
                    if(from != null && from.team == unit.team && from.isValid() && item != null && from.items != null) {
                        int taken = Math.min(from.items.get(item), Math.min(amount, unit.maxAccepted(item)));
                        if(taken > 0) {
                            Call.takeItems(from, item, taken, unit);
                        }
                    }
                }
            };
        }
    }
    
    /** 快速投放物品指令 */
    static class FastItemDropStatement extends LStatement {
        String toVar, amountVar;
        
        public FastItemDropStatement(String[] params) {
            if (params.length >= 3) toVar = params[2];
            if (params.length >= 4) amountVar = params[3];
        }
        
        @Override
        public void build(arc.scene.ui.layout.Table table) {
            table.add("Fast Item Drop:").left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public String toString() {
            return "fastUnitControl itemdrop " + (toVar != null ? toVar : "") + " " + (amountVar != null ? amountVar : "");
        }
        
        @Override
        public LInstruction build(LAssembler builder) {
            int toIndex = toVar != null ? builder.var(toVar) : -1;
            int amountIndex = amountVar != null ? builder.var(amountVar) : -1;
            
            return new LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.unit();
                    Building to = toIndex != -1 ? exec.vars[toIndex].building() : null;
                    int amount = amountIndex != -1 ? (int)exec.vars[amountIndex].numi() : Integer.MAX_VALUE;
                    
                    if(to != null && to.team == unit.team && to.isValid() && unit.item() != null) {
                        int dropped = Math.min(unit.stack.amount, amount);
                        if(dropped > 0) {
                            int accepted = to.acceptStack(unit.item(), dropped, unit);
                            if(accepted > 0) {
                                Call.transferItemTo(unit, unit.item(), accepted, unit.x, unit.y, to);
                            }
                        }
                    }
                }
            };
        }
    }
    
    /** 快速拿取载荷指令 */
    static class FastPayTakeStatement extends LStatement {
        String takeUnitsVar;
        
        public FastPayTakeStatement(String[] params) {
            if (params.length >= 3) takeUnitsVar = params[2];
        }
        
        @Override
        public void build(arc.scene.ui.layout.Table table) {
            table.add("Fast Pay Take:").left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public String toString() {
            return "fastUnitControl paytake " + (takeUnitsVar != null ? takeUnitsVar : "");
        }
        
        @Override
        public LInstruction build(LAssembler builder) {
            int takeUnitsIndex = takeUnitsVar != null ? builder.var(takeUnitsVar) : -1;
            
            return new LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.unit();
                    boolean takeUnits = takeUnitsIndex != -1 ? exec.vars[takeUnitsIndex].bool() : false;
                    
                    if(unit instanceof Payloadc pay) {
                        if(takeUnits) {
                            // 拿取单位
                            Unit result = mindustry.entities.Units.closest(unit.team, unit.x, unit.y, unit.type.hitSize * 2f, u -> 
                                u.isAI() && u.isGrounded() && pay.canPickup(u) && u.within(unit, FIXED_RADIUS));
                            
                            if(result != null) {
                                Call.pickedUnitPayload(unit, result);
                            }
                        } else {
                            // 拿取建筑
                            Building build = mindustry.Vars.world.buildWorld(unit.x, unit.y);
                            
                            if(build != null && build.team == unit.team && build.within(unit, FIXED_RADIUS)) {
                                Payload current = build.getPayload();
                                if(current != null && pay.canPickupPayload(current)) {
                                    Call.pickedBuildPayload(unit, build, false);
                                } else if(build.block.buildVisibility != BuildVisibility.hidden && build.canPickup() && pay.canPickup(build)) {
                                    Call.pickedBuildPayload(unit, build, true);
                                }
                            }
                        }
                    }
                }
            };
        }
    }
    
    /** 快速放下载荷指令 */
    static class FastPayDropStatement extends LStatement {
        
        public FastPayDropStatement(String[] params) {
            // 无参数
        }
        
        @Override
        public void build(arc.scene.ui.layout.Table table) {
            table.add("Fast Pay Drop:").left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public String toString() {
            return "fastUnitControl paydrop";
        }
        
        @Override
        public LInstruction build(LAssembler builder) {
            return new LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.unit();
                    
                    if(unit instanceof Payloadc pay && pay.hasPayload()) {
                        Call.payloadDropped(unit, unit.x, unit.y);
                    }
                }
            };
        }
    }
}