package logicExtend;

import arc.func.Cons;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import mindustry.entities.bullet.BulletType;
import mindustry.logic.LVar;
import mindustry.ui.Styles;

public class LEExtend {
    public static String safeToString(LVar var) {
        if (var == null) {
            return "null";
        }
        Object obj = var.obj();
        if (obj == null) {
            if (!var.isobj) {
                return String.valueOf(Math.floor(var.num()));
            }
            return "null";
        }
        return obj.toString();
    }

    public static TextField field(Table table, String value, Cons<String> setter, float width) {
        return table.field(value, Styles.nodeField, s -> setter.get(sanitize(s)))
                .size(width, 40f).pad(2f).color(table.color).get();
    }

    public static String sanitize(String value){
        if(value.isEmpty()){
            return "";
        }else if(value.length() == 1){
            if(value.charAt(0) == '"' || value.charAt(0) == ';' || value.charAt(0) == ' '){
                return "invalid";
            }
        }else{
            StringBuilder res = new StringBuilder(value.length());
            if(value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'){
                res.append('\"');
                //strip out extra quotes
                for(int i = 1; i < value.length() - 1; i++){
                    if(value.charAt(i) == '"'){
                        res.append('\'');
                    }else{
                        res.append(value.charAt(i));
                    }
                }
                res.append('\"');
            }else{
                //otherwise, strip out semicolons, spaces and quotes
                for(int i = 0; i < value.length(); i++){
                    char c = value.charAt(i);
                    res.append(switch(c){
                        case ';' -> 's';
                        case '"' -> '\'';
                        case ' ' -> '_';
                        default -> c;
                    });
                }
            }

            return res.toString();
        }

        return value;
    }

    public static BulletType load(BulletType b) {
        b.load();
        return b;
    }
}
