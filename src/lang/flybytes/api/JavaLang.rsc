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

@doc{the maximal value for an arithmetic type on the JVM}
real maxValue(float())     = FloatMirror().getStatic("MAX_VALUE").toValue(#real);
real maxValue(double())    = DoubleMirror().getStatic("MAX_VALUE").toValue(#real);
int  maxValue(short())     = ShortMirror().getStatic("MAX_VALUE").toValue(#int);
int  maxValue(character()) = CharacterMirror().getStatic("MAX_VALUE").toValue(#int);
int  maxValue(integer())   = IntegerMirror().getStatic("MAX_VALUE").toValue(#int);
int  maxValue(long())      = LongMirror().getStatic("MAX_VALUE").toValue(#int);
int  maxValue(byte())      = ByteMirror().getStatic("MAX_VALUE").toValue(#int);

@doc{the minimal value for an arithmetic type on the JVM}
int  minValue(byte())      = ByteMirror().getStatic("MIN_VALUE").toValue(#int);
int  minValue(long())      = LongMirror().getStatic("MIN_VALUE").toValue(#int);
int  minValue(integer())   = IntegerMirror().getStatic("MIN_VALUE").toValue(#int);
real minValue(float())     = -1 * maxValue(float());
real minValue(double())    = -1 * maxValue(double());
int  minValue(short())     = ShortMirror().getStatic("MIN_VALUE").toValue(#int);
int  minValue(character()) = CharacterMirror().getStatic("MIN_VALUE").toValue(#int);

@doc{the minimal increment for an arithmetic type on the JVM}
real epsilon(float())     = FloatMirror().getStatic("MIN_VALUE").toValue(#real);  // misnomer in the Java library
real epsilon(double())    = DoubleMirror().getStatic("MIN_VALUE").toValue(#real); // misnomer in the Java library
int  epsilon(short())     = 1;
int  epsilon(character()) = 1;
int  epsilon(integer())   = 1;
int  epsilon(long())      = 1;
int  epsilon(byte())      = 1;

Exp Integer_parseInt(Exp e, int radix) = invokeStatic(Integer(), methodDesc(integer(), "parseInt", [string(), integer()]), [e, iconst(radix)]);
