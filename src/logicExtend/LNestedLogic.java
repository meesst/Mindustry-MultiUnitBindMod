package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.struct.ObjectIntMap;
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
    
    // 反射辅助方法：访问LAssembler.privileged私有字段
    private static boolean getPrivileged(LAssembler builder) {
        try {
            java.lang.reflect.Field privilegedField = LAssembler.class.getDeclaredField("privileged");
            privilegedField.setAccessible(true);
            return privilegedField.getBoolean(builder);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private static void setPrivileged(LAssembler builder, boolean value) {
        try {
            java.lang.reflect.Field privilegedField = LAssembler.class.getDeclaredField("privileged");
            privilegedField.setAccessible(true);
            privilegedField.setBoolean(builder, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 反射辅助方法：访问LExecutor.nameMap受保护字段
    private static arc.struct.ObjectIntMap<String> getNameMap(LExecutor exec) {
        try {
            java.lang.reflect.Field nameMapField = LExecutor.class.getDeclaredField("nameMap");
            nameMapField.setAccessible(true);
            return (arc.struct.ObjectIntMap<String>) nameMapField.get(exec);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static void setNameMap(LExecutor exec, arc.struct.ObjectIntMap<String> nameMap) {
        try {
            java.lang.reflect.Field nameMapField = LExecutor.class.getDeclaredField("nameMap");
            nameMapField.setAccessible(true);
            nameMapField.set(exec, nameMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
                    // 为嵌套逻辑编辑创建一个新的LogicDialog实例
                    mindustry.logic.LogicDialog nestedDialog = new mindustry.logic.LogicDialog();
                    
                    // 显示编辑器，传入当前代码和回调函数
                    nestedDialog.show(nestedCode, null, false, modifiedCode -> {
                        // 保存修改后的代码
                        nestedCode = modifiedCode;
                    });
                });
            }, mindustry.ui.Styles.logict, () -> {}).size(60, 40).color(table.color).left().padLeft(2)
              .self(c -> tooltip(c, "lnestedlogic.button"));
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            // 编译嵌套的逻辑指令
            // 1. 解析嵌套代码为语句序列
            // 2. 创建新的LAssembler编译嵌套指令
            // 3. 复制主builder的变量表到嵌套builder
            // 4. 编译嵌套语句为指令
            // 5. 将嵌套builder中新增的变量复制回主builder
            try {
                // 使用LAssembler.read()解析嵌套代码
                Seq<LStatement> nestedStatements = LAssembler.read(nestedCode, false);
                
                // 清除嵌套逻辑的LStatement对象的elem属性，避免影响主层级的checkHovered()方法
                nestedStatements.each(l -> l.elem = null);
                
                // 创建新的LAssembler实例，用于编译嵌套指令
                LAssembler nestedBuilder = new LAssembler();
                
                // 设置privileged状态（使用反射）
                boolean isPrivileged = getPrivileged(builder);
                setPrivileged(nestedBuilder, isPrivileged);
                
                // 复制主builder的变量表到嵌套builder
                // 这样嵌套逻辑可以访问主逻辑中已有的变量
                for(var entry : builder.vars.entries()) {
                    LVar var = entry.value;
                    if(var.constant) {
                        // 如果是常量，复制常量值
                        nestedBuilder.putConst(var.name, var.isobj ? var.objval : var.numval);
                    } else {
                        // 如果是变量，复制变量引用
                        LVar newVar = nestedBuilder.putVar(var.name);
                        newVar.isobj = var.isobj;
                        newVar.objval = var.objval;
                        newVar.numval = var.numval;
                        newVar.constant = var.constant;
                    }
                }
                
                // 编译嵌套语句为指令
                LExecutor.LInstruction[] nestedInstructions = nestedStatements.map(l -> {
                    // 编译指令
                    return l.build(nestedBuilder);
                }).retainAll(l -> l != null).toArray(LExecutor.LInstruction.class);
                
                // 将嵌套builder中新增的变量复制回主builder
                // 这样主逻辑可以访问嵌套逻辑中创建的变量
                for(var entry : nestedBuilder.vars.entries()) {
                    String name = entry.key;
                    LVar nestedVar = entry.value;
                    
                    // 如果主builder中没有这个变量，添加它
                    if(!builder.vars.containsKey(name)) {
                        LVar mainVar = builder.putVar(name);
                        mainVar.isobj = nestedVar.isobj;
                        mainVar.objval = nestedVar.objval;
                        mainVar.numval = nestedVar.numval;
                        mainVar.constant = nestedVar.constant;
                    }
                }
                
                // 返回嵌套逻辑指令，不包含startIndex
                return new LNestedLogicInstruction(nestedInstructions);
            } catch (Exception e) {
                // 如果编译失败，返回空指令
                e.printStackTrace();
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
        // 执行状态标记
        public boolean executed = false;
        // 嵌套LExecutor实例
        public LExecutor nestedExecutor;
        // 主逻辑返回地址
        public int returnAddress;

        public LNestedLogicInstruction(LExecutor.LInstruction[] instructions) {
            this.instructions = instructions;
        }

        public LNestedLogicInstruction() {
            this.instructions = new LExecutor.LInstruction[0];
        }

        @Override
        public void run(LExecutor exec) {
            // 执行嵌套逻辑指令
            // 不再使用独立的LExecutor，直接在主LExecutor上执行
            // 但需要确保jump指令只在嵌套范围内生效
            
            // 1. 保存原始指令序列
            LExecutor.LInstruction[] originalInstructions = exec.instructions;
            
            // 2. 保存原始counter值
            double originalCounter = exec.counter.numval;
            
            try {
                // 3. 临时替换为嵌套指令序列
                exec.instructions = instructions;
                
                // 4. 重置counter到嵌套逻辑的开始
                exec.counter.numval = 0;
                
                // 5. 执行嵌套逻辑，直到执行完毕或遇到限制
                while ((int) exec.counter.numval < instructions.length && exec.counter.numval < LExecutor.maxInstructions) {
                    // 保存当前counter值，用于检查jump指令
                    double currentCounter = exec.counter.numval;
                    
                    // 执行一条指令
                    exec.runOnce();
                    
                    // 检查是否是jump指令
                    if ((int) exec.counter.numval != currentCounter + 1) {
                        // 是jump指令，检查目标是否在嵌套范围内
                        if ((int) exec.counter.numval < 0 || (int) exec.counter.numval >= instructions.length) {
                            // 跳转到了嵌套范围外，终止执行
                            break;
                        }
                    }
                }
            } finally {
                // 6. 恢复原始指令序列
                exec.instructions = originalInstructions;
                
                // 7. 恢复原始counter值
                exec.counter.numval = originalCounter;
            }
        }
    }
}
