package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.ProgramHeader;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.symboltable.concepts.Obj;

public class SemanticAnalyzer extends VisitorAdaptor {
    // TODO: implement

    @Override
    public void visit(ProgramHeader programHeader) {
        super.visit(programHeader);
        programHeader.obj = MJSymbolTable.insert(Obj.Prog, programHeader.getProgramName(), MJSymbolTable.noType);
        MJSymbolTable.openScope();
    }

    @Override
    public void visit(Program program) {
        super.visit(program);
        MJSymbolTable.chainLocalSymbols(program.getProgramHeader().obj);
        MJSymbolTable.closeScope();
    }

}
