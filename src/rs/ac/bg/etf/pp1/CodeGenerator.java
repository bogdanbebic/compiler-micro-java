package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.*;

public class CodeGenerator extends VisitorAdaptor {

    private final Map<String, Integer> methodOffsets = new HashMap<>();
    private final Stack<Integer> doWhileBodyStartAddresses = new Stack<>();
    private final Stack<List<Integer>> continueAddresses = new Stack<>();
    private final Stack<List<Integer>> breakAddresses = new Stack<>();
    private final Stack<List<Integer>> negativeJumps = new Stack<>();
    private final Stack<List<Integer>> positiveJumps = new Stack<>();
    private final Stack<Boolean> isFirstCaseLabel = new Stack<>();
    private final Stack<Integer> caseAddresses = new Stack<>();
    private final Stack<List<Integer>> yieldAddresses = new Stack<>();

    public int getMainPcOffset() {
        return methodOffsets.get("main");
    }

    private void generateCodeChr() {
        // Signature: char chr(int i);
        this.methodOffsets.put("chr", Code.pc);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);

        Code.put(Code.load_n);

        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    private void generateCodeOrd() {
        // Signature: int ord(char ch);
        this.methodOffsets.put("ord", Code.pc);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);

        Code.put(Code.load_n);

        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    private void generateCodeLen() {
        // Signature: int len(void arr[]);
        this.methodOffsets.put("len", Code.pc);
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
        Designator designator = readStmt.getDesignator();
        Struct designatorType = designator.obj.getType();

        if (MJSymbolTable.intType.equals(designatorType)) {
            // Signature: void read(int x);
            Code.put(Code.read);
        }
        else if (MJSymbolTable.charType.equals(designatorType)) {
            // Signature: void read(char x);
            Code.put(Code.bread);
        }

        Code.store(designator.obj);
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

        this.methodOffsets.put(methodName, Code.pc);

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

    private void generateFunctionCall(String functionName) {
        int functionOffset = methodOffsets.get(functionName);
        int pcRelativeOffset = functionOffset - Code.pc;
        Code.put(Code.call);
        // all functions must be defined before their calls,
        // so there is no need for back patching
        Code.put2(pcRelativeOffset);
    }

    @Override
    public void visit(FunctionCallStmt functionCallStmt) {
        super.visit(functionCallStmt);
        Designator designator = functionCallStmt.getDesignator();
        generateFunctionCall(designator.obj.getName());
        if (!MJSymbolTable.noType.equals(designator.obj.getType())) {
            Code.put(Code.pop);
        }
    }

    private void generateCodePostIncDec(boolean isIncrement, Designator designator) {
        if (designator instanceof DesignatorArrayIndex) {
            DesignatorArrayIndex designatorArrayIndex = (DesignatorArrayIndex) designator;
            designatorArrayIndex.traverseBottomUp(new CodeGenerator());
        }

        Code.load(designator.obj);
        Code.loadConst(1);
        if (isIncrement)
            Code.put(Code.add);
        else
            Code.put(Code.sub);
        Code.store(designator.obj);
    }

    @Override
    public void visit(DesignatorIncStmt designatorIncStmt) {
        super.visit(designatorIncStmt);
        generateCodePostIncDec(true, designatorIncStmt.getDesignator());
    }

    @Override
    public void visit(DesignatorDecStmt designatorDecStmt) {
        super.visit(designatorDecStmt);
        generateCodePostIncDec(false, designatorDecStmt.getDesignator());
    }

    @Override
    public void visit(ReturnStmt returnStmt) {
        super.visit(returnStmt);
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    @Override
    public void visit(YieldStmt yieldStmt) {
        super.visit(yieldStmt);
        this.yieldAddresses.peek().add(Code.pc + 1);
        Code.putJump(0);
    }

    @Override
    public void visit(SwitchBodyStart switchBodyStart) {
        super.visit(switchBodyStart);
        this.isFirstCaseLabel.push(true);
        this.yieldAddresses.push(new ArrayList<>());
    }

    @Override
    public void visit(SwitchBodyEnd switchBodyEnd) {
        super.visit(switchBodyEnd);
        this.isFirstCaseLabel.pop();

        // back patch yield statements
        this.yieldAddresses.pop().forEach(Code::fixup);
    }

    @Override
    public void visit(NonDefaultCaseLabel nonDefaultCaseLabel) {
        super.visit(nonDefaultCaseLabel);
        // back patch previous case label jump if it exists
        if (!this.isFirstCaseLabel.pop())
            Code.fixup(this.caseAddresses.pop());

        this.isFirstCaseLabel.push(false);

        // check if switch expression is matched to this case
        Code.put(Code.dup);
        Code.loadConst(nonDefaultCaseLabel.getValue());
        this.caseAddresses.push(Code.pc + 1);
        Code.putFalseJump(Code.eq, 0);
        Code.put(Code.pop);
    }

    @Override
    public void visit(DefaultCaseLabel defaultCaseLabel) {
        super.visit(defaultCaseLabel);
        // back patch previous case label jump if it exists
        if (!this.isFirstCaseLabel.pop())
            Code.fixup(this.caseAddresses.pop());

        // set to true to prevent back patching attempt
        // in next case when no jump occurs in default
        this.isFirstCaseLabel.push(true);

        Code.put(Code.pop);
    }

    @Override
    public void visit(ElseBranch elseBranch) {
        super.visit(elseBranch);
        // back patch skip else branch jump
        this.positiveJumps.pop().forEach(Code::fixup);
    }

    @Override
    public void visit(ThenBranchEnd thenBranchEnd) {
        super.visit(thenBranchEnd);

        // skip else branch if it exists
        IfElseStmt ifElseStmt = (IfElseStmt) thenBranchEnd.getParent();
        if (ifElseStmt.getOptionalElseBranch() instanceof ElseBranch) {
            this.positiveJumps.add(new ArrayList<>());
            this.positiveJumps.peek().add(Code.pc + 1);
            Code.putJump(0);
        }

        // back patch negative jumps
        this.negativeJumps.pop().forEach(Code::fixup);
    }

    @Override
    public void visit(LogicalOr logicalOr) {
        super.visit(logicalOr);
        this.positiveJumps.peek().add(Code.pc + 1);
        Code.putJump(0);

        // back patch negative jumps
        this.negativeJumps.peek().forEach(Code::fixup);
        this.negativeJumps.peek().clear();
    }

    @Override
    public void visit(ConditionExprRelOp conditionExprRelOp) {
        super.visit(conditionExprRelOp);
        RelOp relOp = conditionExprRelOp.getRelOp();
        int op = 0;
        if (relOp instanceof EqualOp)
            op = Code.eq;
        else if (relOp instanceof NotEqualOp)
            op = Code.ne;
        else if (relOp instanceof GreaterOp)
            op = Code.gt;
        else if (relOp instanceof GreaterEqualOp)
            op = Code.ge;
        else if (relOp instanceof LessOp)
            op = Code.lt;
        else if (relOp instanceof LessEqualOp)
            op = Code.le;

        this.negativeJumps.peek().add(Code.pc + 1);
        Code.putFalseJump(op, 0);
    }

    @Override
    public void visit(FirstConditionExpr firstConditionExpr) {
        super.visit(firstConditionExpr);
        Code.loadConst(0);
        this.negativeJumps.peek().add(Code.pc + 1);
        Code.putFalseJump(Code.ne, 0);
    }

    @Override
    public void visit(IfConditionStart ifConditionStart) {
        super.visit(ifConditionStart);
        this.negativeJumps.add(new ArrayList<>());
        this.positiveJumps.add(new ArrayList<>());
    }

    @Override
    public void visit(IfConditionEnd ifConditionEnd) {
        super.visit(ifConditionEnd);
        // back patch positive jumps
        this.positiveJumps.pop().forEach(Code::fixup);
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
        Designator designator = designatorFactor.getDesignator();
        if (designatorFactor.getOptionalFunctionCall() instanceof NoFunctionCall) {
            Code.load(designator.obj);
        }
        else {
            generateFunctionCall(designator.obj.getName());
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
    public void visit(BreakStmt breakStmt) {
        super.visit(breakStmt);
        this.breakAddresses.peek().add(Code.pc + 1);
        Code.putJump(0);
    }

    @Override
    public void visit(ContinueStmt continueStmt) {
        super.visit(continueStmt);
        this.continueAddresses.peek().add(Code.pc + 1);
        Code.putJump(0);
    }

    @Override
    public void visit(DoWhileBodyStart doWhileBodyStart) {
        super.visit(doWhileBodyStart);
        this.doWhileBodyStartAddresses.push(Code.pc);
        this.breakAddresses.push(new ArrayList<>());
        this.continueAddresses.push(new ArrayList<>());
    }

    @Override
    public void visit(DoWhileBodyEnd doWhileBodyEnd) {
        super.visit(doWhileBodyEnd);
        // back patch for continue statements
        this.continueAddresses.pop().forEach(Code::fixup);
    }

    @Override
    public void visit(DoWhileStatement doWhileStatement) {
        super.visit(doWhileStatement);
        Code.putJump(this.doWhileBodyStartAddresses.pop());

        // back patch break statements and negative jumps
        this.breakAddresses.pop().forEach(Code::fixup);
        this.negativeJumps.pop().forEach(Code::fixup);
    }

}
