module lang::mujava::api::Array

import lang::mujava::Syntax;

// index an array variable using a constant  
Exp index(str array, int index)
   = index(array, iconst(index));
   
// index an array variable using the result of an expression as index
Exp index(str array, Exp index)
   = aaload(load(array), index); 
   
Exp toString(Type elemType, Exp array)
   = invokeStatic(object("java.util.Arrays"), methodDesc(string(), "toString", [array(elemType)]), [array]);