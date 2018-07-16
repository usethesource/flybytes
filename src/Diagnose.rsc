module Diagnose

import ParseTree;
import Set;
import String;
import Brackets;
import Util;
import lang::rascal::format::Grammar;
import salix::HTML;
import salix::Node;
import salix::lib::Bootstrap;
import IO;

void diagnose(Tree t:amb(_)) = findCauses(t);
                              
default void diagnose(Tree _) = paragraph("This tree is not ambiguous at the top. Diagnostics are not enabled.");

void findCauses(Tree t:amb(set[Tree] alts)) {
  if (size(alts) > 2) {
    p(() {
      text("This ambiguity cluster has <size(alts)> alternatives. Each possible combination of two alternatives are analyzed for differences below.");
    });
    
    int count = 1;
    
    for ({Tree x, Tree y, *_} := alts) {
      h2("Combination <count>");
      count += 1;
      
      report(x, y);
    }
  } else if ({Tree x, Tree y} := alts) {
    p(() {
      text("This ambiguity cluster has two alternatives which are analyzed for differences below.");
    });

    report(x, y);
  }
}

void report(Tree x, Tree y) {
  if (/amb(_) := x || /amb(_) := y) {
    paragraph("More ambiguous trees are nested inside either alternative, which can be confusing. It is advisable to focus on the simplest ambiguous substring.");
  }
  
  ruleDifferences(x, y);
  operatorPrecedence(x, y);
  tokens(x, y);
  //verticalCauses(x, y, pX, pY)>
  
}

void paragraph(str x) = p(() { text(x); });    
    
list[tuple[&T,&T]] zipFill(list[&T] x, list[&T] y, &T fill)
  = [<i < sX ? elementAt(x,i) : fill, i < sY ? elementAt(y,i) : fill> | i <- (sX > sY ? index(x) : index(y)) ]
  when sX := size(x), sY := size(y);
      
void ruleDifferences(Tree x, Tree y) {
  h3("Rule differences");
  
  pX = { p | /Production p := x };
  pY = { p | /Production p := y };
  
  if (pX == pY) {
    paragraph("The alternatives use the same rules.");
  }
  else {
    table(class("table"), class("table-hover"), class("table-sm"), () {
      thead(() {
          th(attr("scope", "col"), () {
            text("Rules unique to tree one");
          });
          th(attr("scope", "col"), () {
            text("Rules unique to tree two");
          });
        });
        tbody(() {
          for (<p,q> <- zipFill([topProd2rascal(e) | e <- sort(pX - pY)], [topProd2rascal(e) | e <- sort(pY - pX)], "")) {
            tr(() {
              td(() {
                if (p != "")
                  pre(() { code(p); });
              });
              td(() {
                if (q != "")
                  pre(() { code(q); });
              });
            });
          }        
        });
    });
  }
  
  if (appl(prodX,_) := x, appl(prodY,_) := y) {
    if (prodX == prodY) {
      paragraph("The alternatives have the same rule at the top:");
      rules([prodX]);
    } 
    else {
      paragraph("The alternatives have different rules at the top:");
      table(class("table"), class("table-hover"), class("table-sm"), () {
        thead(() {
          th(attr("scope", "col"), () {
            text("Top rule of tree one");
          });
          th(attr("scope", "col"), () {
            text("Top rule of tree two");
          });
        });
        tbody(() {
          tr(() {
            td(() {
              pre(() { code(topProd2rascal(prodX)); });
            });
            td(() {
              pre(() { code(topProd2rascal(prodY)); });
            });
          });       
        }); 
      });
    }
  }
}

