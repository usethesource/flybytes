module Detection

import ParseTree;
import Node;

bool isAmbiguous(type[Tree] grammar, Tree t) 
  = isAmbiguous(grammar, "<t>");
  
bool isAmbiguous(type[Tree] grammar, str s) {
  try {
    parse((grammar), s);
    return false;
  }
  catch Ambiguity(_,_,_) : 
    return true;
}  

bool hasAmb(Tree t) = /amb(_) := t;

@memo
type[Tree] clean(type[Tree] gr) = unsetRec(gr);