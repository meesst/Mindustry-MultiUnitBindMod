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
import static mindustry.logic.LCanvas.*;

/** 单位绑定组逻辑指令UI类 */
public class LUnitBindGroupUI {
    
    /** 单位绑定组指令类 */
    public static class UnitBindGroupStatement extends LStatement {
        /** 目标单位类型标识，默认绑定到多足单位类型 */
        public String type = "@poly";
        /** 绑定的单位数量，默认值为1 */
        public String count = "1";
        /** 存储当前单位的变量名，默认值为currentUnit */
        public String unitVar = "currentUnit";
        /** 存储单位索引的变量名，默认值为unitIndex */
        public String indexVar = "unitIndex";

        /** 构建指令的UI界面 */
        @Override
        public void build(Table table) {
            table.add(" type ").left().self(this::param); // 显示标签，添加空格并添加左对齐和参数样式

            // 创建可编辑的文本字段，用于输入或显示单位类型标识
            fields(table, type, str -> type = str);

            // 添加选择按钮，点击后显示单位类型选择界面
            table.button(b -> {
                b.image(Icon.pencilSmall); // 按钮图标
                // 点击事件处理：显示单位类型选择对话框
                b.clicked(() -> showSelectTable(b, (t, hide) -> {
                    t.row(); // 换行
                    // 创建表格来显示所有可选的单位类型
                    t.table(i -> {
                        i.left(); // 左对齐
                        int c = 0; // 计数器，用于控制每行显示的单位数量
                        // 遍历所有可用的单位类型
                        for(UnitType item : content.units()){
                            // 过滤条件：必须已解锁、未隐藏且支持逻辑控制
                            if(!item.unlockedNow() || item.isHidden() || !item.logicControllable) continue;
                            // 为每个符合条件的单位类型创建一个选择按钮
                            i.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconSmall, () -> {
                                type = "@" + item.name; // 设置选中的单位类型标识
                                rebuild(table);    // 更新UI
                                hide.run();            // 关闭选择对话框
                            }).size(40f); // 按钮大小

                            // 每6个单位类型换行
                            if(++c % 6 == 0) i.row();
                        }
                    }).colspan(3).width(240f).left(); // 表格宽度和对齐方式
                })); // 结束showSelectTable调用
            }, Styles.logict, () -> {}).size(40f).padLeft(2).color(table.color); // 按钮样式和尺寸，调整间距为2
            
            // 添加count标签和文本输入框
            table.add(" count ").left().self(this::param); // 显示count标签，添加空格并添加左对齐和参数样式
            // 创建可编辑的文本字段，用于输入或显示绑定的单位数量
            fields(table, count, str -> count = str);
            
            // 添加第二排参数
            table.row();
            table.add(" unitVar ").left().self(this::param); // 显示unitVar标签，添加空格并添加左对齐和参数样式
            // 创建可编辑的文本字段，用于输入或显示单位变量名
            fields(table, unitVar, str -> unitVar = str);
            
            table.add(" indexVar ").left().self(this::param); // 显示indexVar标签，添加空格并添加左对齐和参数样式
            // 创建可编辑的文本字段，用于输入或显示索引变量名
            fields(table, indexVar, str -> indexVar = str);
        }
        
        // 添加rebuild方法以支持UI更新
        void rebuild(Table table) {
            table.clearChildren();
            build(table);
        }

        /** 构建指令的执行实例 */
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            // 将所有参数转换为LVar对象，并创建执行器实例
            return new UnitBindGroupI(builder.var(type), builder.var(count), builder.var(unitVar), builder.var(indexVar));
        }

        /** 指定指令在逻辑编辑器中的分类 */
        @Override
        public LCategory category() {
            return LCategory.unit; // 指令归类为单位操作类别
        }
               
        /** 注册自定义逻辑指令 */
        public static void create() {
            // 注册指令解析器
            LAssembler.customParsers.put("unitBindGroup", params -> {
                // 创建新的指令实例
                UnitBindGroupStatement stmt = new UnitBindGroupStatement();
                // 如果有参数，则设置单位类型
                if (params.length >= 2) stmt.type = params[1];
                // 如果有第二个参数，则设置count值
                if (params.length >= 3) stmt.count = params[2];
                // 如果有第三个参数，则设置unitVar值
                if (params.length >= 4) stmt.unitVar = params[3];
                // 如果有第四个参数，则设置indexVar值
                if (params.length >= 5) stmt.indexVar = params[4];
                // 读取后处理，确保指令状态正确
                stmt.afterRead();
                return stmt;
            });
            // 将指令添加到逻辑IO的所有语句列表中，使其在逻辑编辑器中可用
            LogicIO.allStatements.add(UnitBindGroupStatement::new);
        }
        
        /** 序列化指令到字符串 */
        @Override
        public void write(StringBuilder builder){
            // 格式：指令名称 + 空格 + 单位类型标识 + 空格 + count值 + 空格 + unitVar + 空格 + indexVar
            builder.append("unitBindGroup ").append(type).append(" ").append(count).append(" ").append(unitVar).append(" ").append(indexVar);
        }
    }
    
    /** 单位绑定组指令执行器类 */
    public static class UnitBindGroupI implements LExecutor.LInstruction {
        /** 单位类型变量 */
        public LVar type;
        /** 绑定的单位数量变量 */
        public LVar count;
        /** 当前单位变量的变量引用 */
        public LVar unitVar;
        /** 单位索引变量的变量引用 */
        public LVar indexVar;

        /** 构造函数，指定目标单位类型、数量、单位变量和索引变量 */
        public UnitBindGroupI(LVar type, LVar count, LVar unitVar, LVar indexVar) {
            this.type = type;
            this.count = count;
            this.unitVar = unitVar;
            this.indexVar = indexVar;
        }

        /** 空构造函数 */
        public UnitBindGroupI() {
        }

        /** 执行指令的核心逻辑 */
        @Override
        public void run(LExecutor exec) {
            // 初始化或更新绑定计数器数组
            // binds数组用于记录每种单位类型的当前绑定索引
            if(exec.binds == null || exec.binds.length != content.units().size) {
                exec.binds = new int[content.units().size]; // 每种单位类型对应一个计数器
            }

            //binding to `null` was previously possible, but was too powerful and exploitable
            // 处理单位类型绑定（最常见的情况）
            if(type.obj() instanceof UnitType type && type.logicControllable) {
                // 获取同类型的所有单位列表
                Seq<Unit> seq = exec.team.data().unitCache(type);

                // 如果存在该类型的单位且seq不为null
                if(seq != null && seq.size > 0) {
                    // 确保计数器在有效范围内循环（防止索引越界）
                    exec.binds[type.id] %= seq.size;
                    // 绑定到当前索引对应的单位
                    exec.unit.setconst(seq.get(exec.binds[type.id]));
                    // 索引递增，下次执行时将绑定到下一个单位
                    exec.binds[type.id]++;
                } else {
                    // 没有找到该类型的单位，清空当前绑定
                    exec.unit.setconst(null);
                }
            } else if(type.obj() instanceof Unit u && (u.team == exec.team || exec.privileged) && u.type.logicControllable) {
                // 处理直接绑定到特定单位对象的情况
                // 条件：必须是同一队伍或拥有特权，且单位支持逻辑控制
                exec.unit.setconst(u); // 绑定到指定的单位对象
            } else {
                // 其他情况：类型无效或不满足条件，清空当前绑定
                exec.unit.setconst(null);
            }
        }
    }
}