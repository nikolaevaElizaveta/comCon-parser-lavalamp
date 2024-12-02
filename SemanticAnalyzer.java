import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.Stack;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class SemanticAnalyzer extends ImperativeLangBaseListener {

    private final Map<String, String> declaredVariables = new HashMap<>();
    private final Set<String> declaredTypes = new HashSet<>();
    private final Set<String> usedVariables = new HashSet<>();
    private final Map<String, String> variableTypes = new HashMap<>();
    private ParseTreeProperty<String> simplifiedExpressions = new ParseTreeProperty<>();
    Map<String, List<String>> routineParameters = new HashMap<>();
    private Map<String, String> routineReturnTypes = new HashMap<>(); // Для хранения типов возвращаемых значений
    private Stack<String> routineStack = new Stack<>();

    // 1. Declarations Before Usage
    @Override
    public void enterVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx) {
        String varName = ctx.IDENTIFIER().getText(); // Получаем имя переменной
        String varType;

        // Проверяем, указан ли тип
        if (ctx.type() != null) {
            varType = ctx.type().getText(); // Получаем текст типа
        } else {
            varType = "unknown"; // Если тип не указан
        }

        // Добавляем в словарь
        declaredVariables.put(varName, varType);

        if (ctx.expression() != null) {
            usedVariables.add(varName);
        }

        System.out.println("Declared Variable: " + varName + ", Type: " + varType);
    }

    @Override
    public void enterTypeDeclaration(ImperativeLangParser.TypeDeclarationContext ctx) {
        String typeName = ctx.IDENTIFIER().getText();
        Map<String, String> fields = new HashMap<>();

        // Получаем текст типа и проверяем, является ли он записью
        String typeText = ctx.type().getText();
        if (typeText.startsWith("record")) {
            // Убираем "record", "{", "}", и "end" из строки
            String fieldsText = typeText
                    .replace("record", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace("end", "")
                    .trim();

            // Разделяем поля по ключевому слову "var"
            String[] fieldDeclarations = fieldsText.split("var");
            for (String fieldDecl : fieldDeclarations) {
                fieldDecl = fieldDecl.trim();
                if (!fieldDecl.isEmpty()) {
                    // Разделяем по двоеточию, чтобы получить имя и тип поля
                    String[] parts = fieldDecl.split(":");
                    if (parts.length == 2) {
                        String fieldName = parts[0].trim();
                        String fieldType = parts[1].trim();
                        fields.put(fieldName, fieldType);
                    }
                }
            }

            recordTypes.put(typeName, fields);
            System.out.println("Declared record type: " + typeName + ", Fields: " + fields);
        }
    }

    @Override
    public void enterRoutineDeclaration(ImperativeLangParser.RoutineDeclarationContext ctx) {
        String routineName = ctx.IDENTIFIER().getText();
        routineStack.push(routineName);
        List<String> parameterTypes = new ArrayList<>();

        if (ctx.parameters() != null) {
            for (ImperativeLangParser.ParameterDeclarationContext paramCtx : ctx.parameters().parameterDeclaration()) {
                String paramType = mapType(paramCtx.type().getText()); // Предполагается, что mapType преобразует тип в
                                                                       // строку
                parameterTypes.add(paramType);

                // Запоминаем параметр как переменную
                declaredVariables.put(paramCtx.IDENTIFIER().getText(), paramType);
                System.out.println("Declared parameter: " + paramCtx.IDENTIFIER().getText() + ", Type: " + paramType);
            }
        }
        String returnType = ctx.type() != null ? mapType(ctx.type().getText()) : "void";
        routineReturnTypes.put(routineName, returnType);
        routineParameters.put(routineName, parameterTypes);
        System.out.println("Declared routine: " + routineName + ", Parameter types: " + parameterTypes
                + ", Return type: " + returnType);

    }

    @Override
    public void exitRoutineDeclaration(ImperativeLangParser.RoutineDeclarationContext ctx) {
        if (!routineStack.isEmpty()) {
            routineStack.pop(); // Pop the routine name from the stack
        }
    }

    private String getCurrentRoutineName(ParseTree ctx) {
        if (!routineStack.isEmpty()) {
            return routineStack.peek(); // Return the current routine name
        }
        return null; // Or handle the case where there is no current routine
    }

    @Override
    public void enterRoutineCall(ImperativeLangParser.RoutineCallContext ctx) {
        String routineName = ctx.IDENTIFIER().getText();
        List<String> argumentTypes = new ArrayList<>();

        if (ctx.expressionList() != null) {
            for (ImperativeLangParser.ExpressionContext exprCtx : ctx.expressionList().expression()) {
                String argType = determineExpressionType(exprCtx); // Метод для определения типа выражения
                argumentTypes.add(argType);

                if (exprCtx.getChild(0) instanceof TerminalNode) {
                    String varName = exprCtx.getText();
                    if (declaredVariables.containsKey(varName)) {
                        usedVariables.add(varName); // Добавляем в использованные
                    }
                }
            }

        }

        // System.out.println("enterRoutineCall " + routineName);

        // Сравниваем аргументы с параметрами
        List<String> expectedParamTypes = routineParameters.get(routineName);
        if (expectedParamTypes != null) {

            if (expectedParamTypes.size() != argumentTypes.size()) {
                System.out.println("Error: Type mismatch in routine call to " + routineName + ": expected " +
                        expectedParamTypes.size() + " arguments, but got " + argumentTypes.size());
            } else {
                for (int i = 0; i < expectedParamTypes.size(); i++) {
                    if (!expectedParamTypes.get(i).equals(argumentTypes.get(i))) {
                        System.out.println("Error: Type mismatch for parameter " + (i + 1) +
                                " in routine call to " + routineName + ": expected " +
                                expectedParamTypes.get(i) + ", but got " + argumentTypes.get(i));
                    } else {
                        System.out.println("Type match for parameter " + (i + 1) +
                                " in routine call to " + routineName + ": expected " +
                                expectedParamTypes.get(i) + ", got " + argumentTypes.get(i));
                    }
                }
            }
        }
    }

    // Accessing record fields
    @Override
    public void enterModifiablePrimary(ImperativeLangParser.ModifiablePrimaryContext ctx) {
        
        if (ctx.IDENTIFIER().size() > 1) { // Accessing a field, e.g., s.age
            String varName = ctx.IDENTIFIER(0).getText();
            String fieldName = ctx.IDENTIFIER(1).getText();

            // Проверяем, что переменная объявлена
            if (!declaredVariables.containsKey(varName)) {
                System.out.println("Error: Variable " + varName + " used before declaration.");
            } else {
                String varType = getVariableType(varName);

                // Проверяем, что тип переменной - это запись
                if (recordTypes.containsKey(varType)) {
                    Map<String, String> fields = recordTypes.get(varType);
                    // Проверяем, что поле существует в записи
                    if (!fields.containsKey(fieldName)) {
                        System.out.println("Error: Field " + fieldName + " not found in type " + varType);
                    }
                } else {
                    System.out.println("Error: Variable " + varName + " is not a record type.");
                }

                // Добавляем переменную в список использованных
                usedVariables.add(varName);
            }
        }
    }

    // 2. Type Checking
    @Override
    public void enterAssignment(ImperativeLangParser.AssignmentContext ctx) {
        List<TerminalNode> identifiers = ctx.modifiablePrimary().IDENTIFIER();
        String varName = identifiers.get(0).getText(); // Основное имя переменной (например, s для s.age)
        usedVariables.add(varName);
        String fieldName = identifiers.size() > 1 ? identifiers.get(1).getText() : null; // Имя поля, если есть
                                                                                         // (например, age)

        // Проверка, объявлена ли переменная
        if (!declaredVariables.containsKey(varName)) {
            System.out.println("Error: Variable " + varName + " not declared before assignment.");
            return;
        }

        // Получаем тип переменной или поля записи
        String variableType;
        if (fieldName != null) {
            // Если это поле записи, получаем тип записи
            String recordType = getVariableType(varName);
            Map<String, String> fields = recordTypes.get(recordType);
            if (fields == null || !fields.containsKey(fieldName)) {
                System.out.println("Error: Field " + fieldName + " does not exist in record " + recordType);
                return;
            }
            variableType = fields.get(fieldName); // Тип поля записи
        } else {
            // Если это обычная переменная, получаем её тип
            variableType = getVariableType(varName);
        }

        if (variableType.contains("array[")){
            variableType = variableType.replaceAll("array\\[.*?\\]", "");
        }

        // Анализ типа присваиваемого выражения
        System.out.println("Analyzing expression: " + ctx.expression().getText());
        String assignedType = determineExpressionType(ctx.expression());
        String assignedValue = ctx.expression().getText();

        // Проверка типа и вывод результатов
        if (!isAssignmentValid(variableType, assignedType, assignedValue)) {
            System.out.println(
                    "Error: Type mismatch for variable " + varName + (fieldName != null ? "." + fieldName : "") +
                            ": expected " + variableType + ", but got " + assignedType);
        } else {
            System.out.println("Type match for " + varName + (fieldName != null ? "." + fieldName : "") +
                    ": " + variableType + ", " + assignedType);
        }
    }

    private String determineExpressionType(ImperativeLangParser.ExpressionContext ctx) {

        // Обработка выражения
        if (ctx.getChildCount() == 1) {
            // Если есть только один дочерний элемент, это может быть relation
            ParseTree relation = ctx.getChild(0);

            // Вызов метода для определения типа релейшн
            return determineRelationType((ImperativeLangParser.RelationContext) relation);
        } else {
            // Обработка сложного выражения с логическими операторами (and, or, xor)
            String type = determineRelationType((ImperativeLangParser.RelationContext) ctx.getChild(0)); // Вызов для
                                                                                                         // первого
                                                                                                         // relation
            for (int i = 1; i < ctx.getChildCount(); i += 2) {
                String operator = ctx.getChild(i).getText(); // Оператор
                String nextType = determineRelationType((ImperativeLangParser.RelationContext) ctx.getChild(i + 1));

                // Проверка совместимости типов для логических операторов
                if (!type.equals(nextType)) {
                    System.out.println("error at determineExpressionType");
                    return "unknown"; // Ошибка, если типы не совпадают
                }
            }
            return "boolean"; // Возвращаем "boolean" для выражения с логическими операторами
        }
    }

    private String determineRelationType(ImperativeLangParser.RelationContext ctx) {
        if (ctx.getChildCount() == 1) {
            // Если это только simple, определяем его тип
            return determineSimpleType(ctx.simple(0));
        } else if (ctx.getChildCount() == 3) {
            // Если это relation с оператором
            String leftType = determineSimpleType(ctx.simple(0));
            String operator = ctx.getChild(1).getText(); // Оператор
            String rightType = determineSimpleType(ctx.simple(1));
            if (leftType.contains("array[")){
                leftType = leftType.replaceAll("array\\[.*?\\]", "");
            }
            if (rightType.contains("array[")){
                rightType = rightType.replaceAll("array\\[.*?\\]", "");
            }

            // Проверяем, совместимы ли типы с релейшн
            if (leftType.equals(rightType) && (leftType.equals("integer") || leftType.equals("real"))) {
                return "boolean"; // Возвращаем boolean для релейшн
            } else {
                System.out.println("error at determine relation type");
                return "unknown"; // Ошибка совместимости типов
            }
        }

        return "unknown"; // Возвращаем "unknown", если тип не может быть определен
    }

    private String determineSimpleType(ParseTree simple) {
        if (simple.getChildCount() == 1) {
            ParseTree factor = simple.getChild(0);
            return determineFactorType(factor);
        } else if (simple.getChildCount() > 1) {
            String leftType = determineFactorType(simple.getChild(0));
            for (int i = 1; i < simple.getChildCount(); i += 2) {
                String operator = simple.getChild(i).getText(); // Оператор
                String rightType = determineFactorType(simple.getChild(i + 1));
                // System.out.println(leftType + " " + rightType);
                // Для простых операторов (например, *, /, %)
                if (leftType.contains("array[")){
                    leftType = leftType.replaceAll("array\\[.*?\\]", "");
                }
                if (rightType.contains("array[")){
                    rightType = rightType.replaceAll("array\\[.*?\\]", "");
                }
                if (!leftType.equals(rightType)) {
                    System.out.println("error at determineSimpleType");

                    return "unknown"; // Ошибка, если типы не совпадают
                }
                leftType = leftType.equals("integer") ? "integer" : "real"; // Принимаем тип
            }
            return leftType; // Возвращаем тип простого выражения
        }
        return "unknown"; // Если тип не может быть определен
    }

    private String determineFactorType(ParseTree factor) {
        // System.out.println("factor " + factor.getText() + " "+
        // factor.getChildCount());

        if (factor.getChildCount() == 1) {
            ParseTree summand = factor.getChild(0);
            return determineSummandType(summand);
        } else if (factor.getChildCount() > 1) {
            String leftType = determineSummandType(factor.getChild(0));
            for (int i = 1; i < factor.getChildCount(); i += 2) {
                String operator = factor.getChild(i).getText(); // Оператор
                String rightType = determineSummandType(factor.getChild(i + 1));
                if (leftType.contains("array[")){
                    leftType = leftType.replaceAll("array\\[.*?\\]", "");
                }
                if (rightType.contains("array[")){
                    rightType = rightType.replaceAll("array\\[.*?\\]", "");
                }

                // Для операторов + и -
                if (!leftType.equals(rightType)) {
                    System.out.println("error at determineFactorType" + leftType + " " + rightType);

                    return "unknown"; // Ошибка, если типы не совпадают
                }
                leftType = leftType.equals("integer") ? "integer" : "real"; // Принимаем тип
            }
            return leftType; // Возвращаем тип фактора
        }
        return "unknown"; // Если тип не может быть определен
    }

    private String determineSummandType(ParseTree summand) {
        // System.out.println("summand " + summand.getText() + " "+
        // summand.getChildCount());

        if (summand.getChildCount() == 1) {
            ParseTree primary = summand.getChild(0);
            return determinePrimaryType(primary);
        } else if (summand.getChildCount() == 3) {
            // Обработка (expression)
            return determineExpressionType((ImperativeLangParser.ExpressionContext) summand.getChild(1));
        }
        System.out.println("error at determineSummandType");

        return "unknown"; // Если тип не может быть определен
    }

    private String determinePrimaryType(ParseTree primary) {
        System.out.println("primary:" + primary.getText());
        if (primary.getChildCount() == 1) {
            String text = primary.getText();
            if (text.matches("\\d+")) {
                // System.out.println(text +" integer");
                return "integer"; // Integer literal
            } else if (text.matches("\\d+\\.\\d+")) {
                // System.out.println(text + " real");
                return "real"; // Real literal
            } else if (text.equals("true") || text.equals("false")) {
                // System.out.println(text + " bool");
                return "boolean"; // Boolean literal
            }

            // Check identifiers
            String varType = getVariableType(text);
            if (varType != null)
                return varType;
        }
        System.out.println("error at determinePrimaryType");

        return "unknown"; // If type cannot be determined
    }

    private boolean isAssignmentValid(String variableType, String assignedType, String assignedValue) {
        if (variableType.equals(assignedType)) {
            return true;
        }

        if (variableType.equals("integer")) {
            if (assignedType.equals("real"))
                return true; // copying with rounding to nearest integer
            if (assignedType.equals("boolean"))
                return true; // true is converted to integer 1, false is converted to integer 0
        }

        if (variableType.equals("real")) {
            if (assignedType.equals("integer"))
                return true; // integer is generalized to real
            if (assignedType.equals("boolean"))
                return true; // true is converted to real 1.0, false – to real 0.0
        }

        if (variableType.equals("boolean")) {
            if (assignedType.equals("integer")) {
                return assignedValue.equals("0") || assignedValue.equals("1");
            }
            if (assignedType.equals("real"))
                return false;
        }

        return false;
    }


    private String getVariableType(String varName) {
        String[] parts = varName.split("\\.");
        boolean isComplex = true;
        try {
            if (declaredVariables.containsKey(parts[1])) {
                String varType = declaredVariables.get(parts[0]);  // Получаем тип переменной
                // Проверяем, является ли varName записью и содержит ли поле
                if (parts.length == 2) {
                    String recordName = parts[0];
                    String fieldName = parts[1];
                    // Проверяем, является ли recordName записью, и есть ли у неё поле fieldName
                    if (recordTypes.containsKey(varType) && recordTypes.get(varType).containsKey(fieldName)) {
                        usedVariables.add(parts[1]);
                        System.out.println(parts[0] + " record with field " + parts[1]);
                        return recordTypes.get(varType).get(fieldName);  // Возвращаем тип поля
                    } else {
                        System.out.println("Error: Field " + fieldName + " not found in type " + varType);
                        return "unknown";
                    }
                }
                return varType; // Возвращаем тип, если это не поле записи
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            if (declaredVariables.containsKey(varName)) {
                usedVariables.add(varName);
                return declaredVariables.get(varName); // Возвращаем тип, если это не поле записи
            }
        }
        try {
            String[] array = varName.split("\\[");
            String arrayIndex = array[1].split("\\]")[0];
            if (declaredVariables.containsKey(array[0])) {
                return declaredVariables.get(array[0]); // Возвращаем тип, если это не поле записи
            }
            if (declaredVariables.containsKey(arrayIndex)) {
                return declaredVariables.get(arrayIndex); // Возвращаем тип, если это не поле записи
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }
        System.out.println("Error: Variable " + varName + " not declared.");
        return "unknown";
    }

    public void deleteVariable(String varName) {
        declaredVariables.remove(varName);
    }

    @Override
    public void exitForLoop(ImperativeLangParser.ForLoopContext ctx) {  // For loop exiting, memory cleaning
        String iteratorName = ctx.IDENTIFIER().getText();
        deleteVariable(iteratorName);
    }

    // private String getVariableType(String varName) {
    //     if (declaredVariables.containsKey(varName)) {
    //         String varType = declaredVariables.get(varName); // Получаем тип переменной

    //         // Проверяем, является ли varName записью и содержит ли поле
    //         String[] parts = varName.split("\\.");
    //         if (parts.length == 2) {
    //             String recordName = parts[0];
    //             String fieldName = parts[1];

    //             // Проверяем, является ли recordName записью, и есть ли у неё поле fieldName
    //             if (recordTypes.containsKey(varType) && recordTypes.get(varType).containsKey(fieldName)) {
    //                 return recordTypes.get(varType).get(fieldName); // Возвращаем тип поля
    //             } else {
    //                 System.out.println("Error: Field " + fieldName + " not found in type " + varType);
    //                 return "unknown";
    //             }   
    //         }
    //         return varType; // Возвращаем тип, если это не поле записи
    //     }
    //     System.out.println("Error: Variable " + varName + " not declared.");
    //     return "unknown"; // Если переменная не найдена, возвращаем "unknown"
    // }

    public void declareVariable(String varName, String type) {
        declaredVariables.put(varName, type); // Кладем имя переменной и ее тип в словарь
    }

    private boolean isProcessed = false;
    int skip = 0;

    @Override
    public void enterWhileLoop(ImperativeLangParser.WhileLoopContext ctx) {
        // System.out.println("ctx");
        String exprType = determineExpressionType(ctx.expression());
        if (!exprType.equals("boolean")) {
            System.out.println("Error: 'while' loop condition must be boolean, found " + exprType);
        }
    }

    @Override
    public void enterForLoop(ImperativeLangParser.ForLoopContext ctx) {
        String iteratorName = ctx.IDENTIFIER().getText();
        declareVariable(iteratorName, "integer");
        System.out.println(iteratorName + " is int");
    }

    @Override
    public void enterIfStatement(ImperativeLangParser.IfStatementContext ctx) {
        String exprType = determineExpressionType(ctx.expression());
        if (!exprType.equals("boolean")) {
            System.out.println("Error: 'if' condition must be boolean, found " + exprType);
        }
    }

    @Override
    public void enterReturnStatement(ImperativeLangParser.ReturnStatementContext ctx) {
        String exprType = determineExpressionType(ctx.expression());
        // Get the current routine's name from the context or a stored variable
        String routineName = getCurrentRoutineName(ctx);
        // Retrieve the expected return type for the routine
        String expectedReturnType = routineReturnTypes.get(routineName);
        // Compare the expression type with the expected return type
        if (!exprType.equals(expectedReturnType)) {
            System.out.println("Error: Return statement in routine " + routineName + " is of type: " + exprType +
                    "\nexpected: " + expectedReturnType);
        } else {
            System.out.println("Return type matches for routine " + routineName + ": " + exprType);
        }
    }

    // 3. Simplification Logic
    @Override
    public void enterExpression(ImperativeLangParser.ExpressionContext ctx) {
        if (skip <= 0) {
            System.out.println("\n" + ctx.getText());
            if (isConstantExpression(ctx)) {
                // System.out.println("CONSTANT");
                String simplifiedValue = simplifyConstantExpression(ctx);
                // System.out.println("Simplified constant expression to: " + simplifiedValue);

                replaceExpression(ctx, simplifiedValue);
                isProcessed = true;
            } else {
                System.out.println("NOT CONSTANT");
            }
        } else {
            skip--;
        }

    }

    private void replaceExpression(ImperativeLangParser.ExpressionContext ctx, String simplifiedValue) {
        // Создаем новый узел expression
        ImperativeLangParser.ExpressionContext newExpressionCtx = createExpressionNode(simplifiedValue);
        System.out.println(newExpressionCtx.relation());

        if (ctx.getParent() != null) {
            int index = ctx.getParent().children.indexOf(ctx);
            ctx.getParent().children.set(index, newExpressionCtx);
            System.out.println("Replaced expression with simplified value: " + simplifiedValue);
        }
    }

    private ImperativeLangParser.ExpressionContext createExpressionNode(String value) {
        ImperativeLangParser.ExpressionContext expressionCtx = new ImperativeLangParser.ExpressionContext(null, -1);

        // Создаем relation
        ImperativeLangParser.RelationContext relationCtx = new ImperativeLangParser.RelationContext(expressionCtx, -1);
        expressionCtx.addChild(relationCtx);

        // Создаем simple
        ImperativeLangParser.SimpleContext simpleCtx = new ImperativeLangParser.SimpleContext(relationCtx, -1);
        relationCtx.addChild(simpleCtx);

        // Создаем factor
        ImperativeLangParser.FactorContext factorCtx = new ImperativeLangParser.FactorContext(simpleCtx, -1);
        simpleCtx.addChild(factorCtx);

        // Создаем summand
        ImperativeLangParser.SummandContext summandCtx = new ImperativeLangParser.SummandContext(factorCtx, -1);
        factorCtx.addChild(summandCtx);

        // Создаем primary
        ImperativeLangParser.PrimaryContext primaryCtx = createPrimaryNode(value);
        summandCtx.addChild(primaryCtx);

        return expressionCtx;
    }

    private ImperativeLangParser.PrimaryContext createPrimaryNode(String value) {
        ImperativeLangParser.PrimaryContext primaryCtx = new ImperativeLangParser.PrimaryContext(null, -1);

        // Определяем тип primary на основе значения
        if (value.matches("\\d+")) {
            System.out.println("the result is integer");
            // Целое число
            primaryCtx.addChild(createTerminalNode(ImperativeLangParser.INTEGER_LITERAL, value));
        } else if (value.matches("\\d+\\.\\d+")) {
            System.out.println("the result is double");
            // Дробное число
            primaryCtx.addChild(createTerminalNode(ImperativeLangParser.REAL_LITERAL, value));
        } else if ("true".equals(value)) {
            // Логическое значение как ключевое слово
            primaryCtx.addChild(createTerminalNode(ImperativeLangParser.INTEGER_LITERAL, "1"));
        } else if ("false".equals(value)){
            primaryCtx.addChild(createTerminalNode(ImperativeLangParser.INTEGER_LITERAL, "0"));        
        } else {
            throw new IllegalArgumentException("Unsupported primary value: " + value);
        }

        return primaryCtx;
    }


    private TerminalNode createTerminalNode(int tokenType, String value) {
        return new TerminalNodeImpl(new CommonToken(tokenType, value));
    }
    // private TerminalNodeImpl createTerminalNode(String value) {
    //     // Создаем токен на основе ключевого слова или литерала
    //     Token token = new CommonToken(value.equals("true") || value.equals("false") 
    //         ? ImperativeLangParser.TRUE 
    //         : ImperativeLangParser.FALSE, value);
    //     return new TerminalNodeImpl(token);
        
    // }

    

    private int getTokenType(String tokenType) {
        return switch (tokenType) {
            case "INTEGER_LITERAL" -> ImperativeLangParser.INTEGER_LITERAL;
            case "REAL_LITERAL" -> ImperativeLangParser.REAL_LITERAL;
            // case "BOOLEAN_LITERAL":
            //     return ImperativeLangParser.BOOLEAN_LITERAL;
            default -> throw new IllegalArgumentException("Unknown token type: " + tokenType);
        };
    }

    // private void replaceExpression(ImperativeLangParser.ExpressionContext ctx, String simplifiedValue) {
    //     TerminalNode newNode = createTerminalNode(simplifiedValue);

    //     if (ctx.getParent() != null) {
    //         int index = ctx.getParent().children.indexOf(ctx);
    //         // ctx.getParent().children.set(index, newNode);
    //         System.out.println("Replaced expression with simplified value: " + simplifiedValue);
    //     }
    // }

    // private TerminalNode createTerminalNode(String value) {
    //     return new TerminalNodeImpl(new CommonToken(ImperativeLangParser.REAL_LITERAL, value));
    // }

    private boolean isConstantExpression(ImperativeLangParser.ExpressionContext ctx) {
        int openBrackets = 0;
        // System.out.println("here" + ctx.getText());
        for (int i1 = 0; i1 < ctx.getChildCount(); i1 = i1 + 2) { // all relations
            ParseTree relation = ctx.getChild(i1);

            for (int i2 = 0; i2 < relation.getChildCount(); i2 += 2) { // all simple
                ParseTree simple = relation.getChild(i2);

                for (int i3 = 0; i3 < simple.getChildCount(); i3 += 2) { // all factor
                    ParseTree factor = simple.getChild(i3);

                    for (int i4 = 0; i4 < factor.getChildCount(); i4 += 2) { // all summand
                        ParseTree summand = factor.getChild(i4);

                        for (int i5 = 0; i5 < summand.getChildCount(); i5 += 2) { // all primary
                            String primary = summand.getChild(i5).getText();

                            if (!primary.matches("\\d+") && !primary.matches("\\d+\\.\\d+") &&
                                    !primary.equals("true") && !primary.equals("false")) {

                                if (primary.equals("(")) {
                                    openBrackets++;
                                }
                                // Проверяем, является ли это выражением с операторами или скобками
                                if (!primary.equals("(") &&
                                        !primary.equals(")") &&
                                        !primary.equals("and") &&
                                        !primary.equals("or") &&
                                        !primary.equals("xor") &&
                                        !primary.equals("||") &&
                                        !primary.equals("&&")) {
                                    skip = -1;
                                    return false; // Если есть скобки или логические операторы, возвращаем false

                                }

                                // System.out.println(primary);

                                // return false; // Если любой дочерний элемент не является константой, вернуть
                                // false
                            }
                        }
                    }
                }
            }
        }
        skip = openBrackets;
        return true; // All children are constants
    }

    private String simplifyConstantExpression(ImperativeLangParser.ExpressionContext ctx) {
        String expression = ctx.getText().replaceAll("\\s+", "");
        // System.out.println("Expression: " + expression);
        if (expression.length() == 1) {
            return expression;
        }

        Stack<Double> numbers = new Stack<>();
        Stack<String> operations = new Stack<>();
        StringBuilder currentNumber = new StringBuilder();

        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            // System.out.println("Processing character: " + ch);

            if (Character.isDigit(ch) || ch == '.') {
                currentNumber.append(ch);
                // System.out.println("Building number: " + currentNumber);
            } else {
                if (currentNumber.length() > 0) {
                    numbers.push(Double.parseDouble(currentNumber.toString()));
                    // System.out.println("Pushed number to stack: " + currentNumber);
                    currentNumber.setLength(0);
                }

                if (ch == 't' || ch == 'f') {
                    numbers.push(ch == 't' ? 1.0 : 0.0);
                    // System.out.println("Pushed boolean value to stack: " + (ch == 't' ? 1.0 :
                    // 0.0));
                } else if (ch == '(') {
                    operations.push(String.valueOf(ch));
                    // System.out.println("Pushed '(' to operations stack");
                } else if (ch == ')') {
                    // System.out.println("Encountered ')', resolving operations inside
                    // parentheses");
                    // System.out.println("operations: " + operations + " numbers: " + numbers);
                    while (!operations.isEmpty() && !operations.peek().equals("(")) {
                        System.out.println("numbers: " + numbers);
                        if (numbers.size() < 2) {
                            // System.out.println("Numbers stack (insufficient for operation): " + numbers);
                            throw new IllegalArgumentException("Insufficient numbers for operation");
                        }
                        double b = numbers.pop();
                        double a = numbers.pop();
                        String op = operations.pop();
                        double result = applyOperation(a, b, op);
                        numbers.push(result);
                        // System.out.println("Performed operation " + op + " on " + a + " and " + b +
                        // ", result: " + result);
                        // System.out.println("operations: " + operations + " numbers: " + numbers);
                    }
                    if (!operations.isEmpty()) {
                        operations.pop(); // Remove '('
                        // System.out.println("Removed '(' from operations stack");
                    }
                } else if (isOperator(ch)) {
                    System.out.println("Encountered operator: " + ch);
                    String operator = getCompoundOperator(expression, i);
                    if (operator.length() > 1) {
                        i++; // Пропускаем следующий символ для составных операторов
                    }
                    while (!operations.isEmpty() && precedence(operator) <= precedence(operations.peek())) {
                        if (numbers.size() < 2) {
                            System.out.println("Numbers stack (insufficient for operation): " + numbers);
                            throw new IllegalArgumentException("Insufficient numbers for operation");
                        }
                        double b = numbers.pop();
                        double a = numbers.pop();
                        String op = operations.pop();
                        double result = applyOperation(a, b, op);
                        numbers.push(result);
                        System.out.println(
                                "Performed operation " + op + " on " + a + " and " + b + ", result: " + result);
                    }
                    operations.push(String.valueOf(ch));
                    // System.out.println("Pushed operator to operations stack: " + ch);
                }
            }
        }

        if (currentNumber.length() > 0) {
            numbers.push(Double.parseDouble(currentNumber.toString()));
            // System.out.println("Pushed final number to stack: " + currentNumber);
        }

        System.err.println("operations: " + operations);

        while (!operations.isEmpty()) {
            if (numbers.size() < 2) {
                // System.out.println("Numbers stack (insufficient for operation): " + numbers);
                throw new IllegalArgumentException("Insufficient numbers for operation");
            }
            double b = numbers.pop();
            double a = numbers.pop();
            String op = operations.pop();
            double result = applyOperation(a, b, op);
            numbers.push(result);
            System.out.println("Performed final operations " + op + " on " + a + " and " + b + ", result: " + result);
        }

        double finalResult = numbers.pop();
        System.out.println("Final result: " + finalResult);
        return Double.toString(finalResult); // Ensure stack is not empty before returning
    }

    private double applyOperation(double a, double b, String op) {
        switch (op) {
            case "+":
                return a + b;
            case "-":
                return a - b;
            case "*":
                return a * b;
            case "/":
                if (b == 0)
                    throw new UnsupportedOperationException("Cannot divide by zero");
                return a / b;
            case ">":
                return a > b ? 1.0 : 0.0;
            case "<":
                return a < b ? 1.0 : 0.0;
            case ">=":
                return a >= b ? 1.0 : 0.0;
            case "<=":
                return a <= b ? 1.0 : 0.0;
            case "==":
                return Math.abs(a - b) < 1e-10 ? 1.0 : 0.0;

            case "&":
                return (a != 0 && b != 0) ? 1.0 : 0.0; // логическое И
            case "|":
                return (a != 0 || b != 0) ? 1.0 : 0.0; // логическое ИЛИ
            default:
                throw new IllegalArgumentException("Unsupported operation: " + op);
        }
    }

    private int precedence(String op) {
        switch (op) {
            case "+":
            case "-":
                return 1;
            case "*":
            case "/":
                return 2;
            case ">":
            case "<":
            case ">=":
            case "<=":
            case "==":
                return 0;
            case "&":
            case "|":
                return 0; // логические операторы
            default:
                return -1;
        }
    }

    private boolean isOperator(char ch) {
        return ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '&' || ch == '|' || ch == '>' || ch == '<';
    }

    private String getCompoundOperator(String expression, int currentIndex) {
        if (currentIndex + 1 < expression.length()) {
            char nextChar = expression.charAt(currentIndex + 1);
            char currentChar = expression.charAt(currentIndex);

            if (currentChar == '<' && nextChar == '=')
                return "<=";
            if (currentChar == '>' && nextChar == '=')
                return ">=";
            if (currentChar == '=' && nextChar == '=')
                return "==";
        }
        return String.valueOf(expression.charAt(currentIndex));
    }

    // Define a map to store record structures
    private final Map<String, Map<String, String>> recordTypes = new HashMap<>();

    // Helper to map text types to internal types
    private String mapType(String textType) {
        return textType.equals("int") ? "integer" : textType;
    }

    // exit
    @Override
    public void exitProgram(ImperativeLangParser.ProgramContext ctx) {
        for (String var : declaredVariables.keySet()) {
            if (!usedVariables.contains(var)) {
                // System.out.println(usedVariables);
                System.out.println("Warning: Variable " + var + " declared but never used.");
            }
        }
    }
}
