package logicExtend;

import arc.func.Cons;
import arc.graphics.Color;
import arc.scene.Group;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.Bundle;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.*;
import mindustry.gen.Tex;

import static mindustry.Vars.*;

public class LUnitBindGroupUI {
    // 注册方法
    public static void register() {
        // 将在后面实现
    }
    
    // 单位绑定组指令类
    public static class UnitBindGroupStatement extends LStatement {
        // 参数定义
        public String type = "@poly";
        public String count = "1";
        public String group = "stand-alone";
        public String mode = "grabbing";
        public String unitVar = "currentUnit";
        public String indexVar = "unitIndex";
        
        // 悬浮提示辅助方法
        protected <T extends Element> T tooltip(T element, String text) {
            element.setTooltip(text);
            return element;
        }
        
        // 显示单位类型选择器
        protected void showUnitTypeSelector(String current, Cons<String> setter) {
            BaseDialog dialog = new BaseDialog(Bundle.get("ubindgroup.selectunit"));
            dialog.addCloseButton();
            
            // 创建单位类型选择器，按照知识库中的标准实现
            Table selectTable = new Table();
            ScrollPane pane = new ScrollPane(selectTable, Styles.smallPane);
            dialog.cont.add(pane).size(600f, 500f);
            
            // 先添加@poly选项
            Button polyButton = selectTable.button("@poly", Tex.pane, Styles.cleari, () -> {
                setter.get("@poly");
                dialog.hide();
            }).size(140f, 50f);
            if (current.equals("@poly")) {
                polyButton.color.set(Color.lightGray);
            }
            
            // 按照4列网格布局显示所有单位类型
            int cols = 4;
            int i = 1; // 从1开始，因为0位置已经放了@poly
            
            for (UnitType unitType : content.units()) {
                // 新行检查
                if (i % cols == 0) {
                    selectTable.row();
                }
                
                Button button = selectTable.button(unitType.localizedName, Tex.pane, Styles.cleari, () -> {
                    setter.get(unitType.name);
                    dialog.hide();
                }).size(140f, 50f).get();
                
                // 高亮当前选中的单位类型
                if (current.equals(unitType.name)) {
                    button.color.set(Color.lightGray);
                }
                
                i++;
            }
            
            dialog.show();
        }
        
        // 显示组管理窗口
        protected void showGroupManager() {
            BaseDialog dialog = new BaseDialog(Bundle.get("ubindgroup.groupmanager.title"));
            
            // 获取共享组列表（从LUnitBindGroup类中获取）
            ObjectMap<String, LUnitBindGroup.UnitGroupInfo> sharedGroups = LUnitBindGroup.getSharedGroups();
            
            // 创建组列表
            Table groupList = new Table();
            ScrollPane pane = new ScrollPane(groupList, Styles.smallPane);
            dialog.cont.add(pane).size(400f, 400f).row();
            
            // 更新列表的方法
            Runnable updateList = () -> {
                groupList.clear();
                
                // 添加默认组（不可删除）
                if (!mode.equals("access")) { // 只有在mode不是access时才显示stand-alone
                    Button standAloneButton = groupList.button("stand-alone", Tex.pane, Styles.cleari, () -> {
                        group = "stand-alone";
                        dialog.hide();
                    }).size(380f, 50f).row().get();
                    if (group.equals("stand-alone")) {
                        standAloneButton.color.set(Color.lightGray);
                    }
                }
                
                // 添加其他共享组
                for (String groupName : sharedGroups.keys()) {
                    groupList.table(t -> {
                        Button button = t.button(groupName, Tex.pane, Styles.cleari, () -> {
                            group = groupName;
                            dialog.hide();
                        }).size(300f, 50f).get();
                        if (group.equals(groupName)) {
                            button.color.set(Color.lightGray);
                        }
                        
                        // 添加删除按钮
                        t.button("X", Tex.redPane, Styles.cleari, () -> {
                            // 显示删除确认对话框
                            BaseDialog confirm = new BaseDialog(Bundle.get("ubindgroup.groupmanager.delete.confirm"));
                            confirm.cont.add(Bundle.format("ubindgroup.groupmanager.delete.message", groupName)).row();
                            confirm.buttons.button(Bundle.get("ubindgroup.groupmanager.delete.confirm.yes"), () -> {
                                LUnitBindGroup.deleteGroup(groupName);
                                updateList.run();
                                confirm.hide();
                            }).size(150f, 50f);
                            confirm.buttons.button(Bundle.get("ubindgroup.groupmanager.delete.confirm.no"), confirm::hide).size(150f, 50f);
                            confirm.show();
                        }).size(60f, 50f);
                    }).row();
                }
            };
            
            // 初始更新列表
            updateList.run();
            
            // 添加新组的输入框
            Table addGroupTable = new Table();
            TextField newGroupName = addGroupTable.field("", text -> {}).size(250f, 50f).get();
            newGroupName.setMessageText(Bundle.get("ubindgroup.groupmanager.add"));
            addGroupTable.button(Bundle.get("ubindgroup.groupmanager.addbutton"), () -> {
                if (!newGroupName.getText().trim().isEmpty() && 
                    !newGroupName.getText().trim().equals("stand-alone") &&
                    !sharedGroups.containsKey(newGroupName.getText().trim())) {
                    LUnitBindGroup.createNewGroup(newGroupName.getText().trim());
                    updateList.run();
                    newGroupName.setText("");
                }
            }).size(100f, 50f);
            
            dialog.cont.add(addGroupTable).row();
            dialog.buttons.button("Close", dialog::hide).size(150f, 50f);
            dialog.show();
        }
        
