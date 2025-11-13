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

import static mindustry.Vars.*;

/**
 * 单位绑定组UI相关类
 * 负责处理UI构建、模式选择、参数缓存等UI逻辑
 */
public class LUnitBindGroupUI {
    /**
     * 注册UI解析器和语句到逻辑系统
     */
    public static void register() {
        // 注册自定义解析器
        LAssembler.customParsers.put("ubindgroup", params -> {
            UnitBindGroupStatement stmt = new UnitBindGroupStatement();
            if (params.length >= 2) {
                // 处理参数
            }
            return stmt;
        });
        // 将语句添加到语句列表中，使其在游戏UI中可见
        LogicIO.allStatements.add(UnitBindGroupStatement::new);
    }
    
    // 移除静态初始化块，避免重复注册指令
    // 指令注册现在通过LEMain.java中的LUnitBindGroupUI.register()方法统一管理
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
        public LVar unitTypeVar;
        public LVar countVar;
        public LVar unitVar;
        public LVar indexVar;
        public String group = "";
        public int mode = MODE_GRAB;
        
        public String name() {
            return "ubindgroup";
        }
        
        public String description() {
            return "绑定单位到指定组";
        }
        
        public LCategory category() {
            return LCategory.unit;
        }
        
        public void build(Table table) {
            table.clear();
            
            // 添加模式按钮
            Table modeTable = new Table();
            modeButton(modeTable);
            
            // 左侧表格：单位类型、数量、单位、索引变量
            Table leftTable = new Table();
            leftTable.table(tt -> {
                // 单位类型变量选择
                tt.add("单位类型变量").padRight(5f);
                TextField typeField = tt.field("", Styles.defaultField, str -> unitTypeVar = new LVar(str)).width(150f).get();
                // 添加悬浮提示
                typeField.addListener(new Tooltip(tip -> {
                    tip.background(Styles.black6);
                    tip.add("输入单位类型变量名").pad(4f);
                }));
                tt.row();
                
                // 数量变量选择（仅在抓取模式显示）
                if (mode == MODE_GRAB) {
                    tt.add("数量变量").padRight(5f);
                    TextField countField = tt.field("", Styles.defaultField, str -> countVar = new LVar(str)).width(150f).get();
                    countField.addListener(new Tooltip(tip -> {
                        tip.background(Styles.black6);
                        tip.add("输入最大单位数量变量名").pad(4f);
                    }));
                    tt.row();
                }
                
                // 单位变量选择
                tt.add("单位变量").padRight(5f);
                TextField unitField = tt.field("", Styles.defaultField, str -> unitVar = new LVar(str)).width(150f).get();
                unitField.addListener(new Tooltip(tip -> {
                    tip.background(Styles.black6);
                    tip.add("输入单位变量名").pad(4f);
                }));
                tt.row();
                
                // 索引变量选择
                tt.add("索引变量").padRight(5f);
                TextField indexField = tt.field("", Styles.defaultField, str -> indexVar = new LVar(str)).width(150f).get();
                indexField.addListener(new Tooltip(tip -> {
                    tip.background(Styles.black6);
                    tip.add("输入索引变量名").pad(4f);
                }));
            });
            
            // 右侧表格：组名称选择
            Table rightTable = new Table();
            Button groupButton = rightTable.button("组名" + (group.isEmpty() ? "" : ": " + group), Styles.logict, () -> {
                LUnitBindGroupUI.showGroupManagerDialog(group, selectedGroup -> {
                    this.group = selectedGroup != null ? selectedGroup : "";
                    build(table);
                }, mode);
            }).width(200f).padBottom(20f).get();
            
            groupButton.addListener(new Tooltip(tip -> {
                tip.background(Styles.black6);
                tip.add("选择或创建单位组").pad(4f);
            }));
            
            // 组合布局
            table.add(leftTable).left();
            table.add(rightTable).left().padLeft(20f);
            table.row();
            table.add(modeTable).left().padTop(10f).colspan(2);
        }
        
        /**
         * 创建模式切换按钮
         */
        private void modeButton(Table parent) {
            Button button = parent.button("", Styles.logict, () -> {
                // 切换模式
                mode = mode == MODE_GRAB ? MODE_PASSIVE : MODE_GRAB;
                rebuild(parent.parent);
            }).width(180f).get();
            
            // 设置按钮文本
            button.getLabelCell().get().setText(mode == MODE_GRAB ? Core.bundle.get("ubindgroup.mode.grab", "抓取模式") : Core.bundle.get("ubindgroup.mode.passive", "被动模式"));
            
            // 添加悬浮提示
            button.addListener(new Tooltip(tip -> {
                tip.background(Styles.black6);
                tip.add(mode == MODE_GRAB ? "抓取模式：获取并控制单位" : "被动模式：访问但不控制单位").pad(4f);
            }));
        }
        
        // 辅助方法：重建父表格
        private void rebuild(Table parent) {
            if (parent != null) {
                parent.clearChildren();
                build(parent);
            }
        }
        
        public LInstruction build(LAssembler build) {
            return new UnitBindGroupInstruction(
                unitTypeVar, countVar, unitVar, indexVar, group, mode
            );
        }
        
        public void serialize(DataOutput stream) {
            try {
                stream.writeUTF(unitTypeVar != null ? unitTypeVar.name : "");
                stream.writeUTF(countVar != null ? countVar.name : "");
                stream.writeUTF(unitVar != null ? unitVar.name : "");
                stream.writeUTF(indexVar != null ? indexVar.name : "");
                stream.writeUTF(group);
                stream.writeInt(mode);
            } catch (IOException e) {
                Log.err(e);
            }
        }
        
        public void deserialize(DataInput stream) {
            try {
                String unitTypeName = stream.readUTF();
                unitTypeVar = !unitTypeName.isEmpty() ? new LVar(unitTypeName) : null;
                
                String countVarName = stream.readUTF();
                countVar = !countVarName.isEmpty() ? new LVar(countVarName) : null;
                
                String unitVarName = stream.readUTF();
                unitVar = !unitVarName.isEmpty() ? new LVar(unitVarName) : null;
                
                String indexVarName = stream.readUTF();
                indexVar = !indexVarName.isEmpty() ? new LVar(indexVarName) : null;
                
                group = stream.readUTF();
                mode = stream.readInt();
            } catch (IOException e) {
                Log.err(e);
            }
        }
        
        public void compile(LAssembler build) {
            // compile方法可以保留为空，因为build方法已经处理了指令生成
        }
        
        public LStatement copy() {
            UnitBindGroupStatement copy = new UnitBindGroupStatement();
            copy.unitTypeVar = unitTypeVar;
            copy.countVar = countVar;
            copy.unitVar = unitVar;
            copy.indexVar = indexVar;
            copy.group = group;
            copy.mode = mode;
            return copy;
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
        private String group;
        private int mode;
        
        public UnitBindGroupInstruction(LVar unitTypeVar, LVar countVar, LVar unitVar, LVar indexVar, String group, int mode) {
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
