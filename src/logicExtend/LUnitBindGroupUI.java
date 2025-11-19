package logicExtend;

import arc.struct.Seq;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.TextureRegionDrawable;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class LUnitBindGroupUI {
    // 注册方法
    public static void register() {
        // 使用LAssembler.customParsers注册自定义指令
        LAssembler.customParsers.put("unitBindGroup", args -> {
            UnitBindGroupStatement stmt = new UnitBindGroupStatement();
            
            // 解析参数 - 保持和游戏源代码一致，只解析一个type参数
            if(args.length > 0) stmt.type = args[0];
            
            return stmt;
        });
        // 添加到LogicIO.allStatements中
        LogicIO.allStatements.add(UnitBindGroupStatement::new);
    }
    
    // 单位绑定组指令类 - 直接复制游戏源代码中的UnitBindStatement实现
    public static class UnitBindGroupStatement extends LStatement {
        public String type = "@poly";

        @Override
        public void build(Table table) {
            table.add(" type ");

            TextField field = field(type, str -> type = str).get();

            table.button(b -> {
                b.image(Icon.pencilSmall);
                b.clicked(() -> showSelectTable(b, (t, hide) -> {
                    t.row();
                    t.table(i -> {
                        i.left();
                        int c = 0;
                        for(UnitType item : content.units()){
                            if(!item.unlockedNow() || item.isHidden() || !item.logicControllable) continue;
                            i.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, Styles.iconSmall, () -> {
                                type = "@" + item.name;
                                field.setText(type);
                                hide.run();
                            }).size(40f);

                            if(++c % 6 == 0) i.row();
                        }
                    }).colspan(3).width(240f).left();
                }));
            }, Styles.logict, () -> {}).size(40f).padLeft(-2).color(table.color);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new UnitBindGroupI(builder.var(type));
        }

        @Override
        public LExecutor.LCategory category() {
            return LExecutor.LCategory.unit;
        }
        
        // 静态create方法
        public static UnitBindGroupStatement create() {
            return new UnitBindGroupStatement();
        }
    }
    
    // 单位绑定组指令实现类 - 直接复制游戏源代码中的UnitBindI实现
    public static class UnitBindGroupI implements LExecutor.LInstruction {
        public LExecutor.LVar type;

        public UnitBindGroupI(LExecutor.LVar type) {
            this.type = type;
        }

        public UnitBindGroupI() {
        }

        @Override
        public void run(LExecutor exec) {
            // 指令执行逻辑
            if(exec.binds == null || exec.binds.length != content.units().size) {
                exec.binds = new int[content.units().size];
            }

            //binding to `null` was previously possible, but was too powerful and exploitable
            if(this.type.obj() instanceof UnitType type && type.logicControllable) {
                Seq<Unit> seq = exec.team.data().unitCache(type);

                if(seq != null && seq.any()) {
                    exec.binds[type.id] %= seq.size;
                    if(exec.binds[type.id] < seq.size) {
                        //bind to the next unit
                        exec.unit.setconst(seq.get(exec.binds[type.id]));
                    }
                    exec.binds[type.id]++;
                } else {
                    //no units of this type found
                    exec.unit.setconst(null);
                }
            } else if(this.type.obj() instanceof Unit u && (u.team == exec.team || exec.privileged) && u.type.logicControllable) {
                //bind to specific unit object
                exec.unit.setconst(u);
            } else {
                exec.unit.setconst(null);
            }
        }
    }
}