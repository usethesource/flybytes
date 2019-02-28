module lang::flybytes::api::Array

import lang::flybytes::Syntax;

// index an array variable using a constant  
Exp index(str array, int i)
   = index(array, iconst(i));  
   
// index an array variable using the result of an expression as index
Exp index(str array, Exp i)
   = aload(load(array), i); 
   
Exp toString(Type elemType, Exp array)
   = invokeStatic(object("java.util.Arrays"), methodDesc(string(), "toString", [Type::array(elemType)]), [array]);
