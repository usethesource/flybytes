module Util

import ParseTree;
import Grammar;
import Node;
import IO;

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
  
  if (s notin grammar.definitions<0>) {
    if (x:\start(_) <- grammar.definitions) {
      s = x;
    }
    else if (x <- grammar.definitions) {
      s = x;      
    }
  } 
  wrapped = (sort(_) !:= s) && (lex(_) !:= s);
  
  if (wrapped) {
    grammar = type(grammar.symbol, grammar.definitions + (sort("$WRAP$") : prod(sort("$WRAP$"), [s], {})));
  }
  
  if (type[Tree] subgrammar := type(wrapped ? sort("$WRAP$") : s , grammar.definitions)) {
    result = parse(subgrammar, "<t>", allowAmbiguity=true);
    return wrapped ? result.args[0] : result;
  }
  
  throw "this should never happen";
}

bool isChar(char(_)) = true;
default bool isChar(Tree _) = false;

bool isLayout(appl(prod(layouts(_),_,_),_)) = true;
bool isLayout(appl(prod(label(layouts(_),_),_,_),_)) = true;
default bool isLayout(Tree _) = false;

bool isLiteral(appl(prod(lit(_),_,_),_)) = true;
default bool isLiteral(Tree _) = false;

anno int Tree@unique;
Tree unique(Tree t) {
   int secret = 0;
   int unique() { secret += 1; return secret; };
   return visit(t) { 
     case Tree x => x[@unique=unique()] 
   };
}  

Tree completeLocs(Tree t) = nt when <nt, _> := completeLocs(t, t@\loc.top, 0);

tuple[Tree, int] completeLocs(Tree t, loc parent, int offset) {
  int s = offset;
  
  switch (t) {
    case char(_) : return <t[@\loc=parent(offset, 1)], offset + 1>;
    case amb(_)  : {
      newAlts = for (Tree a <- t.alternatives) {
        <a, s> = completeLocs(a, parent, offset);
        append a;
      }
      return <amb({*newAlts})[@\loc=parent(offset, s - offset)], s>;
    }
    case appl(p,_) : {
      newArgs = for (Tree a <- t.args) {
        <a, s> = completeLocs(a, parent, s);
        append a;
      }
      return <appl(p,newArgs)[@\loc=t@\loc?parent(offset, s - offset)], s>;
    }
    case cycle(s, i) : {
      return <cycle(s, i)[@\loc=t@\loc?parent], offset>;
    }
  } 
}

Tree shared(Tree t) {
   done = {};
   
   return visit(t) {
     case Tree a : {
        if (<a, l, u> <- done, l == a@\loc) {
          insert a[@unique=u];
        }
        else {
          done += <a, a@\loc, a@unique>;
        }
      }
   }
}