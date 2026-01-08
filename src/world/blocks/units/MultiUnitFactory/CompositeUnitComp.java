package world.blocks.units.MultiUnitFactory;

import arc.struct.*;
import mindustry.annotations.Annotations.*;
import mindustry.entities.comp.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;

public class CompositeUnitComp extends BlockUnitComp {
    public Seq<Schematic.Stile> components = new Seq<>();
    public float totalHealth;
    public float totalSpeed;
    public float totalSize;

    @Replace
    @Override
    public void add() {
        // 复合单位可以添加到游戏中
    }

    @Replace
    @Override
    public void update() {
        super.update();
        // 处理复合单位的更新逻辑
        // 例如：更新所有组件建筑的状态
    }

    public void setComponents(Schematic schematic) {
        this.components = schematic.tiles;
        // 计算复合单位的总属性
        calculateTotalStats();
    }

    private void calculateTotalStats() {
        // 计算健康值：所有建筑健康值的总和，设置上限为10000
        totalHealth = Math.min(components.sumf(s -> s.block.health), 10000f);
        maxHealth(totalHealth);
        health(totalHealth);

        // 计算大小：基于建筑占用的总面积计算
        float maxWidth = components.maxf(s -> s.x) + 1;
        float maxHeight = components.maxf(s -> s.y) + 1;
        totalSize = Math.max(maxWidth, maxHeight) / 10f;
        hitSize(totalSize * 8f); // 调整为合适的碰撞大小

        // 计算速度：基础速度为1.0f，根据建筑总重量计算惩罚
        float totalWeight = components.sumf(s -> s.block.size * s.block.health);
        float speedPenalty = totalWeight / 10000f;
        totalSpeed = Math.max(1.0f - speedPenalty, 0.3f);
        drag(0.01f); // 设置合适的阻力
    }

    @Replace
    @Override
    public void killed() {
        super.killed();
        // 处理复合单位死亡时的逻辑
        // 例如：销毁所有组件建筑
    }

    @Override
    public void damage(float v, boolean b) {
        super.damage(v, b);
        // 处理复合单位受到伤害时的逻辑
        // 例如：将伤害分配给各个组件建筑
    }

    // 添加自定义方法来处理复合单位的功能
    public void updateComponents() {
        // 更新所有组件建筑的状态
        for (Schematic.Stile stile : components) {
            // 在这里处理组件建筑的更新逻辑
        }
    }

    public void drawComponents() {
        // 绘制所有组件建筑
        for (Schematic.Stile stile : components) {
            // 在这里处理组件建筑的绘制逻辑
        }
    }
}