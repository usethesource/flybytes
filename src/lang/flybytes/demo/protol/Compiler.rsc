module lang::flybytes::demo::protol::Compiler

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;
import lang::flybytes::demo::protol::Syntax;
import lang::flybytes::api::Object;
import lang::flybytes::api::System;
import ParseTree;
import String;
import util::UUID;
import IO;

void testProtol() {
  tree = parse(#start[Program], |project://flybytes/src/lang/flybytes/demo/protol/fact.protol|).top;
  compileProgram(tree, "ProtolFactorial", |project://flybytes/generated|);
}

int prototypes = 0;

str protoClass() {
  res = "Proto_<prototypes>";
  prototypes += 1;
  return res;
}

void compileProgram(Program p, str name, loc binFolder) {
  prototypes = 0;
  
  classes = compile(p, name);
  for (cl <- classes) {
    compileClass(cl, binFolder + "<cl.\type.name>.class", version=v1_8());
  }
}

// the Protol compiler translates object allocation sites to specific JVM class definitions,
// and uses invokeDynamic to call interfaces and set/get fields on objects of each 
// possible object in memory.

Type Prototype = object("lang.flybytes.demo.protol.Prototype");
Type Int       = object("lang.flybytes.demo.protol.Prototype$Int");
Type Str       = object("lang.flybytes.demo.protol.Prototype$Str");
Type Arr       = object("lang.flybytes.demo.protol.Prototype$Arr");

// an intermediate representation for prototype classes
data Type = prototype(str name, list[Method] methods, list[Field] fields);

list[Class] compile(Program p, str name) { 
  progClass = class(object(name),
      methods=[
        main("args", [*compile(p.commands), \return()])
      ]
    );
 
  allClasses = [removePrototypeClasses(progClass), *extractPrototypeClasses(progClass)];

  return declareVariables(allClasses);  
}    

list[Stat] compile(Command* commands) = [compile(c) | c <- commands];

Stat compile((Command) `<Id id> = <Expr v>;`)
  = store("<id>", compile(v));
  
Stat compile((Command) `<Expr obj>.<Id name> = <Expr v>;`)
  = \do(invokeDynamic(bootstrap(Prototype, "bootstrap", []), methodDesc(Prototype, "$set_<name>", [Prototype]), [compile(obj), compile(v)])); 

Exp PROTO() = getStatic(Prototype, Prototype, "PROTO");
  
Stat compile((Command) `<Expr array>[<Expr index>] = <Expr v>;`)
  = astore(compile(array), getField(Str, compile(index), integer(), "integer"), compile(v));

Stat compile((Command) `if(<Expr cond >) { <Command* thenPart> } else { <Command* elsePart> }`)
  = \if(compile(cond), compile(thenPart), compile(elsePart));

Stat compile((Command) `while(<Expr cond>) { <Command* body> }`)
  = \while(compile(cond), compile(body));   

Stat compile((Command) `<Expr e>;`) = \do(compile(e));

Stat compile((Command) `return <Expr e>;`) = \return(compile(e));

Stat compile((Command) `print <Expr e>;`) = stdout(compile(e));
 
Exp compile((Expr) `this`) = load("this");
  
Exp compile((Expr) `<Expr rec>.<Id name>(<{Expr ","}* args>)`)
  = invokeDynamic(bootstrap(Prototype, "bootstrap", []), methodDesc(Prototype, "<name>", [Prototype]/*receiver*/ + [Prototype | _ <- args] ), [compile(rec), *compile(args) ]);
  
list[Exp] compile({Expr ","}* args) = [compile(a) | a <- args];
   
Exp compile((Expr) `[<{Expr ","}* elems>]`)
  = new(Arr, array(Prototype), [newArray(Prototype, compile(args))]); 

Exp compile((Expr) `<Expr receiver>.<Id name>`)
  = invokeDynamic(bootstrap(Prototype, "bootstrap", []), methodDesc(Prototype, "$get_<name>", []), []);
  
Exp compile((Expr) `new`) = new(Prototype, [Prototype], [PROTO()]);

Exp compile((Expr) `new <Expr p>`) = new(Prototype, [Prototype], [compile(p)]);
     
Exp compile((Expr) `new { <Definition* defs> }`)
  = new(prototype(protoClass(), methods(defs), fields(defs)), [Prototype], [PROTO()]);

Exp compile((Expr) `new <Expr p> { <Definition* defs> }`) 
  = new(prototype(protoClass(), methods(defs), fields(defs)), [Prototype], [compile(p)]);
      
Exp compile((Expr) `(<Expr e>)`) = compile(e); 

Exp compile((Expr) `<Id i>`) = load("<i>");

Exp compile((Expr) `<Int i>`) = newInt(iconst(toInt("<i>")));
 
Exp compile((Expr) `<String s>`) = new(Str, [string()], [sconst("<s>"[1..-1])]);

Exp compile((Expr) `<Expr a>[<Expr index>]`) 
  = aload(getField(Arr, compile(a), array(Prototype), "array"),
          compile(index));
  
Exp newInt(Exp e) = new(Int, [integer()], [e]);
     
Exp compile((Expr) `<Expr l> * <Expr r>`) 
  = newInt(compile(l, r, mul));
  
Exp compile((Expr) `<Expr l> / <Expr r>`) 
  = newInt(compile(l, r, div));  

Exp compile((Expr) `<Expr l> + <Expr r>`) 
  = newInt(compile(l, r, add));  

Exp compile((Expr) `<Expr l> - <Expr r>`) 
  = newInt(compile(l, r, sub));  

Exp compile(Expr l, Expr r, Exp (Exp, Exp) op) 
  = op(getField(Int, checkcast(compile(l), Int), integer(), "integer"),
       getField(Int, checkcast(compile(r), Int), integer(), "integer"));

Exp compile((Expr) `<Expr l> == <Expr r>`) 
  = equals(compile(l), compile(r));
 
Exp compile((Expr) `<Expr l> != <Expr r>`) 
  = neg(equals(compile(l), compile(r)));

Exp compile((Expr) `<Expr l> \<\< <Expr r>`)
  = invokeVirtual(Str, compile(l), methodDesc(Prototype, "concat", [Prototype]), [compile(r)]);
  
Exp compile((Expr) `<Expr l> \<= <Expr r>`) 
  = compile(l, r, le);
  
Exp compile((Expr) `<Expr l> \< <Expr r>`) 
  = compile(l, r, lt);
 
Exp compile((Expr) `<Expr l> \> <Expr r>`) 
  = compile(l, r, gt);     

Exp compile((Expr) `<Expr l> \>= <Expr r>`) 
  = compile(l, r, ge);    

list[Method] methods(Definition* defs) 
  = [ method("<name>", args, commands) 
    | (Definition) `<Id name>(<{Id ","}* args>) { <Command* commands> }` <- defs]
    +
    [ method("missing", missingArgs(name, args), commands)
    | (Definition) `missing(<Id name>, <Id args>) { <Command* commands> }` <- defs]
    +
    [ getter("<name>"), setter("<name>") | (Definition) `<Id name> = <Expr val>` <- defs]
    ;

Method getter(str name) 
  = method(\public(), Prototype, "$get_<name>", [], [\return(getField(Prototype, "<name>"))]);

Method setter(str name)
  = method(\public(), \void(), "$set_<name>", [var(Prototype, "a")], [putField(Prototype, "<name>", load("a")), \return()]);
   
{Id ","}* missingArgs(Id name, Id args)
  = ((Definition) `dummy(<Id name>, <Id args>) { }`).args;
  
list[Field] fields(Definition* defs)  
  = [ field("<name>", val) | (Definition) `<Id name> = <Expr val>` <- defs];

Method method(str name, {Id ","}* args, Command* commands)
  = method(\public(), Prototype, name, [var(Prototype, "<a>") | a <- args], compile(commands));

Field field(str name, Expr val)
  = field(Prototype, name, init=compile(val));
   
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

private Stat appendString(Exp s)
  = \do(invokeVirtual(object("java.lang.StringBuilder"), load("sb"), 
           methodDesc(object("java.lang.StringBuilder"), "append", [string()]), [s]));
           
private Stat appendObject(Exp e) = appendString(toString(e));

// lifts local class declarations at newInstance locations to the top:
list[Class] extractPrototypeClasses(Class main) 
  = [ class(object(name),
        super=Prototype, 
         methods=[*ms, 
           // public Class(Prototype proto) { super(proto); }
           constructor(\public(), [var(Prototype, "proto")], [
              invokeSuper([Prototype], [load("proto")]),
              \return()
           ]),
           // public String toString() { StringBuilder sb = new StringBuilder(); ...; return sb.toString(); }
           method(\public(), string(), "toString", [], [
              decl(object("java.lang.StringBuilder"), "sb", init=new(object("java.lang.StringBuilder"))),
              appendString(sconst("{\n")),
              *[  appendString(sconst("  ")), 
                  appendString(sconst(f.name)), 
                  appendString(sconst(" = ")), 
                  appendObject(getField(Prototype, f.name)), 
                  appendString(sconst("\n")) 
               | f <- fs],
              appendString(sconst("}")),
              \return(toString(load("sb")))
           ])
         ], 
         fields=fs
      ) 
    | /prototype(str name, ms, fs) := main];