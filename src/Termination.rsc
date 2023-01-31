module Termination

import Grammar;
import ParseTree;
import Util;
import analysis::grammars::Dependency;
import Set;

data Production(int weight=0);
 

@memo 
@synopsis{Annotate grammar rules in a grammar with stochastic information}
@description{
If a grammar is used to generate random trees then recursion will quickly lead to
very deep trees that almost always produce StackOverflow exceptions.

To counter this, this analysis annotates a grammar. Every rule is given a "weight"
that will determine its likelihood to be randomly selected with respect to its
alternatives.

To make sure random tree generation terminates often (almost always), we 
distinguish between recursive rules and non-recursive rules via grammar analysis.
For every non-terminal, the group of non-recursive alternatives is always given
at least 50% of the total weight. 

Consider an expression grammar, there are usually only a few of those "terminating"
rules (`Id`, `Number`) and all the others are recursive (`e+e`, `e*e`, `e[e]`).

After this grammar transformation, any algorithm that can generate trees randomly
can make use of these weights. The distribution of weight within a group of alternatives
is uniform.
}
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
@synopsis{Extracts which non-terminal depends on which others}
rel[Symbol,Symbol] dependencies(map[Symbol, Production] gr) 
  = {<delabel(from),delabel(to)> | /prod(Symbol from,[_*,Symbol to,_*],_) := gr}+;