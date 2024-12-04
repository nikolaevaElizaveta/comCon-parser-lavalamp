grammar ImperativeLang;

program: (simpleDeclaration | routineDeclaration)*;

simpleDeclaration
    : variableDeclaration
    | typeDeclaration
    ;

variableDeclaration
    : 'var' IDENTIFIER ':' type ('is' expression)?
    | 'var' IDENTIFIER 'is' expression
    ;

typeDeclaration
    : 'type' IDENTIFIER 'is' type
    ;

type
    : primitiveType
    | userType
    | IDENTIFIER
    ;

primitiveType
    : 'integer'
    | 'real'
    | 'boolean'
    ;

userType
    : arrayType
    | recordType
    ;

arrayType
    : 'array' '[' expression? ']' type
    ;

recordType
    : 'record' '{' variableDeclaration* '}' 'end'
    ;

statement
    : assignment
    | routineCall
    | whileLoop
    | forLoop
    | ifStatement
    | returnStatement
    ;

returnStatement
    : 'return' expression?
    ;

assignment
    : modifiablePrimary ':=' expression
    ;

routineCall
    : IDENTIFIER '(' expressionList? ')'
    ;

expressionList
    : expression (',' expression)*
    ;

whileLoop
    : 'while' expression 'loop' body 'end'
    ;

forLoop
    : 'for' IDENTIFIER 'in' ('reverse')? range 'loop' body 'end'
    ;

ifStatement
    : 'if' expression 'then' body ('else' body)? 'end'
    ;

routineDeclaration
    : 'routine' IDENTIFIER '(' parameters? ')' (':' type)? 'is' body 'end'
    ;

parameters
    : parameterDeclaration (',' parameterDeclaration)*
    ;

parameterDeclaration
    : IDENTIFIER ':' type
    ;

body
    : (simpleDeclaration | statement)*
    ;

expression
    : relation (('and' | 'or' | 'xor') relation)*
    ;

relation
    : simple (('<' | '<=' | '>' | '>=' | '=' | '/=') simple)?
    ;

simple
    : factor (('*' | '/' | '%') factor)*
    ;

factor
    : summand (('+' | '-') summand)*
    ;

summand
    : primary
    | '(' expression ')'
    ;

primary
    : INTEGER_LITERAL
    | REAL_LITERAL
    | 'true'
    | 'false'
    | modifiablePrimary
    | routineCall
    ;

modifiablePrimary
    : IDENTIFIER ('.' IDENTIFIER | '[' expression ']')*
    ;

range
    : expression '..' expression
    ;

INTEGER_LITERAL : [0-9]+;
REAL_LITERAL    : [0-9]+'.'[0-9]+;
IDENTIFIER      : [a-zA-Z_][a-zA-Z_0-9]*;
WS              : [ \t\r\n]+ -> skip;
COMMENT         : '//' ~[\r\n]* -> skip;
