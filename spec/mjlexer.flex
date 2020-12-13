
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

Identifier = [a-z|A-Z] [a-z|A-Z|0-9|_]*

PrintableChar = [^\r\n\'\\]

DecIntegerLiteral = 0 | [1-9][0-9]*
CharLiteral       = \' {PrintableChar} \'
BoolLiteral       = true | false

LineComment = "//" [^\r\n]* {LineTerminator}?

Comment = {LineComment}

%%

/* keywords */
program     { return symbol(sym.PROGRAM, yytext()); }
break       { return symbol(sym.BREAK, yytext()); }
class       { return symbol(sym.CLASS, yytext()); }
enum        { return symbol(sym.ENUM, yytext()); }
else        { return symbol(sym.ELSE, yytext()); }
const       { return symbol(sym.CONST, yytext()); }
if          { return symbol(sym.IF, yytext()); }
switch      { return symbol(sym.SWITCH, yytext()); }
do          { return symbol(sym.DO, yytext()); }
while       { return symbol(sym.WHILE, yytext()); }
new         { return symbol(sym.NEW, yytext()); }
print       { return symbol(sym.PRINT, yytext()); }
read        { return symbol(sym.READ, yytext()); }
return      { return symbol(sym.RETURN, yytext()); }
void        { return symbol(sym.VOID, yytext()); }
extends     { return symbol(sym.EXTENDS, yytext()); }
continue    { return symbol(sym.CONTINUE, yytext()); }
case        { return symbol(sym.CASE, yytext()); }


/* identifiers */
{Identifier} { return symbol(sym.IDENT, yytext()); }

/* literals */
{DecIntegerLiteral} { return symbol(sym.NUMBER, new Integer(yytext())); }
{CharLiteral}       { return symbol(sym.CHAR, yytext().charAt(1)); }
{BoolLiteral}       { return symbol(sym.BOOL, new Boolean(yytext())); }

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
