
module lang::flybytes::demo::func::Syntax

lexical Ident =  [a-zA-Z][a-zA-Z0-9]* !>> [a-zA-Z0-9];

lexical Natural = [0-9]+ !>> [0-9];

lexical LAYOUT = [\t-\n\r\ ];

layout LAYOUTLIST = LAYOUT*  !>> [\t-\n\r\ ] ;

start syntax Prog = prog: Func* ;

syntax Func = func: Ident name "(" {Ident ","}* ")" "=" FExp;

syntax FExp = let: "let" {Binding ","}* "in" FExp "end"
            | cond: "if" FExp "then" FExp "else" FExp "end"
            | bracket "(" FExp ")"
            | var: Ident
            | nat: Natural 
            | call: Ident "(" {FExp ","}* ")"
            > non-assoc (
               left mul: FExp "*" FExp 
             | non-assoc div: FExp "/" FExp
            ) 
            > left (
               left add: FExp "+" FExp 
             | left sub: FExp "-" FExp
            )
            >
            non-assoc (
               non-assoc gt: FExp "\>" FExp
             | non-assoc lt:  FExp "\<" FExp
             | non-assoc geq:  FExp "\>=" FExp
             | non-assoc leq:  FExp "\<=" FExp
            )
            >
            right assign: Ident ":=" FExp
            >
            right seq: FExp ";" FExp; 

syntax Binding = binding: Ident "=" FExp;
