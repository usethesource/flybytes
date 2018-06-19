module Detection

import ParseTree;
import Node;
import IO;

bool isValid(type[Tree] gr, Tree t)
  = isValid(gr, "<t>");
  
bool isValid(type[Tree] gr, str s) {
  //println("isValid(<gr>, \"<s>\")");
  
  try {
    parse(gr, s, allowAmbiguity=true);
    return true;
  }
  catch ParseError(_) :
    return false;
}

bool isAmbiguous(type[Tree] gr, Tree t) 
  = isAmbiguous(gr, "<t>");
  
bool isAmbiguous(type[Tree] gr, str s) {
  try {
    parse(gr, s);
    return false;
  } 
  catch Ambiguity(_,_,_) : {
    return true;
  }
}  

bool hasAmb(Tree t) = /amb(_) := t;

@memo
type[Tree] clean(type[Tree] gr) = unsetRec(gr);