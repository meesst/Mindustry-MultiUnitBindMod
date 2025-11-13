package logicExtend;

import arc.*;
import arc.func.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.graphics.Color;
import java.io.*;
import java.util.function.Consumer;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.logic.LExecutor.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.Vars;

import static mindustry.Vars.*;

/**
 * 单位绑定组UI相关类
 * 负责处理UI构建、模式选择、参数缓存等UI逻辑
 */
public class LUnitBindGroupUI {
    /**
     * 重新添加register方法以保持与LEMain.java的兼容性
     */
    public static void register() {
        // 调用UnitBindGroupStatement的create方法进行注册
        UnitBindGroupStatement.create();
    }
    // UI相关的常量定义
    public static final int MODE_GRAB = 1;
    public static final int MODE_PASSIVE = 2;
    
    /**
     * 显示组管理对话框
     */
    public static void showGroupManagerDialog(String currentGroup, Consumer<String> callback, int mode) {
        BaseDialog dialog = new BaseDialog(Core.bundle.get("ubindgroup.groupmanager.title", "单位组管理"));
        dialog.setFillParent(false);
        dialog.cont.setWidth(500f);
        dialog.closeOnBack();
        
        // 左侧：组列表
        // 右侧：组信息和操作按钮
        dialog.cont.table(t -> {
            t.setColor(t.color);
            
            // 左侧组列表
            Table listTable = new Table();
            ScrollPane listScroll = new ScrollPane(listTable, Styles.smallPane);
            listScroll.setFadeScrollBars(false);
            
            // 添加"无单位组"选项
            Table noGroupRow = new Table();
            noGroupRow.add(Core.bundle.get("ubindgroup.groupmanager.nogroup", "无单位组")).left().fillX().pad(4f);
            noGroupRow.row();
            noGroupRow.add().height(1f).fillX().color(Color.darkGray);
            noGroupRow.clicked(() -> {
                callback.accept(null);
                dialog.hide();
            });
            noGroupRow.setBackground(currentGroup == null ? Styles.black3 : Styles.none);
            listTable.add(noGroupRow).fillX().row();
            
            // 添加已有的共享组
            Seq<String> sortedGroups = new Seq<>();
            sortedGroups.addAll(LUnitBindGroup.getSharedGroups().keys());
            sortedGroups.sort(); // 按字母顺序排序
            
            for (String group : sortedGroups) {
                Table groupRow = new Table();
                groupRow.add(group).left().fillX().pad(4f);
                groupRow.row();
                groupRow.add().height(1f).fillX().color(Color.darkGray);
                groupRow.clicked(() -> {
                    callback.accept(group);
                    dialog.hide();
                });
                groupRow.setBackground(currentGroup != null && currentGroup.equals(group) ? Styles.black3 : Styles.none);
                listTable.add(groupRow).fillX().row();
            }
            
            // 添加新组按钮
            listTable.button(Core.bundle.get("ubindgroup.groupmanager.newgroup", "新建组"), Styles.logict, () -> {
                dialog.hide();
                
                // 打开新组对话框
                BaseDialog newGroupDialog = new BaseDialog(Core.bundle.get("ubindgroup.groupmanager.newgrouptitle", "新建单位组"));
                TextField groupField = new TextField("", Styles.nodeField);
                newGroupDialog.cont.add(Core.bundle.get("ubindgroup.groupmanager.groupname", "组名称")).padRight(10f);
                newGroupDialog.cont.add(groupField).width(200f).row();
                
                newGroupDialog.cont.button(Core.bundle.get("ubindgroup.groupmanager.create", "创建"), () -> {
                    String groupName = groupField.getText().trim();
                    if (groupName.isEmpty()) {
                        ui.showInfoToast("组名称不能为空", 5f);
                        return;
                    }
                    
                    if (LUnitBindGroup.getSharedGroups().containsKey(groupName)) {
                        ui.showInfoToast("组名称已存在", 5f);
                        return;
                    }
                    
                    // 创建新组
                    LUnitBindGroup.createNewGroup(groupName);
                    callback.accept(groupName);
                    newGroupDialog.hide();
                }).row();
                
                newGroupDialog.addCloseButton();
                newGroupDialog.show();
            }).width(200f).padTop(10f).row();
            
            // 右侧操作面板
            Table actionsTable = new Table();
            
            // 组信息
            actionsTable.add(Core.bundle.get("ubindgroup.groupmanager.info", "组信息")).left().row();
            actionsTable.image().height(1f).fillX().color(Color.darkGray).row();
            
            // 显示当前选中组的信息
            String groupToShow = currentGroup;
            if (groupToShow != null && LUnitBindGroup.getSharedGroups().containsKey(groupToShow)) {
                LUnitBindGroup.UnitGroupInfo info = LUnitBindGroup.getSharedGroups().get(groupToShow);
                actionsTable.add(Core.bundle.get("ubindgroup.groupmanager.unitcount", "单位数量") + ": " + info.units.size).left().row();
                
                // 删除组按钮（只有在有组的情况下显示）
                actionsTable.button(Core.bundle.get("ubindgroup.groupmanager.delete", "删除组"), Styles.defaultt, () -> {
                    // 确认删除
                    BaseDialog confirmDialog = new BaseDialog(Core.bundle.get("ubindgroup.groupmanager.deleteconfirm", "确认删除"));
                    confirmDialog.cont.add(Core.bundle.get("ubindgroup.groupmanager.deleteconfirmtext", "确定要删除这个单位组吗？所有绑定的单位将被解绑。")).width(400f).wrap().row();
                    confirmDialog.cont.button(Core.bundle.get("ubindgroup.groupmanager.confirm", "确认"), Styles.defaultt, () -> {
                        // 删除组
                            if (LUnitBindGroup.getSharedGroups().containsKey(groupToShow)) {
                                LUnitBindGroup.UnitGroupInfo groupInfo = LUnitBindGroup.getSharedGroups().get(groupToShow);
                                // 解绑所有单位
                                if (groupInfo.units != null) {
                                    for (Unit unit : groupInfo.units) {
                                        LUnitBindGroup.unbindUnit(unit);
                                    }
                                }
                                LUnitBindGroup.deleteGroup(groupToShow);
                            // 切换到"无单位组"
                            callback.accept(null);
                            dialog.hide();
                        }
                        confirmDialog.hide();
                    }).size(120f, 50f).padRight(20f);
                    confirmDialog.cont.button(Core.bundle.get("ubindgroup.groupmanager.cancel", "取消"), Styles.logict, () -> {
                        confirmDialog.hide();
                    }).size(120f, 50f);
                    confirmDialog.show();
                }).size(150f, 50f).padTop(20f);
            } else {
                actionsTable.add(Core.bundle.get("ubindgroup.groupmanager.nogroupselected", "未选中任何单位组")).left().row();
            }
            
            t.add(listScroll).size(250f, 400f);
            t.add(actionsTable).fillY().pad(10f);
        });
        
        dialog.addCloseButton();
        dialog.show();
    }
    
