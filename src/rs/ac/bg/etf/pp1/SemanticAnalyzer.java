package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.test.CompilerError;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.*;

public class SemanticAnalyzer extends VisitorAdaptor {

    private Struct currentDeclarationType = MJSymbolTable.noType;
    private Obj currentMethod;
    private boolean inMethodDeclaration = false;
    private boolean inMethodSignature = false;
    private final Map<String, Obj> currentMethodParams = new LinkedHashMap<>();
    private int inDoWhileBodyCount = 0;
    private int inSwitchBodyCount = 0;
    private int defaultCaseBranchesCount = 0;
    private final Set<Integer> switchCaseLabelValues = new HashSet<>();
    private final List<Struct> switchYieldTypes = new ArrayList<>();
    private final List<Struct> functionCallParamTypes = new ArrayList<>();

    private int variableCount;

    public int getVariableCount() {
        return variableCount;
    }

    @Override
    public void visit(AssignmentStmt assignmentStmt) {
        super.visit(assignmentStmt);
        Designator designator = assignmentStmt.getDesignator();
        Expr expr = assignmentStmt.getExpr();
        if (!MJSymbolTable.isAssignable(designator)) {
            report_error("Assignment variable must be assignable", assignmentStmt);
        }

        Struct designatorType = designator.obj.getType();

        if (MJSymbolTable.noType.equals(designatorType) ||
                MJSymbolTable.noType.equals(expr.struct)) {
            // do not propagate errors
            return;
        }

        if (!designatorType.equals(expr.struct)) {
            MJDumpSymbolTableVisitor visitor = new MJDumpSymbolTableVisitor();
            designatorType.accept(visitor);
            String expectedType = visitor.getOutput();
            visitor = new MJDumpSymbolTableVisitor();
            expr.struct.accept(visitor);
            String foundType = visitor.getOutput();
            String msg = String.format(
                    "Type mismatch in assignment, expected '%s', found '%s'",
                    expectedType, foundType);
            report_error(msg, assignmentStmt);
        }
    }

    @Override
    public void visit(ReturnStmt returnStmt) {
        super.visit(returnStmt);
        if (!inMethodDeclaration) {
            report_error("return statement must be inside of a function", returnStmt);
            return;
        }

        if (returnStmt.getOptionalExpr() instanceof Expression) {
            Expr expr = ((Expression) returnStmt.getOptionalExpr()).getExpr();
            if (!MJSymbolTable.noType.equals(expr.struct) &&
                    !expr.struct.equals(currentMethod.getType())) {
                report_error(
                        "Type mismatch between return type and expression in return",
                        returnStmt);
            }
        }
        else if (returnStmt.getOptionalExpr() instanceof NoExpression) {
            if (!MJSymbolTable.noType.equals(currentMethod.getType())) {
                report_error(
                        "empty return can only be placed in void functions",
                        returnStmt);
            }
        }
    }

    @Override
    public void visit(DesignatorIncStmt designatorIncStmt) {
        super.visit(designatorIncStmt);
        Designator designator = designatorIncStmt.getDesignator();
        if (!MJSymbolTable.isAssignable(designator)) {
            report_error("Increment statement variable must be assignable", designatorIncStmt);
        }

        Struct designatorType = designator.obj.getType();

        if (!MJSymbolTable.intType.equals(designatorType)) {
            report_error("Increment variable must be of type int", designatorIncStmt);
        }
    }

    @Override
    public void visit(DesignatorDecStmt designatorDecStmt) {
        super.visit(designatorDecStmt);
        Designator designator = designatorDecStmt.getDesignator();
        if (!MJSymbolTable.isAssignable(designator)) {
            report_error("Decrement statement variable must be assignable", designatorDecStmt);
        }

        Struct designatorType = designator.obj.getType();

        if (!MJSymbolTable.intType.equals(designatorType)) {
            report_error("Decrement variable must be of type int", designatorDecStmt);
        }
    }

