module lang::flybytes::demo::pico::Compiler

import lang::flybytes::demo::pico::Syntax;

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

import lang::flybytes::api::System; // for stdout
import lang::flybytes::api::Object; // for toString
import lang::flybytes::api::String; // for concat
import lang::flybytes::api::JavaLang; // for parseInt

import lang::flybytes::macros::ControlFlow; // for_array

import IO;
import String;
import ParseTree;
import util::IDE;
import util::ValueUI;

void registerPico() {
  registerLanguage("Pico", "pico", 
    Tree (str input, loc src) { 
      return parse(#start[Program], input, src); 
    }
  );
}

Program parse(loc program) = parse(#start[Program], program).top;

void testFactorial() {
  Program tree = parse(|project://flybytes/src/lang/flybytes/demo/pico/fac.pico|);
  println(tree);
  compileProgram(tree, "Factorial", |project://flybytes/generated|);
} 

void testITE() {
  Program tree = parse(|project://flybytes/src/lang/flybytes/demo/pico/ite.pico|);
  println(tree);
  compileProgram(tree, "ITE", |project://flybytes/generated|);
}

void compileProgram(Program p, str name, loc folder) {
  cl = compileProgram(p, name);
  text(cl);
  compileClass(compileProgram(p, name), folder + "<name>.class", debugMode=true);
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
        ])[src=p@\loc]
      ]
  )[src=p@\loc];
  
list[Stat] decls(Declarations p)
  = [decl(\type(t), "<i>")[src=i@\loc] | (IdType) `<Id i> : <Type t>` <- p.decls];
 

Type \type((Type) `natural`) = integer();
Type \type((Type) `string`)  = string();
  
list[Stat] stats({Statement  ";"}* stats) = [stat(s)[src=s@\loc] | s <- stats];
  
Stat stat(s:(Statement) `<Id var> := <Expression val>`)
   = store("<var>", expr(val)); 
   
Stat stat(s:(Statement) 
                 `if <Expression cond> then 
                 '  <{Statement ";"}* thenPart> 
                 'else 
                 '  <{Statement ";"}* elsePart> 
                 'fi`)
   = \if(ne(expr(cond), iconst(0)), stats(thenPart), stats(elsePart));
   
Stat stat(s:(Statement) 
                 `while <Expression cond> do 
                 '  <{Statement ";"}* body> 
                 'od`)
   = \while(expr(cond), stats(body));
   
Exp expr(e:(Expression) `<Id name>`)                        = load("<name>", src=e@\loc);
Exp expr(e:(Expression) `<String s>`)                       = const(string(), "<s>"[1..-1], src=e@\loc);
Exp expr(e:(Expression) `<Natural natcon>`)                 = const(integer(), toInt("<natcon>"), src=e@\loc);  
Exp expr(e:(Expression) `(<Expression e>)`)                 = expr(e);
Exp expr(e:(Expression) `<Expression l> || <Expression r>`) = String_concat(expr(l), expr(r))[src=e@\loc];
Exp expr(e:(Expression) `<Expression l> + <Expression r>`)  = add(expr(l), expr(r), src=e@\loc);
Exp expr(e:(Expression) `<Expression l> - <Expression r>`)  = sub(expr(l), expr(r), src=e@\loc);

list[Stat] output(Declarations p)
  = [stdout(String_concat(const(string(), "<i>\t: "), toString(i, t)))[src=i@\loc] 
    | (IdType) `<Id i> : <Type t>` <- p.decls]
    ;
    
Exp toString(Id i, (Type) `natural`) 
  = invokeStatic(object("java.lang.Integer"), methodDesc(string(), "toString", [integer()]), [load("<i>")])[src=i@\loc];    
    
Exp toString(Id i, (Type) `string`)
  = load("<i>", src=i@\loc);
      
list[Stat] commandline(Declarations p) 
  = [for_array("$$args", "i", [
       // if (args[i].equals(varName))
        \if (equals(sconst("<i>"), aload(load("$$args"), load("i"))), [
          // varName = fromString(args[i+1])
          store("<i>", fromString(t, aload(load("$$args"), add(load("i"), iconst(1)))))
        ])[src=i@\loc]
      ])
    | (IdType) `<Id i> : <Type t>` <- p.decls];
   
Exp fromString((Type) `natural`, Exp e) = Integer_parseInt(e, 10);
Exp fromString((Type) `string`, Exp e)  = e;
 
