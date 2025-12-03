package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.LogicIO;
import mindustry.logic.LAssembler;
import mindustry.logic.LCategory;
import mindustry.logic.LExecutor;
import mindustry.logic.LStatement;
import mindustry.logic.LVar;
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
                    final mindustry.logic.LCanvas oldCanvas;
                    mindustry.logic.LCanvas tempCanvas = null;
                    try {
                        // 使用反射访问包级私有变量
                        java.lang.reflect.Field canvasField = mindustry.logic.LCanvas.class.getDeclaredField("canvas");
                        canvasField.setAccessible(true);
                        tempCanvas = (mindustry.logic.LCanvas) canvasField.get(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    oldCanvas = tempCanvas;
                    
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
            // 3. 使用主builder的变量表，确保变量共享
            // 4. 使用独立的builder编译嵌套指令，避免jump指令地址冲突
            try {
                // 直接使用嵌套代码，不需要额外解码
                // 因为nestedCode已经是解码后的原始代码
                // 使用LAssembler.read()解析嵌套代码
                Seq<LStatement> nestedStatements = LAssembler.read(nestedCode, false);
                
                // 清除嵌套逻辑的LStatement对象的elem属性，避免影响主层级的checkHovered()方法
                nestedStatements.each(l -> l.elem = null);
                
                // 创建新的LAssembler实例，但共享主builder的变量表
                LAssembler nestedBuilder = new LAssembler() {
                    @Override
                    public LVar var(String symbol) {
                        // 优先从主builder获取变量，确保变量共享
                        LVar mainVar = builder.var(symbol);
                        // 如果主builder中存在该变量，直接返回
                        if (builder.vars.containsKey(mainVar.name)) {
                            return mainVar;
                        }
                        // 否则在嵌套builder中创建
                        return super.var(symbol);
                    }
                    
                    @Override
                    public LVar putVar(String name) {
                        // 优先从主builder获取变量
                        if (builder.vars.containsKey(name)) {
                            return builder.vars.get(name);
                        }
                        // 否则在嵌套builder中创建，并同步到主builder
                        LVar var = super.putVar(name);
                        builder.vars.put(name, var);
                        return var;
                    }
                    
                    @Override
                    public LVar putConst(String name, Object value) {
                        // 优先从主builder获取常量
                        if (builder.vars.containsKey(name)) {
                            return builder.vars.get(name);
                        }
                        // 否则在嵌套builder中创建，并同步到主builder
                        LVar var = super.putConst(name, value);
                        builder.vars.put(name, var);
                        return var;
                    }
                };
                
                // 复制主builder的privileged状态（使用反射访问私有字段）
                try {
                    java.lang.reflect.Field privilegedField = LAssembler.class.getDeclaredField("privileged");
                    privilegedField.setAccessible(true);
                    boolean isPrivileged = privilegedField.getBoolean(builder);
                    privilegedField.setBoolean(nestedBuilder, isPrivileged);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // 编译嵌套指令，使用共享变量表的nestedBuilder
                LExecutor.LInstruction[] nestedInstructions = nestedStatements.map(l -> {
                    return l.build(nestedBuilder);
                }).retainAll(l -> l != null).toArray(LExecutor.LInstruction.class);
                
                // 获取当前builder的指令数量，作为嵌套指令的起始位置
                int startIndex = builder.instructions != null ? builder.instructions.length : 0;
                
                return new LNestedLogicInstruction(nestedInstructions, startIndex);
            } catch (Exception e) {
                // 如果编译失败，返回空指令
                return new LNestedLogicInstruction(new LExecutor.LInstruction[0], 0);
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
        // 嵌套指令的起始位置
        public int startIndex;

        public LNestedLogicInstruction(LExecutor.LInstruction[] instructions, int startIndex) {
            this.instructions = instructions;
            this.startIndex = startIndex;
        }

        public LNestedLogicInstruction() {
            this.instructions = new LExecutor.LInstruction[0];
            this.startIndex = 0;
        }

        @Override
        public void run(LExecutor exec) {
            // 执行嵌套逻辑指令
            // 保存当前的counter状态
            double originalCounter = exec.counter.numval;
            
            // 执行嵌套指令
            for (int i = 0; i < instructions.length; i++) {
                // 检查指令计数限制
                if (exec.counter.numval >= LExecutor.maxInstructions) {
                    break;
                }
                
                // 保存当前循环索引对应的预期计数器值
                double expectedCounter = originalCounter + i + 1;
                
                // 执行当前指令
                instructions[i].run(exec);
                
                // 检查counter是否被修改（如jump指令）
                double currentCounter = exec.counter.numval;
                
                // 如果counter被修改，并且不是正常的顺序执行
                if (Math.abs(currentCounter - expectedCounter) > 0.0001) {
                    // 计算相对于嵌套指令块的偏移量
                    double relativeJump = currentCounter - originalCounter;
                    
                    // 检查jump目标是否在嵌套指令范围内
                    if (relativeJump >= 0 && relativeJump < instructions.length) {
                        // 跳转到嵌套指令内的指定位置
                        i = (int)relativeJump - 1; // -1 because loop will increment i
                        // 更新计数器为相对于原始计数器的正确位置
                        exec.counter.numval = originalCounter + i + 1;
                    } else {
                        // 跳转到了嵌套指令范围外，恢复原来的counter值并退出循环
                        exec.counter.numval = originalCounter + instructions.length;
                        break;
                    }
                }
            }
            
            // 确保计数器值至少为原始值加上嵌套指令长度
            if (exec.counter.numval < originalCounter + instructions.length) {
                exec.counter.numval = originalCounter + instructions.length;
            }
        }
    }
}
