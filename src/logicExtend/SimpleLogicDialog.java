package logicExtend;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.func.*;
import arc.*;
import arc.graphics.*;
import mindustry.ui.dialogs.*;
import mindustry.ui.*;
import mindustry.logic.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import arc.util.Log;
import java.lang.reflect.Field;
import static mindustry.Vars.*;

public class SimpleLogicDialog extends BaseDialog {
    public LCanvas canvas;
    Cons<String> consumer = s -> {};
    boolean privileged;
    
    public SimpleLogicDialog() {
        super("logic");
        
        clearChildren();
        
        // 创建LCanvas实例
        canvas = new LCanvas();
        
        shouldPause = true;
        
        addCloseListener();
        
        shown(() -> {
            canvas.rebuild();
        });
        
        hidden(() -> consumer.get(canvas.save()));
        
        add(canvas).grow().name("canvas");
        
        row();
        
        // 添加基本的编辑按钮
        Table buttons = new Table();
        buttons.defaults().size(160f, 64f);
        buttons.button("@back", Icon.left, this::hide).name("back");
        buttons.button("@add", Icon.add, this::showAddDialog).name("add");
        add(buttons).growX().name("buttons");
        
        Core.app.post(canvas::rebuild);
    }
    
    public void showAddDialog() {
        BaseDialog dialog = new BaseDialog("@add");
        dialog.cont.table(table -> {
            String[] searchText = {""};
            Prov[] matched = {null};
            Runnable[] rebuild = {() -> {}};
            
            table.background(Tex.button);
            
            table.table(s -> {
                s.image(Icon.zoom).padRight(8);
                var search = s.field(null, text -> {
                    searchText[0] = text;
                    rebuild[0].run();
                }).growX().get();
                search.setMessageText("@players.search");
            }).growX().padBottom(4).row();
            
            table.pane(t -> {
                rebuild[0] = () -> {
                    t.clear();
                    
                    var text = searchText[0].toLowerCase();
                    
                    matched[0] = null;
                    
                    for(Prov<LStatement> prov : LogicIO.allStatements) {
                        LStatement example = prov.get();
                        if(example instanceof LStatements.InvalidStatement || example.hidden() || (example.privileged() && !privileged) || (example.nonPrivileged() && privileged)) continue;
                        
                        if(matched[0] == null) {
                            matched[0] = prov;
                        }
                        
                        LCategory category = example.category();
                        Table cat = t.find(category.name);
                        if(cat == null) {
                            t.table(s -> {
                                if(category.icon != null) {
                                    s.image(category.icon, Pal.darkishGray).left().size(15f).padRight(10f);
                                }
                                s.add(category.localized()).color(Pal.darkishGray).left();
                                s.image(Tex.whiteui, Pal.darkishGray).left().height(5f).growX().padLeft(10f);
                            }).growX().pad(5f).padTop(10f);
                            
                            t.row();
                            
                            cat = t.table(c -> {
                                c.top().left();
                            }).name(category.name).top().left().growX().fillY().get();
                            t.row();
                        }
                        
                        cat.button(example.name(), Styles.flatt, () -> {
                            canvas.add(prov.get());
                            dialog.hide();
                        }).size(130f, 50f).top().left();
                        
                        if(cat.getChildren().size % 3 == 0) cat.row();
                    }
                };
                
                rebuild[0].run();
            }).grow();
        }).fill().maxHeight(Core.graphics.getHeight() * 0.8f);
        dialog.addCloseButton();
        dialog.show();
    }
    
    public void show(String code, LExecutor executor, boolean privileged, Cons<String> modified) {
        this.privileged = privileged;
        canvas.statements.clearChildren();
        canvas.rebuild();
        
        // 使用反射设置privileged字段
        try {
            Field privilegedField = LCanvas.class.getDeclaredField("privileged");
            privilegedField.setAccessible(true);
            privilegedField.setBoolean(canvas, privileged);
        } catch(Exception e) {
            Log.err(e);
        }
        
        try {
            // 使用反射获取并保存当前静态canvas
            Field canvasField = LCanvas.class.getDeclaredField("canvas");
            canvasField.setAccessible(true);
            Object oldCanvas = canvasField.get(null);
            
            try {
                // 设置静态canvas为当前实例，确保加载功能正常
                canvasField.set(null, canvas);
                canvas.load(code);
            } finally {
                // 恢复原静态canvas
                canvasField.set(null, oldCanvas);
            }
        } catch(Throwable t) {
            Log.err(t);
            try {
                // 使用反射获取并保存当前静态canvas
                Field canvasField = LCanvas.class.getDeclaredField("canvas");
                canvasField.setAccessible(true);
                Object oldCanvas = canvasField.get(null);
                
                try {
                    // 设置静态canvas为当前实例，确保加载功能正常
                    canvasField.set(null, canvas);
                    canvas.load("");
                } finally {
                    // 恢复原静态canvas
                    canvasField.set(null, oldCanvas);
                }
            } catch(Exception e) {
                Log.err(e);
            }
        }
        this.consumer = result -> {
            if(!result.equals(code)) {
                modified.get(result);
            }
        };
        
        show();
    }
}