package lang.flybytes.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.objectweb.asm.ClassReader;
import org.rascalmpl.objectweb.asm.Handle;
import org.rascalmpl.objectweb.asm.Opcodes;
import org.rascalmpl.objectweb.asm.Type;
import org.rascalmpl.objectweb.asm.tree.AbstractInsnNode;
import org.rascalmpl.objectweb.asm.tree.ClassNode;
import org.rascalmpl.objectweb.asm.tree.FieldInsnNode;
import org.rascalmpl.objectweb.asm.tree.FieldNode;
import org.rascalmpl.objectweb.asm.tree.IincInsnNode;
import org.rascalmpl.objectweb.asm.tree.InsnList;
import org.rascalmpl.objectweb.asm.tree.IntInsnNode;
import org.rascalmpl.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.rascalmpl.objectweb.asm.tree.JumpInsnNode;
import org.rascalmpl.objectweb.asm.tree.LabelNode;
import org.rascalmpl.objectweb.asm.tree.LdcInsnNode;
import org.rascalmpl.objectweb.asm.tree.LineNumberNode;
import org.rascalmpl.objectweb.asm.tree.LocalVariableNode;
import org.rascalmpl.objectweb.asm.tree.LookupSwitchInsnNode;
import org.rascalmpl.objectweb.asm.tree.MethodInsnNode;
import org.rascalmpl.objectweb.asm.tree.MethodNode;
import org.rascalmpl.objectweb.asm.tree.ParameterNode;
import org.rascalmpl.objectweb.asm.tree.TableSwitchInsnNode;
import org.rascalmpl.objectweb.asm.tree.TryCatchBlockNode;
import org.rascalmpl.objectweb.asm.tree.TypeInsnNode;
import org.rascalmpl.objectweb.asm.tree.VarInsnNode;
import org.rascalmpl.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.rascalmpl.uri.URIResolverRegistry;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;


/**
 * Produces a Flybytes AST from a JVM class in bytecode format, with the limitation
 * that it does not recover Expressions and Statements of the method bodies, but rather lists of ASM-style Instructions.
 * The instruction lists can be processed later by a downstream de-compilation step. 
 */
public class ClassDisassembler {
	private final IValueFactory VF;
	private final AST ast;
	
	public ClassDisassembler(IValueFactory VF) {
		this.VF = VF;
		this.ast = new AST(VF);
	}
	
	public IConstructor disassemble(ISourceLocation classLoc) {
		try {
			ClassReader reader = new ClassReader(URIResolverRegistry.getInstance().getInputStream(classLoc));
			return readClass(reader);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
		}
	}
	
	private IConstructor readClass(ClassReader reader) {
		ClassNode cn = new ClassNode();
		reader.accept(cn, ClassReader.SKIP_FRAMES);
		Map<String, IValue> params = new HashMap<>();
		
		params.put("fields", fields(cn.fields));
		params.put("methods", methods(cn.methods));
		params.put("modifiers", modifiers(cn.access));
		if (cn.superName != null) {
			params.put("super", objectType(cn.superName));
		}
		params.put("interfaces", interfaces(cn.interfaces));
		
		if (set(cn.access, Opcodes.ACC_INTERFACE)) {
			return ast.Class_interface(objectType(cn.name)).asWithKeywordParameters().setParameters(params);
		}
		else {
			return ast.Class_class(objectType(cn.name)).asWithKeywordParameters().setParameters(params);
		}
	}

	private IList interfaces(List<String> interfaces) {
		IListWriter w = VF.listWriter();
		
		for (String iface : interfaces) {
			w.append(objectType(iface));
		}
		
		return w.done();
	}

	private IConstructor objectType(String name) {
		return ast.Type_object(name.replaceAll("/", "."));
	}

	private ISet modifiers(int access) {
		ISetWriter sw  = VF.setWriter();

		// mutually exclusive access bits:
		if (set(access, Opcodes.ACC_PUBLIC)) {
			sw.insert(ast.Modifier_public());
		}
		else if (set(access, Opcodes.ACC_PRIVATE)) {
			sw.insert(ast.Modifier_private());
		}
		else if (set(access, Opcodes.ACC_PROTECTED)) {
			sw.insert(ast.Modifier_protected());
		}
		else {
			sw.insert(ast.Modifier_friendly());
		}

		if (set(access, Opcodes.ACC_STATIC)) {
			sw.insert(ast.Modifier_static());
		}

		if (set(access, Opcodes.ACC_FINAL)) {
			sw.insert(ast.Modifier_final());
		}
		
		if (set(access, Opcodes.ACC_SYNCHRONIZED)) {
			sw.insert(ast.Modifier_synchronized());
		}
		
		if (set(access, Opcodes.ACC_ABSTRACT)) {
			sw.insert(ast.Modifier_abstract());
		}

		return sw.done();
	}

