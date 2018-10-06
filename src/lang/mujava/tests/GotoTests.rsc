module lang::mujava::tests::GotoTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;


Class GotoClass1() 
  = class(reference("GotoClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [ // the ASM assembler chokes on dead code, so this is the simplest
          // test for goto which has no dead code in it.
          goto("skip"),
          label("back"),
          goto("done"),          
          label("skip"),
          goto("back"),
          label("done"),
          \return(\true())          
        ])
      ]
    );
    
Class JumpIntoLoop() 
  = class(reference("JumpIntoLoop"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
         // tmp = new Type[len];
          decl(array(integer()), "tmp", init=newArray(array(integer()), const(integer(), 10))),
          decl(integer(), "i", init=const(integer(), 0)),
            
          // tmp[0] = 66;  
          astore(load("tmp"), const(integer(), 0), const(integer(), 66)),   
          goto("middle"), // would skip the first iteration
             
          \for([],
          
          // cond 
          lt(load("i"), alength(load("tmp"))),
          
          [ // next block
            store("i", add(load("i"), const(integer(), 1))) 
          ],
          
          [ // body
            astore(load("tmp"), load("i"), load("i")),
            label("middle")
          ]
          ),
          
          // tmp[0] == 66 instead of 0
          \return(eq(aload(load("tmp"), const(integer(), 0)), const(integer(), 66)))
        ])
      ]
    );
    
bool testGotoClass(Class c) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
} 

test bool gotoSkip() = testGotoClass(GotoClass1());

test bool goIntoLoopBody() = testGotoClass(JumpIntoLoop());

