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
             decl(integer(), "i", init=const(integer(), 0))
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

test bool testNormalFor() = testForClass(forLoopClass());

Class forLoopBreakClass() {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("ForLoopBreakClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          // tmp = new Type[len];
          
          \for(
          [ // init block
             decl(integer(), "i", init=const(integer(), 0))
          ],
          
          // cond: i < 10
          lt(load("i"), const(integer(), 10)),
          
          [ // next: i = i + 1
            store("i", add(load("i"), const(integer(), 1))) 
          ],
          
          [ // if (i == 5) break;
            \if (eq(load("i"), const(integer(), 5)),[ 
              \break()
            ])
          ]
          ),
          
          // return i == 5; 
          \return(eq(load("i"), const(integer(), 5)))
        ])
      ]
    );
} 

test bool testBreakFor() = testForClass(forLoopBreakClass());

Class forLoopContinueClass() {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("ForLoopContinueClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          // tmp = new Type[len];
          
          \for(
          [ // init block
             decl(integer(), "i", init=const(integer(), 0)),
             decl(integer(), "j", init=const(integer(), 0))
          ],
          
          // cond: i < 10
          lt(load("i"), const(integer(), 10)),
          
          [ // next: i = i + 1
            store("i", add(load("i"), const(integer(), 1))) 
          ],
          
          [ // if (i % 2 == 0) break;
            \if (eq(rem(load("i"), const(integer(), 2)), const(integer(), 0)),[ 
              \continue()
            ]),
            // j = j + 1; // count uneven numbers between 0 and 9
            store("j", add(load("j"), const(integer(), 1)))
          ]
          ),
          
          // return j == 5; (0, 2, 4, 6, and 8 ) 
          \return(eq(load("j"), const(integer(), 5)))
        ])
      ]
    );
} 

test bool testBreakContinue() = testForClass(forLoopContinueClass());