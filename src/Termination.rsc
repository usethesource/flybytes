module Termination

import Grammar;
import ParseTree;
import Util;
import analysis::grammars::Dependency;
import Set;

data Production(int weight=0);
 

@memo 
Grammar terminationWeights(Grammar g) { 
   deps = dependencies(g.rules);
   weights = ();
   recProds = {p | /p:prod(s,[*_,t,*_],_) := g, <delabel(t), delabel(s)> in deps};
   
   for (nt <- g.rules) {
      prods       = {p | /p:prod(_,_,_) := g.rules[nt]};
      count       = size(prods);
      recCount    = size(prods & recProds);
      notRecCount = size(prods - recProds);
      
      // at least 50% of the weight should go to non-recursive rules if they exist
      notRecWeight = notRecCount != 0 ? (count * 10) / (2 * notRecCount) : 0;
      recWeight = recCount != 0 ? (count * 10) / (2 * recCount) : 0;
      
      weights += (p : p in recProds ? recWeight : notRecWeight | p <- prods); 
   }
	   
   return visit (g) { 
	   case p:prod(_, _, _) => p[weight=weights[p]]
   }
}

@memo 
rel[Symbol,Symbol] dependencies(map[Symbol, Production] gr) 
  = {<delabel(from),delabel(to)> | /prod(Symbol from,[_*,Symbol to,_*],_) := gr}+;