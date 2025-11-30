package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.LogicIO;
import mindustry.logic.LAssembler;
import mindustry.logic.LCategory;
import mindustry.logic.LExecutor;
import mindustry.logic.LStatement;
import java.util.Base64;

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
            // 使用当前builder编译，共享变量作用域
            try {
                // 解析嵌套代码为LStatement序列
                Seq<LStatement> nestedStatements = LAssembler.read(nestedCode, false);
                // 使用当前builder构建所有嵌套语句，共享同一个变量作用域
                Seq<LExecutor.LInstruction> nestedInstructions = new Seq<>();
                for (LStatement stmt : nestedStatements) {
                    LExecutor.LInstruction instruction = stmt.build(builder);
                    if (instruction != null) {
                        nestedInstructions.add(instruction);
                    }
                }
                return new LNestedLogicInstruction(nestedInstructions.toArray(LExecutor.LInstruction.class));
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
                    // 改进反序列化逻辑，使用递归方式解析多层嵌套
                    stmt.nestedCode = parseNestedCode(params[1]);
                }
                stmt.afterRead();
                return stmt;
            });
            LogicIO.allStatements.add(LNestedLogicStatement::new);
        }

        private static String parseNestedCode(String rawCode) {
            if (rawCode.startsWith("\"") && rawCode.endsWith("\"")) {
                try {
                    // 移除外层引号
                    String encoded = rawCode.substring(1, rawCode.length() - 1);
                    // 使用Base64解码嵌套代码
                    byte[] decodedBytes = Base64.getDecoder().decode(encoded);
                    return new String(decodedBytes);
                } catch (IllegalArgumentException e) {
                    // 如果解码失败，返回空字符串或原始字符串
                    return "";
                }
            } else {
                return rawCode;
            }
        }

        @Override
        public void write(StringBuilder builder) {
            // 序列化嵌套逻辑指令
            builder.append("nestedlogic ");
            // 使用递归方式处理嵌套代码
            writeNestedCode(builder, nestedCode);
        }

        private void writeNestedCode(StringBuilder builder, String code) {
            // 使用Base64编码嵌套代码，避免转义字符问题
            String encoded = Base64.getEncoder().encodeToString(code.getBytes());
            builder.append('"').append(encoded).append('"');
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
