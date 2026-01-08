package world.blocks.units.MultiUnitFactory;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.units.*;
import mindustry.world.meta.*;

public class MultiUnitFactory extends UnitFactory {
    public boolean supportSubspace = true;
    public int subspaceSize = 15;
    public Seq<SubspaceDesign> subspaceDesigns = new Seq<>();

    public MultiUnitFactory(String name) {
        super(name);
        update = true;
        hasPower = true;
        hasItems = true;
        solid = true;
        configurable = true;
        clearOnDoubleTap = true;
        outputsPayload = true;
        rotate = true;
        regionRotated1 = 1;
        commandable = true;
        ambientSound = Sounds.respawning;
        health = 1000;
        size = 3;
        // 设置建筑类别和可见性
        category = Category.units;
        buildVisibility = BuildVisibility.shown;
        // 设置建造成本
        requirements(Category.units, BuildVisibility.shown, ItemStack.with(
            Items.copper, 1000,
            Items.lead, 800,
            Items.silicon, 500
        ));
        // 设置显示属性
        localizedName = "Multi Unit Factory";
        description = "Creates custom units by combining buildings in subspace.";
        // 注意：在Mindustry中，建筑图标通过纹理文件自动加载，不需要手动设置
        // 确保建筑ID与纹理文件名称匹配即可

        // 添加支持亚空间设计生产的配置
        config(Boolean.class, (MultiUnitFactoryBuild build, Boolean useSubspace) -> {
            build.useSubspaceDesign = useSubspace;
            build.selectedDesign = -1;
            build.progress = 0;
        });

        config(Integer.class, (MultiUnitFactoryBuild build, Integer i) -> {
            if (!configurable) return;

            if (build.useSubspaceDesign) {
                if (build.selectedDesign == i) return;
                build.selectedDesign = i < 0 || i >= subspaceDesigns.size ? -1 : i;
            } else {
                if (build.currentPlan == i) return;
                build.currentPlan = i < 0 || i >= plans.size ? -1 : i;
            }
            build.progress = 0;
        });
    }

    public static class SubspaceDesign {
        public String name;
        public Schematic schematic; // 保存的建筑布局
        public float health; // 计算出的总健康值
        public float speed; // 计算出的移动速度
        public float size; // 计算出的单位大小
        public float time; // 生产所需时间
        public ItemStack[] requirements; // 生产所需资源
        
        // 新增属性
        public int itemCapacity; // 物品存储容量
        public float liquidCapacity; // 液体存储容量
        public float powerProduction; // 电力生产能力
        public float powerConsumption; // 电力消耗
        public int weaponCount; // 武器数量
        public float productionCapacity; // 生产能力

        public SubspaceDesign() {
        }

        public SubspaceDesign(String name, Schematic schematic, float health, float speed, float size, float time, ItemStack[] requirements) {
            this.name = name;
            this.schematic = schematic;
            this.health = health;
            this.speed = speed;
            this.size = size;
            this.time = time;
            this.requirements = requirements;
            
            // 初始化新增属性
            this.itemCapacity = 0;
            this.liquidCapacity = 0f;
            this.powerProduction = 0f;
            this.powerConsumption = 0f;
            this.weaponCount = 0;
            this.productionCapacity = 0f;
        }
    }

    public void enterSubspace(MultiUnitFactoryBuild build) {
        // 实现进入亚空间编辑模式的逻辑
        Core.app.post(() -> {
            // 创建并显示亚空间编辑器
            SubspaceEditor editor = new SubspaceEditor(this, build);
            editor.show();
        });
    }

    public void saveSubspaceDesign(String name, Schematic schematic) {
        // 计算设计的各项属性
        SubspaceDesign design = calculateDesignStats(schematic);
        design.name = name;
        subspaceDesigns.add(design);
    }

    public void loadSubspaceDesign(int index) {
        // 实现加载亚空间设计的逻辑
    }

