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
          
          
          \for(
          [ // init block
             // Type[] tmp = new Type[len];
             decl(array(integer()), "tmp", init=newArray(array(integer()), iconst(10))),
             // int i = 0;
             decl(integer(), "i", init=iconst(0))
          ],
          
          // i < tmp.length 
          lt(load("i"), alength(load("tmp"))),
          
          [ // i = i + 1
            store("i", add(load("i"), iconst(1))) 
          ],
          
          [ // tmp[i] = i;
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

Class NestedFor() {
  return class(reference("NestedForClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          \for(
          [ // int i = 0, k = 0
             decl(integer(), "i", init=iconst(0)),
             decl(integer(), "k", init=iconst(0))
          ],
          
          // cond: i < 10
          lt(load("i"), iconst(10)),
          
          [ // next: i = i + 1
            store("i", add(load("i"), iconst(1))) 
          ],
          
          [ 
            \for(
            [ // int j = 0
                decl(integer(), "j", init=iconst(0))
            ],
          
            // cond: j < 10
            lt(load("j"), iconst(10)),
          
            [ // next: j = j + 1
              store("j", add(load("j"), iconst(1))) 
            ],
          
            [ 
              // k = k + 1 
              store("k", add(load("k"), iconst(1)))
            ]
            )
          ]
          ),
          
          // return k == 100; 
          \return(eq(load("k"), iconst(100)))
        ])
      ]
    );
} 

test bool testNestedFor() = testForClass(NestedFor());

Class forLoopBreakClass() {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("ForLoopBreakClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          \for(
          [ // int = 0;
             decl(integer(), "i", init=iconst(0))
          ],
          
          // cond: i < 10
          lt(load("i"), iconst(10)),
          
          [ // next: i = i + 1
            store("i", add(load("i"), iconst(1))) 
          ],
          
          [ // if (i == 5) break;
            \if (eq(load("i"), iconst(5)),[ 
              \break()
            ])
          ]
          ),
          
          // return i == 5; 
          \return(eq(load("i"), iconst(5)))
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
          \for(
          [ // int i = 0, j = 0;
             decl(integer(), "i", init=iconst(0)),
             decl(integer(), "j", init=iconst(0))
          ],
          
          // cond: i < 10
          lt(load("i"), iconst(10)),
          
          [ // next: i = i + 1
            store("i", add(load("i"), iconst(1))) 
          ],
          
          [ // if (i % 2 == 0) break;
            \if (eq(rem(load("i"), iconst(2)), iconst(0)),[ 
              \continue()
            ]),
            // j = j + 1; // count uneven numbers between 0 and 9
            store("j", add(load("j"), iconst(1)))
          ]
          ),
          
          // return j == 5; (0, 2, 4, 6, and 8 ) 
          \return(eq(load("j"), iconst(5)))
        ])
      ]
    );
} 

test bool testForContinue() = testForClass(forLoopContinueClass());

Class forLoopBreakNestedClass() {
  return class(reference("ForLoopBreakNestedClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          // int k = 0
           decl(integer(), "k", init=iconst(0)),
          \for(
          [ // int i = 0
             decl(integer(), "i", init=iconst(0))
          ],
          
          // cond: i < 10
          lt(load("i"), iconst(10)),
          
          [ // next: i = i + 1
            store("i", add(load("i"), iconst(1))) 
          ],
          
          [ 
            \for(
            [ // int j = 0
                decl(integer(), "j", init=iconst(0))
            ],
          
            // cond: j < 10
            lt(load("j"), iconst(10)),
          
            [ // next: j = j + 1
              store("j", add(load("j"), iconst(1))) 
            ],
          
            [ // if (i == 5) break outer;
              \if(eq(load("i"), iconst(5)), [
                \break(label="outer")
              ]),
              
              // k = k + 1 
              store("k", add(load("k"), iconst(1)))
            ],
            label="inner"
            ),
            
            // k = k + 1 (should be skipped after break(label=outer) above)
            store("k", add(load("k"), iconst(1)))
          ],
          label="outer"
          ),
          
          // return k == 55; 
          \return(eq(load("k"), iconst(55)))
        ])
      ]
    );
} 

test bool testBreakNested() = testForClass(forLoopBreakNestedClass());


Class whileClass() {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("WhileClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          decl(integer(), "i", init=iconst(0)),
          \while(lt(load("i"), iconst(10)),
          [ 
             incr("i", 1)
          ]),
          
          \return(\eq(load("i"), iconst(10)))
        ])
      ]
    );
} 

test bool testNormalDoWhile() = testForClass(whileClass());

Class doWhileClass() {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("DoWhileClass"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          decl(integer(), "i", init=iconst(0)),
          decl(integer(), "j", init=iconst(1)),
          \doWhile([ 
             incr("i", 1),
             store("j", mul(load("j"), iconst(2)))
          ],
          lt(load("i"), iconst(3))
          ),
          
          \return(\eq(load("j"), iconst(8)))
        ])
      ]
    );
} 

test bool testNormalDoWhile() = testForClass(doWhileClass());