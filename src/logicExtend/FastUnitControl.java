package logicExtend;

import arc.struct.ObjectMap;
import mindustry.logic.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.type.*;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.meta.BuildVisibility;

public class FastUnitControl {
    
    // 固定半径值
    private static final float FIXED_RADIUS = 5f;
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
    
    /** 快速拿取物品指令 - 与原版一致 */
    static class FastItemTakeStatement extends LStatement {
        String fromVar, itemVar, amountVar;
        
        public FastItemTakeStatement(String[] params) {
            if (params.length >= 3) fromVar = params[2];
            if (params.length >= 4) itemVar = params[3];
            if (params.length >= 5) amountVar = params[4];
        }
        
        @Override
        public void build(arc.scene.ui.layout.Table table) {
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
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar fromVarObj = fromVar != null ? builder.var(fromVar) : null;
            LVar itemVarObj = itemVar != null ? builder.var(itemVar) : null;
            LVar amountVarObj = amountVar != null ? builder.var(amountVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    Building from = fromVarObj != null ? fromVarObj.building() : null;
                    Item item = itemVarObj != null ? itemVarObj.obj() instanceof Item ? (Item)itemVarObj.obj() : null : null;
                    int amount = amountVarObj != null ? (int)amountVarObj.numi() : Integer.MAX_VALUE;
                    
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
    
    /** 快速投放物品指令 - 与原版一致 */
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
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar toVarObj = toVar != null ? builder.var(toVar) : null;
            LVar amountVarObj = amountVar != null ? builder.var(amountVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    Building to = toVarObj != null ? toVarObj.building() : null;
                    int amount = amountVarObj != null ? (int)amountVarObj.numi() : Integer.MAX_VALUE;
                    
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
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar takeUnitsVarObj = takeUnitsVar != null ? builder.var(takeUnitsVar) : null;
            LVar xVarObj = xVar != null ? builder.var(xVar) : null;
            LVar yVarObj = yVar != null ? builder.var(yVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    boolean takeUnits = takeUnitsVarObj != null ? takeUnitsVarObj.bool() : false;
                    float x = xVarObj != null ? xVarObj.numf() : unit.x;
                    float y = yVarObj != null ? yVarObj.numf() : unit.y;
                    
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
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar xVarObj = xVar != null ? builder.var(xVar) : null;
            LVar yVarObj = yVar != null ? builder.var(yVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    float x = xVarObj != null ? xVarObj.numf() : unit.x;
                    float y = yVarObj != null ? yVarObj.numf() : unit.y;
                    
                    // 检查单位是否在指定坐标范围内
                    if(unit.within(x, y, FIXED_RADIUS) && unit instanceof Payloadc pay && pay.hasPayload()) {
                        Call.payloadDropped(unit, x, y);
                    }
                }
            };
        }
    }
}