module salix::lib::Bootstrap

extend salix::HTML; 
extend salix::Node;
import salix::Core;

data ColSize = xsm() | sm() | md() | lg() | xl();

str colsize(xsm()) = "";
str colsize(sm())  = "-sm";
str colsize(md())  = "-md";
str colsize(lg())  = "-lg";
str colsize(xl())  = "-xl";
alias Column = void (int, ColSize);
 
void container(bool fluid, value vals...) {
    build([class("container<if (fluid) {>-fluid<}>")] + vals, "div");
}

void row(value vals...) =  build([class("row")] + vals, "div");

void column(int width, ColSize colSize, value vals...) = build([class("col<colsize(colSize)>-<width>")] + vals, "div");

void alert(str infoWarningDanger, str message) {
    div(class("alert"), class("alert-<infoWarningDanger>"), role("alert"), () {
        paragraph(message);
    });
}

void alertDanger(str message) = alert("danger", message);
void alertInfo(str message) = alert("info", message);
void alertWarning(str message) = alert("warning", message);
void alertSuccess(str message) = alert("success", message);

Attr role(str r) = attr("role", r);
Attr scope(str sc) = attr("scope", sc);
Attr dataToggle(str t) = attr("data-toggle",t);
Attr \data-toggle(str t) = attr("data-toggle",t);
Attr tab() = dataToggle("tab");
Attr dropdown() = dataToggle("dropdown");
Attr hasPopup(bool v) = attr("aria-haspopup","<v>");
Attr expanded(bool v) = attr("aria-expanded", "<v>");
Attr labeledBy(str l) = attr("aria-labelledby", l);
Attr disabled() = attr("disabled", "true");

void paragraph(str x) {
  p(() { 
    text(x); 
  });    
}