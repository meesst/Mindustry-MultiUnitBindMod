package logicExtend;

import arc.struct.Seq;
import mindustry.gen.Unit;
import mindustry.gen.content;
import mindustry.logic.LExecutor;
import mindustry.logic.LVar;
import mindustry.type.UnitType;

/**
 * 单位绑定组执行器类
 * 负责处理单位绑定组的核心执行逻辑
 */
public class LUnitBindGroupRUN implements LExecutor.LInstruction {
    /** 单位类型变量 */
    public LVar type;
    /** 绑定的单位数量变量 */
    public LVar count;
    /** 当前单位变量的变量引用 */
    public LVar unitVar;
    /** 单位索引变量的变量引用 */
    public LVar indexVar;
    /** 绑定模式变量 */
    public LVar mode;
    /** 绑定组类型变量 */
    public LVar group;

    /**
     * 构造函数，指定目标单位类型、数量、模式、单位变量和索引变量
     * @param type 单位类型变量
     * @param count 绑定的单位数量变量
     * @param mode 绑定模式变量
     * @param unitVar 当前单位变量的变量引用
     * @param indexVar 单位索引变量的变量引用
     * @param group 绑定组类型变量
     */
    public LUnitBindGroupRUN(LVar type, LVar count, LVar mode, LVar unitVar, LVar indexVar, LVar group) {
        this.type = type;
        this.count = count;
        this.mode = mode;
        this.unitVar = unitVar;
        this.indexVar = indexVar;
        this.group = group;
    }

    /**
     * 空构造函数
     */
    public LUnitBindGroupRUN() {
    }

    /**
     * 执行指令的核心逻辑
     * @param exec 逻辑执行器实例
     */
    @Override
    public void run(LExecutor exec) {
        // 初始化或更新绑定计数器数组
        // binds数组用于记录每种单位类型的当前绑定索引
        if(exec.binds == null || exec.binds.length != content.units().size) {
            exec.binds = new int[content.units().size]; // 每种单位类型对应一个计数器
        }

        // 处理单位类型绑定（最常见的情况）
        if(type.obj() instanceof UnitType type && type.logicControllable) {
            // 获取同类型的所有单位列表
            Seq<Unit> seq = exec.team.data().unitCache(type);

            // 如果存在该类型的单位且seq不为null
            if(seq != null && seq.size > 0) {
                // 确保计数器在有效范围内循环（防止索引越界）
                exec.binds[type.id] %= seq.size;
                // 绑定到当前索引对应的单位
                exec.unit.setconst(seq.get(exec.binds[type.id]));
                // 索引递增，下次执行时将绑定到下一个单位
                exec.binds[type.id]++;
            } else {
                // 没有找到该类型的单位，清空当前绑定
                exec.unit.setconst(null);
            }
        } else if(type.obj() instanceof Unit u && (u.team == exec.team || exec.privileged) && u.type.logicControllable) {
            // 处理直接绑定到特定单位对象的情况
            // 条件：必须是同一队伍或拥有特权，且单位支持逻辑控制
            exec.unit.setconst(u); // 绑定到指定的单位对象
        } else {
            // 其他情况：类型无效或不满足条件，清空当前绑定
            exec.unit.setconst(null);
        }
    }
}