    /**
     * 单位绑定组语句类 - 负责UI构建和指令注册
     */
    public static class UnitBindGroupStatement extends LStatement {
        public String unitType = null, count = "1", unitVar = "currentUnit", indexVar = "unitIndex", groupName = "\"null\"";
        public int mode = 1; // 1: 正常抓取逻辑，2: 共享组内单位无需抓取
        
        /**
         * 静态方法，用于注册这个语句到游戏的逻辑系统中
         */
        public static void create() {
            LAssembler.registerStatement("ubindgroup", UnitBindGroupStatement::new);
        }
        @Override
        public void build(Table table) {
            rebuild(table);
        }
        
        private void rebuild(Table table) {
            table.clearChildren();
            table.left();
            
            // 第一排：单位类型、单位数量和模式选择
            table.table(t -> {
                t.setColor(table.color);
                
                // 单位类型参数（模式1显示）
                if (mode == 1) {
                    t.add(Core.bundle.get("ubindgroup.param.unitType", "type")).padLeft(10).left().self(c -> {
                        this.param(c);
                    });
                    TextField field = field(t, unitType, str -> unitType = sanitize(str)).get();
                    
                    // 完全按照游戏源代码中的UnitBindStatement实现方式
                    t.button(b -> {
                        b.image(Icon.pencilSmall);
                        b.clicked(() -> showSelectTable(b, (table_, hide) -> {
                            table_.row();
                            table_.table(i -> {
                                i.left();
                                int c = 0;
                                for(UnitType item : Vars.content.units()){
                                    if(!item.unlockedNow() || item.isHidden() || !item.logicControllable) continue;
                                    i.button("", Styles.flati, 24f, () -> {
                                        unitType = "@" + item.name;
                                        field.setText(unitType);
                                        hide.run();
                                    }).size(40f).get().getStyle().imageUp = item.uiIcon;

                                    if(++c % 6 == 0) i.row();
                                }
                            }).colspan(3).width(240f).left();
                        }));
                    }, Styles.logict, () -> {}).size(40f).padLeft(-2).color(t.color);
                    
                    // 数量参数
                    t.add(Core.bundle.get("ubindgroup.param.count", "count")).padLeft(10).left().self(c -> {
                        this.param(c);
                    });
                    t.field(count, Styles.nodeField, s -> count = sanitize(s))
                        .size(144f, 40f).pad(2f).color(t.color)
                        .width(80f).padRight(10).left();
                }
                
                // 模式选择
                t.add("mode:").left();
                modeButton(t, table);
            }).left();
            
            table.row();
            
            // 第二排：变量名和组名称参数
            table.table(t -> {
                t.setColor(table.color);
                
                // 单位变量参数
                t.add(Core.bundle.get("ubindgroup.param.var", "unitVar")).padLeft(10).left().self(c -> {
                    this.param(c);
                });
                t.field(unitVar, Styles.nodeField, s -> unitVar = sanitize(s))
                    .size(144f, 40f).pad(2f).color(t.color)
                    .width(150f).padRight(0).left();
                
                // 索引变量参数
                t.add(Core.bundle.get("ubindgroup.param.index", "indexVar")).padLeft(10).left().self(c -> {
                    this.param(c);
                });
                t.field(indexVar, Styles.nodeField, s -> indexVar = sanitize(s))
                    .size(144f, 40f).pad(2f).color(t.color)
                    .width(150f).padRight(0).left();
                
                // 组名称参数 - 替换为按钮，点击打开组管理窗口
                t.add(Core.bundle.get("ubindgroup.param.group", "groupName")).padLeft(10).left().self(c -> {
                    this.param(c);
                });
                t.button(b -> {
                    // 显示当前选择的组名，参考mode元素按钮的实现方式
                    String displayText = (groupName == null || groupName.equals("null")) ? 
                                        Core.bundle.get("ubindgroup.groupmanager.nogroup", "无单位组") : 
                                        groupName;
                    b.add(displayText).left();
                }, Styles.logict, () -> {
                    // 打开组管理窗口，传递当前模式
                    String currentGroup = groupName;
                    showGroupManagerDialog(currentGroup, (selected) -> {
                        // 更新组名
                        if (selected == null) {
                            groupName = "null"; // 表示选择了"无单位组"
                        } else {
                            groupName = selected;
                        }
                        // 选择组后刷新UI
                        rebuild(table);
                    }, this.mode);
                }).size(150f, 40f).pad(2f).color(t.color)
                    .padRight(0).left();
            }).left();
        }
        
