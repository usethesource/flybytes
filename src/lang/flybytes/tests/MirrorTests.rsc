module lang::flybytes::tests::MirrorTests

import lang::flybytes::Mirror;
import lang::flybytes::api::JavaLang;
import lang::flybytes::Syntax;

test bool intId(int v) = v % maxIntValue(integer()) == integer(integer(v % maxIntValue(integer())));
test bool longId(int v) = v % maxIntValue(long()) == long(long(v % maxIntValue(long())));
test bool byteId(int v) = v % maxIntValue(byte()) == byte(byte(v % maxIntValue(byte())));
test bool shortId(int v) = v % maxIntValue(short()) == short(short(v % maxIntValue(short())));  
test bool stringId(str x) = x == string(string(x));

// due to loss of precision we can not have a general isomorphism between reals and doubles
test bool doubleId() {
   for (d <- [1.0,1.5..10.0]) {
     if (double(double(d)) != d) {
       return false;
     }
   }
   return true;
}

// due to loss of precision we can not have a general isomorphism between reals and floats
test bool floatId() {
   for (f <- [1.0,1.5..10.0]) {
     if (float(float(f)) != f) {
       return false;
     }
   }
   return true;
}


test bool arrayMirror(list[int] v) {
  a = array(integer(), [integer(e mod 1000) | e <- v]);
 
  int i = a.length() - 1;
  while (i >= 0) {
    if (a.load(i).toValue(#int) mod 1000 != v[i] mod 1000) {
      return false;
    }
    i -= 1;
  } 
  
  return true;
}

test bool floatStatic()
  = classMirror("java.lang.Float").getStatic("MAX_VALUE").toValue(#real) == 3.4028235E38;
  
test bool doubleStatic()
  = classMirror("java.lang.Double").getStatic("MAX_VALUE").toValue(#real) == 1.7976931348623157E308;
  
test bool longStatic()
  = classMirror("java.lang.Long").getStatic("MAX_VALUE").toValue(#int) == 9223372036854775807;
    
test bool intStatic()
  = classMirror("java.lang.Integer").getStatic("MAX_VALUE").toValue(#int) == 2147483647;

test bool newInstance()
  = classMirror("java.lang.Integer").newInstance(constructorDesc([string()]), [string("12")]).toValue(#int) == 12;

test bool invokeStatic()
  = classMirror("java.lang.Integer").invokeStatic(methodDesc(integer(), "parseInt", [string()]), [string("100")]).toValue(#int) == 100;

test bool getField() 
  = classMirror("java.awt.Point").newInstance(constructorDesc([]),[]).getField("x").toValue(#int) == 0;
  
test bool invokeMethod() {
  // create a Point instance at (0,0)
  p = classMirror("java.awt.Point").newInstance(constructorDesc([]),[]);
  
  // method with side effect! move to (1,2)
  p.invoke(methodDesc(\void(),"move", [integer(),integer()]), [integer(1), integer(2)]);
  
  // observe side effect
  return p.getField("x").toValue(#int) == 1
      && p.getField("y").toValue(#int) == 2;
}

test bool staticClassName() = classMirror("java.awt.Point").class == "java.awt.Point";
test bool objectClassName() = classMirror("java.awt.Point").newInstance(constructorDesc([]),[]).classMirror.class == "java.awt.Point";
 
test bool arrayLoadInteger()
  = array(integer(), [integer(1331)]).load(0).toValue(#int) == 1331;
  
test bool arrayLoadLong()
  = array(long(), [long(maxValue(long()))]).load(0).toValue(#int) == maxValue(long()); 
  
test bool arrayLoadObjectNull()
  = array(object(), 10).load(0) == Mirror::null(); 
  
test bool arrayLoadStringNull()
  = array(string(), 10).load(0) == Mirror::null();
  
test bool arrayLoadStringSingleton()
  = string(array(string(), [string("x")]).load(0)) == "x"; 
  
test bool arrayLoadStringTwo()
  = string(array(string(), [string("x"), string("y")]).load(1)) == "y"; 
  
test bool arrayLengthFilled(int l)
  = array(integer(), [integer(e) | e <- [0..l mod 100]]).length() == l mod 100;
  
test bool arrayLengthStringFilled(int l)
  = array(string(), [string("<e>") | e <- [0..l mod 100]]).length() == l mod 100;
  
test bool arrayLengthDefault()
  = array(integer(), 100).length() == 100;
  
test bool annoMirror() 
  = classMirror("java.lang.Deprecated").getAnnotation(object("java.lang.annotation.Retention")) != null();  
