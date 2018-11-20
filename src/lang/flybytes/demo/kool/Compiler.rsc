module lang::flybytes::demo::kool::Compiler

import lang::flybytes::demo::pico::Syntax;

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

import lang::flybytes::api::System; // for stdout
import lang::flybytes::api::Object; // for toString
import lang::flybytes::api::String; // for concat
import lang::flybytes::api::JavaLang; // for parseInt

import lang::flybytes::macros::ControlFlow; // for_array

import String;
import ParseTree;


list[Class] compile(str name, (Program) `<Class* classes> <Exp main>`) {
  mainClass = class(object(name)
    methods=[
      main("args", [
         stdout(compile(main))
      ])
    ]
  );
  
  return [mainClass] + [compile(cl) | cl <- classes];
}

Class compile((Class) `class <Name n> extends  <Name super> is <Decl* fields> <Method* methods> end`)
  = class(object(n),
      super=object(super),
      fields = [*fields(decl) | decl <- fields],
      methods = [method(m) | m <- methods]
    );

Class compile((Class) `class <Name n> is <Decl* fields> <Method* methods> end`)
  = class(object(n),
      fields = [*fields(decl) | decl <- fields],
      methods = [method(m) | m <- methods]
    );    
 
list[Field] fields((Decl) `var <{AttributedName ","}+ attrs>`) = fields(attrs);

list[Field] field({AttributedName ","}+ attrs) = [field(attr) | attr <- attrs];

Field field((AttributeName) `<Attribute* attrs> <Name name>`) = field(object(), "<name>");
 
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

list[Stat] compile((Stmr) `if <Exp cond> then <Stmt thenPart> else <Stmt elsePart> fi`)
  = \if(compile(cond), compile(thenPart), compile(elsePart));

list[Stat] compile((Stmr) `if <Exp cond> then <Stmt thenPart> fi`)
  = \if(compile(cond), compile(thenPart));

list[Stat] compile((Stmt) `begin <Decl* decls> <Stmt b> end`)
  = [decl(object(), "<d.name>") | a <- decls, d <- d.attrs]
    + compile(b);

list[Stat] compile((Stmt) `skip`) = [];

// TODO: this seems simplistic
list[Stat] compile((Stmt) `<Name n> \<- <Exp r>`)
  = [store("<n>", compile(r))];

list[Stat] compile((Stmt) `acquire <Exp e>;`)
  = [acquire(compile(e))];

list[Stat] compile((Stmt) `release <Exp e>;`)
  = [release(compile(e))];
 
list[Stat] compile((Stmt) `for <Name n> \<- <Exp from> to <Exp to> do <Stmt body> od`)
  = [decl("$<n>_from", init=compile(from)),
     decl("$<n>_to", init=compile(to)),
     \if(lt(load("$<n>_from"), load("$<n>to")), [
       \for([
           // for (int n = from; 
           decl(integer(), "<n>", \init=load("$<n>_from"))],
           // n < to;
           lt(load("<n>"), load("$<n>_to")),    
           // i++) {
           [incr("<n>", 1)],
           compile(body)
           // }
       )
     ],[
       \for([
           // for (int n = from; 
           decl(integer(), "<n>", \init=load("$<n>_from"))],
           // n >= to;
           ge(load("<n>"), load("$<n>_to")),    
           // i--) {
           [incr("<n>", -1)],
           compile(body)
           // }
       )
     ])
     ];        
/*
  | \assert: "assert" Exp ";" 
  | typeCaseElse: "typecase" Exp "of" Case+ ElseCase "end" 
  | typeCase: "typecase" Exp "of" Case+ "end" 
  | spawn: "spawn" Exp ";" 
  | labelStmt: Name ":" 
  | stmtExp: Exp ";" 
  ;
*/
Stat compile((Stmt) `return;`) = \return();
Stat compile((Stmt) `return <Exp e>;`) = \return(compile(e));