    public SubspaceDesign calculateDesignStats(Schematic schematic) {
        SubspaceDesign design = new SubspaceDesign();
        design.schematic = schematic;
        
        // 初始化统计变量
        float totalHealth = 0f;
        float totalWeight = 0f;
        float totalBuildTime = 0f;
        int totalItemCapacity = 0;
        float totalLiquidCapacity = 0f;
        float totalPowerProduction = 0f;
        float totalPowerConsumption = 0f;
        int weaponCount = 0;
        float totalProductionCapacity = 0f;
        
        // 遍历所有建筑，计算各项属性
        for (Schematic.Stile stile : schematic.tiles) {
            Block block = stile.block;
            
            // 计算健康值
            totalHealth += block.health;
            
            // 计算重量（用于速度惩罚）
            totalWeight += block.size * block.health;
            
            // 计算建造时间
            totalBuildTime += block.buildTime;
            
            // 计算物品存储容量
            if (block.hasItems) {
                totalItemCapacity += block.itemCapacity;
            }
            
            // 计算液体存储容量
            if (block.hasLiquids) {
                totalLiquidCapacity += block.liquidCapacity;
            }
            
            // 计算电力生产和消耗
            if (block.outputsPower) {
                // 检查是否有powerProduction属性
                try {
                    java.lang.reflect.Field field = block.getClass().getField("powerProduction");
                    totalPowerProduction += field.getFloat(block);
                } catch (Exception e) {
                    // 忽略没有powerProduction属性的建筑
                }
            }
            if (block.consumesPower) {
                // 检查是否有powerConsumption属性
                try {
                    java.lang.reflect.Field field = block.getClass().getField("powerConsumption");
                    totalPowerConsumption += field.getFloat(block);
                } catch (Exception e) {
                    // 忽略没有powerConsumption属性的建筑
                }
            }
            
            // 计算武器数量
            if (block instanceof mindustry.world.blocks.defense.turrets.Turret) {
                weaponCount++;
            }
            
            // 计算生产能力（基于建筑大小和类型）
            if (block instanceof mindustry.world.blocks.production.GenericCrafter) {
                totalProductionCapacity += block.size * 10f;
            }
        }
        
        // 设置设计属性
        // 健康值：所有建筑健康值的总和，设置上限为10000
        design.health = Math.min(totalHealth, 10000f);
        
        // 速度：基础速度为1.0f，根据建筑总重量计算惩罚
        float speedPenalty = totalWeight / 10000f;
        design.speed = Math.max(1.0f - speedPenalty, 0.3f);
        
        // 大小：基于建筑占用的总面积计算
        design.size = Math.max(schematic.width, schematic.height) / 10f;
        
        // 生产时间：所有组件建筑建造时间总和 × 1.5（平衡系数）
        design.time = totalBuildTime * 1.5f;
        
        // 物品存储容量
        design.itemCapacity = totalItemCapacity;
        
        // 液体存储容量
        design.liquidCapacity = totalLiquidCapacity;
        
        // 电力生产和消耗
        design.powerProduction = totalPowerProduction;
        design.powerConsumption = totalPowerConsumption;
        
        // 武器数量（限制最多10个）
        design.weaponCount = Math.min(weaponCount, 10);
        
        // 生产能力
        design.productionCapacity = totalProductionCapacity;
        
        // 计算资源需求：所有组件建筑成本总和 × 1.2（平衡系数）
        ItemSeq requirements = schematic.requirements();
        Seq<ItemStack> stackList = new Seq<>();
        requirements.each((item, amount) -> {
            stackList.add(new ItemStack(item, Math.round(amount * 1.2f)));
        });
        design.requirements = stackList.toArray(ItemStack.class);
        
        return design;
    }

    public class MultiUnitFactoryBuild extends UnitFactoryBuild {
        public boolean useSubspaceDesign = false;
        public int selectedDesign = -1;

        @Override
        public boolean shouldConsume() {
            if (useSubspaceDesign) {
                return enabled && payload == null && selectedDesign != -1;
            } else {
                return super.shouldConsume();
            }
        }

