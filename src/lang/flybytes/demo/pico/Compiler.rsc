module lang::flybytes::demo::pico::Compiler

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

void testFactorial() {
  Program tree = parse(#start[Program], |project://flybytes/src/lang/flybytes/demo/pico/fac.pico|).top;
  compileProgram(tree, "Factorial", |project://flybytes/generated|);
}

void compileProgram(Program p, str name, loc folder) {
  compileClass(compileProgram(p, name), folder + "<name>.class");
}

@doc{compile Pico program to a class object}
Class compileProgram(Program p, str name)
  = class(object(name),
      methods=[
        main("$$args", [
          *decls(p.decls), 
          *commandline(p.decls), 
          *stats(p.body),
          *output(p.decls),
          \return()
        ])
      ]
  );
  
list[Stat] decls(Declarations p)
  = [decl(\type(t), "<i>") | (IdType) `<Id i> : <Type t>` <- p.decls];
 

Type \type((Type) `natural`) = integer();
Type \type((Type) `string`)  = string();
  
list[Stat] stats({Statement  ";"}* stats) = [stat(s) | s <- stats];
  
Stat stat((Statement) `<Id var> := <Expression val>`)
   = store("<var>", expr(val)); 
   
Stat stat((Statement) 
                 `if <Expression cond> then 
                 '  <{Statement ";"}* thenPart> 
                 'else 
                 '  <{Statement ";"}* elsePart> 
                 'fi`)
   = \if(expr(cond), stats(thenPart), stats(elsePart));
   
Stat stat((Statement) 
                 `while <Expression cond> do 
                 '  <{Statement ";"}* body> 
                 'od`)
   = \while(expr(cond), stats(body));
   
Exp expr((Expression) `<Id name>`)                        = load("<name>");
Exp expr((Expression) `<String string>`)                  = const(string(), "<string>"[1..-1]);
Exp expr((Expression) `<Natural natcon>`)                 = const(integer(), toInt("<natcon>"));  
Exp expr((Expression) `(<Expression e>)`)                 = expr(e);
Exp expr((Expression) `<Expression l> || <Expression r>`) = String_concat(expr(l), expr(r));
Exp expr((Expression) `<Expression l> + <Expression r>`)  = add(expr(l), expr(r));
Exp expr((Expression) `<Expression l> - <Expression r>`)  = sub(expr(l), expr(r));

list[Stat] output(Declarations p)
  = [stdout(String_concat(const(string(), "<i>\t: "), toString(i, t))) 
    | (IdType) `<Id i> : <Type t>` <- p.decls]
    ;
    
Exp toString(Id i, (Type) `natural`) 
  = invokeStatic(object("java.lang.Integer"), methodDesc(string(), "toString", [integer()]), [load("<i>")]);    
    
Exp toString(Id i, (Type) `string`)
  = load("<i>");
      
list[Stat] commandline(Declarations p) 
  = [for_array("$$args", "i", [
       // if (args[i].equals(varName))
        \if (equals(sconst("<i>"), aload(load("$$args"), load("i"))), [
          // varName = fromString(args[i+1])
          store("<i>", fromString(t, aload(load("$$args"), add(load("i"), iconst(1)))))
        ])
      ])
    | (IdType) `<Id i> : <Type t>` <- p.decls];
   
Exp fromString((Type) `natural`, Exp e) = Integer_parseInt(e, 10);
Exp fromString((Type) `string`, Exp e)  = e;
 
