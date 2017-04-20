module Forest

import salix::lib::Dagre;
import salix::Core;
import salix::HTML;
import salix::SVG;
import salix::App;
import lang::rascal::format::Grammar;
import ParseTree;
import IO;
import List;
import Boolean;
import util::Math;

App[Model] forest(Tree x) 
  = app(Model () { return model(completeLocs(x)); }, view, update, |http://localhost:7002|, |project://drambiguity/src|);

data Model 
  = model(Tree tree, 
      Tree current = tree,
      bool labels = false, 
      bool literals = false,
      bool \layout = false,
      bool chars = true,
      bool shared = false
    );
 
data Msg 
   = labels()
   | literals()
   | \layout()
   | chars()
   | shared()
   | nextAmb()
   | minimize()
   ;

Model update(labels(), Model m) = m[labels = !m.labels];
Model update(literals(), Model m) = m[literals = !m.literals];
Model update(\layout(), Model m) = m[\layout = !m.\layout];
Model update(chars(), Model m) = m[chars = !m.chars];
Model update(shared(), Model m) = m[shared = !m.shared];
Model update(nextAmb(), Model m) = m[current=selectNextAmb(m)]; 
Model update(minimize(), Model m) = m[current=minimize(m.current)]; 
  
void view(Model m) {
   str id(Tree a:appl(_,_)) = "N<a@unique>";
   str id(Tree a:amb(_))    = "N<a@unique>";
   str id(Tree a:char(_))   = "N<a@unique>";
   
   str lbl(appl(p,_)) = m.labels ? "<symbol2rascal(p.def)> = <prod2rascal(p)>" : prodlabel(p);
   str lbl(amb({appl(p,_), *_})) = m.labels ? "<symbol2rascal(p.def)>" : "amb";
   str lbl(c:char(int ch))       = "<c>";
   
   str shp(appl(prod(_,_,_),_)) = "rect";
   str shp(appl(regular(_),_)) = "ellipse";
   str shp(amb(_))    = "diamond";
   str shp(char(_))   = "circle";
   
   list[Tree] args(appl(_,a)) = a;
   list[Tree] args(amb(alts)) = [*alts];
   list[Tree] args(char(_))   = [];
   
   t = !m.shared ? unique(m.current) : shared(unique(m.current));
       
   div(class("row"), () {
     div(class("col-md-10"), style(<"border","1">), style(("overflow-x":"hidden", "overflow-y":"hidden")), () {
       dagre("Forest", class("col-md-10"), rankdir("TD"), (N n, E e) {
         done = {};
         
         void nodes(Tree a) {
           if (id(a) in done) return;
           if (!m.literals && isLiteral(a)) return;
           if (!m.\layout && isLayout(a)) return;
           if (!m.chars && isChar(a)) return;
           
           n(id(a), fill("black"), shape(shp(a)), () { 
               span(style(<"color","black">), lbl(a));
           });
           
           done += {id(a)};
           
           for (b <- args(a)) {
             nodes(b);
           }
         }
         
         void edges(Tree a) {
           if (!m.literals && isLiteral(a)) return;
           if (!m.\layout && isLayout(a)) return;
           if (!m.\layout && isChar(a)) return;
           if (id(a) in done) return;
           
           for (b <- args(a)) {
              if (!m.literals && isLiteral(b)) continue;
              if (!m.\layout && isLayout(b)) continue;
              if (!m.chars && isChar(b)) continue;
           
              e(id(a), id(b), lineInterpolate("linear"));
           }
           
           done += {id(a)};
           
           for (b <- args(a)) {
             edges(b);
           }
         }
         
         nodes(t);
         done = {};
         edges(t);
       });    
     });
     
     div(class("col-md-2"), () {
        ul(style(<"list-style-type","none">), () {
            li(() { 
              pre(() { text("<m.current>");}); 
            });
            li(() { 
              pre(() { text("<m.current@\loc>");}); 
            });
            li(() { 
              input(\type("checkbox"), checked(m.labels), onClick(labels()));
              text("rules");
            });
            li(() { 
              input(\type("checkbox"), checked(m.literals), onClick(literals()));
              text("literals");
            });
            li(() { 
              input(\type("checkbox"), checked(m.\layout), onClick(\layout()));
              text("layout");
            });
            li(() { 
              input(\type("checkbox"), checked(m.chars), onClick(chars()));
              text("chars");
            });
            li(() { 
              input(\type("checkbox"), checked(m.shared), onClick(shared()));
              text("shared");
            });
            li(() {
              button(onClick(nextAmb()), "Next amb");
            });
            li(() {
              button(onClick(minimize()), "Minimize");
            });
        });
     });
   });
}

Tree minimize(Tree t) {
   return removeOne(t, {});
}

Tree selectNextAmb(Model m) = y 
  when [*_, Tree x, Tree y, *_] := [a | /Tree a:amb(_) := m.tree] + [m.tree];

default Tree selectNextAmb(Model m) = m.tree;
 
Tree removeOne(Tree t, set[Tree] protect) {
   found = false;
   
   return visit(t) {
     case Tree a => a when found || a in protect // replace only one
     // removes elements from non-nullable separated lists
     case Tree a:appl(Production r:regular(\iter-seps(_,list[Symbol] seps)),list[Tree] args:![_]) : {
       delta = size(seps) + 1;
       rand = arbInt(size(args) mod s);
       found = true;
       insert appl(r, args[..rand*delta] + args[(rand+1)*delta]);
     }
     // removes elements from nullable separated lists
     case a:appl(r:regular(\iter-star-seps(_,seps)),args:![]) : {
       delta = size(seps) + 1;
       rand = arbInt(size(args) mod s);
       found = true;
       insert appl(r, args[..rand*delta] + args[(rand+1)*delta]);
     }
     // removes elements from non-nullable lists
     case a:appl(r:regular(\iter(_)),args:![_]) : {
       rand = arbInt(size(args));
       found = true;
       insert appl(r, args[..rand] + args[(rand+1)..]);
     }
     // removes elements from nullable lists
     case a:appl(r:regular(\iter-star(_)),args:![]) : {
       rand = arbInt(size(args));
       found = true;
       insert appl(r, args[..rand] + args[(rand+1)..]);
     }
     // removes optionals
     case a:appl(r:regular(\opt(_)),[_]) : {
       if (arbBool()) {
         found = true;
         insert appl(r, []);
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

Symbol delabel(label(str _, Symbol s)) = s;
default Symbol delabel(Symbol s) = s;

str prodlabel(regular(Symbol s)) = symbol2rascal(s);
str prodlabel(prod(label(str x,_),_,_)) = x;
str prodlabel(prod(_, list[Symbol] args:[*_,lit(_),*_],_)) = "<for (lit(x) <- args) {><x> <}>";
default str prodlabel(prod(Symbol s, _,_ )) = symbol2rascal(s);

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
