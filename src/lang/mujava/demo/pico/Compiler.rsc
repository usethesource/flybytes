module lang::mujava::demo::pico::Compiler

import lang::pico::\syntax::Main;
import lang::mujava::Syntax;
import lang::mujava::Compiler;

import lang::mujava::api::System; // for stdout
import lang::mujava::api::Object; // for toString
import lang::mujava::api::String; // for concat

import lang::mujava::macros::ControlFlow; // for_array

import String;
import ParseTree;

void testFactorial() {
  Program tree = parse(#start[Program], |project://mujava/src/lang/mujava/demo/pico/fac.pico|).top;
  compileProgram(tree, "Factorial", |project://mujava/generated|);
}

void compileProgram(Program p, str name, loc folder) {
  compileClass(compileProgram(p, name), folder + "<name>.class");
}

@doc{compile Pico program to a class object}
Class compileProgram(Program p, str name)
  = class(reference(name),
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
 
list[Stat] commandline(Declarations p) 
  = [for_array("$$args", "i", [
        \if (equals(sconst("<i>"), aload(load("$$args"), load("i"))), [
          store("<i>", convert(t, aload(load("$$args"), add(load("i"), iconst(1)))))
        ])
      ])
    | (IdType) `<Id i> : <Type t>` <- p.decls];
   
Exp convert((Type) `natural`, Exp e) = invokeStatic(reference("java.lang.Integer"), methodDesc(integer(), "parseInt", [string(), integer()]), [e, iconst(10)]);
Exp convert((Type) `string`, Exp e)  = e;
 
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
  = invokeStatic(reference("java.lang.Integer"), methodDesc(string(), "toString", [integer()]), [load("<i>")]);    
    
Exp toString(Id i, (Type) `string`)
  = load("<i>");
      
