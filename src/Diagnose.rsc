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
    
void ruleDifferences(Tree x, Tree y) {
  h3("Rule differences");
  
  pX = { p | /Production p := x };
  pY = { p | /Production p := y };
  
  if (pX == pY) {
    paragraph("The alternatives use the same rules.");
  }
  else {
    row(() {
      column(5, md(), () {
        text("Rules unique to tree one");
      });
      column(5, md(), () {
        text("Rules unique to tree two");
      });
    });
    row(() {
      column(5, md(), () {
        rules(pX - pY);
      });
      column(5, md(), () {
        rules(pY - pX);
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
      rules([prodX, prodY]);
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
        pre(() {
           text("<appl(p, [*np, wrap(xq)])>");
        });
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
        pre(() {
           text("<appl(q, [wrap(yp), *yr])>");
        });
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
  bool isWord(Tree t)  = s is \lex || s is \lit || s is \cilit || s is \layout when s := symbol(t);
   
  Words collect(Tree t) = {<n@\loc?|unknown:///|, symbol(n), "<n>"> | /n:appl(_,_) := t, isWord(n)};
  
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
          for (<pos, cat, word> <- sort(ws, bool (Word a, Word b) { return a.pos < b.pos; })) {
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
 
 unreserved = litX<word> & lexY<word> + litY<word> & lexX<word>;
 if (unreserved != {}) {
   h3("Unreserved keywords");
   paragraph("Literal keywords in the one alternative have been re-interpreted as other lexical categories (i.e. identifiers) in the other alternative."); 
   
   paragraph("Suggested disambiguations:");
   ul(() {
     for (<cLit, cLex> <- (litX<cat, word> o lexY<word, cat> + litY<cat, word> o lexX<word, cat>)) {
       li(() {
         text(symbol2rascal(conditional(cLex, {delete(cLit)})));
       });
     }  
   });
 }
 
 litParentsX = {<p,l> | /t:appl(p,[_*,l:appl(prod(l,_,_),_),_*]) := x, lit(_) := l || cilit(_) := l};
 litParentsY = {<p,l> | /t:appl(p,[_*,l:appl(prod(l,_,_),_),_*]) := y, lit(_) := l || cilit(_) := l};
 
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
     = { <posX.offset, c, w1, w2> | <posX, c, w1> <- lexX, <posY, c, w2> <- lexY, posX.offset == posY.offset, posX != posY};
     
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
       ul(() {
         for (f <- follows) {
           li(() {
              text(topProd2rascal(f));
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
  ul(() {
    for (r <- rs) {
      li(() {
        text(topProd2rascal(r));
      });
    }
  });
}

void rules(set[Production] r) = rules(sort(r));

