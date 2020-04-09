module lang::flybytes::Disassembler

extend lang::flybytes::Syntax;
import Exception;

@javaClass{lang.flybytes.internal.ClassDisassembler}
@synopsis{reverses the flybytes compiler, but recovers only lists of instructions from the methods' bodies.}
java Class disassemble(loc classFile, bool signaturesOnly=false) throws IO;

@synopsis{return the disassembled information from all (overloaded) methods with a given name in the given class.}
list[Method] disassemble(loc classFile, str methodName)
  = [ m | Method m <- disassemble(classFile).methods, m.desc?, (m.desc.name?"") == methodName];
 
