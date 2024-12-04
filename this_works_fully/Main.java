import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.FileWriter;

public class Main {
    public static void main(String[] args) throws Exception {
        CharStream input = CharStreams.fromFileName("test.txt");
        ImperativeLangLexer lexer = new ImperativeLangLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ImperativeLangParser parser = new ImperativeLangParser(tokens);

        ParseTree tree = parser.program();

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        ParseTreeWalker walker = new ParseTreeWalker();
        Compiler interpreter = new Compiler();

        // Записываем код Jasmin в файл .j

        walker.walk(analyzer, tree);
        // System.out.println(tree.toStringTree(parser));
        walker.walk(interpreter, tree);
        String jasmineCode = interpreter.getCode();
        try (FileWriter writer = new FileWriter("Main.j")) {
            writer.write(jasmineCode);
        }
    }
}

// import org.antlr.v4.runtime.*;
// import org.antlr.v4.runtime.tree.*;

// public class Main {
// public static void main(String[] args) throws Exception {
// CharStream input = CharStreams.fromFileName(args[0]);
// ImperativeLangLexer lexer = new ImperativeLangLexer(input);
// CommonTokenStream tokens = new CommonTokenStream(lexer);
// ImperativeLangParser parser = new ImperativeLangParser(tokens);

// ParseTree tree = parser.program(); // начало парсинга
// System.out.println(tree.toStringTree(parser)); // вывод дерева
// }
// }
