package world.blocks.units.MultiUnitFactory;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;

public class SubspaceVisuals {
    // 亚空间入口动画
    public static void drawSubspaceEntrance(Building building, float progress) {
        float x = building.x;
        float y = building.y;
        float size = building.block.size * 8f;
        
        // 绘制旋转的光环效果
        for (int i = 0; i < 4; i++) {
            float angle = (Time.time * 2f + i * 90f) % 360f;
            float radius = size + Mathf.sin(Time.time + i) * 4f;
            float alpha = Mathf.clamp(progress * 0.8f);
            
            Draw.color(Pal.accent, Color.white, i / 4f);
            Draw.alpha(alpha);
            Draw.line(x, y, x + Angles.trnsx(angle, radius), y + Angles.trnsy(angle, radius), 2f);
        }
        
        // 绘制脉冲效果
        float pulse = Mathf.sin(Time.time * 5f) * 0.5f + 0.5f;
        float pulseRadius = size * (0.8f + pulse * 0.4f);
        Draw.color(Pal.accent);
        Draw.alpha(progress * 0.3f);
        Lines.circle(x, y, pulseRadius);
        
        Draw.reset();
    }

    // 复合单位渲染 - 优化版本
    public static void drawCompositeUnit(Unit unit, Seq<Schematic.Stile> components) {
        if (components.isEmpty()) return;
        
        float x = unit.x;
        float y = unit.y;
        float unitSize = unit.hitSize();
        
        // 缓存计算结果，避免重复计算
        float maxComponentSize = components.maxf(s -> s.block.size);
        float scale = unitSize / (maxComponentSize * 8f * 2f);
        float centerX = components.maxf(s -> s.x) / 2f;
        float centerY = components.maxf(s -> s.y) / 2f;
        
        // 限制组件数量，避免性能问题
        int maxComponents = Math.min(components.size, 20);
        
        // 绘制所有组件建筑，优化绘制状态管理
        Draw.save();
        
        for (int i = 0; i < maxComponents; i++) {
            Schematic.Stile stile = components.get(i);
            float componentX = x + (stile.x - centerX) * 8f * scale;
            float componentY = y + (stile.y - centerY) * 8f * scale;
            
            // 直接使用translate、rotate和scale，减少save/restore调用
            Draw.translate(componentX, componentY);
            Draw.rotate(stile.rotation * 90f);
            Draw.scale(scale);
            
            // 绘制建筑
            Draw.rect(stile.block.region, 0, 0);
            
            // 如果有旋转区域，绘制旋转部分
            if (stile.block.regionRotated != null) {
                Draw.rect(stile.block.regionRotated, 0, 0);
            }
            
            // 恢复变换
            Draw.scale(1f / scale);
            Draw.rotate(-stile.rotation * 90f);
            Draw.translate(-componentX, -componentY);
        }
        
        Draw.restore();
    }

    // 生产动画：建筑组合成单位
    public static void drawAssemblyAnimation(Building building, SubspaceDesign design, float progress) {
        float x = building.x;
        float y = building.y;
        
        if (progress < 0.5f) {
            // 前半段：显示建筑组件，逐渐靠近中心
            float t = progress * 2f;
            float centerX = design.schematic.width / 2f;
            float centerY = design.schematic.height / 2f;
            
            for (Schematic.Stile stile : design.schematic.tiles) {
                float offsetX = (stile.x - centerX) * 8f * (1f - t);
                float offsetY = (stile.y - centerY) * 8f * (1f - t);
                float alpha = Mathf.clamp(t * 0.8f);
                
                Draw.alpha(alpha);
                Draw.rect(stile.block.region, x + offsetX, y + offsetY, stile.rotation * 90f);
            }
        } else {
            // 后半段：显示组合后的单位，逐渐清晰
            float t = (progress - 0.5f) * 2f;
            
            // 绘制单位轮廓
            Draw.color(Pal.accent, Color.white, t);
            Draw.alpha(t);
            Lines.circle(x, y, design.size * 8f);
            
            // 绘制单位光晕
            float glowRadius = design.size * 8f + Mathf.sin(Time.time * 3f) * 2f;
            Draw.color(Pal.accent);
            Draw.alpha(t * 0.3f);
            Fill.circle(x, y, glowRadius);
            
            // 绘制建筑组件的简化版本
            float centerX = design.schematic.width / 2f;
            float centerY = design.schematic.height / 2f;
            float scale = (design.size * 8f) / (Math.max(design.schematic.width, design.schematic.height) * 8f);
            
            for (Schematic.Stile stile : design.schematic.tiles) {
                float componentX = x + (stile.x - centerX) * 8f * scale;
                float componentY = y + (stile.y - centerY) * 8f * scale;
                float alpha = Mathf.clamp((1f - t) * 0.5f);
                
                Draw.alpha(alpha);
                Draw.rect(stile.block.region, componentX, componentY, stile.rotation * 90f, scale, scale);
            }
        }
        
        Draw.reset();
    }

    // 亚空间内部背景效果
    public static void drawSubspaceBackground(int width, int height) {
        // 绘制网格背景
        Draw.color(Color.darkGray);
        for (int x = 0; x < width; x += 8) {
            for (int y = 0; y < height; y += 8) {
                Draw.rect(Tex.pixel, x, y, 8f, 8f);
            }
        }
        
        // 绘制动态星点效果
        Draw.color(Color.white);
        for (int i = 0; i < 100; i++) {
            float x = (Time.time * 0.1f + i * 12345) % width;
            float y = (Time.time * 0.05f + i * 54321) % height;
            float size = Mathf.random(0.5f, 2f);
            float alpha = Mathf.random(0.2f, 0.8f);
            
            Draw.alpha(alpha);
            Fill.circle(x, y, size);
        }
        
        // 绘制脉动的中心光晕
        float centerX = width / 2f;
        float centerY = height / 2f;
        float pulse = Mathf.sin(Time.time * 0.5f) * 0.5f + 0.5f;
        float radius = Math.max(width, height) * (0.3f + pulse * 0.1f);
        
        Draw.color(Pal.accent);
        Draw.alpha(0.1f);
        Fill.circle(centerX, centerY, radius);
        
        Draw.reset();
    }

    public static class SubspaceDesign {
        public Schematic schematic;
        public float size;

        public SubspaceDesign(Schematic schematic, float size) {
            this.schematic = schematic;
            this.size = size;
        }
    }
}