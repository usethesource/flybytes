module Detection

import ParseTree;
import Node;
import IO;

bool isValid(type[Tree] gr, Tree t)
  = isValid(gr, "<t>");
  
bool isValid(type[Tree] gr, str s) {
  try {
    parse(gr, s, allowAmbiguity=false, hasSideEffects=false);
    return true;
  }
  catch Ambiguity(_) :
    return true;
  catch ParseError(_) :
    return false;
}

bool isAmbiguous(type[Tree] gr, Tree t) 
  = isAmbiguous(gr, "<t>");
  
bool isAmbiguous(type[Tree] gr, str s) {
   try {
     return amb(_) := firstAmbiguity(gr, s);
   }
   catch ParseError(_): {
     return false;
   }
}

bool hasAmb(Tree t) = /amb(_) := t;

@memo
type[Tree] clean(type[Tree] gr) = unsetRec(gr);