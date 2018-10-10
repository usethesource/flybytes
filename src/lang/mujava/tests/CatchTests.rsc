module lang::mujava::tests::CatchTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import lang::mujava::Mirror;

Class catchClass() {
  return class(reference("CatchTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           \try([
             do(div(const(integer(),1), const(integer(), 0))),
             \return(const(boolean(), false))
           ],
           [ // Type \type, str name, list[Stat] block
             \catch(reference("java.lang.ArithmeticException"), "e", [
               \return(const(boolean(), true))
             ])
           ],
           [
             // finally
           ]
           ),
           \return(const(boolean(), false))
        ])
      ]
    );
}

test bool testCatch() = loadClass(catchClass(), file=just(|project://mujava/generated/CatchTest.class|))
  .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
  
Class multipleCatchClass() {
  return class(reference("MultipleCatchTest"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(boolean(), "switch")], [
           \try([
             \if (load("switch"), [
               \do(div(const(integer(),1), const(integer(), 0)))
             ],[
               \throw(new(reference("java.lang.IllegalArgumentException")))
             ]),
             \return(const(boolean(), false))
           ],
           [ // Type \type, str name, list[Stat] block
             \catch(reference("java.lang.ArithmeticException"), "e", [
               \return(const(integer(), 1))
             ]),
             \catch(reference("java.lang.IllegalArgumentException"), "f", [
               \return(const(integer(), 2))
             ])
           ],
           [
             // finally
           ]
           ),
           \return(const(boolean(), false))
        ])
      ]
    );
}

test bool multipleTestCatch() {
  m = loadClass(multipleCatchClass(), file=just(|project://mujava/generated/MultipleCatchTest.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", [boolean()]), [boolean(true)]).toValue(#int) == 1
      && m.invokeStatic(methodDesc(boolean(), "testMethod", [boolean()]), [boolean(false)]).toValue(#int) == 2;
}

Class finallyClass() {
  return class(reference("FinallyTest"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [], [
           \try([
             do(div(const(integer(),1), const(integer(), 0))),
             \return(const(integer(), 1))
           ],
           [ // Type \type, str name, list[Stat] block
             \catch(reference("java.lang.ArithmeticException"), "e", [
               \return(const(integer(), 2))
             ])
           ],
           [ // finally
             \return(const(integer(), 3))
           ]
           ),
           \return(const(integer(), 4))
        ])
      ]
    );
}


test bool finallyTest() {
  m = loadClass(finallyClass(), file=just(|project://mujava/generated/MultipleCatchTest.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#int) == 2 /*should be 3 when return supported finally */;
}
  