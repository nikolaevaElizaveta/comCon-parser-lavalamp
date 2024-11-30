import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;
import java.io.*;
import java.util.Locale;

public class Compiler extends ImperativeLangBaseListener {
    private StringBuilder jasminCode = new StringBuilder();
    private int labelCount = 0;
    private Map<String, Integer> variableIndexMap = new HashMap<>();
    private Map<String, String> variableTypeMap = new HashMap<>();
    private int maxStack = 0;
    private int maxLocals = 0;
    private int currentStack = 0;
    private int prevReal = 0;
    private int prevIndex = 0;

    // Use ParseTreeProperty to store labels associated with loops and if statements
    private ParseTreeProperty<Integer> startLabels = new ParseTreeProperty<>();
    private ParseTreeProperty<Integer> endLabels = new ParseTreeProperty<>();
    private Stack<Integer> startLabelsStack = new Stack<>();
    private Stack<Integer> endLabelsStack = new Stack<>();
    private Stack<Integer> ifEndLabelsStack = new Stack<>();

    // Flag to track if we are inside the main method
    private boolean inMainMethod = false;

    // Method to get the generated code
    public String getCode() {
        return jasminCode.toString();
    }

    private int getNextLabel() {
        return labelCount++;
    }

    private void updateStack(int change) {
        currentStack += change;
        if (currentStack > maxStack) {
            maxStack = currentStack;
        }
    }

    private int getVariableIndex(String varName, String varType, int changePrevReal) {
        System.out.println(variableIndexMap + " " + prevIndex + " prevWasReal:" + prevReal + " change:" + changePrevReal);

        if (!variableIndexMap.containsKey(varName)) {
            int baseIndex;
            if (prevReal==1){
                baseIndex = 2;
            } else {
                baseIndex = inMainMethod ? 1 : 0; // Offset for static method
            }
            variableIndexMap.put(varName, prevIndex + baseIndex);
        }
        int index = variableIndexMap.get(varName);
        System.out.println(varName +" index: " + index);
        if (index + 1 > maxLocals) {
            if (prevReal == 1){
                maxLocals = index + 2;
            } else {            
                maxLocals = index + 1;
            }

            // maxLocals = 100;

            System.out.println("maxLocals " + maxLocals);
        }

        System.out.println(varName + ": " + varType);

        if ((varType.equals("real") || varType.equals("D")) && changePrevReal==1){
            System.out.println("IT IS REAL");
            prevReal = 1;
        } else if (changePrevReal==1){
            prevReal = 0;
        }
        if (index > prevIndex){
            prevIndex = index;
        }
        System.out.println(variableIndexMap + " " + prevIndex + " prevWasReal:" + prevReal + " change:" + changePrevReal+ "\n");
        return index;
    }

    @Override
    public void enterProgram(ImperativeLangParser.ProgramContext ctx) {
        jasminCode.append(".class public Main\n");
        jasminCode.append(".super java/lang/Object\n\n");
        jasminCode.append(".method public <init>()V\n");
        jasminCode.append("   aload_0\n");
        jasminCode.append("   invokespecial java/lang/Object/<init>()V\n");
        jasminCode.append("   return\n");
        jasminCode.append(".end method\n\n");
    }

    @Override
    public void exitProgram(ImperativeLangParser.ProgramContext ctx) {
        // The getCode() method will return the generated code
    }

    @Override
    public void enterRoutineDeclaration(ImperativeLangParser.RoutineDeclarationContext ctx) {
        String routineName = ctx.IDENTIFIER().getText();
        if (routineName.equals("Main")) {
            inMainMethod = true; // Set the flag
            variableIndexMap.clear(); // Reset variable indices for the new method
            variableTypeMap.clear(); // Reset variable types
            jasminCode.append(".method public static main([Ljava/lang/String;)V\n");
        }
        // If you have other routines, handle them here
    }

    @Override
    public void exitRoutineDeclaration(ImperativeLangParser.RoutineDeclarationContext ctx) {
        String routineName = ctx.IDENTIFIER().getText();
        if (routineName.equals("Main")) {
            jasminCode.append("   return\n");
            jasminCode.append(".limit stack ").append(maxStack).append("\n");
            jasminCode.append(".limit locals ").append(maxLocals + 1).append("\n");
            jasminCode.append(".end method\n");
            inMainMethod = false; // Reset the flag
            maxStack = 0; // Reset for the next method
            maxLocals = 0;
            currentStack = 0;
            variableIndexMap.clear(); // Clear variables for the next method
            variableTypeMap.clear();
        }
    }

    private void performTypeCast(String fromType, String toType) {
        if (fromType.equals("I")){
            fromType = "integer";
        } else if (fromType.equals("D")){
            fromType = "real";
        } else if (fromType.equals("D")){
            fromType = "boolean";
        }

        if (!fromType.equals(toType)) {
            System.out.println("performing TypeCast: from " + fromType + " to " + toType);
            if (fromType.equals("real") && toType.equals("integer")) {
                System.out.println("from REAL to INTEGER");

                jasminCode.append("   d2i\n");  
                updateStack(-1);  
            } else if (fromType.equals("bool") && toType.equals("integer")) {
                jasminCode.append("   b2i\n"); 
            } else if (fromType.equals("real") && toType.equals("boolean")){
                jasminCode.append("   d2i\n");
            }
        } else {
            System.out.println("no TypeCast ");
        }
    }


