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
}
