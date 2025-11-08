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
import mindustry.gen.Icon;
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
    
    // 单位组信息类
    public static class UnitGroupInfo {
        public Seq<Unit> units = new Seq<>();      // 单位列表
        public int currentIndex = -1;              // 当前单位索引
        public int mode = 1;                       // 模式
        public long lastAccessTime = System.currentTimeMillis(); // 最后访问时间，用于清理机制
    }
    
    // 存储按控制器独立的单位组和当前索引
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
    
    // 统一参数更新方法 - 将所有参数更新到指定控制器的参数缓存中
    // 此方法已移至类顶层以解决作用域问题
    static void updateAllParams(Building controller, Object unitTypeObj, int countVal, String groupNameStr, int mode) {
        ParamCache cache = paramCaches.get(controller, ParamCache::new);
        // 确保模式值有效，默认使用模式1
        int actualMode = mode;
        if (actualMode == 0) actualMode = 1; // 默认模式1
        cache.update(unitTypeObj, countVal, groupNameStr, actualMode);
    }
    
    // 共享组配置类，用于存储共享组的初始参数
    public static class GroupConfig {
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
    public static class ParamCache {
        public Object unitType;
        public int count;
        public String groupName;
        public int mode; // 添加模式字段
        public String unitVar;
        public String indexVar;
        public long lastAccessTime;

        public boolean hasChanged(Object newUnitType, int newCount, String newGroupName, int newMode) {
            return !Objects.equals(unitType, newUnitType) || 
                   count != newCount || 
                   !Objects.equals(groupName, newGroupName) ||
                   mode != newMode;
        }
        
        public void update(Object newUnitType, int newCount, String newGroupName, int newMode) {
            this.unitType = newUnitType;
            this.count = newCount;
            this.groupName = newGroupName;
            this.mode = newMode;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        // 更新所有参数，包括unitVar和indexVar
        public void update(Object newUnitType, int newCount, String newGroupName, int newMode, String newUnitVar, String newIndexVar) {
            update(newUnitType, newCount, newGroupName, newMode);
            this.unitVar = newUnitVar;
            this.indexVar = newIndexVar;
        }
    }
    
    private static long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL = 60 * 1000; // 每分钟清理一次
    
    public static class UnitBindGroupStatement extends LStatement {
        public String unitType = "@poly", count = "10", unitVar = "currentUnit", indexVar = "unitIndex", groupName = null;
        public int mode = 1; // 1: 正常抓取逻辑，2: 共享组内单位无需抓取
        
        @Override
        public void build(Table table) {
            rebuild(table);
        }
        
        private void rebuild(Table table) {
            table.clearChildren();
            table.left();
            
            // 第一排：模式选择和单位类型参数
            table.table(t -> {
                t.setColor(table.color);
                
                t.add("mode:").left();
                modeButton(t, table);
                
                // 单位类型参数（模式1显示）
                if (mode == 1) {
                    table.add(Core.bundle.get("ubindgroup.param.unitType", "type")).padLeft(10).left().self(this::param);
                    table.field(unitType, Styles.nodeField, s -> unitType = sanitize(s))
                        .size(144f, 40f).pad(2f).color(table.color)
                        .width(85f).padRight(10).left();
                    table.button(Icon.pencilSmall, Styles.nodei, () -> showUnitTypeSelect(table))
                        .size(40f).color(table.color);
                }
            }).left();
            
            table.row();
            
            // 第二排：数量、变量名和组名称参数
            table.table(t -> {
                t.setColor(table.color);
                
                // 数量参数（模式1显示）
                if (mode == 1) {
                    table.add(Core.bundle.get("ubindgroup.param.count", "count")).padLeft(10).left().self(this::param);
                    table.field(count, Styles.nodeField, s -> count = sanitize(s))
                        .size(144f, 40f).pad(2f).color(table.color)
                        .width(85f).padRight(10).left();
                }
                
                // 单位变量参数
                table.add(Core.bundle.get("ubindgroup.param.var", "unitVar")).padLeft(10).left().self(this::param);
                table.field(unitVar, Styles.nodeField, s -> unitVar = sanitize(s))
                    .size(144f, 40f).pad(2f).color(table.color)
                    .width(85f).padRight(10).left();
                
                // 索引变量参数
                table.add(Core.bundle.get("ubindgroup.param.index", "indexVar")).padLeft(10).left().self(this::param);
                table.field(indexVar, Styles.nodeField, s -> indexVar = sanitize(s))
                    .size(144f, 40f).pad(2f).color(table.color)
                    .width(85f).padRight(10).left();
                
                // 组名称参数
                table.add(Core.bundle.get("ubindgroup.param.group", "groupName")).padLeft(10).left().self(this::param);
                table.field(groupName != null ? groupName : "null", Styles.nodeField, s -> groupName = sanitize(s).isEmpty() ? null : sanitize(s))
                    .size(144f, 40f).pad(2f).color(table.color)
                    .width(85f).padRight(10).left();
            }).left();
        }
        
        void modeButton(Table table, Table parent) {
            table.button(mode == 1 ? Core.bundle.get("ubindgroup.mode.capture", "抓取模式") : Core.bundle.get("ubindgroup.mode.access", "访问模式"), Styles.defaultt, () -> {
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
        }
        
        void showUnitTypeSelect(Table table) {
            table.button(unitType.toString(), Styles.defaultt, () -> {
                BaseDialog dialog = new BaseDialog(Core.bundle.get("ubindgroup.unittype.select", "选择单位类型"));
                dialog.cont.pane(selectTable -> {
                    selectTable.left();
                    selectTable.defaults().size(40f).pad(2f);
                    int c = 0;
                    for(UnitType item : Vars.content.units()){
                        if(!item.unlockedNow() || item.isHidden() || !item.logicControllable) continue;
                        selectTable.button(new TextureRegionDrawable(item.uiIcon), Styles.clearNoneTogglei, () -> {
                            unitType = "@" + item.name;
                            rebuild(table);
                            dialog.hide();
                        }).tooltip(item.localizedName);

                        if(++c % 6 == 0) selectTable.row();
                    }
                }).maxHeight(300f).width(240f);
                dialog.addCloseButton();
                dialog.show();
            });
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
        

        
        // 统一参数检查方法，检查所有参数变化
        private static boolean checkAllParamsChanged(Building controller, Object unitTypeObj, int countVal, String groupNameStr, int mode) {
            // 获取参数缓存
            ParamCache cache = paramCaches.get(controller, ParamCache::new);
            
            // 检查所有参数是否变化（包括单位类型、数量、组名和模式）
            return cache.hasChanged(unitTypeObj, countVal, groupNameStr, mode);
        }
        
        // 统一参数更新方法，更新所有参数
        // 注意：这个方法已经移到LUnitBindGroup类顶层
        
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
            // 开始执行
            
            // 控制器有效性检查
            Building controller = exec.build;
            if (controller == null || !controller.isValid()) {
                // 无效 → 清理资源 → 结束
                cleanupInvalidController(controller);
                return;
            }
            
            // 获取并处理组名称
            String groupNameStr = groupName == null ? null : (String)groupName.obj();
            if (groupNameStr != null && groupNameStr.equals("null")) {
                groupNameStr = null;
            }
            
            // 模式判断
            if (mode == 1) {
                // 模式1：抓取模式（管理单位）
                executeMode1(exec, controller, groupNameStr);
            } else if (mode == 2) {
                // 模式2：访问模式（使用单位）
                executeMode2(exec, unitVar, indexVar, groupNameStr);
            }
        }
        
        private void executeMode1(LExecutor exec, Building controller, String groupNameStr) {
            // 模式1：单位控制模式 - 核心功能模式，负责单位的抓取、绑定和管理
            
            // 组名指定判断
            boolean hasGroupName = groupNameStr != null && !groupNameStr.isEmpty();
            
            if (hasGroupName) {
                // 是 → 检查组名使用情况
                boolean contains = false;
                for (ObjectMap.Entry<Building, String> entry : buildingToGroupName.entries()) {
                    // 检查：1. 值不为空 2. 值等于当前组名 3. 键不是当前控制器（忽略自己使用的组名）
                    if (entry.value != null && entry.value.equals(groupNameStr) && entry.key != controller) {
                        contains = true;
                        break;
                    }
                }
                if (contains) {
                    // 已被其他处理器使用 → 设置错误 → 结束
                    String groupConflictError = Core.bundle.get("ubindgroup.error.group_conflict", "组名已被使用");
                    unitVar.setobj(groupConflictError);
                    if (indexVar != null) {
                        indexVar.setobj(groupConflictError);
                    }
                    return;
                }
                // 未被其他处理器使用或自己已在使用 → 使用共享组
            } else {
                // 否 → 使用独立组
            }
            
            // 获取单位参数
            Object unitTypeObj = unitType.obj();
            int countVal = (int)count.num();
            
            // 参数变化检查（在更新组单位前）
            boolean paramsChanged = false;
            ParamCache cache = paramCaches.get(controller, ParamCache::new);
            
            // 检查单位类型变化
            if (!Objects.equals(cache.unitType, unitTypeObj)) {
                paramsChanged = true;
            }
            // 检查单位数量变化
            if (cache.count != countVal) {
                paramsChanged = true;
            }
            // 检查模式变化
            if (cache.mode != mode) {
                paramsChanged = true;
            }
            // 检查组名变化
            if (!Objects.equals(cache.groupName, groupNameStr)) {
                paramsChanged = true;
                // 组名有变化 → 清理旧组名关联
                if (cache.groupName != null && !cache.groupName.isEmpty()) {
                    buildingToGroupName.remove(controller);
                    cleanupUnusedGroup(cache.groupName);
                }
            }
            
            // 有变化 → 重新开始（清理单位池和缓存）
            if (paramsChanged) {
                // 清理单位池和缓存
                unbindAllUnits(controller, groupNameStr);
            }
            
            // 更新参数缓存
            cache.update(unitTypeObj, countVal, groupNameStr, mode);
            
            // 参数未变化且已有单位组 → 使用缓存单位组
            if (!paramsChanged) {
                UnitGroupInfo groupInfo = hasGroupName ? sharedGroups.get(groupNameStr) : individualGroups.get(controller);
                
                if (groupInfo != null && !groupInfo.units.isEmpty()) {
                    // 更新单位绑定
                    groupInfo.currentIndex = (groupInfo.currentIndex + 1) % groupInfo.units.size;
                    Unit unit = groupInfo.units.get(groupInfo.currentIndex);
                    unitVar.setobj(unit);
                    if (indexVar != null) {
                        indexVar.setnum(groupInfo.currentIndex + 1);
                    }
                } else {
                    unitVar.setobj(null);
                    if (indexVar != null) {
                        indexVar.setnum(0);
                    }
                }
                return;
            }
            
            // 单位数量检查
            if (countVal < 0) {
                // 否 → 设置错误 → 结束
                String countError = Core.bundle.get("ubindgroup.error.invalid_count", "单位数量无效");
                unitVar.setobj(countError);
                if (indexVar != null) {
                    indexVar.setobj(countError);
                }
                return;
            }
            // 是 → 更新组单位
            
            // 数量限制在合理范围内
            countVal = Math.min(100, countVal);
            
            // 更新组单位
            if (hasGroupName) {
                // 共享组 → 更新共享组单位并记录映射
                UnitGroupInfo sharedGroup = sharedGroups.get(groupNameStr, UnitGroupInfo::new);
                sharedGroup.mode = mode;
                sharedGroup.units.clear();
                sharedGroup.currentIndex = -1;
                
                // 更新共享组单位并记录映射
                updateUnitGroup(sharedGroup, unitTypeObj, countVal, exec.team, controller, groupNameStr, unitVar, indexVar);
                buildingToGroupName.put(controller, groupNameStr);
                
                // 更新共享组配置
                sharedGroupConfigs.put(groupNameStr, new GroupConfig(unitTypeObj, countVal, mode));
                
                // 更新单位绑定并返回
                if (!sharedGroup.units.isEmpty()) {
                    sharedGroup.currentIndex = 0;
                    Unit unit = sharedGroup.units.get(sharedGroup.currentIndex);
                    unitVar.setobj(unit);
                    if (indexVar != null) {
                        indexVar.setnum(sharedGroup.currentIndex + 1);
                    }
                } else {
                    String noUnitError = Core.bundle.get("ubindgroup.error.empty_group", "组内无单位");
                    unitVar.setobj(noUnitError);
                    if (indexVar != null) {
                        indexVar.setobj(noUnitError);
                    }
                }
            } else {
                // 独立组 → 更新独立组单位并移除映射
                UnitGroupInfo groupInfo = individualGroups.get(controller, UnitGroupInfo::new);
                groupInfo.mode = mode;
                groupInfo.units.clear();
                groupInfo.currentIndex = -1;
                
                // 更新独立组单位并移除映射
                updateUnitGroup(groupInfo, unitTypeObj, countVal, exec.team, controller, groupNameStr, unitVar, indexVar);
                buildingToGroupName.remove(controller);
                
                // 更新单位绑定并返回
                if (!groupInfo.units.isEmpty()) {
                    groupInfo.currentIndex = 0;
                    Unit unit = groupInfo.units.get(groupInfo.currentIndex);
                    unitVar.setobj(unit);
                    if (indexVar != null) {
                        indexVar.setnum(groupInfo.currentIndex + 1);
                    }
                } else {
                    String noUnitError = Core.bundle.get("ubindgroup.error.empty_group", "组内无单位");
                    unitVar.setobj(noUnitError);
                    if (indexVar != null) {
                        indexVar.setobj(noUnitError);
                    }
                }
            }
        }
        }
        
        // 存储每个共享组的最大count值（已在类顶部定义）
        
        // 上次清理时间，用于定期清理

        
        // 更新单位组 - 清理无效单位，添加新单位，维护单位组的有效状态
        private static void updateUnitGroup(UnitGroupInfo info, Object typeObj, int maxCount, Team team, Building controller, String groupName, LVar unitVar, LVar indexVar) {
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
                    if (unitVar != null) unitVar.setobj(noUnitError);
                    if (indexVar != null) indexVar.setobj(noUnitError);
                } else {
                    Unit unit = info.units.get(info.currentIndex);
                    if (unit != null && unit.isValid()) {
                        if (unitVar != null) unitVar.setobj(unit);
                        if (indexVar != null) indexVar.setnum(info.currentIndex + 1);
                    } else {
                        // 单位无效
                        String invalidUnitError = Core.bundle.get("ubindgroup.error.invalid_unit", "单位无效");
                        if (unitVar != null) unitVar.setobj(invalidUnitError);
                        if (indexVar != null) indexVar.setobj(invalidUnitError);
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
        private static boolean isUnitControlledBy(Building controller, Unit unit) {
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
        private static Seq<Unit> collectAvailableUnits(Object typeObj, Team team, Building controller, String groupName) {
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
        private static boolean isUnitAvailableForController(Unit unit, Building controller, String groupName) {
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
            
            // 如果共享组模式
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
        private static boolean isValidAndNotControlled(Unit unit, Building controller) {
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
        private static void lockUnit(Unit unit, Building controller) {
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
        private static void cleanupInvalidController(Building controller) {
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
        // 注意：参数变化检查和基本验证逻辑已移至executeMode1方法中
        private static boolean checkAndUpdateParams(Building controller, Object unitType, int count, String groupName, int mode, LVar unitVar, LVar indexVar) {
            // 获取参数缓存
            ParamCache cache = paramCaches.get(controller, ParamCache::new);
            
            // 检查是否需要重新执行
            boolean needRestart = false;
            
            // 检查单位类型、数量或模式是否变化
            if (!Objects.equals(cache.unitType, unitType) || cache.count != count || cache.mode != mode) {
                needRestart = true;
            }
            
            // 检查组名是否变化
            if (!Objects.equals(cache.groupName, groupName)) {
                // 组名变化，清理旧组名关联
                if (cache.groupName != null && !cache.groupName.isEmpty()) {
                    // 从映射中移除旧的关联
                    buildingToGroupName.remove(controller);
                }
                needRestart = true;
            }
            
            // 如果不需要重新执行，直接返回false
            if (!needRestart) {
                return false;
            }
            
            // 更新参数缓存
            cache.update(unitType, count, groupName, mode);
            
            // 组名相关检查
            if (mode == 1 && groupName != null) {
                if (sharedGroups.containsKey(groupName)) {
                    // 已被使用 → 设置错误 → 结束
                    String conflictError = Core.bundle.get("ubindgroup.error.group_conflict", "组名已被使用");
                    unitVar.setobj(conflictError);
                    if (indexVar != null) {
                        indexVar.setobj(conflictError);
                    }
                    return false;
                }
            }
            
            // 使用统一参数更新方法，更新所有参数
            updateAllParams(controller, unitType, count, groupName, mode);
            
            // 记录组配置
            if (groupName != null) {
                sharedGroupConfigs.put(groupName, new GroupConfig(unitType, count, mode));
            }
            
            // 通过检查 → 返回true
            return true;
        }
        
        // 解绑控制器关联的所有单位
        private static void unbindAllUnits(Building controller, String groupName) {
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
        private static void unlockUnit(Unit unit, Building controller) {
            if (unit != null) {
                // 释放单位控制，重置控制器，参考Mindustry游戏源码的unbind实现
                unit.resetController();
            }
        }
        
        // 定期清理内存和未使用的组
        private static void cleanupMemoryAndUnusedGroups() {
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
        
        private static void cleanupUnusedGroup(String groupName) {
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
        
        private static void executeMode2(LExecutor exec, LVar unitVar, LVar indexVar, String groupNameStr) {
            // 模式2：共享组访问模式 - 只读模式，用于访问已由模式1创建的共享组中的单位
            
            // 检查组名是否为null
            if (groupNameStr == null) {
                // 组名为空 → 设置错误 → 结束
                String groupNameNullError = Core.bundle.get("ubindgroup.error.group_name_null", "共享组名称不能为空");
                unitVar.setobj(groupNameNullError);
                if (indexVar != null) {
                    indexVar.setobj(groupNameNullError);
                }
                return;
            }
            
            // 共享组检查
            UnitGroupInfo info = sharedGroups.get(groupNameStr);
            if (info == null) {
                // 不存在 → 设置错误 → 结束
                String groupNotExistError = Core.bundle.get("ubindgroup.error.group_not_exist", "共享组不存在");
                unitVar.setobj(groupNotExistError);
                if (indexVar != null) {
                    indexVar.setobj(groupNotExistError);
                }
                return;
            }
            
            // 存在 → 设置单位变量和索引 → 结束
            // 获取当前单位
            Unit currentUnit = null;
            if (info.currentIndex >= 0 && info.currentIndex < info.units.size) {
                currentUnit = info.units.get(info.currentIndex);
            }
            
            if (currentUnit != null && currentUnit.isValid()) {
                unitVar.setobj(currentUnit);
                if (indexVar != null) {
                    indexVar.setnum(info.currentIndex + 1); // 从1开始计数
                }
            } else {
                // 尝试使用第一个有效单位
                if (!info.units.isEmpty()) {
                    for (Unit unit : info.units) {
                        if (unit != null && unit.isValid()) {
                            currentUnit = unit;
                            info.currentIndex = info.units.indexOf(unit);
                            break;
                        }
                    }
                }
                
                if (currentUnit != null) {
                    unitVar.setobj(currentUnit);
                    if (indexVar != null) {
                        indexVar.setnum(info.currentIndex + 1);
                    }
                } else {
                    // 没有有效单位时设置错误
                    String noValidUnitError = Core.bundle.get("ubindgroup.error.no_valid_unit", "没有有效单位");
                    unitVar.setobj(noValidUnitError);
                    if (indexVar != null) {
                        indexVar.setobj(noValidUnitError);
                    }
                }
            }
            
            // 更新最后访问时间
            info.lastAccessTime = System.currentTimeMillis();
        }
    }
