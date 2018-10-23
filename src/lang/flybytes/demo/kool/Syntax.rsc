module lang::flybytes::demo::kool::Syntax


syntax Exp =
  new: "new" Name \ NameKeywords "(" {Exp ","}* ")" 
  | newNoParams: "new" Name \ NameKeywords 
  ;

keyword NameKeywords =
  "new" 
  ;

syntax Name =
  LEX_Name 
  ;


keyword NameKeywords =
  "is" 
  | "end" 
  | "method" 
  ;

syntax Name =
  LEX_Name 
  ;

syntax Method =
  methodWParamsDef: "method" Name \ NameKeywords "(" {AttributedName ","}+ ")" "is" Decl* Stmt "end" 
  | methodDef: "method" Name \ NameKeywords "is" Decl* Stmt "end" 
  ;


syntax Exp =
  vector: "(#" {Exp ","}+ "#)" 
  ;

syntax Name =
  LEX_Name 
  ;



syntax Exp =
  self: "self" 
  ;

keyword NameKeywords =
  "self" 
  ;

syntax Name =
  LEX_Name 
  ;


syntax Stmt =
  right seqComp: Stmt Stmt 
  ;

syntax Name =
  LEX_Name 
  ;



keyword NameKeywords =
  "throw" 
  | "end" 
  | "try" 
  | "catch" 
  ;

syntax Stmt =
  \throw: "throw" Exp ";" 
  | tryCatch: "try" Stmt "catch" Name \ NameKeywords Stmt "end" 
  ;

syntax Name =
  LEX_Name 
  ;


