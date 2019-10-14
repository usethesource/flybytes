module lang::flybytes::tests::DecompileTests

import lang::flybytes::Decompiler;

test bool fullyDecompileRoundtripClass()
  = /asm(_) !:= decompile(|project://flybytes/bin/lang/flybytes/tests/examples/RoundtripTestClass.class|);
  
//@Ignore{this test fails on loops which end with if-then-elses and nested loops perhaps too}  
test bool fullyDecompileBankClass()
  = /asm(_) !:= decompile(|project://flybytes/bin/lang/flybytes/tests/examples/business/Bank.class|);  
   
test bool fullyDecompileAccountClass()
  = /asm(_) !:= decompile(|project://flybytes/bin/lang/flybytes/tests/examples/business/Account.class|);  