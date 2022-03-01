module lang::flybytes::tests::DecompileTests

import lang::flybytes::Decompiler;

test bool fullyDecompileRoundtripClass()
  = /asm(_) !:= decompile(|target://flybytes/lang/flybytes/tests/examples/RoundtripTestClass.class|);
  
test bool fullyDecompileBankClass()
  = /asm(_) !:= decompile(|target://flybytes/lang/flybytes/tests/examples/business/Bank.class|);  
   
test bool fullyDecompileAccountClass()
  = /asm(_) !:= decompile(|target://flybytes/lang/flybytes/tests/examples/business/Account.class|);  