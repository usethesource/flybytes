@license{Copyright (c) 2019-2022, NWO-I Centrum Wiskunde & Informatica (CWI) 
All rights reserved. 
 
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: 
 
1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
  
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
}
@contributor{Jurgen J. Vinju}
@doc{
.Synopsis Provides a native interface to Java objects via class and object reflection.

.Description

Using this Mirror representation you can test generated class files by loading the class
and executing static methods on the classes, getting static fields, allocating new instances,
calling methods on these instances, etc. There is also support for native arrays.
}
module lang::flybytes::Mirror

import lang::flybytes::Syntax;

data Mirror
  = class(str class, 
        Mirror (Signature method, list[Mirror] args) invokeStatic,
        Mirror (str name) getStatic,
        Mirror (Signature constructor, list[Mirror] args) newInstance,
        Mirror (Type \type) getAnnotation)
  | object(Mirror classMirror, 
        Mirror (Signature method, list[Mirror] args) invoke,
        Mirror (str name) getField,
        &T  (type[&T] expect) toValue)
  | array(int () length,
        Mirror (int index) load)
  | \null()
  ;
              
@javaClass{lang.flybytes.internal.ClassCompiler}
@memo
@doc{reflects a Rascal value as a JVM object Mirror}
java Mirror val(value v);

@javaClass{lang.flybytes.internal.ClassCompiler}
@memo
@doc{reflects a JVM class object as Mirror class}
java Mirror classMirror(str name);

@javaClass{lang.flybytes.internal.ClassCompiler}
@doc{creates a mirrored array}
java Mirror array(Type \type, list[Mirror] elems);

@javaClass{lang.flybytes.internal.ClassCompiler}
@doc{creates a mirrored array}
java Mirror array(Type \type, int length);

str toString(Mirror m:object(_, _, _, _)) = m.invoke(methodDesc(string(),"toString", []), []).toValue(#str);
str toString(class(str name, _, _, _, _)) = name;
str toString(null()) = "\<null\>";
str toString(Mirror m:array(_, _)) = "array[<m.length()>]";              
   
Mirror integer(int v)
  = val(v).invoke(methodDesc(integer(), "intValue", []), []);
  
Mirror long(int v)
  = val(v).invoke(methodDesc(integer(), "longValue", []), []);
  
Mirror byte(int v)
  = classMirror("java.lang.Byte").invokeStatic(methodDesc(byte(), "parseByte", [string()]), [\string("<v>")]);  

Mirror short(int v)
  = classMirror("java.lang.Short").invokeStatic(methodDesc(byte(), "parseShort", [string()]), [\string("<v>")]);  

Mirror character(int v)
  = classMirror("java.lang.Character").invokeStatic(methodDesc(array(character()), "toChars", [integer()]), [\integer(v)]).load(0); 

Mirror string(str v)
  = val(v).invoke(methodDesc(string(), "getValue", []), []);
  
Mirror double(real v)
  = val(v).invoke(methodDesc(string(), "doubleValue", []), []);
  
Mirror float(real v)
  = val(v).invoke(methodDesc(string(), "floatValue", []), []);
  
Mirror boolean(bool v)
  = val(v).invoke(methodDesc(string(), "getValue", []), []);  

Mirror prim(integer(), int t) = integer(t);
Mirror prim(short(), int t) = short(t);
Mirror prim(byte(), int t) = byte(t);
Mirror prim(long(), int t) = long(t);
Mirror prim(double(), real t) = double(t);
Mirror prim(float(), real t) = float(t);
Mirror prim(string(), str t) = string(t); 
Mirror prim(character(), int t) = character(t); 
Mirror prim(boolean(), bool t) = boolean(t); 


int integer(Mirror i) = i.toValue(#int);
int long(Mirror l) = l.toValue(#int);
int byte(Mirror b) = b.toValue(#int);
int short(Mirror s) = s.toValue(#int);
str string(Mirror s) = s.toValue(#str);
real double(Mirror d) = d.toValue(#real);
real float(Mirror f) = f.toValue(#real);
int character(Mirror f) = f.toValue(#int);
bool boolean(Mirror f) = f.toValue(#bool);
  
