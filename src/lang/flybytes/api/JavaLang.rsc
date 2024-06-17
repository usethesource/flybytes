module lang::flybytes::api::JavaLang

import lang::flybytes::Syntax;
import lang::flybytes::Mirror;

Type String() = string();
Type Boolean() = object("java.lang.Boolean");
Type Integer() = object("java.lang.Integer");
Type Character() = object("java.lang.Character");
Type Double() = object("java.lang.Double");
Type Long() = object("java.lang.Long");
Type Short() = object("java.lang.Short");
Type Float() = object("java.lang.Float");

Type Iterator() = object("java.lang.Iterator");
Type Iterable() = object("java.lang.Iterable");
Signature Iterable_iterator() = methodDesc(Iterator(), "iterator", []);
Signature Iterator_next() = methodDesc(object(), "next", []);
Signature Iterator_hasNext() = methodDesc(boolean(), "hasNext", []);

Mirror StringMirror() = classMirror("java.lang.String");
Mirror BooleanMirror() = classMirror("java.lang.Boolean");
Mirror ByteMirror() = classMirror("java.lang.Byte");
Mirror IntegerMirror() = classMirror("java.lang.Integer");
Mirror CharacterMirror() = classMirror("java.lang.Character");
Mirror DoubleMirror() = classMirror("java.lang.Double");
Mirror LongMirror() = classMirror("java.lang.Long");
Mirror ShortMirror() = classMirror("java.lang.Short");
Mirror FloatMirror() = classMirror("java.lang.Float");

@synopsis{the maximal value for an arithmetic type on the JVM}
real maxRealValue(float())     = FloatMirror().getStatic("MAX_VALUE").toValue(#real);
real maxRealValue(double())    = DoubleMirror().getStatic("MAX_VALUE").toValue(#real);
int maxIntValue(short())     = ShortMirror().getStatic("MAX_VALUE").toValue(#int);
int maxIntValue(character()) = CharacterMirror().getStatic("MAX_VALUE").toValue(#int);
int maxIntValue(integer())   = IntegerMirror().getStatic("MAX_VALUE").toValue(#int);
int maxIntValue(long())      = LongMirror().getStatic("MAX_VALUE").toValue(#int);
int maxIntValue(byte())      = ByteMirror().getStatic("MAX_VALUE").toValue(#int);

@synopsis{the minimal value for an arithmetic type on the JVM}
real minRealValue(float())     = -1 * maxRealValue(float());
real minRealValue(double())    = -1 * maxRealValue(double());
int  minIntValue(byte())      = ByteMirror().getStatic("MIN_VALUE").toValue(#int);
int  minIntValue(long())      = LongMirror().getStatic("MIN_VALUE").toValue(#int);
int  minIntValue(integer())   = IntegerMirror().getStatic("MIN_VALUE").toValue(#int);
int  minIntValue(short())     = ShortMirror().getStatic("MIN_VALUE").toValue(#int);
int  minIntValue(character()) = CharacterMirror().getStatic("MIN_VALUE").toValue(#int);

@doc{the minimal increment for an arithmetic type on the JVM}
num epsilon(float())     = FloatMirror().getStatic("MIN_VALUE").toValue(#real);  // misnomer in the Java library
num epsilon(double())    = DoubleMirror().getStatic("MIN_VALUE").toValue(#real); // misnomer in the Java library
num  epsilon(short())     = 1;
num  epsilon(character()) = 1;
num  epsilon(integer())   = 1;
num  epsilon(long())      = 1;
num  epsilon(byte())      = 1;

Exp Integer_parseInt(Exp e, int radix) = invokeStatic(Integer(), methodDesc(integer(), "parseInt", [string(), integer()]), [e, iconst(radix)]);
