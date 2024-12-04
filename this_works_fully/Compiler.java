import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;
import java.io.*;

public class Compiler extends ImperativeLangBaseListener {
    private StringBuilder jasminCode = new StringBuilder();

    private int labelCount = 0;
    private Map<String, Integer> variableIndexMap = new HashMap<>();
    private Map<String, String> variableTypeMap = new HashMap<>();
    private Map<String, String> routineTypeMap = new HashMap<>(); // return
    private Map<String, String> typeAliases = new HashMap<>(); // Добавляем структуру для хранения type aliases
    private Map<String, Map<String, String>> recordTypes = new HashMap<>(); // Структура для хранения типов записей
    private Map<String, StringBuilder> recordJasminCode = new HashMap<>(); // Хранение Jasmin кода для каждой записи
    private String currentRecord = null; // Текущая обрабатываемая запись
    private StringBuilder currentRecordCode = new StringBuilder(); // Код для текущей записи
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
    private boolean inRoutineCall = false;
    private String currentRoutineCall = "";
    private int currentParam = 0;
    private Map<String, ArrayList<String>> routineParamTypes = new HashMap<>();

    private StringBuilder currentPath = new StringBuilder();

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
        System.out
                .println(variableIndexMap + " " + prevIndex + " prevWasReal:" + prevReal + " change:" + changePrevReal);

        if (!variableIndexMap.containsKey(varName)) {
            System.out.println("is not in the map");
            int baseIndex;
            if (prevReal == 1) {
                baseIndex = 2;
            } else {
                // baseIndex = inMainMethod ? 1 : 0; // Offset for static method
                baseIndex = 1;
            }
            variableIndexMap.put(varName, prevIndex + baseIndex);
        }
        int index = variableIndexMap.get(varName);
        System.out.println(varName + " index: " + index);
        if (index + 1 > maxLocals) {
            if (prevReal == 1) {
                maxLocals = index + 2;
            } else {
                maxLocals = index + 1;
            }

            // maxLocals = 100;

            System.out.println("maxLocals " + maxLocals);
        }

        System.out.println(varName + ": " + varType);

        if ((varType.equals("real") || varType.equals("D")) && changePrevReal == 1) {
            System.out.println("IT IS REAL");
            prevReal = 1;
        } else if (changePrevReal == 1) {
            prevReal = 0;
        }
        if (index > prevIndex) {
            prevIndex = index;
        }
        System.out.println(
                variableIndexMap + " " + prevIndex + " prevWasReal:" + prevReal + " change:" + changePrevReal + "\n");
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
    public void exitReturnStatement(ImperativeLangParser.ReturnStatementContext ctx) {
        System.out.println("Handling return statement...");

        if (ctx.expression() != null) {
            String returnType = getExpressionType(ctx.expression());
            generateExpression(ctx.expression()); // Генерация кода для выражения

            // Обработка возвращаемого значения в зависимости от его типа
            switch (returnType) {
                case "I": // Целое число
                    jasminCode.append("   ireturn\n");
                    updateStack(-1); // Уменьшаем стек после возврата
                    break;
                case "D": // Вещественное число
                    jasminCode.append("   dreturn\n");
                    updateStack(-2); // Уменьшаем стек для double
                    break;
                case "Z": // Логическое значение
                    jasminCode.append("   ireturn\n"); // boolean возвращается как int
                    updateStack(-1);
                    break;
                default:
                    if (recordTypes.containsKey(returnType)) {
                        jasminCode.append("   areturn\n");
                    } else {
                        throw new UnsupportedOperationException("Unsupported return type: " + returnType);
                    }
            }
        } else {
            // Если return без значения (например, void)
            jasminCode.append("   return\n");
        }
    }

