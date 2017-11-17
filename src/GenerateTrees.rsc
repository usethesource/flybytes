module GenerateTrees

import util::Math;
import List;
import Boolean;
import ParseTree;
import Grammar;
import lang::rascal::grammar::definition::Regular;
import lang::rascal::grammar::definition::Literals;
import lang::rascal::grammar::definition::Parameters;
import IO;
import Detection;
import Minimize;
import Util;
import analysis::grammars::Dependency;

data opt[&T] = yes(&T thing) | no();

opt[str] findMinimalAmbiguousString(type[Tree] gr, int effort)
  = yes(Tree t) := findAmbiguousTree(gr, effort) ? yes("<minimize(gr, t)>") : no();

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
  try {
    return { randomTree(grammar) | _ <- [0..max]};
  }
  catch StackOverflow(): {
    println("ran out of stack");
    return {};
  }
}
   
Tree randomTree(type[Tree] grammar) = randomTree(grammar.symbol, [], grammar.definitions);

Tree randomTree(\char-class(list[CharRange] ranges), list[Production] parents, map[Symbol, Production] _)
  = randomChar(ranges[arbInt(size(ranges))]);

 
// this runs out of stack on non-productive grammars and may run out-of stack for "hard to terminate" recursion  
default Tree randomTree(Symbol sort, list[Production] parents, map[Symbol, Production] gr) {
  alts = [p | /p:prod(_,_,_) := gr[sort].alternatives];
  p    = randomAlt(sort, alts, parents, gr);  
  return appl(p, [randomTree(delabel(s), parents + [p], gr) | s <- p.symbols]);
}

default Production randomAlt(Symbol sort, list[Production] alts, list[Production] parents, map[Symbol, Production] gr) {
  alts        = List::sort(alts, bool (Production l, Production r) {
    switch(<l in parents, r in parents>) {
      case <false, true> : return true;
      case <true, false> : return false;
      default: return  weight(l) < weight(r);
    } 
  });
  
  alts = (alts - parents) + (parents & alts); // parents at the end  
  
  if (p:prod(_, ss, _) := ((arbBool() || size(alts) == 1) ? alts[0] : alts[arbInt(size(alts))])) {
    return p;
  }
  
  throw "could not select a production for <sort> from <alts>";
}

int weight(prod(Symbol _, [], set[Attr] _))              = 2;
int weight(prod(Symbol _, list[Symbol] ss, set[Attr] _)) = 0 when all(s <- ss, s is \lit || s is \cilit || s is \lex || s is \char-class);
int weight(prod(Symbol s, list[Symbol] ss:[*Symbol _, s, *Symbol _], set[Attr] _)) = 2 * size(ss);
int weight(Production p) = size(p.symbols);

Tree randomChar(range(int min, int max)) = char(arbInt(max + 1 - min) + min);

type[Tree] completeGrammar(type[Tree] gr) {
  g = grammar({gr.symbol}, gr.definitions);
  g = literals(g);
  g = expandRegularSymbols(makeRegularStubs(g));
  g = expandParameterizedSymbols(g);
  return cast(#type[Tree], type(gr.symbol, g.rules));
} 
