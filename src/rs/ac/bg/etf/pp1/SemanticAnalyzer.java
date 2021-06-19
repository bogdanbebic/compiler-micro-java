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
    private Struct baseClass = MJSymbolTable.noType;

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
        baseClass = MJSymbolTable.noType;
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
