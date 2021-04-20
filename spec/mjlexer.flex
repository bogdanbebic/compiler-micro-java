
package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%class Lexer
%unicode
%cup
%line
%column

%{
    private Symbol symbol(int type) {
        return new Symbol(type, yyline + 1, yycolumn);
    }
    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline + 1, yycolumn, value);
    }
%}

LineTerminator = \r|\n|\r\n
WhiteSpace     = {LineTerminator} | [ \b\t\f]

Identifier = [a-zA-Z] [a-zA-Z0-9_]*

PrintableChar = [^\r\n\'\\]

DecIntegerLiteral = 0 | [1-9][0-9]*
CharLiteral       = \' {PrintableChar} \'
BoolLiteral       = true | false

LineComment = "//" [^\r\n]* {LineTerminator}?

Comment = {LineComment}

%%

/* keywords */
program     { return symbol(sym.PROGRAM); }
break       { return symbol(sym.BREAK); }
class       { return symbol(sym.CLASS); }
enum        { return symbol(sym.ENUM); }
else        { return symbol(sym.ELSE); }
const       { return symbol(sym.CONST); }
if          { return symbol(sym.IF); }
switch      { return symbol(sym.SWITCH); }
do          { return symbol(sym.DO); }
while       { return symbol(sym.WHILE); }
new         { return symbol(sym.NEW); }
print       { return symbol(sym.PRINT); }
read        { return symbol(sym.READ); }
return      { return symbol(sym.RETURN); }
void        { return symbol(sym.VOID); }
extends     { return symbol(sym.EXTENDS); }
continue    { return symbol(sym.CONTINUE); }
case        { return symbol(sym.CASE); }

/* literals */
{DecIntegerLiteral} { return symbol(sym.NUMBER, Integer.parseInt(yytext())); }
{CharLiteral}       { return symbol(sym.CHAR, yytext().charAt(1)); }
{BoolLiteral}       { return symbol(sym.BOOL, Boolean.parseBoolean(yytext())); }

/* identifiers */
{Identifier} { return symbol(sym.IDENT, yytext()); }

/* operators */
"+"     { return symbol(sym.PLUS); }
"-"     { return symbol(sym.MINUS); }
"*"     { return symbol(sym.MULT); }
"/"     { return symbol(sym.DIV); }
"%"     { return symbol(sym.MOD); }
"=="    { return symbol(sym.EQ); }
"!="    { return symbol(sym.NE); }
">"     { return symbol(sym.GT); }
">="    { return symbol(sym.GE); }
"<"     { return symbol(sym.LT); }
"<="    { return symbol(sym.LE); }
"&&"    { return symbol(sym.LAND); }
"||"    { return symbol(sym.LOR); }
"="     { return symbol(sym.ASSIGN); }
"++"    { return symbol(sym.INC); }
"--"    { return symbol(sym.DEC); }
";"     { return symbol(sym.SEMI); }
","     { return symbol(sym.COMMA); }
"."     { return symbol(sym.DOT); }
"("     { return symbol(sym.LPAREN); }
")"     { return symbol(sym.RPAREN); }
"["     { return symbol(sym.LBRACKET); }
"]"     { return symbol(sym.RBRACKET); }
"{"     { return symbol(sym.LBRACE); }
"}"     { return symbol(sym.RBRACE); }
"?"     { return symbol(sym.QUESTION); }
":"     { return symbol(sym.COLON); }

/* comments */
{Comment} { /* ignore */ }

/* whitespace */
{WhiteSpace} { /* ignore */ }

/* error fallback */
. { System.err.println("Lexical error (" + yytext() + ") at line " + (yyline + 1)); }
