module Termination

import Grammar;
import ParseTree;
import Util;
import analysis::grammars::Dependency;

data Production(int weight=0, bool rec=false);
 
@memo 
Grammar terminationWeights(Grammar g) = visit (g) { 
  case p:prod(s,ss,_) => p[weight=weight(p, g)][rec=t <- ss && <delabel(t),delabel(s)> in recursiveSymbols(g.rules)]
};
 
int weight(prod(Symbol _:!layouts(_), [], set[Attr] _), Grammar g) = 15;
int weight(prod(layouts(_), [], set[Attr] _), Grammar g)           = 15;
int weight(prod(Symbol _, list[Symbol] ss, set[Attr] _), Grammar g) = 30 when ss != [], all(s <- ss, terminal(delabel(s)));
default int weight(Production _, Grammar _) = 15;

bool terminal(lit(_))           = true;
bool terminal(cilit(_))         = true;
bool terminal(lex(_))           = true;
bool terminal(\char-class(_))   = true;
bool terminal(\layouts(_))      = true;
default bool terminal(Symbol _) = false;
 
@memo rel[Symbol,Symbol] recursiveSymbols(map[Symbol, Production] gr) 
  = {<delabel(from),delabel(to)> | /prod(Symbol from,[_*,Symbol to,_*],_) := gr}+;