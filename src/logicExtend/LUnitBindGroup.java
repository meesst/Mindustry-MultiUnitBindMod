package logicExtend;

import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.Label;
import arc.scene.ui.TextField;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.scene.style.TextureRegionDrawable;
import arc.math.geom.Vec2;
import arc.Core;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.UnitType;
import mindustry.game.Team;
import mindustry.Vars;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ai.types.LogicAI;
import mindustry.ai.types.CommandAI;
import java.util.Objects;

public class LUnitBindGroup {
    // 常量定义
    private static final float iconSmall = 24f;
    
    public static class UnitBindGroupStatement extends LStatement {
        public String unitType = "@poly", count = "10", unitVar = "currentUnit", indexVar = "unitIndex", groupName = null;
        public int mode = 1; // 1: 正常抓取逻辑，2: 共享组内单位无需抓取
        
        // 实现tooltip方法，用于为标签添加悬浮提示
        private void tooltip(Cell<Label> labelCell, String text) {
            if (labelCell != null && labelCell.get() != null) {
                labelCell.tooltip(text);
            }
        }
        
        // 重写field方法，确保使用正确的样式
        @Override
        protected Cell<TextField> field(Table table, String value, arc.func.Cons<String> setter) {
            // 直接使用父类的sanitize方法和正确的样式
            return table.field(value, Styles.nodeField, s -> setter.get(sanitize(s)));
        }
        
        // 实现showSelectTable方法，用于显示单位类型选择对话框
        private void showSelectTable(Cell<?> button, UnitTypeSelectListener listener) {
            BaseDialog dialog = new BaseDialog("选择单位类型");
            dialog.addCloseButton();
            
            listener.build(dialog.cont, () -> dialog.hide());
            dialog.show();
        }
        
        // 接口定义，用于构建选择表格内容
        private interface UnitTypeSelectListener {
            void build(Table table, Runnable hide);
        }
        
        @Override
        public void build(Table table) {
            // 第一排：模式选择和单位类型参数
            Table firstRow = new Table();
            table.add(firstRow).left().row();
            
            // 模式选择参数
            firstRow.add("mode:");
            modeButton(firstRow, table);
            
            // 单位类型参数（模式1显示）
            if (mode == 1) {
                Cell<Label> typeLabel = firstRow.add(Core.bundle.get("ubindgroup.param.unitType", "type"));
                tooltip(typeLabel, Core.bundle.get("ubindgroup.param.unitType.tooltip", "单位类型: 指定要抓取的单位类型"));
                
                TextField field = field(firstRow, unitType, str -> unitType = str).get();
                
                firstRow.button(b -> {
                    b.image(Icon.pencilSmall);
                    b.clicked(() -> showSelectTable(b, (t, hide) -> {
                        t.row();
                        t.table(i -> {
                            i.left();
                            int c = 0;
                            for(UnitType item : Vars.content.units()){
                                if(!item.unlockedNow() || item.isHidden() || !item.logicControllable) continue;
                                i.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconSmall, () -> {
                                    unitType = "@" + item.name;
                                    field.setText(unitType);
                                    hide.run();
                                }).size(40f);

                                if(++c % 6 == 0) i.row();
                            }
                        }).colspan(3).width(240f).left();
                    }));
                }, Styles.logict, () -> {}).size(40f).padLeft(-2);
            }
            
            // 第二排：数量、变量名和组名称参数
            Table secondRow = new Table();
            table.add(secondRow).left().row();
            
            // 数量参数（模式1显示）
            if (mode == 1) {
                Cell<Label> countLabel = secondRow.add(Core.bundle.get("ubindgroup.param.count", "count"));
                tooltip(countLabel, Core.bundle.get("ubindgroup.param.count.tooltip", "单位数量: 指定要抓取的最大单位数量"));
                field(secondRow, count, str -> count = str);
            }
            
            // 单位变量参数
            Cell<Label> unitVarLabel = secondRow.add(Core.bundle.get("ubindgroup.param.var", "unitVar"));
            tooltip(unitVarLabel, Core.bundle.get("ubindgroup.param.var", "输出变量名:") + " - 存储单位引用的变量名");
            field(secondRow, unitVar, str -> unitVar = str);
            
