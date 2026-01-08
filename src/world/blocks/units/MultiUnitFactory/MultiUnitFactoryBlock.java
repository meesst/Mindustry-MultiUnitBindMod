package world.blocks.units.MultiUnitFactory;

public class MultiUnitFactoryBlock {
    public static MultiUnitFactory multiUnitFactory;
    
    public static void load() {
        multiUnitFactory = new MultiUnitFactory("multi-unit-factory");
    }
}