    private void checkFunctionParamTypes(String functionName, SyntaxNode syntaxNode) {
        Obj funcObj = MJSymbolTable.find(functionName);
        if (MJSymbolTable.noObj.equals(funcObj) || funcObj.getKind() != Obj.Meth) {
            return;
        }

        if (funcObj.getLevel() != functionCallParamTypes.size()) {
            return;
        }

        funcObj.getLocalSymbols().stream()
                .filter(localSymbol -> localSymbol.getFpPos() > 0)
                .filter(funcParam -> !funcParam.getType()
                        .equals(functionCallParamTypes.get(funcParam.getFpPos() - 1)))
                .forEach(funcParam -> {
                    MJDumpSymbolTableVisitor visitor = new MJDumpSymbolTableVisitor();
                    funcParam.accept(visitor);
                    String expectedType = visitor.getOutput();
                    visitor = new MJDumpSymbolTableVisitor();
                    functionCallParamTypes.get(funcParam.getFpPos() - 1).accept(visitor);
                    String foundType = visitor.getOutput();
                    String msg = String.format(
                            "Function '%s' param %d type mismatch, expected '%s', found '%s'",
                            functionName, funcParam.getFpPos(), expectedType, foundType);
                    report_error(msg, syntaxNode);
                });
    }

    @Override
    public void visit(PrintStmt printStmt) {
        super.visit(printStmt);
        if (MJSymbolTable.isNotBuiltinType(printStmt.getExpr().struct)) {
            report_error("print argument must be of a builtin type", printStmt);
        }
    }

    @Override
    public void visit(ReadStmt readStmt) {
        super.visit(readStmt);
        Designator designator = readStmt.getDesignator();
        Struct designatorType = designator.obj.getType();

        if (MJSymbolTable.isNotBuiltinType(designatorType)) {
            report_error("read argument must be of builtin type", readStmt);
        }

        if (!MJSymbolTable.isAssignable(designator)) {
            report_error("read argument must be assignable", readStmt);
        }
    }

    @Override
    public void visit(ActualParamsStart actualParamsStart) {
        super.visit(actualParamsStart);
        functionCallParamTypes.clear();
    }

    @Override
    public void visit(FirstActualParamDecl firstActualParamDecl) {
        super.visit(firstActualParamDecl);
        functionCallParamTypes.add(firstActualParamDecl.getExpr().struct);
    }

    @Override
    public void visit(ActualParamsListDecl actualParamsListDecl) {
        super.visit(actualParamsListDecl);
        functionCallParamTypes.add(actualParamsListDecl.getExpr().struct);
    }

    private String getLastIdentifier(Designator designator) {
        if (designator instanceof SingleIdentifier) {
            return ((SingleIdentifier) designator).getDesignator();
        }
        else if (designator instanceof DesignatorArrayIndex) {
            return getLastIdentifier(((DesignatorArrayIndex) designator).getDesignator());
        }
        else {
            return "";
        }
    }

    @Override
    public void visit(FunctionCallStmt functionCallStmt) {
        super.visit(functionCallStmt);
        String name = getLastIdentifier(functionCallStmt.getDesignator());
        checkFunctionParamTypes(name, functionCallStmt);
        report_usage_info("Found call of function '" + name + "'", functionCallStmt, name);
    }

    @Override
    public void visit(FirstConditionTerm firstConditionTerm) {
        super.visit(firstConditionTerm);
        ConditionTerm term = firstConditionTerm.getConditionTerm();
        if (!MJSymbolTable.boolType.equals(term.struct) &&
                !MJSymbolTable.noType.equals(term.struct)) {
            report_error("Invalid type for condition", firstConditionTerm);
            firstConditionTerm.struct = MJSymbolTable.noType;
            return;
        }

        firstConditionTerm.struct = term.struct;
    }

    @Override
    public void visit(ConditionTermList conditionTermList) {
        super.visit(conditionTermList);
        Struct lhsStruct = conditionTermList.getCondition().struct;
        Struct rhsStruct = conditionTermList.getConditionTerm().struct;
        if (MJSymbolTable.boolType.equals(lhsStruct) && MJSymbolTable.boolType.equals(rhsStruct)) {
            conditionTermList.struct = MJSymbolTable.boolType;
            return;
        }

        conditionTermList.struct = MJSymbolTable.noType;
        if (!MJSymbolTable.noType.equals(lhsStruct) && !MJSymbolTable.noType.equals(rhsStruct)) {
            report_error("Invalid types for || operator", conditionTermList);
        }
    }

