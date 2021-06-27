package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {

    private int mainPcOffset = 0;

    public int getMainPcOffset() {
        return mainPcOffset;
    }

    private void generateCodeChr() {
        // Signature: char chr(int i);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);

        Code.put(Code.load_n);

        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    private void generateCodeOrd() {
        // Signature: int ord(char ch);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);

        Code.put(Code.load_n);

        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    private void generateCodeLen() {
        // Signature: int len(void arr[]);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);

        Code.put(Code.load_n);
        Code.put(Code.arraylength);

        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    private void generateCodeBuiltins() {
        generateCodeChr();
        generateCodeOrd();
        generateCodeLen();
    }

    @Override
    public void visit(ProgramHeader programHeader) {
        super.visit(programHeader);
        generateCodeBuiltins();
    }

    @Override
    public void visit(ReadStmt readStmt) {
        super.visit(readStmt);
        Struct designatorType = readStmt.getDesignator().obj.getType();
        if (readStmt.getDesignator() instanceof DesignatorArrayIndex) {
            designatorType = designatorType.getElemType();
        }

        if (MJSymbolTable.intType.equals(designatorType)) {
            // Signature: void read(int x);
            Code.put(Code.read);
        }
        else if (MJSymbolTable.charType.equals(designatorType)) {
            // Signature: void read(char x);
            Code.put(Code.bread);
        }

        Code.put(Code.store_n);
    }

    @Override
    public void visit(PrintStmt printStmt) {
        super.visit(printStmt);
        Struct exprType = printStmt.getExpr().struct;
        // Initialization constant specifies DEFAULT_WIDTH
        int width = MJSymbolTable.charType.equals(exprType) ? 1 : 5;
        if (printStmt.getOptionalWidthSpecifier() instanceof WidthSpecifier) {
            WidthSpecifier widthSpecifier = (WidthSpecifier) printStmt.getOptionalWidthSpecifier();
            width = widthSpecifier.getWidth();
        }

        Code.loadConst(width);

        if (MJSymbolTable.intType.equals(exprType)) {
            // Signature: void print(int x, int width = DEFAULT_WIDTH);
            Code.put(Code.print);
        }
        else if (MJSymbolTable.charType.equals(exprType)) {
            // Signature: void print(char x, int width = DEFAULT_WIDTH);
            Code.put(Code.bprint);
        }
    }

    @Override
    public void visit(MethodSignatureWithoutParams methodSignatureWithoutParams) {
        super.visit(methodSignatureWithoutParams);
        if ("main".equals(methodSignatureWithoutParams.getMethodName())) {
            mainPcOffset = Code.pc;
        }
    }

}
