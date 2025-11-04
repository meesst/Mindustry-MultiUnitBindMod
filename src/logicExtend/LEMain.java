package logicExtend;

import mindustry.mod.Mod;

public class LEMain extends Mod {
    public LEMain() {}

    @Override
    public void loadContent() {
        LStringMerge.StringMergeStatement.create();
        LAmmo.CreateAmmoStatement.create();
        LAmmo.SetAmmoStatement.create();
        LFunction.LFunctionStatement.create();
        // LUnitBindGroup.UnitBindGroupStatement.create(); - 不再需要，已通过@RegisterStatement注解自动注册
    }
}