        @Override
        public void build(Table table) {
            // 第一行：type, count, mode
            table.table(row -> {
                // 只有在mode不是access时才显示type和count参数
                if (!mode.equals("access")) {
                    // type参数
                    row.add(tooltip(new Label("Type: "), Bundle.get("ubindgroup.param.unitType.tooltip")));
                    row.table(t -> {
                        Button typeButton = t.button(() -> type, Tex.pane, Styles.cleari, () -> 
                            showUnitTypeSelector(type, value -> type = value)
                        ).size(160f, 40f).get();
                        typeButton.setTooltip(Bundle.get("ubindgroup.selectunit"));
                    });
                    
                    // count参数
                    row.add(tooltip(new Label("Count: "), Bundle.get("ubindgroup.param.count.tooltip")));
                    row.addField(count, text -> count = text).size(80f);
                }
                
                // mode参数
                row.add(tooltip(new Label("Mode: "), Bundle.get("ubindgroup.mode.tooltip")));
                row.button(()-> mode, Tex.pane, Styles.cleari, () -> {
                    // 简单的模式切换逻辑
                    if (mode.equals("grabbing")) {
                        mode = "access";
                    } else {
                        mode = "grabbing";
                    }
                    // 重建UI以反映更改
                    table.clearChildren();
                    build(table);
                }).size(120f).tooltip(Bundle.get("ubindgroup.mode"));
            }).row();
            
            // 第二行：unitVar, indexVar, group
            table.table(row -> {
                // unitVar参数
                row.add(tooltip(new Label("Unit Var: "), Bundle.get("ubindgroup.param.var.tooltip")));
                row.addField(unitVar, text -> unitVar = text).size(120f);
                
                // indexVar参数
                row.add(tooltip(new Label("Index Var: "), Bundle.get("ubindgroup.param.index.tooltip")));
                row.addField(indexVar, text -> indexVar = text).size(120f);
                
                // group参数
                row.add(tooltip(new Label("Group: "), Bundle.get("ubindgroup.param.group.tooltip")));
                row.table(t -> {
                    t.add(group).size(120f);
                    t.button(tooltip(new Button("Manage", Styles.logict), Bundle.get("ubindgroup.groupmanager.title")), () -> 
                        showGroupManager()
                    ).size(80f);
                });
            }).row();
        }
        
        @Override
        public LInstruction build(LAssembler builder) {
            // 构建并返回LUnitBindGroupInstruction指令
            return new LUnitBindGroupInstruction(
                type,
                builder.resolveLabel(count),
                group,
                mode.equals("access"),
                builder.resolveVar(unitVar, false),
                builder.resolveVar(indexVar, false)
            );
        }
        
        // 静态create方法，用于创建语句实例
        public static UnitBindGroupStatement create() {
            return new UnitBindGroupStatement();
        }
        
        @Override
        public Category category() {
            return LCategoryExt.units; // 使用units分类
        }
    }
}