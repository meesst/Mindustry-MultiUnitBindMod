package logicExtend;

import arc.scene.style.TextureRegionDrawable;
import arc.struct.ObjectMap;
import mindustry.ui.Fonts;

public class LEIcon {
    public static final ObjectMap<String, TextureRegionDrawable> icons = new ObjectMap<>();
    public static TextureRegionDrawable functionIcon;
    public static void load() {
        functionIcon = Fonts.getGlyph(Fonts.icon, '⒡');
        icons.put("functionIcon", functionIcon);
    }
}
