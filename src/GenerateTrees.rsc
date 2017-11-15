module GenerateTrees

import util::Math;
import List;
import Boolean;
import ParseTree;
import Grammar;
import lang::rascal::grammar::definition::Regular;
import lang::rascal::grammar::definition::Literals;
import IO;
import Detection;

data opt[&T] = yes(&T thing) | no();

opt[str] findAmbiguousString(type[Tree] gr, int effort) 
  = yes(Tree t) := findAmbiguousTree(gr, effort) ? yes("<t>") : no();

opt[Tree] findAmbiguousTree(type[Tree] gr, int effort) {
   gr = completeGrammar(gr);
   
   for (_ <- [0..effort], t := randomTree(gr), isAmbiguous(gr, "<t>")) {
       return yes(t);
   }
   
   return no();
}

set[str] randomAmbiguousStrings(type[Tree] grammar, int max)
  = { "<t>" | t <- randomAmbiguousTrees(grammar, max)};
  
set[Tree] randomAmbiguousTrees(type[Tree] grammar, int max)
  = { t | t <- randomTrees(grammar, max), isAmbiguous(grammar, "<t>")};

set[str] randomStrings(type[Tree] grammar, int max)
  = { "<t>" | t <- randomTrees(grammar, max)};

set[Tree] randomTrees(type[Tree] grammar, int max) {
  grammar = completeGrammar(grammar);
  return { randomTree(grammar) | _ <- [0..max]};
}
   
Tree randomTree(type[Tree] grammar) = randomTree(grammar.symbol, grammar.definitions);

Tree randomTree(\char-class(list[CharRange] ranges), map[Symbol, Production] _)
  = randomChar(ranges[arbInt(size(ranges))]);
 
// this runs out of stack on non-productive grammars and may run out-of stack for "hard to terminate" recursion  
default Tree randomTree(Symbol sort, map[Symbol, Production] grammar) {
  alts        = [p | /p:prod(_,_,_) := grammar[sort].alternatives];
  alts        = List::sort(alts, bool (Production l, Production r) { return terminating(l) < terminating(r); });
  
  if (p:prod(_, ss, _) := (arbBool() ? alts[0] : alts[arbInt(size(alts))])) {
    return appl(p, [randomTree(s, grammar) | s <- ss]);
  } 
  
  throw "???";
}

Tree randomChar(range(int min, int max)) = char(arbInt(max + 1 - min) + min);

type[Tree] completeGrammar(type[Tree] gr)
  = cast(#type[Tree], type(gr.symbol, expandRegularSymbols(literals(grammar({}, gr.definitions))).rules));
 
private int terminating(prod(_, list[Symbol] ss, _)) 
  = (0 | it + 1 | s <- ss, !(lit(_) := s || cilit(_) := s || \char-class(_) := s));
 
 
private &T cast(type[&T] c, value x) {
  if (&T t := x) 
    return t;
  else
    throw "cast exception <c> is not a
     <x>";
}