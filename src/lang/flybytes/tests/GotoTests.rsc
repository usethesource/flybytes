module lang::flybytes::tests::GotoTests

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;


Class GotoClass1() 
  = class(object("GotoClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [ 
          decl(integer(), "i", init=iconst(0)),
          block([
             \if (lt(load("i"), iconst(10)), [
               incr("i", 1),
               \continue(label="again")
             ],[
               \break()
             ])
          ],label="again"),
          \return(eq(load("i"), iconst(10)))          
        ])
      ]
    );
    
bool testGotoClass(Class c) { 
  m = loadClass(c, file=just(|project://flybytes/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
} 

test bool breakContinueLabeledBlock() = testGotoClass(GotoClass1());

