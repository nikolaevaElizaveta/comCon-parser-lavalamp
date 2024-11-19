import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class InterpreterVisitor extends ImperativeLangBaseListener {
    private ParseTreeProperty<Object> values = new ParseTreeProperty<>();
    private Map<String, Object> memory = new HashMap<>();

    // Class to hold array information
    private static class ArrayInfo {
        int size;
        Object elementType;

        ArrayInfo(int size, Object elementType) {
            this.size = size;
            this.elementType = elementType;
        }
    }

    // Helper method to get the value associated with a parse tree node
    private Object getValue(ParserRuleContext ctx) {
        return values.get(ctx);
    }

    // Helper method to set the value associated with a parse tree node
    private void setValue(ParserRuleContext ctx, Object value) {
        values.put(ctx, value);
    }

    // Helper method to extract variable names
    private String getVariableName(ImperativeLangParser.ModifiablePrimaryContext ctx) {
        return ctx.IDENTIFIER(0).getText();
    }

    // Get default value for a given type
    private Object getDefaultValueForType(Object type) {
        return switch (type.toString()) {
            case "integer" -> 0;
            case "real" -> 0.0;
            case "boolean" -> false;
            default -> throw new RuntimeException("Unknown type: " + type);
        };
    }

    // Process the type and return type information

    private Object processType(ImperativeLangParser.TypeContext typeCtx) {
        if (typeCtx.primitiveType() != null) {
            return typeCtx.primitiveType().getText();
        } else if (typeCtx.userType() != null) {
            ImperativeLangParser.UserTypeContext userTypeCtx = typeCtx.userType();
            if (userTypeCtx.arrayType() != null) {
                return processArrayType(userTypeCtx.arrayType());
            } else if (userTypeCtx.recordType() != null) {
                // Handle record type if needed
                throw new RuntimeException("Record types are not supported yet.");
            }
        } else if (typeCtx.IDENTIFIER() != null) {
            // Handle user-defined types (type aliases)
            String typeName = typeCtx.IDENTIFIER().getText();
            // You might need to implement type aliasing if needed
            throw new RuntimeException("Type aliases are not supported yet: " + typeName);
        }
        throw new RuntimeException("Unsupported type");
    }


    private ArrayInfo processArrayType(ImperativeLangParser.ArrayTypeContext arrayTypeCtx) {
        // Evaluate the array size expression
        ImperativeLangParser.ExpressionContext sizeExpr = arrayTypeCtx.expression();
        if (sizeExpr == null) {
            throw new RuntimeException("Array size must be specified");
        }
        // Manually walk the size expression if needed
        if (getValue(sizeExpr) == null) {
            ParseTreeWalker.DEFAULT.walk(this, sizeExpr);
        }
        Object sizeValue = getValue(sizeExpr);
        if (!(sizeValue instanceof Number)) {
            throw new RuntimeException("Array size must be a number");
        }
        int size = ((Number) sizeValue).intValue();

        // Process the element type
        Object elementType = processType(arrayTypeCtx.type());

        return new ArrayInfo(size, elementType);
    }


    // Execute a body (list of statements and declarations)
    private void executeBody(ImperativeLangParser.BodyContext ctx) {
        for (ParseTree child : ctx.children) {
            if (child instanceof ImperativeLangParser.SimpleDeclarationContext) {
                // Listener will handle this
            } else if (child instanceof ImperativeLangParser.StatementContext) {
                // Listener will handle this
            }
        }
    }

    @Override
    public void enterProgram(ImperativeLangParser.ProgramContext ctx) {
        // Execute the main program
        for (ImperativeLangParser.RoutineDeclarationContext routineCtx : ctx.routineDeclaration()) {
            if (routineCtx.IDENTIFIER().getText().equals("Main")) {
                executeRoutine(routineCtx);
            }
        }
    }

    // Execute a routine (function/procedure)
    private void executeRoutine(ImperativeLangParser.RoutineDeclarationContext ctx) {
        ParseTreeWalker.DEFAULT.walk(this, ctx);
    }

    @Override
    public void exitVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx) {
        String varName = ctx.IDENTIFIER().getText();
        Object value = null;

        // Check if the variable has a type
        if (ctx.type() != null) {
            // Process the type
            Object typeInfo = processType(ctx.type());
            if (typeInfo instanceof ArrayInfo arrayInfo) {
                Object[] array = new Object[arrayInfo.size];
                // Initialize array elements with default values
                for (int i = 0; i < arrayInfo.size; i++) {
                    array[i] = getDefaultValueForType(arrayInfo.elementType);
                }
                memory.put(varName, array);
            } else {
                // For primitive types
                if (ctx.expression() != null) {
                    value = getValue(ctx.expression());
                } else {
                    value = getDefaultValueForType(typeInfo);
                }
                memory.put(varName, value);
            }
        } else {
            // If no type is specified, default handling
            if (ctx.expression() != null) {
                value = getValue(ctx.expression());
            }
            memory.put(varName, value);
        }
    }

    @Override
    public void exitAssignment(ImperativeLangParser.AssignmentContext ctx) {
        Object value = getValue(ctx.expression());
        assignToModifiablePrimary(ctx.modifiablePrimary(), value);
    }

    // Helper method to assign a value to a modifiable primary
    private void assignToModifiablePrimary(ImperativeLangParser.ModifiablePrimaryContext ctx, Object value) {
        String varName = ctx.IDENTIFIER(0).getText();
        Object varValue = memory.get(varName);
        if (varValue == null) {
            throw new RuntimeException("Variable " + varName + " not defined");
        }

        Object currentObject = varValue;
        int childIndex = 1;
        while (childIndex < ctx.getChildCount()) {
            if (ctx.getChild(childIndex).getText().equals("[")) {
                // Array indexing
                ImperativeLangParser.ExpressionContext indexCtx = (ImperativeLangParser.ExpressionContext) ctx.getChild(childIndex + 1);
                if (getValue(indexCtx) == null) {
                    ParseTreeWalker.DEFAULT.walk(this, indexCtx);
                }
                Object indexValue = getValue(indexCtx);
                if (!(indexValue instanceof Number)) {
                    throw new RuntimeException("Array index must be a number");
                }
                int index = ((Number) indexValue).intValue();

                if (!(currentObject instanceof Object[] array)) {
                    throw new RuntimeException("Variable is not an array");
                }

                if (index < 0 || index >= array.length) {
                    throw new RuntimeException("Array index out of bounds: " + index);
                }

                if (childIndex + 3 >= ctx.getChildCount()) {
                    // Last index, perform assignment
                    array[index] = value;
                    return;
                } else {
                    currentObject = array[index];
                    childIndex += 3; // Skip '[', expression, ']'
                }
            } else {
                childIndex++;
            }
        }

        // If we reach here, it's a simple variable assignment
        memory.put(varName, value);
    }

    @Override
    public void exitModifiablePrimary(ImperativeLangParser.ModifiablePrimaryContext ctx) {
        Object value = resolveModifiablePrimary(ctx);
        setValue(ctx, value);
    }

    // Helper method to resolve modifiable primary
    private Object resolveModifiablePrimary(ImperativeLangParser.ModifiablePrimaryContext ctx) {
        // Start with the base identifier
        String varName = ctx.IDENTIFIER(0).getText();
        Object value = memory.get(varName);
        if (value == null) {
            throw new RuntimeException("Variable " + varName + " not defined");
        }

        Object currentObject = value;
        int childIndex = 1;
        while (childIndex < ctx.getChildCount()) {
            if (ctx.getChild(childIndex).getText().equals("[")) {
                // Array indexing
                ImperativeLangParser.ExpressionContext indexCtx = (ImperativeLangParser.ExpressionContext) ctx.getChild(childIndex + 1);
                if (getValue(indexCtx) == null) {
                    ParseTreeWalker.DEFAULT.walk(this, indexCtx);
                }
                Object indexValue = getValue(indexCtx);
                if (!(indexValue instanceof Number)) {
                    throw new RuntimeException("Array index must be a number");
                }
                int index = ((Number) indexValue).intValue();

                if (!(currentObject instanceof Object[] array)) {
                    throw new RuntimeException("Variable is not an array");
                }

                if (index < 0 || index >= array.length) {
                    throw new RuntimeException("Array index out of bounds: " + index);
                }

                currentObject = array[index];
                childIndex += 3; // Skip '[', expression, ']'
            } else {
                childIndex++;
            }
        }
        return currentObject;
    }

    // Now, implement the other exit methods without manually calling other exit methods

    @Override
    public void exitExpression(ImperativeLangParser.ExpressionContext ctx) {
        if (ctx.relation().size() == 1) {
            Object value = getValue(ctx.relation(0));
            setValue(ctx, value);
        } else {
            Object left = getValue(ctx.relation(0));
            for (int i = 1; i < ctx.relation().size(); i++) {
                String op = ctx.getChild(2 * i - 1).getText();
                Object right = getValue(ctx.relation(i));
                switch (op) {
                    case "and" -> left = ((Boolean) left) && ((Boolean) right);
                    case "or" -> left = ((Boolean) left) || ((Boolean) right);
                    case "xor" -> left = ((Boolean) left) ^ ((Boolean) right);
                }
            }
            setValue(ctx, left);
        }
    }

    @Override
    public void exitRelation(ImperativeLangParser.RelationContext ctx) {
        if (ctx.simple().size() == 1) {
            Object value = getValue(ctx.simple(0));
            setValue(ctx, value);
        } else {
            Object left = getValue(ctx.simple(0));
            String op = ctx.getChild(1).getText();
            Object right = getValue(ctx.simple(1));
            boolean result = false;
            if (left instanceof Number && right instanceof Number) {
                double l = ((Number) left).doubleValue();
                double r = ((Number) right).doubleValue();
                switch (op) {
                    case "<" -> result = l < r;
                    case "<=" -> result = l <= r;
                    case ">" -> result = l > r;
                    case ">=" -> result = l >= r;
                    case "=" -> result = l == r;
                    case "/=" -> result = l != r;
                }
            } else {
                throw new RuntimeException("Invalid operands for comparison");
            }
            setValue(ctx, result);
        }
    }

    @Override
    public void exitSimple(ImperativeLangParser.SimpleContext ctx) {
        if (ctx.factor().size() == 1) {
            Object value = getValue(ctx.factor(0));
            setValue(ctx, value);
        } else {
            Object result = getValue(ctx.factor(0));
            for (int i = 1; i < ctx.factor().size(); i++) {
                String op = ctx.getChild(2 * i - 1).getText();
                Object right = getValue(ctx.factor(i));
                if (op.equals("*")) {
                    result = ((Number) result).doubleValue() * ((Number) right).doubleValue();
                } else if (op.equals("/")) {
                    result = ((Number) result).doubleValue() / ((Number) right).doubleValue();
                } else if (op.equals("%")) {
                    result = ((Number) result).intValue() % ((Number) right).intValue();
                }
            }
            setValue(ctx, result);
        }
    }

    @Override
    public void exitFactor(ImperativeLangParser.FactorContext ctx) {
        if (ctx.summand().size() == 1) {
            Object value = getValue(ctx.summand(0));
            setValue(ctx, value);
        } else {
            Object result = getValue(ctx.summand(0));
            for (int i = 1; i < ctx.summand().size(); i++) {
                String op = ctx.getChild(2 * i - 1).getText();
                Object right = getValue(ctx.summand(i));
                if (op.equals("+")) {
                    result = ((Number) result).doubleValue() + ((Number) right).doubleValue();
                } else if (op.equals("-")) {
                    result = ((Number) result).doubleValue() - ((Number) right).doubleValue();
                }
            }
            setValue(ctx, result);
        }
    }

    @Override
    public void exitSummand(ImperativeLangParser.SummandContext ctx) {
        if (ctx.primary() != null) {
            Object value = getValue(ctx.primary());
            setValue(ctx, value);
        } else if (ctx.expression() != null) {
            Object value = getValue(ctx.expression());
            setValue(ctx, value);
        }
    }

    @Override
    public void exitPrimary(ImperativeLangParser.PrimaryContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            int value = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
            setValue(ctx, value);
        } else if (ctx.REAL_LITERAL() != null) {
            double value = Double.parseDouble(ctx.REAL_LITERAL().getText());
            setValue(ctx, value);
        } else if (ctx.getText().equals("true")) {
            setValue(ctx, true);
        } else if (ctx.getText().equals("false")) {
            setValue(ctx, false);
        } else if (ctx.modifiablePrimary() != null) {
            Object value = getValue(ctx.modifiablePrimary());
            setValue(ctx, value);
        } else if (ctx.routineCall() != null) {
            Object value = getValue(ctx.routineCall());
            setValue(ctx, value);
        }
    }

    @Override
    public void exitRoutineCall(ImperativeLangParser.RoutineCallContext ctx) {
        String funcName = ctx.IDENTIFIER().getText();
        List<Object> args = new ArrayList<>();
        if (ctx.expressionList() != null) {
            for (ImperativeLangParser.ExpressionContext exprCtx : ctx.expressionList().expression()) {
                Object argValue = getValue(exprCtx);
                args.add(argValue);
            }
        }
        if (funcName.equals("print")) {
            for (Object arg : args) {
                System.out.println(arg);
            }
            setValue(ctx, null);
        } else {
            throw new RuntimeException("Undefined routine: " + funcName);
        }
    }

    @Override
    public void exitIfStatement(ImperativeLangParser.IfStatementContext ctx) {
        Object conditionValue = getValue(ctx.expression());
        if (!(conditionValue instanceof Boolean)) {
            throw new RuntimeException("Condition in if statement is not a boolean");
        }
        boolean condition = (Boolean) conditionValue;
        if (condition) {
            ParseTreeWalker.DEFAULT.walk(this, ctx.body(0));
        } else if (ctx.body(1) != null) {
            ParseTreeWalker.DEFAULT.walk(this, ctx.body(1));
        }
    }

    @Override
    public void exitWhileLoop(ImperativeLangParser.WhileLoopContext ctx) {
        // While loop control
        while (true) {
            // Evaluate the condition
            // Walk the expression subtree to evaluate it
            ParseTreeWalker.DEFAULT.walk(this, ctx.expression());
            Object conditionValue = getValue(ctx.expression());
            if (!(conditionValue instanceof Boolean)) {
                throw new RuntimeException("Condition in while loop is not a boolean");
            }
            boolean condition = (Boolean) conditionValue;
            if (!condition) {
                break;
            }
            // Execute the loop body
            // Walk the body subtree to execute statements and declarations
            ParseTreeWalker.DEFAULT.walk(this, ctx.body());
        }
    }


    @Override
    public void exitForLoop(ImperativeLangParser.ForLoopContext ctx) {
        String varName = ctx.IDENTIFIER().getText();
        Object startValue = getValue(ctx.range().expression(0));
        Object endValue = getValue(ctx.range().expression(1));

        if (!(startValue instanceof Number) || !(endValue instanceof Number)) {
            throw new RuntimeException("Range expressions must be numeric");
        }

        int start = ((Number) startValue).intValue();
        int end = ((Number) endValue).intValue();

        boolean reverse = ctx.getText().contains("reverse");

        if (!reverse) {
            for (int i = start; i <= end; i++) {
                memory.put(varName, i);
                ParseTreeWalker.DEFAULT.walk(this, ctx.body());
            }
        } else {
            for (int i = start; i >= end; i--) {
                memory.put(varName, i);
                ParseTreeWalker.DEFAULT.walk(this, ctx.body());
            }
        }
    }
}