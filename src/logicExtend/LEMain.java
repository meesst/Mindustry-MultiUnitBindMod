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
    }
}
