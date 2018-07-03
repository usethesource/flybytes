//@bootstrapParser
module GrammarEditor

import lang::rascal::grammar::definition::Modules;
import lang::rascal::\syntax::Rascal;
import Grammar;
import IO;

type[Tree] refreshGrammar(type[&O <: Tree] oldGrammar, str newText) {
   Module m = parse(#start[Module], "module Dummy
                                    '
                                    '<newText>").top;
                                    
   Grammar gm = modules2grammar("Dummy", {m});
   
   if (type[Tree] gr := type(oldGrammar.symbol, gm.rules)) {
     return gr;
   }
   
   throw "could not generate a proper grammar: <gm>";
}