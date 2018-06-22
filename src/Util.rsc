module Util

import ParseTree;
import Grammar;
import Node;

Symbol delabel(label(str _, Symbol s)) = delabel(s);
Symbol delabel(conditional(Symbol s, set[Condition] _)) = delabel(s);
default Symbol delabel(Symbol s) = unset(s);

&T cast(type[&T] c, value x) {
  if (&T t := x) 
    return t;
  else
    throw "cast exception <c> is not a
     <x>";
}

Symbol symbol(appl(prod(label(str _, Symbol s), _ , _), _)) = s;
Symbol symbol(amb({Tree a, *Tree _})) = symbol(a);
Symbol symbol(char(int i)) = \char-class([range(i, i)]);
default Symbol symbol(appl(prod(Symbol s, _ , _), _)) = s;

Tree reparse(type[Tree] grammar, Tree t) {
  if (type[Tree] subgrammar := type(symbol(t), grammar.definitions)) {
    return parse(subgrammar, "<t>", allowAmbiguity=true);
  }
  
  throw "this should never happen";
}