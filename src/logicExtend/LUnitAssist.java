package logicExtend;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.ai.types.LogicAI;
import mindustry.ai.types.BuilderAI;

import static mindustry.logic.LCanvas.tooltip;

//单位协助指令实现类
public class LUnitAssist {
    
    /** 单位协助指令类 */
    public static class UnitAssistStatement extends LStatement {
        /** 协助者变量名 */
        public String assisterVar = "assister";
        /** 目标单位变量名 */
        public String targetVar = "target";

        /** 构建指令的UI界面 */
        @Override
        public void build(Table table) {
            table.clearChildren();
            table.left();
            
            // 第一排：assisterVar和targetVar参数
            table.table(t -> {
                t.setColor(table.color);
                
                // 显示assisterVar参数
                t.add(" assisterVar ").left().self(c -> tooltip(c, "unitassist.assistervar"));
                field(t, assisterVar, str -> assisterVar = str);
                
                // 显示targetVar参数
                t.add(" targetVar ").left().self(c -> tooltip(c, "unitassist.targetvar"));
                field(t, targetVar, str -> targetVar = str);
            }).left();
        }

        /** 构建指令的执行实例 */
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            return new UnitAssistI(builder.var(assisterVar), builder.var(targetVar));
        }

        /** 指定指令在逻辑编辑器中的分类 */
        @Override
        public LCategory category() {
            return LCategory.unit; // 指令归类为单位操作类别
        }

        /** 序列化指令到字符串 */
        @Override
        public void write(StringBuilder builder) {
            builder.append("unitAssist ").append(assisterVar).append(" ").append(targetVar);
        }
    }
    
    /** 单位协助指令执行器类 */
    public static class UnitAssistI implements LExecutor.LInstruction {
        /** 协助者变量 */
        public LVar assisterVar;
        /** 目标单位变量 */
        public LVar targetVar;

        /** 构造函数，指定协助者变量和目标单位变量 */
        public UnitAssistI(LVar assisterVar, LVar targetVar) {
            this.assisterVar = assisterVar;
            this.targetVar = targetVar;
        }

        /** 空构造函数 */
        public UnitAssistI() {
        }

        /** 执行指令的核心逻辑 */
        @Override
        public void run(LExecutor exec) {
            // 获取协助者和目标单位
            Object assisterObj = assisterVar.isobj ? assisterVar.obj() : null;
            Object targetObj = targetVar.isobj ? targetVar.obj() : null;
            
            // 检查参数类型
            if (!(assisterObj instanceof Unit assister) || !(targetObj instanceof Unit target)) {
                return;
            }
            
            // 检查单位是否有效
            if (!assister.isValid() || !target.isValid()) {
                return;
            }
            
            // 检查单位是否属于同一队伍
            if (assister.team() != target.team()) {
                return;
            }
            
            // 设置协助逻辑
            if (assister.controller() instanceof LogicAI) {
                // 创建BuilderAI控制器，设置为只协助模式
                BuilderAI builderAI = new BuilderAI();
                builderAI.onlyAssist = false; // 设置为false，允许主动协助建造
                
                // 设置协助目标
                builderAI.assistFollowing = target;
                
                // 将单位的控制器切换为BuilderAI
                assister.controller(builderAI);
            }
        }
    }
    
    /** 注册自定义逻辑指令 */
    public static void create() {
        // 注册unitAssist指令解析器
        LAssembler.customParsers.put("unitAssist", params -> {
            UnitAssistStatement stmt = new UnitAssistStatement();
            if (params.length >= 2) stmt.assisterVar = params[1];
            if (params.length >= 3) stmt.targetVar = params[2];
            stmt.afterRead();
            return stmt;
        });
        // 将指令添加到逻辑IO的所有语句列表中，使其在逻辑编辑器中可用
        LogicIO.allStatements.add(UnitAssistStatement::new);
    }
}