    @Override
    public void enterVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx) {
        System.out.println("ctx: " + ctx.getText());
        if (ctx.expression()!=null){
            System.out.println("\nEntering var declaration: " + ctx.getText() + " " + ctx.expression().getText());
        }
        String variableName = ctx.IDENTIFIER().getText();
        String variableType = ctx.type() != null ? ctx.type().getText() : null;
        // String varName = ctx.IDENTIFIER().getText();
        int varIndex = getVariableIndex(variableName, variableType, 1);

        String varType = "I"; // Default type is integer

        if (ctx.type() != null) {
            Object typeInfo = processType(ctx.type());
            System.out.println("typeInfo " + typeInfo);
            if (typeInfo.equals("integer")) {
                varType = "I";
            } else if (typeInfo.equals("real")) {
                // System.out.println("here");
                varType = "D";
            } else if (typeInfo.equals("boolean")) {
                varType = "I";
            } else {
                System.out.println("user type?" + ctx.type().getText());

            }
            variableTypeMap.put(variableName, varType);
        }

        // Если есть выражение
        if (ctx.expression() != null) {
            System.out.println("generating Expression...");
            generateExpression(ctx.expression());

            // Приведение к указанному типу
            if (variableType != null) {
                System.out.println("before analyzing Expression Type...");
                String expressionType = analyzeExpressionType(ctx.expression());
                System.out.println("variableType: " + variableType + ", expressionType: " + expressionType);

                if (!expressionType.equals(variableType)) {
                    // Выполнить тайпкаст
                    System.out.println("TypeCast...");
                    performTypeCast(expressionType, variableType);
                }
            }


            // Генерация кода выражения
            if (ctx.expression() != null) {
                // System.out.println("here");
                
                if (varType.equals("I") || varType.equals("Z")) {
                    jasminCode.append("   istore ").append(varIndex).append("\n");
                    updateStack(-1);
                } else if (varType.equals("D")) {

                    jasminCode.append("   dstore ").append(varIndex).append("\n");
                    updateStack(-2);
                }
            } else {
                // Initialize variable with default value
                if (varType.equals("I") || varType.equals("Z")) {
                    jasminCode.append("   iconst_0\n");
                    updateStack(1);
                    jasminCode.append("   istore ").append(varIndex).append("\n");
                    updateStack(-1);
                } else if (varType.equals("D")) {
                    jasminCode.append("   dconst_0\n");
                    updateStack(2);
                    jasminCode.append("   dstore ").append(varIndex).append("\n");
                    updateStack(-2);
                }
            }

            

            // Сохранение значения в переменную
            // allocateVariable(variableName, variableType);
        } else {
            System.out.println("no expression");

            // Просто выделяем память для переменной
            // allocateVariable(variableName, variableType);
        }
    }

    private String analyzeExpressionType(ImperativeLangParser.ExpressionContext ctx) {
        // Здесь должен быть ваш анализатор типов, который проверяет выражение.
        System.out.println("Expression: " + ctx.getText());
        return getExpressionType(ctx);
        
        // return ctx.getText().matches("\\d+") ? "integer" : "real"; // Упрощённый пример
    }

    @Override
    public void exitVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx) {
        // String varName = ctx.IDENTIFIER().getText();
        // int varIndex = getVariableIndex(varName);

        // String varType = "I"; // Default type is integer

        // if (ctx.type() != null) {
        //     Object typeInfo = processType(ctx.type());
        //     if (typeInfo.equals("integer")) {
        //         varType = "I";
        //     } else if (typeInfo.equals("real")) {
        //         varType = "D";
        //     } else if (typeInfo.equals("boolean")) {
        //         varType = "I";
        //     }
        //     variableTypeMap.put(varName, varType);
        // }

    }

    private Object processType(ImperativeLangParser.TypeContext typeCtx) {
        if (typeCtx.primitiveType() != null) {
            return typeCtx.primitiveType().getText();
        } else if (typeCtx.userType() != null) {
            // Handle user-defined types, arrays, etc.
            // throw new UnsupportedOperationException("User types are not supported yet.");
            ImperativeLangParser.UserTypeContext userTypeCtx = typeCtx.userType();
            if (userTypeCtx.arrayType() != null) {
                // Обрабатываем массив
                return processArrayType(userTypeCtx.arrayType());
            } else if (userTypeCtx.recordType() != null) {
                // Обрабатываем запись
                return processRecordType(userTypeCtx.recordType());
            }
        } else if (typeCtx.IDENTIFIER() != null) {
            // Handle type aliases if necessary
            throw new UnsupportedOperationException("Type aliases are not supported yet.");
        }
        throw new RuntimeException("Unsupported type");
    }

    // Обработка массива
    private Object processArrayType(ImperativeLangParser.ArrayTypeContext arrayTypeCtx) {
        String arrayElementType = arrayTypeCtx.type().getText();
        String dimensions = arrayTypeCtx.expression() != null ? arrayTypeCtx.expression().getText() : "[]";
        
        System.out.println("Обнаружен массив с элементами типа: " + arrayElementType + " и размерностью: " + dimensions);
        
        // Добавление информации о массиве в jasminCode
        jasminCode.append("   ; Объявление массива типа ").append(arrayElementType).append("\n");

        // Генерация кода для определения размера массива
        if (arrayTypeCtx.expression() != null) {
            generateExpression(arrayTypeCtx.expression());
        } else {
            jasminCode.append("   ldc 0 ; Размер массива по умолчанию\n");
            updateStack(1);
        }

        // Создание массива в зависимости от типа элементов
        if (arrayElementType.equals("integer") || arrayElementType.equals("I")) {
            jasminCode.append("   newarray int\n");
        } else if (arrayElementType.equals("real") || arrayElementType.equals("D")) {
            jasminCode.append("   newarray double\n");
        } else if (arrayElementType.equals("boolean") || arrayElementType.equals("Z")) {
            jasminCode.append("   newarray boolean\n");
        } else {
            jasminCode.append("   anewarray ").append(arrayElementType).append("\n");
        }
        
        updateStack(0);  // Создание массива не изменяет стек.

        return "Array<" + arrayElementType + ">";
    }

    // Обработка записи
    private Object processRecordType(ImperativeLangParser.RecordTypeContext recordTypeCtx) {
        StringBuilder recordDefinition = new StringBuilder("Record { ");
        for (ImperativeLangParser.VariableDeclarationContext varDecl : recordTypeCtx.variableDeclaration()) {
            String varName = varDecl.IDENTIFIER().getText();
            String varType = processType(varDecl.type()).toString();
            recordDefinition.append(varName).append(": ").append(varType).append("; ");
        }
        recordDefinition.append("}");
        System.out.println("Обнаружена запись: " + recordDefinition);
        return recordDefinition.toString();
    }

    @Override
    public void enterAssignment(ImperativeLangParser.AssignmentContext ctx) {
        System.out.println("Entering assignment: " + ctx.getText());
    }

    @Override
    public void exitAssignment(ImperativeLangParser.AssignmentContext ctx) {
        ImperativeLangParser.ModifiablePrimaryContext modCtx = ctx.modifiablePrimary();
        String varName = modCtx.IDENTIFIER(0).getText();
        String varType = variableTypeMap.get(varName);
        System.out.println("in exit assignment");
        int varIndex = getVariableIndex(varName, varType, 1);
        // System.out.println("ctx: " + ctx.getText());
        // if (ctx.getText().contains(":=")) {
        //     System.out.println("it is an assignment");
        //     generateExpression(ctx.assignment().expression());
        // }
        generateExpression(ctx.expression());

        if (varType.equals("I") || varType.equals("Z")) {
            jasminCode.append("   istore ").append(varIndex).append("\n");
            updateStack(-1);
        } else if (varType.equals("D")) {
            System.out.println("dstore " + varIndex + " " + varType + "varName" +
                    varName);
            jasminCode.append("   dstore ").append(varIndex).append("\n");
            updateStack(-2);
        } else {
            // Handle other types if needed
        }
    }

    @Override
    public void enterWhileLoop(ImperativeLangParser.WhileLoopContext ctx) {
        int startLabel = getNextLabel();
        int endLabel = getNextLabel();

        startLabels.put(ctx, startLabel);
        startLabelsStack.push(startLabel);
        endLabels.put(ctx, endLabel);
        endLabelsStack.push(endLabel);

        jasminCode.append("Label").append(startLabel).append(":\n");

        generateExpression(ctx.expression());

        // jasminCode.append("   ifeq Label").append(endLabel).append("\n");
        // updateStack(-1);
    }

    @Override
    public void exitWhileLoop(ImperativeLangParser.WhileLoopContext ctx) {
        int startLabel = startLabels.get(ctx);
        int endLabel = endLabels.get(ctx);
        // System.out.println("startLabels " + startLabels + "\nendLabels " + endLabels);
        // Напечатаем содержимое startLabels
        // System.out.println("Start Labels:");
        // for (ParseTree node : startLabels.keySet()) {
        //     int label = startLabels.get(node);
        //     System.out.println("Node: " + node.getText() + " -> Label" + label);
        // }

        // // Напечатаем содержимое endLabels
        // System.out.println("End Labels:");
        // for (ParseTree node : endLabels.keySet()) {
        //     int label = endLabels.get(node);
        //     System.out.println("Node: " + node.getText() + " -> Label" + label);
        // }

        jasminCode.append("   goto Label").append(startLabel).append("\n");
        jasminCode.append("Label").append(endLabel).append(":\n");
    }

    @Override
    public void enterBody(ImperativeLangParser.BodyContext ctx) {
        // System.out.println("Входим в метод enterBody..." + ctx.getText());


        // Проверяем, является ли данный body частью if-then
        ParseTree parent = ctx.getParent();
        if (parent instanceof ImperativeLangParser.IfStatementContext) {
            System.out.println("Этот body является частью if-then...");

            ImperativeLangParser.IfStatementContext ifCtx = (ImperativeLangParser.IfStatementContext) parent;
            // Пропускаем первое body (then)
            if (ifCtx.body(0) == ctx) {
                System.out.println("Пропускаем обработку первого body (then)... " + ifCtx.body(0).getText());
                updateStack(-2);
                return; // Прекращаем обработку данного body
            }
        }
        // System.out.println("Обрабатываем body...");
        // Обычная обработка body
        super.enterBody(ctx);
        // processBody(ctx);
        // System.out.println("Выход из метода enterBody...");
    }

    @Override
    public void enterIfStatement(ImperativeLangParser.IfStatementContext ctx) {
        System.out.println("\nentering IfStatement...");
        int elseLabel = getNextLabel();
        int endLabel = getNextLabel();
        int ifendlabel = getNextLabel();
        ifEndLabelsStack.push(ifendlabel); // реальный конец иф

        startLabels.put(ctx, elseLabel);
        startLabelsStack.push(elseLabel); // позер

        endLabels.put(ctx, elseLabel);
        endLabelsStack.push(elseLabel);

        System.out.println("ctx.getText(): " + ctx.getText());
        System.out.println("ctx.expression().getText() " + ctx.expression().getText());
        generateExpression(ctx.expression());

        if ( ctx.getText().contains("else")){

            System.out.println("trying... " + ctx.body(0).getText());
            if (ctx.body() != null && ctx.body().size() > 0) {
                for (ImperativeLangParser.StatementContext statementCtx : ctx.body(0).statement()) {
                    System.out.println("routine call------------------------------- " + statementCtx.getText() + ": "+statementCtx.routineCall().getText());
                    if (statementCtx.routineCall() != null) {
                        generateRoutineCall(statementCtx.routineCall());
                        System.out.println("finished routine call----------------------- " + statementCtx.routineCall().getText());

                    }

                    // if (statementCtx instanceof ImperativeLangParser.RoutineCallContext) {
                    //     // Обработка вызова процедуры
                    //     ImperativeLangParser.RoutineCallContext routineCallCtx = (ImperativeLangParser.RoutineCallContext) statementCtx;
                    //     generateRoutineCall(routineCallCtx);
                    // }
                }
                jasminCode.append("   goto Label").append(ifEndLabelsStack.peek()).append("\n");
                jasminCode.append("Label").append(elseLabel).append(":\n");

            }
            System.out.println("trying...x2 " + ctx.body(1).getText());
            // generateExpression(ctx.body(1));
            System.out.println("haha?");

        }


        // jasminCode.append("   entering IfStatement...").append("\n");
        // jasminCode.append("   ifeq Label").append(endLabel).append("\n");
        // updateStack(-1);
        // jasminCode.append("   exitting IfStatement...").append("\n");
    }

    // @Override
    // public void enterElseStatement(ImperativeLangParser.ElseStatementContext ctx) {
    //     System.out.println("\nEntering ElseStatement...");
    //     int elseLabel = getNextLabel();
    //     int endLabel = endLabelsStack.peek(); // Берем метку конца if-else из стека

    //     // Записываем метку для блока else
    //     jasminCode.append("Label").append(elseLabel).append(":\n");

    //     // Генерация кода для блока else
    //     // Пример: код для печати переменной b
    //     jasminCode.append("   getstatic java/lang/System/out Ljava/io/PrintStream;\n");
    //     jasminCode.append("   iload 2\n"); // Переменная b
    //     jasminCode.append("   invokevirtual java/io/PrintStream/println(I)V\n");

    //     // Переход к метке конца if-else
    //     jasminCode.append("   goto Label").append(endLabel).append("\n");
    // }

    @Override
    public void exitIfStatement(ImperativeLangParser.IfStatementContext ctx) {
        int elseLabel = startLabels.get(ctx);
        System.out.println("elseLabel " + elseLabel);
        int ifendLabel = ifEndLabelsStack.peek();

        // jasminCode.append("   exitIfStatement...").append("\n");
        if (ctx.body().size() > 1) {
            jasminCode.append("   goto Label").append(ifendLabel).append("\n");
            // jasminCode.append("2Label").append(elseLabel).append(":\n"); // ??
            // The else body has been generated during the traversal
            jasminCode.append("Label").append(ifendLabel).append(":\n");
        } else {
            jasminCode.append("Label").append(ifendLabel).append(":\n");
        }
    }

    @Override
    public void exitRoutineCall(ImperativeLangParser.RoutineCallContext ctx) {
        String funcName = ctx.IDENTIFIER().getText();
        System.out.println("funcName " + funcName);
        ParseTree parent = ctx.getParent();
        if (parent instanceof ImperativeLangParser.StatementContext) {
            System.out.println("Этот routinecall является частью statement...");
            ImperativeLangParser.StatementContext stCtx = (ImperativeLangParser.StatementContext) parent;
            parent = stCtx.getParent();
            if (parent instanceof ImperativeLangParser.BodyContext) {
                System.out.println("Этот statement является частью body...");
                ImperativeLangParser.BodyContext bdCtx = (ImperativeLangParser.BodyContext) parent;
                parent = bdCtx.getParent();
                if (parent instanceof ImperativeLangParser.IfStatementContext) {
                    System.out.println("Этот body является частью ifStatement...");
                    ImperativeLangParser.IfStatementContext ifCtx = (ImperativeLangParser.IfStatementContext) parent;
                    if (ifCtx.body(0).getText().equals(ctx.getText())){
                        System.out.println("скип");

                        return; 
                    }
                }
                
            }

        }

        if (funcName.equals("print")) {
            ImperativeLangParser.ExpressionListContext exprList = ctx.expressionList();
            if (exprList != null && exprList.expression().size() == 1) {
                // jasminCode.append("   exitRoutineCall;\n");
                jasminCode.append("   getstatic java/lang/System/out Ljava/io/PrintStream;\n");
                updateStack(1);

                generateExpression(exprList.expression(0));
                String exprType = getExpressionType(exprList.expression(0));

                if (exprType.equals("I") || exprType.equals("Z")) {
                    jasminCode.append("   invokevirtual java/io/PrintStream/println(I)V\n");
                    updateStack(-2);
                } else if (exprType.equals("D")) {
                    jasminCode.append("   invokevirtual java/io/PrintStream/println(D)V\n");
                    updateStack(-3);
                } else {
                    throw new UnsupportedOperationException("Unsupported type for print: " + exprType);
                }
            } else {
                throw new UnsupportedOperationException("print() with multiple arguments is not supported.");
            }
        } else {
            throw new UnsupportedOperationException("Routine call to " + funcName + " is not supported.");
        }
    }

    private void generateExpression(ImperativeLangParser.ExpressionContext ctx) {
        System.out.println("Compiling an expression... " + ctx.getText());
        System.out.println(currentStack);
        if (ctx.relation().size() == 1) {
            // System.out.println("generateExpression relation " + ctx.relation(0).getText());
            generateRelation(ctx.relation(0));
        } else {
            // Logical operations (and, or, xor)
            System.out.println("generateExpression several relations");
            generateRelation(ctx.relation(0));
            for (int i = 1; i < ctx.relation().size(); i++) {
                generateRelation(ctx.relation(i));
                String op = ctx.getChild(2 * i - 1).getText();
                switch (op) {
                    case "and":
                        jasminCode.append("   iand\n");
                        updateStack(-1);
                        break;
                    case "or":
                        jasminCode.append("   ior\n");
                        updateStack(-1);
                        break;
                    case "xor":
                        jasminCode.append("   ixor\n");
                        updateStack(-1);
                        break;
                }
            }
        }
        // jasminCode.append(" ").append(ctx.getText()).append("\n");
    }

    private void generateRelation(ImperativeLangParser.RelationContext ctx) {
        // System.out.println("generateRelation " + ctx.getText() + " " + ctx.simple().size());
        if (ctx.simple().size() == 1) {
            generateSimple(ctx.simple(0));
        } else {
            generateSimple(ctx.simple(0));
            generateSimple(ctx.simple(1));
            String op = ctx.getChild(1).getText();
            int labelTrue = getNextLabel();
            int labelEnd = getNextLabel();

            String exprType = getExpressionType(ctx.simple(0));
            System.out.println("exprType is " + exprType);

            // jasminCode.append("   generateRelation beginning").append("\n");

            if (exprType.equals("I") || exprType.equals("Z")) {
                switch (op) {
                    case "<":
                        jasminCode.append("   if_icmplt Label").append(labelTrue).append("\n");
                        break;
                    case "<=":
                        jasminCode.append("   if_icmple Label").append(labelTrue).append("\n");
                        break;
                    case ">":
                        jasminCode.append("   if_icmpgt Label").append(labelTrue).append("\n");
                        break;
                    case ">=":
                        jasminCode.append("   if_icmpge Label").append(labelTrue).append("\n");
                        break;
                    case "=":
                        jasminCode.append("   if_icmpeq Label").append(labelTrue).append("\n");
                        break;
                    case "/=":
                        jasminCode.append("   if_icmpne Label").append(labelTrue).append("\n");
                        break;
                    default:
                        throw new UnsupportedOperationException("Comparison operator '" + op + "' is not supported.");
                }
                updateStack(-2);
            } else if (exprType.equals("D")) {
                // print current stack
                System.out.println("1 current stack is " + currentStack);
                jasminCode.append("   dcmpl\n");
                // print current stack
                System.out.println("2 current stack is " + currentStack);
                updateStack(-3);
                // print current stack
                System.out.println("current stack is " + currentStack);
                switch (op) {
                    case "<":
                        jasminCode.append("   iflt Label").append(labelTrue).append("\n");
                        break;
                    case "<=":
                        jasminCode.append("   ifle Label").append(labelTrue).append("\n");
                        break;
                    case ">":
                        jasminCode.append("   ifgt Label").append(labelTrue).append("\n");
                        break;
                    case ">=":
                        jasminCode.append("   ifge Label").append(labelTrue).append("\n");
                        break;
                    case "=":
                        jasminCode.append("   ifeq Label").append(labelTrue).append("\n");
                        break;
                    case "/=":
                        jasminCode.append("   ifne Label").append(labelTrue).append("\n");
                        break;
                    default:
                        throw new UnsupportedOperationException("Comparison operator '" + op + "' is not supported.");
                }
            } else {
                System.out.println("error");
                throw new UnsupportedOperationException("Unsupported type for comparison.");
            }

            // jasminCode.append("   iconst_0\n");
            updateStack(1);

            jasminCode.append("   goto Label").append(endLabelsStack.peek()).append("\n");

            jasminCode.append("Label").append(labelTrue).append(":\n"); // 5
            // jasminCode.append("   iconst_1\n");
            // updateStack(1); // Already accounted for
            // jasminCode.append("6Label").append(labelEnd).append(":\n");

            // jasminCode.append("   generateRelation end").append("\n");
        }
    }

    private void generateSimple(ImperativeLangParser.SimpleContext ctx) {
        // System.out.println("generateSimple " + ctx.getText() + " " + ctx.factor().size());
        if (ctx.factor().size() == 1) {
            generateFactor(ctx.factor(0));
        } else {
            String exprType = getExpressionType(ctx.factor(0));
            generateFactor(ctx.factor(0));

            for (int i = 1; i < ctx.factor().size(); i++) {
                generateFactor(ctx.factor(i));
                String op = ctx.getChild(2 * i - 1).getText();

                if (exprType.equals("I")) {
                    switch (op) {
                        case "*":
                            jasminCode.append("   imul\n");
                            updateStack(-1);
                            break;
                        case "/":
                            jasminCode.append("   idiv\n");
                            updateStack(-1);
                            break;
                        case "%":
                            jasminCode.append("   irem\n");
                            updateStack(-1);
                            break;
                    }
                } else if (exprType.equals("D")) {
                    switch (op) {
                        case "*":
                            jasminCode.append("   dmul\n");
                            updateStack(-2);
                            break;
                        case "/":
                            jasminCode.append("   ddiv\n");
                            updateStack(-2);
                            break;
                        case "%":
                            // Convert to integers for modulo operation
                            jasminCode.append("   d2i\n");
                            updateStack(-1);
                            jasminCode.append("   swap\n");
                            jasminCode.append("   d2i\n");
                            updateStack(-1);
                            jasminCode.append("   irem\n");
                            updateStack(-1);
                            jasminCode.append("   i2d\n");
                            updateStack(1);
                            break;
                    }
                }
            }
        }
    }

    private void generateFactor(ImperativeLangParser.FactorContext ctx) {
        // System.out.println("generateFactor " + ctx.getText() + " " + ctx.summand().size());
        if (ctx.summand().size() == 1) {
            generateSummand(ctx.summand(0));
        } else {
            String exprType = getExpressionType(ctx.summand(0));
            generateSummand(ctx.summand(0));

            for (int i = 1; i < ctx.summand().size(); i++) {
                generateSummand(ctx.summand(i));
                String op = ctx.getChild(2 * i - 1).getText();

                if (exprType.equals("I")) {
                    switch (op) {
                        case "+":
                            jasminCode.append("   iadd\n");
                            updateStack(-1);
                            break;
                        case "-":
                            jasminCode.append("   isub\n");
                            updateStack(-1);
                            break;
                    }
                } else if (exprType.equals("D")) {
                    switch (op) {
                        case "+":
                            jasminCode.append("   dadd\n");
                            updateStack(-2);
                            break;
                        case "-":
                            jasminCode.append("   dsub\n");
                            updateStack(-2);
                            break;
                    }
                }
            }
        }
    }

    private void generateSummand(ImperativeLangParser.SummandContext ctx) {
        // System.out.println("generateSummand " + ctx.getText() +
                // " type: " + getExpressionType(ctx) +
                // " context: " + ctx.primary());
        if (ctx.primary() != null) {
            generatePrimary(ctx.primary());
        } else if (ctx.expression() != null) {
            generateExpression(ctx.expression());
        }
    }

    private void generatePrimary(ImperativeLangParser.PrimaryContext ctx) {
        // System.out.println("generatePrimary " + ctx.getText());
        if (ctx.INTEGER_LITERAL() != null) {
            System.out.println("generatePrimary INTEGER_LITERAL " + ctx.INTEGER_LITERAL().getText());
            int value = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
            if (value >= -1 && value <= 5) {
                // System.out.println("HERE");
                jasminCode.append("   iconst_").append(value).append("\n");
            } else if (value >= -128 && value <= 127) {
                jasminCode.append("   bipush ").append(value).append("\n");
            } else if (value >= -32768 && value <= 32767) {
                jasminCode.append("   sipush ").append(value).append("\n");
            } else {
                jasminCode.append("   ldc ").append(value).append("\n");
            }
            updateStack(1);
        } else if (ctx.REAL_LITERAL() != null) {
            System.out.println("generatePrimary REAL_LITERAL " + ctx.REAL_LITERAL().getText());
            double value = Double.parseDouble(ctx.REAL_LITERAL().getText());
            if (value == 0.0) {
                jasminCode.append("   dconst_0\n");
            } else if (value == 1.0) {
                jasminCode.append("   dconst_1\n");
            } else {
                System.out.println("generatePrimary REAL_LITERAL ldc2_w " + String.format(Locale.US, "%.15f", value));
                jasminCode.append("   ldc2_w ").append(String.format(Locale.US, "%.15f", value)).append("\n");
            }
            // print current stack
            // System.out.println("4 current stack is " + currentStack);
            updateStack(2);
            // print current stack
            // System.out.println("5 current stack is " + currentStack);
        } else if (ctx.getText().equals("true")) {
            System.out.println("generatePrimary true");
            jasminCode.append("   iconst_1\n");
            updateStack(1);
        } else if (ctx.getText().equals("false")) {
            System.out.println("generatePrimary false");
            jasminCode.append("   iconst_0\n");
            updateStack(1);
        } else if (ctx.modifiablePrimary() != null) {
            generateModifiablePrimary(ctx.modifiablePrimary());
        } else if (ctx.routineCall() != null) {
            // Handle routine calls
            generateRoutineCall(ctx.routineCall());
        }
    }

    private void generateModifiablePrimary(ImperativeLangParser.ModifiablePrimaryContext ctx) {
        String varName = ctx.IDENTIFIER(0).getText();
        String varType = variableTypeMap.get(varName);
        System.out.println("in generate modifiable " + ctx.getText());
        System.out.println("variableTypeMap " + variableTypeMap);

        int varIndex = getVariableIndex(varName, varType, 0);

        if (ctx.getChildCount() == 1) {
            // Simple variable
            if (varType.equals("I") || varType.equals("Z") || varType.equals("integer") || varType.equals("boolean") || varType.equals("bool")) {
                jasminCode.append("   iload ").append(varIndex).append("\n");
                updateStack(1);
            } else if (varType.equals("D")) {
                jasminCode.append("   dload ").append(varIndex).append("\n");
                updateStack(2);
            }
        } else {
            // Access array element (not implemented)
            // throw new UnsupportedOperationException("Array access is not supported yet.");
            // Доступ к элементу массива
            if (varType.startsWith("Array<")) {
                String elementType = varType.substring(6, varType.length() - 1); // Получаем тип элементов массива

                // Загрузка массива в стек
                jasminCode.append("   aload ").append(varIndex).append("\n");
                updateStack(1);

                // Обработка индекса массива
                ImperativeLangParser.ExpressionContext indexCtx = ctx.expression(0);
                generateExpression(indexCtx); // Генерация кода для вычисления индекса

                // Загрузка элемента массива в зависимости от типа
                if (elementType.equals("integer") || elementType.equals("I")) {
                    jasminCode.append("   iaload\n");
                    updateStack(-1);
                } else if (elementType.equals("real") || elementType.equals("D")) {
                    jasminCode.append("   daload\n");
                    updateStack(-1);
                } else if (elementType.equals("boolean") || elementType.equals("Z")) {
                    jasminCode.append("   iaload\n"); // Для boolean используем iaload
                    updateStack(-1);
                } else {
                    throw new UnsupportedOperationException("Array type " + elementType + " not supported yet.");
                }
            } else {
                throw new UnsupportedOperationException("Accessing non-array type as array: " + varType);
            }
        }
    }

    private void generateRoutineCall(ImperativeLangParser.RoutineCallContext ctx) {
        String funcName = ctx.IDENTIFIER().getText();
        if (funcName.equals("print")) {
            ImperativeLangParser.ExpressionListContext exprList = ctx.expressionList();
            if (exprList != null && exprList.expression().size() == 1) {
                jasminCode.append("   getstatic java/lang/System/out Ljava/io/PrintStream;\n");
                updateStack(1);
                generateExpression(exprList.expression(0));
                String exprType = getExpressionType(exprList.expression(0));

                if (exprType.equals("I") || exprType.equals("Z")) {
                    jasminCode.append("   invokevirtual java/io/PrintStream/println(I)V\n");
                    updateStack(-2);
                } else if (exprType.equals("D")) {
                    jasminCode.append("   invokevirtual java/io/PrintStream/println(D)V\n");
                    updateStack(-3);
                }
            }
        }
    }

    private String getExpressionType(ParserRuleContext ctx) {
        // print whether ctx is expression or relation or simple or factor or summand or
        // primary or modifiablePrimary
        // System.out.println("ctx is " + ctx.getClass().getSimpleName() + " " +
        // ctx.getText());
        // Determine the type of the expression
        if (ctx instanceof ImperativeLangParser.PrimaryContext primaryCtx) {
            // System.out.println("primaryCtx is " + primaryCtx.getClass().getSimpleName() + " " + primaryCtx.getText());
            if (primaryCtx.INTEGER_LITERAL() != null) {
                // System.out.println("it is int");
                return "I";
            } else if (primaryCtx.REAL_LITERAL() != null) {
                // System.out.println("it is real");
                return "D";
            } else if (primaryCtx.getText().equals("true") || primaryCtx.getText().equals("false")) {
                // System.out.println("it is bool");
                return "Z";
            } else if (primaryCtx.modifiablePrimary() != null) {
                // System.out.println("it is modifiable");
                String varName = primaryCtx.modifiablePrimary().IDENTIFIER(0).getText();
                return variableTypeMap.get(varName);
            }
        } else if (ctx instanceof ImperativeLangParser.ModifiablePrimaryContext modCtx) {
            String varName = modCtx.IDENTIFIER(0).getText();
            return variableTypeMap.get(varName);
        } else if (ctx instanceof ImperativeLangParser.ExpressionContext exprCtx) {
            if (exprCtx.relation().size() == 1) {
                return getExpressionType(exprCtx.relation(0));
            } else {
                return "Z"; // Logical operations result in boolean
            }
        } else if (ctx instanceof ImperativeLangParser.RelationContext relCtx) {
            if (relCtx.simple().size() == 1) {
                return getExpressionType(relCtx.simple(0));
            } else {
                return "Z"; // Comparison operations result in boolean
            }
        } else if (ctx instanceof ImperativeLangParser.SimpleContext simpleCtx) {
            return getExpressionType(simpleCtx.factor(0));
        } else if (ctx instanceof ImperativeLangParser.FactorContext factorCtx) {
            return getExpressionType(factorCtx.summand(0));
        } else if (ctx instanceof ImperativeLangParser.SummandContext summandCtx) {
            if (summandCtx.primary() != null) {
                return getExpressionType(summandCtx.primary());
            } else if (summandCtx.expression() != null) {
                return getExpressionType(summandCtx.expression());
            }
        }

        throw new UnsupportedOperationException("Unable to determine expression type.");
    }

    @Override
    public void enterForLoop(ImperativeLangParser.ForLoopContext ctx) {
        System.out.println("\nEntering for loop...");

        int startLabel = getNextLabel(); // Метка начала цикла
        int endLabel = getNextLabel();   // Метка конца цикла

        String varName = ctx.IDENTIFIER().getText();  // Получаем имя переменной
        variableTypeMap.put(varName, "integer");  // Устанавливаем тип переменной как "I" (целое число)
    
        System.out.println("------------------------------------"+ varName);
        int varIndex = getVariableIndex(varName, "integer", 1);

        // getVariableIndex(variableName, variableType, 1);

        startLabels.put(ctx, startLabel);
        endLabels.put(ctx, endLabel);

        // Проверка наличия ключевого слова 'reverse'
        boolean isReverse = ctx.getText().contains("reverse");

        // Генерация кода для начального значения диапазона
        generateExpression(ctx.range().expression(0));  // Первое выражение в range
        // jasminCode.append("   istore");             // Сохраняем в локальную переменную (цикл переменная)
        performTypeCast(analyzeExpressionType(ctx.range().expression(0)), "integer");
        varIndex = getVariableIndex(varName, "integer", 1);
        jasminCode.append("   istore ").append(varIndex).append("\n");
        updateStack(1);

        // Генерация кода для конечного значения диапазона
        generateExpression(ctx.range().expression(1));  // Второе выражение в range
        
        // jasminCode.append("   istore");             // Сохраняем в локальную переменную (конец диапазона)
        System.out.println("ctx.range() " + ctx.range().expression(1).getText());
        performTypeCast(analyzeExpressionType(ctx.range().expression(1)), "integer");
        varIndex = getVariableIndex(ctx.range().expression(1).getText(), "integer", 1);
        jasminCode.append("   istore ").append(varIndex).append("\n");
        updateStack(1);


        // Метка начала цикла
        jasminCode.append("Label").append(startLabel).append(":\n");

        // Загружаем значения переменной и конца диапазона
        jasminCode.append("   iload ").append(variableIndexMap.get(varName)).append("\n");  // Текущее значение переменной цикла ??
        updateStack(1);
        jasminCode.append("   iload ").append(variableIndexMap.get(ctx.range().expression(1).getText())).append("\n");  // Конечное значение диапазона ??
        updateStack(1);

        // Условие выхода из цикла
        if (isReverse) {
            jasminCode.append("   if_icmplt Label").append(endLabel).append("\n");  // Для обратного порядка
        } else {
            jasminCode.append("   if_icmpgt Label").append(endLabel).append("\n");  // Для прямого порядка
        }
    }


    @Override
    public void exitForLoop(ImperativeLangParser.ForLoopContext ctx) {
        int startLabel = startLabels.get(ctx);
        int endLabel = endLabels.get(ctx);

        // int varIndex = getVariableIndex(varName, "integer", 1);

        // Проверка наличия ключевого слова 'reverse'
        boolean isReverse = ctx.getText().contains("reverse");

        // Обновление переменной цикла: прибавление или вычитание
        jasminCode.append("   iload ").append(variableIndexMap.get(ctx.IDENTIFIER().getText())).append("\n"); 
        updateStack(1);

        if (isReverse) {
            jasminCode.append("   iconst_1\n");
            updateStack(1);

            jasminCode.append("   isub\n");  // Вычитаем 1, если 'reverse'
        } else {
            jasminCode.append("   iconst_1\n");
            updateStack(1);

            jasminCode.append("   iadd\n");  // Прибавляем 1, если нет 'reverse'
        }
        // jasminCode.append("   istore\n");
        jasminCode.append("   istore ").append(variableIndexMap.get(ctx.IDENTIFIER().getText())).append("\n");
        

        // Переход к началу цикла
        jasminCode.append("   goto Label").append(startLabel).append("\n");

        // Метка конца цикла
        jasminCode.append("Label").append(endLabel).append(":\n");
    }


}
