/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tajo.engine.planner;


import org.apache.tajo.catalog.Schema;
import org.apache.tajo.cli.InvalidStatementException;
import org.apache.tajo.common.TajoDataTypes;
import org.apache.tajo.datum.*;
import org.apache.tajo.engine.eval.*;
import org.apache.tajo.storage.Tuple;
import org.apache.tajo.storage.VTuple;
import org.apache.tajo.util.CodeGenUtil;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Stack;

public class TestExprCodeGenerator extends ExprTestBase {
  private static Schema schema;
  static {
    schema = new Schema();
    schema.addColumn("col1", TajoDataTypes.Type.INT2);
    schema.addColumn("col2", TajoDataTypes.Type.INT4);
    schema.addColumn("col3", TajoDataTypes.Type.INT8);
    schema.addColumn("col4", TajoDataTypes.Type.FLOAT4);
    schema.addColumn("col5", TajoDataTypes.Type.FLOAT8);
  }

  public TestExprCodeGenerator() {
    super(true);
  }

  @Test
  public void testArithmetic() throws IOException {
    testEval(schema, "table1", "1,2,3,4.5,6.5", "select 1+1;", new String [] {"2"});
  }

  @Test
  public void testGetField() throws IOException {
    testEval(schema, "table1", "1,2,3,4.5,5.5", "select col1 from table1;", new String [] {"1"});
    testEval(schema, "table1", "1,2,3,4.5,5.5", "select col2 from table1;", new String [] {"2"});
    testEval(schema, "table1", "1,2,3,4.5,5.5", "select col3 from table1;", new String [] {"3"});
    testEval(schema, "table1", "1,2,3,4.5,5.5", "select col4 from table1;", new String [] {"4.5"});
    testEval(schema, "table1", "1,2,3,4.5,5.5", "select col5 from table1;", new String [] {"5.5"});
  }

  public static class CodeGenContext {
    private Schema schema;

    private ClassWriter classWriter;
    private MethodVisitor evalMethod;

    public CodeGenContext(Schema schema) {
      this.schema = schema;

      classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      classWriter.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "org/Test3", null, getClassName(EvalNode.class), null);
      classWriter.visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;",
          null, null).visitEnd();

