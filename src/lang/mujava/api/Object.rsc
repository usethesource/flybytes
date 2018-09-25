module lang::mujava::api::Object

import lang::mujava::Syntax;


// call toString() on an object      
Expression toString(Expression object) 
   = invokeVirtual("java.lang.Object", object, methodDesc(string(), "toString", []), []);

// call hashCode() on an object
Expression hashCode(Expression object) 
   = invokeVirtual("java.lang.Object", object, methodDesc(integer(), "hashCode", []), []);

// call equals(Object a) on an object   
Expression equals(Expression object, Expression compared) 
   = invokeVirtual("\<current\>", object, methodDesc(boolean(), "equals", [object()]), [compared]);