        @Override
        public void updateTile() {
            if (!configurable) {
                currentPlan = 0;
                useSubspaceDesign = false;
            }

            if (efficiency > 0) {
                if (useSubspaceDesign) {
                    if (selectedDesign != -1 && selectedDesign < ((MultiUnitFactory) block).subspaceDesigns.size) {
                        SubspaceDesign design = ((MultiUnitFactory) block).subspaceDesigns.get(selectedDesign);
                        
                        // 检查资源是否充足
                        boolean canProduce = true;
                        for (ItemStack stack : design.requirements) {
                            if (items.get(stack.item) < stack.amount) {
                                canProduce = false;
                                break;
                            }
                        }
                        
                        if (canProduce) {
                            // 更新生产进度
                            time += edelta() * speedScl * Vars.state.rules.unitBuildSpeed(team);
                            progress += edelta() * Vars.state.rules.unitBuildSpeed(team);
                            speedScl = arc.math.Mathf.lerpDelta(speedScl, 1f, 0.05f);
                        } else {
                            // 资源不足，降低生产速度
                            speedScl = arc.math.Mathf.lerpDelta(speedScl, 0.5f, 0.05f);
                        }
                    }
                } else {
                    super.updateTile();
                    return;
                }
            } else {
                speedScl = arc.math.Mathf.lerpDelta(speedScl, 0f, 0.05f);
            }

            moveOutPayload();

            if (useSubspaceDesign && selectedDesign != -1 && selectedDesign < ((MultiUnitFactory) block).subspaceDesigns.size && payload == null) {
                SubspaceDesign design = ((MultiUnitFactory) block).subspaceDesigns.get(selectedDesign);

                if (progress >= design.time) {
                    // 检查资源是否充足
                    boolean canProduce = true;
                    for (ItemStack stack : design.requirements) {
                        if (items.get(stack.item) < stack.amount) {
                            canProduce = false;
                            break;
                        }
                    }
                    
                    if (canProduce) {
                        // 消耗资源
                        for (ItemStack stack : design.requirements) {
                            items.remove(stack.item, stack.amount);
                        }
                        
                        // 创建复合单位
                        Unit unit = createCompositeUnit(design);
                        payload = new UnitPayload(unit);
                        payVector.setZero();
                        
                        // 重置进度
                        progress %= 1f;
                    }
                }

                progress = arc.math.Mathf.clamp(progress, 0, design.time);
            }
        }

        private Unit createCompositeUnit(SubspaceDesign design) {
            // 基于设计的schematic创建一个具有所有建筑功能的单位
            Unit unit = UnitTypes.dagger.create(team);
            
            // 设置复合单位的基础属性
            unit.maxHealth(design.health);
            unit.health(design.health);
            unit.hitSize(design.size * 8f);
            // Unit.speed()是获取速度的方法，不是设置速度的方法，移除这个调用
            
            // 为单位添加BlockUnitComp组件，实现建筑-单位转换
            try {
                // 使用反射获取add方法，添加BlockUnitComp组件
                java.lang.reflect.Method addMethod = unit.getClass().getMethod("add", java.lang.Object.class);
                
                // 创建BlockUnitComp实例
                Object blockUnitComp = Class.forName("mindustry.entities.comp.BlockUnitComp").newInstance();
                
                // 设置BlockUnitComp的schematic属性
                java.lang.reflect.Field schematicField = blockUnitComp.getClass().getField("schematic");
                schematicField.set(blockUnitComp, design.schematic);
                
                // 添加BlockUnitComp组件到单位
                addMethod.invoke(unit, blockUnitComp);
                
                // Unit对象没有getTeam()方法，直接使用team变量
                // 单位创建后会自动添加到团队数据中，不需要手动添加
                
            } catch (Exception e) {
                // 如果反射失败，使用默认实现
                // 记录错误信息
                arc.util.Log.err("Failed to add BlockUnitComp to unit: " + e.getMessage());
            }
            
            return unit;
        }

