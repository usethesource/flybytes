module lang::flybytes::tests::AnnotationTests

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

Class annoClass() =
  class(object("AnnoClass")
  methods=[
    staticMethod(\public(),boolean(),"testMethod", [], [ \return(\true())])[
      annotations=[
        \tag("java.lang.Deprecated"),
        \anno("java.lang.Annotation", array(integer()), [0,1,2], name="version")
      ]
    ]
  ],
  annotations=[
    \anno("java.lang.Annotation", integer(), 0, name="version"),
    \anno("javax.annotation.processing.SupportedSourceVersion", object("javax.lang.model.SourceVersion"), "RELEASE_0")
  ]
  );

bool testAnnoClass(Class c) { 
  m = loadClass(c, file=just(|project://flybytes/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
}
  
test bool noCrashWithAnnosTest() 
  = testAnnoClass(annoClass());  
