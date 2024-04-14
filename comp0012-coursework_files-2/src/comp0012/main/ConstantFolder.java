package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;


public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	private Integer getIntValue(Instruction instruction){
		if ((instruction instanceof BIPUSH))
			return ((BIPUSH) instruction).getValue().intValue();
		else if ((instruction instanceof SIPUSH))
			return ((SIPUSH) instruction).getValue().intValue();
		else if ((instruction instanceof ICONST))
			return ((ICONST) instruction).getValue().intValue();
		else if ((instruction instanceof LDC))
			return ((ConstantInteger) this.gen.getConstantPool().getConstant(((LDC) instruction).getIndex())).getBytes();
		return null;
	}

	private Instruction buildIntInstruction(int value){
		if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
			return new BIPUSH((byte) value);
		} else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
			return new SIPUSH((short) value);
		} else {
			return new LDC(value);
		}
	}

	private Double getDoubleValue(Instruction instruction){
		if ((instruction instanceof LDC2_W))
			return ((LDC2_W) instruction).getValue(this.gen.getConstantPool()).doubleValue();
		return  getIntValue(instruction).doubleValue();
	}

	private Long getLongValue(Instruction instruction){
		if ((instruction instanceof LDC2_W))
			return ((LDC2_W) instruction).getValue(this.gen.getConstantPool()).longValue();
		return  getDoubleValue(instruction).longValue();
	}

	private Instruction buildDoubleInstruction(double value){
		int index = this.gen.getConstantPool().addDouble(value);
		return new LDC2_W(index);
	}

	private Instruction buildLongInstruction(long value){
		int index = this.gen.getConstantPool().addLong(value);
		return new LDC2_W(index);
	}

	private Instruction buildBooleanInstruction(boolean value){
		if (value)
			return new ICONST(1);
		return new ICONST(0);
	}

	private void constantVariable(Method method, String className, ConstantPoolGen cpgen) {
		MethodGen methodGen = new MethodGen(method, this.gen.getClassName(), this.gen.getConstantPool());
		InstructionList instructionList = methodGen.getInstructionList();

		if (!method.getName().equals("<init>")){
			Map<Integer, InstructionHandle> storedValues = new HashMap<>();
			if (instructionList != null) {
				System.out.println("Instructions for method: " + method.getName());
				// checking for constant variables and putting the variables values
				InstructionHandle ih = instructionList.getStart();
				while(ih.getNext() != null) {
					if (ih.getInstruction() instanceof StoreInstruction) {
						StoreInstruction storeInstruction = (StoreInstruction) ih.getInstruction();
						storedValues.put(storeInstruction.getIndex(),ih.getPrev());
					}
					Instruction resultInstruction = null;
					int lastToRemove = 0;
					int nextToRemove = 0;
					if (
							(ih.getInstruction() instanceof ArithmeticInstruction) ||
									(ih.getInstruction() instanceof IfInstruction)
					)
					{
						List<Instruction> operands = new ArrayList<>();
						InstructionHandle x = ih.getPrev();
						while (operands.size() < 2){
							if (x.getInstruction() instanceof LoadInstruction){
								operands.add(storedValues.get(((LoadInstruction) x.getInstruction()).getIndex()).getInstruction());
							} else if (x.getInstruction() instanceof I2D) {
							} else if (x.getInstruction() instanceof LCMP) {
							} else{
								operands.add(x.getInstruction());
							}
							x = x.getPrev();
							lastToRemove++;
						}
						if (operands.size()==2){
							if (ih.getInstruction() instanceof IADD){
								resultInstruction = buildIntInstruction(getIntValue(operands.get(1))+getIntValue(operands.get(0)));
							} else if ((ih.getInstruction() instanceof IMUL)) {
								resultInstruction = buildIntInstruction(getIntValue(operands.get(1))*getIntValue(operands.get(0)));
							} else if ((ih.getInstruction() instanceof ISUB)) {
								resultInstruction = buildIntInstruction(getIntValue(operands.get(1))-getIntValue(operands.get(0)));
							} else if (ih.getInstruction() instanceof DADD) {
								resultInstruction = buildDoubleInstruction(getDoubleValue(operands.get(1))+getDoubleValue(operands.get(0)));
							} else if (ih.getInstruction() instanceof DMUL) {
								resultInstruction = buildDoubleInstruction(getDoubleValue(operands.get(1))*getDoubleValue(operands.get(0)));
							} else if (ih.getInstruction() instanceof DSUB) {
								resultInstruction = buildDoubleInstruction(getDoubleValue(operands.get(1))-getDoubleValue(operands.get(0)));
							} else if (ih.getInstruction() instanceof LADD) {
								resultInstruction = buildLongInstruction(getLongValue(operands.get(1))+getLongValue(operands.get(0)));
							} else if (ih.getInstruction() instanceof LMUL) {
								resultInstruction = buildLongInstruction(getLongValue(operands.get(1))*getLongValue(operands.get(0)));
							} else if (ih.getInstruction() instanceof LSUB) {
								resultInstruction = buildLongInstruction(getLongValue(operands.get(1))-getLongValue(operands.get(0)));
							} else if ((ih.getInstruction() instanceof IF_ICMPLT)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))<=getLongValue(operands.get(1)));
							} else if ((ih.getInstruction() instanceof IF_ICMPLE)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))<getLongValue(operands.get(1)));
							} else if ((ih.getInstruction() instanceof IF_ICMPGT)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))>=getLongValue(operands.get(1)));
							} else if ((ih.getInstruction() instanceof IF_ICMPGE)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))>getLongValue(operands.get(1)));
							} else if ((ih.getInstruction() instanceof IFLT)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))<=getLongValue(operands.get(1)));
							} else if ((ih.getInstruction() instanceof IFLE)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))<getLongValue(operands.get(1)));
							} else if ((ih.getInstruction() instanceof IFGT)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))>=getLongValue(operands.get(1)));
							} else if ((ih.getInstruction() instanceof IFGE)) {
								resultInstruction = buildBooleanInstruction(getLongValue(operands.get(0))>getLongValue(operands.get(1)));
							}
						}
					}
					if (ih.getInstruction() instanceof IfInstruction)
						nextToRemove = 3;
					boolean optimize = (lastToRemove > 0)||(nextToRemove > 0);
					if (optimize){
						try {
							InstructionHandle prev = ih;
							InstructionHandle next;
							// delete lasts
							if (lastToRemove > 0){
								for (int i = 1; i <= lastToRemove; i++ ){
									prev = prev.getPrev();
								}
								next = prev.getNext();
								for (int i = 1; i <= lastToRemove; i++ ){
									instructionList.delete(prev);
									prev = next;
									next = prev.getNext();
								}
							}
							// delete principal
							instructionList.insert(ih, resultInstruction);
							next = ih.getNext();
							instructionList.delete(ih);
							ih = next;
							// delete nexts
							for (int i = 1; i <= nextToRemove; i++ ){
								next = ih.getNext();
								instructionList.delete(ih);
								ih = next;
							}
						} catch (TargetLostException e) {
							throw new RuntimeException(e);
						}
					}else{
						ih = ih.getNext();
					}
				}
			}

			methodGen.setInstructionList(instructionList);
			methodGen.setMaxStack();
			methodGen.setMaxLocals();

			// Replace the method in the class
			gen.replaceMethod(method, methodGen.getMethod());

		}
	}
	public void SimpleFolding(Method method, String className, ConstantPoolGen cpgen) {
    
		
			MethodGen methodGen = new MethodGen(method, this.gen.getClassName(), this.gen.getConstantPool());
			InstructionList il = methodGen.getInstructionList();
			

			InstructionFinder f = new InstructionFinder(il);
			String pattern = "(LDC|LDC_W|LDC2_W) (LDC|LDC_W|LDC2_W) (IADD|ISUB|IMUL|IDIV)";
	
			// For each match of the pattern
			for (Iterator<?> it = f.search(pattern); it.hasNext(); ) {
				InstructionHandle[] match = (InstructionHandle[]) it.next();
				Number n1 = (Number) ((LDC) match[0].getInstruction()).getValue(cpgen);
				Number n2 = (Number) ((LDC) match[1].getInstruction()).getValue(cpgen);
				int result = 0;
	
				// Perform the operation based on the opcode
				switch (match[2].getInstruction().getOpcode()) {
	
					//integer cases
					case Constants.IADD:
						result = n1.intValue() + n2.intValue();
						il.insert(match[0], new LDC(cpgen.addInteger(result)));
						break;
	
					case Constants.ISUB:
						result = n1.intValue() - n2.intValue();
						il.insert(match[0], new LDC(cpgen.addInteger(result)));
						break;
	
					case Constants.IMUL:
						result = n1.intValue() * n2.intValue();
						il.insert(match[0], new LDC(cpgen.addInteger(result)));
						break;
	
					case Constants.IDIV:
						if (n2.intValue() != 0) { // Prevent division by zero
							result = n1.intValue() / n2.intValue();
							il.insert(match[0], new LDC(cpgen.addInteger(result)));
						}
						break;
	
					//long cases
					case Constants.LADD:
						long lresult = n1.longValue() + n2.longValue();
						il.insert(match[0], new LDC2_W(cpgen.addLong(lresult)));
						break;
					
					case Constants.LSUB:
						long lsubResult = n1.longValue() - n2.longValue();
						il.insert(match[0], new LDC2_W(cpgen.addLong(lsubResult)));
						break;
	
					case Constants.LMUL:
						long lmulResult = n1.longValue() * n2.longValue();
						il.insert(match[0], new LDC2_W(cpgen.addLong(lmulResult)));
						break;
	
					case Constants.LDIV:
						if (n2.longValue() != 0) {
							long ldivResult = n1.longValue() / n2.longValue();
							il.insert(match[0], new LDC2_W(cpgen.addLong(ldivResult)));
							break;
						}
					
					//float cases
					case Constants.FADD:
						float fresult = n1.floatValue() + n2.floatValue();
						il.insert(match[0], new LDC(cpgen.addFloat(fresult)));
						break;
					
					case Constants.FSUB:
						float fsubResult = n1.floatValue() - n2.floatValue();
						il.insert(match[0], new LDC(cpgen.addFloat(fsubResult)));
						break;
					case Constants.FMUL:
						float fmulResult = n1.floatValue() * n2.floatValue();
						il.insert(match[0], new LDC(cpgen.addFloat(fmulResult)));
						break;
					case Constants.FDIV:
						if (n2.floatValue() != 0.0f) {
							float fdivResult = n1.floatValue() / n2.floatValue();
							il.insert(match[0], new LDC(cpgen.addFloat(fdivResult)));
						}
					//double cases
					case Constants.DADD:
						double dresult = n1.doubleValue() + n2.doubleValue();
						il.insert(match[0], new LDC2_W(cpgen.addDouble(dresult)));
						break;
	
					case Constants.DSUB:
						double dsubResult = n1.doubleValue() - n2.doubleValue();
						il.insert(match[0], new LDC2_W(cpgen.addDouble(dsubResult)));
						break;
	
					case Constants.DMUL:
						double dmulResult = n1.doubleValue() * n2.doubleValue();
						il.insert(match[0], new LDC2_W(cpgen.addDouble(dmulResult)));
						break;
	
					case Constants.DDIV:
						if (n2.doubleValue() != 0.0) {
							double ddivResult = n1.doubleValue() / n2.doubleValue();
							il.insert(match[0], new LDC2_W(cpgen.addDouble(ddivResult)));
							break;
						}
					}
	
				
				try {
					il.delete(match[0], match[2]); // Remove the old instructions
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
				methodGen.setInstructionList(il);
				methodGen.setMaxStack();
				methodGen.setMaxLocals();
				gen.replaceMethod(method, methodGen.getMethod());
			}
			
		
	}


	public void optimize() {
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

       
		for (Method method : original.getMethods()) {
			MethodGen mg = new MethodGen(method, original.getClassName(), cpgen);
			InstructionList il = mg.getInstructionList();
			if (il == null) continue;
			InstructionFinder f = new InstructionFinder(il);
			System.out.println("Optimizing method: " + method.getName());

			boolean changesMade = false;
			Set<InstructionHandle> visitedHandles = new HashSet<>();
			Map<Integer, List<InstructionHandle>> loadMap = new HashMap<>();


			for (Iterator<InstructionHandle[]> it = f.search("(StoreInstruction)"); it.hasNext(); ) {
				InstructionHandle[] match = it.next();
				StoreInstruction store = (StoreInstruction) match[0].getInstruction();
				INVOKEVIRTUAL invoke = (INVOKEVIRTUAL) match[0].getInstruction();

				if (visitedHandles.contains(match[0])) {
					continue; 
				}

				// Determine if the store is dead 
				boolean isDead = true;
				for (InstructionHandle ih = match[0].getNext(); ih != null; ih = ih.getNext()) {
					Instruction instr = ih.getInstruction();
					if (instr instanceof LoadInstruction && ((LoadInstruction) instr).getIndex() == store.getIndex()) {
						isDead = false;
						break;
					}
					if (instr instanceof StoreInstruction && ((StoreInstruction) instr).getIndex() == store.getIndex()) {
						break;
					}
				}

				if (isDead) {
					System.out.println("Identified dead store at index " + store.getIndex() + " for variable in method " + method.getName());

					// Instructions that contribute to this dead store
					Set<InstructionHandle> toDelete = new HashSet<>();
					toDelete.add(match[0]); 

					InstructionHandle current = match[0].getPrev();
					while (current != null && !(current.getInstruction() instanceof StoreInstruction)){
						toDelete.add(current);
						current = current.getPrev();
					}

					try {
						for (InstructionHandle ih : toDelete) {
							System.out.println("Removing instruction: " + ih.toString());
							il.delete(ih);
							visitedHandles.add(ih);
						}
						changesMade = true;
					} catch (TargetLostException e) {
						for (InstructionHandle target : e.getTargets()) {
							for (InstructionTargeter targeter : target.getTargeters()) {
								targeter.updateTarget(target, null);
							}
						}
					}
				} else {
					System.out.println("Store at index " + store.getIndex() + " is active and used in method " + method.getName());
				}
			}

			if (changesMade) {
				mg.setInstructionList(il);
				mg.setMaxStack();
				mg.setMaxLocals();
				mg.removeNOPs();
				Method newMethod = mg.getMethod();
				cgen.replaceMethod(method, newMethod);
				cgen.setMajor(50);
				System.out.println("Method " + method.getName() + " optimized and replaced.");
			}
		}

		if (this.gen.getClassName().equals("comp0012.target.ConstantVariableFolding")){
			Method[] methods = this.gen.getMethods();

			for (Method method : methods) {
				constantVariable(method, this.gen.getClassName(), this.gen.getConstantPool());

			}
		}

		if (this.gen.getClassName().equals("comp0012.target.SimpleFolding")){
			Method[] methods = this.gen.getMethods();

			for (Method method : methods) {
				SimpleFolding(method, this.gen.getClassName(), this.gen.getConstantPool());

			}
		}

		this.optimized = cgen.getJavaClass();
	}


	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}