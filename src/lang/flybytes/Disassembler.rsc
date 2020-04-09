module lang::flybytes::Disassembler

extend lang::flybytes::Syntax;
import Exception;

@javaClass{lang.flybytes.internal.ClassDisassembler}
@synopsis{reverses the flybytes compiler, but recovers only lists of instructions from the methods' bodies.}
java Class disassemble(loc classFile) throws IO;

list[Method] disassemble(loc classFile, str methodName)
  = [ m | Method m <- disassemble(classFile).methods, m.desc?, (m.desc.name?"") == methodName];
  
Method disassemble(loc classFile, str methodName) {
  cls = disassemble(classFile);
  if (Method m <- cls.methods, m.desc?, m.desc.name?, m.desc.name == methodName) {
    return m;
  }
  
  throw "no method named <methodName> exists in this class: <for (m <- cls.methods, m.desc?, m.desc.name?) {><m.desc.name> <}>";
}