        @Override
        public void buildConfiguration(Table table) {
            // 添加亚空间模式切换开关
            table.add(new CheckBox("Use Subspace Design") {{ 
                changed(() -> {
                    useSubspaceDesign = isChecked();
                    configure(useSubspaceDesign);
                });
                setChecked(useSubspaceDesign);
            }}).left().colspan(4).row();
            
            if (useSubspaceDesign) {
                // 亚空间模式UI
                table.button("Enter Subspace Editor", Icon.pencil, () -> {
                    // 进入亚空间编辑模式
                    ((MultiUnitFactory) block).enterSubspace(this);
                }).size(200, 60).colspan(4).row();
                
                // 显示已保存的设计列表
                if (((MultiUnitFactory) block).subspaceDesigns.size > 0) {
                    table.label(() -> "Saved Designs:").left().colspan(4).row();
                    
                    Table designs = new Table();
                    designs.defaults().size(120, 40);
                    
                    for (int i = 0; i < ((MultiUnitFactory) block).subspaceDesigns.size; i++) {
                        SubspaceDesign design = ((MultiUnitFactory) block).subspaceDesigns.get(i);
                        int finalI = i;
                        
                        // 设计选择按钮
                        Table designRow = new Table();
                        designRow.button(design.name, () -> {
                            configure(finalI);
                        }).size(120, 40);
                        
                        // 设计管理按钮
                        designRow.button(Icon.edit, () -> {
                            // 重命名设计
                            showRenameDialog(finalI, design.name);
                        }).size(30, 30).padLeft(5);
                        
                        designRow.button(Icon.trash, () -> {
                            // 删除设计
                            showDeleteDialog(finalI);
                        }).size(30, 30).padLeft(5);
                        
                        designs.add(designRow);
                        
                        if ((i + 1) % 2 == 0) {
                            designs.row();
                        }
                    }
                    
                    table.add(designs).colspan(4).row();
                }
            } else {
                // 普通单位工厂模式，使用原有逻辑
                super.buildConfiguration(table);
            }
        }
        
        @Override
        public void display(Table table) {
            super.display(table);

            table.row();
            table.table(t -> {
                t.left();
                t.add(new CheckBox("Use Subspace Design") {{ 
                    changed(() -> {
                        useSubspaceDesign = isChecked();
                        configure(useSubspaceDesign);
                    });
                    setChecked(useSubspaceDesign);
                }}).left().padRight(10);
                if (useSubspaceDesign) {
                    t.label(() -> selectedDesign == -1 ? "No Design Selected" : ((MultiUnitFactory) block).subspaceDesigns.get(selectedDesign).name).color(Color.lightGray).left();
                }
            }).left();
        }

        /**
         * 显示重命名设计对话框
         * @param index 设计索引
         * @param oldName 旧名称
         */
        private void showRenameDialog(int index, String oldName) {
            BaseDialog dialog = new BaseDialog("Rename Design");
            TextField nameField = dialog.cont.field(oldName, text -> {}).width(300).get();
            
            dialog.cont.row();
            dialog.cont.button("Rename", () -> {
                String newName = nameField.getText().trim();
                if (!newName.isEmpty() && !newName.equals(oldName)) {
                    // 重命名设计
                    ((MultiUnitFactory) block).subspaceDesigns.get(index).name = newName;
                    // 移除错误的UI刷新调用
                }
                dialog.hide();
            }).size(120, 50).pad(10);
            dialog.cont.button("Cancel", dialog::hide).size(120, 50).pad(10);
            
            dialog.show();
        }
        
        /**
         * 显示删除设计对话框
         * @param index 设计索引
         */
        private void showDeleteDialog(int index) {
            BaseDialog dialog = new BaseDialog("Delete Design");
            dialog.cont.add("Are you sure you want to delete this design?").row();
            
            dialog.cont.button("Delete", () -> {
                // 删除设计
                ((MultiUnitFactory) block).subspaceDesigns.remove(index);
                // 如果当前选中的是被删除的设计，重置选择
                if (selectedDesign == index) {
                    selectedDesign = -1;
                } else if (selectedDesign > index) {
                    // 如果当前选中的设计索引大于被删除的索引，调整索引
                    selectedDesign--;
                }
                // 移除错误的UI刷新调用
                dialog.hide();
            }).size(120, 50).pad(10);
            dialog.cont.button("Cancel", dialog::hide).size(120, 50).pad(10);
            
            dialog.show();
        }
        
        @Override
        public Object config() {
            if (useSubspaceDesign) {
                return selectedDesign;
            } else {
                return currentPlan;
            }
        }
    }
}