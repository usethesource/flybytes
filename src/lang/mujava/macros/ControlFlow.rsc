module lang::mujava::macros::ControlFlow

import lang::mujava::api::JavaLang;

Stat for_array(str arrayVar, str indexVar, list[Stat] body)
  = \for([
         // for (int i = 0; 
         decl(integer(), indexVar, \init=iconst(0))],
         // i < arrayVar.length;
         lt(load(indexVar), alength(load(arrayVar))),    
         // i++) {
         [incr(indexVar ,1)],
         body
         // }
         );                                    

Stat for_iter(str elementVar, str iterableVar, str iteratorVar, list[Stat] body)
  = block([
      // Iterator iter = iterable.iterator();
      decl(Iterator(), iteratorVar, init=invokeInterface(load(iterableVar), Iteratable_iterator(), [])),
      // Object elem;
      decl(object(), elementVar),
      // while (iter.hasNext()) {
      \while(invokeInterface(load(iteratorVar), Iterator_hasNext(), []), [
        // elem = iter.next();
        store(elementVar, invokeInterface(iteratorVar, Iterator_next(), [])),
        *body
        // }
      ]) 
  ]);