package logicExtend;

import arc.struct.Groups;
import mindustry.ai.types.BuilderAI;
import mindustry.gen.Player;
import mindustry.gen.Unit;

/**
 * 可绑定的建造者AI，扩展自BuilderAI
 * 实现单位只协助发出指令的特定玩家功能
 */
public class BindableBuilderAI extends BuilderAI {
    
    /**
     * 重写updateMovement方法，修改onlyAssist模式下的行为
     * 简化实现：只在首次寻找玩家时选择，之后保持不变
     */
    @Override
    public void updateMovement() {
        // 调用父类方法处理基础逻辑
        super.updateMovement();
        
        // 只有在onlyAssist模式下，才修改行为
        if (onlyAssist) {
            // 检查是否已经有协助目标
            if (assistFollowing != null && assistFollowing.isValid() && assistFollowing.player != null) {
                // 已经有有效目标，不改变
                return;
            }
            
            // 寻找第一个有效玩家，而不是最近的玩家
            Player firstPlayer = null;
            for (Player player : Groups.player) {
                if (player.isBuilder() && player.team() == unit.team) {
                    firstPlayer = player;
                    break;
                }
            }
            
            // 设置协助目标为第一个找到的玩家
            if (firstPlayer != null) {
                assistFollowing = firstPlayer.unit();
            }
        }
    }
}