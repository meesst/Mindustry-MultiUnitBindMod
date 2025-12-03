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
            // 3. 复制主builder的关键变量
            // 4. 编译嵌套语句为指令
            try {
                // 使用LAssembler.read()解析嵌套代码
                Seq<LStatement> nestedStatements = LAssembler.read(nestedCode, false);
                
                // 清除嵌套逻辑的LStatement对象的elem属性，避免影响主层级的checkHovered()方法
                nestedStatements.each(l -> l.elem = null);
                
                // 创建新的LAssembler实例，用于编译嵌套指令
                LAssembler nestedBuilder = new LAssembler();
                
                // 设置privileged状态
                nestedBuilder.privileged = builder.privileged;
                
                // 编译嵌套语句为指令
                LExecutor.LInstruction[] nestedInstructions = nestedStatements.map(l -> {
                    // 编译指令
                    return l.build(nestedBuilder);
                }).retainAll(l -> l != null).toArray(LExecutor.LInstruction.class);
                
                // 返回嵌套逻辑指令，不包含startIndex
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
            if (!executed) {
                // 第一次执行：初始化
                
                // 1. 保存主逻辑返回地址
                returnAddress = (int) exec.counter.numval;
                
                // 2. 创建嵌套LExecutor实例
                nestedExecutor = new LExecutor();
                
                // 3. 设置嵌套LExecutor的指令序列
                nestedExecutor.instructions = instructions;
                
                // 4. 共享变量：直接使用主exec的vars和nameMap
                nestedExecutor.vars = exec.vars;
                nestedExecutor.nameMap = exec.nameMap;
                
                // 5. 初始化嵌套LExecutor的内置变量
                nestedExecutor.counter = new LVar("@counter");
                nestedExecutor.counter.isobj = false;
                nestedExecutor.counter.numval = 0;
                
                nestedExecutor.unit = exec.unit;
                nestedExecutor.thisv = exec.thisv;
                nestedExecutor.ipt = exec.ipt;
                
                nestedExecutor.privileged = exec.privileged;
                nestedExecutor.build = exec.build;
                nestedExecutor.team = exec.team;
                nestedExecutor.links = exec.links;
                nestedExecutor.linkIds = exec.linkIds;
                
                nestedExecutor.graphicsBuffer = exec.graphicsBuffer;
                nestedExecutor.textBuffer = exec.textBuffer;
                
                // 6. 执行嵌套逻辑的第一条指令
                nestedExecutor.runOnce();
                
                // 7. 标记为已开始执行
                executed = true;
                
                // 8. 暂停主逻辑，下一帧继续执行此指令
                exec.counter.numval = returnAddress - 1;
            } else {
                // 后续执行：继续执行嵌套逻辑
                
                // 1. 检查指令计数限制
                if (nestedExecutor.counter.numval >= LExecutor.maxInstructions) {
                    // 嵌套逻辑执行超时，返回主逻辑
                    exec.counter.numval = returnAddress;
                    return;
                }
                
                // 2. 检查嵌套逻辑是否执行完毕
                if ((int) nestedExecutor.counter.numval < nestedExecutor.instructions.length) {
                    // 继续执行嵌套逻辑
                    nestedExecutor.runOnce();
                    
                    // 暂停主逻辑，下一帧继续执行此指令
                    exec.counter.numval = returnAddress - 1;
                } else {
                    // 嵌套逻辑执行完毕，恢复主逻辑执行
                    exec.counter.numval = returnAddress;
                }
            }
        }
    }
}
