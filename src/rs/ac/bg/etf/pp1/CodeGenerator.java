package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
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
        String methodName = methodSignatureWithoutParams.getMethodName();

        if ("main".equals(methodName)) {
            mainPcOffset = Code.pc;
        }

        Obj methodObj = methodSignatureWithoutParams.obj;
        Code.put(Code.enter);
        Code.put(methodObj.getLevel());
        Code.put(methodObj.getLocalSymbols().size());
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        super.visit(methodDecl);
        ReturnType returnType = methodDecl.getMethodSignature().getMethodSignatureWithoutParams().getReturnType();
        if (returnType instanceof VoidReturnType) {
            Code.put(Code.exit);
            Code.put(Code.return_);
        }
        else {
            // traps method end without return
            Code.put(Code.trap);
            Code.put(0);
        }
    }

    @Override
    public void visit(AssignmentStmt assignmentStmt) {
        super.visit(assignmentStmt);
        Code.store(assignmentStmt.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorIncStmt designatorIncStmt) {
        super.visit(designatorIncStmt);
        Code.load(designatorIncStmt.getDesignator().obj);
        Code.loadConst(1);
        Code.put(Code.add);
        Code.store(designatorIncStmt.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorDecStmt designatorDecStmt) {
        super.visit(designatorDecStmt);
        Code.load(designatorDecStmt.getDesignator().obj);
        Code.loadConst(1);
        Code.put(Code.sub);
        Code.store(designatorDecStmt.getDesignator().obj);
    }

    @Override
    public void visit(ReturnStmt returnStmt) {
        super.visit(returnStmt);
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    @Override
    public void visit(AddOpExpression addOpExpression) {
        super.visit(addOpExpression);
        AddOp addOp = addOpExpression.getAddOp();
        if (addOp instanceof PlusOp)
            Code.put(Code.add);
        else if (addOp instanceof MinusOp)
            Code.put(Code.sub);
    }

    @Override
    public void visit(NegativeTerm negativeTerm) {
        super.visit(negativeTerm);
        Code.put(Code.neg);
    }

    @Override
    public void visit(MulOpTerm mulOpTerm) {
        super.visit(mulOpTerm);
        MulOp mulOp = mulOpTerm.getMulOp();
        if (mulOp instanceof MultiplyOp)
            Code.put(Code.mul);
        else if (mulOp instanceof DivideOp)
            Code.put(Code.div);
        else if (mulOp instanceof ModuloOp)
            Code.put(Code.rem);
    }

    @Override
    public void visit(DesignatorFactor designatorFactor) {
        super.visit(designatorFactor);
        if (designatorFactor.getOptionalFunctionCall() instanceof NoFunctionCall) {
            Code.load(designatorFactor.getDesignator().obj);
        }
    }

    @Override
    public void visit(ConstantFactor constantFactor) {
        super.visit(constantFactor);
        Constant constant = constantFactor.getConstant();
        int value = 0;
        if (constant instanceof NumberConstant) {
            value = ((NumberConstant) constant).getValue();
        }
        else if (constant instanceof CharConstant) {
            value = ((CharConstant) constant).getValue();
        }
        else if (constant instanceof BoolConstant) {
            value = ((BoolConstant) constant).getValue();
        }

        Code.loadConst(value);
    }

    @Override
    public void visit(AllocationFactor allocationFactor) {
        super.visit(allocationFactor);
        Code.put(Code.newarray);
        if (MJSymbolTable.intType.equals(allocationFactor.getType().struct)) {
            Code.put(1);
        }
        else {
            Code.put(0);
        }
    }

    @Override
    public void visit(SingleIdentifier singleIdentifier) {
        super.visit(singleIdentifier);
        if (singleIdentifier.getParent() instanceof DesignatorArrayIndex) {
            Code.load(singleIdentifier.obj);
        }
    }

    @Override
    public void visit(DesignatorArrayIndex designatorArrayIndex) {
        super.visit(designatorArrayIndex);
        Obj designatorObj = designatorArrayIndex.getDesignator().obj;
        designatorArrayIndex.obj = new Obj(Obj.Elem, designatorObj.getName(), designatorObj.getType().getElemType());
    }
}
