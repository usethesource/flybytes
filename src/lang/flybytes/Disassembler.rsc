module lang::flybytes::Disassembler

extend lang::flybytes::Syntax;
import Exception;

@javaClass{lang.flybytes.internal.ClassDisassembler}
@synopsis{reverses the flybytes compiler, but recovers only lists of instructions from the methods' bodies.}
java Class disassemble(loc classFile) throws IO;

list[Method] disassemble(loc classFile, str methodName)
  = [ m | Method m <- disassemble(classFile).methods, (m.desc.name?"") == methodName];