module lang::mujava::tests::MirrorTests

import lang::mujava::Mirror;
import lang::mujava::Compiler;
import lang::mujava::api::JavaLang;

test bool intMirror(int i) = i mod 1000 == val(i).toValue(#int) mod 1000;
test bool strMirror(str i) = i == val(i).toValue(#str);

test bool nativeIntMirror(int i) = "<i % 1000>" == integer(i % 1000).toValue(#str);
test bool nativeShortMirror(int i) = "<i % 1000>" == short(i % 1000).toValue(#str);
test bool nativeByteMirror(int i) = "<i % 128>" == byte(i % 128).toValue(#str);
test bool nativeLongMirror(int i) = "<i % 50000>" == long(i % 50000).toValue(#str);

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

test bool intId(int v) = v % 1000 == integer(integer(v % 1000));
test bool longId(int v) = v % 40000 == long(long(v % 40000));
test bool byteId(int v) = v % 128 == byte(byte(v % 128));
test bool shortId(int v) = v % 20000 == short(short(v % 20000));  
test bool stringId(str x) = x == string(string(x));
test bool doubleId() = 2.0 == double(double(2.0));
test bool floutId() = 3.00 == float(float(3.00)); 

