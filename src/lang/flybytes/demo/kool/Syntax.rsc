module lang::flybytes::demo::kool::Syntax

syntax Program = program: Class* Exp;
  
syntax Class 
  = class: "class" Name "extends" Name "is" Decl* Method* "end" 
  | primClassNoParent: "primclass" Name "is" Decl* Method* "end" 
  | classNoParent: "class" Name "is" Decl* Method* "end" 
  | primClass: "primclass" Name "extends" Name "is" Decl* Method* "end" 
  ;

syntax Method 
  = methodWParamsDef: "method" Name "(" {AttributedName ","}+ ")" "is" Decl* Stmt "end" 
  | methodDef: "method" Name "is" Decl* Stmt "end" 
  ;

syntax Stmt 
  = right seqComp: Stmt Stmt 
  | \throw: "throw" Exp ";" 
  | tryCatch: "try" Stmt "catch" Name Stmt "end" 
  | whileLoop: "while" Exp "do" Stmt "od" 
  | doLoop: "do" Stmt "while" Exp "od" 
  | \break: "break" ";" 
  | forLoop: "for" Name "\<-" Exp "to" Exp "do" Stmt "od" 
  | \continue: "continue" ";" 
  | ifThenElse: "if" Exp "then" Stmt "else" Stmt "fi" 
  | ifThen: "if" Exp "then" Stmt "fi" 
  | \assert: "assert" Exp ";" 
  | returnNoVal: "return" ";" 
  | \return: "return" Exp ";" 
  | typeCaseElse: "typecase" Exp "of" Case+ ElseCase "end" 
  | typeCase: "typecase" Exp "of" Case+ "end" 
  | block: "begin" Decl* Stmt "end" 
  | assign: Exp "\<-" Exp ";" 
  | release: "release" Exp ";" 
  | spawn: "spawn" Exp ";" 
  | acquire: "acquire" Exp ";" 
  | skip: "skip" ";" 
  | labelStmt: Name ":" 
  | stmtExp: Exp ";" 
  ;

syntax Attribute 
  = attribNoParams: "@" Name 
  | attribWParams: "@" Name "(" {Exp ","}+ ")" 
  ;

syntax Exp 
  = new: "new" Name "(" {Exp ","}* ")" 
  | newNoParams: "new" Name 
  | vector: "(#" {Exp ","}+ "#)" 
  | self: "self" 
  | superSame: "super" "(" {Exp ","}+ ")" 
  | superUnarySame: "super" "(" ")" 
  | super: "super" "." Name "(" {Exp ","}+ ")" 
  | superUnary: "super" "." Name (  "("    ")"  )? 
  | string: String 
  | \bool: Bool 
  | integer: Integer 
  | nil: "nil" 
  | bracket "(" Exp ")" 
  | name: Name 
  | char: Char 
  | float: Float 
  | prim: "primInvoke" "(" {Exp ","}+ ")" 
  | left send: Exp "." Name "(" {Exp ","}+ ")" 
  | left unarySend: Exp "." Name (  "("    ")"  )? 
  | left binOpSend: Exp OperatorName Exp 
  | left 
    ( left unarySend: Exp "." Name (  "("    ")"  )? 
    | left send: Exp "." Name "(" {Exp ","}+ ")" 
    )
  > left binOpSend: Exp OperatorName Exp 
  > non-assoc 
    ( refEq: Exp "==" Exp
    | non-assoc refNeq: Exp "/=" Exp
    )
  ;

lexical Float 
  = [+ \-]? [0-9]+ "." 
  | [+ \-]? "." [0-9]+ 
  | [+ \-]? [0-9]+ "." [0-9]+ 
  ;

lexical Integer = [+ \-]? [0-9]+ ; 

syntax AttributedName = attribName: Attribute* Name;

syntax Decl = decl: "var" {AttributedName ","}+ ";" ;

syntax Case = \case: "case" Name "of" Stmt ;

syntax ElseCase = elseCase: "else" Stmt ;

lexical Bool 
  = "true" 
  | "false" 
  ;

syntax Name 
  = [0-9 A-Z a-z] !<< ([A-Z a-z] [0-9 A-Z a-z]*) !>> [0-9 A-Z a-z] \ NameKeywords
  | OperatorName
  ;

keyword NameKeywords 
  = "var" 
  | "return" 
  | "spawn" 
  | "skip" 
  | "primInvoke" 
  | "if" 
  | "fi" 
  | "else" 
  | "then" 
  | "continue" 
  | "do" 
  | "for" 
  | "break" 
  | "od" 
  | "to" 
  | "while"
  | "nil"
  | "super"
  | "extends" 
  | "throw" 
  | "self" 
  | "new" 
  | "is" 
  | "end" 
  | "method" 
  | "begin" 
  | "end" 
  | "true" 
  | "false"
  | "case" 
  | "end" 
  | "else" 
  | "typecase"
  | "end" 
  | "try" 
  | "catch" 
  | "end" 
  | "is" 
  | "class" 
  | "primclass"  
  | "assert" 
  | "release" 
  | "acquire" 
  ;
  
lexical OperatorName 
  = [!#%*-+\-/\<-=\>^] !<< [!#%*-+\-/\<-=\>^]+ !>> [!#%*-+\-/\<-=\>^] \ OperatorNameKeywords
  ;

keyword OperatorNameKeywords 
  = "/=" 
  | "==" 
  | "@"
  | "\<-" 
  ;

lexical String = "\"" ![\n\a0D\"\\]* "\"";
lexical Char = "\'" [0-9 A-Z a-z] "\'";

layout WhitespaceComments  = (Whitespace | Comment)* !>> [\t-\n\a0D\ ] !>> "//" !>> "/*" ;

lexical Whitespace = [\t-\n \a0D \ ];

lexical Comment 
  = @category="Comment" line: "//" ![\n]* [\n] 
  | @category="Comment" BlockComment 
  ;

lexical BlockComment =  "/*" CommentPart* "*/";

lexical CommentPart 
  = "*" !>> [/] 
  | ![*/]+ !>> ![*/] 
  | BlockComment 
  | "/" !>> [*] 
  ;