    @Override
    public void visit(FirstConditionFactor firstConditionFactor) {
        super.visit(firstConditionFactor);
        ConditionFactor factor = firstConditionFactor.getConditionFactor();
        if (!MJSymbolTable.boolType.equals(factor.struct) &&
                !MJSymbolTable.noType.equals(factor.struct)) {
            report_error("Invalid type for condition", firstConditionFactor);
            firstConditionFactor.struct = MJSymbolTable.noType;
            return;
        }

        firstConditionFactor.struct = factor.struct;
    }

    @Override
    public void visit(ConditionFactorList conditionFactorList) {
        super.visit(conditionFactorList);
        Struct lhsStruct = conditionFactorList.getConditionTerm().struct;
        Struct rhsStruct = conditionFactorList.getConditionFactor().struct;
        if (MJSymbolTable.boolType.equals(lhsStruct) && MJSymbolTable.boolType.equals(rhsStruct)) {
            conditionFactorList.struct = MJSymbolTable.boolType;
            return;
        }

        conditionFactorList.struct = MJSymbolTable.noType;

        if (!MJSymbolTable.noType.equals(lhsStruct) && !MJSymbolTable.noType.equals(rhsStruct)) {
            report_error("Invalid types for && operator", conditionFactorList);
        }
    }

    @Override
    public void visit(FirstConditionExpr firstConditionExpr) {
        super.visit(firstConditionExpr);
        Expr expr = firstConditionExpr.getExpr();
        if (!MJSymbolTable.boolType.equals(expr.struct) &&
                !MJSymbolTable.noType.equals(expr.struct)) {
            report_error("Invalid type for condition", firstConditionExpr);
            firstConditionExpr.struct = MJSymbolTable.noType;
            return;
        }

        firstConditionExpr.struct = expr.struct;
    }

    @Override
    public void visit(ConditionExprRelOp conditionExprRelOp) {
        super.visit(conditionExprRelOp);
        Struct lhsStruct = conditionExprRelOp.getExpr().struct;
        Struct rhsStruct = conditionExprRelOp.getExpr1().struct;
        if (MJSymbolTable.noType.equals(lhsStruct) || MJSymbolTable.noType.equals(rhsStruct)) {
            conditionExprRelOp.struct = MJSymbolTable.noType;
            return;
        }

        if (!lhsStruct.compatibleWith(rhsStruct)) {
            report_error("Types in relational operation not compatible", conditionExprRelOp);
            conditionExprRelOp.struct = MJSymbolTable.noType;
            return;
        }

        RelOp relOp = conditionExprRelOp.getRelOp();
        if ((lhsStruct.isRefType() || rhsStruct.isRefType()) &&
                !(relOp instanceof EqualOp) &&
                !(relOp instanceof NotEqualOp)) {
            report_error("Operator cannot be applied to reference type", conditionExprRelOp);
            conditionExprRelOp.struct = MJSymbolTable.noType;
            return;
        }

        conditionExprRelOp.struct = MJSymbolTable.boolType;
    }

    @Override
    public void visit(ExprTermListDecl exprTermListDecl) {
        super.visit(exprTermListDecl);
        exprTermListDecl.struct = exprTermListDecl.getExprTermList().struct;
    }

    @Override
    public void visit(SingleTerm singleTerm) {
        super.visit(singleTerm);
        singleTerm.struct = singleTerm.getExpressionTerm().struct;
    }

    @Override
    public void visit(AddOpExpression addOpExpression) {
        super.visit(addOpExpression);
        if (MJSymbolTable.intType.equals(addOpExpression.getExprTermList().struct) &&
                MJSymbolTable.intType.equals(addOpExpression.getTerm().struct)) {
            addOpExpression.struct = MJSymbolTable.intType;
            return;
        }

        // not compatible with addition
        addOpExpression.struct = MJSymbolTable.noType;

        if (!MJSymbolTable.noType.equals(addOpExpression.getExprTermList().struct) &&
                !MJSymbolTable.noType.equals(addOpExpression.getTerm().struct)) {
            report_error("Invalid types for addition operator", addOpExpression);
        }
    }

    @Override
    public void visit(PositiveTerm positiveTerm) {
        super.visit(positiveTerm);
        positiveTerm.struct = positiveTerm.getTerm().struct;
    }

