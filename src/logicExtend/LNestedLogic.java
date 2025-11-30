package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.LogicIO;
import mindustry.logic.LAssembler;
import mindustry.logic.LCategory;
import mindustry.logic.LExecutor;
import mindustry.logic.LStatement;

public class LNestedLogic {

    public static class LNestedLogicStatement extends LStatement {
        // 存储嵌套的逻辑代码
        public String nestedCode = "";

        @Override
        public void build(Table table) {
            table.add("NestedLogic");
            // 添加编辑按钮，使用与游戏内置按钮相似的样式
            table.button("Edit", mindustry.ui.Styles.logicTogglet, () -> {
                // 为嵌套逻辑编辑创建一个新的LogicDialog实例
                // 这样当关闭嵌套逻辑编辑页面时，只会关闭这个新的对话框，而不会影响主逻辑编辑器
                mindustry.logic.LogicDialog nestedDialog = new mindustry.logic.LogicDialog();
                // 显示编辑器，传入当前代码和回调函数
                nestedDialog.show(nestedCode, null, false, modifiedCode -> {
                    // 保存修改后的代码
                    nestedCode = modifiedCode;
                });
            }).size(80f, 40f).padLeft(10f);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            // 编译嵌套的逻辑指令
            // 直接使用LParser解析嵌套代码，创建新的LStatement对象
            // 确保不影响主层级LStatement对象的elem属性
            try {
                // 创建新的LParser实例解析嵌套代码
                mindustry.logic.LParser parser = new mindustry.logic.LParser(nestedCode, false);
                Seq<LStatement> nestedStatements = parser.parse();
                // 使用builder编译嵌套指令
                LExecutor.LInstruction[] nestedInstructions = nestedStatements.map(l -> l.build(builder)).retainAll(l -> l != null).toArray(LExecutor.LInstruction.class);
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
                    // 正确处理多层嵌套的代码
                    // 移除引号并处理转义字符
                    String rawCode = params[1];
                    if (rawCode.startsWith("\"") && rawCode.endsWith("\"")) {
                        // 移除外层引号
                        String innerCode = rawCode.substring(1, rawCode.length() - 1);
                        // 正确处理多层嵌套的转义字符
                        stmt.nestedCode = innerCode
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
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
            // 写入嵌套代码，正确处理多层嵌套的特殊字符
            builder.append('"');
            // 处理嵌套代码中的特殊字符
            for (char c : nestedCode.toCharArray()) {
                switch (c) {
                    case '"':
                        builder.append("\\\"");
                        break;
                    case '\n':
                        builder.append("\\n");
                        break;
                    case '\\':
                        builder.append("\\\\");
                        break;
                    default:
                        builder.append(c);
                        break;
                }
            }
            builder.append('"');
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
