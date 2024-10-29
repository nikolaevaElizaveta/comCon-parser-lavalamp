import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class Main {
    public static void main(String[] args) throws Exception {
        CharStream input = CharStreams.fromFileName("test.txt");
        ImperativeLangLexer lexer = new ImperativeLangLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ImperativeLangParser parser = new ImperativeLangParser(tokens);

        ParseTree tree = parser.program();

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(analyzer, tree);
    }
}

// import org.antlr.v4.runtime.*;
// import org.antlr.v4.runtime.tree.*;

// public class Main {
//     public static void main(String[] args) throws Exception {
//         CharStream input = CharStreams.fromFileName(args[0]);
//         ImperativeLangLexer lexer = new ImperativeLangLexer(input);
//         CommonTokenStream tokens = new CommonTokenStream(lexer);
//         ImperativeLangParser parser = new ImperativeLangParser(tokens);
        
//         ParseTree tree = parser.program(); // начало парсинга
//         System.out.println(tree.toStringTree(parser)); // вывод дерева
//     }
// }
