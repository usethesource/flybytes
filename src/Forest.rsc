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
import GrammarEditor;

private loc www = |http://localhost:7002/index.html|;
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
      str grammarText = trim(grammar2rascal(Grammar::grammar({}, grammar.definitions))),
      list[Tree] examples = [],
      int generateAmount = 5, 
      str errors = "",
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
   | selectExample(Tree ex)
   | removeExample(int count)
   | generateAmount(int count)
   | storeInput()
   | newGrammar(str x)
   | refreshGrammar()
   ;

Tree again(type[Tree] grammar, Tree t) = parse(grammar, "<t>");

Model update(labels(), Model m) = m[labels = !m.labels];
Model update(literals(), Model m) = m[literals = !m.literals];
Model update(\layout(), Model m) = m[\layout = !m.\layout];
Model update(chars(), Model m) = m[chars = !m.chars];
Model update(shared(), Model m) = m[shared = !m.shared];
Model update(focus(), Model m) = focus(m); 
Model update(selectExample(Tree ex), Model m) = m[tree = ex];
Model update(removeExample(int count), Model m) = m[examples = m.examples[0..count-1] + m.examples[count..]];
Model update(generateAmount(int count), Model m) = m[generateAmount = count > 0 && count < 100 ? count : m.generateAmount];
Model update(newGrammar(str x), Model m) = m[grammarText=x];
Model update(storeInput(), Model m) = m[examples= [m.tree] + m.examples];
Model update(refreshGrammar(), Model m) {
  println("refreshGrammar!");
  try {
    m.grammar = refreshGrammar(m.grammar, m.grammarText);
    
    println("new grammar received.");
    
    // then reparse the input
    try {
      m.tree = reparse(m.grammar, m.tree);
      println("input reparsed.");
    }
    catch ParseError (l) :
      m.errors = "parse error in input at <l>";
    
    println("reparsing <size(m.examples)> examples");
    
    // and reparse the examples
    m.examples = for (Tree ex <- m.examples) {
      try {
        println("reparsing <ex>");
        append(reparse(m.grammar, ex));
      }
      catch ParseError(e) :
        m.errors += "parse error in example \'<ex>\' at <e>\n\n";
    }
    
    m.errors = "";
  }
  catch value x: 
    m.errors = "grammar could not be processed due to <x>";
  
  return m;
}

Model update(newInput(str new), Model m) {
  try {
    m.tree = completeLocs(parse(m.grammar, new, allowAmbiguity=true));
    m.errors = "";
  }
  catch ParseError(l) : {
    //m.tree = appl(regular(lit("parse error")), [char(i) | i <- chars(new)]);
    m.errors = "parse error in input at <l>";
  }
  
  return m;
}

Model update(simplify(), Model m) {
  m.tree=completeLocs(reparse(m.grammar, simplify(m.grammar, m.tree)));
  m.tree = m.tree;
  return m;
}

Model update(freshSentence(), Model m) = freshSentences(m);

