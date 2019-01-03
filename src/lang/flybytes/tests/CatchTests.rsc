module lang::flybytes::tests::CatchTests

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;
import lang::flybytes::Mirror;

Class catchClass() {
  return class(object("CatchTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           \try([
             do(div(iconst(1), iconst(0))),
             \return(\false())
           ],
           [ // Type \type, str name, list[Stat] block
             \catch(object("java.lang.ArithmeticException"), "e", [
               \return(\true())
             ])
           ]
           ),
           \return(\false())
        ])
      ]
    );
}

test bool testCatch() = loadClass(catchClass(), file=just(|project://flybytes/generated/CatchTest.class|))
  .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
  
Class multipleCatchClass() {
  return class(object("MultipleCatchTest"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(boolean(), "switch")], [
           \try([
             \if (load("switch"), [
               \do(div(iconst(1), iconst(0)))
             ],[
               \throw(new(object("java.lang.IllegalArgumentException")))
             ]),
             \return(const(boolean(), false))
           ],
           [ // Type \type, str name, list[Stat] block
             \catch(object("java.lang.ArithmeticException"), "e", [
               \return(iconst(1))
             ]),
             \catch(object("java.lang.IllegalArgumentException"), "f", [
               \return(iconst(2))
             ])
           ]
           ),
           \return(\false())
        ])
      ]
    );
}

test bool multipleTestCatch() {
  m = loadClass(multipleCatchClass(), file=just(|project://flybytes/generated/MultipleCatchTest.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", [boolean()]), [boolean(true)]).toValue(#int) == 1
      && m.invokeStatic(methodDesc(boolean(), "testMethod", [boolean()]), [boolean(false)]).toValue(#int) == 2;
}

Class finallyClass() {
  return class(object("FinallyTest"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [], [
           \try([
             do(div(iconst(1), iconst(0))),
             \return(iconst(1))
           ],
           [ 
             \catch(object("java.lang.ArithmeticException"), "e", [
               \return(iconst(2))
             ]),
             \finally([
               \return(iconst(3))
             ])
           ]
           ),
           \return(iconst(4))
        ])
      ]
    );
}


test bool finallyTest() {
  m = loadClass(finallyClass(), file=just(|project://flybytes/generated/FinallyTest.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#int) == 3 /*should be 3 when return supported finally */;
}

Class finallyContinueClass() {
  return class(object("FinallyContinueTest"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [], [
           decl(integer(), "j", init=iconst(0)),
           \for([decl(integer(), "i" ,init=iconst(0))], // init
                lt(load("i"), iconst(10)), // cond
                [incr("i", 1)], // next
                
                // loop body
                [ 
                  \try([
                    \continue() // loop again, but go past the finally block first!
                  ],[
                    \finally([ // finally
                      incr("j", 1)
                    ])
                  ])
                ]
                ),
           \return(load("j"))
        ])
      ]
    );
}


test bool finallyContinueTest() {
  m = loadClass(finallyContinueClass(), debugMode=false, file=just(|project://flybytes/generated/FinallyContinueTest.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#int) == 10 /*should be 3 when return supported finally */;
}

Class finallyBreakClass() {
  return class(object("FinallyBreakTest"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [], [
           decl(integer(), "j", init=iconst(0)),
           \for([decl(integer(), "i" ,init=iconst(0))], // init
                lt(load("i"), iconst(10)), // cond
                [incr("i", 1)], // next
                
                // loop body
                [ 
                  \try([
                    \break() // loop again, but go past the finally block first!
                  ],
                  [
                    \finally([
                      incr("j", 1)
                    ])
                  ])
                 ]
                ),
           \return(load("j"))
        ])
      ]
    );
}


test bool finallyBreakTest() {
  m = loadClass(finallyBreakClass(), debugMode=false, file=just(|project://flybytes/generated/FinallyBreakTest.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#int) == 1;
}
  
