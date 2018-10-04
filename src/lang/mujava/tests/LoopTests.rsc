module lang::mujava::tests::LoopTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import Node;
  
Class forLoopClass() {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("ForLoopTestClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          // tmp = new Type[len];
          
          \for(
          [ // init block
             decl(array(integer()), "tmp", init=newArray(array(integer()), const(integer(), 10))),
             decl(integer(), "i", const(integer(), 0))
          ],
          
          // cond 
          lt(load("i"), alength(load("tmp"))),
          
          [ // next block
            store("i", add(load("i"), const(integer(), 1))) 
          ],
          
          [ // body
            astore(load("tmp"), load("i"), load("i"))
          ]
          ),
          
          // return true; 
          rt
        ])
      ]
    );
} 

bool testForClass(Class c) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
} 

