package logicExtend;

import mindustry.logic.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.type.*;
import mindustry.Vars;

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
            LVar fromVar = this.fromVar != null ? builder.var(this.fromVar) : null;
            LVar itemVar = this.itemVar != null ? builder.var(this.itemVar) : null;
            LVar amountVar = this.amountVar != null ? builder.var(this.amountVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.object instanceof Unit ? (Unit)exec.unit.object : null;
                    if(unit == null) return;
                    
                    Building from = fromVar != null ? fromVar.object instanceof Building ? (Building)fromVar.object : null : null;
                    Item item = itemVar != null ? itemVar.object instanceof Item ? (Item)itemVar.object : null : null;
                    int amount = amountVar != null ? (int)amountVar.num : Integer.MAX_VALUE;
                    
                    if(from != null && from.team == unit.team && from.isValid() && from.items != null &&
                       item != null && unit.within(from, logicItemTransferRange + from.block.size * Vars.tilesize/2f)){
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
            LVar toVar = this.toVar != null ? builder.var(this.toVar) : null;
            LVar amountVar = this.amountVar != null ? builder.var(this.amountVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.object instanceof Unit ? (Unit)exec.unit.object : null;
                    if(unit == null) return;
                    
                    Building to = toVar != null ? toVar.object instanceof Building ? (Building)toVar.object : null : null;
                    int amount = amountVar != null ? (int)amountVar.num : Integer.MAX_VALUE;
                    
                    if(unit.item() != null) {
                        if(to == null) {
                            if(!Vars.net.client()) {
                                unit.clearItem();
                            }
                        } else if(to.team == unit.team && to.isValid()) {
                            int dropped = Math.min(unit.stack.amount, amount);
                            if(dropped > 0 && unit.within(to, logicItemTransferRange + to.block.size * Vars.tilesize/2f)) {
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
            LVar takeUnitsVar = this.takeUnitsVar != null ? builder.var(this.takeUnitsVar) : null;
            LVar xVar = this.xVar != null ? builder.var(this.xVar) : null;
            LVar yVar = this.yVar != null ? builder.var(this.yVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.object instanceof Unit ? (Unit)exec.unit.object : null;
                    if(unit == null) return;
                    
                    boolean takeUnits = takeUnitsVar != null ? takeUnitsVar.bool : false;
                    float x = xVar != null ? xVar.num : unit.x;
                    float y = yVar != null ? yVar.num : unit.y;
                    
                    if(unit.within(x, y, FIXED_RADIUS) && unit instanceof Payloadc pay) {
                        if(takeUnits) {
                            Unit result = mindustry.entities.Units.closest(unit.team, x, y, unit.type.hitSize * 2f, u -> 
                                u.isAI() && u.isGrounded() && pay.canPickup(u) && u.within(x, y, FIXED_RADIUS));
                            
                            if(result != null) {
                                Call.pickedUnitPayload(unit, result);
                            }
                        } else {
                            Building build = Vars.world.buildWorld(x, y);
                            
                            if(build != null && build.team == unit.team) {
                                mindustry.world.blocks.payloads.Payload current = build.getPayload();
                                if(current != null && pay.canPickupPayload(current)) {
                                    Call.pickedBuildPayload(unit, build, false);
                                } else if(build.block.buildVisibility != mindustry.world.meta.BuildVisibility.hidden && build.canPickup() && pay.canPickup(build)) {
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
            LVar xVar = this.xVar != null ? builder.var(this.xVar) : null;
            LVar yVar = this.yVar != null ? builder.var(this.yVar) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.object instanceof Unit ? (Unit)exec.unit.object : null;
                    if(unit == null) return;
                    
                    float x = xVar != null ? xVar.num : unit.x;
                    float y = yVar != null ? yVar.num : unit.y;
                    
                    if(unit.within(x, y, FIXED_RADIUS) && unit instanceof Payloadc pay && pay.hasPayload()) {
                        Call.payloadDropped(unit, x, y);
                    }
                }
            };
        }
    }
}