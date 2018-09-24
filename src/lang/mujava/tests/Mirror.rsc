module lang::mujava::tests::Mirror

import lang::mujava::Mirror;
import lang::mujava::Compiler;

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


