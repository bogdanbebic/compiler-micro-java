package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.Designator;
import rs.ac.bg.etf.pp1.ast.DesignatorArrayIndex;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class MJSymbolTable extends Tab {

    public static final Struct boolType = new Struct(Struct.Bool);

    public static void init() {
        Tab.init();
        Tab.currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
    }

    public static boolean isNotBuiltinType(Struct struct) {
        return !intType.equals(struct) && !charType.equals(struct) && !boolType.equals(struct);
    }

    public static boolean isAssignable(Designator designator) {
        if (designator.obj.getType().getKind() == Struct.Array &&
                designator instanceof DesignatorArrayIndex) {
            return isAssignable(((DesignatorArrayIndex) designator).getDesignator());
        }

        return designator.obj.getKind() == Obj.Var || designator.obj.getKind() == Obj.Elem;
    }
}
