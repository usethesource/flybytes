module lang::mujava::api::Object

import lang::mujava::Syntax;


// call toString() on an object      
Expression toString(Expression obj) 
   = invokeVirtual("java.lang.Object", obj, methodDesc(string(), "toString", []), []);

// call hashCode() on an object
Expression hashCode(Expression obj) 
   = invokeVirtual("java.lang.Object", obj, methodDesc(integer(), "hashCode", []), []);

// call equals(Object a) on an object   
Expression equals(Expression obj, Expression compared) 
   = invokeVirtual("\<current\>", obj, methodDesc(boolean(), "equals", [object()]), [compared]);