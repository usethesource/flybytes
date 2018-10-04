module lang::mujava::tests::ArrayTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import Node;
  
Class primArrayTestClass(Type t, int len) {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("PrimArrayTestClass_<getName(t)>_<len>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        block([var(array(t), "tmp")],
        [
          // tmp = new Type[len];
          store("tmp", newArray(array(t), const(integer(), len))),
           
          // fail if tmp.length != len
          \if(ne(const(integer(), len), alength(load("tmp"))), [rf]),
           
          // fail if (tmp[0] != def)
          *[\if(ne(defVal(t), aload(load("tmp"), const(integer(), 0))),[rf]) | len > 0, _ <- [0..1]],
           
          // generate `len` store instructions: tmp[i] = i;
          *[astore(load("tmp"), const(integer(), I), const(t, I)) | I <- [0..len]],
           
          // see if that worked by indexing into the array: if (tmp[i] != i)
          *[\if(ne(aload(load("tmp"), const(integer(), I)), const(t, I)), [rf]) | I <- [0..len]],
          
          // return true; 
          rt
        ]))
      ]
    );
} 

Expression defVal(integer()) = const(integer(), 0);
Expression defVal(long()) = const(long(), 0);
Expression defVal(byte()) = const(byte(), 0);
Expression defVal(character()) = const(character(), 0);
Expression defVal(short()) = const(short(), 0);
Expression defVal(reference(str _)) = null();
Expression defVal(array(Type _)) = null();
Expression defVal(string()) = null();
Expression defVal(boolean()) = const(boolean(), false);

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
  
Class valArrayTestClass(Type t, int len, Expression val) {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("ValArrayTestClass_<getName(t)>_<len>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        block([var(array(t), "tmp")],
        [
          // tmp = new Type[len];
          store("tmp", newArray(array(t), const(integer(), len))),
           
          // fail if tmp.length != len
          \if(ne(const(integer(), len), alength(load("tmp"))), [rf]),
           
          // fail if (tmp[0] != def)
          *[\if(ne(defVal(t), aload(load("tmp"), const(integer(), 0))),[rf]) | len > 0, _ <- [0..1]],
           
          // generate `len` store instructions: tmp[i] = i;
          *[astore(load("tmp"), const(integer(), I), val) | I <- [0..len]],
           
          // see if that worked by indexing into the array: if (tmp[i] != i)
          *[\if(ne(aload(load("tmp"), const(integer(), I)), val), [rf]) | I <- [0..len]],
          
          // return true; 
          rt
        ]))
      ]
    );
}  

test bool boolArrayTrue1() 
  = testArrayClass(valArrayTestClass(boolean(), 1, \true()));
  
test bool boolArrayFalse1() 
  = testArrayClass(valArrayTestClass(boolean(), 1, \false()));  

test bool boolArrayFalse10() 
  = testArrayClass(valArrayTestClass(boolean(), 10, \false()));
 