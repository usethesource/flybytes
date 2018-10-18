module lang::mujava::demo::func::Compiler

import demo::lang::Func::Func;

import lang::mujava::Syntax;
import lang::mujava::Compiler;

import lang::mujava::api::System; // for stdout
import lang::mujava::api::Object; // for toString
import lang::mujava::api::String; // for concat
import lang::mujava::api::JavaLang; // for parseInt

import String;
import ParseTree;
import util::UUID;

void testFunFactorial() {
  Prog tree = parse(#start[Prog], |project://mujava/src/lang/mujava/demo/func/fac.func|).top;
  compileProg(tree, "FuncFactorial", |project://mujava/generated|);
}

void compileProg(Prog p, str name, loc folder) {
  compileClass(compileProg(p, name), folder + "<name>.class");
}

Class compileProg((Prog) `<Func* funcs>`, str name) 
  = class(reference(name),
      methods=[
        *functions(funcs),
        main("args",[
           stdout(invokeStatic(Integer(), methodDesc(string(), "toString", [integer()]), [invokeStatic(methodDesc(integer(), "main", []), [])])),
           \return() 
        ])
      ]
  );

list[Method] functions(Func* funcs) = [func(f) | f <- funcs];

Method func((Func) `<Ident name>(<{Ident ","}* params>) = <Exp e>`)
  = staticMethod(\public(), integer(), "<name>", formals(params), [
      \return(expr(e))
    ]);

list[Formal] formals({Ident ","}* params) = [var(integer(), "<i>") | Ident i <- params];

Exp expr((Exp) `let <{Binding ","}* bindings> in <Exp e> end`) {
  renamed = ("<i>" : "$var_<uuidi()>" | (Binding) `<Ident i> = <Exp init>` <- bindings);
  
  Exp rename(Exp f) = visit (f) {
    case Ident i => [Ident] renamed["<i>"] when "<i>" in renamed 
  };
  
  decls = [decl(integer(), renamed["<i>"], init=expr(rename(inExp))) 
          | (Binding) `<Ident i> = <Exp inExp>` <- bindings
          ];
          
  return sblock(decls, expr(rename(e)));
}

Exp  expr((Exp) `if <Exp cond> then <Exp thenPart> else <Exp elsePart> end`) {
  v = "$cond_<uuidi()>";
  return sblock([
    decl(boolean(), v),
    \if (expr(cond), [store(v, expr(thenPart))], [store(v, expr(elsePart))])
  ], load(v));
}

Exp expr((Exp) `(<Exp e>)`) = expr(e);

Exp expr((Exp) `<Ident i>`) = load("<i>");

Exp expr((Exp) `<Natural n>`) = iconst(toInt("<n>"));

Exp expr((Exp) `<Ident i>(<{Exp ","}* args>)`)
  = invokeStatic(methodDesc(integer(), "<i>", [integer() | _ <- args]), [expr(a) | a <- args]);

// TODO
//           | address: "&" Ident
//           > deref: "*" Exp 

Exp expr((Exp) `<Exp l> * <Exp r>`) = mul(expr(l), expr(r));
Exp expr((Exp) `<Exp l> / <Exp r>`) = div(expr(l), expr(r));
Exp expr((Exp) `<Exp l> + <Exp r>`) = add(expr(l), expr(r));
Exp expr((Exp) `<Exp l> - <Exp r>`) = sub(expr(l), expr(r));

Exp expr((Exp) `<Exp l> \> <Exp r>`) = gt(expr(l), expr(r));
Exp expr((Exp) `<Exp l> \< <Exp r>`) = lt(expr(l), expr(r));
Exp expr((Exp) `<Exp l> \>= <Exp r>`) = ge(expr(l), expr(r));
Exp expr((Exp) `<Exp l> \<= <Exp r>`) = le(expr(l), expr(r));

Exp expr((Exp) `<Ident i> := <Exp r>`) = sblock([store("<i>", <expr(r)>)],load("<i>"));
Exp expr((Exp) `<Exp l> ; <Exp r>`) = sblock([\do(expr(l))], expr(r));