void operatorPrecedence(Tree x, Tree y) {
  if ((/appl(Production p, [*np, xq:appl(Production q, _)]) := x && /appl(q, [yp:appl(p, _), *yr]) := y) 
    || (/appl(Production p, [*np, xq:appl(Production q, _)]) := y && /appl(q, [yp:appl(p, _), *yr]) := x)) {
    h3("Operator precedence"); 
    row(() {
      column(12, md(), () {
        paragraph("Parentheses (...) indicate the precedence for the left and the right tree. Appropriate disambiguations for each alternative are shown below.");
      });
      column(6, md(), () {
        pre(() { code("<appl(p, [*np, wrap(xq)])>"); });
        if (p != q) {
          rules([
            priority(p.def, [q, p]),
            associativity(p.def, \right(), {q, p})
          ]);
        } else {
          rules([
            p[attributes=p.attributes+{\assoc(\right())}]
          ]);
        }
      });
      column(6, md(), () {
        pre(() { code("<appl(q, [wrap(yp), *yr])>"); });
        if (p != q) {
          rules([
            priority(p.def, [p, q]),
            associativity(p.def, \left(), {p, q})
          ]);
        } else {
          rules([
            p[attributes=p.attributes+{\assoc(\left())}]
          ]);
        }
      });
    });
  }
}


alias Word = tuple[loc pos, Symbol cat, str word];
alias Words = set[Word];

