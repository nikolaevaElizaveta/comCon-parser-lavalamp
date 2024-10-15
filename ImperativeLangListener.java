// Generated from ImperativeLang.g4 by ANTLR 4.13.0
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ImperativeLangParser}.
 */
public interface ImperativeLangListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(ImperativeLangParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(ImperativeLangParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#simpleDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterSimpleDeclaration(ImperativeLangParser.SimpleDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#simpleDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitSimpleDeclaration(ImperativeLangParser.SimpleDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#variableDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#variableDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclaration(ImperativeLangParser.VariableDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#typeDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterTypeDeclaration(ImperativeLangParser.TypeDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#typeDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitTypeDeclaration(ImperativeLangParser.TypeDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(ImperativeLangParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(ImperativeLangParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#primitiveType}.
	 * @param ctx the parse tree
	 */
	void enterPrimitiveType(ImperativeLangParser.PrimitiveTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#primitiveType}.
	 * @param ctx the parse tree
	 */
	void exitPrimitiveType(ImperativeLangParser.PrimitiveTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#userType}.
	 * @param ctx the parse tree
	 */
	void enterUserType(ImperativeLangParser.UserTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#userType}.
	 * @param ctx the parse tree
	 */
	void exitUserType(ImperativeLangParser.UserTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#arrayType}.
	 * @param ctx the parse tree
	 */
	void enterArrayType(ImperativeLangParser.ArrayTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#arrayType}.
	 * @param ctx the parse tree
	 */
	void exitArrayType(ImperativeLangParser.ArrayTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#recordType}.
	 * @param ctx the parse tree
	 */
	void enterRecordType(ImperativeLangParser.RecordTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#recordType}.
	 * @param ctx the parse tree
	 */
	void exitRecordType(ImperativeLangParser.RecordTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(ImperativeLangParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(ImperativeLangParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#assignment}.
	 * @param ctx the parse tree
	 */
	void enterAssignment(ImperativeLangParser.AssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#assignment}.
	 * @param ctx the parse tree
	 */
	void exitAssignment(ImperativeLangParser.AssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#routineCall}.
	 * @param ctx the parse tree
	 */
	void enterRoutineCall(ImperativeLangParser.RoutineCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#routineCall}.
	 * @param ctx the parse tree
	 */
	void exitRoutineCall(ImperativeLangParser.RoutineCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#expressionList}.
	 * @param ctx the parse tree
	 */
	void enterExpressionList(ImperativeLangParser.ExpressionListContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#expressionList}.
	 * @param ctx the parse tree
	 */
	void exitExpressionList(ImperativeLangParser.ExpressionListContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#whileLoop}.
	 * @param ctx the parse tree
	 */
	void enterWhileLoop(ImperativeLangParser.WhileLoopContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#whileLoop}.
	 * @param ctx the parse tree
	 */
	void exitWhileLoop(ImperativeLangParser.WhileLoopContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#forLoop}.
	 * @param ctx the parse tree
	 */
	void enterForLoop(ImperativeLangParser.ForLoopContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#forLoop}.
	 * @param ctx the parse tree
	 */
	void exitForLoop(ImperativeLangParser.ForLoopContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void enterIfStatement(ImperativeLangParser.IfStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void exitIfStatement(ImperativeLangParser.IfStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#routineDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterRoutineDeclaration(ImperativeLangParser.RoutineDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#routineDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitRoutineDeclaration(ImperativeLangParser.RoutineDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#parameters}.
	 * @param ctx the parse tree
	 */
	void enterParameters(ImperativeLangParser.ParametersContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#parameters}.
	 * @param ctx the parse tree
	 */
	void exitParameters(ImperativeLangParser.ParametersContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#parameterDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterParameterDeclaration(ImperativeLangParser.ParameterDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#parameterDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitParameterDeclaration(ImperativeLangParser.ParameterDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#body}.
	 * @param ctx the parse tree
	 */
	void enterBody(ImperativeLangParser.BodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#body}.
	 * @param ctx the parse tree
	 */
	void exitBody(ImperativeLangParser.BodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(ImperativeLangParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(ImperativeLangParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#relation}.
	 * @param ctx the parse tree
	 */
	void enterRelation(ImperativeLangParser.RelationContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#relation}.
	 * @param ctx the parse tree
	 */
	void exitRelation(ImperativeLangParser.RelationContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#simple}.
	 * @param ctx the parse tree
	 */
	void enterSimple(ImperativeLangParser.SimpleContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#simple}.
	 * @param ctx the parse tree
	 */
	void exitSimple(ImperativeLangParser.SimpleContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#factor}.
	 * @param ctx the parse tree
	 */
	void enterFactor(ImperativeLangParser.FactorContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#factor}.
	 * @param ctx the parse tree
	 */
	void exitFactor(ImperativeLangParser.FactorContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#summand}.
	 * @param ctx the parse tree
	 */
	void enterSummand(ImperativeLangParser.SummandContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#summand}.
	 * @param ctx the parse tree
	 */
	void exitSummand(ImperativeLangParser.SummandContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#primary}.
	 * @param ctx the parse tree
	 */
	void enterPrimary(ImperativeLangParser.PrimaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#primary}.
	 * @param ctx the parse tree
	 */
	void exitPrimary(ImperativeLangParser.PrimaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#modifiablePrimary}.
	 * @param ctx the parse tree
	 */
	void enterModifiablePrimary(ImperativeLangParser.ModifiablePrimaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#modifiablePrimary}.
	 * @param ctx the parse tree
	 */
	void exitModifiablePrimary(ImperativeLangParser.ModifiablePrimaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link ImperativeLangParser#range}.
	 * @param ctx the parse tree
	 */
	void enterRange(ImperativeLangParser.RangeContext ctx);
	/**
	 * Exit a parse tree produced by {@link ImperativeLangParser#range}.
	 * @param ctx the parse tree
	 */
	void exitRange(ImperativeLangParser.RangeContext ctx);
}