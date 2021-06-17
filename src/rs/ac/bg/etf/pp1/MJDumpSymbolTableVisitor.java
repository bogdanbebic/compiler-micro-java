package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.DumpSymbolTableVisitor;

public class MJDumpSymbolTableVisitor extends DumpSymbolTableVisitor {

    @Override
    public void visitStructNode(Struct structToVisit) {
        if (structToVisit.getKind() == Struct.Bool) {
            super.output.append("bool");
        }
        else if (structToVisit.getKind() == Struct.Array &&
                structToVisit.getElemType().getKind() == Struct.Bool) {
            super.output.append("Arr of bool");
        }
        else {
            super.visitStructNode(structToVisit);
        }
    }

}