    @Override
    public void visit(NegativeTerm negativeTerm) {
        super.visit(negativeTerm);
        if (!MJSymbolTable.intType.equals(negativeTerm.getTerm().struct) &&
                !MJSymbolTable.noType.equals(negativeTerm.getTerm().struct)) {
            report_error("Invalid type for unary -", negativeTerm);
            negativeTerm.struct = MJSymbolTable.noType;
            return;
        }

        negativeTerm.struct = negativeTerm.getTerm().struct;
    }

    @Override
    public void visit(SingleFactor singleFactor) {
        super.visit(singleFactor);
        singleFactor.struct = singleFactor.getFactor().struct;
    }

    @Override
    public void visit(MulOpTerm mulOpTerm) {
        super.visit(mulOpTerm);
        if (MJSymbolTable.intType.equals(mulOpTerm.getTerm().struct) &&
                MJSymbolTable.intType.equals(mulOpTerm.getFactor().struct)) {
            mulOpTerm.struct = MJSymbolTable.intType;
            return;
        }

        // not compatible with multiplication
        mulOpTerm.struct = MJSymbolTable.noType;

        if (!MJSymbolTable.noType.equals(mulOpTerm.getTerm().struct) &&
                !MJSymbolTable.noType.equals(mulOpTerm.getFactor().struct)) {
            report_error("Invalid types for multiplication operator", mulOpTerm);
        }
    }

    @Override
    public void visit(DesignatorFactor designatorFactor) {
        super.visit(designatorFactor);
        Designator designator = designatorFactor.getDesignator();
        designatorFactor.struct = designator.obj.getType();

        if (designatorFactor.getOptionalFunctionCall() instanceof FunctionCall) {
            // NOTE: if there are no classes this is
            // a call of a global function because there
            // are no class methods available
            String name = getLastIdentifier(designatorFactor.getDesignator());
            checkFunctionParamTypes(name, designatorFactor);
            report_usage_info("Found call of function '" + name + "'", designatorFactor, name);
        }
    }

    @Override
    public void visit(SingleIdentifier singleIdentifier) {
        super.visit(singleIdentifier);
        String identifier = singleIdentifier.getDesignator();
        Obj obj = MJSymbolTable.find(identifier);
        singleIdentifier.obj = obj;
        if (MJSymbolTable.noObj.equals(obj)) {
            report_error("Undeclared identifier '" + identifier + "'", singleIdentifier);
            return;
        }

        // usage
        String name = obj.getName();
        if (obj.getKind() == Obj.Con) {
            report_usage_info("Found global constant '" + name + "'", singleIdentifier, name);
        }
        else if (obj.getKind() == Obj.Var) {
            if (obj.getLevel() == 0) {
                // global variable
                report_usage_info("Found global variable '" + name + "'", singleIdentifier, name);
            }
            else if (obj.getLevel() == 1) {
                if (obj.getFpPos() > 0) {
                    // function argument
                    report_usage_info("Found function param '" + name + "'", singleIdentifier, name);
                }
                else {
                    // local variable
                    report_usage_info("Found local variable '" + name + "'", singleIdentifier, name);
                }
            }
        }
    }

    @Override
    public void visit(DesignatorArrayIndex designatorArrayIndex) {
        super.visit(designatorArrayIndex);
        Designator designator = designatorArrayIndex.getDesignator();
        Struct elemType = designator.obj.getType().getElemType();
        designatorArrayIndex.obj = new Obj(Obj.Elem, designator.obj.getName(), elemType);
        if (designator.obj.getType().getKind() != Struct.Array) {
            report_error("Indexing must be done on type array", designatorArrayIndex);
        }

        String name = getLastIdentifier(designator);
        report_usage_info("Found indexing of array '" + name + "'", designatorArrayIndex, name);
    }

    @Override
    public void visit(ConstantFactor constantFactor) {
        super.visit(constantFactor);
        Constant constant = constantFactor.getConstant();
        if (constant instanceof NumberConstant) {
            constantFactor.struct = MJSymbolTable.intType;
        }
        else if (constant instanceof CharConstant) {
            constantFactor.struct = MJSymbolTable.charType;
        }
        else if (constant instanceof BoolConstant) {
            constantFactor.struct = MJSymbolTable.boolType;
        }
        else {
            // should never happen
            constantFactor.struct = MJSymbolTable.noType;
            report_error("Invalid constant factor", constantFactor);
        }
    }

