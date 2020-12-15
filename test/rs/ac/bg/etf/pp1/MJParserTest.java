package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;

import java.io.*;

public class MJParserTest {

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
    }

    public static void main(String[] args) throws Exception {

        Logger log = Logger.getLogger(MJParserTest.class);

        Reader br = null;
        try {
            File sourceCode = new File("test/program.mj");
            log.info("Compiling source file: " + sourceCode.getAbsolutePath());

            br = new BufferedReader(new FileReader(sourceCode));
            Lexer lexer = new Lexer(br);

            MJParser p = new MJParser(lexer);
            Symbol s = p.parse();

            Program program = (Program)(s.value);
            // log syntax tree
            log.info(program.toString(""));
        }
        finally {
            if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
        }

    }


}
