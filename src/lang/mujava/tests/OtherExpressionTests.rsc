module lang::mujava::tests::OtherExpressionTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;

Class incExpClass() {
  return class(object("IncTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           decl(integer(), "i", init=iconst(1)),
           do(inc("i", 1)),
           \return(eq(load("i"), iconst(2)))
        ])
      ]
    );
}

test bool testInc() = loadClass(incExpClass())
  .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
  
Class incStatClass() {
  return class(object("IncTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           decl(integer(), "i", init=iconst(1)),
           incr("i", 1),
           \return(eq(load("i"), iconst(2)))
        ])
      ]
    );
}

test bool testIncStat() = loadClass(incStatClass())
  .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);  