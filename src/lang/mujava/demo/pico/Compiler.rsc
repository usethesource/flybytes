module lang::mujava::demo::pico::Compiler

import lang::pico::\syntax::Main;
import lang::mujava::Syntax;
import lang::mujava::Compiler;

import lang::mujava::api::System; // for stdout
import lang::mujava::api::Object; // for toString
import lang::mujava::api::String; // for concat

import String;
import ParseTree;

void testFactorial() {
  Program tree = parse(#start[Program], |std:///demo/lang/Pico/programs/Fac.pico|).top;
  compileProgram(tree, "Factorial", |project://mujava/generated|);
}

Class getFactorial() {
   Program tree = parse(#start[Program], |std:///demo/lang/Pico/programs/Fac.pico|).top;
   return compileProgram(tree, "Factorial");
}

void compileProgram(Program p, str name, loc folder) {
  compileClass(compileProgram(p, name), folder + "<name>.class");
}

@doc{compile Pico program to a class object}
Class compileProgram(Program p, str name)
  = class(reference(name),
      methods=[
        main("args", [
          *decls(p.decls),
          *stats(p.body),
          *output(p.decls),
          \return()
        ])
      ]
  );
  
list[Stat] decls(Declarations p)
  = [decl(\type(t), "<i>") | (IdType) `<Id i> : <Type t>` <- p.decls];
 
Type \type((Type) `natural`) = integer();
Type \type((Type) `string`) = string();
  
list[Stat] stats({Statement  ";"}* stats) 
  = [stat(s) | s <- stats];
  
Stat stat((Statement) `<Id var> := <Expression val>`)
   = store("<var>", expr(val)); 
   
Stat stat((Statement) `if <Expression cond> then 
                 '  <{Statement ";"}* thenPart> 
                 'else 
                 '  <{Statement ";"}* elsePart> 
                 'fi`)
   = \if(expr(cond), stats(thenPart), stats(elsePart));
   
Stat stat((Statement) `while <Expression cond> do 
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
      
