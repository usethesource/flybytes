module GenerateTrees

import util::Math;
import Set;
import List;
import Boolean;
import ParseTree;
import Grammar;
import lang::rascal::grammar::definition::Regular;
import lang::rascal::grammar::definition::Literals;
import lang::rascal::grammar::definition::Parameters;
import IO;
import Detection;
import Termination;
import Conditions;
import Simplify;
import Util;
import analysis::grammars::Dependency;

data opt[&T] = yes(&T thing) | no();

opt[str] findAmbiguousSubString(type[Tree] gr, int effort) 
  = yes(Tree t) := findAmbiguousTree(gr, effort) ? yes("<t>") : no();

opt[Tree] findAmbiguousSubTree(type[Tree] gr, int effort) {
   gr = completeGrammar(gr);

   for (_ <- [0..effort], t := randomTree(gr), isAmbiguous(gr, t)) {
       return yes(t);
   }
   
   return no();
}

set[str] randomAmbiguousSubStrings(type[Tree] grammar, int max)
  = {"<t>" | t <- randomAmbiguousSubTrees(grammar, max)};
  
set[Tree] randomAmbiguousSubTrees(type[Tree] grammar, int max)
  = { firstAmbiguity(grammar, "<t>") | t <- randomTrees(grammar, max), isAmbiguous(grammar, t)};

set[str] randomStrings(type[Tree] grammar, int max)
  = {"<t>" | t <- randomTrees(grammar, max)};

set[Tree] randomTrees(type[Tree] gr, int max) {
  gr = completeGrammar(gr);
  try {
    return {randomTree(gr) | _ <- [0..max]};
  }
  catch StackOverflow(): {
    println("StackOverflow!?! The chance of overflow is one in a gazillion... Go buy a lottery ticket?"); 
    return {};
  }
}
   
Tree randomTree(type[Tree] gr) 
  = randomTree(gr.symbol, 0, toMap({ <s, p> | s <- gr.definitions, /Production p:prod(_,_,_) <- gr.definitions[s]}));

Tree randomTree(\char-class(list[CharRange] ranges), int rec, map[Symbol, set[Production]] _)
  = randomChar(ranges[arbInt(size(ranges))]);

Tree randomTree(Symbol sort:layouts(_), int rec, map[Symbol, set[Production]] gr) {
  if (arbBool()) {
    p = randomAlt(sort, gr[sort], rec);  
    return appl(p, [randomTree(delabel(s), rec + 1, gr) | s <- p.symbols]);
  }
  else {
    return smallestTree(sort, gr);
  }
}

// this certainly runs out of stack on non-productive grammars and 
// may (low chance) run out-of stack for "hard to terminate" recursion  
default Tree randomTree(Symbol sort, int rec, map[Symbol, set[Production]] gr) {
   p = randomAlt(sort, gr[sort], rec);  
   return appl(p, [randomTree(delabel(s), rec + 1, gr) | s <- p.symbols]);
}

default Production randomAlt(Symbol sort, set[Production] alts, int rec) {
  int w(Production p) = rec > 100 ?  p.weight * p.weight : p.weight;
  int total(set[Production] ps) = (1 | it + w(p) | Production p <- ps);
  
  r = arbInt(total(alts));
  
  count = 0;
  for (Production p <- alts) {
    count += w(p);

    if (count >= r) {
      return p;
    }
  } 
  
  throw "could not select a production for <sort> from <alts>";
}

Tree randomChar(range(int min, int max)) = char(arbInt(max + 1 - min) + min);

data Production(int distance = 0);

Tree smallestTree(type[Tree] gr) {
  gr = completeGrammar(gr);
  return smallestTree(gr.symbol, toMap({ <s, p> | s <- gr.definitions, /Production p:prod(_,_,_) <- gr.definitions[s]}));
}

Tree smallestTree(\char-class(list[CharRange] ranges), map[Symbol, set[Production]] _)
  = randomChar(ranges[arbInt(size(ranges))]);

default Tree smallestTree(Symbol sort, map[Symbol, set[Production]] gr) {
   p = smallestAlt(sort, gr[sort]);  
   return appl(p, [smallestTree(delabel(s), gr) | s <- p.symbols]);
}

Production smallestAlt(Symbol sort, set[Production] alts) {
   return Set::sort(alts, bool (Production l, Production r) { return l.weight < r.weight; })[0];
}

type[Tree] completeGrammar(type[Tree] gr) {
  g = grammar({gr.symbol}, gr.definitions);
  //g = simulateConditions(g);
  g = literals(g);
  g = expandParameterizedSymbols(g);
  g = expandRegularSymbols(makeRegularStubs(g));
  g = visit(g) { case Symbol s => delabel(s) };
  g = terminationWeights(g);
  return cast(#type[Tree], type(gr.symbol, g.rules));
} 