	private boolean set(int access, int bit) {
		return (access & bit) != 0;
	}

	private IList methods(List<MethodNode> methods) {
		IListWriter lw = VF.listWriter();
		
		for (MethodNode fn : methods) {
			lw.append(method(fn));
		}
		
		return lw.done();
	}

	private IConstructor method(MethodNode fn) {
		IConstructor desc = descriptor(fn.name, fn.desc);
		IList instructions = instructions(fn.instructions);
		
		if (fn.tryCatchBlocks != null) {
			for (TryCatchBlockNode tc : fn.tryCatchBlocks) {
				instructions = instructions.append(ast.Instruction_TRYCATCH(typeName(tc.type), tc.start.getLabel().toString(), tc.end.getLabel().toString(), tc.handler.getLabel().toString()));
			}
		}
		
		if (fn.localVariables != null) {
			for (LocalVariableNode var : fn.localVariables) {
				instructions = instructions.append(ast.Instruction_LOCALVARIABLE(var.name, type(var.desc), var.start.getLabel().toString(), var.end.getLabel().toString(), var.index));
			}
		}
		
		IList formals = formals(fn.parameters, fn.localVariables, (IList) desc.get("formals"), set(fn.access, Opcodes.ACC_STATIC));
		
		if (fn.name.equals("<clinit>")) {
			return ast.Method_static(VF.list(ast.Stat_asm(instructions)));
		}
		else {
			return ast.Method_method(desc, formals, VF.list(ast.Stat_asm(instructions)));
		}
	}

	private IList formals(List<ParameterNode> parameters, List<LocalVariableNode> locals, IList types, boolean isStatic) {
		IListWriter lw = VF.listWriter();
	
		if (parameters != null && !parameters.isEmpty()) {
			// only when class was compiled with javac -parameters
			int i = 0;
			for (IValue elem : types) {
				lw.append(ast.Formal_var((IConstructor) elem, parameters.get(i++).name));
			}
		}
		else if (locals != null && locals.size() >= types.length() - (isStatic?0:1)) {
			// only when class was compiled with javac -debug
			int i = 0;
			for (IValue elem : types) {
				LocalVariableNode local = locals.get(i + (isStatic?0:1));
				lw.append(ast.Formal_var((IConstructor) elem, local.name));
				i++;
			}
		}
		else {
			// otherwise we "invent" the parameter names
			int i = 0;
			for (IValue elem : types) {
				lw.append(ast.Formal_var((IConstructor) elem, "arg_" + i));
			}
		}

		return lw.done();
	}

	private IList instructions(InsnList instructions) {
		IListWriter lw = VF.listWriter();
		Iterator<AbstractInsnNode> iter = instructions.iterator();
		
		while (iter.hasNext()) {
			lw.append(instruction(iter.next()));
		}
		
		return lw.done();
	}

