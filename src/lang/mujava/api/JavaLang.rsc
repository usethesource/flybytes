module lang::mujava::api::JavaLang

import lang::mujava::Syntax;
import lang::mujava::Mirror;

Type String() = string();
Type Boolean() = classType("java.lang.Boolean");
Type Integer() = classType("java.lang.Integer");
Type Character() = classType("java.lang.Character");
Type Double() = classType("java.lang.Double");
Type Long() = classType("java.lang.Long");
Type Short() = classType("java.lang.Short");
Type Float() = classType("java.lang.Float");

Mirror StringMirror() = classMirror("java.lang.string");
Mirror BooleanMirror() = classMirror("java.lang.Boolean");
Mirror ByteMirror() = classMirror("java.lang.Byte");
Mirror IntegerMirror() = classMirror("java.lang.Integer");
Mirror CharacterMirror() = classMirror("java.lang.Character");
Mirror DoubleMirror() = classMirror("java.lang.Double");
Mirror LongMirror() = classMirror("java.lang.Long");
Mirror ShortMirror() = classMirror("java.lang.Short");
Mirror FloatMirror() = classMirror("java.lang.Float");

real maxValue(float()) = FloatMirror().getStatic("MAX_VALUE").toValue(#real);
real minValue(float()) = FloatMirror().getStatic("MIN_VALUE").toValue(#real);

real maxValue(double()) = DoubleMirror().getStatic("MAX_VALUE").toValue(#real);
real minValue(double()) = DoubleMirror().getStatic("MIN_VALUE").toValue(#real);

int maxValue(short()) = ShortMirror().getStatic("MAX_VALUE").toValue(#int);
int minValue(short()) = ShortMirror().getStatic("MIN_VALUE").toValue(#int);

int maxValue(character()) = CharacterMirror().getStatic("MAX_VALUE").toValue(#int);
int minValue(character()) = CharacterMirror().getStatic("MIN_VALUE").toValue(#int);

int maxValue(integer())  = IntegerMirror().getStatic("MAX_VALUE").toValue(#int);
int minValue(integer()) = IntegerMirror().getStatic("MIN_VALUE").toValue(#int);

int maxValue(long())  = LongMirror().getStatic("MAX_VALUE").toValue(#int);
int minValue(long()) = LongMirror().getStatic("MIN_VALUE").toValue(#int);

int maxValue(byte()) = ByteMirror().getStatic("MAX_VALUE").toValue(#int);
int minValue(byte()) = ByteMirror().getStatic("MIN_VALUE").toValue(#int);
