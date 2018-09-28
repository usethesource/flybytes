module lang::mujava::tests::ArrayTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import Node;
  
Class primArrayTestClass(Type t, int len) {
  rf = \return(boolean(), \false());
  rt = \return(boolean(), \true());
  
  return class(classType("PrimArrayTestClass_<getName(t)>_<len>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        block([var(array(t), "tmp")],
        [
          // tmp = new Type[len];
          store("tmp", newArray(t, const(integer(), len))),
           
          // fail if tmp.length != len
          //\if(ne(t, const(integer(), len), alength(load("tmp"))), [rf]),
           
          // fail if (tmp[0] != def)
          //\if(ne(t, defVal(t), aaload(load("tmp"), const(integer(), 0))),[rf]),
           
          // generate `len` store instructions: tmp[i] = i;
          *[aastore(t, load("tmp"), const(integer(), I), const(integer(), I)) | I <- [0..len]],
           
          // see if that worked by indexing into the array: if (tmp[i] != i)
          *[\if(ne(t, aaload(integer(), load("tmp"), const(integer(), I)), const(integer(), I)), [rf]) | I <- [0..len]],
          
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
Expression defVal(classType(str _)) = null();
Expression defVal(array(Type _)) = null();
Expression defVal(string()) = null();
 