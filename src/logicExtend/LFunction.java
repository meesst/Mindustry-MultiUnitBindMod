package logicExtend;

import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import mindustry.gen.LogicIO;
import mindustry.logic.LAssembler;
import mindustry.logic.LCategory;
import mindustry.logic.LExecutor;
import mindustry.logic.LStatement;

public class LFunction {
    public ObjectMap<String, Integer> map = ObjectMap.of();

    public static class LFunctionStatement extends LStatement {
        public String functionName = "\"function\"";

        @Override
        public void build(Table table) {
            table.add("Function(");
            LEExtend.field(table, functionName, str -> functionName = str, 330f);
            table.add(")");
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return null;
        }

        @Override
        public LCategory category() {
            return LCategoryExt.function;
        }

        /** Anuken, if you see this, you can replace it with your own @RegisterStatement, because this is my last resort... **/
        public static void create() {
            LAssembler.customParsers.put("function", params -> {
                LFunctionStatement stmt = new LFunctionStatement();
                if (params.length >= 2) stmt.functionName = params[1];
                stmt.afterRead();
                return stmt;
            });
            LogicIO.allStatements.add(LFunctionStatement::new);
        }

        @Override
        public void write(StringBuilder builder) {
            builder.append("function ").append(functionName);
        }
    }

    public static class LFunctionReturnStatement extends LStatement {

        @Override
        public void build(Table table) {

        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return null;
        }

        @Override
        public LCategory category() {
            return LCategoryExt.function;
        }

        @Override
        public void write(StringBuilder builder) {
            builder.append("return ");
        }
    }

    public static class LFunctionInvokeStatement extends LStatement {
        public String func = "function";

        @Override
        public void build(Table table) {
            table.add("function");
            LEExtend.field(table, func, str -> func = str, 550f);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return null;
        }

        @Override
        public LCategory category() {
            return LCategoryExt.function;
        }

        @Override
        public void write(StringBuilder builder) {
            builder.append("invokefunction ");
        }
    }

    public static class LFunctionI implements LExecutor.LInstruction {

        @Override
        public void run(LExecutor exec) {

        }
    }

    public static class LFunctionReturnI implements LExecutor.LInstruction {

        @Override
        public void run(LExecutor exec) {

        }
    }

    public static class LFunctionInvokeI implements LExecutor.LInstruction {

        @Override
        public void run(LExecutor exec) {

        }
    }

    @RegisterStatement("nested")
    public static class LNestedLogicStatement extends LStatement {
        public String nestedCode = "";
        public String name = "nested";

        @Override
        public void build(arc.scene.ui.layout.Table table) {
            table.add("Nested Logic(");
            LEExtend.field(table, name, str -> name = str, 150f);
            table.add(")");
            
            table.button(arc.scene.ui.Icon.edit, arc.scene.ui.Styles.logici, () -> {
                mindustry.logic.LogicDialog dialog = new mindustry.logic.LogicDialog();
                dialog.show(nestedCode, null, false, result -> {
                    nestedCode = result;
                });
            }).size(40f).padLeft(8);
        }

        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new LNestedLogicI(nestedCode);
        }

        @Override
        public LCategory category() {
            return LCategoryExt.function;
        }

        @Override
        public void write(java.lang.StringBuilder builder) {
            builder.append("nested ").append(name).append(" ").append('"').append(nestedCode.replace("\"", "\\\"").replace("\n", "\\n")).append('"');
        }

        public static void create() {
            LAssembler.customParsers.put("nested", params -> {
                LNestedLogicStatement stmt = new LNestedLogicStatement();
                if (params.length >= 2) stmt.name = params[1];
                if (params.length >= 3) {
                    stmt.nestedCode = params[2].replace("\\\"", "\"").replace("\\n", "\n");
                }
                stmt.afterRead();
                return stmt;
            });
            mindustry.gen.LogicIO.allStatements.add(LNestedLogicStatement::new);
        }
    }

    public static class LNestedLogicI implements LExecutor.LInstruction {
        private String nestedCode;
        private LExecutor.LInstruction[] nestedInstructions;

        public LNestedLogicI(String nestedCode) {
            this.nestedCode = nestedCode;
            this.nestedInstructions = compileNestedCode(nestedCode);
        }

        private LExecutor.LInstruction[] compileNestedCode(String code) {
            try {
                LAssembler assembler = new LAssembler();
                assembler.assemble(code, false);
                return assembler.instructions.toArray(LExecutor.LInstruction.class);
            } catch (Exception e) {
                return new LExecutor.LInstruction[0];
            }
        }

        @Override
        public void run(LExecutor exec) {
            for (LExecutor.LInstruction instruction : nestedInstructions) {
                instruction.run(exec);
            }
        }
    }
}
