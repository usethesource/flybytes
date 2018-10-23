module lang::mujava::tests::ArrayTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import Node;
  
Class primArrayTestClass(Type t, int len) {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(object("PrimArrayTestClass_<getName(t)>_<len>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          // Type[] tmp = new Type[len];
          decl(array(t), "tmp", init=newArray(array(t), iconst(len))),
           
          // fail if tmp.length != len
          \if(ne(iconst(len), alength(load("tmp"))), [rf]),
           
          // fail if (tmp[0] != def)
          *[\if(ne(defVal(t), aload(load("tmp"), iconst(0))),[rf]) | len > 0, _ <- [0..1]],
           
          // generate `len` store instructions: tmp[i] = i;
          *[astore(load("tmp"), iconst(I), const(t, I)) | I <- [0..len]],
           
          // see if that worked by indexing into the array: if (tmp[i] != i)
          *[\if(ne(aload(load("tmp"), iconst(I)), const(t, I)), [rf]) | I <- [0..len]],
          
          // return true; 
          rt
        ])
      ]
    );
} 

Exp defVal(integer()) = iconst(0);
Exp defVal(long()) = jconst(0);
Exp defVal(byte()) = bconst(0);
Exp defVal(character()) = cconst(0);
Exp defVal(short()) = sconst(0);
Exp defVal(object(str _)) = null();
Exp defVal(array(Type _)) = null();
Exp defVal(string()) = null();
Exp defVal(boolean()) = \false();

list[Type] primTypes = [integer(), short(), byte(), character(), long()];

bool testArrayClass(Class c) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
} 

test bool primitiveArrays10() 
  = all( t <- primTypes, testArrayClass(primArrayTestClass(t, 10)));
  
test bool primitiveArrays0() 
  = all( t <- primTypes, testArrayClass(primArrayTestClass(t, 0)));
 
test bool primitiveArrays1() 
  = all( t <- primTypes, testArrayClass(primArrayTestClass(t, 1)));
  
Class valArrayTestClass(Type t, int len, Exp val) {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(object("ValArrayTestClass_<getName(t)>_<len>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          decl(array(t), "tmp"),
          // tmp = new Type[len];
          store("tmp", newArray(array(t), const(integer(), len))),
           
          // fail if tmp.length != len
          \if(ne(const(integer(), len), alength(load("tmp"))), [rf]),
           
          // fail if (tmp[0] != def)
          *[\if(ne(defVal(t), aload(load("tmp"), iconst(0))),[rf]) | len > 0, _ <- [0..1]],
           
          // generate `len` store instructions: tmp[i] = i;
          *[astore(load("tmp"), iconst(I), val) | I <- [0..len]],
           
          // see if that worked by indexing into the array: if (tmp[i] != i)
          *[\if(ne(aload(load("tmp"), iconst(I)), val), [rf]) | I <- [0..len]],
          
          // return true; 
          rt
        ])
      ]
    );
}  

test bool boolArrayTrue1() 
  = testArrayClass(valArrayTestClass(boolean(), 1, \true()));
  
test bool boolArrayFalse1() 
  = testArrayClass(valArrayTestClass(boolean(), 1, \false()));  

test bool boolArrayFalse10() 
  = testArrayClass(valArrayTestClass(boolean(), 10, \false()));
 
