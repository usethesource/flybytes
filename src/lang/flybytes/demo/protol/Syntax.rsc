module lang::flybytes::demo::protol::Syntax

// PROTOL is a demo language, featuring object-oriented language features
// and a dynamic type system based on object and array prototypes with the missing_method
// and missing_field features. There are no classes on protol, just objects used as templates
// for other objects.

syntax Program = Command* commands;

syntax Command 
  = assign: Assignable assign "=" Expr val ";"
  | \if: "if" "(" Exp cond ")"  "{" Command* thenPart "}" "else" "{" Command* elsePart "}"
  | \for: "while" "(" Exp cond ")" "{" Command* body "}" 
  | exp: Exp e ";"
  | \return: "return" Exp e ";" 
  | print: "print" Exp e ";"
  ;

syntax Assignable
  = var: Id name
  | field: Expr obj "." Id name
  | array: Expr array "[" Expr index "]"
  ;
    
syntax Expr
  = send: Expr receiver "." Id name "(" {Expr ","}* args ")"
  | array: "{" {Expr ","}* "}"
  | new: "new" Expr prototype?
  | \extend: "new" Expr prototype? "{" Definition+ definitions "}" 

  | bracket "(" Expr ")"
  | ref: Id id
  | \int: Int
  | \str: String
  | field: Expr receiver "." Id name
  > array: Expr array "[" Expr index "]"
  > mul: Expr "*" Expr
  > left ( Expr "+" Expr | Expr "-" Expr )
  > right( Expr "==" Expr | Expr "!=" Expr)
  > right (Expr "\<=" Expr | Expr "\<" Expr | Expr "\>" Expr | Expr "\>=" Expr)
  ; 

syntax Definition
  = field:Id name "=" Expr val
  | method:Id name "(" {Id ","}* args ")" "{" Command* commands "}"
  | "missing_method" "(" Id arg ")" "{" Command* commands "}"
  | "missing_field" "(" Id arg ")" "{" Command* commands "}"
  ;  
  
lexical Id = [A-Za-z][a-zA-Z0-9]+ \ "if" \ "new" \ "else" \ "while"  \ "return" \ "object";  
lexical Int = [0-9]+;
lexical String = "\"" ![\"]* "\"";

layout WS = [\t\n\r\ ]*;