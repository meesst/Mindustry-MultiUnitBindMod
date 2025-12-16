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
    
    /** 日志记录开关，1表示开启日志，0表示关闭日志，默认开启 */
    public static int debugLog = 0;
    
    /** nestedlogic指令的分支枚举 */
    public enum NestedLogicType {
        push("variable", "index", "stackName"),
        call("logicName"),
        pop("variable", "index", "stackName");
        
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
        public String stackName = "default"; // 栈名称，默认"default"
    }
    
    /** 调用上下文 */
    public static class CallContext {
        // 使用ObjectMap存储多个栈，key为栈名称，value为该栈的元素列表
        public arc.struct.ObjectMap<String, Seq<CallStackElement>> stacks = new arc.struct.ObjectMap<>();
        public double callCounter;
        public mindustry.logic.LogicDialog dialog;
        
        /** 获取指定名称的栈，如果不存在则创建 */
        public Seq<CallStackElement> getStack(String stackName) {
            if (stackName == null || stackName.isEmpty()) {
                stackName = "default";
            }
            // Arc库的ObjectMap没有putIfAbsent方法，使用containsKey替代
            if (!stacks.containsKey(stackName)) {
                stacks.put(stackName, new Seq<>());
            }
            return stacks.get(stackName);
        }
    }
    
    /** 全局调用上下文栈 */
    public static final Seq<CallContext> callContextStack = new Seq<>();
    
    /** 获取当前调用上下文 */
    public static CallContext getCurrentContext() {
        return callContextStack.isEmpty() ? null : callContextStack.get(callContextStack.size - 1);
    }
    
    /** 日志记录方法，记录信息到指定路径 */
    public static void log(String message) {
        // 只有当debugLog等于1时才记录日志
        if (debugLog == 1) {
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
    }
    
    public static class LNestedLogicStatement extends LStatement {
        // 指令类型
        public NestedLogicType type = NestedLogicType.push;
        // 第一个参数（push的变量名，或call的逻辑名）
        public String p1 = "";
        // 第二个参数（index，push/pop使用）
        public String p2 = "";
        // 第三个参数（stackName，push/pop使用）
        public String p3 = "";
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
                    // 直接使用父类的showSelect方法，它会自动为枚举值添加悬浮提示
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
                // push分支：创建变量输入框、索引输入框和栈名称输入框
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.variable", "Variable")));
                
                fields(table, "Index", p2, str -> {
                    p2 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.index", "Index")));
                
                fields(table, "Stack Name", p3, str -> {
                    p3 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.stackname", "Stack Name")));
            } else if (type == NestedLogicType.call) {
                // call分支：创建变量输入框 + 编辑页面按钮
                fields(table, "Logic Name", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(120f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.logicname", "Logic Name")));
                
                table.button(b -> {
                    b.label(() -> "Edit Logic");
                    b.clicked(() -> {
                        log("call: 开始点击Edit Logic按钮");
                        
                        // 保存当前的canvas静态变量，使用数组包装以便在lambda中访问
                        final mindustry.logic.LCanvas[] originalCanvas = {null};
                        final java.lang.reflect.Field[] canvasField = {null};
                        final boolean[] reflectionSuccess = {false};
                        
                        try {
                            log("call: 开始尝试获取LCanvas类");
                            // 获取LCanvas类
                            Class<?> lCanvasClass = mindustry.logic.LCanvas.class;
                            log("call: 成功获取LCanvas类: " + lCanvasClass.getName());
                            
                            // 使用反射获取LCanvas类的私有静态canvas字段
                            log("call: 开始尝试获取canvas字段");
                            canvasField[0] = lCanvasClass.getDeclaredField("canvas");
                            log("call: 成功获取canvas字段");
                            
                            // 设置字段可访问
                            canvasField[0].setAccessible(true);
                            log("call: 成功设置canvas字段可访问");
                            
                            // 获取当前canvas值
                            originalCanvas[0] = (mindustry.logic.LCanvas) canvasField[0].get(null);
                            log("call: 成功获取当前canvas值: " + originalCanvas[0]);
                            reflectionSuccess[0] = true;
                        } catch (NoSuchFieldException e) {
                            log("call: 无法找到canvas字段: " + e.getMessage());
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            log("call: 无法访问canvas字段: " + e.getMessage());
                            e.printStackTrace();
                        } catch (SecurityException e) {
                            log("call: 安全异常: " + e.getMessage());
                            e.printStackTrace();
                        } catch (Exception e) {
                            log("call: 其他异常: " + e.getMessage());
                            e.printStackTrace();
                        }
                        
                        // 创建恢复canvas的Runnable
                        Runnable restoreCanvasRunnable = () -> {
                            log("call: 开始执行恢复canvas的Runnable");
                            // 恢复原始canvas静态变量
                            if (reflectionSuccess[0]) {
                                try {
                                    log("call: 开始尝试恢复原始canvas");
                                    if (canvasField[0] != null && originalCanvas[0] != null) {
                                        // 获取当前canvas值，用于比较
                                        mindustry.logic.LCanvas currentCanvas = (mindustry.logic.LCanvas) canvasField[0].get(null);
                                        log("call: 当前canvas值: " + currentCanvas + ", 原始canvas值: " + originalCanvas[0]);
                                        
                                        // 恢复原始canvas值
                                        canvasField[0].set(null, originalCanvas[0]);
                                        log("call: 成功恢复原始canvas");
                                        
                                        // 获取恢复后的canvas值，用于验证
                                        mindustry.logic.LCanvas restoredCanvas = (mindustry.logic.LCanvas) canvasField[0].get(null);
                                        log("call: 恢复后的canvas值: " + restoredCanvas);
                                        
                                        // 触发主编辑器的UI重绘
                                        log("call: 开始触发主编辑器UI重绘");
                                        originalCanvas[0].rebuild();
                                        log("call: 调用rebuild()");
                                        log("call: 主编辑器UI重绘完成");
                                    }
                                } catch (Exception e) {
                                    log("call: 无法恢复原始canvas字段: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                                log("call: 反射获取失败，跳过恢复操作");
                            }
                            log("call: 恢复canvas的Runnable执行完成");
                        };
                        
                        // 打开嵌套逻辑编辑器
                        log("call: 开始创建并显示嵌套逻辑编辑器");
                        mindustry.logic.LogicDialog nestedDialog = new mindustry.logic.LogicDialog();
                        
                        // 添加hidden回调，无论用户是点击保存还是返回按钮，都会触发
                        nestedDialog.hidden(restoreCanvasRunnable);
                        
                        nestedDialog.show(nestedCode, null, false, modifiedCode -> {
                            // 保存修改后的代码
                            log("call: 开始处理嵌套逻辑编辑器保存回调");
                            nestedCode = modifiedCode;
                            saveUI();
                            log("call: 保存修改后的代码成功");
                            log("call: 嵌套逻辑编辑器保存回调处理完成");
                        });
                        
                        log("call: Edit Logic按钮点击处理完成");
                    });
                }, mindustry.ui.Styles.logict, () -> {}).size(120f, 40f).color(table.color).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.editlogic", "Edit Logic")));
            } else if (type == NestedLogicType.pop) {
                // pop分支：创建变量输入框、索引输入框和栈名称输入框
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.variable", "Variable")));
                
                fields(table, "Index", p2, str -> {
                    p2 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.index", "Index")));
                
                fields(table, "Stack Name", p3, str -> {
                    p3 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.stackname", "Stack Name")));
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
                    // 注册要压入的变量和索引变量
                    if (!p1.isEmpty()) {
                        builder.putVar(p1);
                    }
                    // 注册索引变量（如果是变量名）
                    if (!p2.isEmpty() && !p2.matches("\\d+")) {
                        builder.putVar(p2);
                    }
                    // push指令：将变量压入当前上下文的调用栈，支持索引和多栈
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
                            // 解析索引值，支持变量引用
                            int index = 0;
                            if (!p2.isEmpty()) {
                                // 首先尝试将p2作为变量名获取值
                                LVar indexVar = exec.optionalVar(p2);
                                if (indexVar != null) {
                                    // 是变量名
                                    if (!indexVar.isobj) {
                                        // 数值类型：直接使用其值
                                        index = (int) indexVar.numval;
                                        log("push: 使用变量 " + p2 + " 的值 " + indexVar.numval + " 作为索引");
                                    } else {
                                        // 对象类型：检查是否为文本
                                        if (indexVar.objval instanceof String) {
                                            // 文本类型：尝试解析为数字
                                            String textValue = (String) indexVar.objval;
                                            try {
                                                index = Integer.parseInt(textValue);
                                                log("push: 成功将文本变量 \"" + p2 + "\" 的值 \"" + textValue + "\" 解析为索引 " + index);
                                            } catch (NumberFormatException e) {
                                                log("push: 无法将文本变量 \"" + p2 + "\" 的值 \"" + textValue + "\" 解析为数字，使用默认索引 0");
                                            }
                                        } else {
                                            // 其他对象类型：使用默认值
                                            log("push: 变量 " + p2 + " 是对象类型，使用默认索引 0");
                                        }
                                    }
                                } else {
                                    // 尝试作为直接数字解析
                                    try {
                                        index = Integer.parseInt(p2);
                                        log("push: 使用直接数字 " + p2 + " 作为索引");
                                    } catch (NumberFormatException e) {
                                        log("push: 无法解析 \"" + p2 + "\" 为数字，使用默认索引 0");
                                    }
                                }
                            }
                            
                            // 获取栈名称，默认为"default"
                            String stackName = p3 == null || p3.isEmpty() ? "default" : p3;
                            
                            // 获取指定名称的栈
                            Seq<CallStackElement> currentStack = context.getStack(stackName);
                            
                            // 检查调用栈中是否已经存在该索引，如果存在则更新其值
                            boolean alreadyExists = false;
                            for (CallStackElement existingElem : currentStack) {
                                if (existingElem.index == index) {
                                    // 更新已有索引的值
                                    existingElem.varName = p1;
                                    existingElem.varValue = var.isobj ? var.objval : var.numval;
                                    existingElem.stackName = stackName;
                                    alreadyExists = true;
                                    log("push: 更新栈 \"" + stackName + "\" 中索引 " + index + " 的变量 " + p1 + " 值为 " + existingElem.varValue);
                                    break;
                                }
                            }
                            
                            if (!alreadyExists) {
                                CallStackElement elem = new CallStackElement();
                                elem.varName = p1;
                                elem.varValue = var.isobj ? var.objval : var.numval;
                                elem.index = index;
                                elem.stackName = stackName;
                                currentStack.add(elem);
                                // 记录日志
                                log("push: 将变量 " + p1 + " 压入栈 \"" + stackName + "\" 的索引 " + index + "，值为 " + elem.varValue);
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
                        // 设置nestedBuilder的privileged字段为主builder的privileged字段
                        java.lang.reflect.Field privilegedField = null;
                        try {
                            privilegedField = LAssembler.class.getDeclaredField("privileged");
                            privilegedField.setAccessible(true);
                            privilegedField.setBoolean(nestedBuilder, privilegedField.getBoolean(builder));
                        } catch (Exception e) {
                            // 忽略反射异常
                        }
                        
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
                        // 复制前一个上下文的所有栈（如果存在）
                        if (!callContextStack.isEmpty()) {
                            CallContext prevContext = callContextStack.get(callContextStack.size - 1);
                            // 遍历前一个上下文的所有栈
                            for (arc.struct.ObjectMap.Entry<String, Seq<CallStackElement>> stackEntry : prevContext.stacks) {
                                String stackName = stackEntry.key;
                                Seq<CallStackElement> stack = stackEntry.value;
                                // 复制栈中的所有元素
                                Seq<CallStackElement> newStack = new Seq<>();
                                for (CallStackElement elem : stack) {
                                    CallStackElement newElem = new CallStackElement();
                                    newElem.varName = elem.varName;
                                    newElem.varValue = elem.varValue;
                                    newElem.index = elem.index;
                                    newElem.stackName = elem.stackName;
                                    newStack.add(newElem);
                                }
                                context.stacks.put(stackName, newStack);
                            }
                            log("call: 复制前一个上下文的所有栈，共 " + context.stacks.size + " 个栈");
                        }
                        callContextStack.add(context);
                              
                            try {
                                // 获取当前上下文
                                CallContext currentContext = getCurrentContext();
                                
                                // 记录日志：当前所有栈信息
                                log("call: 当前共有 " + currentContext.stacks.size + " 个栈");
                                // 记录每个栈的大小和第一个元素
                                for (arc.struct.ObjectMap.Entry<String, Seq<CallStackElement>> stackEntry : currentContext.stacks) {
                                    String stackName = stackEntry.key;
                                    Seq<CallStackElement> stack = stackEntry.value;
                                    log("call: 栈 \"" + stackName + "\" 大小: " + stack.size);
                                    // 记录栈的第一个元素（如果有）
                                    if (!stack.isEmpty()) {
                                        CallStackElement firstElem = stack.first();
                                        log("call: 栈 \"" + stackName + "\" 示例元素 - 变量名: " + firstElem.varName + ", 值: " + firstElem.varValue);
                                    }
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
                                
                                // 从主执行器复制动态变量的实际值到嵌套执行器
                                // 这些变量在编译时可能没有正确的值，需要在运行时获取
                                nestedExec.thisv.set(exec.thisv);
                                nestedExec.unit.set(exec.unit);
                                
                                // 记录日志：嵌套执行器变量列表
                                log("call: 嵌套执行器变量数量: " + nestedExec.vars.length);
                                for (LVar var : nestedExec.vars) {
                                    log("call: 嵌套执行器变量 - 名称: " + var.name + ", 类型: " + (var.isobj ? "对象" : "数值") + ", 值: " + (var.isobj ? var.objval : var.numval));
                                }
                                
                                // 复制所有栈中的变量值到嵌套执行器
                                log("call: 复制所有栈中的变量值到嵌套执行器");
                                // 遍历所有栈
                                for (arc.struct.ObjectMap.Entry<String, Seq<CallStackElement>> stackEntry : currentContext.stacks) {
                                    String stackName = stackEntry.key;
                                    Seq<CallStackElement> stack = stackEntry.value;
                                    // 遍历当前栈中的所有元素
                                    for (CallStackElement elem : stack) {
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
                                            log("call: 从栈 \"" + stackName + "\" 复制变量 - 名称: " + elem.varName + ", 值: " + elem.varValue + " 到嵌套执行器");
                                        } else {
                                            log("call: 嵌套执行器中未找到变量 - 名称: " + elem.varName);
                                        }
                                    }
                                }
                                
                                // 记录日志：执行嵌套逻辑前
                                log("call: 开始执行嵌套逻辑，指令数量: " + nestedInstructions.length);
                                
                                // 编译嵌套指令到nestedBuilder
                                nestedBuilder.instructions = nestedInstructions;
                                
                                // 加载嵌套指令到嵌套执行器
                                nestedExec.load(nestedBuilder);
                                
                                // 重置嵌套执行器的counter为0，确保从第一条指令开始执行
                                nestedExec.counter.numval = 0;
                                
                                // 执行嵌套指令，使用LExecutor的原生执行逻辑
                                // 执行嵌套指令，直到所有指令执行完毕或达到最大指令数限制
                                int instructionCount = 0;
                                double originalMainCounter = exec.counter.numval;
                                
                                // 为嵌套执行器设置独立的最大指令数限制，防止无限循环卡死游戏
                                // 使用与主执行器相同的最大指令数，但独立计算
                                int nestedMaxInstructions = LExecutor.maxInstructions;
                                int nestedCounter = 0;
                                
                                while (true) {
                                    // 检查嵌套执行器是否已经执行完毕
                                    if (nestedExec.counter.numval >= nestedExec.instructions.length) {
                                        log("call: 嵌套逻辑执行完毕");
                                        break;
                                    }
                                    
                                    // 检查嵌套执行器是否达到最大指令数限制（独立于主执行器）
                                    // 这确保嵌套逻辑的无限循环不会导致游戏卡死
                                    if (nestedCounter >= nestedMaxInstructions) {
                                        log("call: 嵌套逻辑达到单tick最大指令数限制，停止执行");
                                        break;
                                    }
                                    
                                    // 执行一条嵌套指令
                                    nestedExec.runOnce();
                                    
                                    // 增加指令计数
                                    instructionCount++;
                                    nestedCounter++;
                                }
                                
                                // 恢复主执行器的counter值，确保call指令不会影响主执行器的指令预算
                                // 嵌套逻辑应该在独立的指令预算中执行，不影响主逻辑
                                exec.counter.numval = originalMainCounter;
                                
                                log("call: 执行了 " + instructionCount + " 条嵌套指令");
                                
                                // 更新所有栈中的变量值（从嵌套执行器）
                                for (arc.struct.ObjectMap.Entry<String, Seq<CallStackElement>> stackEntry : currentContext.stacks) {
                                    String stackName = stackEntry.key;
                                    Seq<CallStackElement> stack = stackEntry.value;
                                    // 遍历当前栈中的所有元素
                                    for (CallStackElement elem : stack) {
                                        LVar nestedVar = nestedExec.optionalVar(elem.varName);
                                        if (nestedVar != null) {
                                            // 更新调用栈中的变量值
                                            elem.varValue = nestedVar.isobj ? nestedVar.objval : nestedVar.numval;
                                            log("call: 更新栈 \"" + stackName + "\" 中变量 " + elem.varName + " 的值为 " + elem.varValue);
                                        }
                                    }
                                }
                                
                                // 将更新后的所有栈写回到前一个上下文
                                if (callContextStack.size > 1) {
                                    CallContext prevContext = callContextStack.get(callContextStack.size - 2);
                                    // 清除前一个上下文的所有栈
                                    prevContext.stacks.clear();
                                    // 将当前上下文的所有栈复制到前一个上下文
                                    for (arc.struct.ObjectMap.Entry<String, Seq<CallStackElement>> stackEntry : currentContext.stacks) {
                                        String stackName = stackEntry.key;
                                        Seq<CallStackElement> stack = stackEntry.value;
                                        // 复制栈中的所有元素
                                        Seq<CallStackElement> newStack = new Seq<>();
                                        for (CallStackElement elem : stack) {
                                            CallStackElement newElem = new CallStackElement();
                                            newElem.varName = elem.varName;
                                            newElem.varValue = elem.varValue;
                                            newElem.index = elem.index;
                                            newElem.stackName = elem.stackName;
                                            newStack.add(newElem);
                                        }
                                        prevContext.stacks.put(stackName, newStack);
                                    }
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
                    // 注册要弹出到的变量和索引变量
                    if (!p1.isEmpty()) {
                        builder.putVar(p1);
                    }
                    // 注册索引变量（如果是变量名）
                    if (!p2.isEmpty() && !p2.matches("\\d+")) {
                        builder.putVar(p2);
                    }
                    // pop指令：从调用栈中弹出指定索引的值到指定变量，支持多栈
                    return (LExecutor exec) -> {
                        // 获取当前调用上下文
                        CallContext context = getCurrentContext();
                        if (context == null) {
                            log("pop: 调用栈为空，无法弹出值");
                            return;
                        }
                        
                        // 获取栈名称，默认为"default"
                        String stackName = p3 == null || p3.isEmpty() ? "default" : p3;
                        
                        // 获取指定名称的栈
                        Seq<CallStackElement> currentStack = context.getStack(stackName);
                        if (currentStack.isEmpty()) {
                            log("pop: 栈 \"" + stackName + "\" 为空，无法弹出值");
                            return;
                        }
                        
                        // 解析索引值，支持变量引用
                        int index = 0;
                        if (!p2.isEmpty()) {
                            // 首先尝试将p2作为变量名获取值
                            LVar indexVar = exec.optionalVar(p2);
                            if (indexVar != null) {
                                // 是变量名
                                if (!indexVar.isobj) {
                                    // 数值类型：直接使用其值
                                    index = (int) indexVar.numval;
                                    log("pop: 使用变量 " + p2 + " 的值 " + indexVar.numval + " 作为索引");
                                } else {
                                    // 对象类型：检查是否为文本
                                    if (indexVar.objval instanceof String) {
                                        // 文本类型：尝试解析为数字
                                        String textValue = (String) indexVar.objval;
                                        try {
                                            index = Integer.parseInt(textValue);
                                            log("pop: 成功将文本变量 \"" + p2 + "\" 的值 \"" + textValue + "\" 解析为索引 " + index);
                                        } catch (NumberFormatException e) {
                                            log("pop: 无法将文本变量 \"" + p2 + "\" 的值 \"" + textValue + "\" 解析为数字，使用默认索引 0");
                                        }
                                    } else {
                                        // 其他对象类型：使用默认值
                                        log("pop: 变量 " + p2 + " 是对象类型，使用默认索引 0");
                                    }
                                }
                            } else {
                                // 尝试作为直接数字解析
                                try {
                                    index = Integer.parseInt(p2);
                                    log("pop: 使用直接数字 " + p2 + " 作为索引");
                                } catch (NumberFormatException e) {
                                    log("pop: 无法解析 \"" + p2 + "\" 为数字，使用默认索引 0");
                                }
                            }
                        }
                        
                        // 查找指定索引的元素
                        CallStackElement targetElem = null;
                        for (CallStackElement elem : currentStack) {
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
                            currentStack.remove(targetElem);
                            
                            // 记录日志
                            log("pop: 从栈 \"" + stackName + "\" 的索引 " + index + " 弹出值 " + targetElem.varValue + " 到变量 " + p1);
                        } else {
                            // 记录日志
                            log("pop: 栈 \"" + stackName + "\" 中不存在索引为 " + index + " 的元素");
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
            
            // 序列化第三个参数（stackName），仅push和pop指令使用
            if (type == NestedLogicType.push || type == NestedLogicType.pop) {
                builder.append(" ");
                builder.append(p3);
            }
            
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
            // 嵌套代码的解析已经在customParsers中处理
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
                    // push和pop类型：支持索引参数和栈名称
                    if (params.length >= 4) {
                        stmt.p2 = params[3];
                    }
                    // 解析第三个参数（stackName）
                    if (params.length >= 5) {
                        stmt.p3 = params[4];
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