    @Override
    public void visit(AllocationFactor allocationFactor) {
        super.visit(allocationFactor);
        Struct type = allocationFactor.getType().struct;
        allocationFactor.struct = new Struct(Struct.Array, type);
        if (!MJSymbolTable.intType.equals(allocationFactor.getArrayIndexing().getExpr().struct)) {
            report_error("Allocation size must be of type int", allocationFactor);
        }
    }

    @Override
    public void visit(ParenthesesFactor parenthesesFactor) {
        super.visit(parenthesesFactor);
        parenthesesFactor.struct = parenthesesFactor.getExpr().struct;
    }

    private boolean isDoubleDeclaration(String identifier, int level) {
        Obj obj = MJSymbolTable.find(identifier);
        // not in symbol table or not in the same scope level
        if (MJSymbolTable.noObj.equals(obj) || obj.getLevel() != level)
            return false;

        // check if method of the same name exists in the same class
        Struct currentType = obj.getType();
        Obj foundObj = MJSymbolTable.currentScope().findSymbol(identifier);
        if (foundObj == null ||
                obj.getKind() == Obj.Meth &&
                !currentType.equals(foundObj.getType()))
            return false;

        // same scope level
        // obj found in symbol table is not a class field or
        // current declaration is not method parameter
        return obj.getKind() != Obj.Fld || !inMethodSignature;
    }

    @Override
    public void visit(MethodSignatureWithoutParams methodSignatureWithoutParams) {
        super.visit(methodSignatureWithoutParams);
        String methodName = methodSignatureWithoutParams.getMethodName();
        if (isDoubleDeclaration(methodName, 0)) {
            report_error("Identifier '" + methodName + "' already defined", methodSignatureWithoutParams);
            return;
        }

        inMethodDeclaration = true;
        inMethodSignature = true;
        Struct returnTypeStruct = new Struct(Struct.None);
        if (methodSignatureWithoutParams.getReturnType() instanceof NonVoidReturnType) {
            NonVoidReturnType returnType = (NonVoidReturnType) methodSignatureWithoutParams.getReturnType();
            returnTypeStruct = returnType.getType().struct;
        }

        currentMethod = MJSymbolTable.insert(Obj.Meth, methodName, returnTypeStruct);
        methodSignatureWithoutParams.obj = currentMethod;
        MJSymbolTable.openScope();
    }

    @Override
    public void visit(ValidMethodParams validMethodParams) {
        super.visit(validMethodParams);
        if (!inMethodDeclaration)
            return;

        MJSymbolTable.chainLocalSymbols(currentMethod);
        int numberOfFormalParams = currentMethodParams.size();
        currentMethod.setLevel(numberOfFormalParams);
    }

    @Override
    public void visit(MethodSignature MethodSignature) {
        super.visit(MethodSignature);
        inMethodSignature = false;
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        super.visit(methodDecl);
        if (!inMethodDeclaration)
            return;

        MJSymbolTable.chainLocalSymbols(currentMethod);
        MJSymbolTable.closeScope();
        currentMethodParams.clear();
        currentMethod = null;
        inMethodDeclaration = false;
    }

    @Override
    public void visit(Type type) {
        super.visit(type);
        Obj typeObj = MJSymbolTable.find(type.getTypename());
        if (typeObj.equals(MJSymbolTable.noObj)) {
            report_error("Type does not exist in the symbol table", type);
            currentDeclarationType = MJSymbolTable.noType;
        }
        else if (typeObj.getKind() != Obj.Type) {
            report_error("Identifier found is not a type", type);
            currentDeclarationType = MJSymbolTable.noType;
        }
        else {
            currentDeclarationType = typeObj.getType();
        }

        type.struct = currentDeclarationType;
    }

