@doc{
.Synopsis Provides a native interface to Java objects via class and object reflection.

.Description

Using this Mirror representation you can test generated class files by loading the class
and executing static methods on the classes, getting static fields, allocating new instances,
calling methods on these instances, etc. There is also support for native arrays.
}
module lang::mujava::Mirror

import lang::mujava::Syntax;

data Mirror
  = class(str class, 
        Mirror (Signature method, list[Mirror] args) invokeStatic,
        Mirror (str name) getStatic,
        Mirror (Signature constructor, list[Mirror] args) newInstance)
  | object(Mirror classMirror, 
        Mirror (Signature method, list[Mirror] args) invoke,
        Mirror (str name) getField,
        &T  (type[&T] expect) toValue)
  | array(Mirror () length,
        Mirror (int index) load,
        void   (int index, Mirror object) store)
  | \null()
  ;
              
@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
@memo
@doc{reflects a Rascal value as a JVM object Mirror}
java Mirror val(value v);

@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
@memo
@doc{reflects a Rascal value as a JVM object Mirror}
java Mirror classMirror(str name);

@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
@doc{creates a mirrored array}
java Mirror array(list[Mirror] elems);

Mirror vals(list[value] elems) = array([val(e) | e <- elems]);
  
str toString(Mirror m:object(_, _, _, _)) = m.invoke(methodDesc(string(),"toString", []), []).toValue(#str);
str toString(class(str name,_,_,_)) = name; 
str toString(null()) = "<null>";
str toString(Mirror m:array(_,_,_)) = "array[<m.length()>]";              
   
Mirror \integer(int v)
  = val(v).invoke(methodDesc(integer(), "intValue", []), []);
  
Mirror \long(int v)
  = val(v).invoke(methodDesc(integer(), "longValue", []), []);
  
Mirror \byte(int v)
  = classMirror("java.lang.Byte").invokeStatic(methodDesc(byte(), "parseByte", [string()]), [\string("<v>")]);  

Mirror \short(int v)
  = classMirror("java.lang.Short").invokeStatic(methodDesc(byte(), "parseShort", [string()]), [\string("<v>")]);  

Mirror \string(str v)
  = val(v).invoke(methodDesc(string(), "getValue", []), []);
  
Mirror \double(real v)
  = val(v).invoke(methodDesc(string(), "doubleValue", []), []);
  
Mirror \float(real v)
  = val(v).invoke(methodDesc(string(), "floatValue", []), []); 
   
Mirror native(list[value] vs) = array([native(v) | v <- vs]);
                