	private IConstructor instruction(AbstractInsnNode instr) {
		switch (instr.getOpcode()) {
		case Opcodes.NOP: 
			return ast.Instruction_NOP();
		case Opcodes.ACONST_NULL:
			return ast.Instruction_ACONST_NULL();
		case Opcodes.ICONST_M1:
			return ast.Instruction_ICONST_M1();
		case Opcodes.ICONST_0:
			return ast.Instruction_ICONST_0();
		case Opcodes.ICONST_1:
			return ast.Instruction_ICONST_1();
		case Opcodes.ICONST_2:
			return ast.Instruction_ICONST_2();
		case Opcodes.ICONST_3:
			return ast.Instruction_ICONST_3();
		case Opcodes.ICONST_4:
			return ast.Instruction_ICONST_4();
		case Opcodes.ICONST_5:
			return ast.Instruction_ICONST_5();
		case Opcodes.LCONST_0:
			return ast.Instruction_LCONST_0();
		case Opcodes.LCONST_1:
			return ast.Instruction_LCONST_1();
		case Opcodes.FCONST_0:
			return ast.Instruction_FCONST_0();
		case Opcodes.FCONST_1:
			return ast.Instruction_FCONST_1();
		case Opcodes.FCONST_2:
			return ast.Instruction_FCONST_2();
		case Opcodes.DCONST_0:
			return ast.Instruction_DCONST_0();
		case Opcodes.DCONST_1:
			return ast.Instruction_DCONST_1();
		case Opcodes.IALOAD:
			return ast.Instruction_IALOAD();
		case Opcodes.LALOAD:
			return ast.Instruction_LALOAD();
		case Opcodes.FALOAD:
			return ast.Instruction_FALOAD();
		case Opcodes.DALOAD:
			return ast.Instruction_DALOAD();
		case Opcodes.AALOAD:
			return ast.Instruction_AALOAD();
		case Opcodes.BALOAD:
			return ast.Instruction_BALOAD();
		case Opcodes.CALOAD:
			return ast.Instruction_CALOAD();
		case Opcodes.SALOAD:
			return ast.Instruction_SALOAD();
		case Opcodes.IASTORE:
			return ast.Instruction_IASTORE();
		case Opcodes.LASTORE:
			return ast.Instruction_LASTORE();
		case Opcodes.FASTORE:
			return ast.Instruction_FASTORE();
		case Opcodes.DASTORE:
			return ast.Instruction_DASTORE();
		case Opcodes.AASTORE:
			return ast.Instruction_AASTORE();
		case Opcodes.BASTORE:
			return ast.Instruction_BASTORE();
		case Opcodes.CASTORE:
			return ast.Instruction_CASTORE();
		case Opcodes.SASTORE:
			return ast.Instruction_SASTORE();
		case Opcodes.POP:
			return ast.Instruction_POP();
		case Opcodes.POP2:
			return ast.Instruction_POP2();
		case Opcodes.DUP:
			return ast.Instruction_DUP();
		case Opcodes.DUP_X1:
			return ast.Instruction_DUP_X1();
		case Opcodes.DUP_X2:
			return ast.Instruction_DUP_X2();
		case Opcodes.DUP2:
			return ast.Instruction_DUP2();
		case Opcodes.DUP2_X1:
			return ast.Instruction_DUP2_X1();
		case Opcodes.DUP2_X2:
			return ast.Instruction_DUP2_X2();
		case Opcodes.SWAP:
			return ast.Instruction_SWAP();
		case Opcodes.IADD:
			return ast.Instruction_IADD();
		case Opcodes.LADD:
			return ast.Instruction_LADD();
		case Opcodes.FADD:
			return ast.Instruction_FADD();
		case Opcodes.DADD:
			return ast.Instruction_DADD();
		case Opcodes.ISUB:
			return ast.Instruction_ISUB();
		case Opcodes.LSUB:
			return ast.Instruction_LSUB();
		case Opcodes.FSUB:
			return ast.Instruction_FSUB();
		case Opcodes.DSUB:
			return ast.Instruction_DSUB();
		case Opcodes.IMUL:
			return ast.Instruction_IMUL();
		case Opcodes.LMUL:
			return ast.Instruction_LMUL();
		case Opcodes.FMUL:
			return ast.Instruction_FMUL();
		case Opcodes.DMUL:
			return ast.Instruction_DMUL();
		case Opcodes.IDIV:
			return ast.Instruction_IDIV();
		case Opcodes.LDIV:
			return ast.Instruction_LDIV();
		case Opcodes.FDIV:
			return ast.Instruction_FDIV();
		case Opcodes.DDIV:
			return ast.Instruction_DDIV();
		case Opcodes.IREM:
			return ast.Instruction_IREM();
		case Opcodes.LREM:
			return ast.Instruction_LREM();
		case Opcodes.FREM:
			return ast.Instruction_FREM();
		case Opcodes.DREM:
			return ast.Instruction_DREM();
		case Opcodes.INEG:
			return ast.Instruction_INEG();
		case Opcodes.LNEG:
			return ast.Instruction_LNEG();
		case Opcodes.FNEG:
			return ast.Instruction_FNEG();
		case Opcodes.DNEG:
			return ast.Instruction_DNEG();
		case Opcodes.ISHL:
			return ast.Instruction_ISHL();
		case Opcodes.LSHL:
			return ast.Instruction_LSHL();
		case Opcodes.ISHR:
			return ast.Instruction_ISHR();
		case Opcodes.LSHR:
			return ast.Instruction_LSHR();
		case Opcodes.IUSHR:
			return ast.Instruction_IUSHR();
		case Opcodes.LUSHR:
			return ast.Instruction_LUSHR();
		case Opcodes.IAND:
			return ast.Instruction_IAND();
		case Opcodes.LAND:
			return ast.Instruction_LAND();
		case Opcodes.IOR:
			return ast.Instruction_IOR();
		case Opcodes.LOR:
			return ast.Instruction_LOR();
		case Opcodes.IXOR:
			return ast.Instruction_IXOR();
		case Opcodes.LXOR:
			return ast.Instruction_LXOR();
		case Opcodes.I2L:
			return ast.Instruction_I2L();
		case Opcodes.I2F:
			return ast.Instruction_I2F();
		case Opcodes.I2D:
			return ast.Instruction_I2D();
		case Opcodes.L2I:
			return ast.Instruction_L2I();
		case Opcodes.L2F:
			return ast.Instruction_L2F();
		case Opcodes.L2D:
			return ast.Instruction_L2D();
		case Opcodes.F2I:
			return ast.Instruction_F2I();
		case Opcodes.F2L:
			return ast.Instruction_F2L();
		case Opcodes.F2D:
			return ast.Instruction_F2D();
		case Opcodes.D2I:
			return ast.Instruction_D2I();
		case Opcodes.D2L:
			return ast.Instruction_D2L();
		case Opcodes.D2F:
			return ast.Instruction_D2F();
		case Opcodes.I2B:
			return ast.Instruction_I2B();
		case Opcodes.I2C:
			return ast.Instruction_I2C();
		case Opcodes.I2S:
			return ast.Instruction_I2S();
		case Opcodes.LCMP:
			return ast.Instruction_LCMP();
		case Opcodes.FCMPL:
			return ast.Instruction_FCMPL();
		case Opcodes.FCMPG:
			return ast.Instruction_FCMPG();
		case Opcodes.DCMPL:
			return ast.Instruction_DCMPL();
		case Opcodes.DCMPG:
			return ast.Instruction_DCMPG();
		case Opcodes.IRETURN:
			return ast.Instruction_IRETURN();
		case Opcodes.LRETURN:
			return ast.Instruction_LRETURN();
		case Opcodes.FRETURN:
			return ast.Instruction_FRETURN();
		case Opcodes.DRETURN:
			return ast.Instruction_DRETURN();
		case Opcodes.ARETURN:
			return ast.Instruction_ARETURN();
		case Opcodes.RETURN:
			return ast.Instruction_RETURN();
		case Opcodes.ARRAYLENGTH:
			return ast.Instruction_ARRAYLENGTH();
		case Opcodes.ATHROW:
			return ast.Instruction_ATHROW();
		case Opcodes.MONITORENTER:
			return ast.Instruction_MONITORENTER();
		case Opcodes.MONITOREXIT:
			return ast.Instruction_MONITOREXIT();
		case Opcodes.ILOAD:
			return ast.Instruction_ILOAD(((VarInsnNode) instr).var);
		case Opcodes.LLOAD:
			return ast.Instruction_LLOAD(((VarInsnNode) instr).var);
		case Opcodes.FLOAD:
			return ast.Instruction_FLOAD(((VarInsnNode) instr).var);
		case Opcodes.DLOAD:
			return ast.Instruction_DLOAD(((VarInsnNode) instr).var);
		case Opcodes.ALOAD:
			return ast.Instruction_ALOAD(((VarInsnNode) instr).var);
		case Opcodes.ISTORE:
			return ast.Instruction_ISTORE(((VarInsnNode) instr).var);
		case Opcodes.LSTORE:
			return ast.Instruction_LSTORE(((VarInsnNode) instr).var);
		case Opcodes.FSTORE:
			return ast.Instruction_FSTORE(((VarInsnNode) instr).var);
		case Opcodes.DSTORE:
			return ast.Instruction_DSTORE(((VarInsnNode) instr).var);
		case Opcodes.ASTORE:
			return ast.Instruction_ASTORE(((VarInsnNode) instr).var);
		case Opcodes.RET:
			return ast.Instruction_RET(((VarInsnNode) instr).var);
		case Opcodes.BIPUSH:
			return ast.Instruction_BIPUSH(((IntInsnNode) instr).operand);
		case Opcodes.SIPUSH:
			return ast.Instruction_BIPUSH(((IntInsnNode) instr).operand);
		case Opcodes.LDC:
			return ast.Instruction_LDC(constType(((LdcInsnNode) instr).cst), initializer(((LdcInsnNode) instr).cst).get("constant"));
		case Opcodes.IINC:
			return ast.Instruction_IINC(((IincInsnNode) instr).var, ((IincInsnNode) instr).incr);
		case Opcodes.IFEQ:
			return ast.Instruction_IFEQ((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IFNE:
			return ast.Instruction_IFNE((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IFLT:
			return ast.Instruction_IFLT((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IFGE:
			return ast.Instruction_IFGE((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IFGT:
			return ast.Instruction_IFGT((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IFLE:
			return ast.Instruction_IFLE((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ICMPEQ:
			return ast.Instruction_IF_ICMPEQ((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ICMPNE:
			return ast.Instruction_IF_ICMPNE((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ICMPLT:
			return ast.Instruction_IF_ICMPLT((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ICMPGE:
			return ast.Instruction_IF_ICMPGE((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ICMPGT:
			return ast.Instruction_IF_ICMPGT((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ICMPLE:
			return ast.Instruction_IF_ICMPLE((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ACMPEQ:
			return ast.Instruction_IF_ACMPEQ((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IF_ACMPNE:
			return ast.Instruction_IF_ACMPNE((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.GOTO:
			return ast.Instruction_GOTO((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.JSR:
			return ast.Instruction_JSR((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IFNULL:
			return ast.Instruction_IFNULL((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.IFNONNULL:
			return ast.Instruction_IFNONNULL((((JumpInsnNode) instr).label.getLabel().toString()));
		case Opcodes.TABLESWITCH:
			return tableSwitchInstruction((TableSwitchInsnNode) instr);
		case Opcodes.LOOKUPSWITCH:
			return lookupSwitchInstruction((LookupSwitchInsnNode) instr);
		case Opcodes.GETSTATIC:
			return ast.Instruction_GETSTATIC(typeName(((FieldInsnNode) instr).owner),
					((FieldInsnNode) instr).name,
					type(((FieldInsnNode) instr).desc));
		case Opcodes.PUTSTATIC:
			return ast.Instruction_PUTSTATIC(typeName(((FieldInsnNode) instr).owner),
					((FieldInsnNode) instr).name,
					type(((FieldInsnNode) instr).desc));
		case Opcodes.GETFIELD:
			return ast.Instruction_GETFIELD(typeName(((FieldInsnNode) instr).owner),
					((FieldInsnNode) instr).name,
					type(((FieldInsnNode) instr).desc));
		case Opcodes.PUTFIELD:
			return ast.Instruction_PUTFIELD(typeName(((FieldInsnNode) instr).owner),
					((FieldInsnNode) instr).name,
					type(((FieldInsnNode) instr).desc));
		case Opcodes.INVOKEVIRTUAL:
			return ast.Instruction_INVOKEVIRTUAL(typeName(((MethodInsnNode) instr).owner), 
					descriptor(((MethodInsnNode) instr).name, 
							   ((MethodInsnNode) instr).desc),
					((MethodInsnNode) instr).itf
					);
		case Opcodes.INVOKESPECIAL:
			return ast.Instruction_INVOKESPECIAL(typeName(((MethodInsnNode) instr).owner), 
					descriptor(((MethodInsnNode) instr).name, 
							   ((MethodInsnNode) instr).desc),
					((MethodInsnNode) instr).itf
					);
		case Opcodes.INVOKESTATIC:
			return ast.Instruction_INVOKESTATIC(typeName(((MethodInsnNode) instr).owner), 
					descriptor(((MethodInsnNode) instr).name, 
							   ((MethodInsnNode) instr).desc),
					((MethodInsnNode) instr).itf
					);
		case Opcodes.INVOKEINTERFACE:
			return ast.Instruction_INVOKEINTERFACE(typeName(((MethodInsnNode) instr).owner), 
					descriptor(((MethodInsnNode) instr).name, 
							   ((MethodInsnNode) instr).desc),
					((MethodInsnNode) instr).itf
					);
		case Opcodes.INVOKEDYNAMIC:
		    InvokeDynamicInsnNode invokeDynamicNode = (InvokeDynamicInsnNode) instr;
            return ast.Instruction_INVOKEDYNAMIC(
		            descriptor(invokeDynamicNode.name, invokeDynamicNode.desc),
		            handle(invokeDynamicNode.bsm, invokeDynamicNode.bsmArgs));
		case Opcodes.NEWARRAY:
			return ast.Instruction_NEWARRAY(type(((TypeInsnNode) instr).desc));
		case Opcodes.NEW:
			return ast.Instruction_NEW(typeName(((TypeInsnNode) instr).desc));
		case Opcodes.ANEWARRAY:
			return ast.Instruction_ANEWARRAY(typeName(((TypeInsnNode) instr).desc));
		case Opcodes.CHECKCAST:
			return ast.Instruction_CHECKCAST(typeName(((TypeInsnNode) instr).desc));
		case Opcodes.INSTANCEOF:
			return ast.Instruction_INSTANCEOF(typeName(((TypeInsnNode) instr).desc));
		case Opcodes.MULTIANEWARRAY:
			return ast.Instruction_MULTIANEWARRAY(typeName(((MultiANewArrayInsnNode) instr).desc), 
					((MultiANewArrayInsnNode) instr).dims); 
		case -1: // LABELNODE & LINENUMBER NODE
			if (instr instanceof LabelNode) {
				return ast.Instruction_LABEL(((LabelNode) instr).getLabel().toString());
			}
			else if (instr instanceof LineNumberNode) {
				return ast.Instruction_LINENUMBER(((LineNumberNode) instr).line, ((LineNumberNode) instr).start.getLabel().toString());
			}
		}
		
		
		throw new IllegalArgumentException("unrecognized instruction: " + instr);
	}

	private IConstructor handle(Handle bootstrapMethod, Object[] bootstrapMethodArgs) {
	    IConstructor cls = typeName(bootstrapMethod.getOwner());
	    IConstructor descriptor = descriptor(bootstrapMethod.getName(), bootstrapMethod.getDesc());
	    
	    return ast.BootstrapCall_bootstrap(cls, descriptor, bootstrapArgs(bootstrapMethodArgs));
    }

    private IList bootstrapArgs(Object[] bootstrapMethodArgs) {
        IListWriter w = VF.listWriter();
        
        for (Object arg : bootstrapMethodArgs) {
            w.append(bootstrapArg(arg));
        }
        
        return w.done();
    }

    private IValue bootstrapArg(Object arg) {
        if (arg instanceof String) {
            return ast.CallSiteInfo_stringInfo((String) arg);
        }
        else if (arg instanceof Integer) {
            return ast.CallSiteInfo_integerInfo((int) arg);
        }
        else if (arg instanceof Double) {
            return ast.CallSiteInfo_doubleInfo((double) arg);
        }
        else if (arg instanceof Long) {
            return ast.CallSiteInfo_longInfo((long) arg);
        }
        else if (arg instanceof Class<?>) {
            return ast.CallSiteInfo_classInfo(((Class<?>) arg).getName());
        }
        else if (arg instanceof Type) {
            Type t = (Type) arg;
            switch (t.getSort()) {
            case Type.METHOD:
                return ast.CallSiteInfo_methodTypeInfo(descriptor("$anonymous", t.getDescriptor()));
            }
        }
        else if (arg instanceof Handle) {
            Handle h = (Handle) arg;
            return ast.CallSiteInfo_virtualHandle(typeName(h.getOwner()), h.getName(), descriptor(h.getName(), h.getDesc()));
        }
        
        throw new IllegalArgumentException("no support for this bootstrap argument yet: " + arg);
    }

    private IConstructor lookupSwitchInstruction(LookupSwitchInsnNode instr) {
		IListWriter labels = VF.listWriter();
		for (LabelNode l : instr.labels) {
			labels.append(VF.string(l.getLabel().toString()));
		}
		
		IListWriter keys = VF.listWriter();
		for (int key : instr.keys) {
			keys.append(VF.integer(key));
		}
		
		return ast.Instruction_LOOKUPSWITCH(instr.dflt.getLabel().toString(), keys.done(), labels.done());
	}

	private IConstructor tableSwitchInstruction(TableSwitchInsnNode instr) {
		IListWriter labels = VF.listWriter();
		
		for (LabelNode l : instr.labels) {
			labels.append(VF.string(l.getLabel().toString()));
		}
		
		return ast.Instruction_TABLESWITCH(instr.min, instr.max, instr.dflt.getLabel().toString(), labels.done());
	}

	private IConstructor descriptor(String name, String desc) {
		org.rascalmpl.objectweb.asm.Type d = Type.getType(desc);
		
		Type ret = d.getReturnType();
		Type[] args = d.getArgumentTypes();

		if ("<init>".equals(name)) {
			return ast.Signature_constructorDesc(sigFormals(args));
		}
		else {
			return ast.Signature_methodDesc(type(ret.getDescriptor()), name, sigFormals(args));
		}
	}

	private IList sigFormals(Type[] args) {
		IListWriter lw = VF.listWriter();
		
		for (Type t : args) {
			lw.append(type(t.getDescriptor()));
		}
		
		return lw.done();
	}

	private IList fields(List<FieldNode> fields) {
		IListWriter lw = VF.listWriter();
		
		for (FieldNode fn : fields) {
			lw.append(field(fn));
		}
		
		return lw.done();
	}

	private IValue field(FieldNode fn) {
		Map<String, IValue> params = new HashMap<>();
		
		if (fn.value != null) {
			params.put("init", initializer(fn.value));
		}
		
		params.put("modifiers", modifiers(fn.access));
		
		return ast.Field_field(type(fn.desc), fn.name).asWithKeywordParameters().setParameters(params);
	}

	private IConstructor initializer(Object value) {
		if (value instanceof String) {
			return ast.Exp_const(ast.Type_string(), VF.string((String) value));
		}
		
		if (value instanceof Float) {
			return ast.Exp_const(ast.Type_float(), VF.real((Float) value));
		}
		
		if (value instanceof Double) {
			return ast.Exp_const(ast.Type_double(), VF.real((Double) value));
		}
		
		if (value instanceof Byte) {
			return ast.Exp_const(ast.Type_byte(), VF.integer((Byte) value));
		}
		
		if (value instanceof Short) {
			return ast.Exp_const(ast.Type_short(), VF.integer((Short) value));
		}
		
		if (value instanceof Character) {
			return ast.Exp_const(ast.Type_character(), VF.integer((Character) value));
		}
		
		if (value instanceof Integer) {
			return ast.Exp_const(ast.Type_integer(), VF.integer((Integer) value));
		}
		
		if (value instanceof Long) {
			return ast.Exp_const(ast.Type_long(), VF.integer((Long) value));
		}
		
		return ast.Exp_null();
	}
	
	private IConstructor constType(Object value) {
		if (value instanceof String) {
			return ast.Type_string();
		}
		
		if (value instanceof Float) {
			return ast.Type_float();
		}
		
		if (value instanceof Double) {
			return ast.Type_double();
		}
		
		if (value instanceof Byte) {
			return ast.Type_byte();
		}
		
		if (value instanceof Short) {
			return ast.Type_short();
		}
		
		if (value instanceof Character) {
			return ast.Type_character();
		}
		
		if (value instanceof Integer) {
			return ast.Type_integer();
		}
		
		if (value instanceof Long) {
			return ast.Type_long();
		}
		
		throw new IllegalArgumentException("constant type not detected: " + value);
	}

	private IConstructor typeName(String cls) {
		return type("L" + cls + ";");
	}
	
	private IConstructor type(String desc) {
		if ("Ljava/lang/String;".equals(desc)) {
			return ast.Type_string();
		}
		
		if (desc.startsWith("L")) {
			return objectType(desc.substring(1, desc.indexOf(";")));
		}
		
		if (desc.startsWith("[")) {
			return ast.Type_array(type(desc.substring(1)));
		}
		
		if ("Z".equals(desc)) {
			return ast.Type_boolean();
		}
		
		if ("I".equals(desc)) {
			return ast.Type_integer();
		}
		
		if ("S".equals(desc)) {
			return ast.Type_short();
		}
		
		if ("B".equals(desc)) {
			return ast.Type_byte();
		}
		
		if ("C".equals(desc)) {
			return ast.Type_character();
		}
		
		if ("F".equals(desc)) {
			return ast.Type_float();
		}
		
		if ("D".equals(desc)) {
			return ast.Type_double();
		}
		
		if ("J".equals(desc)) {
			return ast.Type_long();
		}
		
		if ("V".equals(desc)) {
			return ast.Type_void();
		}
		
		throw new IllegalArgumentException("not a type descriptor: " + desc);
	}
}
