package comp0012.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;


public class ConstantFolder {

  ClassParser parser = null;
  ClassGen gen = null;

  JavaClass original = null;
  JavaClass optimized = null;
  Stack<Object> valueStack;
  Stack<InstructionHandle> instructionStack;

  public ConstantFolder(String classFilePath) {
    try {
      this.parser = new ClassParser(classFilePath);
      this.original = this.parser.parse();
      this.gen = new ClassGen(original);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void optimize() {

    System.out.println("Optimizing class: " + this.gen.getClassName());
    Method[] methods = this.gen.getMethods();

    for (Method method : methods) {
      MethodGen methodGen = new MethodGen(method, this.gen.getClassName(), this.gen.getConstantPool());
      foldConstants(methodGen);
      removeDeadCode(methodGen);
      gen.replaceMethod(method, methodGen.getMethod());
    }

    this.optimized = gen.getJavaClass();
  }

  public void write(String optimisedFilePath) {
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

  private void removeDeadCode(MethodGen mg) {
//    MethodGen mg = new MethodGen(method, this.gen.getClassName(), this.gen.getConstantPool());

    System.out.println("Optimizing method: " + mg.getMethod().getName());
    while (tryRemoveDeadCode(mg)) {
    }
  }

  private boolean tryRemoveDeadCode(MethodGen mg) {
    InstructionList il = mg.getInstructionList();
    if (il == null) {
      return false;
    }
    InstructionFinder f = new InstructionFinder(il);

    for (Iterator<InstructionHandle[]> it = f.search("(StoreInstruction)"); it.hasNext(); ) {
      InstructionHandle[] match = it.next();
      if (match[0] == null) {
        continue;
      }
      StoreInstruction store = (StoreInstruction) match[0].getInstruction();

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

      InstructionHandle handle = match[0];

      if (isDead) {
        System.out.println("Dead code: " + handle);
        System.out.println("Prev: " + handle.getPrev());
        // Instructions that contribute to this dead store
        List<InstructionHandle> toDelete = new ArrayList<>();
        toDelete.add(match[0]);

        InstructionHandle current = match[0].getPrev();
        while (current != null && !(current.getInstruction() instanceof StoreInstruction)) {
          toDelete.add(current);
          current = current.getPrev();
        }

        Collections.reverse(toDelete);

        try {

          il.delete(toDelete.get(0), toDelete.get(toDelete.size() - 1));
        } catch (TargetLostException e) {
          System.out.println("Target lost exception");
          for (InstructionHandle target : e.getTargets()) {
            for (InstructionTargeter targeter : target.getTargeters()) {
              targeter.updateTarget(target, null);
            }
          }
        }

        // Print all instruction handles
        for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
          System.out.println(ih);
        }

        mg.setInstructionList(il);
        mg.setMaxStack();
        mg.setMaxLocals();
        mg.removeNOPs();
        gen.setMajor(50);

        return true;
      }
    }

    return false;
  }

  private void foldConstants(MethodGen methodGen) {

    if (methodGen.getMethod().getName().equals("<init>")) {
      return;
    }

    while (tryOptimize(methodGen)) {
    }

  }

  boolean tryOptimize(MethodGen methodGen) {

    InstructionList instructionList = methodGen.getInstructionList();
    boolean optimized = false;
    // Map of variable values, null means the value is not constant
    Map<Integer, Object> variableValues = new HashMap<>();
    valueStack = new Stack<>();
    instructionStack = new Stack<>();

    for (int i = 0; i < instructionList.getLength(); i++) {
      Instruction inst = instructionList.getInstructionHandles()[i].getInstruction();

      if (inst instanceof ConversionInstruction) {
        Object lastValue = valueStack.pop();
        valueStack.push(convertValue(inst, lastValue));
      } else if (inst instanceof PushInstruction) {
        valueStack.push(getValue(inst, variableValues));
        instructionStack.push(instructionList.getInstructionHandles()[i]);
      } else if (inst instanceof StoreInstruction) {
        int index = ((StoreInstruction) inst).getIndex();
        Object value = valueStack.pop();
        instructionStack.pop();
        variableValues.put(index, value);
      } else if (isCompareInstruction(inst)) {
        valueStack.pop();
        instructionStack.pop();
        valueStack.pop();
        instructionStack.pop();
        valueStack.push(null);
        instructionStack.push(instructionList.getInstructionHandles()[i]);

      } else if (inst instanceof IfInstruction) {

        InstructionHandle lastHandle;
        Boolean result;
        if (isSingletonIfInstruction(inst)) {
          Object value = valueStack.pop();
          lastHandle = instructionStack.pop();
        } else {
          Object value2 = valueStack.pop();
          instructionStack.pop();
          Object value1 = valueStack.pop();
          lastHandle = instructionStack.pop();
        }

        removeValuesThatChangeInIf(instructionList.getInstructionHandles()[i], variableValues);

        valueStack.push(null);
        instructionStack.push(instructionList.getInstructionHandles()[i]);

      } else if (inst instanceof ArithmeticInstruction) {
        Object value2 = valueStack.pop();
        instructionStack.pop();
        Object value1 = valueStack.pop();
        InstructionHandle lastHandle = instructionStack.pop();
        // res = value1 op value2

        if (value1 == null || value2 == null) {
          valueStack.push(null);
          instructionStack.push(instructionList.getInstructionHandles()[i]);
          continue;
        }

        Instruction resultInst = getResultInstruction(inst, value1, value2);

        if (resultInst != null) {

          instructionList.append(instructionList.getInstructionHandles()[i], resultInst);
          try {
            instructionList.delete(lastHandle, instructionList.getInstructionHandles()[i]);
          } catch (TargetLostException e) {
            for (InstructionHandle target : e.getTargets()) {
              for (InstructionTargeter targeter : target.getTargeters()) {
                targeter.updateTarget(target, null);
              }
            }
          }

          optimized = true;
          break;
        } else {
          valueStack.push(null);
          instructionStack.push(instructionList.getInstructionHandles()[i]);
        }
      } else if (inst instanceof PopInstruction) {
        valueStack.pop();
        instructionStack.pop();
      }
    }

    if (optimized) {
      instructionList.setPositions(true);
      methodGen.setInstructionList(instructionList);
      methodGen.setMaxStack();
      methodGen.setMaxLocals();
      return true;
    } else {
      return false;
    }
  }

  private void removeValuesThatChangeInIf(InstructionHandle ifHandle, Map<Integer, Object> variableValues) {
    BranchInstruction ifInst = (BranchInstruction) ifHandle.getInstruction();
    InstructionHandle target = ifInst.getTarget();

    for (InstructionHandle handle = ifHandle.getNext(); handle != target; handle = handle.getNext()) {
      if (handle.getInstruction() instanceof StoreInstruction) {
        int index = ((StoreInstruction) handle.getInstruction()).getIndex();
        if (variableValues.get(index) != null) {
          variableValues.put(index, null);
        }
      } else if (handle.getInstruction() instanceof IINC) {
        int index = ((IINC) handle.getInstruction()).getIndex();
        if (variableValues.get(index) != null) {
          variableValues.put(index, null);
        }
      }
    }
  }

  private boolean isCompareInstruction(Instruction inst) {
    return inst instanceof DCMPG || inst instanceof DCMPL || inst instanceof FCMPG || inst instanceof FCMPL
        || inst instanceof LCMP;
  }

  private boolean isSingletonIfInstruction(Instruction inst) {
    return inst instanceof IFNULL ||
        inst instanceof IFNONNULL ||
        inst instanceof IFEQ ||
        inst instanceof IFNE ||
        inst instanceof IFLT ||
        inst instanceof IFGE ||
        inst instanceof IFGT ||
        inst instanceof IFLE;
  }

  private Object convertValue(Instruction inst, Object value) {
    assert (inst instanceof ConversionInstruction);

    if (value == null) {
      return null;
    }

    if (inst instanceof I2F) {
      return (float) (int) value;
    } else if (inst instanceof I2D) {
      return (double) (int) value;
    } else if (inst instanceof I2L) {
      return (long) (int) value;
    } else if (inst instanceof I2B) {
      return (byte) (int) value;
    } else if (inst instanceof I2C) {
      return (char) (int) value;
    } else if (inst instanceof I2S) {
      return (short) (int) value;
    } else if (inst instanceof F2I) {
      return (int) (float) value;
    } else if (inst instanceof F2D) {
      return (double) (float) value;
    } else if (inst instanceof F2L) {
      return (long) (float) value;
    } else if (inst instanceof D2I) {
      return (int) (double) value;
    } else if (inst instanceof D2F) {
      return (float) (double) value;
    } else if (inst instanceof D2L) {
      return (long) (double) value;
    } else if (inst instanceof L2I) {
      return (int) (long) value;
    } else if (inst instanceof L2F) {
      return (float) (long) value;
    } else if (inst instanceof L2D) {
      return (double) (long) value;
    } else {
      return null;
    }
  }

  private Instruction getResultInstruction(Instruction inst, Object value1, Object value2) {
    if (inst instanceof IADD) {
      return buildIntInstruction((Integer) value1 + (Integer) value2);
    } else if (inst instanceof FADD) {
      return buildFloatInstruction((Float) value1 + (Float) value2);
    } else if (inst instanceof DADD) {
      return buildDoubleInstruction((Double) value1 + (Double) value2);
    } else if (inst instanceof LADD) {
      return buildLongInstruction((Long) value1 + (Long) value2);
    } else if (inst instanceof ISUB) {
      return buildIntInstruction((Integer) value1 - (Integer) value2);
    } else if (inst instanceof FSUB) {
      return buildFloatInstruction((Float) value1 - (Float) value2);
    } else if (inst instanceof DSUB) {
      return buildDoubleInstruction((Double) value1 - (Double) value2);
    } else if (inst instanceof LSUB) {
      return buildLongInstruction((Long) value1 - (Long) value2);
    } else if (inst instanceof IMUL) {
      return buildIntInstruction((Integer) value1 * (Integer) value2);
    } else if (inst instanceof FMUL) {
      return buildFloatInstruction((Float) value1 * (Float) value2);
    } else if (inst instanceof DMUL) {
      return buildDoubleInstruction((Double) value1 * (Double) value2);
    } else if (inst instanceof LMUL) {
      return buildLongInstruction((Long) value1 * (Long) value2);
    } else if (inst instanceof IDIV) {
      return buildIntInstruction((Integer) value1 / (Integer) value2);
    } else if (inst instanceof FDIV) {
      return buildFloatInstruction((Float) value1 / (Float) value2);
    } else if (inst instanceof DDIV) {
      return buildDoubleInstruction((Double) value1 / (Double) value2);
    } else if (inst instanceof LDIV) {
      return buildLongInstruction((Long) value1 / (Long) value2);
    } else if (inst instanceof IREM) {
      return buildIntInstruction((Integer) value1 % (Integer) value2);
    } else if (inst instanceof FREM) {
      return buildFloatInstruction((Float) value1 % (Float) value2);
    } else if (inst instanceof DREM) {
      return buildDoubleInstruction((Double) value1 % (Double) value2);
    } else if (inst instanceof LREM) {
      return buildLongInstruction((Long) value1 % (Long) value2);
    } else {
      return null;
    }
  }

  private Object getValue(Instruction inst, Map<Integer, Object> variableValues) {
    if (inst instanceof ICONST) {
      return ((ICONST) inst).getValue();
    } else if (inst instanceof FCONST) {
      return ((FCONST) inst).getValue();
    } else if (inst instanceof DCONST) {
      return ((DCONST) inst).getValue();
    } else if (inst instanceof LCONST) {
      return ((LCONST) inst).getValue();
    } else if (inst instanceof BIPUSH) {
      return ((BIPUSH) inst).getValue();
    } else if (inst instanceof SIPUSH) {
      return ((SIPUSH) inst).getValue();
    } else if (inst instanceof LDC_W) {
      return ((LDC_W) inst).getValue(this.gen.getConstantPool());
    } else if (inst instanceof LDC2_W) {
      return ((LDC2_W) inst).getValue(this.gen.getConstantPool());
    } else if (inst instanceof LDC) {
      return ((LDC) inst).getValue(this.gen.getConstantPool());
    } else if (inst instanceof LoadInstruction) {
      int index = ((LoadInstruction) inst).getIndex();
      return variableValues.get(index);
    } else {
      return null;
    }
  }

  private Instruction buildIntInstruction(int value) {
    if (value >= -1 && value <= 5) {
      return new ICONST(value);
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      return new BIPUSH((byte) value);
    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      return new SIPUSH((short) value);
    } else {
      int index = this.gen.getConstantPool().addInteger(value);
      return new LDC(index);
    }
  }

  private Instruction buildFloatInstruction(float value) {
    if (value == 0.0) {
      return new FCONST(0);
    } else if (value == 1.0) {
      return new FCONST(1);
    } else if (value == 2.0) {
      return new FCONST(2);
    } else {
      int index = this.gen.getConstantPool().addFloat(value);
      return new LDC(index);
    }
  }

  private Instruction buildLongInstruction(long value) {
    if (value == 0) {
      return new LCONST(0);
    } else if (value == 1) {
      return new LCONST(1);
    } else {
      int index = this.gen.getConstantPool().addLong(value);
      return new LDC2_W(index);
    }
  }

  private Instruction buildDoubleInstruction(double value) {
    if (value == 0.0) {
      return new DCONST(0);
    } else if (value == 1.0) {
      return new DCONST(1);
    } else {
      int index = this.gen.getConstantPool().addDouble(value);
      return new LDC2_W(index);
    }
  }
}