module Simplify

import ParseTree;
import Boolean;
import util::Math;
import Detection;
import Util;
import Set;
import IO;

@synopsis{simplifies an ambiguous parse forest while trying to remain ambiguous}
Tree simplify(type[Tree] gr, Tree t, int effort=100) {
   work = effort;

   for (int i <- [0..effort]) {
     new = simplify(t);
     if (new != t && isAmbiguous(gr, new)) {
       return new;
     }
   }   
   
   return t;
}

Tree simplify(Tree t) {
   if (amb(alts) := t, Tree a <- alts, arbBool()) {
     return simplify(a);
   }

   // otherwise half of the time we descend into the tree
   if (arbBool(), a:appl(p, args) := t) {
       for (i <- index(args)) {
          n = simplify(args[i]);
          
          if (n != args[i]) {
            return appl(p, [*args[..i], n, *args[i+1..]])[@\loc=a@\loc]; 
          }
       }
   }
    
   // the other half we try to contract some simplification rules, randomly:
   if (appl(p, _) := t) {
      println("contracting <p>");
   }

   switch(t) {
     // removes elements from non-empty separated lists
     case Tree a:appl(Production r:regular(\iter-seps(_,list[Symbol] seps)),list[Tree] args:![_]) : {
       delta = size(seps) + 1;
       rand = arbInt(size(args));

       if (arbBool()) {
          return appl(r, [args]);
       }

       return appl(r, args[..rand*delta] + args[min(rand*(delta+1), size(args))..])[@\loc=a@\loc];
     }
   
     // remove elements from star separated lists
     case a:appl(r:regular(\iter-star-seps(_,seps)),args:![]) : {
       delta = size(seps) + 1;
       rand = arbInt(size(args)) mod delta;

       if (arbBool()) {
          return appl(r, []);
       }

       return appl(r, args[..rand*delta] + args[min(rand*(delta+1), size(args))..])[@\loc=a@\loc];
     }
     
     // removes elements from non-nullable lists
     case a:appl(r:regular(\iter(_)),args:![_]) : {
       rand = arbInt(size(args));

       if (arbBool()) {
          return appl(r, [args[rand]]);
       }

       return appl(r, args[..rand] + args[(rand+1)..])[@\loc=a@\loc];
     }
     
     // removes elements from nullable lists
     case a:appl(r:regular(\iter-star(_)),args:![]) : {
       if (arbBool()) {
          return appl(r, []);
       }

       rand = arbInt(size(args));
       return appl(r, args[..rand] + args[(rand+1)..])[@\loc=a@\loc];
     }
     
     // removes optionals
     case a:appl(r:regular(\opt(_)),[_]) :
       if (arbBool()) { 
         return appl(r, [])[@\loc=a@\loc];
       }
       else {
         fail;
       }
         
     // removes direct recursion
     case a:appl(prod(p,_,_),[*_,b:appl(prod(q,_,_),_),*_]) : {
       if (arbBool()) {
         fail; // skip to another match
       } else if (delabel(p) == delabel(q)) {
         return b;
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
         return b;
       } else {
         fail;
       }
     }
     
     // just pick a random child to continue simplifcation in
     case a:appl(p, args): {
       for (i <- index(args), arbBool()) {
          n = simplify(args[i]);
          
          if (n != args[i]) {
            return appl(p, [*args[..i], n, *args[i+1..]])[@\loc=a@\loc]; 
          }
       }
     }
     
     // pick an ambiguous alternative, arbitrarily, not randomly
     case a:amb(alts) : {
       return getOneFrom(alts);
     }
   };
   
   return t;
}



