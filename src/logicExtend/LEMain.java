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
<<<<<<< HEAD
        LUnitBindGroup.UnitBindGroupStatement.create();
=======
>>>>>>> 16f918d68a1f2a08fa108730421acd0db21b2aa2
    }
}
