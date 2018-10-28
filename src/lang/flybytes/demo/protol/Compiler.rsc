module lang::flybytes::demo::protol::Compiler

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;
import lang::flybytes::api::Object;
import String;

// the Protol compiler translates object allocation sites to specific JVM class definitions,
// and uses invokeDynamic to call interfaces and set/get fields on objects of each 
// possible object in memory.

Type Prototype = object("lang.flybytes.demo.protol.Prototype");
Type Int       = object("lang.flybytes.demo.protol.Prototype.Int");
Type Str       = object("lang.flybytes.demo.protol.Prototype.Str");
Type Arr       = object("lang.flybytes.demo.protol.Prototype.Arr");

// an intermediate representation for prototype classes
data Type = prototype(str name, list[Method] methods, list[Field] fields);

list[Class] compile(Program p, str name) { 
  main = class(reference(name),
      methods=[
        main(\public(), compile(p.commands))
      ]
    );
 
  allClasses = [removePrototypeClasses(main), *extractPrototypeClasses(main)];

  return declareVariables(allClasses);  
}    

list[Stat] compile(Command *) = [compile(c) | c <- commands];

Stat compile((Command) `<Id id> = <Expr v>;`)
  = store("<id>", compile(v));
  
Stat compile((Command) `<Expr array>[<Expr index>] = <Expr v>;`)
  = astore(compile(array), getField(Str, compile(index), integer(), "integer"), compile(v));

Stat compile((Command) `if(<Expr cond >) { <Command* thenPart> } else { <Command* elsePart> }`)
  = \if(compile(cond), compile(thenPart), compile(elsePart));

Stat compile((Command) `while(<Expr cond>) { <Command* body> }`)
  = \while(compile(cond), compile(body));   

Stat compile((Command) `<Exp e>;`) = \do(compile(e));

Stat compile((Command) `return <Exp e>;`) = \return(compile(e));

Stat compile((Command) `print <Exp e>;`) = stdout(compile(e));
   
Exp compile((Expr) `<Expr rec>.<Id name>(<{Expr ","}* args>)`)
  = invokeDynamic(bootstrap(Prototype, "bootstrap", []), methodDesc(Prototype, "<name>", [Prototype | _ <- args]), compile(args));
   
Exp compile((Expr) `{<{Expr ","}* elems>}`)
  = new(Arr, array(Prototype), [newArray(Prototype, compile(args))]); 

Exp compile((Expr) `new`) = new(Prototype);

Exp compile((Expr) `new <Expr p>`) = new(Prototype, [compile(p)]);
     
Exp compile((Expr) `new { <Definition+ defs> }`)
  = new(prototype("<uuid()>", methods(defs), fields(defs)));

Exp compile((Expr) `new <Expr p> { <Definition+ defs> }`) 
  = new(prototype("<uuid()>", methods(defs), fields(defs)), [compile(p)]);
      
Exp compile((Expr) `(<Expr e>)`) = compile(e); 

Exp compile((Expr) `<Id i>`) = load("<i>");

Exp compile((Expr) `<Int i>`) = new(Int, toInt("<i>"));
 
Exp compile((Expr) `<String s>`) = new(Str, sconst("<s>"[1..-1]));

Exp compile((Expr) `<Expr a>[<Expr index>]`) 
  = aload(getField(Arr, compile(a), array(Prototype), "array"),
          compile(index));
     
Exp compile((Exp) `<Expr l> * <Expr r>`) 
  = new(Int, compile(l, r, mul));
  
Exp compile((Exp) `<Expr l> / <Expr r>`) 
  = new(Int, compile(l, r, div));  

Exp compile((Exp) `<Expr l> + <Expr r>`) 
  = new(Int, compile(l, r, add));  

Exp compile((Exp) `<Expr l> - <Expr r>`) 
  = new(Int, compile(l, r, sub));  

Exp compile(Expr l, Expr r, Exp (Exp, Exp) op) 
  = op(getField(Int, compile(l), integer(), "integer"),
       getField(Int, compile(r), integer(), "integer"));

Exp compile((Exp) `<Expr l> == <Expr r>`) 
  = equals(compile(l), compile(r));
 
Exp compile((Exp) `<Expr l> != <Expr r>`) 
  = neg(equals(compile(l), compile(r)));

Exp compile((Exp) `<Expr l> \<= <Expr r>`) 
  = compile(l, r, le);
  
Exp compile((Exp) `<Expr l> \< <Expr r>`) 
  = compile(l, r, lt);
 
Exp compile((Exp) `<Expr l> \> <Expr r>`) 
  = compile(l, r, gt);     

Exp compile((Exp) `<Expr l> \>= <Expr r>`) 
  = compile(l, r, ge);    

list[Method] methods(Definition+ defs) 
  = [ method("<name>", args, commands) 
    | (Definition) `<Id name>(<{Id ","}* args>) { <Command* commands> }` <- defs];

list[Field] fields(Definition+ defs)  
  = [ field("<name>", val) | (Definition) `<Id name> = <Expr val>` <- defs];

Method method(str name, {Id ","}* args, Command* commands)
  = method(\public(), Prototype, name, [var(Prototype, "<a>") | a <- args], compile(block));

Field field(str name, Expr val)
  = field(Prototype, name, init=compile(val));
   
list[Class] declareVariables(list[Class] classes) 
  = visit(classes) {
      case method(desc, formals, block) => method(desc, formals, [*decls, *block])  
      when 
        // transform assignments to declarations and remove duplicates:
        decls := { decl(Prototype, name) | /store(str name, _) := stats}
  };
  
Class removePrototypeClasses(Class main) = visit(main) {
  case prototype(_, _, _) => Prototype
};

// lifts local class declarations at newInstance locations to the top:
list[Class] extractPrototypeClasses(Class main) 
  = [ class(reference(name), methods=methods, fields=fields) 
    | /prototype(str name, methods, fields) := main];