void tokens(Tree x, Tree y) {
  bool isWord(Tree t)  = s is \lex || s is \lit || s is \cilit || s is \layout when s := Util::symbol(t);
   
  Words collect(Tree t) = {<n@\loc, symbol(n), "<n>"> | /n:appl(_,_) := t, isWord(n)};
  
  Words wordsX = collect(x);
  Words wordsY = collect(y);
  
  h3("Word differences");

  if (wordsX == wordsY) {
    paragraph("Both alternatives have split up the input into the exact same words, so there is no lexical ambiguity.");
  } 
  else {
    paragraph("The alternatives have split up the input into different words, so ambiguity is caused (among possible other conjunctive causes) 
              'by overlapping lexical categories.");
              
    // TODO: show different highlights
              
    void listWords(Words ws) {
      table(class("table"), class("table-hover"), class("table-sm"), () {
        thead(() {
          th(attr("scope", "col"), () {
            text("Offset");
          });
          th(attr("scope", "col"), () {
            text("Category");
          });
          th(attr("scope", "col"), () {
            text("Word");
          });
        });
        tbody(() {
          for (<pos, cat, word> <- sort(ws, bool (Word a, Word b) { return (a.pos.offset?0) < (b.pos.offset?0); })) {
            tr(() {
              th(attr("scope", "row"), () {
                text("<pos.offset?0>");
              });
              td(() {
                text("<type(cat, ())>");
              });
              td(() {
                text(word);
              });
            });
          }        
        });
      });
    }
    
    row(() {
      column(6, md(), () {
        paragraph("Words unique to the one alternative");
        listWords(wordsX - wordsY);
      });
      column(6, md(), () {
        paragraph("Words unique to the other alternative");
        listWords(wordsY - wordsX);
      });
    });          
  }
  
 Words litX = {t | t <- wordsX, lit(_) := t.cat || cilit(_) := t.cat};
 Words litY = {t | t <- wordsY, lit(_) := t.cat || cilit(_) := t.cat};
 
 Words lexX = {t | t <- wordsX, lex(_) := t.cat};
 Words lexY = {t | t <- wordsY, lex(_) := t.cat};
 
 litParentsX = {<p,l> | /t:appl(p,[_*,l:appl(prod(l,_,_),_),_*]) := x, lit(_) := l || cilit(_) := l};
 litParentsY = {<p,l> | /t:appl(p,[_*,l:appl(prod(l,_,_),_),_*]) := y, lit(_) := l || cilit(_) := l};
 
 unreserved
    = { <posX.offset, lc, c> | <posX, lc, _> <- litX - litY, <posY, c, _>  <- lexY - lexX, posX.offset == posY.offset, posX.length == posY.length}
    + { <posY.offset, lc, c> | <posX, c, _>  <- lexX - lexY, <posY, lc, _> <- litY - litX, posX.offset == posY.offset, posY.length == posX.length};
    
 if (unreserved != {}) {
   h3("Unreserved keywords");
   paragraph("Literal keywords in the one alternative have been re-interpreted as other lexical categories (i.e. identifiers) in the other alternative."); 
   
   table(class("table"), class("table-hover"), class("table-sm"), () {
       thead(() {
         th(scope("col"), "Offset");
         th(scope("col"), "Literal");
         th(scope("col"), "Lexical");
       });
       tbody(() {
         for (<p, lc, c> <- sort(unreserved, bool (<int of1, _, _>, <int of2, _, _>) { return of1 < of2; })) {
           tr(() {
             th(scope("row"), "<p>");
             td("<symbol2rascal(lc)>");
             td("<symbol2rascal(c)>");
           });
         }
       });
     });
     
   paragraph("Suggested disambiguations:");
   ul(class("list-unstyled"), () {
     for (<_, cLit, cLex> <- unreserved) {
       li(() {
         pre(() { code(symbol2rascal(conditional(cLex, {delete(cLit)}))); });
       });
     }  
   });
 }
 
 
 
 if (litParentsX != litParentsY) {
   h3("Overloaded literals");
   paragraph("Literal keywords in the one alternative have been reused for other purposes in the other alternative. This is one of the 
             '(conjunctive) causes of ambiguity.");
   
   paragraph("Productions sharing the same keywords:");
   table(class("table"), class("table-hover"), class("table-sm"), () {
     for (<ppX, l, ppY> <- litParentsX<0,1,1> o litParentsY<1,0>) {
       tr(() {
         td(() {
           text(symbol2rascal(l));
         });
         td(() {
           text(topProd2rascal(ppX));
         });
         td(() {
           text(topProd2rascal(ppY));
         });
       });
     }
   });
 } 
  
 if (lexX != lexY) {
 //   // tuple[loc pos, Symbol cat, str word]
   samePosDifferentLength
     = { <posX.offset, c, w1, w2> | <posX, c, w1> <- lexX, <posY, c, w2> <- lexY, posX.offset == posY.offset, posX.length != posY.length};
     
   if (samePosDifferentLength != {}) {
     h3("Longest match");
     paragraph("The following words start at the same position, but are of different length between the alternatives:");
     table(class("table"), class("table-hover"), class("table-sm"), () {
       thead(() {
         th(attr("scope", "col"), () {
           text("Offset");
         });
         th(attr("scope", "col"), () {
           text("Category");
         });
         th(attr("scope", "col"), () {
           text("Word 1");
         });
         th(attr("scope", "col"), () {
           text("Word 2");
         });
       });
       tbody(() {
         for (<p, c, w1, w2> <- sort(samePosDifferentLength, bool (<int of1, _, _, _>, <int of2, _, _, _>) { return of1 < of2; })) {
           tr(() {
             th(attr("scope","row"), () {
               text("<p>");
             });
             td(() {
               text("<symbol2rascal(c)>");
             });
             td(() {
               text(w1);
             });
             td(() {
                 text(w2);
             });           
           });
         }
       });
     });
     
     follows = { prod(c, [*yy, conditional(ll, {\not-follow(cc)})], aa) 
               | <_, c, _, _> <- samePosDifferentLength, 
               /Production p:prod(c, [*yy, ll:\iter(cc:\char-class(_))],aa) := amb({x, y})
               };
               
     if (follows != {} ) {
       paragraph("Suggested follow restrictions:");
       ul(class("list-unstyled"), () {
         for (f <- follows) {
           li(() {
              pre(() { code(topProd2rascal(f)); } );
           });
         }
       });
     }
  }   
     
  shorterLitPrefixLex
    = { <posX.offset, c, lc, w1, w2> | <posX, lc, w1> <- litX - litY, <posY, c, w2>  <- lexY - lexX, posX.offset == posY.offset, posX.length < posY.length}
    + { <posY.offset, c, lc, w1, w2> | <posX, c, w1>  <- lexX - lexY, <posY, lc, w2> <- litY - litX, posX.offset == posY.offset, posY.length < posX.length};
   
   // TODO: remove if literal is used in the definition of the lexical!
    
  if (shorterLitPrefixLex != {}) {
     h3("First match");
     
     paragraph("The following literals overlap with the starts of other lexical categories:");
     table(class("table"), class("table-hover"), class("table-sm"), () {
       thead(() {
         th(scope("col"), "Offset");
         th(scope("col"), "Literal");
         th(scope("col"), "Lexical");
         th(scope("col"), "Words");
       });
       tbody(() {
         for (<p, lc, c, w1, w2> <- sort(shorterLitPrefixLex, bool (<int of1, _, _, _, _>, <int of2, _, _, _, _>) { return of1 < of2; })) {
           tr(() {
             th(scope("row"), "<p>");
             td("<symbol2rascal(c)>");
             td("<symbol2rascal(lc)>");
             td("\'<w1>\' vs. \'<w2>\'");
           });
         }
       });
     });
     
     preceeds = {prod(c, [conditional(cc, {\not-precede(cc)}), *yy], aa) 
                | <_, c, _, _, _> <- shorterLitPrefixLex, 
                /Production p:prod(c, [cc:\char-class(_), *yy],aa) := amb({x, y})
                };
               
     if (preceeds != {} ) {
       paragraph("Suggested preceed restrictions:");
       ul(class("list-unstyled"), () {
         for (f <- preceeds) {
           li(() {
              pre(() { code(topProd2rascal(f)); });
           });
         }
       });
     }
   }
 } 
}

str verticalCauses(Tree x, Tree y, set[Production] pX, set[Production] pY) {
  return exceptAdvise(x, y, pX, pY)
       + exceptAdvise(y, x, pY, pX);
}

str exceptAdvise(Tree x, Tree y, set[Production] pX, set[Production] pY) {
  result = "";
  if (appl(p, argsX) := x, appl(q, argsY) := y) {
    if (i <- index(argsX), appl(apX,_) := argsX[i], apX notin pY) {
      labelApX = "PROVIDE_LABEL";
      
      if (prod(label(l,_),_,_) := apX) {
        labelApX = l;
      }
      else {
        result += "ADVISORY: provide a label for: 
                  '  <topProd2rascal(apX[def=label(labelApX, apX.def)])>
                  '";
      }
       
      result += "To fix this ambiguity, you could consider restricting the nesting of
                '  <topProd2rascal(apX)>
                'under
                '  <topProd2rascal(p)>
                'using the ! operator on argument <i/2>: !<labelApX>
                'However, you should realize that you are introducing a restriction that makes the language smaller.
                '
                '";
    }
     
  }
  return result;
}


str danglingCauses(Tree x, Tree y) {
  if (appl(p,/appl(q,_)) := x, appl(q,/appl(p,_)) := y) {
    return danglingFollowSolutions(x, y);
  }

  return "";
}

str danglingFollowSolutions(Tree x, Tree y) {
  if (prod(_, lhs, _) := x.prod, prod(_, [pref*, _, l:lit(_), more*], _) := y.prod, lhs == pref) {
    return "To fix this ambiguity you might add a follow restriction for <symbol2rascal(l)> on:
           '   <topProd2rascal(x.prod)> (<x.prod>)
           "; 
  }
  
  if (prod(_, lhs, _) := y.prod, prod(_, [pref*, _, l:lit(_), more*], _) := x.prod, lhs == pref) {
    return "To fix this ambiguity you might add a follow restriction for <symbol2rascal(l)> on:
           '  <topProd2rascal(y.prod)>
           "; 
  }
  
  return ""; 
}

void rules(list[Production] rs) {
  ul(class("list-unstyled"), () {
    for (r <- rs) {
      li(() {
        pre(() { code(topProd2rascal(r)); });
      });
    }
  });
}

void rules(set[Production] r) = rules(sort(r));

