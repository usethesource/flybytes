module Forest

import salix::lib::Dagre;
import salix::Core;
import salix::HTML;
import salix::SVG;
import salix::App;
import salix::lib::Bootstrap;
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
import Grammar;
import Diagnose;
import Brackets;

private loc www = |http://localhost:7001/index.html|;
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
      bool sentence = "<tree>",
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
   | focus()
   | simplify()
   | freshSentence()
   | newInput(str x)
   ;

Tree again(type[Tree] grammar, Tree t) = parse(grammar, "<t>");

Model update(labels(), Model m) = m[labels = !m.labels];
Model update(literals(), Model m) = m[literals = !m.literals];
Model update(\layout(), Model m) = m[\layout = !m.\layout];
Model update(chars(), Model m) = m[chars = !m.chars];
Model update(shared(), Model m) = m[shared = !m.shared];
Model update(focus(), Model m) = focus(m); 
Model update(newInput(str new), Model m) {
  try {
    m.tree = completeLocs(parse(m.grammar, new, allowAmbiguity=true));
  }
  catch ParseError(_) : {
    m.tree = appl(regular(lit("parse error")), [char(i) | i <- chars(new)]);
  }
  
  return m;
}

Model update(simplify(), Model m) {
  m.tree=completeLocs(reparse(m.grammar, simplify(m.grammar, m.tree)));
  m.tree = m.tree;
  return m;
}

Model update(freshSentence(), Model m) {
  m.tree = completeLocs(freshSentence(m.grammar));
  m.tree = m.tree;
  return m;
}

Tree freshSentence(type[Tree] gr) {
  if (options:{_,*_} := randomAmbiguousSubTrees(gr, 20)) {
    example = sort(options, bool (Tree l, Tree r) { return size("<l>") < size("<r>"); })[0];
    
    try {
        return reparse(gr, example);
    }
    catch ParseError(_) : {
      return appl(regular(lit("parse error")), [char(i) | i <- chars(example)]);
    }
  }
  else {
    return getOneFrom(randomTrees(gr, 1));
  }
}

str updateSrc(str src, int fromLine, int fromCol, int toLine, int toCol, str text, str removed) {
  list[str] lines = mySplit("\n", src);
  int from = ( 0 | it + size(l) + 1 | str l <- lines[..fromLine] ) + fromCol;
  int to = from + size(removed);
  str newSrc = src[..from] + text + src[to..];
  return newSrc;  
}

void graphic(Model m) {
   str id(Tree a:appl(_,_)) = "N<a@unique>";
   str id(Tree a:amb(_))    = "N<a@unique>";
   str id(Tree a:char(_))   = "N<a@unique>";
   
   str lbl(appl(p,_)) = m.labels ? "<symbol2rascal(delabel(p.def))> = <prod2rascal(p)>" : prodlabel(p);
   str lbl(amb({appl(p,_), *_})) = m.labels ? "<symbol2rascal(delabel(p.def))>" : "amb";
   str lbl(c:char(int ch))       = "<c>";
   
   str shp(appl(prod(_,_,_),_)) = "rect";
   str shp(appl(regular(_),_)) = "ellipse";
   str shp(amb(_))    = "diamond";
   str shp(char(_))   = "circle";
   
   list[Tree] args(appl(_,a)) = a;
   list[Tree] args(amb(alts)) = [*alts];
   list[Tree] args(char(_))   = [];
   
   t = !m.shared ? unique(m.tree) : shared(unique(m.tree));

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
}
 
void view(Model m) {
   container(true, () {
     ul(class("tabs nav nav-tabs"), id("tabs"), () {
       li(() {
         a(attr("data-toggle","tab"), href("#input"), () { 
           text("Input"); 
         });
       });
       li(() {
         a(attr("data-toggle","tab"), href("#brackets"), () {
           text("Brackets");
         });
       });
       li(class("active"), () {
         a(attr("data-toggle","tab"), href("#graphic"), () {
           text("Graphic");
         });
       });
       li(() {
         a(attr("data-toggle","tab"), href("#grammar"), () { 
           text("Grammar"); 
         });
       });
       li(() {
         a(attr("data-toggle","tab"), href("#diagnose"), () { 
           text("Diagnosis"); 
         });
       });
       li(() {
         a(attr("data-toggle","tab"), href("#help"), () { 
           text("Help"); 
         });
       });
    });
        
    div(id("main-tabs"), class("tab-content"), () {
      div(class("tab-pane fade in"), id("input"), () {
        row(() {
        column(10, md(), () {
           textarea(class("form-control"), style(<"width","100%">), rows(10), onChange(Msg (str t) { return newInput(t); }), () { 
              text("<m.sentence>"); 
           });
        });    
        column(2, md(), () {
            div(class("list-group list-group-flush"), style(<"list-style-type","none">), () {
              div(class("list-group-item alert alert-<if (!hasAmb(m.tree)) {>info<} else {>warning<}>"), () {
                text("Sentence is<if (!hasAmb(m.tree)) {> not<}> ambiguous.");
              });
              button(class("list-group-item"), onClick(simplify()), "Simplify sentence");
              button(class("list-group-item"), onClick(freshSentence()), "Generate sentence");
              if (amb({/amb(_), *_}) := m.tree) {
                div(class("list-group-item alert alert-warning"), () {
                  text("Sentence has nested ambiguity.");
                });
                button(class("list-group-item"), onClick(focus()), "Focus");
              }
            });
        });
        });
     });
     div(class("tab-pane active"), id("graphic"), () {
        row(() {
          column(10, md(), () {
            graphic(m);
          });
          column(2, md(), () {
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
		    });
          });
        });
      });
      div(class("tab-pane fade in"), id("brackets"), () {
        if (amb({t1, t2}) := m.tree) {
	        row(() {
	          column(6, md(), () {
	             pre(() {
	               text("<brackets(t1)>");
	             });
	          });
	          column(6, md(), () {
	             pre(() {
	               text("<brackets(t2)>");
	             });
	          });
	        });
	    } else {
	      text("No two trees to compare");
	    } 
      });
      div(class("tab-pane fade in"), id("grammar"), () {
        pre(() { 
          text(grammar2rascal(grammar({}, m.grammar.definitions))); 
        });
      });
      div(class("tab-pane fade in"), id("diagnose"), () {
          diagnose(m.tree); 
      });
      div(class("tab-pane fade in"), id("help"), () {
        h3("What is ambiguity?");
      });
    });
  });
}

Model focus(Model m) {
  ambs = [a | /Tree a:amb(_) := m.tree];
  
  m.tree = ambs[arbInt(size(ambs))];
  
  return m;
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

