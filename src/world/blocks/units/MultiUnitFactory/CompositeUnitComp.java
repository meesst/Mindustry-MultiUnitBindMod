package world.blocks.units.MultiUnitFactory;

import arc.struct.*;
import mindustry.game.*;
import mindustry.gen.*;

/**
 * 复合单位组件类 - 用于处理建筑组合转换为单位的逻辑
 * 简化实现，避免使用复杂的注解和继承
 */
public class CompositeUnitComp {
    public Seq<Schematic.Stile> components = new Seq<>();
    public float totalHealth;
    public float totalSpeed;
    public float totalSize;

    /**
     * 设置复合单位的组件
     */
    public void setComponents(Schematic schematic) {
        this.components = schematic.tiles;
        // 计算复合单位的总属性
        calculateTotalStats();
    }

    /**
     * 计算复合单位的总属性
     */
    private void calculateTotalStats() {
        // 计算健康值：所有建筑健康值的总和，设置上限为10000
        totalHealth = Math.min(components.sumf(s -> s.block.health), 10000f);
        
        // 计算大小：基于建筑占用的总面积计算
        float maxWidth = components.max(f -> f.x) + 1;
        float maxHeight = components.max(f -> f.y) + 1;
        totalSize = Math.max(maxWidth, maxHeight) / 10f;
        
        // 计算速度：基础速度为1.0f，根据建筑总重量计算惩罚
        float totalWeight = components.sumf(s -> s.block.size * s.block.health);
        float speedPenalty = totalWeight / 10000f;
        totalSpeed = Math.max(1.0f - speedPenalty, 0.3f);
    }

    /**
     * 更新所有组件建筑的状态
     */
    public void updateComponents() {
        // 更新所有组件建筑的状态
        for (Schematic.Stile stile : components) {
            // 在这里处理组件建筑的更新逻辑
        }
    }

    /**
     * 绘制所有组件建筑
     */
    public void drawComponents(Unit unit) {
        // 委托给SubspaceVisuals类处理绘制
        SubspaceVisuals.drawCompositeUnit(unit, components);
    }
}