package logicExtend;

import arc.struct.Seq;
import arc.scene.ui.layout.Table;
import arc.util.Timer;
import mindustry.gen.Building;
import mindustry.gen.LogicIO;
import mindustry.logic.*;
import mindustry.ui.Styles;

import static arc.Core.*;
import static mindustry.logic.LCanvas.tooltip;
import static mindustry.Vars.*;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class LNestedLogic {
    
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
    
    /** 用于管理栈操作的锁 */
    private static final Object stackLock = new Object();
    
    /** 定时器，用于自动回收栈元素 */
    private static Timer.Task cleanupTask;
    
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
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        cleanupTask = Timer.schedule(() -> cleanupStacks(), checkInterval, checkInterval);
    }
    
    /** 清理超时的栈元素和空栈 */
    private static void cleanupStacks() {
        if (stacks.isEmpty()) return;
        
        long currentTime = System.currentTimeMillis();
        int timeout = settings.getInt("lnestedlogic-element-timeout", elementTimeout);
        long timeoutMs = timeout * 1000L;
        
        Seq<String> emptyStacks = new Seq<>();
        for (arc.struct.ObjectMap.Entry<String, Seq<CallStackElement>> entry : stacks) {
            String stackName = entry.key;
            Seq<CallStackElement> stack = entry.value;
            
            if (stack.isEmpty()) {
                emptyStacks.add(stackName);
                continue;
            }
            
            stack.removeAll(elem -> {
                boolean timeout = currentTime - elem.lastPushTime > timeoutMs;
                return timeout;
            });
            
            if (stack.isEmpty()) {
                emptyStacks.add(stackName);
            }
        }
        
        for (String stackName : emptyStacks) {
            stacks.remove(stackName);
        }
    }
    
    /** 获取指定名称的栈，如果不存在则创建 */
    public static Seq<CallStackElement> getStack(String stackName) {
        if (stackName == null || stackName.isEmpty()) {
            stackName = "default";
        }
        if (!stacks.containsKey(stackName)) {
            stacks.put(stackName, new Seq<>());
        }
        return stacks.get(stackName);
    }
    
    /** 日志记录方法 */
    public static void log(String message) {
        if (settings.getBool("lnestedlogic-debug-log")) {
            // 使用游戏内置日志系统
            Log.infoTag("NestedLogic", message);
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
                    super.showSelect(b, NestedLogicType.values(), type, t -> {
                        type = t;
                        saveUI();
                        table.parent.invalidateHierarchy();
                        table.clearChildren();
                        build(table);
                    }, 2, cell -> cell.size(120, 50));
                });
            }, Styles.logict, () -> {}).size(120, 40).color(table.color).left().padLeft(2);
            
            // 根据当前选项动态创建UI元素
            if (type == NestedLogicType.push) {
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f);
                
                fields(table, "Index", p2, str -> {
                    try {
                        Integer.parseInt(str);
                        p2 = str;
                    } catch (NumberFormatException ignored) {}
                    saveUI();
                }).size(80f, 40f).pad(2f);
                
                fields(table, "Stack Name", p3, str -> {
                    p3 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f);
            } else if (type == NestedLogicType.call) {
                fields(table, "Logic Name", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(120f, 40f).pad(2f);
                
                table.button(b -> {
                    b.label(() -> "Edit Logic");
                    b.clicked(() -> {
                        log("call: Opening nested logic editor");
                        
                        // 简化的canvas处理
                        mindustry.logic.LCanvas originalCanvas = null;
                        java.lang.reflect.Field canvasField = null;
                        boolean reflectionSuccess = false;
                        
                        try {
                            Class<?> lCanvasClass = mindustry.logic.LCanvas.class;
                            canvasField = lCanvasClass.getDeclaredField("canvas");
                            canvasField.setAccessible(true);
                            originalCanvas = (mindustry.logic.LCanvas) canvasField.get(null);
                            reflectionSuccess = true;
                        } catch (Exception e) {
                            log("call: Failed to get canvas field: " + e.getMessage());
                        }
                        
                        // 恢复canvas的Runnable
                        Runnable restoreCanvas = () -> {
                            if (reflectionSuccess && originalCanvas != null) {
                                try {
                                    canvasField.set(null, originalCanvas);
                                    originalCanvas.rebuild();
                                } catch (Exception e) {
                                    log("call: Failed to restore canvas: " + e.getMessage());
                                }
                            }
                        };
                        
                        // 打开嵌套逻辑编辑器
                        mindustry.logic.LogicDialog nestedDialog = new mindustry.logic.LogicDialog();
                        nestedDialog.hidden(restoreCanvas);
                        nestedDialog.show(nestedCode, null, false, modifiedCode -> {
                            nestedCode = modifiedCode;
                            saveUI();
                        });
                    });
                }, Styles.logict, () -> {}).size(120f, 40f).color(table.color).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.editlogic", "Edit Logic")));
            } else if (type == NestedLogicType.pop) {
                fields(table, "Variable", p1, str -> {
                    p1 = str;
                    saveUI();
                }).size(80f, 40f).pad(2f)
                .self(elem -> tooltip(elem, bundle.get("lnestedlogic.variable", "Variable")));
                
                fields(table, "Index", p2, str -> {
                    try {
                        Integer.parseInt(str);
                        p2 = str;
                    } catch (NumberFormatException ignored) {}
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
        public LExecutor.LInstruction build(LAssembler builder) {
            switch (type) {
                case push:
                    if (!p1.isEmpty()) builder.var(p1);
                    if (!p2.isEmpty()) builder.var(p2);
                    if (!p3.isEmpty()) builder.var(p3);
                    
                    return (exec) -> {
                        Object pushValue;
                        boolean isVariable = false;
                        
                        LVar var = exec.optionalVar(p1);
                        if (var != null) {
                            isVariable = true;
                            pushValue = var.isobj ? var.objval : var.numval;
                        } else {
                            try {
                                pushValue = Double.parseDouble(p1);
                            } catch (NumberFormatException e) {
                                pushValue = p1.startsWith("\"") && p1.endsWith("\"") 
                                    ? p1.substring(1, p1.length() - 1) : p1;
                            }
                        }
                        
                        int index = 0;
                        if (!p2.isEmpty()) {
                            LVar indexVar = exec.optionalVar(p2);
                            if (indexVar != null) {
                                if (!indexVar.isobj) {
                                    index = (int) indexVar.numval;
                                } else if (indexVar.objval instanceof String) {
                                    try {
                                        index = Integer.parseInt((String) indexVar.objval);
                                    } catch (NumberFormatException ignored) {}
                                }
                            } else {
                                try {
                                    index = Integer.parseInt(p2);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        
                        String stackName = p3.isEmpty() ? "default" : p3;
                        LVar stackNameVar = exec.optionalVar(stackName);
                        if (stackNameVar != null) {
                            stackName = stackNameVar.isobj 
                                ? (stackNameVar.objval != null ? stackNameVar.objval.toString() : "default")
                                : String.valueOf(stackNameVar.numval);
                        }
                        
                        String encodedStackName = Base64.getEncoder().encodeToString(stackName.getBytes(StandardCharsets.UTF_8));
                        
                        synchronized(stackLock) {
                            Seq<CallStackElement> currentStack = getStack(encodedStackName);
                            
                            CallStackElement existing = currentStack.find(e -> e.index == index);
                            if (existing != null) {
                                existing.varName = p1;
                                existing.varValue = pushValue;
                                existing.stackName = encodedStackName;
                                existing.lastPushTime = System.currentTimeMillis();
                            } else {
                                CallStackElement elem = new CallStackElement();
                                elem.varName = p1;
                                elem.varValue = pushValue;
                                elem.index = index;
                                elem.stackName = encodedStackName;
                                elem.lastPushTime = System.currentTimeMillis();
                                currentStack.add(elem);
                            }
                        }
                    };
                    
                case call:
                    return (exec) -> {
                        // 简化的嵌套深度限制
                        ThreadLocal<Integer> nestedDepth = ThreadLocal.withInitial(() -> 0);
                        if (nestedDepth.get() >= 5) return;
                        
                        try {
                            nestedDepth.set(nestedDepth.get() + 1);
                            
                            LExecutor nestedExec;
                            
                            if (cachedNestedExec == null) {
                                LAssembler nestedBuilder = LAssembler.assemble(nestedCode, false);
                                
                                if (exec.build != null) {
                                    for (var link : exec.build.links) {
                                        if (link.valid) {
                                            Building building = world.build(link.x, link.y);
                                            if (building != null) {
                                                nestedBuilder.putConst(link.name, building);
                                            }
                                        }
                                    }
                                }
                                
                                nestedExec = new LExecutor();
                                nestedExec.build = exec.build;
                                nestedExec.team = exec.team;
                                nestedExec.privileged = exec.privileged;
                                nestedExec.links = exec.links;
                                nestedExec.linkIds = exec.linkIds;
                                
                                nestedExec.load(nestedBuilder);
                                
                                cachedNestedBuilder = nestedBuilder;
                                cachedNestedExec = nestedExec;
                            } else {
                                nestedExec = cachedNestedExec;
                                nestedExec.build = exec.build;
                                nestedExec.team = exec.team;
                                nestedExec.privileged = exec.privileged;
                                nestedExec.links = exec.links;
                                nestedExec.linkIds = exec.linkIds;
                            }
                            
                            // 更新嵌套执行器的常量
                            if (nestedExec.build != null) {
                                LVar linksVar = nestedExec.optionalVar("@links");
                                if (linksVar != null) {
                                    linksVar.isobj = false;
                                    linksVar.numval = nestedExec.links.length;
                                }
                                
                                LVar iptVar = nestedExec.optionalVar("@ipt");
                                if (iptVar != null) {
                                    iptVar.isobj = false;
                                    iptVar.numval = nestedExec.build.ipt;
                                }
                            }
                            
                            // 复制动态变量
                            if (nestedExec.thisv != null && exec.thisv != null) {
                                nestedExec.thisv.set(exec.thisv);
                            }
                            if (nestedExec.unit != null && exec.unit != null) {
                                nestedExec.unit.set(exec.unit);
                            }
                            
                            // 重置计数器
                            if (nestedExec.counter != null) {
                                nestedExec.counter.numval = 0;
                            }
                            
                            // 执行嵌套指令
                            int nestedCounter = 0;
                            int nestedMaxInstructions = LExecutor.maxInstructions;
                            
                            while (nestedExec.counter.numval < nestedExec.instructions.length && nestedCounter < nestedMaxInstructions) {
                                nestedExec.runOnce();
                                nestedCounter++;
                            }
                        } finally {
                            nestedDepth.set(nestedDepth.get() - 1);
                        }
                    };
                    
                case pop:
                    if (!p1.isEmpty()) builder.var(p1);
                    if (!p2.isEmpty()) builder.var(p2);
                    if (!p3.isEmpty()) builder.var(p3);
                    
                    return (exec) -> {
                        String stackName = p3.isEmpty() ? "default" : p3;
                        LVar stackNameVar = exec.optionalVar(stackName);
                        if (stackNameVar != null) {
                            stackName = stackNameVar.isobj 
                                ? (stackNameVar.objval != null ? stackNameVar.objval.toString() : "default")
                                : String.valueOf(stackNameVar.numval);
                        }
                        
                        String encodedStackName = Base64.getEncoder().encodeToString(stackName.getBytes(StandardCharsets.UTF_8));
                        
                        int index = 0;
                        if (!p2.isEmpty()) {
                            LVar indexVar = exec.optionalVar(p2);
                            if (indexVar != null) {
                                if (!indexVar.isobj) {
                                    index = (int) indexVar.numval;
                                } else if (indexVar.objval instanceof String) {
                                    try {
                                        index = Integer.parseInt((String) indexVar.objval);
                                    } catch (NumberFormatException ignored) {}
                                }
                            } else {
                                try {
                                    index = Integer.parseInt(p2);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        
                        synchronized(stackLock) {
                            Seq<CallStackElement> currentStack = getStack(encodedStackName);
                            if (currentStack.isEmpty()) return;
                            
                            CallStackElement targetElem = currentStack.find(e -> e.index == index);
                            if (targetElem != null) {
                                LVar targetVar = exec.optionalVar(p1);
                                if (targetVar != null) {
                                    if (targetElem.varValue instanceof Double) {
                                        targetVar.isobj = false;
                                        targetVar.numval = (Double) targetElem.varValue;
                                    } else {
                                        targetVar.isobj = true;
                                        targetVar.objval = targetElem.varValue;
                                    }
                                }
                            }
                        }
                    };
                    
                default:
                    return (exec) -> {};
            }
        }

        @Override
        public LCategory category() {
            return LCategoryExt.function;
        }

        @Override
        public void write(StringBuilder builder) {
            builder.append("nestedlogic ").append(type.name()).append(" ");
            
            if (type == NestedLogicType.call) {
                builder.append(uniqueId).append(" ").append(p1).append(" ");
                String encoded = Base64.getEncoder().encodeToString(nestedCode.getBytes(StandardCharsets.UTF_8));
                builder.append('"').append(encoded).append('"');
            } else {
                builder.append(p1).append(" ").append(p2).append(" ").append(p3);
            }
        }

        @Override
        public void afterRead() {
            // 无需额外处理
        }
        
        // 更新嵌套代码时清除缓存
        public void setNestedCode(String nestedCode) {
            this.nestedCode = nestedCode;
            this.cachedNestedBuilder = null;
            this.cachedNestedExec = null;
        }
        
        public static void create() {
            LAssembler.customParsers.put("nestedlogic", params -> {
                LNestedLogicStatement stmt = new LNestedLogicStatement();
                
                // 处理旧格式兼容性
                if (params.length >= 2) {
                    try {
                        stmt.type = NestedLogicType.valueOf(params[1]);
                    } catch (IllegalArgumentException e) {
                        stmt.type = NestedLogicType.call;
                        if (params.length >= 3) {
                            stmt.nestedCode = params[2];
                        }
                        return stmt;
                    }
                }
                
                if (stmt.type == NestedLogicType.call) {
                    if (params.length >= 3) stmt.uniqueId = params[2];
                    
                    if (params.length >= 4) {
                        int codeIndex = -1;
                        for (int i = 3; i < params.length; i++) {
                            if (params[i].startsWith("\"")) {
                                codeIndex = i;
                                break;
                            }
                        }
                        
                        if (codeIndex != -1) {
                            if (codeIndex > 3) {
                                StringBuilder logicName = new StringBuilder();
                                for (int i = 3; i < codeIndex; i++) {
                                    if (i > 3) logicName.append(" ");
                                    logicName.append(params[i]);
                                }
                                stmt.p1 = logicName.toString();
                            }
                            
                            try {
                                String rawCode = params[codeIndex];
                                String encoded = rawCode.substring(1, rawCode.length() - 1);
                                byte[] decoded = Base64.getDecoder().decode(encoded);
                                stmt.nestedCode = new String(decoded, StandardCharsets.UTF_8);
                            } catch (Exception ignored) {
                                stmt.nestedCode = "";
                            }
                        } else {
                            stmt.p1 = params.length > 3 ? params[3] : "";
                            stmt.nestedCode = "";
                        }
                    }
                } else {
                    if (params.length >= 3) stmt.p1 = params[2];
                    if (params.length >= 4) stmt.p2 = params[3];
                    if (params.length >= 5) stmt.p3 = params[4];
                }
                
                stmt.afterRead();
                return stmt;
            });
            
            LogicIO.allStatements.add(LNestedLogicStatement::new);
        }
    }
}