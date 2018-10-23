module lang::flybytes::api::Array

import lang::flybytes::Syntax;

// index an array variable using a constant  
Exp index(str array, int index)
   = index(array, iconst(index));
   
// index an array variable using the result of an expression as index
Exp index(str array, Exp index)
   = aaload(load(array), index); 
   
Exp toString(Type elemType, Exp array)
   = invokeStatic(object("java.util.Arrays"), methodDesc(string(), "toString", [array(elemType)]), [array]);
