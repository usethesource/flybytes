module DrAmbiguity

import DateTime;
// import salix::lib::Dagre;
import salix::Core;
import salix::HTML;
import salix::Node;
import salix::Index;
// import salix::SVG; 
import salix::App;
import salix::lib::Bootstrap;
// extend salix::lib::CodeMirror;
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
import util::Maybe;
import ValueIO;

private loc www = |http://localhost:7000/index.html|;
private loc root = |project://drambiguity/src|;

@synopsis{start DrAmbiguity with a fresh grammar and an example input sentence}
App[Model] drAmbiguity(type[&T <: Tree] grammar, loc input) 
  = drAmbiguity(model(grammar, input=readFile(input)));
  
@synopsis{Continue DrAmbiguity with a previously saved project}  
App[Model] drAmbiguity(loc project) 
  = drAmbiguity(readBinaryValueFile(#Model, project));

@synopsis{start DrAmbiguity with a fresh grammar and an example input sentence}
App[Model] drAmbiguity(type[&T <: Tree] grammar, str input) 
  = drAmbiguity(model(grammar, input=input));

@synopsis{start DrAmbiguity with a fresh grammar and no input sentence yet}
App[Model] drAmbiguity(type[&T <: Tree] grammar) 
  = drAmbiguity(model(grammar));
  
@synopsis{start DrAmbiguity with a fresh grammar and a corresponding example (ambiguous) example tree}  
App[Model] drAmbiguity(type[&T <: Tree] grammar, &T input) 
  = drAmbiguity(model(completeLocs(input), grammar));

@synopsis{This is the internal work horse that boots up the Salix application that is called DrAmbiguity.}  
App[Model] drAmbiguity(Model m) 
  = webApp(makeApp("drAmbiguity", Model () { return m; }, view, update), www);

data Model 
  = model(type[Tree] grammar,
      str input = "",
      Maybe[Tree] tree = saveParse(grammar, input),
      Maybe[loc] file = just(|home:///myproject.dra|),
      bool inputDirty = false,
      str grammarText = trim(grammar2rascal(Grammar::grammar({}, grammar.definitions))),
      bool grammarDirty = false,
      str commitMessage = "",
      lrel[datetime stamp, str msg, str grammar] grammarHistory = [<now(), "original", trim(grammar2rascal(Grammar::grammar({}, grammar.definitions)))>],
      lrel[str input, Symbol nt, Maybe[Tree] tree, str status]  examples = [],
      int generateAmount = 5, 
      list[str] errors = [],
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
   | selectExample(int count)
   | removeExample(int count)
   | generateAmount(int count)
   | storeInput()
   | newGrammar(str x)
   | commitGrammar(int selector)
   | setStartNonterminal(Symbol s)
   | clearErrors()
   | removeGrammar(int count)
   | saveProject(loc file)
   | loadProject(loc file)
   | filename(loc file)
   | nofilename()
   | commitMessage(str msg)
   ;

Model update(clearErrors(), Model m) = m[errors=[]];
Model update(labels(), Model m) = m[labels = !m.labels];
Model update(literals(), Model m) = m[literals = !m.literals];
Model update(\layout(), Model m) = m[\layout = !m.\layout];
Model update(Msg::chars(), Model m) = m[chars = !m.chars];
Model update(Msg::shared(), Model m) = m[shared = !m.shared];
Model update(Msg::focus(), Model m) = focus(m);
Model update(Msg::filename(loc f), Model m) = m[file=just(f)];
Model update(nofilename(), Model m) = m[file=nothing()];
Model update(commitMessage(str msg), Model m) = m[commitMessage=msg];
Model update(removeGrammar(int count), Model m) = m[grammarHistory = m.grammarHistory[0..count-1] + m.grammarHistory[count..]];

Model update(loadProject(loc f), Model m) {
  try {
    m = readBinaryValueFile(#Model, f);
    m.errors = [];
    return m;
  } 
  catch value x: {
    m.errors += ["IO exception: <x>"];
    return m;
  }
}

Model update(saveProject(loc f), Model m) {
  writeBinaryValueFile(f, m);
  return m;
}
 
Model update(selectExample(int count), Model m) {
  m.tree = m.examples[count-1].tree;
  m.grammar = type[Tree] ng := type(m.examples[count-1].nt, m.grammar.definitions) ? ng : m.grammar;
  m.input = m.examples[count-1].input;
  m.inputDirty = true;
  if (m.tree == nothing()) {
    m.errors += ["input sentence has parse error"];
  }
  return m;
}

Model update(removeExample(int count), Model m) = m[examples = m.examples[0..count-1] + m.examples[count..]];
Model update(generateAmount(int count), Model m) = m[generateAmount = count > 0 && count < 101 ? count : m.generateAmount];
Model update(newGrammar(str x), Model m) {
  m.grammarText=x;
  m.grammarDirty=false;
  return m;
}

str status(nothing()) = "error";
str status(just(Tree x)) = "no amb." when /amb(_) !:= x;
default str status(just(Tree x)) = "amb";

Model update(storeInput(), Model m) = m[examples= [<m.input, Util::symbol(m.tree.val), m.tree, status(m.tree)>] + m.examples];

Model update(setStartNonterminal(Symbol s), Model m) {
  if (type[Tree] new := type(s, m.grammar.definitions)) {
    m.grammar = new;
    
    try {
      m.tree = just(reparse(m.grammar, m.input));
      m.inputDirty = false;
      m.errors = [];
    }
    catch ParseError (l) : {
      m.errors += ["parse error in input at <l>"];
      m.tree = nothing();
    }
    catch value v: {
      m.errors += ["unexpected error: <v>"];
      m.tree = nothing();
    }
  }
  
  return m;
}

Model update(Msg::commitGrammar(int selector), Model m) {
  try {
    str newGrammar = "";
    
    if (selector == -1) {
      m.grammarHistory = [<now(), m.commitMessage, m.grammarText>] + m.grammarHistory;
      newGrammar = m.grammarText;
      m.grammarDirty = false;
    }
    else {
      newGrammar = m.grammarHistory[selector-1].grammar;
      m.grammarText = m.grammarHistory[selector-1].grammar;
      m.grammarDirty = true;
    }
    
    m.commitMessage = "";
    m.errors = [];
    m.grammar = commitGrammar(m.grammar.symbol, newGrammar);
        
    // then reparse the input
    try {
      m.tree = just(reparse(m.grammar, m.input));
    }
    catch ParseError (l) : {
      m.tree = nothing();
      m.errors += ["parse error in input at <l>"];
    }
    catch value v: {
      m.error += ["unexpected error: <v>"];
      m.tree = nothing();
    }
    
    // and reparse the examples
    m.examples = for (<str ex, Symbol s, Maybe[Tree] t, str st> <- m.examples) {
      try {
        t = reparse(m.grammar, s, ex);
        append <ex, s, just(t), status(just(t))>;
      }
      catch ParseError(e) :
        append <ex, s, nothing(), status(nothing())>;
      catch value v: {
        append <ex, s, nothing(), status(nothing())>;
        m.errors += ["unexpected error: <v>"];
      }  
    }
  }
  catch value x: 
    m.errors += ["grammar could not be processed due to <x>"];
  
  return m;
}

Model update(newInput(str new), Model m) {
  try {
    m.input = new;
    m.tree = saveParse(m.grammar, new);
    m.errors = [];
    m.inputDirty = false;
  }
  catch ParseError(l) : {
    m.errors += ["parse error in input at <l>"];
    m.tree = nothing();
    m.inputDirty = false;
  }
  
  return m;
}

Model update(simplify(), Model m) {
  m.tree=just(completeLocs(reparse(m.grammar, simplify(m.grammar, m.tree.val))));
  m.tree = m.tree;
  m.input = "<m.tree.val>";
  m.inputDirty = true;
  return m;
}

Model update(freshSentence(), Model m) = freshSentences(m);

Model freshSentences(Model m) {
  if (options:{_,*_} := randomAmbiguousSubTrees(m.grammar, m.generateAmount)) {
    new = m.examples == [] ? [*options] : [op | op <- options, !any(e <- m.examples, just(op) := e.tree)];
    if (new != []) {
      m.examples += [<"<n>", Util::symbol(n), just(completeLocs(n)), status(just(n))> | n <- new];
      m.errors = [];
    }
    else {
      m.errors += ["no new ambiguous sentences found; only <size(options)> existing examples."];
    }
    
    return m;
  }
  
  m.errors += ["no ambiguous sentences found\n"];
  return m;
}

void graphic(Model m) {
   str id(Tree a:appl(_,_)) = "N<a@unique>";
   str id(Tree a:amb(_))    = "N<a@unique>";
   str id(Tree a:char(_))   = "N<a@unique>";
   str id(Tree a:cycle(_,_))   = "N<a@unique>";
   
   str lbl(appl(p,_)) = m.labels ? "<symbol2rascal(delabel(p.def))> = <prod2rascal(p)>" : prodlabel(p);
   str lbl(amb({appl(p,_), *_})) = m.labels ? "<symbol2rascal(delabel(p.def))>" : "amb";
   str lbl(char(9)) = "⇥"; // tab
   str lbl(char(10)) = "␤"; // newline
   str lbl(char(11)) = "⤓"; // vt
   str lbl(char(12)) = "⇟"; // ff
   str lbl(char(13)) = "⏎"; // carriage return
   str lbl(char(32)) = "␠"; //space
   str lbl(char(int i)) = "␠␠" when i in {133,160,5760,6158,8232,8239,8233,8287,12288} || (i >= 8192 && i <= 8202);
   // [\u0009-\u000D \u0020 \u0085 \u00A0 \u1680 \u180E \u2000-\u200A \u2028 \u2029 \u202F \u205F \u3000
   default str lbl(c:char(int ch))       = "<c>";
   str lbl(cycle(s, i))          = "<s> (<i>)";
   
   str shp(appl(prod(_,_,_),_)) = "rect";
   str shp(appl(regular(_),_)) = "ellipse";
   str shp(amb(_))    = "diamond";
   str shp(char(_))   = "circle";
   str shp(cycle(_,_))   = "circle";
   
   list[Tree] args(appl(_,a)) = a;
   list[Tree] args(amb(alts)) = [*alts];
   list[Tree] args(char(_))   = [];
   list[Tree] args(cycle(_,_))   = [];
   
   t = !m.shared ? unique(m.tree.val) : shared(unique(m.tree.val));

  //  dagre("Forest",  style(<"overflow-x","scroll">,<"overflow-y","scroll">,<"border", "solid">,<"border-radius","5px">,<"height","600px">,<"width","100%">), rankdir("TD"), (N n, E e) {
  //        done = {};
         
  //        void nodes(Tree a) {
  //          if (id(a) in done) return;
  //          if (!m.literals && isLiteral(a)) return;
  //          if (!m.\layout && isLayout(a)) return;
  //          if (!m.chars && isChar(a)) return;
           
  //          n(id(a), fill("black"), shape(shp(a)), () { 
  //              span(style(<"color","black">), lbl(a));
  //          });
           
  //          done += {id(a)};
           
  //          if (a@unique > 500) {
  //            return;
  //          }
           
  //          for (b <- args(a)) {
  //            nodes(b);
  //          }
  //        }
         
  //        void edges(Tree a) {
  //          if (!m.literals && isLiteral(a)) return;
  //          if (!m.\layout && isLayout(a)) return;
  //          if (!m.\layout && isChar(a)) return;
  //          if (id(a) in done) return;
           
  //          for (b <- args(a)) {
  //             if (!m.literals && isLiteral(b)) continue;
  //             if (!m.\layout && isLayout(b)) continue;
  //             if (!m.chars && isChar(b)) continue;
           
  //             e(id(a), id(b), lineInterpolate("linear"));
  //          }
           
  //          done += {id(a)};
           
  //          if (a@unique > 500) {
  //            return;
  //          }
           
  //          for (b <- args(a)) {
  //            edges(b);
  //          }
  //        }
         
  //        nodes(t);
  //        done = {};
  //        edges(t);
  //        done = {};
  //      });
}
 
Msg onNewSentenceInput(str t) = newInput(t);
Msg onNewGrammarInput(str t) = newGrammar(t); 
 
void view(Model m) {
   container(true, () {
     ul(class("tabs nav nav-tabs"), id("tabs"), () {
       li(() {
         fileUI(m);
       });
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
          if (m.tree is just) {
            diagnose(m.tree.val);
          }
          else {
            paragraph("Diagnosis of ambiguity is unavailable while the input sentence has a parse error.");
          } 
      });
      
      div(class("tab-pane fade in"), id("help"), () {
        h3("What is ambiguity?");
      });
    });
    
    if (m.errors != []) {
      row(() {
        column(10, md(), () {
           for (e <- m.errors) {
             div(class("alert"), class("alert-danger"), role("alert"), () {
                paragraph(e);
             });
           }
        });
        column(2, md(), () {
          div(class("list-group list-group-flush"), style(<"list-style-type","none">), () {
            button(class("list-group-item"), onClick(clearErrors()), "Clear");
          });
        });
      });
    }
  });
}

Msg onCommitMessageInput(str m) {
  return commitMessage(m);
}

void grammarPane(Model m) {
  row(() {
    column(10, md(), () {
      if (m.grammarDirty) {
        textarea(class("form-control"), style(<"width","100%">), rows(25), onInput(onNewGrammarInput), \value(m.grammarText), m.grammarText);
      }
      else {
        textarea(class("form-control"), style(<"width","100%">), rows(25), onInput(onNewGrammarInput), m.grammarText);
      }
    });
    column(2, md(), () {
      input(class("list-group-item"), style(<"width","100%">), \type("text"), onInput(onCommitMessageInput), m.commitMessage);
      if (trim(m.commitMessage) != "") {
        button(class("list-group-item"), onClick(commitGrammar(-1)), "Commit");
      }
      else {
        button(class("list-group-item"), disabled(), "Commit");
      }
    });
  });
  
  if (m.grammarHistory != []) { 
          row(() {
            column(10, md(), () {
              table(class("table"), class("table-hover"), class("table-sm"), () {
                colgroup(() {
                  col(class("col-sm-1"));
                  col(class("col-sm-5"));
                  col(class("col-sm-1"));
                  col(class("col-sm-1"));
                });
                thead(() {
                  th(scope("col"), "Version");
                  th(scope("col"),"Message");
                  th(scope("col"),"Revert");
                  th(scope("col"),"Remove");
                });
                tbody(() {
                  int count = 1;
                  for (<datetime stamp, str msg, str grammar> <- m.grammarHistory) {
                    tr( () {
                      td(printDateTime(stamp, "dd-MM-yyyy HH:mm:ss"));
                      td(msg);
                      td(() {
                           button(class("button"), onClick(commitGrammar(count)), "revert");
                      });
                      td(() {
                         button(class("button"), onClick(removeGrammar(count)), "rm");
                      });
                    });
                    count += 1;
                  }
                });
              });
            });
          });
        } 
}

Msg newAmountInput(int i) {
  return generateAmount(i);
}

Msg loadProjectInput(str file) {
 if (/C:\\fakepath\\<name:.*>/ := file) { 
   return loadProject(|home:///| + name);
 }
 else {
   return loadProject(|home:///| + file);
 }
}

Msg onProjectNameInput(str f) {
  if (trim(f) != "") {
    return filename((|home:///| + f)[extension="dra"]);
  }
  else {
    return nofilename();
  }
}

void fileUI(Model m) {
  div(class("list-group-item"), class("dropdown"),  () {
                a(class("dropdown-toggle"), \type("button"), id("projectMenu"), dropdown(), hasPopup(true), expanded(false), "File");
                div(class("dropdown-menu"), labeledBy("nonterminalChoice"), () {
                    input(class("list-group-item"), \type("text"), onInput(onProjectNameInput), \value(m.file != nothing() ? (m.file.val[extension=""].path[1..]) : ""));
                    if (m.file != nothing()) {
                      button(class("list-group-item"), onClick(saveProject(m.file.val)), "Save");
                    }
                    button(class("list-group-item"), attr("onclick", "document.getElementById(\'loadProjectButton\').click();"), "Load…");
                    input(\type("file"), attr("accept",".dra"), style(<"display", "none">), id("loadProjectButton"), onInput(loadProjectInput));
                });
   });
 }
 
void inputPane(Model m) {
   bool isError = m.tree == nothing();
   bool isAmb = m.tree != nothing() && amb(_) := m.tree.val ;
   bool nestedAmb = m.tree != nothing() && (amb({/amb(_), *_}) := m.tree.val || appl(_,/amb(_)) := m.tree.val);
   str  sentence = m.input;
   
   row(() {
          column(10, md(), () {
             if (m.inputDirty) {
               textarea(class("form-control"), style(<"width","100%">), rows(10), onInput(onNewSentenceInput), \value(sentence), sentence);
             }
             else {
               textarea(class("form-control"), style(<"width","100%">), rows(10), onInput(onNewSentenceInput), sentence);
             } 
          });    
          column(2, md(), () {
            div(class("list-group list-group-flush"), style(<"list-style-type","none">), () {
              span(class("list-group-item"), () {
                if (isError) {
                  paragraph("This sentence is not a <m.grammar>; it has a parse error");
                } 
                else {
                  paragraph("This sentence is <if (!isAmb) {>not<}> ambiguous, and it has<if (!nestedAmb) {> no<}> nested ambiguity.");
                }
              });
              if (nestedAmb) {          
                button(class("list-group-item"), onClick(focus()), "Focus on nested");
              }
              if (m.tree is just) {          
                button(class("list-group-item"), onClick(storeInput()), "Stash");
              }
              if (isAmb || nestedAmb) {
                button(class("list-group-item"), onClick(simplify()), "Simplify");
              }
              button(class("list-group-item"), onClick(freshSentence()), "Generate");
              input(class("list-group-item"), \type("range"), \value("5"), min("1"), max("100"), onInput(newAmountInput));
              div(class("list-group-item"), class("dropdown"),  () {
                button(class("btn"), class("btn-secondary"), class("dropdown-toggle"), \type("button"), id("nonterminalChoice"), dropdown(), hasPopup(true), expanded(false), 
                  "Start: <symbol2rascal(m.grammar.symbol)>");
                div(class("dropdown-menu"), labeledBy("nonterminalChoice"), () {
                    for (Symbol x <- sorts(m.grammar)) {
                        button(class("list-group-item"), href("#"), onClick(setStartNonterminal(x)),  "<symbol2rascal(x)>");
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
                  for (<inp, exs, t, st> <- m.examples) {
                    
                    tr( () {
                      count += 1;
                      td("<count>");
                      td("<symbol2rascal(exs)>");
                      td(() {
                        pre(() { code(inp); });
                      });
                      td(st);
                      td(() {
                           button(class("button"), onClick(selectExample(count)), "use");
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
  if (m.tree is nothing) {
    paragraph("Graphical parse tree representation unavailable due to parse error in input sentence.");
    return;
  }
  
  bool isAmb = amb(_) := m.tree.val;
  bool nestedAmb = amb({/amb(_), *_}) := m.tree.val || appl(_,/amb(_)) := m.tree.val;
   
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
  ambs = [a | /Tree a:amb(_) := m.tree.val];
  
  m.tree.val = ambs[arbInt(size(ambs))];
  m.input = "<m.tree.val>";
  m.inputDirty = true;
  
  return m;
}
 
str prodlabel(regular(Symbol s)) = symbol2rascal(s);
str prodlabel(prod(label(str x,_),_,_)) = x;
str prodlabel(prod(_, list[Symbol] args:[*_,lit(_),*_],_)) = "<for (lit(x) <- args) {><x> <}>";
default str prodlabel(prod(Symbol s, _,_ )) = symbol2rascal(s);



