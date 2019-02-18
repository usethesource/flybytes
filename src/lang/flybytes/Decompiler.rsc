module lang::flybytes::Decompiler

import lang::flybytes::Syntax;
import Exception;

@javaClass{lang.flybytes.internal.ClassDecompiler}
java Class decompile(loc classFile) throws IO;