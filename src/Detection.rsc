module Detection

import ParseTree;
import Exception;
import Node;
import IO;

@synopsis{A tree is still "valid" if parsing its yield does not produce a parse error; ambiguity is ok.}
bool isValid(type[Tree] gr, Tree t)
  = isValid(gr, "<t>");

@synopsis{A string is a "valid" if it does not produce a parse error; ambiguity is ok.}  
bool isValid(type[Tree] gr, str s) {
  try {
    parse(gr, s, allowAmbiguity=false, hasSideEffects=false);
    return true;
  }
  catch Ambiguity(_,_,_) :
    return true;
  catch ParseError(_) :
    return false;
}

@synopsis{Use the parser to find out if the sentence this tree represents is ambiguous or not}
bool isAmbiguous(type[Tree] gr, Tree t) 
  = isAmbiguous(gr, "<t>");
  
@synopsis{Use the parser to find out if this sentence is ambiguous or not}
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