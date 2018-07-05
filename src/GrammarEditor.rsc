//@bootstrapParser
module GrammarEditor

import lang::rascal::grammar::definition::Modules;
import lang::rascal::\syntax::Rascal;
import Grammar;
import IO;

type[Tree] refreshGrammar(Symbol s, str newText) {
   Module m = parse(#start[Module], "module Dummy
                                    '
                                    '<newText>").top;
                                    
   Grammar gm = modules2grammar("Dummy", {m});
   
   if (s notin gm.rules<0>) {
     if (x:\start(_) <- gm.rules) {
       s = x;
     }
     else if (x <- gm.rules) {
       s = x;
     }
   }
   
   if (type[Tree] gr := type(s, gm.rules)) {
     return gr;
   }
   
   throw "could not generate a proper grammar: <gm>";
}