package logicExtend;

import mindustry.ai.types.BuilderAI;
import mindustry.gen.Player;
import mindustry.gen.Unit;

/**
 * 可绑定的建造者AI，扩展自BuilderAI
 * 实现单位只协助发出指令的特定玩家功能
 */
public class BindableBuilderAI extends BuilderAI {
    
    /** 绑定的目标玩家ID */
    private int boundPlayerId = -1;
    
    /**
     * 设置绑定的玩家
     * @param player 要绑定的玩家
     */
    public void setBoundPlayer(Player player) {
        this.boundPlayerId = player.id;
    }
    
    /**
     * 获取绑定的玩家ID
     * @return 绑定的玩家ID，-1表示未绑定
     */
    public int getBoundPlayerId() {
        return boundPlayerId;
    }
    
    /**
     * 重写updateMovement方法，修改onlyAssist模式下的行为
     * 使单位只协助绑定的玩家，而不是寻找最近的玩家
     */
    @Override
    public void updateMovement() {
        // 调用父类方法处理基础逻辑
        super.updateMovement();
        
        // 只有在onlyAssist模式下且已绑定玩家时，才执行自定义逻辑
        if (onlyAssist && boundPlayerId != -1) {
            // 寻找绑定的玩家
            Player boundPlayer = null;
            for (Player player : Groups.player) {
                if (player.id == boundPlayerId && player.isValid()) {
                    boundPlayer = player;
                    break;
                }
            }
            
            // 如果绑定的玩家仍然有效，更新assistFollowing为该玩家的单位
            if (boundPlayer != null && boundPlayer.isBuilder() && boundPlayer.team() == unit.team) {
                assistFollowing = boundPlayer.unit();
            } else {
                // 如果绑定的玩家无效，取消绑定
                boundPlayerId = -1;
            }
        }
    }
}