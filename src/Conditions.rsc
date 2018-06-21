module Conditions

import Grammar;
import ParseTree;


    //= \follow(Symbol symbol)
    // | \not-follow(Symbol symbol)
    // | \precede(Symbol symbol)
    // | \not-precede(Symbol symbol)
    // | \delete(Symbol symbol)
    // | \at-column(int column) 
    // | \begin-of-line()  
    // | \end-of-line()  
    // | \except(str label)


Grammar simulateConditions(Grammar g) = innermost visit(g) {
  case prod(s,[*b,conditional(t,{\end-of-line(),*z}),*a], as)
    => prod(s,[*b,conditional(seq([t,lit("\n")]),z),*a], as)
  case prod(s,[*b,conditional(t,{\begin-of-line(),*z}),*a], as)
    => prod(s,[*b,conditional(seq([lit("\n"), t]),z),*a], as)  
};