    @Override
    public void visit(SingleConstantDecl singleConstantDecl) {
        super.visit(singleConstantDecl);
        String constantName = singleConstantDecl.getConstantName();
        if (isDoubleDeclaration(constantName, 0)) {
            report_error("Identifier '" + constantName + "' already defined", singleConstantDecl);
            return;
        }

        int constantValue;
        Constant constant = singleConstantDecl.getConstant();
        // get constant value and type check it
        switch (currentDeclarationType.getKind()) {
            case Struct.Int:
                if (!(constant instanceof NumberConstant)) {
                    report_error("Actual constant is not of type int", constant);
                    return;
                }
                constantValue = ((NumberConstant) constant).getValue();
                break;
            case Struct.Char:
                if (!(constant instanceof CharConstant)) {
                    report_error("Actual constant is not of type char", constant);
                    return;
                }
                constantValue = ((CharConstant) constant).getValue();
                break;
            case Struct.Bool:
                if (!(constant instanceof BoolConstant)) {
                    report_error("Actual constant is not of type bool", constant);
                    return;
                }
                constantValue = ((BoolConstant) constant).getValue();
                break;
            default:
                report_error("Constant must be a builtin type", constant);
                return;
        }

        // insert the constant to the symbol table
        Obj constObj = MJSymbolTable.insert(Obj.Con, constantName, currentDeclarationType);
        constObj.setAdr(constantValue);
    }

    @Override
    public void visit(SingleVariableDecl singleVariableDecl) {
        super.visit(singleVariableDecl);
        String variableName = singleVariableDecl.getVariableName();
        int level = inMethodDeclaration ? 1 : 0;
        if (isDoubleDeclaration(variableName, level)) {
            report_error("Identifier '" + variableName + "' already defined", singleVariableDecl);
            return;
        }

        int kind = Obj.Var;

        Obj variableObj;
        if (singleVariableDecl.getOptionalArraySpecifier() instanceof ArraySpecifier) {
            // array variable declaration
            Struct arrayStruct = new Struct(Struct.Array, currentDeclarationType);
            variableObj = MJSymbolTable.insert(kind, variableName, arrayStruct);
        }
        else {
            // scalar variable declaration
            variableObj = MJSymbolTable.insert(kind, variableName, currentDeclarationType);
        }

        variableObj.setLevel(level);

        if (inMethodSignature) {
            currentMethodParams.put(variableName, variableObj);
            variableObj.setFpPos(currentMethodParams.size());
        }
    }

    @Override
    public void visit(DoWhileBodyStart doWhileBodyStart) {
        super.visit(doWhileBodyStart);
        inDoWhileBodyCount++;
    }

    @Override
    public void visit(DoWhileBodyEnd doWhileBodyEnd) {
        super.visit(doWhileBodyEnd);
        inDoWhileBodyCount--;
    }

    @Override
    public void visit(BreakStmt breakStmt) {
        super.visit(breakStmt);
        if (inDoWhileBodyCount == 0)
            report_error("break statement must not be outside of do-while", breakStmt);
    }

    @Override
    public void visit(ContinueStmt continueStmt) {
        super.visit(continueStmt);
        if (inDoWhileBodyCount == 0)
            report_error("continue statement must not be outside of do-while", continueStmt);
    }

    @Override
    public void visit(SwitchBodyStart switchBodyStart) {
        super.visit(switchBodyStart);
        inSwitchBodyCount++;
        defaultCaseBranchesCount = 0;
        switchYieldTypes.clear();
        switchCaseLabelValues.clear();
    }

    @Override
    public void visit(SwitchExprDecl switchExprDecl) {
        super.visit(switchExprDecl);
        switchExprDecl.struct = switchExprDecl.getSwitchExpr().struct;
    }

