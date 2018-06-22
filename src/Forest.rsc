module Forest

import salix::lib::Dagre;
import salix::Core;
import salix::HTML;
import salix::SVG;
import salix::App;
extend salix::lib::CodeMirror;
import lang::rascal::format::Grammar;
import ParseTree;
import IO;
import List;
import Set;
import String;
import Boolean;
import util::Math;
import Simplify;
import GenerateTrees;
import Detection;
import util::Reflective;
import Util;

private loc www = |http://localhost:7005/index.html|;
private loc root = getModuleLocation("Forest").parent;

App[Model] drAmbiguity(type[&T <: Tree] grammar, loc input) 
  = app(Model () { return model(completeLocs(parse(grammar, input)), grammar); }, view, update, www, root);

App[Model] drAmbiguity(type[&T <: Tree] grammar, str input) 
  = app(Model () { return model(completeLocs(parse(grammar, input, |unknown:///|, allowAmbiguity=true)), grammar); }, view, update, www, root);

App[Model] drAmbiguity(type[&T <: Tree] grammar) 
  = app(Model () { return model(completeLocs(freshSentence(grammar)), grammar); }, view, update, www, root);
  
App[Model] drAmbiguity(type[&T <: Tree] grammar, &T input) 
  = app(Model () { return model(completeLocs(input), grammar); }, view, update, www, root);

data Model 
  = model(Tree tree, type[Tree] grammar, 
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
   | simplify()
   | freshSentence()
   ;

Tree again(type[Tree] grammar, Tree t) = parse(grammar, "<t>");

Model update(labels(), Model m) = m[labels = !m.labels];
Model update(literals(), Model m) = m[literals = !m.literals];
Model update(\layout(), Model m) = m[\layout = !m.\layout];
Model update(chars(), Model m) = m[chars = !m.chars];
Model update(shared(), Model m) = m[shared = !m.shared];
Model update(nextAmb(), Model m) = m[current=selectNextAmb(m)]; 

Model update(simplify(), Model m) {
  m.tree=completeLocs(reparse(m.grammar, simplify(m.grammar, m.current)));
  m.current = m.tree;
  return m;
}

Model update(freshSentence(), Model m) {
  m.tree = completeLocs(freshSentence(m.grammar));
  m.current = m.tree;
  return m;
}

Tree freshSentence(type[Tree] gr) {
  if (options:{_,*_} := randomAmbiguousSubTrees(gr, 20)) {
    example = sort(options, bool (Tree l, Tree r) { return size("<l>") < size("<r>"); })[0];
    
    try {
        return reparse(gr, example);
    }
    catch ParseError(_) : {
      println("no parse tree to show?!");
      return char(32);
    }
  }
  else {
    return smallestTree(gr);
  }
}
 
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

   div(class("container"), () {
   div(class("row"), () {
     div(class("col-md-10"), () {
       dagre("Forest",  style(<"overflow-x","scroll">,<"overflow-y","scroll">,<"border", "solid">,<"border-radius","5px">,<"height","600px">,<"width","100%">), rankdir("TD"), (N n, E e) {
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
         done = {};
       }); 
       
        
     });
     
     div(class("col-md-2"), () {
        div(class("list-group"), style(<"list-style-type","none">), () {
            div(class("list-group-item"), () { 
              input(\type("checkbox"), checked(m.labels), onClick(labels()));
              text("rules");
            });
            div(class("list-group-item "), () { 
              input(id("literals"), \type("checkbox"), checked(m.literals), onClick(literals()));
              text("literals");
            });
            div(class("list-group-item"), () { 
              input(\type("checkbox"), checked(m.\layout), onClick(\layout()));
              text("layout");
            });
            div(class("list-group-item"), () { 
              input(\type("checkbox"), checked(m.chars), onClick(chars()));
              text("chars");
            });
            div(class("list-group-item"), () { 
              input(\type("checkbox"), checked(m.shared), onClick(shared()));
              text("shared");
            });
            button(class("list-group-item"), onClick(nextAmb()), "Next ambiguity");
        });
     });  
   });
   
   div(class("row"), () {
        div(class("col-md-10"), () {
          textarea(class("form-control"), style(<"width","100%">), rows(10), () { text("<m.tree>"); });
        });
        
        div(class("col-md-2"), () {
            div(class("list-group list-group-flush"), style(<"list-style-type","none">), () {
              div(class("list-group-item alert alert-<if (!hasAmb(m.tree)) {>info<} else {>warning<}>"), () {
                text("Sentence is<if (!hasAmb(m.tree)) {> not<}> ambiguous.");
              });
              button(class("list-group-item"), onClick(simplify()), "Simplify sentence");
              button(class("list-group-item"), onClick(freshSentence()), "Generate sentence");
            });
          });
      });
   });
}

Tree selectNextAmb(Model m) {
  ambs = [a | /Tree a:amb(_) := m.tree];
  
  if (m.tree == m.current, ambs != []) {
    return ambs[0];
  }
  else if ([*_, Tree x, Tree y, *_] := ambs, x == m.current, y@\loc != m.current@\loc) {
    return y;
  }
  else {
    return m.tree;
  }
}
 
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

Tree parseAgain(type[Tree] _, cycle(Symbol s, int c)) = cycle(s, c);
Tree parseAgain(type[Tree] _, char(int ch)) = char(ch);
Tree parseAgain(type[Tree] _, amb({Tree t, *Tree _})) = parseAgain(grammar, t);
   
default Tree parseAgain(type[Tree] gr, Tree t) = parse(type(delabel(t.prod.def), gr.definitions), "<t>", allowAmbiguity=true);
