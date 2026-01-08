package world.blocks.units.MultiUnitFactory;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
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
        }
    }

    public void enterSubspace() {
        // 实现进入亚空间编辑模式的逻辑
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
        
        // 计算健康值：所有建筑健康值的总和，设置上限为10000
        design.health = Math.min(schematic.tiles.sumf(s -> s.block.health), 10000f);
        
        // 计算大小：基于建筑占用的总面积计算
        design.size = Math.max(schematic.width, schematic.height) / 10f;
        
        // 计算资源需求：所有组件建筑成本总和 × 1.2（平衡系数）
        ItemSeq requirements = schematic.requirements();
        Seq<ItemStack> stackList = new Seq<>();
        requirements.each((item, amount) -> {
            stackList.add(new ItemStack(item, Math.round(amount * 1.2f)));
        });
        design.requirements = stackList.toArray(ItemStack.class);
        
        // 计算生产时间：所有组件建筑建造时间总和 × 1.5（平衡系数）
        design.time = schematic.tiles.sumf(s -> s.block.buildTime) * 1.5f;
        
        // 计算速度：基础速度为1.0f，根据建筑总重量计算惩罚
        float totalWeight = schematic.tiles.sumf(s -> s.block.size * s.block.health);
        float speedPenalty = totalWeight / 10000f;
        design.speed = Math.max(1.0f - speedPenalty, 0.3f);
        
        // 武器数量限制：每个单位最多10个武器
        int weaponCount = 0;
        for (Schematic.Stile stile : schematic.tiles) {
            if (stile.block instanceof mindustry.world.blocks.defense.turrets.Turret) {
                weaponCount++;
            }
        }
        if (weaponCount > 10) {
            // 可以在这里添加警告或限制逻辑
        }
        
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
                    if (selectedDesign != -1 && selectedDesign < subspaceDesigns.size) {
                        time += edelta() * speedScl * Vars.state.rules.unitBuildSpeed(team);
                        progress += edelta() * Vars.state.rules.unitBuildSpeed(team);
                        speedScl = arc.math.Mathf.lerpDelta(speedScl, 1f, 0.05f);
                    }
                } else {
                    super.updateTile();
                    return;
                }
            } else {
                speedScl = arc.math.Mathf.lerpDelta(speedScl, 0f, 0.05f);
            }

            moveOutPayload();

            if (useSubspaceDesign && selectedDesign != -1 && selectedDesign < subspaceDesigns.size && payload == null) {
                SubspaceDesign design = subspaceDesigns.get(selectedDesign);

                if (progress >= design.time) {
                    progress %= 1f;

                    // 创建复合单位
                    Unit unit = createCompositeUnit(design);
                    payload = new UnitPayload(unit);
                    payVector.setZero();
                    consume();
                }

                progress = arc.math.Mathf.clamp(progress, 0, design.time);
            }
        }

        private Unit createCompositeUnit(SubspaceDesign design) {
            // 这里需要实现创建复合单位的逻辑
            // 基于设计的schematic创建一个具有所有建筑功能的单位
            Unit unit = UnitTypes.dagger.create(team); // 临时使用dagger类型，后续需要修改为支持复合单位的类型
            // 设置复合单位的属性
            unit.maxHealth(design.health);
            unit.health(design.health);
            unit.hitSize(design.size * 8f);
            // 后续需要添加复合单位组件的设置
            return unit;
        }

        // 暂时注释display方法，避免Table类依赖
        /*
        @Override
        public void display(arc.scene.ui.Table table) {
            super.display(table);

            table.row();
            table.table(t -> {
                t.left();
                t.checkBox("Use Subspace Design", () -> useSubspaceDesign, this::configure).left().padRight(10);
                if (useSubspaceDesign) {
                    t.label(() -> selectedDesign == -1 ? "No Design Selected" : subspaceDesigns.get(selectedDesign).name).color(arc.graphics.Color.lightGray).left();
                }
            }).left();
        }
        */

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