module lang::mujava::api::Object

import lang::mujava::Syntax;


// call toString() on an object      
Exp toString(Exp obj) 
   = invokeVirtual(object(), obj, methodDesc(string(), "toString", []), []);

// call hashCode() on an object
Exp hashCode(Exp obj) 
   = invokeVirtual(object(), obj, methodDesc(integer(), "hashCode", []), []);

// call equals(Object a) on an object   
Exp equals(Exp obj, Exp compared) 
   = invokeVirtual(object(), obj, methodDesc(boolean(), "equals", [object()]), [compared]);