        void modeButton(Table table, Table parent) {
            table.button(b -> {
                b.add(mode == 1 ? Core.bundle.get("ubindgroup.mode.capture", "抓取模式") : Core.bundle.get("ubindgroup.mode.access", "访问模式")).left();
                b.clicked(() -> {
                    BaseDialog dialog = new BaseDialog(Core.bundle.get("ubindgroup.mode.select.title", "选择模式"));
                    dialog.cont.setWidth(300f);
                    dialog.cont.button("1. " + Core.bundle.get("ubindgroup.mode.capture", "抓取模式"), () -> {
                        mode = 1;
                        rebuild(parent);
                        dialog.hide();
                    }).width(280f).row();
                    dialog.cont.button("2. " + Core.bundle.get("ubindgroup.mode.access", "访问模式"), () -> {
                        mode = 2;
                        rebuild(parent);
                        dialog.hide();
                    }).width(280f).row();
                    dialog.addCloseButton();
                    dialog.show();
                });
            }, Styles.logict, () -> {}).size(120f, 40f).color(table.color);
        }
        
        // 不再需要单独的showUnitTypeSelect方法，按钮逻辑已集成到rebuild方法中
        void showUnitTypeSelect(Table table) {
            // 保留此方法以避免编译错误，但实际功能已移至rebuild方法
        }
        
        @Override
        public LExecutor.LInstruction build(LAssembler builder) {
            // 单独判断groupName参数，处理"null"字符串情况
            LVar groupNameVar = null;
            if (groupName != null && !groupName.equals("null")) {
                groupNameVar = builder.var(groupName);
            }
            
            return new UnitBindGroupInstruction(
                builder.var(unitType),
                builder.var(count),
                builder.var(unitVar),
                indexVar.isEmpty() || indexVar.equals("null") ? null : builder.var(indexVar),
                groupNameVar,
                mode
            );
        }
        
        @Override
        public LCategory category() {
            return LCategory.unit;
        }
        
        @Override
        public String name() {
            return Core.bundle.get("lst.ubindgroup", "ubindgroup");
        }
        
        public String description() {
            return Core.bundle.get("lst.ubindgroup.description", "单位绑定组: 将单位分组管理和访问");
        }
        
        @Override
        public void write(StringBuilder builder) {
            builder.append("ubindgroup ").append(unitType).append(" ").append(count).append(" ")
                   .append(unitVar).append(" " ).append(indexVar);
            if (groupName != null) {
                builder.append(" " ).append(groupName);
            }
            builder.append(" " ).append(mode);
        }
    }
    
    /**
     * 单位绑定组指令类 - 负责UI指令逻辑
     */
    public static class UnitBindGroupInstruction implements LInstruction {
        private LVar unitTypeVar;
        private LVar countVar;
        private LVar unitVar;
        private LVar indexVar;
        private LVar group;
        private int mode;
        
        public UnitBindGroupInstruction(LVar unitTypeVar, LVar countVar, LVar unitVar, LVar indexVar, LVar group, int mode) {
            this.unitTypeVar = unitTypeVar;
            this.countVar = countVar;
            this.unitVar = unitVar;
            this.indexVar = indexVar;
            this.group = group;
            this.mode = mode;
        }
        
        
        public void run(LExecutor executor) {
            // 调用主逻辑类的bindGroup方法
            LUnitBindGroup.bindGroup(
                executor, unitTypeVar, countVar, unitVar, indexVar, group, mode
            );
        }
        
        public boolean isControlFlow() {
            return false;
        }
    }
    
    /**
     * 无参数版本的showGroupManagerDialog方法，保持向后兼容
     */
    public static void showGroupManagerDialog() {
        showGroupManagerDialog(null, groupName -> {}, MODE_GRAB);
    }
}