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
Symbol symbol(appl(regular(Symbol s), _)) = delabel(s);
Symbol symbol(amb({Tree a, *Tree _})) = symbol(a);
Symbol symbol(char(int i)) = \char-class([range(i, i)]);
default Symbol symbol(appl(prod(Symbol s, _ , _), _)) = s;

Tree reparse(type[Tree] grammar, Tree t) {
  s = symbol(t);
  wrapped = (sort(_) !:= s) && (lex(_) !:= s);
  
  if (wrapped) {
    grammar = type(grammar.symbol, grammar.definitions + (sort("$WRAP$") : prod(sort("$WRAP$"), [s], {})));
  }
  
  if (type[Tree] subgrammar := type(symbol(t), grammar.definitions)) {
    result = parse(subgrammar, "<t>", allowAmbiguity=true);
    return wrapped ? result.args[0] : result;
  }
  
  throw "this should never happen";
}