lexical String =
  "\"" ![\n \a0D \" \\]* "\"" 
  ;

syntax Name =
  LEX_Name 
  ;


lexical Slash =
  [/] 
  ;

layout LAYOUTLIST  =
  LAYOUT* !>> [\t-\n \a0D \ ] 
  ;

syntax Name =
  LEX_Name 
  ;

lexical LAYOUT =
  "//" ![\n]* [\n] 
  | BlockComment 
  ;

lexical BlockComment =
  "/*" CommentPart* "*/" 
  ;

lexical Asterisk =
  [*] 
  ;

lexical CommentPart =
  Asterisk !>> [/] 
  | ![* /] 
  | BlockComment 
  | Slash !>> [*] 
  ;


lexical Char =
  "\'" [0-9 A-Z a-z] "\'" 
  ;

syntax Name =
  LEX_Name 
  ;

syntax Class =
  class: "class" Name \ NameKeywords "extends" Name \ NameKeywords "is" Decl* Method* "end" 
  | primClassNoParent: "primclass" Name \ NameKeywords "is" Decl* Method* "end" 
  | classNoParent: "class" Name \ NameKeywords "is" Decl* Method* "end" 
  | primClass: "primclass" Name \ NameKeywords "extends" Name \ NameKeywords "is" Decl* Method* "end" 
  ;

keyword NameKeywords =
  "extends" 
  | "end" 
  | "is" 
  | "class" 
  | "primclass" 
  ;

syntax Name =
  LEX_Name 
  ;


syntax Name =
  LEX_Name 
  ;


syntax Exp =
  superSame: "super" "(" {Exp ","}+ ")" 
  | superUnarySame: "super" "(" ")" 
  | super: "super" "." Name \ NameKeywords "(" {Exp ","}+ ")" 
  | superUnary: "super" "." Name \ NameKeywords (  "("    ")"  )? 
  ;

keyword NameKeywords =
  "super" 
  ;

syntax Name =
  LEX_Name 
  ;


keyword OperatorNameKeywords =
  "@" 
  ;

syntax Attribute =
  attribNoParams: "@" Name \ NameKeywords 
  | attribWParams: "@" Name \ NameKeywords "(" {Exp ","}+ ")" 
  ;

syntax Name =
  LEX_Name 
  ;


keyword OperatorNameKeywords =
  "/=" 
  | "==" 
  ;

syntax Exp =
  refNeq: Exp "/=" Exp 
  | string: String 
  | \bool: Bool 
  | integer: Integer 
  | refEq: Exp "==" Exp 
  | nil: "nil" 
  | bracket "(" Exp ")" 
  | name: Name \ NameKeywords 
  | char: Char 
  | float: Float 
  ;

keyword NameKeywords =
  "nil" 
  ;

syntax Name =
  LEX_Name 
  ;


syntax Name =
  LEX_Name 
  ;

syntax Program =
  program: Class* Exp 
  ;

keyword NameKeywords =
  "continue" 
  | "do" 
  | "for" 
  | "break" 
  | "od" 
  | "to" 
  | "while" 
  ;

syntax Stmt =
  whileLoop: "while" Exp "do" Stmt "od" 
  | doLoop: "do" Stmt "while" Exp "od" 
  | \break: "break" ";" 
  | forLoop: "for" Name \ NameKeywords "\<-" Exp "to" Exp "do" Stmt "od" 
  | \continue: "continue" ";" 
  ;

syntax Name =
  LEX_Name 
  ;


keyword NameKeywords =
  "if" 
  | "fi" 
  | "else" 
  | "then" 
  ;

syntax Stmt =
  ifThenElse: "if" Exp "then" Stmt "else" Stmt "fi" 
  | ifThen: "if" Exp "then" Stmt "fi" 
  ;

syntax Name =
  LEX_Name 
  ;

syntax Exp =
  prim: "primInvoke" "(" {Exp ","}+ ")" 
  ;

keyword NameKeywords =
  "primInvoke" 
  ;

syntax Name =
  LEX_Name 
  ;


keyword NameKeywords =
  "assert" 
  ;

syntax Stmt =
  \assert: "assert" Exp ";" 
  ;

syntax Name =
  LEX_Name 
  ;



syntax Exp =
  left send: Exp "." Name \ NameKeywords "(" {Exp ","}+ ")" 
  | left unarySend: Exp "." Name \ NameKeywords (  "("    ")"  )? 
  | left binOpSend: Exp OperatorName \ OperatorNameKeywords Exp 
  ;

syntax Exp =
  left 
    ( left unarySend: Exp "." Name \ NameKeywords (  "("    ")"  )? 
    | left send: Exp "." Name \ NameKeywords "(" {Exp ","}+ ")" 
    )
  > left binOpSend: Exp OperatorName \ OperatorNameKeywords Exp 
  ;

keyword NameKeywords =
  "return" 
  ;

syntax Stmt =
  returnNoVal: "return" ";" 
  | \return: "return" Exp ";" 
  ;

syntax Name =
  LEX_Name 
  ;


keyword NameKeywords =
  "skip" 
  ;

syntax Stmt =
  skip: "skip" ";" 
  | labelStmt: Name \ NameKeywords ":" 
  | stmtExp: Exp ";" 
  ;

syntax Name =
  LEX_Name 
  ;



lexical Float =
  [+ \-]? [0-9]+ "." 
  | [+ \-]? "." [0-9]+ 
  | [+ \-]? [0-9]+ "." [0-9]+ 
  ;

lexical Integer =
  [+ \-]? [0-9]+ 
  ;

syntax Name =
  LEX_Name 
  ;



keyword NameKeywords =
  "spawn" 
  | "release" 
  | "acquire" 
  ;

syntax Stmt =
  release: "release" Exp ";" 
  | spawn: "spawn" Exp ";" 
  | acquire: "acquire" Exp ";" 
  ;

syntax Name =
  LEX_Name 
  ;


syntax AttributedName =
  attribName: Attribute* Name \ NameKeywords 
  ;

keyword NameKeywords =
  "var" 
  ;

syntax Name =
  LEX_Name 
  ;

syntax Decl =
  decl: "var" {AttributedName ","}+ ";" 
  ;



syntax Case =
  \case: "case" Name \ NameKeywords "of" Stmt 
  ;

keyword NameKeywords =
  "case" 
  | "end" 
  | "else" 
  | "typecase" 
  ;

syntax ElseCase =
  elseCase: "else" Stmt 
  ;

syntax Stmt =
  typeCaseElse: "typecase" Exp "of" Case+ ElseCase "end" 
  | typeCase: "typecase" Exp "of" Case+ "end" 
  ;

syntax Name =
  LEX_Name 
  ;



keyword NameKeywords =
  "true" 
  | "false" 
  ;

syntax Name =
  LEX_Name 
  ;

lexical Bool =
  "true" 
  | "false" 
  ;


layout LAYOUTLIST  =
  LAYOUT* !>> [\t-\n \a0D \ ] 
  ;

syntax Name =
  LEX_Name 
  ;

lexical LAYOUT =
  [\t-\n \a0D \ ] 
  ;


syntax Name =
  OperatorName \ OperatorNameKeywords 
  | LEX_Name 
  ;

lexical LEX_Name =
  [A-Z a-z] [0-9 A-Z a-z]* 
  ;

lexical OperatorName =
  [! # % *-+ \- / \<-= \> ^]+ 
  ;


keyword NameKeywords =
  "begin" 
  | "end" 
  ;

syntax Stmt =
  block: "begin" Decl* Stmt "end" 
  ;

syntax Name =
  LEX_Name 
  ;


keyword OperatorNameKeywords =
  "\<-" 
  ;

keyword NameKeywords =
  "\<-" 
  ;

syntax Stmt =
  assign: Exp "\<-" Exp ";" 
  ;

syntax Name =
  LEX_Name 
  ;
