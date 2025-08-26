module lang::flybytes::demo::func::Compiler

import lang::flybytes::demo::func::Syntax;

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

import lang::flybytes::api::System; // for stdout
import lang::flybytes::api::JavaLang; // for parseInt

import String;
import ParseTree;

void testFunc() {
  tree = parse(#start[Prog], |project://flybytes/src/lang/flybytes/demo/func/fac.func|).top;
  compileProg(tree, "FuncFactorial", |project://flybytes/generated|);
  
  tree = parse(#start[Prog], |project://flybytes/src/lang/flybytes/demo/func/factlet.func|).top;
  compileProg(tree, "FuncLetFactorial", |project://flybytes/generated|);
  
  tree = parse(#start[Prog], |project://flybytes/src/lang/flybytes/demo/func/shadowing.func|).top;
  compileProg(tree, "Shadowing", |project://flybytes/generated|);
}

void compileProg(Prog p, str name, loc folder) {
  compileClass(compileProg(p, name), folder + "<name>.class", debugMode=true);
}

Class compileProg(p:(Prog) `<Func* funcs>`, str name) 
  = class(object(name),
      methods=[
        *functions(funcs),
        main("args",[
           stdout(invokeStatic(Integer(), methodDesc(string(), "toString", [integer()]), [invokeStatic(methodDesc(integer(), "main", []), [])[src=p@\loc]])),
           \return() 
        ])[src=p@\loc]
      ]
  )[src=p@\loc];

list[Method] functions(Func* funcs) = [func(f) | f <- funcs];

Method func((Func) `<Ident name>(<{Ident ","}* params>) = <FExp e>`)
  = staticMethod(\public(), integer(), "<name>", formals(params), [
      \return(expr(e, ()), src=e@\loc)
    ])[src=e@\loc];

list[Formal] formals({Ident ","}* params) = [var(integer(), "<i>") | Ident i <- params];

Exp expr((FExp) `let <{Binding ","}* bindings> in <FExp e> end`, map[str,str] names) {
  decls = for((Binding) `<Ident i> = <FExp val>` <- bindings) {
    // it's a let*
    names += ("<i>" : (names["<i>"]?) ? "<names["<i>"]>\'" : "<i>"); // shadowing works via renaming
    append decl(integer(), names["<i>"], init=expr(val, names), src=val@\loc);
  }
  
  return sblock(decls, expr(e, names));
}

Exp  expr(e:(FExp) `if <FExp c> then <FExp thenPart> else <FExp elsePart> end`, map[str,str] names)
  = Exp::cond(expr(c, names), expr(thenPart, names), expr(elsePart, names), src=e@\loc);

Exp expr((FExp) `(<FExp e>)`, map[str,str] names) = expr(e, names);
Exp expr(e:(FExp) `<Ident i>`, map[str,str] names) = load(names["<i>"]?"<i>", src=e@\loc);
Exp expr(e:(FExp) `<Natural n>`, map[str,str] _) = iconst(toInt("<n>"))[src=e@\loc];

Exp expr(e:(FExp) `<Ident i>(<{FExp ","}* args>)`, map[str,str] names)
  = invokeStatic(methodDesc(integer(), names["<i>"]?"<i>", [integer() | _ <- args]), [expr(a, names) | a <- args])[src=e@\loc];
Exp expr(e:(FExp) `<FExp l> * <FExp r>`, map[str,str] names) = mul(expr(l, names), expr(r, names), src=e@\loc);
Exp expr(e:(FExp) `<FExp l> / <FExp r>`, map[str,str] names) = div(expr(l, names), expr(r, names), src=e@\loc);
Exp expr(e:(FExp) `<FExp l> + <FExp r>`, map[str,str] names) = add(expr(l, names), expr(r, names), src=e@\loc);
Exp expr(e:(FExp) `<FExp l> - <FExp r>`, map[str,str] names) = sub(expr(l, names), expr(r, names), src=e@\loc);

Exp expr(e:(FExp) `<FExp l> \> <FExp r>`, map[str,str] names) = gt(expr(l, names), expr(r, names), src=e@\loc);
Exp expr(e:(FExp) `<FExp l> \< <FExp r>`, map[str,str] names) = lt(expr(l, names), expr(r, names), src=e@\loc);
Exp expr(e:(FExp) `<FExp l> \>= <FExp r>`, map[str,str] names) = ge(expr(l, names), expr(r, names), src=e@\loc);
Exp expr(e:(FExp) `<FExp l> \<= <FExp r>`, map[str,str] names) = le(expr(l, names), expr(r, names), src=e@\loc);

Exp expr(e:(FExp) `<Ident i> := <FExp r>`, map[str,str] names) = sblock([store("<i>", expr(r, names), src=e@\loc)],load("<i>"), src=e@\loc);
Exp expr((FExp) `<FExp l> ; <FExp r>`, map[str,str] names) = sblock([\do(expr(l, names))], expr(r, names));
