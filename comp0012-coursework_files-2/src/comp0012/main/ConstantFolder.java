package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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


	public void optimize() {
		//ClassGen cgen = new ClassGen(original);
		//ConstantPoolGen cpgen = cgen.getConstantPool();

		if (this.gen.getClassName().equals("comp0012.target.ConstantVariableFolding")){
			Method[] methods = this.gen.getMethods();

			for (Method method : methods) {
				constantVariable(method, this.gen.getClassName(), this.gen.getConstantPool());

			}
		}

		this.optimized = gen.getJavaClass();
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