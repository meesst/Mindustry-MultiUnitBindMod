package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.LogicIO;
import mindustry.logic.LAssembler;
import mindustry.logic.LCategory;
import mindustry.logic.LExecutor;
import mindustry.logic.LStatement;
import mindustry.logic.LVar;
import mindustry.gen.Building;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static mindustry.logic.LCanvas.tooltip;
import static arc.Core.*;

public class LNestedLogic {
    
    /** 日志记录开关，1表示开启日志，0表示关闭日志，默认关闭 */
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
        public long lastPushTime; // 最后push时间，用于自动回收
    }
    

    
    /** 全局栈存储，key为栈名称，value为该栈的元素列表 */
    public static final arc.struct.ObjectMap<String, Seq<CallStackElement>> stacks = new arc.struct.ObjectMap<>();
    
    /** 用于管理栈操作的锁，确保同一时间只能读或写，支持同时读，不支持同时写 */
    private static final Object stackLock = new Object();
    
    /** 定时器，用于自动回收栈元素 */
    private static arc.util.Timer.Task cleanupTask;
    
    /** 元素超时时间，默认30秒 */
    public static int elementTimeout = 30;
    
    /** 检查间隔，默认10秒 */
    public static int checkInterval = 10;
    
    static {
        // 初始化自动回收定时器
        initCleanupTimer();
    }
    
    /** 初始化自动回收定时器 */
    private static void initCleanupTimer() {
        // 如果定时器已存在，先取消
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        // 创建新的定时器任务，每checkInterval秒执行一次
        cleanupTask = arc.util.Timer.schedule(() -> {
            cleanupStacks();
        }, checkInterval, checkInterval);
        
        log("初始化自动回收定时器，每" + checkInterval + "秒检查一次，元素超时时间：" + elementTimeout + "秒");
    }
    
    /** 清理超时的栈元素和空栈 */
    private static void cleanupStacks() {
        if (stacks.isEmpty()) {
            return; // 没有栈需要清理
        }
        
        log("开始清理栈元素，当前栈数量：" + stacks.size);
        
        long currentTime = System.currentTimeMillis();
        // 从设置中读取超时时间，默认30秒
        int timeout = arc.Core.settings.getInt("lnestedlogic-element-timeout", elementTimeout);
        long timeoutMs = timeout * 1000;
        
        // 遍历所有栈
        Seq<String> emptyStacks = new Seq<>();
        for (arc.struct.ObjectMap.Entry<String, Seq<CallStackElement>> stackEntry : stacks) {
            String stackName = stackEntry.key;
            Seq<CallStackElement> stack = stackEntry.value;
            
            if (stack.isEmpty()) {
                // 栈已经为空，标记为需要删除
                emptyStacks.add(stackName);
                continue;
            }
            
            // 清理超时元素
            Seq<CallStackElement> toRemove = new Seq<>();
            for (CallStackElement elem : stack) {
                if (currentTime - elem.lastPushTime > timeoutMs) {
                    // 元素超时，标记为需要删除
                    toRemove.add(elem);
                }
            }
            
            // 删除超时元素
            if (!toRemove.isEmpty()) {
                for (CallStackElement elem : toRemove) {
                    stack.remove(elem);
                    log("清理超时元素：栈\"" + stackName + "\" 索引 " + elem.index + " 变量 " + elem.varName);
                }
                
                // 检查栈是否变为空
                if (stack.isEmpty()) {
                    emptyStacks.add(stackName);
                }
            }
        }
        
        // 删除空栈
        for (String stackName : emptyStacks) {
            stacks.remove(stackName);
            log("清理空栈：" + stackName);
        }
        
        log("清理完成，剩余栈数量：" + stacks.size);
    }
    
    /** 获取指定名称的栈，如果不存在则创建 */
    public static Seq<CallStackElement> getStack(String stackName) {
        if (stackName == null || stackName.isEmpty()) {
            stackName = "default";
        }
        // Arc库的ObjectMap没有putIfAbsent方法，使用containsKey替代
        if (!stacks.containsKey(stackName)) {
            stacks.put(stackName, new Seq<>());
        }
        return stacks.get(stackName);
    }
    
    /** 日志记录方法，记录信息到指定路径 */
    public static void log(String message) {
        // 每次调用都从设置中读取最新值，确保开关立即生效
        boolean isEnabled = arc.Core.settings.getBool("lnestedlogic-debug-log");
        if (isEnabled) {
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
        // 唯一编号，用于标识每个call指令实例
        public String uniqueId = UUID.randomUUID().toString();
        // 第一个参数（push的变量名，或call的逻辑名）
        public String p1 = "var";
        // 第二个参数（index，push/pop使用）
        public String p2 = "0";
        // 第三个参数（stackName，push/pop使用）
        public String p3 = "default";
        // 存储嵌套的逻辑代码（仅call类型使用）
        public String nestedCode = "";
        
        // 编译后的嵌套逻辑缓存
        private transient LAssembler cachedNestedBuilder = null;
        // 嵌套执行器缓存，用于保持变量状态
        private transient LExecutor cachedNestedExec = null;
        
        // 重写name方法，返回正确的指令名称
        @Override
        public String name() {
            return "lnestedlogic";
        }

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
                // fields方法内部会自动调用param()，为标签添加悬浮提示
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f);
                
                fields(table, "Index", p2, str -> {
                    p2 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f);
                
                fields(table, "Stack Name", p3, str -> {
                    p3 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f);
            } else if (type == NestedLogicType.call) {
                // call分支：创建变量输入框 + 编辑页面按钮
                fields(table, "Logic Name", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(120f, 40f).pad(2f);
                
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
                    // push指令：将变量压入全局调用栈，支持索引和多栈
                        return (LExecutor exec) -> {
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
                                
                                // 加锁确保栈操作的线程安全，同一时间只能读或写，支持同时读，不支持同时写
                                synchronized(stackLock) {
                                    // 获取指定名称的栈
                                    Seq<CallStackElement> currentStack = getStack(stackName);
                                    
                                    // 检查调用栈中是否已经存在该索引，如果存在则更新其值
                                    boolean alreadyExists = false;
                                    for (CallStackElement existingElem : currentStack) {
                                        if (existingElem.index == index) {
                                            // 更新已有索引的值
                                            existingElem.varName = p1;
                                            existingElem.varValue = var.isobj ? var.objval : var.numval;
                                            existingElem.stackName = stackName;
                                            existingElem.lastPushTime = System.currentTimeMillis();
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
                                        elem.lastPushTime = System.currentTimeMillis();
                                        currentStack.add(elem);
                                        // 记录日志
                                        log("push: 将变量 " + p1 + " 压入栈 \"" + stackName + "\" 的索引 " + index + "，值为 " + elem.varValue);
                                    }
                                }
                            } else {
                                // 记录日志
                                log("push: 变量 " + p1 + " 不存在，无法压入调用栈");
                            }
                        };
                    
                case call:
                    return (LExecutor exec) -> {
                        // 使用简单的嵌套深度限制
                        ThreadLocal<Integer> nestedDepth = ThreadLocal.withInitial(() -> 0);
                        if (nestedDepth.get() >= 5) { // 限制嵌套深度为5层
                            log("call: 嵌套深度超过限制，最大深度为5");
                            return;
                        }
                        
                        try {
                            nestedDepth.set(nestedDepth.get() + 1);
                            
                            // 记录日志：开始执行call指令
                            log("call: 开始执行call指令，逻辑名称: " + p1 + "，唯一编号: " + uniqueId);
                            
                            // 创建或获取嵌套执行器
                            LExecutor nestedExec;
                            
                            // 检查是否有缓存的嵌套执行器
                            if (cachedNestedExec == null) {
                                // 第一次执行，编译嵌套逻辑
                                log("call: 第一次执行，编译嵌套逻辑");
                                
                                // 直接编译嵌套逻辑，复用游戏的编译机制
                                LAssembler nestedBuilder = LAssembler.assemble(nestedCode, false);
                                
                                // 如果主逻辑块有链接点，将链接点变量添加到嵌套逻辑中
                                if (exec.build != null) {
                                    // 获取主逻辑块的链接点
                                    for (var link : exec.build.links) {
                                        // 检查链接点是否有效
                                        if (link.valid) {
                                            // 获取链接点对应的建筑
                                            Building building = mindustry.Vars.world.build(link.x, link.y);
                                            if (building != null) {
                                                // 在嵌套逻辑的汇编器中创建链接点变量
                                                nestedBuilder.putConst(link.name, building);
                                            }
                                        }
                                    }
                                }
                                
                                // 创建嵌套执行器
                                log("call: 创建嵌套执行器");
                                nestedExec = new LExecutor();
                                nestedExec.build = exec.build;
                                nestedExec.team = exec.team;
                                nestedExec.privileged = exec.privileged;
                                nestedExec.links = exec.links;
                                nestedExec.linkIds = exec.linkIds;
                                
                                // 加载嵌套指令到嵌套执行器
                                nestedExec.load(nestedBuilder);
                                
                                // 缓存编译后的嵌套逻辑和执行器
                                cachedNestedBuilder = nestedBuilder;
                                cachedNestedExec = nestedExec;
                                log("call: 缓存嵌套逻辑和执行器");
                            } else {
                                // 使用缓存的执行器，保持变量状态
                                log("call: 使用缓存的执行器，保持变量状态");
                                nestedExec = cachedNestedExec;
                                
                                // 更新执行器的属性
                                nestedExec.build = exec.build;
                                nestedExec.team = exec.team;
                                nestedExec.privileged = exec.privileged;
                                nestedExec.links = exec.links;
                                nestedExec.linkIds = exec.linkIds;
                            }
                            
                            // 从主执行器复制动态变量的实际值到嵌套执行器
                            if (nestedExec.thisv != null && exec.thisv != null) {
                                nestedExec.thisv.set(exec.thisv);
                            }
                            if (nestedExec.unit != null && exec.unit != null) {
                                nestedExec.unit.set(exec.unit);
                            }
                            
                            // 重置嵌套执行器的counter为0
                            if (nestedExec.counter != null) {
                                nestedExec.counter.numval = 0;
                            }
                            
                            // 执行嵌套指令，与游戏的执行机制一致
                            int instructionCount = 0;
                            double originalMainCounter = exec.counter.numval;
                            
                            // 为嵌套执行器设置独立的最大指令数限制
                            int nestedMaxInstructions = LExecutor.maxInstructions;
                            int nestedCounter = 0;
                            
                            while (true) {
                                // 检查嵌套执行器是否已经执行完毕
                                if (nestedExec.counter.numval >= nestedExec.instructions.length) {
                                    log("call: 嵌套逻辑执行完毕");
                                    break;
                                }
                                
                                // 检查嵌套执行器是否达到最大指令数限制
                                if (nestedCounter >= nestedMaxInstructions) {
                                    log("call: 嵌套逻辑达到单tick最大指令数限制，停止执行");
                                    break;
                                }
                                
                                // 执行一条嵌套指令，与游戏的执行机制一致
                                nestedExec.runOnce();
                                
                                // 增加指令计数
                                instructionCount++;
                                nestedCounter++;
                            }
                            
                            // 恢复主执行器的counter值
                            exec.counter.numval = originalMainCounter;
                            
                            log("call: 执行了 " + instructionCount + " 条嵌套指令，唯一编号: " + uniqueId);
                            
                        } finally {
                            nestedDepth.set(nestedDepth.get() - 1);
                            log("call: 嵌套逻辑执行完毕，退出调用，唯一编号: " + uniqueId);
                        }
                    };
                    
                case pop:
                    // 注册要弹出到的变量和索引变量
                    if (!p1.isEmpty()) {
                        builder.putVar(p1);
                    }
                    // 注册索引变量（如果是变量名）
                    if (!p2.isEmpty() && !p2.matches("\\d+")) {
                        builder.putVar(p2);
                    }
                    // pop指令：从全局调用栈中弹出指定索引的值到指定变量，支持多栈
                        return (LExecutor exec) -> {
                            // 获取栈名称，默认为"default"
                            String stackName = p3 == null || p3.isEmpty() ? "default" : p3;
                            
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
                            
                            // 加锁确保栈操作的线程安全，同一时间只能读或写，支持同时读，不支持同时写
                            synchronized(stackLock) {
                                // 获取指定名称的栈
                                Seq<CallStackElement> currentStack = getStack(stackName);
                                if (currentStack.isEmpty()) {
                                    log("pop: 栈 \"" + stackName + "\" 为空，无法弹出值");
                                    return;
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
            // 序列化唯一编号
            builder.append(uniqueId);
            builder.append(" ");
            
            if (type == NestedLogicType.call) {
                // call指令格式：nestedlogic call uniqueId logicName "encodedCode"
                // 序列化第一个参数（logicName，玩家备注）
                builder.append(p1);
                builder.append(" ");
                // 序列化嵌套代码，始终用引号包裹
                String encoded = Base64.getEncoder().encodeToString(nestedCode.getBytes(StandardCharsets.UTF_8));
                builder.append('"').append(encoded).append('"');
            } else {
                // push/pop指令：nestedlogic type uniqueId p1 p2 p3
                // 序列化第一个参数
                builder.append(p1);
                builder.append(" ");
                // 序列化第二个参数
                builder.append(p2);
                
                // 序列化第三个参数（stackName），仅push和pop指令使用
                builder.append(" ");
                builder.append(p3);
            }
        }

        @Override
        public void afterRead() {
            // 不需要额外处理，直接使用nestedCode
            // 嵌套代码的解析已经在customParsers中处理
        }
        
        // 重写setNestedCode方法，在nestedCode更新时清除缓存
        public void setNestedCode(String nestedCode) {
            this.nestedCode = nestedCode;
            // 清除缓存，下次执行时重新编译
            this.cachedNestedBuilder = null;
            this.cachedNestedExec = null;
            log("call: nestedCode更新，清除缓存");
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
                        stmt.type = NestedLogicType.call;
                        if (params.length >= 3) {
                            stmt.nestedCode = params[2];
                        }
                        return stmt;
                    }
                }
                
                // 解析唯一编号
                if (params.length >= 3) {
                    stmt.uniqueId = params[2];
                }
                
                if (stmt.type == NestedLogicType.call) {
                    // call指令格式：nestedlogic call uniqueId logicName "encodedCode"
                    // 解析logicName（玩家备注）和嵌套代码
                    if (params.length >= 4) {
                        // 寻找嵌套代码的位置（以引号开头的参数）
                        int codeIndex = -1;
                        for (int i = 3; i < params.length; i++) {
                            if (params[i].startsWith("\"")) {
                                codeIndex = i;
                                break;
                            }
                        }
                        
                        if (codeIndex != -1) {
                            // 解析logicName（如果存在）
                            if (codeIndex > 3) {
                                // 从参数3到codeIndex-1都是logicName的组成部分（处理包含空格的情况）
                                StringBuilder logicNameBuilder = new StringBuilder();
                                for (int i = 3; i < codeIndex; i++) {
                                    if (i > 3) logicNameBuilder.append(" ");
                                    logicNameBuilder.append(params[i]);
                                }
                                stmt.p1 = logicNameBuilder.toString();
                            } else {
                                // logicName为空
                                stmt.p1 = "";
                            }
                            
                            // 解析嵌套代码
                            String rawCode = params[codeIndex];
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
                            // 没有找到嵌套代码，设置默认值
                            stmt.p1 = params.length > 3 ? params[3] : "";
                            stmt.nestedCode = "";
                        }
                    }
                } else {
                    // push/pop指令：nestedlogic type uniqueId p1 p2 p3
                    // 解析第一个参数
                    if (params.length >= 4) {
                        stmt.p1 = params[3];
                    }
                    // 解析第二个参数
                    if (params.length >= 5) {
                        stmt.p2 = params[4];
                    }
                    // 解析第三个参数（stackName）
                    if (params.length >= 6) {
                        stmt.p3 = params[5];
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
