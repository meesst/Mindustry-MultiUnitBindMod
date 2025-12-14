package logicExtend;

import arc.struct.ObjectMap;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.Drawable;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.world.*;
import mindustry.type.*;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.meta.BuildVisibility;
import static mindustry.logic.LCanvas.tooltip;
import static mindustry.Vars.*;

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
        
        // 将所有指令分支添加到逻辑IO的所有语句列表中
        LogicIO.allStatements.add(FastItemTakeStatement::new);
        LogicIO.allStatements.add(FastItemDropStatement::new);
        LogicIO.allStatements.add(FastPayTakeStatement::new);
        LogicIO.allStatements.add(FastPayDropStatement::new);
    }
    
    /** 快速拿取物品指令 - 与原版一致 */
    static class FastItemTakeStatement extends LStatement {
        /** 从哪个建筑拿取 */
        public String from = "";
        /** 拿取什么物品 */
        public String item = "";
        /** 拿取多少 */
        public String amount = "1";
        
        /** 构造函数 */
        public FastItemTakeStatement() {}
        
        /** 从参数创建 */
        public FastItemTakeStatement(String[] params) {
            if (params.length >= 3) from = params[2];
            if (params.length >= 4) item = params[3];
            if (params.length >= 5) amount = params[4];
            afterRead();
        }
        
        @Override
        public void build(Table table) {
            table.clearChildren();
            table.left();
            
            table.table(t -> {
                t.setColor(table.color);
                
                t.add(" from ").left().self(c -> tooltip(c, "fastunitcontrol.itemtake.from"));
                field(t, from, str -> from = str);
                
                t.add(" item ").left().self(c -> tooltip(c, "fastunitcontrol.itemtake.item"));
                field(t, item, str -> item = str);
                
                t.add(" amount ").left().self(c -> tooltip(c, "fastunitcontrol.itemtake.amount"));
                field(t, amount, str -> amount = str);
            }).left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public void write(StringBuilder builder) {
            builder.append("fastunitcontrol itemtake ")
                   .append(from).append(" ")
                   .append(item).append(" ")
                   .append(amount);
        }
        
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar fromVar = from != null && !from.isEmpty() ? builder.var(from) : null;
            LVar itemVar = item != null && !item.isEmpty() ? builder.var(item) : null;
            LVar amountVar = amount != null && !amount.isEmpty() ? builder.var(amount) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    Building from = fromVar != null ? fromVar.building() : null;
                    Item item = itemVar != null ? itemVar.obj() instanceof Item ? (Item)itemVar.obj() : null : null;
                    int amount = amountVar != null ? (int)amountVar.numi() : 1;
                    
                    // 与原版一致的逻辑，只是去除了CD检查
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
        /** 投放到哪个建筑 */
        public String to = "";
        /** 投放多少 */
        public String amount = "1";
        
        /** 构造函数 */
        public FastItemDropStatement() {}
        
        /** 从参数创建 */
        public FastItemDropStatement(String[] params) {
            if (params.length >= 3) to = params[2];
            if (params.length >= 4) amount = params[3];
            afterRead();
        }
        
        @Override
        public void build(Table table) {
            table.clearChildren();
            table.left();
            
            table.table(t -> {
                t.setColor(table.color);
                
                t.add(" to ").left().self(c -> tooltip(c, "fastunitcontrol.itemdrop.to"));
                field(t, to, str -> to = str);
                
                t.add(" amount ").left().self(c -> tooltip(c, "fastunitcontrol.itemdrop.amount"));
                field(t, amount, str -> amount = str);
            }).left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public void write(StringBuilder builder) {
            builder.append("fastunitcontrol itemdrop ")
                   .append(to).append(" ")
                   .append(amount);
        }
        
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar toVar = to != null && !to.isEmpty() ? builder.var(to) : null;
            LVar amountVar = amount != null && !amount.isEmpty() ? builder.var(amount) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    Building to = toVar != null ? toVar.building() : null;
                    int amount = amountVar != null ? (int)amountVar.numi() : 1;
                    
                    // 与原版一致的逻辑，只是去除了CD检查
                    if(unit.item() != null) {
                        // 向空气投放（清空物品）
                        if(to == null) {
                            //only server-side; no need to call anything, as items are synced in snapshots
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
        /** 是否拿取单位 */
        public String takeUnits = "false";
        /** x坐标 */
        public String x = "";
        /** y坐标 */
        public String y = "";
        
        /** 构造函数 */
        public FastPayTakeStatement() {}
        
        /** 从参数创建 */
        public FastPayTakeStatement(String[] params) {
            if (params.length >= 3) takeUnits = params[2];
            if (params.length >= 4) x = params[3];
            if (params.length >= 5) y = params[4];
            afterRead();
        }
        
        @Override
        public void build(Table table) {
            table.clearChildren();
            table.left();
            
            table.table(t -> {
                t.setColor(table.color);
                
                t.add(" takeUnits ").left().self(c -> tooltip(c, "fastunitcontrol.paytake.takeunits"));
                field(t, takeUnits, str -> takeUnits = str);
                
                t.add(" x ").left().self(c -> tooltip(c, "fastunitcontrol.paytake.x"));
                field(t, x, str -> x = str);
                
                t.add(" y ").left().self(c -> tooltip(c, "fastunitcontrol.paytake.y"));
                field(t, y, str -> y = str);
            }).left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public void write(StringBuilder builder) {
            builder.append("fastunitcontrol paytake ")
                   .append(takeUnits).append(" ")
                   .append(x).append(" ")
                   .append(y);
        }
        
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar takeUnitsVar = takeUnits != null && !takeUnits.isEmpty() ? builder.var(takeUnits) : null;
            LVar xVar = x != null && !x.isEmpty() ? builder.var(x) : null;
            LVar yVar = y != null && !y.isEmpty() ? builder.var(y) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    boolean takeUnits = takeUnitsVar != null ? takeUnitsVar.bool() : false;
                    float x = xVar != null ? xVar.numf() : unit.x;
                    float y = yVar != null ? yVar.numf() : unit.y;
                    
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
                            Building build = Vars.world.buildWorld(x, y);
                            
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
        /** x坐标 */
        public String x = "";
        /** y坐标 */
        public String y = "";
        
        /** 构造函数 */
        public FastPayDropStatement() {}
        
        /** 从参数创建 */
        public FastPayDropStatement(String[] params) {
            if (params.length >= 3) x = params[2];
            if (params.length >= 4) y = params[3];
            afterRead();
        }
        
        @Override
        public void build(Table table) {
            table.clearChildren();
            table.left();
            
            table.table(t -> {
                t.setColor(table.color);
                
                t.add(" x ").left().self(c -> tooltip(c, "fastunitcontrol.paydrop.x"));
                field(t, x, str -> x = str);
                
                t.add(" y ").left().self(c -> tooltip(c, "fastunitcontrol.paydrop.y"));
                field(t, y, str -> y = str);
            }).left();
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public void write(StringBuilder builder) {
            builder.append("fastunitcontrol paydrop ")
                   .append(x).append(" ")
                   .append(y);
        }
        
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            LVar xVar = x != null && !x.isEmpty() ? builder.var(x) : null;
            LVar yVar = y != null && !y.isEmpty() ? builder.var(y) : null;
            
            return new LExecutor.LInstruction() {
                @Override
                public void run(LExecutor exec) {
                    Unit unit = exec.unit.obj() instanceof Unit ? (Unit)exec.unit.obj() : null;
                    if(unit == null) return;
                    
                    float x = xVar != null ? xVar.numf() : unit.x;
                    float y = yVar != null ? yVar.numf() : unit.y;
                    
                    // 检查单位是否在指定坐标范围内
                    if(unit.within(x, y, FIXED_RADIUS) && unit instanceof Payloadc pay && pay.hasPayload()) {
                        Call.payloadDropped(unit, x, y);
                    }
                }
            };
        }
    }
    
    /** 创建可编辑的文本字段 */
    private static TextField field(Table table, String value, Cons<String> consumer) {
        return table.field(value, consumer).width(100f).get();
    }
}