            // 索引变量参数
            Cell<Label> indexVarLabel = secondRow.add(Core.bundle.get("ubindgroup.param.index", "indexVar"));
            tooltip(indexVarLabel, Core.bundle.get("ubindgroup.param.index.tooltip", "索引变量: 存储当前单位索引的变量名（从1开始）"));
            field(secondRow, indexVar, str -> indexVar = str);
            
            // 组名称参数
            Cell<Label> groupNameLabel = secondRow.add(Core.bundle.get("ubindgroup.param.group", "groupName"));
            tooltip(groupNameLabel, Core.bundle.get("ubindgroup.param.group.tooltip", "组名称: 标识共享单位组的唯一名称"));
            field(secondRow, groupName != null ? groupName : "null", str -> groupName = str.isEmpty() ? null : str);
        }
        
        void modeButton(Table table, Table parent) {
            table.button(b -> {
                b.label(() -> mode == 1 ? Core.bundle.get("ubindgroup.mode.capture", "抓取模式") : Core.bundle.get("ubindgroup.mode.access", "访问模式"));
                b.clicked(() -> {
                    BaseDialog dialog = new BaseDialog(Core.bundle.get("ubindgroup.mode.select.title", "选择模式"));
                    // 设置对话框宽度为300像素，解决文字竖向排列问题
                    dialog.cont.width(300f);
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
            }, Styles.logict, () -> {}).size(160f, 40f).pad(4f).color(table.color);
            // 为按钮添加tooltip（修复变量引用错误）
        }
        
        void rebuild(Table table) {
            table.clearChildren();
            build(table);
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
        
        /** Anuken, if you see this, you can replace it with your own @RegisterStatement, because this is my last resort... **/
        public static void create() {
            LAssembler.customParsers.put("ubindgroup", params -> {
                UnitBindGroupStatement stmt = new UnitBindGroupStatement();
                if (params.length >= 2) stmt.unitType = params[1];
                if (params.length >= 3) stmt.count = params[2];
                if (params.length >= 4) stmt.unitVar = params[3];
                if (params.length >= 5) stmt.indexVar = params[4].equals("null") ? "" : params[4];
                if (params.length >= 6) stmt.groupName = params[5].equals("null") ? null : params[5];
                if (params.length >= 7) {
                    try {
                        stmt.mode = Integer.parseInt(params[6]);
                        if (stmt.mode < 1 || stmt.mode > 2) stmt.mode = 1; // 范围检查
                    } catch (NumberFormatException e) {
                        stmt.mode = 1;
                    }
                }
                stmt.afterRead();
                return stmt;
            });
            LogicIO.allStatements.add(UnitBindGroupStatement::new);
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
    
    public static class UnitBindGroupInstruction implements LExecutor.LInstruction {
        private final LVar unitType;
        private final LVar count;
        private final LVar unitVar;
        private final LVar indexVar;
        private final LVar groupName;
        private final int mode;
        
        // 存储每个逻辑控制器的独立单位组和当前索引
        private static final ObjectMap<Building, UnitGroupInfo> individualGroups = new ObjectMap<>();
        // 存储按组名共享的单位组和当前索引
        private static final ObjectMap<String, UnitGroupInfo> sharedGroups = new ObjectMap<>();
        // 记录处理器与共享组的关联
        private static final ObjectMap<Building, String> buildingToGroupName = new ObjectMap<>();
        // 存储处理器的参数缓存，用于检测参数变化
        private static final ObjectMap<Building, ParamCache> paramCaches = new ObjectMap<>();
        // 用于存储共享组的最大count值
        private static final ObjectMap<String, Integer> sharedGroupMaxCounts = new ObjectMap<>();
        // 用于存储共享组的初始配置
        private static final ObjectMap<String, GroupConfig> sharedGroupConfigs = new ObjectMap<>();
        
        // 共享组配置类，用于存储共享组的初始参数
        private static class GroupConfig {
            public final Object unitType;
            public final int count;
            public final int mode;
            
            public GroupConfig(Object unitType, int count, int mode) {
                this.unitType = unitType;
                this.count = count;
                this.mode = mode;
            }
        }
        
        // 参数缓存类，用于存储上次执行时的参数值
        private static class ParamCache {
            public Object unitType;
            public int count;
            public String groupName;
            
            public boolean hasChanged(Object newUnitType, int newCount, String newGroupName) {
                return !Objects.equals(unitType, newUnitType) || 
                       count != newCount || 
                       !Objects.equals(groupName, newGroupName);
            }
            
            public void update(Object newUnitType, int newCount, String newGroupName) {
                this.unitType = newUnitType;
                this.count = newCount;
                this.groupName = newGroupName;
            }
        }
        
        public UnitBindGroupInstruction(LVar unitType, LVar count, LVar unitVar, LVar indexVar, LVar groupName, int mode) {
            this.unitType = unitType;
            this.count = count;
            this.unitVar = unitVar;
            this.indexVar = indexVar;
            this.groupName = groupName;
            this.mode = mode;
        }
        
        @Override
        public void run(LExecutor exec) {
            // 获取当前逻辑控制器
            Building controller = exec.build;
            
            // 安全检查：确保控制器有效
            if (controller == null || !controller.isValid()) {
                // 清理无效控制器的资源
                cleanupInvalidController(controller);
                return;
            }

            // 获取组名称参数
            String groupNameStr = groupName == null ? null : (String)groupName.obj();
            // 单独判断"null"字符串情况
            if (groupNameStr != null && groupNameStr.equals("null")) {
                groupNameStr = null;
            }
            
            // 初始化结果变量
            String errorMessage = null;
            
            try {
                // 处理共享组模式下的参数冲突检测
                if (groupNameStr != null) {
                    // 模式1的参数冲突检测
                    if (mode == 1) {
                        // 获取单位类型和数量参数
                        Object typeObj = unitType.obj();
                        int maxCount = Math.max(1, (int)count.num());
                        
                        // 检查共享组配置冲突
                        if (sharedGroupConfigs.containsKey(groupNameStr)) {
                            GroupConfig config = sharedGroupConfigs.get(groupNameStr);
                            if (config.mode == 1 && 
                                (!Objects.equals(config.unitType, typeObj) || 
                                 config.count != maxCount)) {
                                // 参数冲突，设置错误信息
                                errorMessage = Core.bundle.get("ubindgroup.error.param_conflict", "参数冲突");
                            }
                        } else {
                            // 第一次设置此共享组，记录配置
                            sharedGroupConfigs.put(groupNameStr, new GroupConfig(typeObj, maxCount, mode));
                        }
                    } else if (mode == 2) {
                        // 模式2下，检查组是否存在
                        if (!sharedGroupConfigs.containsKey(groupNameStr)) {
                            // 模式2需要有对应的共享组存在
                            errorMessage = Core.bundle.get("ubindgroup.error.group_not_exist", "共享组不存在");
                        }
                    }
                }
                
                // 如果有错误，设置错误状态并返回
                if (errorMessage != null) {
                    unitVar.setobj(errorMessage);
                    if (indexVar != null) {
                        indexVar.setobj(errorMessage);
                    }
                    return;
                }
                
                // 根据模式执行不同逻辑
                if (mode == 1) {
                    // 模式1：正常抓取逻辑
                    executeMode1(exec, controller, groupNameStr);
                } else if (mode == 2) {
                    // 模式2：共享组访问逻辑
                    executeMode2(exec, groupNameStr);
                }
            } catch (Exception e) {
                // 处理异常情况，返回多语言异常错误信息
                String exceptionError = Core.bundle.get("ubindgroup.error.exception", "执行异常");
                unitVar.setobj(exceptionError);
                if (indexVar != null) {
                    indexVar.setobj(exceptionError);
                }
            }
            return;
        }
        
        private void executeMode1(LExecutor exec, Building controller, String groupNameStr) {
            // 获取单位类型和数量参数
            Object typeObj = unitType.obj();
            int maxCount = Math.max(1, (int)count.num());
            
            // 检查参数是否发生变化，如果变化则需要重置
            boolean paramsChanged = checkAndUpdateParams(controller, typeObj, maxCount, groupNameStr);
            
            // 获取或创建单位组信息
            UnitGroupInfo info = null;
            
            if (groupNameStr != null) {
                // 使用共享单位池
                info = sharedGroups.get(groupNameStr);
                if (info == null) {
                    info = new UnitGroupInfo();
                    info.mode = this.mode; // 设置模式
                    sharedGroups.put(groupNameStr, info);
                }
                // 记录处理器与共享组的关联
                buildingToGroupName.put(controller, groupNameStr);
            } else {
                // 使用独立单位池（兼容原有功能）
                info = individualGroups.get(controller);
                if (info == null) {
                    info = new UnitGroupInfo();
                    info.mode = this.mode; // 设置模式
                    individualGroups.put(controller, info);
                }
                // 移除可能存在的共享组关联
                buildingToGroupName.remove(controller);
            }
        
            // 如果参数发生变化，重置索引
            if (paramsChanged) {
                info.currentIndex = -1;
            }
            
            // 定期进行内存清理和未使用组的清理
            cleanupMemoryAndUnusedGroups();
            
            // 确保单位组是最新的，传入控制器信息用于状态检查
            updateUnitGroup(info, typeObj, maxCount, exec.team, controller, groupNameStr);
            
            // 循环遍历单位组
            if (!info.units.isEmpty()) {
                // 更新当前索引
                info.currentIndex = (info.currentIndex + 1) % info.units.size;
                
                // 获取当前单位
                Unit unit = info.units.get(info.currentIndex);
                
                // 写入返回变量
                unitVar.setobj(unit);
                
                // 如果指定了索引变量，写入单位索引
                if (indexVar != null) {
                    indexVar.setnum(info.currentIndex + 1); // 从1开始计数
                }
            } else {
                // 没有找到单位，清空返回变量
                unitVar.setobj(null);
                if (indexVar != null) {
                    indexVar.setnum(0);
                }
            }
        }
        
        // 存储每个共享组的最大count值（已在类顶部定义）
        
        // 上次清理时间，用于定期清理
        private static long lastCleanupTime = 0;
        private static final long CLEANUP_INTERVAL = 60 * 1000; // 每分钟清理一次
        
        // 更新单位组
        private void updateUnitGroup(UnitGroupInfo info, Object typeObj, int maxCount, Team team, Building controller, String groupName) {
            // 对于共享组，更新最大count值
            if (groupName != null) {
                Integer currentMax = sharedGroupMaxCounts.get(groupName);
                // 无论count增大还是减小，都更新为最新值
                sharedGroupMaxCounts.put(groupName, maxCount);
                // 使用存储的最新count值
                maxCount = sharedGroupMaxCounts.get(groupName);
            }
            // 记录更新前的单位数量，用于检测变化
            int previousSize = info.units.size;
            
            // 如果单位数量超过新的maxCount，立即调整大小
            if (info.units.size > maxCount) {
                info.units.truncate(maxCount);
                // 重置当前索引，避免索引越界
                if (info.currentIndex >= info.units.size) {
                    info.currentIndex = -1;
                }
                
                // 处理没有可用单位的情况
                if (info.currentIndex == -1 || info.currentIndex >= info.units.size) {
                    String noUnitError = Core.bundle.get("ubindgroup.error.no_unit", "无可用单位");
                    unitVar.setobj(noUnitError);
                    if (indexVar != null) {
                        indexVar.setobj(noUnitError);
                    }
                } else {
                    Unit unit = info.units.get(info.currentIndex);
                    if (unit != null && unit.isValid()) {
                        unitVar.setobj(unit);
                        if (indexVar != null) {
                            indexVar.setnum(info.currentIndex + 1);
                        }
                    } else {
                        // 单位无效
                        String invalidUnitError = Core.bundle.get("ubindgroup.error.invalid_unit", "单位无效");
                        unitVar.setobj(invalidUnitError);
                        if (indexVar != null) {
                            indexVar.setobj(invalidUnitError);
                        }
                    }
                }
            }

            // 彻底清理无效单位，确保只保留符合所有条件的单位
            Seq<Unit> validUnits = new Seq<>();
            for (Unit unit : info.units) {
                // 全面检查单位状态
                if (unit != null && unit.isValid() && unit.team == team && !unit.dead && !unit.isPlayer()) {
                    // 对于共享组，单位可以被组内的任何处理器控制
                    boolean isValidUnit = false;
                    
                    if (groupName != null) {
                        // 共享组模式：检查单位是否被组内任何处理器控制
                        for (Building building : buildingToGroupName.keys()) {
                            if (building != null && building.isValid() && buildingToGroupName.get(building) != null && 
                                buildingToGroupName.get(building).equals(groupName)) {
                                if (isUnitControlledBy(building, unit)) {
                                    isValidUnit = true;
                                    break;
                                }
                            }
                        }
                        // 如果单位未被任何控制器控制，也认为是有效的
                        if (!isValidUnit && unit.controller() == null) {
                            isValidUnit = true;
                        }
                    } else {
                        // 独立模式：使用原有的验证逻辑
                        isValidUnit = isValidAndNotControlled(unit, controller);
                    }
                    
                    if (isValidUnit) {
                        validUnits.add(unit);
                        // 重新锁定有效的单位，确保控制关系持续存在
                        lockUnit(unit, controller);
                    }
                }
            }
            info.units = validUnits;

            // 检查是否有单位数量减少或状态变化
            boolean needSupplementation = info.units.size < previousSize || info.units.size < maxCount;

            // 如果需要补充单位
            if (needSupplementation) {
                // 获取符合条件的所有可用单位
                Seq<Unit> availableUnits = collectAvailableUnits(typeObj, team, controller, groupName);
                
                // 提高抓取概率的优化：先尝试直接控制单位，再添加到池中
                int needed = maxCount - info.units.size;
                int added = 0;
                
                // 优先处理未被控制的单位
                for (Unit unit : availableUnits) {
                    if (!unit.isValid() || unit.team != team || unit.dead || unit.isPlayer()) continue;
                    
                    // 确保单位尚未在池中
                    if (!info.units.contains(unit)) {
                        // 先尝试锁定单位，提高控制成功率
                        lockUnit(unit, controller);
                        
                        // 再次检查单位是否可用
                        boolean canAdd = isUnitAvailableForController(unit, controller, groupName);
                        
                        if (canAdd) {
                            info.units.add(unit);
                            added++;
                            
                            if (added >= needed) break;
                        }
                    }
                }
                
                // 如果还需要补充，再尝试其他单位
                if (added < needed) {
                    for (Unit unit : availableUnits) {
                        if (!unit.isValid() || unit.team != team || unit.dead || unit.isPlayer()) continue;
                        
                        // 确保单位尚未在池中
                        if (!info.units.contains(unit)) {
                            boolean canAdd = true;
                            
                            if (groupName != null) {
                                // 检查单位是否被其他非本组成员的处理器控制
                                for (Building building : individualGroups.keys()) {
                                    if (building != null && building.isValid() && !buildingToGroupName.containsKey(building) && 
                                        isUnitControlledBy(building, unit)) {
                                        canAdd = false;
                                        break;
                                    }
                                }
                                // 检查单位是否被其他共享组控制
                                if (canAdd) {
                                    for (Building building : buildingToGroupName.keys()) {
                                        String otherGroupName = buildingToGroupName.get(building);
                                        if (building != null && building.isValid() && otherGroupName != null && 
                                            !otherGroupName.equals(groupName) && 
                                            isUnitControlledBy(building, unit)) {
                                            canAdd = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (canAdd) {
                                // 再次锁定单位
                                lockUnit(unit, controller);
                                info.units.add(unit);
                                added++;
                                
                                if (added >= needed) break;
                            }
                        }
                    }
                }
            }
        }
        
        // 检查单位是否被指定处理器控制
        private boolean isUnitControlledBy(Building controller, Unit unit) {
            if (unit == null || controller == null || !controller.isValid()) return false;

            if (unit.controller() instanceof LogicAI) {
                LogicAI logicAI = (LogicAI)unit.controller();
                return logicAI != null && logicAI.controller == controller;
            } else if (unit.controller() instanceof Building) {
                return ((Building)unit.controller()) == controller;
            }

            return false;
        }
        
        // 收集所有符合条件的可用单位
        private Seq<Unit> collectAvailableUnits(Object typeObj, Team team, Building controller, String groupName) {
            Seq<Unit> result = new Seq<>();

            if (typeObj instanceof UnitType type && type.logicControllable) {
                // 针对特定单位类型
                Seq<Unit> units = team.data().unitCache(type);
                if (units != null) {
                    for (Unit unit : units) {
                        // 先检查基本条件
                        if (unit == null || !unit.isValid() || unit.team != team || unit.dead || unit.isPlayer()) continue;
                        
                        // 检查单位是否可用
                        boolean isAvailable = isUnitAvailableForController(unit, controller, groupName);
                        
                        if (isAvailable) {
                            result.add(unit);
                        }
                    }
                }
            } else if (typeObj instanceof String && ((String)typeObj).equals("@poly")) {
                // 处理@poly类型，表示任意可控制单位
                // 提高抓取概率：先遍历所有单位，包括从unitCache获取的特定类型单位
                // 1. 先获取所有可控制的单位类型
                for (UnitType ut : Vars.content.units()) {
                    if (!ut.logicControllable) continue;
                    
                    // 获取该类型的单位缓存
                    Seq<Unit> units = team.data().unitCache(ut);
                    if (units != null) {
                        for (Unit unit : units) {
                            // 先检查基本条件
                            if (unit == null || !unit.isValid() || unit.team != team || unit.dead || unit.isPlayer()) continue;
                            
                            // 避免重复添加
                            if (!result.contains(unit)) {
                                // 检查单位是否可用
                                boolean isAvailable = isUnitAvailableForController(unit, controller, groupName);
                                
                                if (isAvailable) {
                                    result.add(unit);
                                }
                            }
                        }
                    }
                }
                
                // 2. 再遍历team.data().units，确保不会遗漏任何单位
                for (Unit unit : team.data().units) {
                    // 确保单位可以被逻辑控制
                    if (unit == null || !unit.type.logicControllable) continue;
                    
                    // 先检查基本条件
                    if (!unit.isValid() || unit.team != team || unit.dead || unit.isPlayer()) continue;
                    
                    // 避免重复添加
                    if (!result.contains(unit)) {
                        // 检查单位是否可用
                        boolean isAvailable = isUnitAvailableForController(unit, controller, groupName);
                        
                        if (isAvailable) {
                            result.add(unit);
                        }
                    }
                }
            }

            return result;
        }
        
        // 检查单位是否可被当前控制器（或其所属组）使用
        private boolean isUnitAvailableForController(Unit unit, Building controller, String groupName) {
            // 空单位或空控制器检查
            if (unit == null || controller == null) return false;
            
            // 检查单位是否已被其他非本组成员的处理器占用
            
            // 检查独立处理器的单位池
            for (Building building : individualGroups.keys()) {
                if (building == null || building == controller) continue;
                
                UnitGroupInfo info = individualGroups.get(building);
                if (info != null && info.units.contains(unit)) {
                    return false;
                }
            }
            
            // 如果是共享组模式
            if (groupName != null) {
                // 检查单位是否被其他共享组占用
                for (String otherGroupName : sharedGroups.keys()) {
                    if (otherGroupName == null || otherGroupName.equals(groupName)) continue;
                    
                    UnitGroupInfo info = sharedGroups.get(otherGroupName);
                    if (info != null && info.units.contains(unit)) {
                        return false;
                    }
                }
                
                // 对于共享组，单位可以被组内任何处理器控制，或者未被控制
                // 检查单位控制器
                if (unit.controller() instanceof LogicAI) {
                    LogicAI logicAI = (LogicAI)unit.controller();
                    Building unitController = logicAI.controller;
                    
                    // 如果单位未被控制或者被组内处理器控制，则可用
                    if (unitController == null) {
                        return true;
                    }
                    
                    // 检查单位控制器是否属于同一组
                    String controllerGroupName = buildingToGroupName.get(unitController);
                    return controllerGroupName != null && controllerGroupName.equals(groupName);
                } else if (unit.controller() instanceof Building) {
                    Building unitController = (Building)unit.controller();
                    
                    // 检查单位控制器是否属于同一组
                    String controllerGroupName = buildingToGroupName.get(unitController);
                    return controllerGroupName != null && controllerGroupName.equals(groupName);
                } else {
                    // 单位未被控制，可用
                    return true;
                }
            } else {
                // 独立模式：单位必须未被控制或仅被当前控制器控制
                if (unit.controller() instanceof LogicAI) {
                    LogicAI logicAI = (LogicAI)unit.controller();
                    return logicAI.controller == null || logicAI.controller == controller;
                } else if (unit.controller() instanceof Building) {
                    return ((Building)unit.controller()) == controller;
                } else {
                    // 检查单位是否已被当前控制器的独立单位池包含
                    UnitGroupInfo info = individualGroups.get(controller);
                    if (info != null && info.units.contains(unit)) {
                        return true;
                    }
                    // 单位未被控制，可用
                    return true;
                }
            }
        }
        
        // 检查单位是否有效且未被其他处理器控制
        private boolean isValidAndNotControlled(Unit unit, Building controller) {
            if (!unit.isValid() || unit.team != controller.team) return false;

            // 检查单位是否死亡
            if (unit.dead) return false;

            // 检查单位是否被玩家控制
            if (unit.isPlayer()) return false;

            // 检查单位是否被其他处理器控制
            // 关键修复：确保正确识别单位是否被其他LogicAI控制器控制
            if (unit.controller() instanceof LogicAI) {
                LogicAI logicAI = (LogicAI)unit.controller();
                // 只有当单位被当前控制器控制或未被任何控制器控制时才返回true
                return logicAI.controller == controller;
            }

            // 处理Building控制器的情况
            if (unit.controller() instanceof Building) {
                Building controllingBuilding = (Building)unit.controller();
                return controllingBuilding == controller;
            }

            // 单位未被控制，可供当前控制器使用
            return true;
        }
        
        // 锁定单位，与ucontrol within指令效果相似
        private void lockUnit(Unit unit, Building controller) {
            // 添加多层安全检查，防止任何可能的空指针或无效状态
            if (unit == null || !unit.isValid() || controller == null || !controller.isValid()) return;
            
            // 设置单位的控制器为当前处理器，与ucontrol指令效果一致
            // 使用LogicAI来控制单位，而不是直接使用Building
            try {
                LogicAI logicAI;
                if(unit.controller() instanceof LogicAI la){
                    // 更新现有LogicAI的控制器
                    logicAI = la;
                    logicAI.controller = controller;
                }else{
                    // 创建新的LogicAI控制器
                    logicAI = new LogicAI();
                    logicAI.controller = controller;
                    
                    unit.controller(logicAI);
                    //clear old state
                    unit.mineTile = null;
                    unit.clearBuilding();
                }
                
                // 设置单位的控制目标为处理器位置，模拟within区域锁定效果
                // 添加双重安全检查：先检查isCommandable()，然后在调用command()时使用try-catch
                if(unit.isCommandable()){
                    try {
                        // 再次检查单位有效性，防止状态变化
                        if(unit.isValid() && unit.isCommandable()){
                            CommandAI ai = unit.command();
                            if(ai != null){
                                ai.commandPosition(new Vec2(controller.x, controller.y));
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        // 捕获可能的"Unit cannot be commanded"异常，安全地忽略
                    }
                }
            } catch (Exception e) {
                // 捕获所有可能的异常，确保MOD不会崩溃
            }
        }
        
        // 清理无效控制器的资源
        private void cleanupInvalidController(Building controller) {
            if (controller == null) return;
            
            // 清理独立组
            individualGroups.remove(controller);
            
            // 清理共享组关联
            String groupName = buildingToGroupName.get(controller);
            if (groupName != null) {
                buildingToGroupName.remove(controller);
                // 检查该组是否还有其他处理器使用
                cleanupUnusedGroup(groupName);
            }
            
            // 移除参数缓存
            paramCaches.remove(controller);
        }
        
        // 检查并更新参数缓存，返回参数是否发生变化
        private boolean checkAndUpdateParams(Building controller, Object unitType, int count, String groupName) {
            ParamCache cache = paramCaches.get(controller);
            
            if (cache == null) {
                // 第一次执行，创建缓存并更新参数
                cache = new ParamCache();
                cache.update(unitType, count, groupName);
                paramCaches.put(controller, cache);
                return true; // 参数变化（从无到有）
            } else {
                boolean changed = cache.hasChanged(unitType, count, groupName);
                if (changed) {
                    cache.update(unitType, count, groupName);
                    // 解绑单位池中的所有单位
                    unbindAllUnits(controller, groupName);
                }
                return changed;
            }
        }
        
        // 解绑控制器关联的所有单位
        private void unbindAllUnits(Building controller, String groupName) {
            // 解绑单个组的单位
            UnitGroupInfo info = individualGroups.get(controller);
            if (info != null && info.units != null) {
                for (Unit unit : info.units) {
                    if (unit != null && unit.isValid()) {
                        unlockUnit(unit, controller);
                    }
                }
                info.units.clear();
                info.currentIndex = -1;
            }
            
            // 如果有共享组，也解绑共享组中与此控制器相关的单位
            if (groupName != null) {
                UnitGroupInfo sharedInfo = sharedGroups.get(groupName);
                if (sharedInfo != null && sharedInfo.units != null) {
                    // 创建要移除的单位列表
                    Seq<Unit> toRemove = new Seq<>();
                    for (Unit unit : sharedInfo.units) {
                        if (unit != null && unit.isValid() && isUnitControlledBy(controller, unit)) {
                            unlockUnit(unit, controller);
                            toRemove.add(unit);
                        }
                    }
                    // 从共享组中移除这些单位
                    for (Unit unit : toRemove) {
                        sharedInfo.units.remove(unit);
                    }
                }
            }
        }
        
        // 解锁单位，取消控制器的控制
        private void unlockUnit(Unit unit, Building controller) {
            // 这里可以添加释放单位控制的逻辑
            // 例如移除单位上的控制标记或引用
        }
        
        // 定期清理内存和未使用的组
        private void cleanupMemoryAndUnusedGroups() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCleanupTime < CLEANUP_INTERVAL) {
                return; // 未到清理时间
            }
            
            lastCleanupTime = currentTime;
            
            // 清理无效的独立组控制器
            Seq<Building> invalidControllers = new Seq<>();
            for (Building controller : individualGroups.keys()) {
                if (controller == null || !controller.isValid()) {
                    invalidControllers.add(controller);
                }
            }
            for (Building controller : invalidControllers) {
                // 使用cleanupInvalidController来同时清理各种映射
                cleanupInvalidController(controller);
            }
            
            // 清理无效的共享组关联和参数缓存
            invalidControllers.clear();
            for (Building controller : buildingToGroupName.keys()) {
                if (controller == null || !controller.isValid()) {
                    invalidControllers.add(controller);
                }
            }
            for (Building controller : invalidControllers) {
                String groupName = buildingToGroupName.get(controller);
                buildingToGroupName.remove(controller);
                // 同时移除对应的参数缓存
                paramCaches.remove(controller);
                if (groupName != null) {
                    cleanupUnusedGroup(groupName);
                }
            }
            
            // 清理没有对应参数缓存的控制器
            invalidControllers.clear();
            for (Building controller : paramCaches.keys()) {
                if (controller == null || !controller.isValid()) {
                    invalidControllers.add(controller);
                }
            }
            for (Building controller : invalidControllers) {
                paramCaches.remove(controller);
            }
        }
        
        private void cleanupUnusedGroup(String groupName) {
            if (groupName == null) return;
            
            // 检查是否还有任何处理器使用该组
            boolean isUsed = false;
            for (String name : buildingToGroupName.values()) {
                if (groupName.equals(name)) {
                    isUsed = true;
                    break;
                }
            }
            
            // 如果没有处理器使用该组，则清理
            if (!isUsed) {
                sharedGroups.remove(groupName);
                sharedGroupMaxCounts.remove(groupName);
                sharedGroupConfigs.remove(groupName);
            }
        }
        
        private void executeMode2(LExecutor exec, String groupNameStr) {
            try {
                // 模式2：直接访问共享组内的单位，无需抓取
                UnitGroupInfo info = sharedGroups.get(groupNameStr);
                if (info != null) {
                    if (!info.units.isEmpty()) {
                        // 获取当前单位
                        Unit currentUnit = info.units.get(info.currentIndex);
                        if (currentUnit != null && currentUnit.isValid()) {
                            unitVar.setobj(currentUnit);
                            if (indexVar != null) {
                                indexVar.setnum(info.currentIndex + 1); // 从1开始计数
                            }
                            return;
                        } else {
                            // 单位无效或不存在
                            String noValidUnitError = Core.bundle.get("ubindgroup.error.no_valid_unit", "没有有效单位");
                            unitVar.setobj(noValidUnitError);
                            if (indexVar != null) {
                                indexVar.setobj(noValidUnitError);
                            }
                        }
                    } else {
                        // 组内无单位
                        String emptyGroupError = Core.bundle.get("ubindgroup.error.empty_group", "组内无单位");
                        unitVar.setobj(emptyGroupError);
                        if (indexVar != null) {
                            indexVar.setobj(emptyGroupError);
                        }
                    }
                } else {
                    // 共享组不存在
                    String groupNotExistError = Core.bundle.get("ubindgroup.error.group_not_exist", "共享组不存在");
                    unitVar.setobj(groupNotExistError);
                    if (indexVar != null) {
                        indexVar.setobj(groupNotExistError);
                    }
                }
            } catch (Exception e) {
                // 捕获所有异常
                String exceptionError = Core.bundle.get("ubindgroup.error.exception", "执行异常");
                unitVar.setobj(exceptionError);
                if (indexVar != null) {
                    indexVar.setobj(exceptionError);
                }
            }
        }
    }
    
    // 单位组信息类
    private static class UnitGroupInfo {
        public Seq<Unit> units = new Seq<>();      // 单位列表
        public int currentIndex = -1;              // 当前单位索引
        public int mode = 1;                       // 模式
    }
}