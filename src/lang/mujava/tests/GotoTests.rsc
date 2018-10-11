module lang::mujava::tests::GotoTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;


Class GotoClass1() 
  = class(reference("GotoClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [ 
          decl(integer(), "i", init=const(integer(), 0)),
          block("again",[
             \if (lt(load("i"), const(integer(), 10)), [
               incr("i", 1),
               \continue(label="again")
             ],[
               \break()
             ])
          ]),
          \return(eq(load("i"), const(integer(), 10)))          
        ])
      ]
    );
    
bool testGotoClass(Class c) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
} 

test bool breakContinueLabeledBlock() = testGotoClass(GotoClass1());

