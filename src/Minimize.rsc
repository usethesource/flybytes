module Minimize

import ParseTree;
import Boolean;
import util::Math;
import Detection;
import Util;

Tree readable(type[Tree] gr, Tree t, int effort=100) {
  return t; 
}

Tree minimize(type[Tree] gr, Tree t, int effort=100) {
   solve (t) {
     for (_ <- [0..effort]) {
       orig = t;
       new = removeOne(t, {});
       t = isAmbiguous(gr, new) ? new : orig;
     }
   } 
   
   return t;
}

bool isAmbiguous(t) = /amb(_) := t;

Tree removeOne(Tree t, set[Tree] protect) {
   found = false;
   
   return visit(t) {
     case Tree a => a when found || a in protect // replace only one
     // removes elements from non-nullable separated lists
     case Tree a:appl(Production r:regular(\iter-seps(_,list[Symbol] seps)),list[Tree] args:![_]) : {
       delta = size(seps) + 1;
       rand = arbInt(size(args) mod s);
       found = true;
       insert appl(r, args[..rand*delta] + args[(rand+1)*delta])[@\loc=a@\loc];
     }
     // removes elements from nullable separated lists
     case a:appl(r:regular(\iter-star-seps(_,seps)),args:![]) : {
       delta = size(seps) + 1;
       rand = arbInt(size(args) mod s);
       found = true;
       insert appl(r, args[..rand*delta] + args[(rand+1)*delta])[@\loc=a@\loc];
     }
     // removes elements from non-nullable lists
     case a:appl(r:regular(\iter(_)),args:![_]) : {
       rand = arbInt(size(args));
       found = true;
       insert appl(r, args[..rand] + args[(rand+1)..])[@\loc=a@\loc];
     }
     // removes elements from nullable lists
     case a:appl(r:regular(\iter-star(_)),args:![]) : {
       rand = arbInt(size(args));
       found = true;
       insert appl(r, args[..rand] + args[(rand+1)..])[@\loc=a@\loc];
     }
     // removes optionals
     case a:appl(r:regular(\opt(_)),[_]) : {
       if (arbBool()) {
         found = true;
         insert appl(r, [])[@\loc=a@\loc];
       }
     }
     // removes direct recursion
     case a:appl(prod(p,_,_),[*_,b:appl(prod(q,_,_),_),*_]) : {
       if (arbBool()) {
         fail; // skip to another match
       } else if (delabel(p) == delabel(q)) {
         found = true;
         insert b;
       } else {
         fail;
       }
     }
     // removes indirect recursion (one level removed)
     case a:appl(prod(p,_,_),[*_,appl(_,[*_,b:appl(prod(q,_,_),_),*_]),*_]) : {
       if (arbBool()) {
         fail; // skip to another match
       } else if (delabel(p) == delabel(q)) {
         found = true;
         insert b;
       } else {
         fail;
       }
     }
   };
}



