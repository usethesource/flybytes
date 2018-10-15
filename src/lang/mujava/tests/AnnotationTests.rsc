module lang::mujava::tests::AnnotationTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;

Class annoClass() =
  class(reference("AnnoClass")
  methods=[
    staticMethod(\public(),integer(),"testMethod", [], [ \return(iconst(1))])[
      annotations=[\anno("java.lang.Deprecated")]
    ]
  ],
  annotations=[
    \anno("java.lang.Annotation", array(integer()), "version", [1,0,9])
  ]
  );