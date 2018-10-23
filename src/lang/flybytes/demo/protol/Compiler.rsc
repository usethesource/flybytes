module lang::flybytes::demo::protol::Compiler

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

// the Protol compiler translates object allocation sites to specific JVM class definitions,
// and uses invokeDynamic to call interfaces and set/get fields on objects of each 
// possible object in memory.

Type Prototype = object("lang.flybytes.demo.protol.Prototype");

