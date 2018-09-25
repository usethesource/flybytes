module lang::mujava::tests::MirrorTests

import lang::mujava::Mirror;
import lang::mujava::Compiler;
import lang::mujava::api::JavaLang;

test bool intId(int v) = v % 1000 == integer(integer(v % 1000));
test bool longId(int v) = v % 40000 == long(long(v % 40000));
test bool byteId(int v) = v % 128 == byte(byte(v % 128));
test bool shortId(int v) = v % 20000 == short(short(v % 20000));  
test bool stringId(str x) = x == string(string(x));
test bool doubleId() = 2.0 == double(double(2.0));
test bool floutId() = 3.00 == float(float(3.00)); 

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
  
test bool intStatic()
  = classMirror("java.lang.Integer").getStatic("MAX_VALUE").toValue(#int) == 2147483647;



