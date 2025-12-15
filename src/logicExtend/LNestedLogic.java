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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static mindustry.logic.LCanvas.tooltip;
import static arc.Core.*;

public class LNestedLogic {
    
    /** nestedlogic指令的分支枚举 */
    public enum NestedLogicType {
        push("variable", "index"),
        call("logicName"),
        pop("variable", "index");
        
        public final String[] params;
        
        NestedLogicType(String... params) {
            this.params = params;
        }
    }
    
    /** 调用栈元素 */
    public static class CallStackElement {
        public String varName;
        public Object varValue;
        public int index = 0; // 栈的索引，默认0
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
    
    /** 日志记录方法，记录信息到指定路径 */
    public static void log(String message) {
        try {
            // 日志文件路径
            String logPath = "E:\\SteamLibrary\\steamapps\\common\\Mindustry\\nestedlogic.log";
            // 创建日志文件（如果不存在）
            File logFile = new File(logPath);
            logFile.getParentFile().mkdirs();
            
            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestamp = now.format(formatter);
            
            // 写入日志
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + timestamp + "] " + message + "\n");
            }
        } catch (IOException e) {
            // 忽略日志写入错误
            e.printStackTrace();
        }
    }
    
    public static class LNestedLogicStatement extends LStatement {
        // 指令类型
        public NestedLogicType type = NestedLogicType.push;
        // 第一个参数（push的变量名，或call的逻辑名）
        public String p1 = "";
        // 第二个参数（call的额外参数，push时忽略）
        public String p2 = "";
        // 存储嵌套的逻辑代码（仅call类型使用）
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
                        // 重建UI
                        table.parent.invalidateHierarchy();
                        table.clearChildren();
                        build(table);
                    }, 2, cell -> cell.size(120, 50));
                });
            }, mindustry.ui.Styles.logict, () -> {}).size(120, 40).color(table.color).left().padLeft(2);
            
            // 根据当前选项动态创建UI元素
            if (type == NestedLogicType.push) {
                // push分支：创建变量输入框和索引输入框
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(150f, 40f).pad(2f);
                
                fields(table, "Index", p2, str -> {
                    p2 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f);
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
            } else if (type == NestedLogicType.pop) {
                // pop分支：创建变量输入框和索引输入框
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(150f, 40f).pad(2f);
                
                fields(table, "Index", p2, str -> {
                    p2 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f);
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
                    // 注册要压入的变量
                    if (!p1.isEmpty()) {
                        builder.putVar(p1);
                    }
                    // push指令：将变量压入当前上下文的调用栈，支持索引
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
                            // 解析索引值，默认为0
                            int index = 0;
                            try {
                                if (!p2.isEmpty()) {
                                    index = Integer.parseInt(p2);
                                }
                            } catch (NumberFormatException e) {
                                index = 0;
                            }
                            
                            // 检查调用栈中是否已经存在该索引，如果存在则更新其值
                            boolean alreadyExists = false;
                            for (CallStackElement existingElem : context.callStack) {
                                if (existingElem.index == index) {
                                    // 更新已有索引的值
                                    existingElem.varName = p1;
                                    existingElem.varValue = var.isobj ? var.objval : var.numval;
                                    alreadyExists = true;
                                    log("push: 更新索引 " + index + " 的变量 " + p1 + " 值为 " + existingElem.varValue);
                                    break;
                                }
                            }
                            
                            if (!alreadyExists) {
                                CallStackElement elem = new CallStackElement();
                                elem.varName = p1;
                                elem.varValue = var.isobj ? var.objval : var.numval;
                                elem.index = index;
                                context.callStack.add(elem);
                                // 记录日志
                                log("push: 将变量 " + p1 + " 压入索引 " + index + "，值为 " + elem.varValue);
                            }
                        } else {
                            // 记录日志
                            log("push: 变量 " + p1 + " 不存在，无法压入调用栈");
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
                        
                        // 复制所有变量（除了@counter）
                        // 这确保嵌套逻辑能访问主逻辑中所有变量，包括普通变量和全局变量
                        for (arc.struct.OrderedMap.Entry<String, LVar> entry : builder.vars) {
                            String key = entry.key;
                            LVar var = entry.value;
                            if (!key.equals("@counter")) {
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
                            // 记录日志：开始执行call指令
                            log("call: 开始执行call指令，逻辑名称: " + p1);
                            
                            // 进入新的调用上下文
                        CallContext context = new CallContext();
                        context.callCounter = exec.counter.numval;
                        // 复制前一个上下文的调用栈（如果存在）
                        if (!callContextStack.isEmpty()) {
                            CallContext prevContext = callContextStack.get(callContextStack.size - 1);
                            context.callStack = prevContext.callStack.copy();
                            log("call: 复制前一个上下文的调用栈，栈大小: " + context.callStack.size);
                        }
                        callContextStack.add(context);
                             
                            try {
                                // 获取当前上下文的调用栈
                                CallContext currentContext = getCurrentContext();
                                
                                // 记录日志：当前调用栈信息
                                log("call: 当前调用栈大小: " + currentContext.callStack.size);
                                // 只记录一次调用栈元素信息，避免大量重复日志
                                if (!currentContext.callStack.isEmpty()) {
                                    CallStackElement firstElem = currentContext.callStack.first();
                                    log("call: 调用栈示例元素 - 变量名: " + firstElem.varName + ", 值: " + firstElem.varValue);
                                }
                                
                                // 创建嵌套执行器
                                log("call: 创建嵌套执行器");
                                LExecutor nestedExec = new LExecutor();
                                nestedExec.build = exec.build;
                                nestedExec.team = exec.team;
                                nestedExec.privileged = exec.privileged;
                                nestedExec.links = exec.links;
                                nestedExec.linkIds = exec.linkIds;
                                
                                // 关键修复：初始化嵌套执行器的vars数组
                                // 将nestedBuilder中的所有非恒定变量复制到嵌套执行器的vars数组中
                                log("call: 初始化嵌套执行器的vars数组，变量数量: " + nestedBuilder.vars.size);
                                nestedExec.vars = nestedBuilder.vars.values().toSeq().retainAll(var -> !var.constant).toArray(LVar.class);
                                // 为每个变量设置id
                                for (int i = 0; i < nestedExec.vars.length; i++) {
                                    nestedExec.vars[i].id = i;
                                }
                                
                                // 初始化嵌套执行器的counter、unit、thisv等字段
                                nestedExec.counter = nestedBuilder.getVar("@counter");
                                nestedExec.unit = nestedBuilder.getVar("@unit");
                                nestedExec.thisv = nestedBuilder.getVar("@this");
                                nestedExec.ipt = nestedBuilder.putConst("@ipt", nestedExec.build != null ? nestedExec.build.ipt : 0);
                                
                                // 记录日志：嵌套执行器变量列表
                                log("call: 嵌套执行器变量数量: " + nestedExec.vars.length);
                                for (LVar var : nestedExec.vars) {
                                    log("call: 嵌套执行器变量 - 名称: " + var.name + ", 类型: " + (var.isobj ? "对象" : "数值") + ", 值: " + (var.isobj ? var.objval : var.numval));
                                }
                                
                                // 复制调用栈中的变量值到嵌套执行器
                                log("call: 复制调用栈中的变量值到嵌套执行器");
                                for (CallStackElement elem : currentContext.callStack) {
                                    // 从嵌套执行器中获取变量
                                    LVar nestedVar = nestedExec.optionalVar(elem.varName);
                                    if (nestedVar != null) {
                                        // 变量存在，设置其值为调用栈中保存的值
                                        if (elem.varValue instanceof Double) {
                                            nestedVar.isobj = false;
                                            nestedVar.numval = (Double) elem.varValue;
                                        } else {
                                            nestedVar.isobj = true;
                                            nestedVar.objval = elem.varValue;
                                        }
                                        log("call: 复制变量 - 名称: " + elem.varName + ", 值: " + elem.varValue + " 到嵌套执行器");
                                    } else {
                                        log("call: 嵌套执行器中未找到变量 - 名称: " + elem.varName);
                                    }
                                }
                                
                                // 记录日志：执行嵌套逻辑前
                                log("call: 开始执行嵌套逻辑，指令数量: " + nestedInstructions.length);
                                
                                // 执行嵌套指令，模拟LExecutor.runOnce()的方式，考虑counter变化
                                // 主执行器的@counter值由主执行器自己管理，只会在整个call指令执行完后递增1
                                int instructionCount = 0;
                                double originalCounter = nestedExec.counter.numval;
                                
                                // 设置嵌套执行器的instructions，用于jump指令
                                nestedExec.instructions = nestedInstructions;
                                
                                // 执行嵌套指令，直到达到最大指令数限制或counter超出范围
                                while (instructionCount < nestedInstructions.length) {
                                    if (exec.counter.numval >= LExecutor.maxInstructions) {
                                        log("call: 达到最大指令数限制，停止执行嵌套逻辑");
                                        break;
                                    }
                                    
                                    // 检查counter是否在有效范围内
                                    double counterVal = nestedExec.counter.numval;
                                    if (counterVal < 0 || counterVal >= nestedInstructions.length) {
                                        log("call: counter超出范围，停止执行嵌套逻辑");
                                        break;
                                    }
                                    
                                    // 执行当前指令
                                    LExecutor.LInstruction inst = nestedInstructions[(int)counterVal];
                                    inst.run(nestedExec);
                                    
                                    // 增加指令计数
                                    instructionCount++;
                                }
                                
                                // 恢复嵌套执行器的counter到原始值
                                nestedExec.counter.numval = originalCounter;
                                
                                // 清除嵌套执行器的instructions
                                nestedExec.instructions = new LExecutor.LInstruction[0];
                                
                                log("call: 执行了 " + instructionCount + " 条嵌套指令");
                                
                                // 记录日志：执行嵌套逻辑后
                                log("call: 嵌套逻辑执行完毕");
                                
                                // 更新调用栈中的变量值（从嵌套执行器）
                                for (CallStackElement elem : currentContext.callStack) {
                                    LVar nestedVar = nestedExec.optionalVar(elem.varName);
                                    if (nestedVar != null) {
                                        // 更新调用栈中的变量值
                                        elem.varValue = nestedVar.isobj ? nestedVar.objval : nestedVar.numval;
                                        log("call: 更新调用栈中变量 " + elem.varName + " 的值为 " + elem.varValue);
                                    }
                                }
                                
                                // 将更新后的调用栈写回到前一个上下文
                                if (callContextStack.size > 1) {
                                    CallContext prevContext = callContextStack.get(callContextStack.size - 2);
                                    // 清除前一个上下文的调用栈
                                    prevContext.callStack.clear();
                                    // 将当前上下文的调用栈复制到前一个上下文
                                    prevContext.callStack.addAll(currentContext.callStack);
                                }
                                
                            } finally {
                                // 退出调用上下文
                                callContextStack.pop();
                                log("call: 退出调用上下文，执行完毕");
                            }
                        };
                    } catch (Exception e) {
                        // 如果编译失败，返回空指令
                        return (LExecutor exec) -> {};
                    }
                    
                case pop:
                    // 注册要弹出到的变量
                    if (!p1.isEmpty()) {
                        builder.putVar(p1);
                    }
                    // pop指令：从调用栈中弹出指定索引的值到指定变量
                    return (LExecutor exec) -> {
                        // 获取当前调用上下文
                        CallContext context = getCurrentContext();
                        if (context == null || context.callStack.isEmpty()) {
                            log("pop: 调用栈为空，无法弹出值");
                            return;
                        }
                        
                        // 解析索引值，默认为0
                        int index = 0;
                        try {
                            if (!p2.isEmpty()) {
                                index = Integer.parseInt(p2);
                            }
                        } catch (NumberFormatException e) {
                            index = 0;
                        }
                        
                        // 查找指定索引的元素
                        CallStackElement targetElem = null;
                        for (CallStackElement elem : context.callStack) {
                            if (elem.index == index) {
                                targetElem = elem;
                                break;
                            }
                        }
                        
                        if (targetElem != null) {
                            // 获取要弹出到的变量
                        LVar targetVar = exec.optionalVar(p1);
                        if (targetVar == null) {
                            // 变量不存在，跳过
                            log("pop: 目标变量 " + p1 + " 不存在");
                            return;
                        }
                            
                            // 设置变量值
                            if (targetElem.varValue instanceof Double) {
                                targetVar.isobj = false;
                                targetVar.numval = (Double) targetElem.varValue;
                            } else {
                                targetVar.isobj = true;
                                targetVar.objval = targetElem.varValue;
                            }
                            
                            // 从调用栈中移除该元素
                            context.callStack.remove(targetElem);
                            
                            // 记录日志
                            log("pop: 从索引 " + index + " 弹出值 " + targetElem.varValue + " 到变量 " + p1);
                        } else {
                            // 记录日志
                            log("pop: 调用栈中不存在索引为 " + index + " 的元素");
                        }
                    };
                    
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
                    // call类型：支持两种格式
                    // 格式1: nestedlogic call logicName "encodedCode" (没有p2参数)
                    // 格式2: nestedlogic call logicName p2 "encodedCode" (带有p2参数)
                    if (params.length >= 4) {
                        // 检查参数3是否是引号包围的Base64代码
                        if (params[3].startsWith("\"")) {
                            // 格式1: 参数3是嵌套代码
                            try {
                                // 移除外层引号
                                String encoded = params[3].substring(1, params[3].length() - 1);
                                // 使用Base64解码嵌套代码
                                byte[] decodedBytes = Base64.getDecoder().decode(encoded);
                                stmt.nestedCode = new String(decodedBytes, StandardCharsets.UTF_8);
                            } catch (IllegalArgumentException e) {
                                // 如果解码失败，返回空字符串
                                stmt.nestedCode = "";
                            }
                        } else {
                            // 格式2: 参数3是p2，参数4是嵌套代码
                            stmt.p2 = params[3];
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
                        }
                    }
                } else {
                    // push和pop类型：支持索引参数
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
