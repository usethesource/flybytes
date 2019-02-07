module lang::flybytes::demo::protol::Compiler

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;
import lang::flybytes::demo::protol::Syntax;
import lang::flybytes::api::Object;
import lang::flybytes::api::System;
import ParseTree;
import String;

void testProtol() {
  //tree = parse(#start[Program], |project://flybytes/src/lang/flybytes/demo/protol/fact.protol|).top;
  //compileProgram(tree, "ProtolFactorial", |project://flybytes/generated|);
  //
  //tree = parse(#start[Program], |project://flybytes/src/lang/flybytes/demo/protol/inheritance.protol|).top;
  //compileProgram(tree, "ProtolInheritance", |project://flybytes/generated|);
  //
  //tree = parse(#start[Program], |project://flybytes/src/lang/flybytes/demo/protol/missing.protol|).top;
  //compileProgram(tree, "ProtolMissing", |project://flybytes/generated|);
  
  tree = parse(#start[Program], |project://flybytes/src/lang/flybytes/demo/protol/fields.protol|).top;
  compileProgram(tree, "ProtolFields", |project://flybytes/generated|);
}

private int prototypes = 0;
private str program = "Program";

str protoClass() {
  res = "Proto_<program>_<prototypes>";
  prototypes += 1;
  return res;
}

void compileProgram(Program p, str name, loc binFolder) {
  prototypes = 0;
  program = name;
  
  classes = compile(p, name);
  for (cl <- classes) {
    compileClass(cl, binFolder + "<cl.\type.name>.class", version=v1_8(), debugMode=true);
  }
}

// the Protol compiler translates object allocation sites to specific JVM class definitions,
// and uses invokeDynamic to call interfaces and set/get fields on objects of each 
// possible object in memory.

Type Prototype = object("lang.flybytes.demo.protol.Prototype");
Type Int       = object("lang.flybytes.demo.protol.Prototype$Int");
Type Str       = object("lang.flybytes.demo.protol.Prototype$Str");
Type Arr       = object("lang.flybytes.demo.protol.Prototype$Arr");

Signature Prototype_getInteger = methodDesc(integer(), "$get_integer", [Prototype]);
Signature Prototype_getArray = methodDesc(array(Prototype), "$get_array", [Prototype]);

Exp getInt(Exp rec) = invokeDynamic(bootstrap(Prototype, "bootstrap", []), Prototype_getInteger, [rec]);
Exp getArray(Exp rec) = invokeDynamic(bootstrap(Prototype, "bootstrap", []), Prototype_getArray, [rec]);

// an intermediate representation for prototype classes
data Type = prototype(str name, list[Method] methods, list[Field] fields);

list[Class] compile(Program p, str name) { 
  progClass = class(object(name),
      methods=[
        main("args", [*compile(p.commands), \return()])[src=p@\loc]
      ]
    )[src=p@\loc];
 
  allClasses = [removePrototypeClasses(progClass), *extractPrototypeClasses(progClass)];

  return declareVariables(allClasses);  
}    

list[Stat] compile(Command* commands) = [compile(c) | c <- commands];

Stat compile((Command) `<Id id> = <Expr v>;`)
  = store("<id>", compile(v), src=id@\loc);
  
Stat compile((Command) `<Expr obj>.<Id name> = <Expr v>;`)
  = \do(invokeDynamic(bootstrap(Prototype, "bootstrap", []), methodDesc(Prototype, "$set_<name>", [Prototype]), [compile(obj), compile(v)], src=name@\loc)); 

Exp PROTO() = getStatic(Prototype, Prototype, "PROTO");
  
Stat compile((Command) `<Expr array>[<Expr index>] = <Expr v>;`)
  = astore(compile(array), getInt(compile(index)), compile(v), src=array@\loc);

Stat compile((Command) `if(<Expr cond >) { <Command* thenPart> } else { <Command* elsePart> }`)
  = \if(compile(cond), compile(thenPart), compile(elsePart), src=cond@\loc);

Stat compile((Command) `while(<Expr cond>) { <Command* body> }`)
  = \while(compile(cond), compile(body), src=cond@\loc);   

Stat compile((Command) `<Expr e>;`) = \do(compile(e));

Stat compile(c:(Command) `return <Expr e>;`) = \return(compile(e))[src=c@\loc];

Stat compile(c:(Command) `print <Expr e>;`) = stdout(compile(e))[src=c@\loc];
 
Exp compile(e:(Expr) `this`) = load("this", src=e@\loc);
  
Exp compile((Expr) `<Expr rec>.<Id name>(<{Expr ","}* args>)`)
  = invokeDynamic(bootstrap(Prototype, "bootstrap", []), methodDesc(Prototype, "<name>", [Prototype]/*receiver*/ + [Prototype | _ <- args] ), [compile(rec), *compile(args) ], src=name@\loc);
  
list[Exp] compile({Expr ","}* args) = [compile(a) | a <- args];
   
Exp compile(x:(Expr) `[<{Expr ","}* elems>]`)
  = new(Arr, [array(Prototype)[src=x@\loc]], [newArray(array(Prototype), [compile(e) | e <- elems])])[src=x@\loc]; 

Exp compile((Expr) `<Expr receiver>.<Id name>`)
  = invokeDynamic(bootstrap(Prototype, "bootstrap", []), methodDesc(Prototype, "$get_<name>", []), [compile(receiver)], src=name@\loc);
  
Exp compile(x:(Expr) `new`) = new(Prototype, [Prototype], [PROTO()])[src=x@\loc];

Exp compile(x:(Expr) `new <Expr p>`) = new(Prototype, [Prototype], [compile(p)])[src=x@\loc];
     
Exp compile(x:(Expr) `new { <Definition* defs> }`)
  = new(prototype(protoClass(), methods(defs), fields(defs)), [Prototype], [PROTO()])[src=x@\loc];

Exp compile(x:(Expr) `new <Expr p> { <Definition* defs> }`) 
  = new(prototype(protoClass(), methods(defs), fields(defs)), [Prototype], [compile(p)])[src=x@\loc];
      
Exp compile((Expr) `(<Expr e>)`) = compile(e); 

Exp compile(x:(Expr) `<Id i>`) = load("<i>", src=x@\loc);

Exp compile(x:(Expr) `<Int i>`) = newInt(iconst(toInt("<i>"))[src=x@\loc])[src=x@\loc];
 
Exp compile(x:(Expr) `<String s>`) = new(Str, [string()], [sconst("<s>"[1..-1])[src=x@\loc]])[src=x@\loc];

Exp compile(x:(Expr) `<Expr a>[<Expr index>]`) 
  = aload(getArray(compile(a))[src=x@\loc], getInt(compile(index)), src=x@\loc);
  
Exp newInt(Exp e) = new(Int, [integer()], [e])[src=e@\loc];

Exp compile(Expr l, Expr r, Exp (Exp, Exp) op) 
  = op(getInt(compile(l)), getInt(compile(r)));
       
Exp compile(x:(Expr) `<Expr l> * <Expr r>`) 
  = newInt(compile(l, r, mul)[src=x@\loc]);
  
Exp compile(x:(Expr) `<Expr l> / <Expr r>`) 
  = newInt(compile(l, r, div)[src=x@\loc]);  

Exp compile(x:(Expr) `<Expr l> + <Expr r>`) 
  = newInt(compile(l, r, add)[src=x@\loc]);  

Exp compile(x:(Expr) `<Expr l> - <Expr r>`) 
  = newInt(compile(l, r, sub)[src=x@\loc]);  

Exp compile(x:(Expr) `<Expr l> == <Expr r>`) 
  = equals(compile(l), compile(r)[src=x@\loc]);
 
Exp compile(x:(Expr) `<Expr l> != <Expr r>`) 
  = neg(equals(compile(l), compile(r))[src=x@\loc]);

Exp compile(x:(Expr) `<Expr l> \<\< <Expr r>`)
  = invokeVirtual(Prototype, compile(l), methodDesc(Prototype, "concat", [Prototype]), [compile(r)])[src=x@\loc];
  
Exp compile(x:(Expr) `<Expr l> \<= <Expr r>`) 
  = compile(l, r, le)[src=x@\loc];
  
Exp compile(x:(Expr) `<Expr l> \< <Expr r>`) 
  = compile(l, r, lt)[src=x@\loc];
 
Exp compile(x:(Expr) `<Expr l> \> <Expr r>`) 
  = compile(l, r, gt)[src=x@\loc];     

Exp compile(x:(Expr) `<Expr l> \>= <Expr r>`) 
  = compile(l, r, ge)[src=x@\loc];    

list[Method] methods(Definition* defs) 
  = [ method("<name>", args, commands) 
    | (Definition) `<Id name>(<{Id ","}* args>) { <Command* commands> }` <- defs]
    +
    [ method("missing", missingArgs(name, args), commands)
    | (Definition) `missing(<Id name>, <Id args>) { <Command* commands> }` <- defs]
    +
    [ getter("<name>", name@\loc), setter("<name>", name@\loc) | (Definition) `<Id name> = <Expr val>` <- defs]
    ;

Method getter(str name, loc src) 
  = method(\public(), Prototype, "$get_<name>", [], [\return(getField(Prototype, "<name>")[src=src])]);

Method setter(str name, loc src)
  = method(\public(), \void(), "$set_<name>", [var(Prototype, "a")], [putField(Prototype, "<name>", load("a"))[src=src], \return()]);
   
{Id ","}* missingArgs(Id name, Id args)
  = ((Definition) `dummy(<Id name>, <Id args>) { }`).args;
  
list[Field] fields(Definition* defs)  
  = [ field("<name>", val)[src=name@\loc] | (Definition) `<Id name> = <Expr val>` <- defs];

Method method(str name, {Id ","}* args, Command* commands)
  = method(\public(), Prototype, name, [var(Prototype, "<a>") | a <- args], compile(commands));

Field field(str name, Expr val)
  = field(Prototype, name, init=compile(val), modifiers={\public()});
   
&T declareVariables(&T classes) 
  = visit(classes) {
      case method(desc, formals, block, modifiers=m) => 
           method(desc, formals, [*decls, *block], modifiers=m)  
      when 
        // transform assignments to declarations and remove duplicates:
        decls := { decl(Prototype, name) | /store(str name, _) := block}
  };
  
Class removePrototypeClasses(Class main) = visit(main) {
  case prototype(n, _, _) => object(n)
};

// lifts local class declarations at newInstance locations to the top:
list[Class] extractPrototypeClasses(Class main) 
  = [ class(object(name),
        super=Prototype, 
         methods=[*ms, 
           // public Class(Prototype proto) { super(proto); }
           constructor(\public(), [var(Prototype, "proto")], [
              invokeSuper([Prototype], [load("proto")]),
              \return()
           ])
         ], 
         fields=fs
      )[src=main.src] 
    | /prototype(str name, ms, fs) := main];