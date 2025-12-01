package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.LogicIO;
import mindustry.logic.LAssembler;
import mindustry.logic.LCategory;
import mindustry.logic.LExecutor;
import mindustry.logic.LStatement;
import java.util.Base64;

import static mindustry.logic.LCanvas.tooltip;
import static arc.Core.*;

public class LNestedLogic {

    public static class LNestedLogicStatement extends LStatement {
        // 存储嵌套的逻辑代码
        public String nestedCode = "";
        // 文本输入框的默认值为语言包中的lnestedlogic.field键值
        public String defaultFieldText = arc.Core.bundle.get("lnestedlogic.field");

        @Override
        public void build(Table table) {
            table.setColor(table.color);
            // 使用field方法实现文本框
             field(table, defaultFieldText, str -> {
                        defaultFieldText = "\"" + str + "\"";
                    })
               .size(500f, 40f).pad(2f)
               .self(c -> tooltip(c, "lnestedlogic.field"));
            // 添加编辑按钮，使用指定的样式
            table.button(b -> {
                b.label(() -> "Edit");
                b.clicked(() -> {
                    // 保存当前的canvas实例
                    mindustry.logic.LCanvas oldCanvas = null;
                    try {
                        // 使用反射访问包级私有变量
                        java.lang.reflect.Field canvasField = mindustry.logic.LCanvas.class.getDeclaredField("canvas");
                        canvasField.setAccessible(true);
                        oldCanvas = (mindustry.logic.LCanvas) canvasField.get(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    // 为嵌套逻辑编辑创建一个新的LogicDialog实例
                    // 这样当关闭嵌套逻辑编辑页面时，只会关闭这个新的对话框，而不会影响主逻辑编辑器
                    mindustry.logic.LogicDialog nestedDialog = new mindustry.logic.LogicDialog();
                    
                    // 创建恢复canvas的lambda
                    Runnable restoreCanvas = () -> {
                        if (oldCanvas != null) {
                            try {
                                // 使用反射恢复canvas实例
                                java.lang.reflect.Field canvasField = mindustry.logic.LCanvas.class.getDeclaredField("canvas");
                                canvasField.setAccessible(true);
                                canvasField.set(null, oldCanvas);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    
                    // 显示编辑器，传入当前代码和回调函数
                    nestedDialog.show(nestedCode, null, false, modifiedCode -> {
                        // 保存修改后的代码
                        nestedCode = modifiedCode;
                        // 恢复原来的canvas实例
                        restoreCanvas.run();
                    });
                    
                    // 当对话框隐藏时恢复canvas实例（确保无论如何都会恢复）
                    nestedDialog.hidden(restoreCanvas);
                });
            }, mindustry.ui.Styles.logict, () -> {}).size(60, 40).color(table.color).left().padLeft(2)
              .self(c -> tooltip(c, "lnestedlogic.button"));
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            // 编译嵌套的逻辑指令
            // 1. 解码Base64嵌套代码
            // 2. 使用LAssembler.read()解析嵌套代码
            // 3. 使用主builder的变量表编译嵌套指令，确保变量被正确注册
            try {
                // 直接使用嵌套代码，不需要额外解码
                // 因为nestedCode已经是解码后的原始代码
                // 使用LAssembler.read()解析嵌套代码
                Seq<LStatement> nestedStatements = LAssembler.read(nestedCode, false);
                
                // 清除嵌套逻辑的LStatement对象的elem属性，避免影响主层级的checkHovered()方法
                nestedStatements.each(l -> l.elem = null);
                
                // 编译嵌套指令，使用主builder的变量表
                // 这确保嵌套逻辑中的变量被注册到主执行器的变量表中
                LExecutor.LInstruction[] nestedInstructions = nestedStatements.map(l -> {
                    // 编译指令
                    return l.build(builder);
                }).retainAll(l -> l != null).toArray(LExecutor.LInstruction.class);
                return new LNestedLogicInstruction(nestedInstructions);
            } catch (Exception e) {
                // 如果编译失败，返回空指令
                return new LNestedLogicInstruction(new LExecutor.LInstruction[0]);
            }
        }

        @Override
        public LCategory category() {
            return LCategoryExt.function;
        }

        /** Anuken, if you see this, you can replace it with your own @RegisterStatement, because this is my last resort... **/
        public static void create() {
            LAssembler.customParsers.put("nestedlogic", params -> {
                LNestedLogicStatement stmt = new LNestedLogicStatement();
                if (params.length >= 2) {
                    // 先读取defaultFieldText
                    stmt.defaultFieldText = params[1];
                }
                if (params.length >= 3) {
                    // 再读取嵌套代码
                    // 改进反序列化逻辑，使用Base64解码嵌套代码
                    String rawCode = params[2];
                    if (rawCode.startsWith("\"")) {
                        try {
                            // 移除外层引号
                            String encoded = rawCode.substring(1, rawCode.length() - 1);
                            // 使用Base64解码嵌套代码
                            byte[] decodedBytes = Base64.getDecoder().decode(encoded);
                            stmt.nestedCode = new String(decodedBytes);
                        } catch (IllegalArgumentException e) {
                            // 如果解码失败，返回空字符串
                            stmt.nestedCode = "";
                        }
                    } else {
                        stmt.nestedCode = rawCode;
                    }
                }
                stmt.afterRead();
                return stmt;
            });
            LogicIO.allStatements.add(LNestedLogicStatement::new);
        }

        @Override
        public void write(StringBuilder builder) {
            // 序列化嵌套逻辑指令
            builder.append("nestedlogic ");
            // 先序列化defaultFieldText
            builder.append(defaultFieldText);
            // 再序列化嵌套代码
            // 使用Base64编码嵌套代码，避免转义字符问题
            String encoded = Base64.getEncoder().encodeToString(nestedCode.getBytes());
            builder.append(" \"").append(encoded).append('"');
        }

        @Override
        public void afterRead() {
            // 不需要额外处理，直接使用nestedCode
        }
    }

    public static class LNestedLogicInstruction implements LExecutor.LInstruction {
        // 编译后的嵌套逻辑指令
        public LExecutor.LInstruction[] instructions;

        public LNestedLogicInstruction(LExecutor.LInstruction[] instructions) {
            this.instructions = instructions;
        }

        public LNestedLogicInstruction() {
            this.instructions = new LExecutor.LInstruction[0];
        }

        @Override
        public void run(LExecutor exec) {
            // 执行嵌套逻辑指令
            // 使用主执行器的变量作用域
            // 执行嵌套指令
            for (LExecutor.LInstruction instruction : instructions) {
                // 检查指令计数限制
                if (exec.counter.numval >= LExecutor.maxInstructions) {
                    break;
                }
                instruction.run(exec);
            }
        }
    }
}