      // constructor method
      MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
      methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, getClassName(EvalNode.class), "<init>", "()V");
      methodVisitor.visitInsn(Opcodes.RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
  }

  public static String getClassName(Class clazz) {
    return clazz.getName().replace('.', '/');
  }

  public static class ExprCodeGenerator extends BasicEvalNodeVisitor<CodeGenContext, EvalNode> {


    private static void invokeInitDatum(CodeGenContext context, Class clazz, String paramDesc) {
      String slashedName = getClassName(clazz);
      context.evalMethod.visitMethodInsn(Opcodes.INVOKESPECIAL, slashedName, "<init>", paramDesc);
      context.evalMethod.visitTypeInsn(Opcodes.CHECKCAST, getClassName(Datum.class));
      context.evalMethod.visitInsn(Opcodes.ARETURN);
      context.evalMethod.visitMaxs(0, 0);
      context.evalMethod.visitEnd();
      context.classWriter.visitEnd();
    }

    public EvalNode generate(Schema schema, EvalNode expr) throws NoSuchMethodException, IllegalAccessException,
        InvocationTargetException, InstantiationException, PlanningException {
      CodeGenContext context = new CodeGenContext(schema);
      // evalMethod
      context.evalMethod = context.classWriter.visitMethod(Opcodes.ACC_PUBLIC, "eval",
          "(Lorg/apache/tajo/catalog/Schema;Lorg/apache/tajo/storage/Tuple;)Lorg/apache/tajo/datum/Datum;", null, null);
      context.evalMethod.visitCode();

      Class returnTypeClass;
      String signatureDesc;
      switch (expr.getValueType().getType()) {
      case INT2:
        returnTypeClass = Int2Datum.class;
        signatureDesc = "(S)V";
        break;
      case INT4:
        returnTypeClass = Int4Datum.class;
        signatureDesc = "(I)V";
        break;
      case INT8:
        returnTypeClass = Int8Datum.class;
        signatureDesc = "(J)V";
        break;
      case FLOAT4:
        returnTypeClass = Float4Datum.class;
        signatureDesc = "(F)V";
        break;
      case FLOAT8:
        returnTypeClass = Float8Datum.class;
        signatureDesc = "(D)V";
        break;
      default:
        throw new PlanningException("Unsupported type: " + expr.getValueType().getType());
      }

      context.evalMethod.visitTypeInsn(Opcodes.NEW, getClassName(returnTypeClass));
      context.evalMethod.visitInsn(Opcodes.DUP);

      visitChild(context, expr, new Stack<EvalNode>());

      invokeInitDatum(context, returnTypeClass, signatureDesc);

      MyClassLoader myClassLoader = new MyClassLoader();
      Class aClass = myClassLoader.defineClass("org.Test3", context.classWriter.toByteArray());
      Constructor constructor = aClass.getConstructor();
      EvalNode r = (EvalNode) constructor.newInstance();
      return r;
    }

    public EvalNode visitField(CodeGenContext context, Stack<EvalNode> stack, FieldEval evalNode) {
      int idx = context.schema.getColumnId(evalNode.getColumnRef().getQualifiedName());

      String methodName;
      String desc;
      switch (evalNode.getValueType().getType()) {
      case INT1:
      case INT2:
      case INT4: methodName = "getInt4"; desc = "(I)I"; break;
      case INT8: methodName = "getInt8"; desc = "(I)J"; break;
      case FLOAT4: methodName = "getFloat4"; desc = "(I)F"; break;
      case FLOAT8: methodName = "getFloat8"; desc = "(I)D"; break;
      default: throw new InvalidEvalException(evalNode.getType() + " is not supported yet");
      }

      context.evalMethod.visitVarInsn(Opcodes.ALOAD, 2);
      context.evalMethod.visitLdcInsn(idx);
      context.evalMethod.visitMethodInsn(Opcodes.INVOKEINTERFACE, getClassName(Tuple.class), methodName, desc);

      return null;
    }

    public EvalNode visitPlus(CodeGenContext context, BinaryEval evalNode, Stack<EvalNode> stack) {
      super.visitPlus(context, evalNode, stack);

      int opcode;
      switch (evalNode.getValueType().getType()) {
      case INT4:
        opcode = Opcodes.IADD;
        break;
      case INT8:
        opcode = Opcodes.LADD;
        break;
      case FLOAT4:
        opcode = Opcodes.FADD;
        break;
      case FLOAT8:
        opcode = Opcodes.DADD;
        break;
      default:
        throw new RuntimeException("Plus does not support:" + evalNode.getValueType().getType());
      }

      context.evalMethod.visitInsn(opcode);

      return null;
    }

    public EvalNode visitMinus(CodeGenContext context, BinaryEval evalNode, Stack<EvalNode> stack) {
      super.visitMinus(context, evalNode, stack);

      int opcode;
      switch (evalNode.getValueType().getType()) {
      case INT4:
        opcode = Opcodes.ISUB;
        break;
      case INT8:
        opcode = Opcodes.LSUB;
        break;
      case FLOAT4:
        opcode = Opcodes.FSUB;
        break;
      case FLOAT8:
        opcode = Opcodes.DSUB;
        break;
      default:
        throw new RuntimeException("Plus does not support:" + evalNode.getValueType().getType());
      }

      context.evalMethod.visitInsn(opcode);

      return null;
    }

    @Override
    public EvalNode visitMultiply(CodeGenContext context, BinaryEval evalNode, Stack<EvalNode> stack) {
      super.visitMultiply(context, evalNode, stack);

      int opcode;
      switch (evalNode.getValueType().getType()) {
      case INT4:
        opcode = Opcodes.IMUL;
        break;
      case INT8:
        opcode = Opcodes.LMUL;
        break;
      case FLOAT4:
        opcode = Opcodes.FMUL;
        break;
      case FLOAT8:
        opcode = Opcodes.DMUL;
        break;
      default:
        throw new RuntimeException("Plus does not support:" + evalNode.getValueType().getType());
      }

      context.evalMethod.visitInsn(opcode);

      return null;
    }

    @Override
    public EvalNode visitDivide(CodeGenContext context, BinaryEval evalNode, Stack<EvalNode> stack) {
      super.visitDivide(context, evalNode, stack);

      int opcode;
      switch (evalNode.getValueType().getType()) {
      case INT4:
        opcode = Opcodes.IDIV;
        break;
      case INT8:
        opcode = Opcodes.LDIV;
        break;
      case FLOAT4:
        opcode = Opcodes.FDIV;
        break;
      case FLOAT8:
        opcode = Opcodes.DDIV;
        break;
      default:
        throw new RuntimeException("Plus does not support:" + evalNode.getValueType().getType());
      }

      context.evalMethod.visitInsn(opcode);

      return null;
    }

    @Override
    public EvalNode visitModular(CodeGenContext context, BinaryEval evalNode, Stack<EvalNode> stack) {

      super.visitModular(context, evalNode, stack);

      int opcode;
      switch (evalNode.getValueType().getType()) {
      case INT4:
        opcode = Opcodes.IREM;
        break;
      case INT8:
        opcode = Opcodes.LREM;
        break;
      case FLOAT4:
        opcode = Opcodes.FREM;
        break;
      case FLOAT8:
        opcode = Opcodes.DREM;
        break;
      default:
        throw new RuntimeException("Plus does not support:" + evalNode.getValueType().getType());
      }

      context.evalMethod.visitInsn(opcode);

      return null;
    }

    public EvalNode visitConst(CodeGenContext context, ConstEval evalNode, Stack<EvalNode> stack) {
      switch (evalNode.getValueType().getType()) {
      case INT2:
      case INT4:
        context.evalMethod.visitLdcInsn(evalNode.getValue().asInt4());
        break;
      case INT8:
        context.evalMethod.visitLdcInsn(evalNode.getValue().asInt8());
        break;
      case FLOAT4:
        context.evalMethod.visitLdcInsn(evalNode.getValue().asFloat4());
        break;
      case FLOAT8:
        context.evalMethod.visitLdcInsn(evalNode.getValue().asFloat8());
      }
      return evalNode;
    }

    @Override
    public EvalNode visitCast(CodeGenContext context, CastEval signedEval, Stack<EvalNode> stack) {
      super.visitCast(context, signedEval, stack);

      TajoDataTypes.Type srcType = signedEval.getOperand().getValueType().getType();
      TajoDataTypes.Type targetType = signedEval.getValueType().getType();
      CodeGenUtil.insertCastInst(context.evalMethod, srcType, targetType);

      return null;
    }
  }

  //@Test
  public void testGenerateCodeFromQuery() throws InvalidStatementException, PlanningException,
      InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    ExprCodeGenerator generator = new ExprCodeGenerator();

    Schema schema = new Schema();
    schema.addColumn("col1", TajoDataTypes.Type.INT2);
    schema.addColumn("col2", TajoDataTypes.Type.INT4);
    schema.addColumn("col3", TajoDataTypes.Type.INT8);
    schema.addColumn("col4", TajoDataTypes.Type.FLOAT4);
    schema.addColumn("col5", TajoDataTypes.Type.FLOAT8);

    Tuple tuple = new VTuple(5);
    tuple.put(0, DatumFactory.createInt2((short) 1));
    tuple.put(1, DatumFactory.createInt4(2));
    tuple.put(2, DatumFactory.createInt8(3));
    tuple.put(3, DatumFactory.createFloat4(4.0f));
    tuple.put(4, DatumFactory.createFloat8(5.0f));

    //Target [] targets = getRawTargets("select 5 + 2 * 3 % 6", true);
    Target [] targets = null;
    long start = System.currentTimeMillis();
    EvalNode code = generator.generate(null, targets[0].getEvalTree());
    long end = System.currentTimeMillis();
    System.out.println(code.eval(schema, tuple));
    long execute = System.currentTimeMillis();

    System.out.println(end - start + " msec");
    System.out.println(execute - end + " execute");
  }

  @Test
  public void testGenerateCode() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
      InstantiationException {
    MyClassLoader myClassLoader = new MyClassLoader();
    Class aClass = myClassLoader.defineClass("org.Test2", getBytecodeForClass());
    System.out.println(aClass.getSimpleName());
    Constructor constructor = aClass.getConstructor();
    Example r = (Example) constructor.newInstance();
    r.run("test");
  }

  public static class Example {
    public void run(String msg) {
      System.out.println(msg);
    }
  }

  @Test
  public void testGenerateObjectReturn() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
      InstantiationException {
    MyClassLoader myClassLoader = new MyClassLoader();
    Class aClass = myClassLoader.defineClass("org.Test3", getBytecodeForObjectReturn());
    Constructor constructor = aClass.getConstructor();
    NewMockUp r = (NewMockUp) constructor.newInstance();
    System.out.println(r.eval(1, 5));
  }



  public static class NewMockUp {
    public Datum eval(int x, int y) {
      return null;
    }
  }

  public static byte[] getBytecodeForObjectReturn() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "org/Test3", null, "org/apache/tajo/engine/planner/TestExprCodeGenerator$NewMockUp", null);
    cw.visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;",
        null, null).visitEnd();

    MethodVisitor methodVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/apache/tajo/engine/planner/TestExprCodeGenerator$NewMockUp", "<init>", "()V");
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();

    methodVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "eval", "(II)Lorg/apache/tajo/datum/Datum;", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);

    methodVisitor.visitTypeInsn(Opcodes.NEW, "org/apache/tajo/datum/Int4Datum");
    methodVisitor.visitInsn(Opcodes.DUP);

    methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
    methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
    methodVisitor.visitInsn(Opcodes.IADD);

    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/apache/tajo/datum/Int4Datum", "<init>", "(I)V");
    methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "org/apache/tajo/datum/Datum");
    methodVisitor.visitInsn(Opcodes.ARETURN);
    methodVisitor.visitMaxs(0, 0);
    methodVisitor.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  public void testGenerateCodePlus() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
      InstantiationException {
    MyClassLoader myClassLoader = new MyClassLoader();
    Class aClass = myClassLoader.defineClass("org.Test2", getBytecodeForPlus());
    Constructor constructor = aClass.getConstructor();
    PlusExpr r = (PlusExpr) constructor.newInstance();
    System.out.println(r.eval(1, 3));
  }

  public static class PlusExpr {
    public int eval(int x, int y) {
      return x + y;
    }
  }

  public static byte[] getBytecodeForPlus() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "org/Test2", null, "org/apache/tajo/engine/planner/TestExprCodeGenerator$PlusExpr", null);
    cw.visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;",
        null, null).visitEnd();

    System.out.println(Opcodes.ACC_PUBLIC);
    MethodVisitor methodVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/apache/tajo/engine/planner/TestExprCodeGenerator$PlusExpr", "<init>", "()V");
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();

    methodVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "eval", "(II)I", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
    methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
    methodVisitor.visitInsn(Opcodes.IADD);
    methodVisitor.visitInsn(Opcodes.IRETURN);
    methodVisitor.visitMaxs(0, 0);
    methodVisitor.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  public static byte[] getBytecodeForClass() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "org/Test2", null, "org/apache/tajo/engine/planner/TestExprCodeGenerator$Example", null);
    cw.visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;",
        null, null).visitEnd();

    System.out.println(Opcodes.ACC_PUBLIC);
    MethodVisitor methodVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/apache/tajo/engine/planner/TestExprCodeGenerator$Example", "<init>", "()V");
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();


    methodVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "(Ljava/lang/String;)V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J");
    methodVisitor.visitVarInsn(Opcodes.LSTORE, 2);
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/apache/tajo/engine/planner/TestExprCodeGenerator$Example", "run", "(Ljava/lang/String;)V");
    methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J");
    methodVisitor.visitVarInsn(Opcodes.LLOAD, 2);
    methodVisitor.visitInsn(Opcodes.LSUB);
    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(J)V");
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(5, 4);
    methodVisitor.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  static class MyClassLoader extends ClassLoader {
    public Class defineClass(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }
}