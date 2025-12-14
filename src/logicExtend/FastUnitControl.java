package logicExtend;

import arc.struct.ObjectMap;
import mindustry.logic.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.type.*;

public class FastUnitControl {
    
    // 固定半径值
    private static final float FIXED_RADIUS = 3f;
    // 游戏中的物品转移范围 - 与原版一致
    private static final float logicItemTransferRange = 3f;
    
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
                    
                    // 与原版一致的逻辑，只是去除了CD检查
                    if(from != null && from.team == unit.team && from.isValid() && from.items != null &&
                       item != null && unit.within(from, logicItemTransferRange + from.block.size * mindustry.Vars.tilesize/2f)){
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
                    
                    // 与原版一致的逻辑，只是去除了CD检查
                    if(unit.item() != null) {
                        // 向空气投放（清空物品）
                        if(to == null) {
                            //only server-side; no need to call anything, as items are synced in snapshots
                            if(!mindustry.Vars.net.client()) {
                                unit.clearItem();
                            }
                        } else if(to.team == unit.team && to.isValid()) {
                            int dropped = Math.min(unit.stack.amount, amount);
                            if(dropped > 0 && unit.within(to, logicItemTransferRange + to.block.size * mindustry.Vars.tilesize/2f)) {
                                int accepted = to.acceptStack(unit.item(), dropped, unit);
                                if(accepted > 0) {
                                    Call.transferItemTo(unit, unit.item(), accepted, unit.x, unit.y, to);
                                }
                            }
                        }
                    }
                }
            };
        }
    }
    
    /** 快速拿取载荷指令 - 支持指定坐标 */
    static class FastPayTakeStatement extends LStatement {
        String takeUnitsVar, xVar, yVar;
        
        public FastPayTakeStatement(String[] params) {
            if (params.length >= 3) takeUnitsVar = params[2];
            if (params.length >= 4) xVar = params[3];
            if (params.length >= 5) yVar = params[4];
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
            return "fastUnitControl paytake " + (takeUnitsVar != null ? takeUnitsVar : "") + " " + (xVar != null ? xVar : "") + " " + (yVar != null ? yVar : "");
        }
        
        @Override
        public LInstruction build(LAssembler builder) {
            int takeUnitsIndex = takeUnitsVar != null ? builder.var(takeUnitsVar) : -1;
            int xIndex = xVar != null ? builder.var(xVar) : -1;
            int yIndex = yVar != null ? builder.var(yVar) : -1;
            
            return new LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.unit();
                    boolean takeUnits = takeUnitsIndex != -1 ? exec.vars[takeUnitsIndex].bool() : false;
                    float x = xIndex != -1 ? exec.vars[xIndex].numf() : unit.x;
                    float y = yIndex != -1 ? exec.vars[yIndex].numf() : unit.y;
                    
                    // 检查单位是否在指定坐标范围内
                    if(unit.within(x, y, FIXED_RADIUS) && unit instanceof Payloadc pay) {
                        if(takeUnits) {
                            // 拿取单位
                            Unit result = mindustry.entities.Units.closest(unit.team, x, y, unit.type.hitSize * 2f, u -> 
                                u.isAI() && u.isGrounded() && pay.canPickup(u) && u.within(x, y, FIXED_RADIUS));
                            
                            if(result != null) {
                                Call.pickedUnitPayload(unit, result);
                            }
                        } else {
                            // 拿取建筑
                            Building build = mindustry.Vars.world.buildWorld(x, y);
                            
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
                }
            };
        }
    }
    
    /** 快速放下载荷指令 - 支持指定坐标 */
    static class FastPayDropStatement extends LStatement {
        String xVar, yVar;
        
        public FastPayDropStatement(String[] params) {
            if (params.length >= 3) xVar = params[2];
            if (params.length >= 4) yVar = params[3];
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
            return "fastUnitControl paydrop " + (xVar != null ? xVar : "") + " " + (yVar != null ? yVar : "");
        }
        
        @Override
        public LInstruction build(LAssembler builder) {
            int xIndex = xVar != null ? builder.var(xVar) : -1;
            int yIndex = yVar != null ? builder.var(yVar) : -1;
            
            return new LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.unit();
                    float x = xIndex != -1 ? exec.vars[xIndex].numf() : unit.x;
                    float y = yIndex != -1 ? exec.vars[yIndex].numf() : unit.y;
                    
                    // 检查单位是否在指定坐标范围内
                    if(unit.within(x, y, FIXED_RADIUS) && unit instanceof Payloadc pay && pay.hasPayload()) {
                        Call.payloadDropped(unit, x, y);
                    }
                }
            };
        }
    }
}