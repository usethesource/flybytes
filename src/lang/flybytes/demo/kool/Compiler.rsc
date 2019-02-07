@synopsis{A compiler from the Kool language to JVM bytecode}
module lang::flybytes::demo::kool::Compiler

import lang::flybytes::demo::kool::Syntax;

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

import lang::flybytes::api::System; // for stdout

anno loc Exp@\loc;

@synopsis{Compiles a parse tree of a complete Kool program to a list of Flybytes classes}
@parameter{name		: name of the main class}
@parameter{Program	: parse tree of a Kool `Program`}
@description{
This Kool to Flybytes compiler accepts a list of class definitions and a final expression to execute.
This final expression is nested in a main class with a given `name`. Each 
class in the prelude is compiled to an individual JVM class first.
  
This compiler does not have a classpath to search for pre-compiled classes or additional
source files. All necessary classes must be included in the parse tree of a Program.
  
This compiler is simplistic, in the sense that it does not use a name analysis or type analysis stage.
It is meant to demonstrate a mapping of a realistic programming language syntax tree to Flybytes only. 
Therefore it may deviate from the official Kool language definition 
(by Mark Hills written in the K framework) in certain areas. Explicit comments have been added where 
this is known, but there may be other (more implicit) deviations. 
  
After Flybytes classes have been constructed, the Flybytes compiler can quickly map them
to JVM bytecode.
}
list[Class] compile(str name, (Program) `<Class* classes> <Exp m>`) {
  mainClass = class(object(name)
    methods=[
      main("args", [
         stdout(compile(m, []))
      ])[src=m@\loc]
    ]
  )[src=m@\loc];
  
  return [mainClass] + [compile(cl) | cl <- classes];
}

Class compile((Class) `class <Name n> extends  <Name super> is <Decl* fs> <Method* methods> end`)
  = class(object("<n>"),
      super=object("<super>"),
      fields = fields,
      methods = [method(m, fields) | m <- methods]
    )
  when fields := [*fields(decl) | decl <- fs];

Class compile((Class) `class <Name n> is <Decl* fs> <Method* methods> end`)
  = class(object("<n>"),
      fields = fields,
      methods = [method(m, fields) | m <- methods]
    )
   when fields := [*fields(decl) | decl <- fs];  
 
list[Field] fields((Decl) `var <{AttributedName ","}+ attrs>`) = fields(attrs);

list[Field] fields({AttributedName ","}+ attrs) = [field(attr) | attr <- attrs];

// TODO store the attributes in JVM annotations
Field field((AttributedName) `<Attribute* _> <Name name>`) = field(object(), "<name>");
 
Method method((Method) `method <Name name>(<{AttributedName ","}+ attrs>) is <Decl* decls> <Stmt block> end`, list[Field] fields)
  = method(\public(), object(), "<name>", [var(object(), "<a.name>") | a <- attrs], 
     [decl(object(), "<d.name>") | a <- decls, d <- a.attrs] 
   + [compile(block, fields)]);
   
Method method((Method) `method <Name name> is <Decl* decls> <Stmt block> end`, list[Field] fields)
  = method(\public(), object(), "<name>", [], 
     [decl(object(), "<d.name>") | a <- decls, d <- a.attrs] 
   + [compile(block, fields)]);   
  
Exp compile((Exp) `self`, list[Field] _) = this();

default list[Stat] compile((Stmt) `<Stmt first> <Stmt next>`, list[Field] fields) = [*compile(first, fields), *compile(next, fields)];

list[Stat] compile((Stmt) `throw <Exp e>`, list[Field] fields) = [\throw(compile(e, fields))];

list[Stat] compile((Stmt) `try <Stmt b> catch <Name n> <Stmt c> end`, list[Field] fields)
  = [\try(compile(b, fields),[\catch(object(), "<n>", compile(c, fields))])];
  
list[Stat] compile((Stmt) `while <Exp cond> do <Stmt b> od`, list[Field] fields)
  = [\while(compile(cond, fields), compile(b, fields))];
  
list[Stat] compile((Stmt) `do <Stmt b> while <Exp cond> od`, list[Field] fields)
  = [\doWhile(compile(b, fields), compile(cond, fields))];

list[Stat] compile((Stmt) `break;`, list[Field] fields) = [\break()];

list[Stat] compile((Stmt) `continue;`, list[Field] fields) = [\continue()];

list[Stat] compile((Stmt) `if <Exp cond> then <Stmt thenPart> else <Stmt elsePart> fi`, list[Field] fields)
  = [\if(compile(cond, fields), compile(thenPart, fields), compile(elsePart, fields))];

list[Stat] compile((Stmt) `if <Exp cond> then <Stmt thenPart> fi`, list[Field] fields)
  = [\if(compile(cond, fields), compile(thenPart, fields))];

list[Stat] compile((Stmt) `begin <Decl* decls> <Stmt b> end`, list[Field] fields)
  = [decl(object(), "<d.name>") | a <- decls, d <- d.attrs]
    + compile(b, fields);

list[Stat] compile((Stmt) `skip`, list[Field] fields) = [];

// TODO: this seems simplistic, but since we do not have name analysis, I can not figure out
// if this assignment is into a field or into something else. Perhaps a little environment must be
// passed down to list at least which names are fields. 
list[Stat] compile((Stmt) `<Name n> \<- <Exp r>`, list[Field] fields)
  = [store("<n>", compile(r, fields))];

list[Stat] compile((Stmt) `acquire <Exp e>;`, list[Field] fields)
  = [acquire(compile(e, fields))];

list[Stat] compile((Stmt) `release <Exp e>;`, list[Field] fields)
  = [release(compile(e, fields))];
 
list[Stat] compile((Stmt) `for <Name n> \<- <Exp from> to <Exp to> do <Stmt body> od`, list[Field] fields)
  = [decl(integer(), "$<n>_from", init=compile(from, fields)),
     decl(integer(), "$<n>_to", init=compile(to, fields)),
     // if (from < to) { and so we count up:
     \if(lt(load("$<n>_from"), load("$<n>to")), [
       \for([
           // for (int n = from; 
           decl(integer(), "<n>", \init=load("$<n>_from"))],
           // n < to;
           lt(load("<n>"), load("$<n>_to")),    
           // n++) {
           [incr("<n>", 1)],
           compile(body, fields)
           // }
       )
     ],[ // } else { ( from >= to ), and so we count down:
       \for([
           // for (int n = from; 
           decl(integer(), "<n>", \init=load("$<n>_from"))],
           // n >= to;
           ge(load("<n>"), load("$<n>_to")),    
           // n--) {
           [incr("<n>", -1)],
           compile(body, fields)
           // }
       )
     ])
     ]; 
     
list[Stat] compile((Stmt) `<Exp e>;`, list[Field] fields) = [\do(compile(e, fields))];
            
/*
  | \assert: "assert" Exp ";" 
  | typeCaseElse: "typecase" Exp "of" Case+ ElseCase "end" 
  | typeCase: "typecase" Exp "of" Case+ "end" 
  | spawn: "spawn" Exp ";" 
  | labelStmt: Name ":" 
  ;
*/
Stat compile((Stmt) `return;`, list[Field] fields) = \return();
Stat compile((Stmt) `return <Exp e>;`, list[Field] fields) = \return(compile(e, fields));