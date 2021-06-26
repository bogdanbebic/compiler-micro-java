package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.test.Compiler;
import rs.ac.bg.etf.pp1.test.CompilerError;
import rs.ac.bg.etf.pp1.util.Log4JUtils;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MJCompiler implements Compiler {

    private final List<CompilerError> errors = new ArrayList<>();

    public void addError(CompilerError error) {
        this.errors.add(error);
    }

    private boolean hasCompileTimeErrors() {
        return !this.errors.isEmpty();
    }

    private static final Logger log;

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
        log = Logger.getLogger(MJCompiler.class);
    }

    private static final MJCompiler instance = new MJCompiler();

    public static MJCompiler getInstance() {
        return instance;
    }

    @Override
    public List<CompilerError> compile(String sourceFilePath, String outputFilePath) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(sourceFilePath))) {
            Lexer lexer = new Lexer(reader);
            Parser parser = new Parser(lexer);
            Symbol symbol = parser.parse();
            Program program = (Program) symbol.value;

            MJSymbolTable.init();
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
            program.traverseBottomUp(semanticAnalyzer);

            MJSymbolTable.dump(new MJDumpSymbolTableVisitor());

            if (this.hasCompileTimeErrors()) {
                return errors;
            }

            // TODO: code generation
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return errors;
    }

    private static final String usage = "Usage: MJCompiler sourceFilePath outputFilePath\n" +
            "\tsourceFilePath - micro java source file to be compiled\n" +
            "\toutputFilePath - place the output into outputFilePath\n";

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            log.error("Invalid arguments, 2 file paths required");
            log.info(usage);
            return;
        }

        List<CompilerError> errors = getInstance().compile(args[0], args[1]);
        for (CompilerError error : errors) {
            log.error(error);
        }
    }

}
