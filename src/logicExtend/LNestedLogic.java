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
import java.nio.charset.StandardCharsets;

import static mindustry.logic.LCanvas.tooltip;
import static arc.Core.*;

public class LNestedLogic {
    
    /** nestedlogic指令的分支枚举 */
    public enum NestedLogicType {
        push("variable"),
        call("logicName");
        
        public final String[] params;
        
        NestedLogicType(String... params) {
            this.params = params;
        }
    }
    
    /** 调用栈元素 */
    public static class CallStackElement {
        public String varName;
        public Object varValue;
    }
    
    /** 调用上下文 */
    public static class CallContext {
        public Seq<CallStackElement> callStack = new Seq<>();
        public double callCounter;
        public mindustry.logic.LogicDialog dialog;
    }
    
    /** 全局调用上下文栈 */
    public static final Seq<CallContext> callContextStack = new Seq<>();
    
    /** 获取当前调用上下文 */
    public static CallContext getCurrentContext() {
        return callContextStack.isEmpty() ? null : callContextStack.get(callContextStack.size - 1);
    }
    
    public static class LNestedLogicStatement extends LStatement {
        // 指令类型
        public NestedLogicType type = NestedLogicType.push;
        // 第一个参数
        public String p1 = "";
        // 第二个参数
        public String p2 = "";
        // 存储嵌套的逻辑代码
        public String nestedCode = "";

