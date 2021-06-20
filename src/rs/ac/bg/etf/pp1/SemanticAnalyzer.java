package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.test.CompilerError;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.LinkedHashMap;
import java.util.Map;

public class SemanticAnalyzer extends VisitorAdaptor {

    private Struct currentDeclarationType = MJSymbolTable.noType;
    private Obj currentClass;
    private Obj currentMethod;
    private boolean inClassDefinition = false;
    private boolean inMethodDeclaration = false;
    private boolean inMethodSignature = false;
    private final Map<String, Obj> currentMethodParams = new LinkedHashMap<>();
    private Struct baseClass = null;
    private boolean inDoWhileBody = false;
    private boolean inSwitchBody = false;
    private int defaultCaseBranchesCount = 0;

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
        designatorFactor.struct = designatorFactor.getDesignator().struct;
    }

    @Override
    public void visit(SingleIdentifier singleIdentifier) {
        super.visit(singleIdentifier);
        String identifier = singleIdentifier.getDesignator();
        Obj obj = MJSymbolTable.find(identifier);
        if (MJSymbolTable.noObj.equals(obj)) {
            report_error("Undeclared identifier '" + identifier + "'", singleIdentifier);
            return;
        }

        singleIdentifier.struct = obj.getType();
    }

    @Override
    public void visit(DesignatorMemberAccess designatorMemberAccess) {
        super.visit(designatorMemberAccess);
        designatorMemberAccess.struct = MJSymbolTable.noType;
    }

    @Override
    public void visit(DesignatorArrayIndex designatorArrayIndex) {
        super.visit(designatorArrayIndex);
        designatorArrayIndex.struct = designatorArrayIndex.getDesignator().struct.getElemType();
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
        if (allocationFactor.getOptionalArrayIndexing() instanceof ArrayIndex) {
            allocationFactor.struct = new Struct(Struct.Array, type);
        }
        else if (allocationFactor.getOptionalArrayIndexing() instanceof NoArrayIndexing) {
            allocationFactor.struct = type;
        }
        else {
            // should never happen
            allocationFactor.struct = MJSymbolTable.noType;
            report_error("Invalid allocation factor", allocationFactor);
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
                inClassDefinition &&
                obj.getKind() == Obj.Meth &&
                !currentType.equals(foundObj.getType()))
            return false;

        // same scope level
        // obj found in symbol table is not a class field or
        // current declaration is not method parameter
        return obj.getKind() != Obj.Fld || !inMethodSignature;
    }

    private void visitClassDecl(String className, ClassDeclStart classDeclStart) {
        if (isDoubleDeclaration(className, 0)) {
            report_error("Identifier '" + className + "' already defined", classDeclStart);
            return;
        }

        inClassDefinition = true;
        currentClass = MJSymbolTable.insert(Obj.Type, className, new Struct(Struct.Class));
        MJSymbolTable.openScope();
    }

    @Override
    public void visit(ClassDeclarationStart classDeclarationStart) {
        super.visit(classDeclarationStart);
        String className = classDeclarationStart.getClassName();
        visitClassDecl(className, classDeclarationStart);
    }

    @Override
    public void visit(ErroneousInheritance erroneousInheritance) {
        super.visit(erroneousInheritance);
        String className = erroneousInheritance.getClassName();
        visitClassDecl(className, erroneousInheritance);
    }

    @Override
    public void visit(InheritanceDecl inheritanceDecl) {
        super.visit(inheritanceDecl);
        Type type = inheritanceDecl.getType();
        String typename = type.getTypename();
        if (type.struct.getKind() != Struct.Class) {
            report_error("Type '" + typename + "' is not a valid base class", inheritanceDecl);
            return;
        }

        baseClass = type.struct;
    }

    @Override
    public void visit(NoInheritanceDecl NoInheritanceDecl) {
        super.visit(NoInheritanceDecl);
        baseClass = null;
    }

    @Override
    public void visit(ClassDeclEnd classDeclEnd) {
        super.visit(classDeclEnd);
        if (!inClassDefinition)
            return;

        MJSymbolTable.chainLocalSymbols(currentClass.getType());
        MJSymbolTable.closeScope();
        currentClass.getType().setElementType(baseClass);
        currentClass = null;
        inClassDefinition = false;
    }

    @Override
    public void visit(MethodSignatureWithoutParams methodSignatureWithoutParams) {
        super.visit(methodSignatureWithoutParams);
        String methodName = methodSignatureWithoutParams.getMethodName();
        int level = inClassDefinition ? 1 : 0;
        if (isDoubleDeclaration(methodName, level)) {
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
                constantValue = Character.getNumericValue(((CharConstant) constant).getValue());
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
        int level = inClassDefinition || inMethodDeclaration ? 1 : 0;
        if (isDoubleDeclaration(variableName, level)) {
            report_error("Identifier '" + variableName + "' already defined", singleVariableDecl);
            return;
        }

        int kind = inClassDefinition && !inMethodDeclaration ? Obj.Fld : Obj.Var;

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

        if (inMethodSignature) {
            currentMethodParams.put(variableName, variableObj);
        }
    }

    @Override
    public void visit(DoWhileBodyStart doWhileBodyStart) {
        super.visit(doWhileBodyStart);
        inDoWhileBody = true;
    }

    @Override
    public void visit(DoWhileBodyEnd doWhileBodyEnd) {
        super.visit(doWhileBodyEnd);
        inDoWhileBody = false;
    }

    @Override
    public void visit(BreakStmt breakStmt) {
        super.visit(breakStmt);
        if (!inDoWhileBody)
            report_error("break statement must not be outside of do-while", breakStmt);
    }

    @Override
    public void visit(ContinueStmt continueStmt) {
        super.visit(continueStmt);
        if (!inDoWhileBody)
            report_error("continue statement must not be outside of do-while", continueStmt);
    }

    @Override
    public void visit(SwitchBodyStart switchBodyStart) {
        super.visit(switchBodyStart);
        inSwitchBody = true;
        defaultCaseBranchesCount = 0;
    }

    @Override
    public void visit(SwitchExpression switchExpression) {
        super.visit(switchExpression);
        inSwitchBody = false;
        if (defaultCaseBranchesCount == 0) {
            report_error("Missing default case", switchExpression);
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
        if (!inSwitchBody) {
            report_error("yield statement must not be outside of switch", yieldStmt);
        }
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
        MJCompiler.getInstance().addError(new CompilerError(
                line,
                message,
                CompilerError.CompilerErrorType.SEMANTIC_ERROR
        ));
    }

}