Model freshSentences(Model m) {
  if (options:{_,*_} := randomAmbiguousSubTrees(m.grammar, m.generateAmount)) {
    m.examples += sort(options, bool (Tree l, Tree r) { return size("<l>") < size("<r>"); });
  }
  
  return m;
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
 
Msg onNewSentenceInput(str t) = newInput(t);
 
void view(Model m) {
   
   
   container(true, () {
     ul(class("tabs nav nav-tabs"), id("tabs"), () {
       li(() {
         a(tab(), href("#input"), "Input"); 
       });
       li(class("active"), () {
         a(tab(), href("#graphic"), "Graphic");
       });
       li(() {
         a(tab(), href("#grammar"), "Grammar"); 
       });
       li(() {
         a(tab(), href("#diagnose"), "Diagnosis"); 
       });
       li(() {
         a(tab(), href("#help"), "Help"); 
       });
    });
        
    div(id("main-tabs"), class("tab-content"), () {
      div(class("tab-pane fade in"), id("input"), () {
        inputPane(m);
     });
     
     div(class("tab-pane active"), id("graphic"), () {
        graphicPane(m);
      });
      div(class("tab-pane fade in"), id("grammar"), () {
        grammarPane(m);
      });
      div(class("tab-pane fade in"), id("diagnose"), () {
          diagnose(m.tree); 
      });
      div(class("tab-pane fade in"), id("help"), () {
        h3("What is ambiguity?");
      });
    });
    
    if (m.errors != "") {
      row(() {
        column(10, md(), () {
           div(class("alert"), class("alert-danger"), role("alert"), () {
              paragraph(m.errors);
           });
        });
      });
    }
  });
}

void grammarPane(Model m) {
  row(() {
          column(10, md(), () {
            textarea(class("form-control"), style(<"width","100%">), rows(25), onInput(Msg (str t) { return newGrammar(t); }), \value(m.grammarText), m.grammarText);
          });
          column(2, md(), () {
            button(class("list-group-item"), onClick(refreshGrammar()), "Refresh");
          });
  });
}

void inputPane(Model m) {
bool isAmb = amb(_) := m.tree;
   bool nestedAmb = amb({/amb(_), *_}) := m.tree || appl(_,/amb(_)) := m.tree;
   str  sentence = "<m.tree>";
   
row(() {
          column(10, md(), () {
             textarea(class("form-control"), style(<"width","100%">), rows(10), onInput(onNewSentenceInput), \value(sentence), sentence); 
          });    
          column(2, md(), () {
            div(class("list-group list-group-flush"), style(<"list-style-type","none">), () {
              span(class("list-group-item"), () {
                paragraph("This sentence is <if (!isAmb) {>not<}> ambiguous, and it has<if (!nestedAmb) {> no<}> nested ambiguity.");
              });
              if (nestedAmb) {          
                button(class("list-group-item"), onClick(focus()), "Focus on nested");
              }
              if (m.tree notin m.examples) {          
                button(class("list-group-item"), onClick(storeInput()), "Stash");
              }
              button(class("list-group-item"), onClick(simplify()), "Simplify");
              button(class("list-group-item"), onClick(freshSentence()), "Generate");
              div(class("list-group-item"), class("dropdown"),  () {
                button(class("btn"), class("btn-secondary"), class("dropdown-toggle"), \type("button"), id("dropdownMenuButton"), dropdown(), hasPopup(true), expanded(false), 
                  "Amount: <m.generateAmount>");
                div(class("dropdown-menu"), labeledBy("dropdownMenuButton"), () {
                  for (int i <- [1..26]) {
                    button(class("dropdown-item"), href("#"), onClick(generateAmount(i)),  "<i>");
                  }
                });
              });
            });
          });
        });
        
        if (m.examples != []) { 
          ruleCount = (0 | it + 1 | /prod(_,_,_) := m.grammar.definitions);
          
          row(() {
            column(10, md(), () {
              table(class("table"), class("table-hover"), class("table-sm"), () {
                colgroup(() {
                  col(class("col-sm-1"));
                  col(class("col-sm-1"));
                  col(class("col-sm-7"));
                  col(class("col-sm-1"));
                });
                thead(() {
                  th(scope("col"), "#");
                  th(scope("col"),"Syntax category");
                  th(scope("col"),"Sentence");
                  th(scope("col"),"Status");
                  th(scope("col"),"Select");
                  th(scope("col"),"Remove");
                });
                tbody(() {
                  int count = 0;
                  for (Tree ex <- m.examples) {
                    exs = Util::symbol(ex);
                    
                    tr( () {
                      count += 1;
                      td("<count>");
                      td("<symbol2rascal(exs)>");
                      td(() {
                        pre(class("pre-scrollable"), "<ex>");
                      });
                      td(/amb(_) := ex ? "amb." : "not amb.");
                      td(() {
                           button(class("button"), onClick(selectExample(ex)), "use");
                      });
                      td(() {
                         button(class("button"), onClick(removeExample(count)), "rm");
                      });
                    });
                  }
                });
              });
            });
          });
        } 
}

void graphicPane(Model m) {
bool isAmb = amb(_) := m.tree;
   bool nestedAmb = amb({/amb(_), *_}) := m.tree || appl(_,/amb(_)) := m.tree;
   
row(() {
          column(10, md(), () {
            graphic(m);
          });
          column(2, md(), () {
		        div(class("list-group"), style(<"list-style-type","none">), () {
		          span(class("list-group-item"), () {
                  paragraph("This tree is <if (!isAmb) {>not<}> ambiguous, and it has<if (!nestedAmb) {> no<}> nested ambiguity.");
                });
                if (nestedAmb) {          
                  button(class("list-group-item"), onClick(focus()), "Focus on nested");
                }
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