        @Override
        public void build(Table table) {
            table.setColor(table.color);
            table.left();
            
            // 分支选择按钮
            table.button(b -> {
                b.label(() -> type.name());
                b.clicked(() -> {
                    // 直接使用父类的showSelect方法
                    super.showSelect(b, NestedLogicType.values(), type, t -> {
                        type = t;
                        // 保存UI状态
                        saveUI();
                    }, 2, cell -> cell.size(120, 50));
                });
            }, mindustry.ui.Styles.logict, () -> {}).size(120, 40).color(table.color).left().padLeft(2);
            
            // 根据当前选项动态创建UI元素
            if (type == NestedLogicType.push) {
                // push分支：创建变量输入框
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(150f, 40f).pad(2f);
            } else if (type == NestedLogicType.call) {
                // call分支：创建变量输入框 + 编辑页面按钮
                fields(table, "Logic Name", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(300f, 40f).pad(2f);
                
                table.button(b -> {
                    b.label(() -> "Edit Logic");
                    b.clicked(() -> {
                        // 打开嵌套逻辑编辑器
                        mindustry.logic.LogicDialog nestedDialog = new mindustry.logic.LogicDialog();
                        nestedDialog.show(nestedCode, null, false, modifiedCode -> {
                            // 保存修改后的代码
                            nestedCode = modifiedCode;
                            saveUI();
                        });
                    });
                }, mindustry.ui.Styles.logict, () -> {}).size(120f, 40f).color(table.color).pad(2f);
            }
        }
        
        @Override
        public void saveUI() {
            super.saveUI();
            // 确保UI状态被正确保存
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            switch (type) {
                case push:
                    // push指令：将变量压入当前上下文的调用栈
                    return (LExecutor exec) -> {
                        // 获取或创建当前调用上下文
                        CallContext context = getCurrentContext();
                        if (context == null) {
                            context = new CallContext();
                            callContextStack.add(context);
                        }
                        
                        // 获取要压入的变量
                        LVar var = exec.optionalVar(p1);
                        if (var != null) {
                            CallStackElement elem = new CallStackElement();
                            elem.varName = p1;
                            elem.varValue = var.isobj ? var.objval : var.numval;
                            context.callStack.add(elem);
                        }
                    };
                    
                case call:
                    try {
                        // 解析嵌套代码
                        // 使用false作为privileged参数，因为我们无法访问builder的私有字段
                        Seq<LStatement> nestedStatements = LAssembler.read(nestedCode, false);
                        
                        // 清除嵌套逻辑的LStatement对象的elem属性，避免影响主层级的checkHovered()方法
                        nestedStatements.each(l -> l.elem = null);
                        
                        // 创建新的LAssembler实例，不共享主builder的变量表
                        LAssembler nestedBuilder = new LAssembler();
                        // 不再直接访问privileged字段
                        
                        // 复制共享全局变量（除了@counter）
                        for (arc.struct.OrderedMap.Entry<String, LVar> entry : builder.vars) {
                            String key = entry.key;
                            LVar var = entry.value;
                            if (key.startsWith("@") && !key.equals("@counter")) {
                                LVar nestedVar = nestedBuilder.putVar(key);
                                nestedVar.set(var);
                                nestedVar.constant = var.constant;
                            }
                        }
                        
                        // 编译嵌套指令
                        LExecutor.LInstruction[] nestedInstructions = nestedStatements.map(l -> {
                            return l.build(nestedBuilder);
                        }).retainAll(l -> l != null).toArray(LExecutor.LInstruction.class);
                        
                        return (LExecutor exec) -> {
                            // 进入新的调用上下文
                        CallContext context = new CallContext();
                        context.callCounter = exec.counter.numval;
                        // 复制前一个上下文的调用栈（如果存在）
                        if (!callContextStack.isEmpty()) {
                            CallContext prevContext = callContextStack.get(callContextStack.size - 1);
                            context.callStack = prevContext.callStack.copy();
                        }
                        callContextStack.add(context);
                            
                            try {
                                // 获取当前上下文的调用栈
                                CallContext currentContext = getCurrentContext();
                                
                                // 创建嵌套执行器
                                LExecutor nestedExec = new LExecutor();
                                nestedExec.build = exec.build;
                                nestedExec.team = exec.team;
                                nestedExec.privileged = exec.privileged;
                                nestedExec.links = exec.links;
                                nestedExec.linkIds = exec.linkIds;
                                
                                // 获取共享全局变量并复制到嵌套执行器
                                for (LVar var : exec.vars) {
                                    if (var.name.startsWith("@") && !var.name.equals("@counter")) {
                                        LVar nestedVar = nestedExec.optionalVar(var.name);
                                        if (nestedVar != null) {
                                            nestedVar.set(var);
                                        }
                                    }
                                }
                                
                                // 复制调用栈中的变量到嵌套执行器
                                for (CallStackElement elem : currentContext.callStack) {
                                    // 首先尝试获取变量，如果不存在则创建
                                    LVar nestedVar = nestedExec.optionalVar(elem.varName);
                                    if (nestedVar == null) {
                                        // 变量不存在，创建一个新的LVar
                                        LVar newVar = new LVar(elem.varName);
                                        // 设置变量值
                                        if (elem.varValue instanceof Double) {
                                            newVar.isobj = false;
                                            newVar.numval = (Double) elem.varValue;
                                        } else {
                                            newVar.isobj = true;
                                            newVar.objval = elem.varValue;
                                        }
                                        // 将新变量添加到嵌套执行器的变量列表中
                                        // 首先创建一个新的数组，长度加1
                                        LVar[] newVars = new LVar[nestedExec.vars.length + 1];
                                        // 复制旧数组的内容到新数组
                                        System.arraycopy(nestedExec.vars, 0, newVars, 0, nestedExec.vars.length);
                                        // 添加新变量到数组末尾
                                        newVars[nestedExec.vars.length] = newVar;
                                        // 更新嵌套执行器的变量列表
                                        nestedExec.vars = newVars;
                                        // 重置nameMap，以便下次optionalVar能正确找到新变量
                                        nestedExec.nameMap = null;
                                        // 使用新创建的变量
                                        nestedVar = newVar;
                                    } else {
                                        // 变量存在，直接设置值
                                        if (elem.varValue instanceof Double) {
                                            nestedVar.isobj = false;
                                            nestedVar.numval = (Double) elem.varValue;
                                        } else {
                                            nestedVar.isobj = true;
                                            nestedVar.objval = elem.varValue;
                                        }
                                    }
                                }
                                
                                // 执行嵌套指令
                                for (LExecutor.LInstruction inst : nestedInstructions) {
                                    if (exec.counter.numval >= LExecutor.maxInstructions) {
                                        break;
                                    }
                                    inst.run(nestedExec);
                                    // 累加指令计数
                                    exec.counter.numval++;
                                }
                                
                                // 将嵌套执行器中修改后的变量复制回主执行器
                                for (CallStackElement elem : currentContext.callStack) {
                                    LVar mainVar = exec.optionalVar(elem.varName);
                                    LVar nestedVar = nestedExec.optionalVar(elem.varName);
                                    if (mainVar != null && nestedVar != null) {
                                        // 将嵌套执行器中修改后的变量值复制回主执行器
                                        mainVar.set(nestedVar);
                                    }
                                }
                                
                            } finally {
                                // 退出调用上下文
                                callContextStack.pop();
                            }
                        };
                    } catch (Exception e) {
                        // 如果编译失败，返回空指令
                        return (LExecutor exec) -> {};
                    }
                    
                default:
                    return (LExecutor exec) -> {};
            }
        }

        @Override
        public LCategory category() {
            return LCategoryExt.function;
        }

        @Override
        public void write(StringBuilder builder) {
            // 序列化嵌套逻辑指令
            builder.append("nestedlogic ");
            // 先序列化指令类型
            builder.append(type.name());
            builder.append(" ");
            // 序列化第一个参数
            builder.append(p1);
            builder.append(" ");
            // 序列化第二个参数
            builder.append(p2);
            
            // 如果是call指令，序列化嵌套代码
                if (type == NestedLogicType.call) {
                    // 使用Base64编码嵌套代码，避免转义字符问题
                    String encoded = Base64.getEncoder().encodeToString(nestedCode.getBytes(StandardCharsets.UTF_8));
                    builder.append(" \"").append(encoded).append('"');
                }
        }

        @Override
        public void afterRead() {
            // 不需要额外处理，直接使用nestedCode
        }
        
        /** Anuken, if you see this, you can replace it with your own @RegisterStatement, because this is my last resort... **/
        public static void create() {
            LAssembler.customParsers.put("nestedlogic", params -> {
                LNestedLogicStatement stmt = new LNestedLogicStatement();
                
                // 处理旧格式的兼容性
                if (params.length >= 2) {
                    try {
                        // 尝试解析为新格式的指令类型
                        stmt.type = NestedLogicType.valueOf(params[1]);
                    } catch (IllegalArgumentException e) {
                        // 旧格式：第一个参数是defaultFieldText，第二个是嵌套代码
                        // 新格式：第一个参数是指令类型，第二个是p1，第三个是p2，第四个是嵌套代码
                        stmt.type = NestedLogicType.call;
                        if (params.length >= 3) {
                            stmt.nestedCode = params[2];
                        }
                        return stmt;
                    }
                }
                
                if (params.length >= 3) {
                    stmt.p1 = params[2];
                }
                
                if (stmt.type == NestedLogicType.call) {
                    // call类型：参数2是p2，参数3是嵌套代码
                    if (params.length >= 4) {
                        stmt.p2 = params[3];
                    }
                    
                    if (params.length >= 5) {
                        // 改进反序列化逻辑，使用Base64解码嵌套代码
                        String rawCode = params[4];
                        if (rawCode.startsWith("\"")) {
                            try {
                                // 移除外层引号
                                String encoded = rawCode.substring(1, rawCode.length() - 1);
                                // 使用Base64解码嵌套代码
                                byte[] decodedBytes = Base64.getDecoder().decode(encoded);
                                stmt.nestedCode = new String(decodedBytes, StandardCharsets.UTF_8);
                            } catch (IllegalArgumentException e) {
                                // 如果解码失败，返回空字符串
                                stmt.nestedCode = "";
                            }
                        } else {
                            stmt.nestedCode = rawCode;
                        }
                    }
                } else {
                    // push类型：参数3是p2
                    if (params.length >= 4) {
                        stmt.p2 = params[3];
                    }
                }
                stmt.afterRead();
                return stmt;
            });
            LogicIO.allStatements.add(LNestedLogicStatement::new);
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
