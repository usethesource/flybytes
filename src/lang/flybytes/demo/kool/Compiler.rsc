@synopsis{A compiler from the Kool language to JVM bytecode}
module lang::flybytes::demo::kool::Compiler

import lang::flybytes::demo::kool::Syntax;

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

import lang::flybytes::api::System; // for stdout


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
         stdout(compile(m))
      ])
    ]
  );
  
  return [mainClass] + [compile(cl) | cl <- classes];
}

Class compile((Class) `class <Name n> extends  <Name super> is <Decl* fs> <Method* methods> end`)
  = class(object("<n>"),
      super=object("<super>"),
      fields = [*fields(decl) | decl <- fs],
      methods = [method(m) | m <- methods]
    );

Class compile((Class) `class <Name n> is <Decl* fs> <Method* methods> end`)
  = class(object("<n>"),
      fields = [*fields(decl) | decl <- fs],
      methods = [method(m) | m <- methods]
    );    
 
list[Field] fields((Decl) `var <{AttributedName ","}+ attrs>`) = fields(attrs);

list[Field] fields({AttributedName ","}+ attrs) = [field(attr) | attr <- attrs];

Field field((AttributedName) `<Attribute* attrs> <Name name>`) = field(object(), "<name>");
 
Method method((Method) `method <Name name>(<{AttributedName ","}+ attrs>) is <Decl* decls> <Stmt block> end`)
  = method(\public(), "<name>", [object() | _ <- attrs], 
     [decl(object(), "<d.name>") | a <- decls, d <- d.attrs] 
   + compile(block));
   
Method method((Method) `method <Name name> is <Decl* decls> <Stmt block> end`)
  = method(\public(), "<name>", [], 
     [decl(object(), "<d.name>") | a <- decls, d <- d.attrs] 
   + compile(block));   
  
Exp compile((Exp) `self`) = this();

default list[Stat] compile((Stmt) `<Stmt first> <Stmt next>`) = [*compile(first), *compile(next)];

list[Stat] compile((Stmt) `throw <Exp e>`) = [\throw(compile(e))];

list[Stat] compile((Stmt) `try <Stmt b> catch <Name n> <Stmt c> end`)
  = [\try(compile(b),[\catch(object(), "<n>", compile(c))])];
  
list[Stat] compile((Stmt) `while <Exp cond> do <Stmt b> od`)
  = [\while(compile(cond), compile(b))];
  
list[Stat] compile((Stmt) `do <Stmt b> while <Exp cond> od`)
  = [\doWhile(compile(b), compile(cond))];

list[Stat] compile((Stmt) `break;`) = [\break()];

list[Stat] compile((Stmt) `continue;`) = [\continue()];

list[Stat] compile((Stmt) `if <Exp cond> then <Stmt thenPart> else <Stmt elsePart> fi`)
  = [\if(compile(cond), compile(thenPart), compile(elsePart))];

list[Stat] compile((Stmt) `if <Exp cond> then <Stmt thenPart> fi`)
  = [\if(compile(cond), compile(thenPart))];

list[Stat] compile((Stmt) `begin <Decl* decls> <Stmt b> end`)
  = [decl(object(), "<d.name>") | a <- decls, d <- d.attrs]
    + compile(b);

list[Stat] compile((Stmt) `skip`) = [];

// TODO: this seems simplistic, but since we do not have name analysis, I can not figure out
// if this assignment is into a field or into something else. Perhaps a little environment must be
// passed down to list at least which names are fields. 
list[Stat] compile((Stmt) `<Name n> \<- <Exp r>`)
  = [store("<n>", compile(r))];

list[Stat] compile((Stmt) `acquire <Exp e>;`)
  = [acquire(compile(e))];

list[Stat] compile((Stmt) `release <Exp e>;`)
  = [release(compile(e))];
 
list[Stat] compile((Stmt) `for <Name n> \<- <Exp from> to <Exp to> do <Stmt body> od`)
  = [decl(integer(), "$<n>_from", init=compile(from)),
     decl(integer(), "$<n>_to", init=compile(to)),
     // if (from < to) { and so we count up:
     \if(lt(load("$<n>_from"), load("$<n>to")), [
       \for([
           // for (int n = from; 
           decl(integer(), "<n>", \init=load("$<n>_from"))],
           // n < to;
           lt(load("<n>"), load("$<n>_to")),    
           // n++) {
           [incr("<n>", 1)],
           compile(body)
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
           compile(body)
           // }
       )
     ])
     ]; 
     
list[Stat] compile((Stmt) `<Exp e>;`) = [\do(compile(e))];
            
/*
  | \assert: "assert" Exp ";" 
  | typeCaseElse: "typecase" Exp "of" Case+ ElseCase "end" 
  | typeCase: "typecase" Exp "of" Case+ "end" 
  | spawn: "spawn" Exp ";" 
  | labelStmt: Name ":" 
  ;
*/
Stat compile((Stmt) `return;`) = \return();
Stat compile((Stmt) `return <Exp e>;`) = \return(compile(e));