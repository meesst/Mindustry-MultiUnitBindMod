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
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.TextField;
import arc.scene.style.Drawable;
import mindustry.gen.Icon;
import arc.Core;

import static mindustry.Vars.*;
import static mindustry.logic.LCanvas.tooltip;
import static arc.Core.*;

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
            rebuild(table);
        }
        
        private void rebuild(Table table) {
            table.clearChildren();
            table.left();
            
            // 第一排：type和count参数（使用嵌套Table）
            table.table(t -> {
                t.setColor(table.color);
                
                // 显示type参数
                t.add(" type ").left().self(c -> tooltip(c, "unitbindgroup.type"));  // 显示标签，添加空格并添加左对齐和参数样式及悬浮提示

                // 创建可编辑的文本字段，用于输入或显示单位类型标识
                TextField typeField = field(t, type, str -> type = str).get();

                // 添加选择按钮，点击后显示单位类型选择界面
                t.button(b -> {
                    b.image(Icon.pencilSmall); // 按钮图标
                    // 点击事件处理：显示单位类型选择对话框
                    b.clicked(() -> showSelectTable(b, (tableSelect, hide) -> {
                        tableSelect.row(); // 换行
                        // 创建表格来显示所有可选的单位类型
                        tableSelect.table(i -> {
                            i.left(); // 左对齐
                            int c = 0; // 计数器，用于控制每行显示的单位数量
                            // 遍历所有可用的单位类型
                            for(UnitType item : content.units()){
                                // 过滤条件：必须已解锁、未隐藏且支持逻辑控制
                                if(!item.unlockedNow() || item.isHidden() || !item.logicControllable) continue;
                                // 为每个符合条件的单位类型创建一个选择按钮
                                i.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconSmall, () -> {
                                    type = "@" + item.name; // 设置选中的单位类型标识
                                    typeField.setText(type);    // 更新UI
                                    hide.run();            // 关闭选择对话框
                                }).size(40f); // 按钮大小

                                // 每6个单位类型换行
                                if(++c % 6 == 0) i.row();
                            }
                        }).colspan(3).width(240f).left(); // 表格宽度和对齐方式
                    })); // 结束showSelectTable调用
                }, Styles.logict, () -> {}).size(40f).padLeft(-2).color(t.color); // 按钮样式和尺寸，调整间距为2
                
                // 添加count标签和文本输入框
                t.add(" count ").left().self(c -> { this.param((Cell<Label>)c); tooltip(c, "unitbindgroup.count"); }); // 显示count标签，添加空格并添加左对齐和参数样式及悬浮提示
                // 创建可编辑的文本字段，用于输入或显示绑定的单位数量，确保count值不小于1
                field(t, count, str -> {
                    try {
                        int value = Integer.parseInt(str);
                        count = value < 1 ? "1" : str;
                    } catch (NumberFormatException e) {
                        // 如果输入不是数字，设置为默认值1
                        count = "1";
                    }
                });
            }).left();
            
            // 换行到第二排
            table.row();
            
            // 第二排：unitVar和indexVar参数（使用嵌套Table）
            table.table(t -> {
                t.setColor(table.color);
                
                t.add(" unitVar ").left().self(c -> tooltip(c, "unitbindgroup.unitvar"));  // 显示unitVar标签，添加空格并添加左对齐和参数样式及悬浮提示
                // 创建可编辑的文本字段，用于输入或显示单位变量名
                field(t, unitVar, str -> unitVar = str);
                
                t.add(" indexVar ").left().self(c -> tooltip(c, "unitbindgroup.indexvar"));  // 显示indexVar标签，添加空格并添加左对齐和参数样式及悬浮提示
                // 创建可编辑的文本字段，用于输入或显示索引变量名
                field(t, indexVar, str -> indexVar = str);
            }).left();
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
            // 调用外部类LUnitBindGroupRUN中的run方法执行实际逻辑
            LUnitBindGroupRUN.run(exec, type, count, unitVar, indexVar);
        }
    }
}