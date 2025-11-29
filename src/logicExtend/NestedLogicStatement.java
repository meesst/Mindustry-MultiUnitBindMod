package logicExtend;

import arc.struct.*;
import mindustry.logic.*;
import mindustry.logic.LCanvas.*;
import mindustry.logic.LExecutor.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.graphics.*;
import mindustry.gen.*;
import mindustry.gen.Icon;
import arc.func.Prov;
import arc.input.KeyCode;
import static arc.Core.*;

public class NestedLogicStatement extends LStatement {
    // 存储嵌套的逻辑语句
    public Seq<LStatement> nestedStatements = new Seq<>();
    
    @Override
    public void build(Table table) {
        // 添加标题
        table.add("嵌套逻辑").left().self(this::param);
        
        // 添加编辑按钮
        table.button(b -> {
            b.image(Icon.pencilSmall);
            b.clicked(() -> {
                // 打开嵌套逻辑编辑对话框
                showNestedLogicDialog();
            });
        }, Styles.logict, () -> {}).size(40f).color(table.color).padLeft(2);
        
        // 添加嵌套逻辑的预览
        table.table(t -> {
            updatePreview(t);
        }).growX().padTop(5);
    }
    
    @Override
    public LInstruction build(LAssembler builder) {
        // 创建嵌套逻辑指令
        NestedLogicInstruction instruction = new NestedLogicInstruction();
        
        // 编译嵌套的逻辑语句
        for (LStatement stmt : nestedStatements) {
            instruction.instructions.add(stmt.build(builder));
        }
        
        return instruction;
    }
    
    @Override
    public void write(StringBuilder builder) {
        // 序列化嵌套的逻辑语句
        for (LStatement stmt : nestedStatements) {
            stmt.write(builder);
            builder.append('\n');
        }
    }
    
    @Override
    public LCategory category() {
        return LCategoryExt.function;
    }
    
    // 更新预览
    private void updatePreview(Table table) {
        table.clear();
        table.left();
        
        if (nestedStatements.isEmpty()) {
            table.add("无嵌套逻辑").color(Color.gray);
        } else {
            table.add("嵌套逻辑语句数量: " + nestedStatements.size);
        }
    }
    
    // 显示嵌套逻辑编辑对话框
    private void showNestedLogicDialog() {
        // 创建对话框
        BaseDialog dialog = new BaseDialog("嵌套逻辑编辑");
        
        // 创建LCanvas
        LCanvas canvas = new LCanvas();
        
        // 设置嵌套的逻辑语句：从nestedStatements到canvas
        if (!nestedStatements.isEmpty()) {
            // 构建临时语句列表
            Seq<LStatement> tempStatements = new Seq<>();
            tempStatements.addAll(nestedStatements);
            // 保存为字符串，直接调用静态方法
            String asm = LAssembler.write(tempStatements);
            // 加载到canvas
            canvas.load(asm);
        }
        
        // 添加LCanvas到对话框
        dialog.cont.pane(canvas).size(800f, 600f);
        
        // 添加添加指令按钮
        dialog.buttons.button("添加指令", Icon.add, () -> {
            showAddDialog(canvas, dialog);
        }).size(150f, 50f);
        
        // 添加保存按钮
        dialog.buttons.button("保存", () -> {
            // 保存嵌套的逻辑语句：从canvas到nestedStatements
            String asm = canvas.save();
            if (asm != null && !asm.isEmpty()) {
                // 加载语句，直接调用静态方法，privileged设为false
                Seq<LStatement> loadedStatements = LAssembler.read(asm, false);
                // 更新嵌套语句
                nestedStatements.clear();
                nestedStatements.addAll(loadedStatements);
            } else {
                // 如果没有内容，清空嵌套语句
                nestedStatements.clear();
            }
            dialog.hide();
        }).size(150f, 50f);
        
        // 添加取消按钮
        dialog.buttons.button("取消", dialog::hide).size(150f, 50f);
        
        // 显示对话框
        dialog.show();
    }
    
    // 显示添加指令对话框
    private void showAddDialog(LCanvas canvas, BaseDialog parentDialog) {
        BaseDialog dialog = new BaseDialog("添加指令");
        dialog.cont.table(table -> {
            String[] searchText = {""};
            Prov[] matched = {null};
            Runnable[] rebuild = {() -> {}};
            
            table.background(Tex.button);
            
            // 搜索框
            table.table(s -> {
                s.image(Icon.zoom).padRight(8);
                var search = s.field(null, text -> {
                    searchText[0] = text;
                    rebuild[0].run();
                }).growX().get();
                search.setMessageText("搜索指令");
                
                // 回车键自动添加第一个匹配项
                search.keyDown(KeyCode.enter, () -> {
                    if(!searchText[0].isEmpty() && matched[0] != null){
                        canvas.add((LStatement)matched[0].get());
                        dialog.hide();
                    }
                });
            }).growX().padBottom(4).row();
            
            // 指令列表
            table.pane(t -> {
                rebuild[0] = () -> {
                    t.clear();
                    
                    var text = searchText[0].toLowerCase();
                    
                    matched[0] = null;
                    
                    // 遍历所有可用的语句
                    for(Prov<LStatement> prov : LogicIO.allStatements){
                        LStatement example = prov.get();
                        // 过滤条件：不是无效语句、没有隐藏、权限匹配
                        if(example instanceof LStatements.InvalidStatement || example.hidden()) continue;
                        
                        // 搜索匹配
                        if(!text.isEmpty() && !example.name().toLowerCase().contains(text) && !example.typeName().toLowerCase().contains(text)) continue;
                        
                        if(matched[0] == null) matched[0] = prov;
                        
                        // 添加指令按钮
                        t.button(example.name(), Styles.logicTogglet, () -> {
                            canvas.add(prov.get());
                            dialog.hide();
                        }).size(130f, 50f).top().left();
                        
                        // 每行显示3个按钮
                        if(t.getChildren().size % 3 == 0) t.row();
                    }
                };
                
                rebuild[0].run();
            }).grow();
        }).fill().maxHeight(Core.graphics.getHeight() * 0.8f);
        
        dialog.addCloseButton();
        dialog.show();
    }
    
    // 注册语句
    public static void create() {
        // 注册语句解析器
        LAssembler.customParsers.put("nestedLogic", params -> {
            NestedLogicStatement stmt = new NestedLogicStatement();
            // 解析参数
            return stmt;
        });
        
        // 添加到所有语句列表，使其在游戏界面显示
        LogicIO.allStatements.add(NestedLogicStatement::new);
    }
    
    // 嵌套逻辑指令
    public static class NestedLogicInstruction implements LInstruction {
        public Seq<LInstruction> instructions = new Seq<>();
        
        @Override
        public void run(LExecutor exec) {
            // 依次执行所有嵌套的指令
            for (LInstruction instruction : instructions) {
                instruction.run(exec);
            }
        }
    }
}