    @Override
    public void visit(SwitchExpression switchExpression) {
        super.visit(switchExpression);
        inSwitchBodyCount--;
        if (defaultCaseBranchesCount == 0) {
            report_error("Missing default case", switchExpression);
            switchExpression.struct = MJSymbolTable.noType;
            return;
        }

        if (switchYieldTypes.isEmpty()) {
            report_error("Missing yield statement in switch", switchExpression);
            switchExpression.struct = MJSymbolTable.noType;
            return;
        }

        if (!MJSymbolTable.intType.equals(switchExpression.getExpr().struct)) {
            report_error("Switch variable must be of type int", switchExpression);
            switchExpression.struct = MJSymbolTable.noType;
            return;
        }

        if (switchYieldTypes.stream().anyMatch(MJSymbolTable.noType::equals)) {
            switchExpression.struct = MJSymbolTable.noType;
            return;
        }

        // it may be better to find the LUB of the yield types
        // and set it as the switch return type
        // NOTE: if there are no classes, then there is
        // no LUB as there is no type hierarchy
        Struct switchYieldStruct = switchYieldTypes.get(0);
        if (switchYieldTypes.stream().allMatch(switchYieldStruct::equals)) {
            switchExpression.struct = switchYieldStruct;
            return;
        }

        switchExpression.struct = MJSymbolTable.noType;
        report_error("Different types in yield statements in switch", switchExpression);
    }

    @Override
    public void visit(NonDefaultCaseLabel nonDefaultCaseLabel) {
        super.visit(nonDefaultCaseLabel);
        Integer caseLabelValue = nonDefaultCaseLabel.getValue();
        if (!switchCaseLabelValues.add(caseLabelValue)) {
            report_error("Duplicate case label with value " + caseLabelValue, nonDefaultCaseLabel);
        }
    }

    @Override
    public void visit(DefaultCaseLabel defaultCaseLabel) {
        super.visit(defaultCaseLabel);
        if (++defaultCaseBranchesCount > 1) {
            report_error("Duplicate default case label", defaultCaseLabel.getParent());
        }
    }

    @Override
    public void visit(YieldStmt yieldStmt) {
        super.visit(yieldStmt);
        if (inSwitchBodyCount == 0) {
            report_error("yield statement must not be outside of switch", yieldStmt);
            return;
        }

        switchYieldTypes.add(yieldStmt.getExpr().struct);
    }

    @Override
    public void visit(ProgramHeader programHeader) {
        super.visit(programHeader);
        programHeader.obj = MJSymbolTable.insert(Obj.Prog, programHeader.getProgramName(), MJSymbolTable.noType);
        MJSymbolTable.openScope();
    }

    @Override
    public void visit(Program program) {
        super.visit(program);
        variableCount = MJSymbolTable.currentScope().getnVars();
        MJSymbolTable.chainLocalSymbols(program.getProgramHeader().obj);
        Obj mainObj = MJSymbolTable.find("main");
        MJSymbolTable.closeScope();

        // check main semantics
        if (MJSymbolTable.noObj.equals(mainObj)) {
            report_error("No main defined", null);
            return;
        }

        if (mainObj.getKind() != Obj.Meth) {
            report_error("main must be defined as a method", null);
            return;
        }

        if (mainObj.getLevel() != 0) {
            report_error("main must not take formal params", null);
        }

        if (mainObj.getType().getKind() != Struct.None) {
            report_error("main must be defined with type void", null);
        }
    }

    private final Logger log = Logger.getLogger(getClass());

    private void report_error(String message, SyntaxNode syntaxNode) {
        StringBuilder msg = new StringBuilder(message);
        int line = syntaxNode != null ? syntaxNode.getLine() : 0;
        if (syntaxNode != null)
            msg.append(" on line ").append(line);

        log.error(msg.toString());
        MJCompiler.getInstance().addError(new CompilerError(
                line,
                message,
                CompilerError.CompilerErrorType.SEMANTIC_ERROR
        ));
    }

    private void report_info(String message, SyntaxNode syntaxNode) {
        StringBuilder msg = new StringBuilder(message);
        int line = syntaxNode != null ? syntaxNode.getLine() : 0;
        if (syntaxNode != null)
            msg.append(" on line ").append(line);

        log.info(msg.toString());
    }

    private void report_usage_info(String message, SyntaxNode syntaxNode, String identifier) {
        StringBuilder msg = new StringBuilder(message);
        int line = syntaxNode != null ? syntaxNode.getLine() : 0;
        if (syntaxNode != null)
            msg.append(" on line ").append(line);

        Obj obj = MJSymbolTable.find(identifier);
        if (!MJSymbolTable.noObj.equals(obj)) {
            MJDumpSymbolTableVisitor visitor = new MJDumpSymbolTableVisitor();
            obj.accept(visitor);
            msg.append(": ").append(visitor.getOutput());
        }

        log.info(msg.toString());
    }
}