    @Override
    public void enterRoutineDeclaration(ImperativeLangParser.RoutineDeclarationContext ctx) {
        System.out.println("\nentering RoutineDeclaration..." + ctx.getText());
        String routineDeclarationJasminCode = "";
        String routineName = ctx.IDENTIFIER().getText();
        if (ctx.type() != null) {
            String routineReturnType = ctx.type().getText();
            routineTypeMap.put(routineName, routineReturnType);
        }

        routineParamTypes.putIfAbsent(routineName, new ArrayList<>());

        if (ctx.parameters() != null) {
            for (ImperativeLangParser.ParameterDeclarationContext paramCtx : ctx.parameters().parameterDeclaration()) {
                String paramType = paramCtx.type().getText();
                routineParamTypes.get(routineName).add(paramType);
            }
            System.out.println("Параметры рутины " + routineName + ": " + routineParamTypes.get(routineName));
        }

        if (routineName.equals("Main")) {
            inMainMethod = true; // Set the flag
            currentPath = new StringBuilder();
            variableIndexMap.clear(); // Reset variable indices for the new method
            variableTypeMap.clear(); // Reset variable types
            jasminCode.append(".method public static main([Ljava/lang/String;)V\n");
        } else {
            inMainMethod = false;
            // currentPath = currentPath.append(routineName).append("/");

            // Начинаем декларацию метода
            jasminCode.append(".method public static ")
                    .append(routineName)
                    .append("(");

            // Обработка параметров
            if (ctx.parameters() != null) {
                for (ImperativeLangParser.ParameterDeclarationContext param : ctx.parameters().parameterDeclaration()) {
                    String paramType = getTypeDescriptor(param.type().getText());
                    // jasminCode.append(paramType.equals("I"));
                    if (paramType.equals("integer") || paramType.equals("I")) {
                        // jasminCode.append("!");
                        paramType = "I";
                        // if (paramType.equals("I")) {

                        jasminCode.append(paramType);
                        // } else {
                        // jasminCode.append("I");
                        // }
                    } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]I") ||
                            paramType.equals("Array<I>") ||
                            paramType.matches("array\\[\\d+(\\.\\d+)?\\]integer") ||
                            paramType.equals("Array<integer>")) {
                        jasminCode.append("[I");
                    } else if (paramType.equals("real") || paramType.equals("D")) {
                        jasminCode.append("D");
                    } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]D") ||
                            paramType.equals("Array<D>") ||
                            paramType.matches("array\\[\\d+(\\.\\d+)?\\]real") ||
                            paramType.equals("Array<real>")) {
                        jasminCode.append("[D");
                    } else if (paramType.equals("bool") || paramType.contains("boolean") || paramType.equals("Z")) {
                        jasminCode.append("Z");
                    } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]Z") ||
                            paramType.equals("Array<Z>") ||
                            paramType.matches("array\\[\\d+(\\.\\d+)?\\]bool") ||
                            paramType.equals("Array<bool>") ||
                            paramType.matches("array\\[\\d+(\\.\\d+)?\\]boolean") ||
                            paramType.equals("Array<boolean>")) {
                        jasminCode.append("[Z");
                    } else {
                        jasminCode.append("L").append(paramType).append(";");
                    }
                    // jasminCode.append(paramType);

                    // Сохранение типа параметра в карте переменных
                    String paramName = param.IDENTIFIER().getText();
                    variableIndexMap.put(paramName, variableIndexMap.size()); // Индекс переменной
                    System.out.println("-----------INDEX " + getVariableIndex(paramName, paramType, 1));
                    variableTypeMap.put(paramName, paramType); // Тип переменной
                }
            }
            if (jasminCode.charAt(jasminCode.length() - 1) == ';') {
                jasminCode.deleteCharAt(jasminCode.length() - 1);
            }
            jasminCode.append(")");

            // Обработка возвращаемого типа (если есть)
            if (ctx.type() != null) {
                if (ctx.type().getText().equals("integer") || ctx.type().getText().equals("real")
                        || ctx.type().getText().equals("boolean") || ctx.type().getText().equals("I")
                        || ctx.type().getText().equals("bool")
                        || ctx.type().getText().equals("D") || ctx.type().getText().equals("Z")) {
                    jasminCode.append(getTypeDescriptor(ctx.type().getText()));
                } else if (ctx.type() != null) {
                    jasminCode.append("L").append(getTypeDescriptor(ctx.type().getText())).append(";");
                }
            } else {
                jasminCode.append("V"); // По умолчанию void
            }
            jasminCode.append("\n");
            jasminCode.append(".limit stack 10\n")
                    .append(".limit locals 10\n");
        }

        // Указание начала метода и инициализация стека

        // If you have other routines, handle them here
    }

    private String getTypeDescriptor(String type) {
        switch (type) {
            case "integer":
                return "I"; // int
            case "real":
                return "F"; // float
            case "boolean":
                return "Z"; // boolean
            case "void":
                return "V"; // void
            default:
                return type; // Для пользовательских типов
        }
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
        } else {
            if (!ctx.getText().contains("return")) {
                jasminCode.append("   return\n");
            }
            jasminCode.append(".end method\n\n");
        }
    }

    private void performTypeCast(String fromType, String toType) {
        if (fromType.equals("I")) {
            fromType = "integer";
        } else if (fromType.equals("D")) {
            fromType = "real";
        } else if (fromType.equals("Z")) {
            fromType = "boolean";
        }

        if (toType.equals("I")) {
            toType = "integer";
        } else if (toType.equals("D")) {
            toType = "real";
        } else if (toType.equals("Z")) {
            toType = "boolean";
        }
        // jasminCode.append(fromType + " " + toType + "\n");
        if (!fromType.equals(toType)) {
            System.out.println("performing TypeCast: from " + fromType + " to " + toType);
            if (fromType.equals("real") && toType.equals("integer")) {
                System.out.println("from REAL to INTEGER");

                jasminCode.append("   d2i\n");
                updateStack(-1);
            } else if (fromType.equals("bool") && toType.equals("integer")) {
                jasminCode.append("   b2i\n");
            } else if (fromType.equals("real") && toType.equals("boolean")) {
                jasminCode.append("   d2i\n");
            }
        } else {
            System.out.println("no TypeCast ");

        }
    }

    @Override
    public void enterVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx) {
        System.out.println("ctx: " + ctx.getText());
        if (ctx.expression() != null) {
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
                System.out.println("user type? " + ctx.type().getText());
                varType = typeInfo.toString();

            }
            variableTypeMap.put(variableName, varType);
            System.out.println("variableName " + variableName + " varType " + varType);
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
                if (!isRoutineCall(ctx.expression())) {
                    // System.out.println("here");

                    if (varType.equals("I") || varType.equals("Z")) {
                        System.out.println("------here " + variableName + " " + varType);
                        jasminCode.append("   istore ").append(varIndex).append("\n");
                        updateStack(-1);
                    } else if (varType.equals("D")) {

                        jasminCode.append("   dstore ").append(varIndex).append("\n");
                        updateStack(-2);
                    }
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
            if (variableType != null) {
                // implement storing declared variables in jasmin code
                if (recordTypes.containsKey(variableType)) {
                    jasminCode.append("   new ").append(variableType).append("\n");
                    jasminCode.append("   dup\n");
                    jasminCode.append("   invokespecial ").append(variableType).append("/<init>()V\n");
                    // putstatic Main/s LStudent;
                    jasminCode.append("   putstatic ").append("Main/").append(currentPath).append(variableName)
                            .append(" L")
                            .append(variableType).append(";\n\n");

                }
            }
            // jasminCode.append(" end of variable declaration\n");

            // Просто выделяем память для переменной
            // allocateVariable(variableName, variableType);
        }
    }

    private boolean isRoutineCall(ImperativeLangParser.ExpressionContext ctx) {
        // // Если это выражение и оно содержит вызов рутины, проверяем его
        // if (ctx instanceof ImperativeLangParser.PrimaryContext) {
        // ImperativeLangParser.PrimaryContext primaryCtx =
        // (ImperativeLangParser.PrimaryContext) ctx;

        // // Проверяем, является ли текст выражения вызовом рутины
        // if (primaryCtx.routineCall() != null) {
        // return true; // Это вызов рутины
        // }
        // }

        // // Если это выражение с несколькими частями, проверяем их
        // if (ctx instanceof ImperativeLangParser.SummandContext) {
        // ImperativeLangParser.SummandContext summandCtx =
        // (ImperativeLangParser.SummandContext) ctx;
        // // Погружаемся в первое (или следующее) выражение
        // if (summandCtx.primary() != null) {
        // return isRoutineCall(summandCtx.primary());
        // }
        // }

        // // Если это сложное выражение, например, сложение или вычитание
        // if (ctx instanceof ImperativeLangParser.FactorContext) {
        // ImperativeLangParser.FactorContext factorCtx =
        // (ImperativeLangParser.FactorContext) ctx;
        // // Погружаемся в первичное выражение
        // if (factorCtx.summand() != null) {
        // return isRoutineCall(factorCtx.summand());
        // }
        // }

        // // Если это выражение с фактором, например, отношение или логическое
        // выражение

        // Если это выражение с операциями (and, or, xor), проверяем составные выражения
        System.out.println(ctx.getText());
        if (ctx instanceof ImperativeLangParser.ExpressionContext) {
            ImperativeLangParser.ExpressionContext expressionCtx = (ImperativeLangParser.ExpressionContext) ctx;
            System.out.println(expressionCtx.relation(0).getText());

            if (expressionCtx.relation().size() == 1) {
                System.out.println(4);

                ImperativeLangParser.RelationContext relationCtx = (ImperativeLangParser.RelationContext) expressionCtx
                        .relation(0);
                System.out.println(2);
                if (relationCtx.simple().size() == 1) {
                    ImperativeLangParser.SimpleContext simpleCtx = (ImperativeLangParser.SimpleContext) relationCtx
                            .simple(0);
                    System.out.println(3);
                    if (simpleCtx.factor().size() == 1) {
                        ImperativeLangParser.FactorContext factorCtx = (ImperativeLangParser.FactorContext) simpleCtx
                                .factor(0);
                        if (factorCtx.summand().size() == 1) {
                            ImperativeLangParser.SummandContext summandCtx = (ImperativeLangParser.SummandContext) factorCtx
                                    .summand(0);
                            if (summandCtx.primary() != null) {
                                ImperativeLangParser.PrimaryContext primaryCtx = (ImperativeLangParser.PrimaryContext) summandCtx
                                        .primary();
                                if (primaryCtx.routineCall() != null) {
                                    System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
                                    return true;
                                }
                            }
                        }
                    }

                }
            }
        }

        // Если выражение не является вызовом рутины
        return false;
    }

    private String analyzeExpressionType(ImperativeLangParser.ExpressionContext ctx) {
        // Здесь должен быть ваш анализатор типов, который проверяет выражение.
        System.out.println("Expression: " + ctx.getText());
        return getExpressionType(ctx);

        // return ctx.getText().matches("\\d+") ? "integer" : "real"; // Упрощённый
        // пример
    }

    @Override
    public void exitVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx) {
        // System.out.println("exitVariableDeclaration: " + ctx.getText() + " " +
        // ctx.expression().getText());
        String variableName = ctx.IDENTIFIER().getText();
        String varType = ctx.type() != null ? ctx.type().getText() : null;
        int varIndex = variableIndexMap.get(variableName);

        // Генерация кода выражения
        if (ctx.expression() != null) {
            if (isRoutineCall(ctx.expression())) {
                System.out.println("here");

                if (varType.equals("I") || varType.equals("Z") || varType.equals("integer") || varType.equals("boolean")
                        || varType.equals("bool") || varType.equals("Array<integer>")) {
                    System.out.println("------here " + variableName + " " + varType);
                    jasminCode.append("   istore ").append(varIndex).append("\n");
                    updateStack(-1);
                } else if (varType.equals("D")) {

                    jasminCode.append("   dstore ").append(varIndex).append("\n");
                    updateStack(-2);
                }
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
        // String varName = ctx.IDENTIFIER().getText();
        // int varIndex = getVariableIndex(varName);

        // String varType = "I"; // Default type is integer

        // if (ctx.type() != null) {
        // Object typeInfo = processType(ctx.type());
        // if (typeInfo.equals("integer")) {
        // varType = "I";
        // } else if (typeInfo.equals("real")) {
        // varType = "D";
        // } else if (typeInfo.equals("boolean")) {
        // varType = "I";
        // }
        // variableTypeMap.put(varName, varType);
        // }

    }

    private Object processType(ImperativeLangParser.TypeContext typeCtx) {
        System.out.println("typeCtx " + typeCtx.getText());
        if (typeCtx.primitiveType() != null) {
            return typeCtx.primitiveType().getText();
        } else if (typeCtx.userType() != null) {
            ImperativeLangParser.UserTypeContext userTypeCtx = typeCtx.userType();
            System.out.println("userTypeCtx " + userTypeCtx.getText());
            if (userTypeCtx.arrayType() != null) {
                return processArrayType(userTypeCtx.arrayType());
            } else if (userTypeCtx.recordType() != null) {
                return processRecordType(userTypeCtx.recordType());
            }
        } else if (typeCtx.IDENTIFIER() != null) {
            // Проверяем, является ли это типом записи
            String typeName = typeCtx.IDENTIFIER().getText();
            if (recordTypes.containsKey(typeName)) {
                return typeName; // Возвращаем имя типа записи
            }
            throw new RuntimeException("Неизвестный тип: " + typeName);
        }
        throw new RuntimeException("Неподдерживаемый тип");
    }

    // Метод для добавления нового type alias
    private void addTypeAlias(String aliasName, String actualType) {
        typeAliases.put(aliasName, actualType);
    }

    // Метод для получения реального типа из alias
    private String resolveTypeAlias(String typeName) {
        return typeAliases.getOrDefault(typeName, typeName);
    }

    @Override
    public void enterTypeDeclaration(ImperativeLangParser.TypeDeclarationContext ctx) {
        System.err.println("enterTypeDeclaration" + ctx.getText());
        String typeName = ctx.IDENTIFIER().getText();

        // Если это объявление типа записи
        if (ctx.type().userType() != null && ctx.type().userType().recordType() != null) {
            currentRecord = typeName;
            System.out.println("currentRecord " + currentRecord);

            // Создаем новую запись в recordTypes
            Map<String, String> fields = new HashMap<>();
            recordTypes.put(typeName, fields);

            // Обработка полей записи
            ImperativeLangParser.RecordTypeContext recordCtx = ctx.type().userType().recordType();
            StringBuilder recordCode = new StringBuilder();

            // Генерируем .j файл для записи
            recordCode.append(".class public ").append(typeName).append("\n");
            recordCode.append(".super java/lang/Object\n\n");

            // Обрабатываем поля записи
            for (ImperativeLangParser.VariableDeclarationContext varDecl : recordCtx.variableDeclaration()) {
                String fieldName = varDecl.IDENTIFIER().getText();
                String fieldType;

                if (varDecl.type() != null) {
                    fieldType = processType(varDecl.type()).toString();
                } else {
                    fieldType = getExpressionType(varDecl.expression());
                }

                // Сохраняем информацию о поле
                fields.put(fieldName, fieldType);

                // Добавляем объявление поля в .j файл
                recordCode.append(".field public ").append(fieldName).append(" ")
                        .append(getJasminType(fieldType)).append("\n");
            }

            // Добавляем конструктор
            recordCode.append("\n.method public <init>()V\n");
            recordCode.append("    aload_0\n");
            recordCode.append("    invokespecial java/lang/Object/<init>()V\n");

            // Инициализация полей значениями по умолчанию
            // for (Map.Entry<String, String> field : fields.entrySet()) {
            // String fieldName = field.getKey();
            // String fieldType = field.getValue();

            // recordCode.append(" aload_0\n");
            // switch (fieldType) {
            // case "integer":
            // recordCode.append(" iconst_0\n");
            // break;
            // case "real":
            // recordCode.append(" dconst_0\n");
            // break;
            // case "boolean":
            // recordCode.append(" iconst_0\n");
            // break;
            // default:
            // if (recordTypes.containsKey(fieldType)) {
            // // Если поле является записью, создаем новый экземпляр
            // recordCode.append(" new ").append(fieldType).append("\n");
            // recordCode.append(" dup\n");
            // recordCode.append(" invokespecial
            // ").append(fieldType).append("/<init>()V\n");
            // } else {
            // recordCode.append(" aconst_null\n");
            // }
            // }
            // recordCode.append(" putfield ").append(typeName).append("/")
            // .append(fieldName).append(" ")
            // .append(getJasminType(fieldType)).append("\n");
            // }

            recordCode.append("    return\n");
            recordCode.append(".end method\n\n");

            // Добавляем геттеры и сеттеры
            // for (Map.Entry<String, String> field : fields.entrySet()) {
            // generateGetter(recordCode, field.getKey(), field.getValue(), typeName);
            // generateSetter(recordCode, field.getKey(), field.getValue(), typeName);
            // }

            // Записываем .j файл
            try {
                FileWriter writer = new FileWriter(typeName + ".j");
                writer.write(recordCode.toString());
                writer.close();
                System.out.println("Создан файл для записи: " + typeName + ".j");
            } catch (IOException e) {
                throw new RuntimeException("Ошибка при создании файла для записи: " + e.getMessage());
            }

        }
    }

    // private void generateGetter(StringBuilder code, String fieldName, String
    // fieldType, String recordName) {
    // String jasminType = getJasminType(fieldType);
    // code.append(".method public
    // get").append(capitalize(fieldName)).append("()").append(jasminType).append("\n");
    // code.append(" .limit stack 1\n");
    // code.append(" .limit locals 1\n");
    // code.append(" aload_0\n");
    // code.append(" getfield
    // ").append(recordName).append("/").append(fieldName).append("
    // ").append(jasminType)
    // .append("\n");
    // code.append(" ").append(getReturnInstruction(fieldType)).append("\n");
    // code.append(".end method\n\n");
    // }

    // private void generateSetter(StringBuilder code, String fieldName, String
    // fieldType, String recordName) {
    // String jasminType = getJasminType(fieldType);
    // code.append(".method public
    // set").append(capitalize(fieldName)).append("(").append(jasminType).append(")V\n");
    // code.append(" .limit stack 2\n");
    // code.append(" .limit locals 2\n");
    // code.append(" aload_0\n");
    // code.append(" ").append(getLoadInstruction(fieldType, 1)).append("\n");
    // code.append(" putfield
    // ").append(recordName).append("/").append(fieldName).append("
    // ").append(jasminType)
    // .append("\n");
    // code.append(" return\n");
    // code.append(".end method\n\n");
    // }

    private String getJasminType(String type) {
        switch (type) {
            case "integer":
                return "I";
            case "real":
                return "D";
            case "boolean":
                return "Z";
            default:
                if (type.startsWith("array")) {
                    String elementType = type.substring(type.indexOf(']') + 1).trim();
                    return "[" + getJasminType(elementType);
                }
                // Для пользовательских типов (записей)
                if (recordTypes.containsKey(type)) {
                    return "L" + type + ";";
                }
                throw new RuntimeException("Неизвестный тип: " + type);
        }
    }

    // private String getLoadInstruction(String type, int index) {
    // switch (type) {
    // case "integer":
    // case "boolean":
    // return "iload_" + index;
    // case "real":
    // return "dload_" + index;
    // default:
    // return "aload_" + index;
    // }
    // }

    // private String getReturnInstruction(String type) {
    // switch (type) {
    // case "integer":
    // case "boolean":
    // return "ireturn";
    // case "real":
    // return "dreturn";
    // default:
    // return "areturn";
    // }
    // }

    // private String capitalize(String str) {
    // return str.substring(0, 1).toUpperCase() + str.substring(1);
    // }

    // Обработка массива
    private Object processArrayType(ImperativeLangParser.ArrayTypeContext arrayTypeCtx) {
        String arrayElementType = arrayTypeCtx.type().getText();
        String dimensions = arrayTypeCtx.expression() != null ? arrayTypeCtx.expression().getText() : "[]";

        System.out
                .println("Обнаружен массив с элементами типа: " + arrayElementType + " и размерностью: " + dimensions);

        // Добавление информации о массиве в jasminCode
        // jasminCode.append(" ; Объявление массива типа
        // ").append(arrayElementType).append("\n");

        // Генерация кода для определения размера массива
        if (arrayTypeCtx.expression() != null) {
            generateExpression(arrayTypeCtx.expression());
            performTypeCast(analyzeExpressionType(arrayTypeCtx.expression()), "integer");
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

        // Получаем имя массива из родительского VariableDeclarationContext
        ParseTree parent = arrayTypeCtx.getParent();
        if (parent instanceof ImperativeLangParser.UserTypeContext) {
            String arrayName = "";
            ImperativeLangParser.UserTypeContext utctx = (ImperativeLangParser.UserTypeContext) parent;
            parent = utctx.getParent();
            if (parent instanceof ImperativeLangParser.TypeContext) {
                ImperativeLangParser.TypeContext tctx = (ImperativeLangParser.TypeContext) parent;
                parent = tctx.getParent();
                if (parent instanceof ImperativeLangParser.VariableDeclarationContext) {
                    ImperativeLangParser.VariableDeclarationContext vdctx = (ImperativeLangParser.VariableDeclarationContext) parent;

                    arrayName = vdctx.IDENTIFIER().getText();
                    System.out.println("arrayName " + arrayName);
                }
            }

            // Записываем массив в локальную переменную
            int arrayIndex = getVariableIndex(arrayName, arrayElementType, 1);
            jasminCode.append("   astore ").append(arrayIndex).append("\n");
        } else {
            throw new RuntimeException("Не удалось получить имя массива.");
        }

        updateStack(-1); // Создание массива не изменяет стек.

        return "Array<" + arrayElementType + ">";
    }

    private Object processRecordType(ImperativeLangParser.RecordTypeContext recordCtx) {
        Map<String, String> fields = new HashMap<>();

        // В нашей грамматике recordType: 'record' '{' variableDeclaration* '}' 'end'
        for (ImperativeLangParser.VariableDeclarationContext varDecl : recordCtx.variableDeclaration()) {
            String fieldName;
            String fieldType;

            // Обработка обоих вариантов объявления переменной из грамматики
            if (varDecl.type() != null) {
                fieldName = varDecl.IDENTIFIER().getText();
                fieldType = processType(varDecl.type()).toString();
            } else {
                fieldName = varDecl.IDENTIFIER().getText();
                // Если тип не указан явно, определяем его из выражения
                fieldType = getExpressionType(varDecl.expression());
            }

            fields.put(fieldName, fieldType);
        }
        return fields;
    }

    @Override
    public void enterAssignment(ImperativeLangParser.AssignmentContext ctx) {
        System.out.println("Entering assignment: " + ctx.getText());
        // ??????
        if (ctx.modifiablePrimary().getText().contains("[")) {
            ImperativeLangParser.ModifiablePrimaryContext modCtx = ctx.modifiablePrimary();
            String varName = modCtx.IDENTIFIER(0).getText();
            String varType = variableTypeMap.get(varName);
            System.out.println("in exit assignment for variable: " + varName);

            int varIndex = getVariableIndex(varName, varType, 1);
            System.out.println("CONTAINS1!!!! " + ctx.getText());

            jasminCode.append("   aload ").append(varIndex).append("\n");
        } else if (ctx.modifiablePrimary().getText().contains(".")) {
            String varName = ctx.modifiablePrimary().IDENTIFIER(0).getText();
            String varType = variableTypeMap.get(varName);
            // get type of ctx.modifiablePrimary().getText().split("\\.")[1]
            String fieldType = recordTypes.get(varType).get(ctx.modifiablePrimary().getText().split("\\.")[1]);

            if (fieldType.equals("integer")) {
                fieldType = "I";
                // jasminCode.append("I").append("\n"); // Integer тип
            } else if (fieldType.equals("boolean") || fieldType.equals("bool")) {
                // jasminCode.append("Z").append("\n"); // Boolean тип
                fieldType = "Z";
            } else if (fieldType.equals("real")) {
                // jasminCode.append("D").append("\n"); // Double (real) тип
                fieldType = "D";
            }

            // getstatic Main/s LStudent;
            jasminCode.append("   getstatic ").append("Main/").append(ctx.modifiablePrimary().getText().split("\\.")[0])
                    .append(" L")
                    .append(varType).append(";\n");
            // bipush 20
            generateExpression(ctx.expression());
            performTypeCast(analyzeExpressionType(ctx.expression()), fieldType);

            // putfield Student/age I
            jasminCode.append("   putfield ").append(varType).append("/")
                    .append(ctx.modifiablePrimary().getText().split("\\.")[1]).append(" ")
                    .append(fieldType)
                    .append("\n\n");

        }
        // ??????
    }

    @Override
    public void exitAssignment(ImperativeLangParser.AssignmentContext ctx) {
        ImperativeLangParser.ModifiablePrimaryContext modCtx = ctx.modifiablePrimary();
        String varName = modCtx.IDENTIFIER(0).getText();
        String varType = variableTypeMap.get(varName);
        System.out.println("in exit assignment for variable: " + varName);

        // Генерация кода для выражения
        if (ctx.modifiablePrimary().getText().contains(".")) {

            return;
        }
        generateExpression(ctx.expression());
        int varIndex = getVariableIndex(varName, varType, 1);

        // Обработка массива
        if (modCtx.getText().contains("[")) {
            // Это обращение к массиву
            System.out.println("Array assignment detected: " + varName);

            // jasminCode.append("1111 aload ").append(varIndex).append("\n");
            // jasminCode.append(" swap ").append(varIndex).append("\n");

            // Получаем индекс массива из контекста
            ImperativeLangParser.ExpressionContext indexCtx = modCtx.expression(0);
            generateExpression(indexCtx); // Загружаем индекс на стек
            performTypeCast(analyzeExpressionType(indexCtx), "integer"); // Преобразуем тип индекса, если нужно

            // Корректировка индекса: ычитаем 1, чтобы получить правильную индексацию
            jasminCode.append("   iconst_1\n"); // Загружаем 1
            jasminCode.append("   isub\n"); // Вычитаем 1 из индекса

            jasminCode.append("   swap").append("\n");
            // jasminCode.append(varType).append("\n");

            // Записываем в массив
            if (varType.equals("I") || varType.equals("Z") || varType.equals("integer") || varType.equals("boolean")
                    || varType.equals("bool") || varType.equals("Array<integer>")) {
                // jasminCode.append("1 aload ").append(varIndex).append("\n");
                // jasminCode.append("1 swap").append("\n");
                jasminCode.append("   iastore\n"); // Сохраняем значение в массив для целых чисел
                updateStack(-3); // После iastore в стеке должно быть меньше элементов
            } else if (varType.equals("D")) {
                jasminCode.append("   dastore\n"); // Для типов с плавающей запятой
                updateStack(-3); // После dastore в стеке должно быть меньше элементов
            } else if (varType.equals("Z")) {
                jasminCode.append("   bastore\n"); // Для булевых типов
                updateStack(-3); // После bastore в стеке должно быть меньше элементов
            }
        } else {
            // Для обычной переменной

            if (varType.equals("I") || varType.equals("Z")) {
                jasminCode.append("   istore ").append(varIndex).append("\n");
                updateStack(-1);
            } else if (varType.equals("D")) {
                jasminCode.append("   dstore ").append(varIndex).append("\n");
                updateStack(-2);
            } else {
                // Обработка других типов, если необходимо
            }
        }
        // System.out.println("ergopj" + ctx.getText());

        // jasminCode.append("smth\n");
        System.out.println("exit assignment " + ctx.getText());
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

        // jasminCode.append(" ifeq Label").append(endLabel).append("\n");
        // updateStack(-1);
    }

    @Override
    public void exitWhileLoop(ImperativeLangParser.WhileLoopContext ctx) {
        int startLabel = startLabels.get(ctx);
        int endLabel = endLabels.get(ctx);
        // System.out.println("startLabels " + startLabels + "\nendLabels " +
        // endLabels);
        // Напечатаем содержимое startLabels
        // System.out.println("Start Labels:");
        // for (ParseTree node : startLabels.keySet()) {
        // int label = startLabels.get(node);
        // System.out.println("Node: " + node.getText() + " -> Label" + label);
        // }

        // // Напечатаем содержимое endLabels
        // System.out.println("End Labels:");
        // for (ParseTree node : endLabels.keySet()) {
        // int label = endLabels.get(node);
        // System.out.println("Node: " + node.getText() + " -> Label" + label);
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

        // if (ctx.getText().contains("else")) {
        int elseLabel = getNextLabel();
        startLabels.put(ctx, elseLabel);
        startLabelsStack.push(elseLabel); // позер

        endLabels.put(ctx, elseLabel);
        endLabelsStack.push(elseLabel);
        System.out.println("elseLabel: " + elseLabel);

        // }
        int endLabel = getNextLabel();
        int ifendlabel = getNextLabel();
        ifEndLabelsStack.push(ifendlabel); // реальный конец иф
        System.out.println("endLabel: " + endLabel + ", ifendlabel: " + ifendlabel);

        System.out.println("ctx.getText(): " + ctx.getText());
        System.out.println("ctx.expression().getText() " + ctx.expression().getText());
        generateExpression(ctx.expression());

        if (ctx.getText().contains("else")) {

            System.out.println("trying... " + ctx.body(0).getText());
            if (ctx.body() != null && ctx.body().size() > 0) {
                for (ImperativeLangParser.StatementContext statementCtx : ctx.body(0).statement()) {
                    // System.out.println("routine call------------------------------- " +
                    // statementCtx.getText() + ": "
                    // + statementCtx.routineCall().getText());
                    if (statementCtx.routineCall() != null) {
                        generateRoutineCall(statementCtx.routineCall());
                        System.out.println(
                                "finished routine call----------------------- " + statementCtx.routineCall().getText());

                    }

                    // if (statementCtx instanceof ImperativeLangParser.RoutineCallContext) {
                    // // Обработка вызова процедуры
                    // ImperativeLangParser.RoutineCallContext routineCallCtx =
                    // (ImperativeLangParser.RoutineCallContext) statementCtx;
                    // generateRoutineCall(routineCallCtx);
                    // }
                }
                jasminCode.append("   goto Label").append(ifEndLabelsStack.peek()).append("\n");
                if (ctx.getText().contains("else")) {
                    jasminCode.append("Label").append(elseLabel).append(":\n");
                }

            }
            System.out.println("trying...x2 " + ctx.body(1).getText());
            // generateExpression(ctx.body(1));
            System.out.println("haha?");

        } else {
            // jasminCode.append("no else").append("\n");
            System.out.println("trying... " + ctx.body(0).getText());
            if (ctx.body() != null && ctx.body().size() > 0) {
                for (ImperativeLangParser.StatementContext statementCtx : ctx.body(0).statement()) {
                    System.out.println("routine call------------------------------- " + statementCtx.getText() + ": "
                            + statementCtx.routineCall().getText());
                    if (statementCtx.routineCall() != null) {
                        generateRoutineCall(statementCtx.routineCall());
                        System.out.println(
                                "finished routine call----------------------- " + statementCtx.routineCall().getText());

                    }

                    // if (statementCtx instanceof ImperativeLangParser.RoutineCallContext) {
                    // // Обработка вызова процедуры
                    // ImperativeLangParser.RoutineCallContext routineCallCtx =
                    // (ImperativeLangParser.RoutineCallContext) statementCtx;
                    // generateRoutineCall(routineCallCtx);
                    // }
                }
                // jasminCode.append(" goto
                // Label").append(ifEndLabelsStack.peek()).append("\n");
                // if (ctx.getText().contains("else")){
                // jasminCode.append("Label").append(elseLabel).append(":\n");
                // }

            }

        }

        // jasminCode.append(" ifeq Label").append(endLabel).append("\n");
        // updateStack(-1);
        // jasminCode.append(" exitting IfStatement...").append("\n");
    }

    @Override
    public void exitIfStatement(ImperativeLangParser.IfStatementContext ctx) {
        int elseLabel = startLabels.get(ctx);
        System.out.println("elseLabel " + elseLabel);
        int ifendLabel = ifEndLabelsStack.peek();

        // jasminCode.append(" exitIfStatement...").append("\n");
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
    public void enterRoutineCall(ImperativeLangParser.RoutineCallContext ctx) {
        inRoutineCall = true;
        currentRoutineCall = ctx.IDENTIFIER().getText();
        System.out.println("CURRENT ROUTINE CALL: " + currentRoutineCall);

        super.enterRoutineCall(ctx);
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
                    if (ifCtx.body(0).getText().equals(ctx.getText())) {
                        System.out.println("скип");

                        return;
                    }
                }

            }

        }

        if (funcName.equals("print")) {
            ImperativeLangParser.ExpressionListContext exprList = ctx.expressionList();
            if (exprList != null && exprList.expression().size() == 1) {
                // jasminCode.append(" exitRoutineCall;\n");
                jasminCode.append("   getstatic java/lang/System/out Ljava/io/PrintStream;\n");
                updateStack(1);

                generateExpression(exprList.expression(0));
                // jasminCode.append(" iconst_1").append("\n");
                // jasminCode.append(" isub").append("\n");
                String exprType = getExpressionType(exprList.expression(0));

                if (exprType.equals("I") || exprType.equals("Z")) {
                    jasminCode.append("   invokevirtual java/io/PrintStream/println(I)V\n");
                    updateStack(-2);
                } else if (exprType.equals("D")) {
                    jasminCode.append("   invokevirtual java/io/PrintStream/println(D)V\n");
                    updateStack(-3);
                } else if (exprType.contains("Array<") && exprType.contains(">")) {
                    // Печать элемента массива
                    String elementType = exprType.substring(6, exprType.length() - 1); // Получаем тип элементов массива

                    // Загружаем массив на стек
                    // jasminCode.append(" aload ")
                    // .append(getVariableIndex(exprList.expression(0).getText(), exprType,
                    // 0)).append("\n");
                    // jasminCode.append(exprList.expression(0).getText()).append("\n");
                    // updateStack(1);

                    // Получаем индекс элемента массива (например, передается в print() как второй
                    // аргумент)
                    ImperativeLangParser.ExpressionContext indexCtx = exprList.expression(1); // Индекс в массиве
                    // generateExpression(indexCtx); // Генерация кода для вычисления индекса

                    // // Загружаем элемент массива в зависимости от его типа
                    // if (elementType.equals("I") || elementType.equals("Z") ||
                    // elementType.equals("integer")
                    // || elementType.equals("boolean") || elementType.equals("bool")) {
                    // jasminCode.append("1 iaload\n"); // Для целых чисел
                    // updateStack(-1);
                    // } else if (elementType.equals("D")) {
                    // jasminCode.append(" daload\n"); // Для чисел с плавающей запятой
                    // updateStack(-1);
                    // } else {
                    // throw new UnsupportedOperationException(
                    // "Unsupported array element type for print: " + elementType);
                    // }

                    // Печать элемента массива
                    if (elementType.equals("I") || elementType.equals("Z") || elementType.equals("integer")
                            || elementType.equals("boolean") || elementType.equals("bool")) {
                        jasminCode.append("   invokevirtual java/io/PrintStream/println(I)V\n");
                        updateStack(-1);
                    } else if (elementType.equals("D")) {
                        jasminCode.append("   invokevirtual java/io/PrintStream/println(D)V\n");
                        updateStack(-2);
                    }
                } else if (exprType.contains("{")) {
                    System.out.println("-----------record type " + exprType);
                } else if (recordTypes.containsKey(exprType)) {
                    if (!jasminCode.toString()
                            .contains(".field public static " + exprList.expression(0).getText().split("\\.")[0])) {
                        int methodIndex = jasminCode.indexOf(".method");
                        if (methodIndex != -1) {
                            // Вставляем строку перед первым вхождением .method
                            jasminCode.insert(methodIndex,
                                    ".field public static " + exprList.expression(0).getText().split("\\.")[0] + " L"
                                            + exprType
                                            + ";\n\n");
                        }
                    }
                    System.err.println("Record type " + exprType);
                    String recordName = exprList.expression(0).relation(0).simple(0).factor(0).summand(0).primary()
                            .modifiablePrimary().getText().split("\\.")[0];
                    // getstatic Main/s LStudent;
                    jasminCode.append("   getstatic ").append("Main/").append(currentPath.toString())
                            .append(recordName)
                            .append(" L").append(exprType).append(";\n");
                    // jasminCode.append(" getstatic ").append("Main/")
                    // .append(ctx.modifiablePrimary().getText().split("\\.")[0])
                    // .append(" L")
                    // .append(varType).append(";\n");

                    String fieldType = recordTypes.get(exprType).get(exprList.expression(0).relation(0).simple(0)
                            .factor(0).summand(0).primary().modifiablePrimary().getText().split("\\.")[1]);
                    if (fieldType.equals("integer")) {
                        fieldType = "I";
                        // jasminCode.append("I").append("\n"); // Integer тип
                    } else if (fieldType.equals("boolean") || fieldType.equals("bool")) {
                        // jasminCode.append("Z").append("\n"); // Boolean тип
                        fieldType = "Z";
                    } else if (fieldType.equals("real")) {
                        // jasminCode.append("D").append("\n"); // Double (real) тип
                        fieldType = "D";
                    }

                    // getfield Student/age I
                    jasminCode.append("   getfield ").append(exprType).append("/")
                            .append(exprList.expression(0).getText().split("\\.")[1])
                            .append(" ").append(fieldType).append("\n");
                    // invokevirtual java/io/PrintStream/println(I)V
                    jasminCode.append("   invokevirtual java/io/PrintStream/println(").append(fieldType)
                            .append(")V\n\n");
                    updateStack(-2);

                    // jasminCode.append("2 aload ")
                    // .append(getVariableIndex(exprList.expression(0).getText(), exprType,
                    // 0)).append("\n");
                } else {
                    throw new UnsupportedOperationException("Unsupported type for print: " + exprType);
                }
            } else {
                throw new UnsupportedOperationException("print() with multiple arguments is not supported.");
            }
        } else {
            handleUserDefinedRoutineCall(ctx, funcName);
            // throw new UnsupportedOperationException("Routine call to " + funcName + " is
            // not supported.");
        }
        inRoutineCall = false;
        currentRoutineCall = "";
        currentParam = 0;
        System.out.println("Finished routine call");
    }

    // Метод для обработки вызова пользовательских рутин
    private void handleUserDefinedRoutineCall(ImperativeLangParser.RoutineCallContext ctx, String funcName) {
        System.out.println("Метод для обработки вызова пользовательских рутин " + ctx.expressionList().getText());
        ImperativeLangParser.ExpressionListContext exprList = ctx.expressionList();

        int argumentCount = (exprList != null) ? exprList.expression().size() : 0;
        maxLocals += argumentCount * 2;
        maxStack += argumentCount * 2;

        // Генерация кода для каждого аргумента
        if (exprList != null) {
            for (ImperativeLangParser.ExpressionContext exprCtx : exprList.expression()) {
                generateExpression(exprCtx);
            }
        }

        if (inMainMethod) {
            // Вызов пользовательской рутины
            jasminCode.append("   invokestatic ")
                    .append("Main/")
                    .append(funcName)
                    .append("(");
        } else {
            // Вызов пользовательской рутины
            jasminCode.append("   invokestatic ")
                    // .append("Main/")
                    .append(currentPath)
                    .append(funcName)
                    .append("(");
        }

        // Добавляем дескрипторы типов аргументов
        System.out.println("routineParamTypes " + routineParamTypes.get(funcName));
        if (routineParamTypes.get(funcName) != null) {
            System.out.println("routineParamTypes is not null");
            for (String paramType : routineParamTypes.get(funcName)) {
                if (paramType.equals("integer") || paramType.equals("I")) {
                    jasminCode.append("I");
                } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]I") ||
                        paramType.equals("Array<I>") ||
                        paramType.matches("array\\[\\d+(\\.\\d+)?\\]integer") ||
                        paramType.equals("Array<integer>")) {
                    jasminCode.append("[I");
                } else if (paramType.equals("real") || paramType.equals("D")) {
                    jasminCode.append("D");
                } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]D") ||
                        paramType.equals("Array<D>") ||
                        paramType.matches("array\\[\\d+(\\.\\d+)?\\]real") ||
                        paramType.equals("Array<real>")) {
                    jasminCode.append("[D");
                } else if (paramType.equals("bool") || paramType.contains("boolean") || paramType.equals("Z")) {
                    jasminCode.append("Z");
                } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]Z") ||
                        paramType.equals("Array<Z>") ||
                        paramType.matches("array\\[\\d+(\\.\\d+)?\\]bool") ||
                        paramType.equals("Array<bool>") ||
                        paramType.matches("array\\[\\d+(\\.\\d+)?\\]boolean") ||
                        paramType.equals("Array<boolean>")) {
                    jasminCode.append("[Z");
                } else {
                    // System.out.println("paramType!!!!!S " + paramType);
                    jasminCode.append("L").append(paramType).append(";");
                    // jasminCode.append(paramType);
                }
            }

        }

        // if (routineParamTypes.get(funcName) != null){
        // // if (exprList != null) {
        // for (String paramType : routineParamTypes.get(funcName)) {
        // // String paramType = getExpressionType(exprCtx);
        // if (paramType.equals("integer") || paramType.equals("I")){
        // jasminCode.append("I");
        // } else if (paramType.matches("array\\[\d+(\\.\\d+)?\\]I") ||
        // paramType.equals("Array<I>") ||
        // paramType.matches("array\\[\\d+(\\.\\d+)?\\]integer") ||
        // paramType.equals("Array<integer>")){
        // jasminCode.append("[I");
        // } else if (paramType.equals("real") || paramType.equals("D")){
        // jasminCode.append("D");
        // } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]D") ||
        // paramType.equals("Array<D>") ||
        // paramType.matches("array\\[\\d+(\\.\\d+)?\\]real") ||
        // paramType.equals("Array<real>")){
        // jasminCode.append("[D");
        // } else if (paramType.equals("bool") || paramType.contains("boolean") ||
        // paramType.equals("Z")){
        // jasminCode.append("Z");
        // } else if (paramType.matches("array\\[\\d+(\\.\\d+)?\\]Z") ||
        // paramType.equals("Array<Z>") ||
        // paramType.matches("array\\[\\d+(\\.\\d+)?\\]bool") ||
        // paramType.equals("Array<bool>") ||
        // paramType.matches("array\\[\\d+(\\.\\d+)?\\]boolean") ||
        // paramType.equals("Array<boolean>")){
        // jasminCode.append("[Z");
        // } else {
        // jasminCode.append(paramType);
        // }

        // }
        // }
        // }
        // }
        // }
        // }
        // }
        // }

        if (jasminCode.charAt(jasminCode.length() - 1) == ';') {
            jasminCode.deleteCharAt(jasminCode.length() - 1);
        }
        jasminCode.append(")");

        // Если возвращаемый тип рутины известен, добавляем его
        if (routineTypeMap.containsKey(funcName)) {
            String returnType = routineTypeMap.get(funcName);

            // Определяем соответствующий тип Jasmin
            if (returnType.equals("integer")) {
                jasminCode.append("I").append("\n"); // Integer тип
            } else if (returnType.equals("boolean") || returnType.equals("bool")) {
                jasminCode.append("Z").append("\n"); // Boolean тип
            } else if (returnType.equals("real")) {
                jasminCode.append("D").append("\n"); // Double (real) тип
            } else {
                if (recordTypes.containsKey(returnType)) {
                    jasminCode.append("L").append(returnType).append(";\n");
                    jasminCode.append("   areturn\n");
                } else {
                    throw new UnsupportedOperationException("Unsupported return type: " + returnType);
                }
            }
        } else {
            jasminCode.append("V").append("\n"); // По умолчанию void

        }

        updateStack(-argumentCount); // Снижаем стек на количество аргументов
    }

    private void generateExpression(ImperativeLangParser.ExpressionContext ctx) {
        System.out.println("Compiling an expression... " + ctx.getText());
        System.out.println(currentStack);
        if (ctx.relation().size() == 1) {
            // System.out.println("generateExpression relation " +
            // ctx.relation(0).getText());
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
        // System.out.println("generateRelation " + ctx.getText() + " " +
        // ctx.simple().size());
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

            // jasminCode.append(" generateRelation beginning").append("\n");

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

            // jasminCode.append(" iconst_0\n");
            updateStack(1);

            // jasminCode.append(ctx.getText());

            ParseTree parent = ctx.getParent();
            System.out.println("here!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            // jasminCode.append(parent.getText());
            // if (parent instanceof ImperativeLangParser.StatementContext) {
            // System.out.println("Этот routinecall является частью statement...");
            // ImperativeLangParser.StatementContext stCtx =
            // (ImperativeLangParser.StatementContext) parent;
            // parent = stCtx.getParent();
            if (parent instanceof ImperativeLangParser.ExpressionContext) {
                System.out.println("Этот statement является частью expression...");
                ImperativeLangParser.ExpressionContext exCtx = (ImperativeLangParser.ExpressionContext) parent;
                parent = exCtx.getParent();
                if (parent instanceof ImperativeLangParser.IfStatementContext) {
                    System.out.println("Этот body является частью ifStatement...");
                    ImperativeLangParser.IfStatementContext ifCtx = (ImperativeLangParser.IfStatementContext) parent;
                    if (ifCtx.getText().contains("else")) {
                        jasminCode.append("   goto Label").append(endLabelsStack.peek()).append("\n"); // через перента

                        jasminCode.append("Label").append(labelTrue).append(":\n"); // 5
                    } else {
                        jasminCode.append("   goto Label").append(ifEndLabelsStack.peek()).append("\n"); // через
                                                                                                         // перента

                        jasminCode.append("Label").append(labelTrue).append(":\n"); // 5

                        System.out.println("no else((");
                    }
                    // if (ifCtx.body(0).getText().equals(ctx.getText())) {
                    // System.out.println("скип2");

                    // return;
                    // }
                } else {
                    jasminCode.append("   goto Label").append(endLabelsStack.peek()).append("\n"); // через перента

                    jasminCode.append("Label").append(labelTrue).append(":\n");
                }

            }

            // }
            // jasminCode.append("0 goto Label").append(endLabelsStack.peek()).append("\n");
            // // через перента

            // jasminCode.append("Label").append(labelTrue).append(":\n"); // 5
            // jasminCode.append(" iconst_1\n");
            // updateStack(1); // Already accounted for
            // jasminCode.append("6Label").append(labelEnd).append(":\n");

            // jasminCode.append(" generateRelation end").append("\n");
        }
    }

    private void generateSimple(ImperativeLangParser.SimpleContext ctx) {
        System.out.println("generateSimple " + ctx.getText() + " " + ctx.factor().size());
        if (ctx.factor().size() == 1) {
            generateFactor(ctx.factor(0));
        } else {
            String exprType = getExpressionType(ctx.factor(0));
            generateFactor(ctx.factor(0));

            for (int i = 1; i < ctx.factor().size(); i++) {
                generateFactor(ctx.factor(i));
                String op = ctx.getChild(2 * i - 1).getText();
                System.out.println("++++++++++++++++++++++++++++++++++++++++++++++" + exprType);
                if (exprType.equals("I") || exprType.equals("integer")) {
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
        // System.out.println("generateFactor " + ctx.getText() + " " +
        // ctx.summand().size());
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
        // jasminCode.append(" here\n");
        System.err.println("-----------------primary " + ctx.getText());

        if (ctx.INTEGER_LITERAL() != null) {
            System.out.println("generatePrimary INTEGER_LITERAL " + ctx.INTEGER_LITERAL().getText());
            int value = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
            if (value >= -1 && value <= 5) {
                // System.out.println("HERE");
                // if (value == 1){
                // jasminCode.append("7 iconst_1\n");
                // } else {
                jasminCode.append("   iconst_").append(value).append("\n");
                // }
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
                // ParseTree parent = ctx.getParent();
                // System.out.println("parent" + parent.getText());

                // if (parent instanceof ImperativeLangParser.SummandContext) {
                // ImperativeLangParser.SummandContext sctx =
                // (ImperativeLangParser.SummandContext) parent;
                // parent = sctx.getParent();
                // System.out.println("1.");

                // if (parent instanceof ImperativeLangParser.FactorContext) {
                // ImperativeLangParser.FactorContext fctx =
                // (ImperativeLangParser.FactorContext) parent;
                // parent = fctx.getParent();
                // System.out.println("2.");

                // if (parent instanceof ImperativeLangParser.SimpleContext) {
                // ImperativeLangParser.SimpleContext simctx =
                // (ImperativeLangParser.SimpleContext) parent;
                // parent = simctx.getParent();
                // System.out.println("3.");

                // if (parent instanceof ImperativeLangParser.RelationContext) {
                // ImperativeLangParser.RelationContext rctx =
                // (ImperativeLangParser.RelationContext) parent;
                // parent = rctx.getParent();
                // if (parent instanceof ImperativeLangParser.ExpressionContext) {
                // ImperativeLangParser.ExpressionContext ectx =
                // (ImperativeLangParser.ExpressionContext) parent;
                // parent = ectx.getParent();
                // if (parent instanceof ImperativeLangParser.RoutineCallContext) {
                // ImperativeLangParser.RoutineCallContext roctx =
                // (ImperativeLangParser.RoutineCallContext) parent;
                // // parent = ectx.getParent();
                // System.out.println("THIS IS PARAMETER");
                // }
                // }
                // }
                // }
                // }
                // }

                if (inRoutineCall) {
                    // routineParamTypes.get(currentRoutineCall).get(0);

                    performTypeCast("real", routineParamTypes.get(currentRoutineCall).get(currentParam)); // ??
                    currentParam++;
                }
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
            System.err.println("-----------------routineCall " + ctx.routineCall().getText());
            // jasminCode.append(ctx.routineCall().getText() + "\n");
            // Handle routine calls
            generateRoutineCall(ctx.routineCall());
            // jasminCode.append(" iconst_1\n");
            // updateStack(1);
            // jasminCode.append(" isub\n");
            // updateStack(-1);
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
            if (varType.equals("I") || varType.equals("Z") || varType.equals("integer") || varType.equals("boolean")
                    || varType.equals("bool")) {
                jasminCode.append("   iload ").append(varIndex).append("\n");
                updateStack(1);
            } else if (varType.equals("D")) {
                jasminCode.append("   dload ").append(varIndex).append("\n");
                updateStack(2);
            }
        } else {
            // Access array element (not implemented)
            // throw new UnsupportedOperationException("Array access is not supported
            // yet.");
            // Доступ к элементу массива
            if (varType.startsWith("Array<") || varType.startsWith("array[")) {
                String elementType = "";
                if (varType.startsWith("Array<")) {
                    elementType = varType.substring(6, varType.length() - 1); // Получаем тип элементов массива
                } else if (varType.startsWith("array[")) {
                    int endIndex = varType.indexOf(']') + 1;
                    elementType = varType.substring(endIndex); // Получаем тип элементов массива
                    // elementType = varType.substring(6, varType.length()); // Получаем тип
                    // элементов массива
                }

                // Загрузка массива в стек
                jasminCode.append("   aload ").append(varIndex).append("\n");
                updateStack(1);

                // Обработка индекса массива
                ImperativeLangParser.ExpressionContext indexCtx = ctx.expression(0);
                generateExpression(indexCtx); // Генерация кода для вычисления индекса
                if (ctx.getText().contains("[")) {
                    System.err.println("-----------------arr " + ctx.getText());
                    jasminCode.append("   iconst_1\n");
                    updateStack(1);
                    jasminCode.append("   isub\n");
                    updateStack(-1);
                }

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
            } else if (ctx.getText().contains(".")) {
                System.out.println("-----------record type " + ctx.getText());
            } else {
                System.out.println("-----------notarray, not record type " + ctx.getText());
                throw new UnsupportedOperationException("what is it? " + varType);
            }
        }
    }

    private void generateRoutineCall(ImperativeLangParser.RoutineCallContext ctx) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!=fjr");
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
        } else {
            if (ctx.expressionList() != null) {
                for (ImperativeLangParser.ExpressionContext expressionCtx : ctx.expressionList().expression()) {
                    if (expressionCtx.relation().size() == 1) {
                        System.out.println(4);

                        ImperativeLangParser.RelationContext relationCtx = (ImperativeLangParser.RelationContext) expressionCtx
                                .relation(0);
                        System.out.println(2);
                        if (relationCtx.simple().size() == 1) {
                            ImperativeLangParser.SimpleContext simpleCtx = (ImperativeLangParser.SimpleContext) relationCtx
                                    .simple(0);
                            System.out.println(3);
                            if (simpleCtx.factor().size() == 1) {
                                ImperativeLangParser.FactorContext factorCtx = (ImperativeLangParser.FactorContext) simpleCtx
                                        .factor(0);
                                System.out.println(5);

                                if (factorCtx.summand().size() == 1) {
                                    System.out.println(6);
                                    ImperativeLangParser.SummandContext summandCtx = (ImperativeLangParser.SummandContext) factorCtx
                                            .summand(0);
                                    if (summandCtx.primary() != null) {
                                        System.out.println(7);
                                        ImperativeLangParser.PrimaryContext primaryCtx = (ImperativeLangParser.PrimaryContext) summandCtx
                                                .primary();
                                        if (variableIndexMap.containsKey(primaryCtx.getText())) {
                                            System.out.println("HERE" + primaryCtx.getText() + " "
                                                    + variableTypeMap.get(primaryCtx.getText()));
                                            if (variableTypeMap.get(primaryCtx.getText()).equals("integer")) {
                                                System.out.println("INTEGER");
                                                jasminCode.append("   iload ")
                                                        .append(variableIndexMap.get(primaryCtx.getText()))
                                                        .append("\n");
                                            } else if (variableTypeMap.get(primaryCtx.getText()).contains("arr")
                                                    || variableTypeMap.get(primaryCtx.getText()).contains("Arr")) {
                                                System.out.println("ARRAY");
                                                jasminCode.append("   aload ")
                                                        .append(variableIndexMap.get(primaryCtx.getText()))
                                                        .append("\n");
                                                System.out.println("ARRAY");

                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                    // generateExpression(expr);

                }
                System.out.println("wjOPS'VM'SPV'ARO");
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
            // System.out.println("primaryCtx is " + primaryCtx.getClass().getSimpleName() +
            // " " + primaryCtx.getText());
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
            } else if (primaryCtx.routineCall() != null) {
                String routineName = primaryCtx.routineCall().IDENTIFIER().getText();
                if (routineTypeMap.containsKey(routineName)) {
                    return routineTypeMap.get(routineName); // Возвращаем тип рутины
                } else {
                    throw new UnsupportedOperationException("Unknown routine return type for: " + routineName);
                }
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

        System.out.println("---!--- Unable to determine " + ctx.getText());
        throw new UnsupportedOperationException("Unable to determine expression type.");
    }

    @Override
    public void enterForLoop(ImperativeLangParser.ForLoopContext ctx) {
        System.out.println("\nEntering for loop...");

        int startLabel = getNextLabel(); // Метка начала цикла
        int endLabel = getNextLabel(); // Метка конца цикла

        String varName = ctx.IDENTIFIER().getText(); // Получаем имя переменной
        variableTypeMap.put(varName, "integer"); // Устанавливаем тип переменной как "I" (целое число)

        System.out.println("------------------------------------" + varName);
        int varIndex = getVariableIndex(varName, "integer", 1);

        // getVariableIndex(variableName, variableType, 1);

        startLabels.put(ctx, startLabel);
        endLabels.put(ctx, endLabel);

        // Проверка наличия ключевого слова 'reverse'
        boolean isReverse = ctx.getText().contains("reverse");

        // Генерация кода для начального значения диапазона
        generateExpression(ctx.range().expression(0)); // Первое выражение в range
        // jasminCode.append(" istore"); // Сохраняем в локальную переменную (цикл
        // переменная)
        performTypeCast(analyzeExpressionType(ctx.range().expression(0)), "integer");
        varIndex = getVariableIndex(varName, "integer", 1);
        jasminCode.append("   istore ").append(varIndex).append("\n");
        updateStack(1);

        // Генерация кода для конечного значения диапазона
        generateExpression(ctx.range().expression(1)); // Второе выражение в range

        // jasminCode.append(" istore"); // Сохраняем в локальную переменную (конец
        // диапазона)
        System.out.println("ctx.range() " + ctx.range().expression(1).getText());
        performTypeCast(analyzeExpressionType(ctx.range().expression(1)), "integer");
        varIndex = getVariableIndex(ctx.range().expression(1).getText(), "integer", 1);
        jasminCode.append("   istore ").append(varIndex).append("\n");
        updateStack(1);

        // Метка начала цикла
        jasminCode.append("Label").append(startLabel).append(":\n");

        // Загружаем значения переменной и конца диапазона
        jasminCode.append("   iload ").append(variableIndexMap.get(varName)).append("\n"); // Теущее значение
                                                                                           // переменной цикла ??
        updateStack(1);
        jasminCode.append("   iload ").append(variableIndexMap.get(ctx.range().expression(1).getText())).append("\n"); // Конечное
                                                                                                                       // значение
                                                                                                                       // диапазона
                                                                                                                       // ??
        updateStack(1);

        // Условие выхода из цикла
        if (isReverse) {
            jasminCode.append("   if_icmplt Label").append(endLabel).append("\n"); // Для обратного порядка
        } else {
            jasminCode.append("   if_icmpgt Label").append(endLabel).append("\n"); // Для прямого порядка
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

            jasminCode.append("   isub\n"); // Вычитаем 1, если 'reverse'
        } else {
            jasminCode.append("   iconst_1\n");
            updateStack(1);

            jasminCode.append("   iadd\n"); // Прибавляем 1, если нет 'reverse'
        }
        // jasminCode.append(" istore\n");
        jasminCode.append("   istore ").append(variableIndexMap.get(ctx.IDENTIFIER().getText())).append("\n");

        // Переход к началу цикла
        jasminCode.append("   goto Label").append(startLabel).append("\n");

        // Метка конца цикла
        jasminCode.append("Label").append(endLabel).append(":\n");
    }

    @Override
    public void enterRecordType(ImperativeLangParser.RecordTypeContext ctx) {
        // String recordName = ctx.IDENTIFIER().getText();
        // currentRecord = recordName;
        // recordTypes.put(recordName, new HashMap<>());
        // recordJasminCode.put(recordName, new StringBuilder());

        // // Инициализация файла .j для записи
        // StringBuilder recordCode = recordJasminCode.get(recordName);
        // recordCode.append(".class public ").append(recordName).append("\n");
        // recordCode.append(".super java/lang/Object\n\n");

        // // Добавляем поля записи
        // for (ImperativeLangParser.VariableDeclarationContext varDecl :
        // ctx.recordType().variableDeclaration()) {
        // String fieldName = varDecl.IDENTIFIER().getText();
        // String fieldType = processType(varDecl.type()).toString();
        // recordTypes.get(recordName).put(fieldName, fieldType);

        // // Добавляем объявление поля в Jasmin код
        // recordCode.append(".field public ").append(fieldName).append(" ");
        // recordCode.append(getJasminType(fieldType)).append("\n");
        // }

        // // Добавляем конструктор
        // recordCode.append("\n.method public <init>()V\n");
        // recordCode.append(" aload_0\n");
        // recordCode.append(" invokespecial java/lang/Object/<init>()V\n");
        // recordCode.append(" return\n");
        // recordCode.append(".end method\n\n");

        // // Добавляем геттеры и сеттеры для каждого поля
        // for (Map.Entry<String, String> field :
        // recordTypes.get(recordName).entrySet()) {
        // generateGetter(recordCode, field.getKey(), field.getValue());
        // generateSetter(recordCode, field.getKey(), field.getValue());
        // }
    }

    @Override
    public void exitRecordType(ImperativeLangParser.RecordTypeContext ctx) {
        // String recordName = ctx.IDENTIFIER().getText();

        // // Записываем сгенерированный код в файл
        // try {
        // FileWriter writer = new FileWriter(recordName + ".j");
        // writer.write(recordJasminCode.get(recordName).toString());
        // writer.close();
        // } catch (IOException e) {
        // throw new RuntimeException("Ошибка при создании файла для записи: " +
        // e.getMessage());
        // }

        // currentRecord = null;
    }

}
