package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.ProgramHeader;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.mj.runtime.Code;

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

}
