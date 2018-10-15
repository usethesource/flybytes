module lang::mujava::tests::AnnotationTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;

Class annoClass() =
  class(reference("AnnoClass")
  methods=[
    staticMethod(\public(),boolean(),"testMethod", [], [ \return(\true())])[
      annotations=[
        \anno("java.lang.Deprecated"),
        \anno("java.lang.Annotation", array(integer()), [0,1,2], name="version")
      ]
    ]
  ],
  annotations=[
    \anno("java.lang.Annotation", integer(), 0, name="version"),
    \anno("javax.annotation.processing.SupportedSourceVersion", reference("javax.lang.model.SourceVersion"), "RELEASE_0")
  ]
  );

bool testAnnoClass(Class c) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
}
  
test bool noCrashWithAnnosTest() 
  = testAnnoClass(annoClass());  