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

package org.apache.tajo.engine.eval;

import org.apache.tajo.catalog.*;
import org.apache.tajo.common.TajoDataTypes;
import org.apache.tajo.datum.Datum;
import org.apache.tajo.datum.DatumFactory;
import org.apache.tajo.plan.expr.*;
import org.apache.tajo.storage.Tuple;
import org.apache.tajo.storage.VTuple;
import org.apache.tajo.type.Type;
import org.junit.Test;

import static org.apache.tajo.common.TajoDataTypes.Type.INT4;
import static org.apache.tajo.common.TajoDataTypes.Type.TEXT;
import static org.junit.Assert.*;

public class TestEvalTree extends ExprTestBase {
  @Test
  public void testTupleEval() throws CloneNotSupportedException {
    ConstEval e1 = new ConstEval(DatumFactory.createInt4(1));
    assertCloneEqual(e1);
    FieldEval e2 = new FieldEval("table1.score", CatalogUtil.newSimpleDataType(INT4)); // it indicates
    assertCloneEqual(e2);

    Schema schema1 = SchemaBuilder.builder()
        .add("table1.id", INT4)
        .add("table1.score", INT4)
        .build();
    
    BinaryEval expr = new BinaryEval(EvalType.PLUS, e1, e2);
    expr.bind(null, schema1);

    assertCloneEqual(expr);
    VTuple tuple = new VTuple(2);
    tuple.put(0, DatumFactory.createInt4(1)); // put 0th field
    tuple.put(1, DatumFactory.createInt4(99)); // put 1th field

    // the result of evaluation must be 100.
    assertEquals(expr.eval(tuple).asInt4(), 100);
  }

  public static class MockTrueEval extends EvalNode {

    public MockTrueEval() {
      super(EvalType.CONST);
    }

    @Override
    public String getName() {
      return this.getClass().getName();
    }

    @Override
    public void preOrder(EvalNodeVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public void postOrder(EvalNodeVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public Datum eval(Tuple tuple) {
      super.eval(tuple);
      return DatumFactory.createBool(true);
    }

    @Override
    public boolean equals(Object obj) {
      return true;
    }

    @Override
    public Type getValueType() {
      return Type.Bool;
    }

    @Override
    public int childNum() {
      return 0;
    }

    @Override
    public EvalNode getChild(int idx) {
      return null;
    }
  }

  public static class MockFalseExpr extends EvalNode {

    public MockFalseExpr() {
      super(EvalType.CONST);
    }

    @Override
    public Datum eval(Tuple tuple) {
      super.eval(tuple);
      return DatumFactory.createBool(false);
    }

    @Override
    public boolean equals(Object obj) {
      return true;
    }

    @Override
    public String getName() {
      return this.getClass().getName();
    }

    @Override
    public void preOrder(EvalNodeVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public void postOrder(EvalNodeVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public Type getValueType() {
      return Type.Bool;
    }

    @Override
    public int childNum() {
      return 0;
    }

    @Override
    public EvalNode getChild(int idx) {
      return null;
    }
  }

  @Test
  public void testAndTest() {
    MockTrueEval trueExpr = new MockTrueEval();
    MockFalseExpr falseExpr = new MockFalseExpr();

    BinaryEval andExpr = new BinaryEval(EvalType.AND, trueExpr, trueExpr);
    andExpr.bind(null, null);
    assertTrue(andExpr.eval(null).asBool());

    andExpr = new BinaryEval(EvalType.AND, falseExpr, trueExpr);
    andExpr.bind(null, null);
    assertFalse(andExpr.eval(null).asBool());

    andExpr = new BinaryEval(EvalType.AND, trueExpr, falseExpr);
    andExpr.bind(null, null);
    assertFalse(andExpr.eval(null).asBool());

    andExpr = new BinaryEval(EvalType.AND, falseExpr, falseExpr);
    andExpr.bind(null, null);
    assertFalse(andExpr.eval(null).asBool());
  }

  @Test
  public void testOrTest() {
    MockTrueEval trueExpr = new MockTrueEval();
    MockFalseExpr falseExpr = new MockFalseExpr();

    BinaryEval orExpr = new BinaryEval(EvalType.OR, trueExpr, trueExpr);
    orExpr.bind(null, null);
    assertTrue(orExpr.eval(null).asBool());

    orExpr = new BinaryEval(EvalType.OR, falseExpr, trueExpr);
    orExpr.bind(null, null);
    assertTrue(orExpr.eval(null).asBool());

    orExpr = new BinaryEval(EvalType.OR, trueExpr, falseExpr);
    orExpr.bind(null, null);
    assertTrue(orExpr.eval(null).asBool());

    orExpr = new BinaryEval(EvalType.OR, falseExpr, falseExpr);
    orExpr.bind(null, null);
    assertFalse(orExpr.eval(null).asBool());
  }

  @Test
  public final void testCompOperator() {
    ConstEval e1;
    ConstEval e2;
    BinaryEval expr;

    // Constant
    e1 = new ConstEval(DatumFactory.createInt4(9));
    e2 = new ConstEval(DatumFactory.createInt4(34));
    expr = new BinaryEval(EvalType.LTH, e1, e2);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.LEQ, e1, e2);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.LTH, e2, e1);
    assertFalse(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.LEQ, e2, e1);
    assertFalse(expr.bind(null, null).eval(null).asBool());

    expr = new BinaryEval(EvalType.GTH, e2, e1);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.GEQ, e2, e1);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.GTH, e1, e2);
    assertFalse(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.GEQ, e1, e2);
    assertFalse(expr.bind(null, null).eval(null).asBool());

    BinaryEval plus = new BinaryEval(EvalType.PLUS, e1, e2);
    expr = new BinaryEval(EvalType.LTH, e1, plus);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.LEQ, e1, plus);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.LTH, plus, e1);
    assertFalse(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.LEQ, plus, e1);
    assertFalse(expr.bind(null, null).eval(null).asBool());

    expr = new BinaryEval(EvalType.GTH, plus, e1);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.GEQ, plus, e1);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.GTH, e1, plus);
    assertFalse(expr.bind(null, null).eval(null).asBool());
    expr = new BinaryEval(EvalType.GEQ, e1, plus);
    assertFalse(expr.bind(null, null).eval(null).asBool());
  }

  @Test
  public final void testArithmaticsOperator() 
      throws CloneNotSupportedException {
    ConstEval e1;
    ConstEval e2;

    // PLUS
    e1 = new ConstEval(DatumFactory.createInt4(9));
    e2 = new ConstEval(DatumFactory.createInt4(34));
    BinaryEval expr = new BinaryEval(EvalType.PLUS, e1, e2);
    assertEquals(expr.bind(null, null).eval(null).asInt4(), 43);
    assertCloneEqual(expr);
    
    // MINUS
    e1 = new ConstEval(DatumFactory.createInt4(5));
    e2 = new ConstEval(DatumFactory.createInt4(2));
    expr = new BinaryEval(EvalType.MINUS, e1, e2);
    assertEquals(expr.bind(null, null).eval(null).asInt4(), 3);
    assertCloneEqual(expr);
    
    // MULTIPLY
    e1 = new ConstEval(DatumFactory.createInt4(5));
    e2 = new ConstEval(DatumFactory.createInt4(2));
    expr = new BinaryEval(EvalType.MULTIPLY, e1, e2);
    assertEquals(expr.bind(null, null).eval(null).asInt4(), 10);
    assertCloneEqual(expr);
    
    // DIVIDE
    e1 = new ConstEval(DatumFactory.createInt4(10));
    e2 = new ConstEval(DatumFactory.createInt4(5));
    expr = new BinaryEval(EvalType.DIVIDE, e1, e2);
    assertEquals(expr.bind(null, null).eval(null).asInt4(), 2);
    assertCloneEqual(expr);
  }

  @Test
  public final void testGetReturnType() {
    ConstEval e1;
    ConstEval e2;

    // PLUS
    e1 = new ConstEval(DatumFactory.createInt4(9));
    e2 = new ConstEval(DatumFactory.createInt4(34));
    BinaryEval expr = new BinaryEval(EvalType.PLUS, e1, e2);
    assertEquals(Type.Int4, expr.getValueType());

    expr = new BinaryEval(EvalType.LTH, e1, e2);
    assertTrue(expr.bind(null, null).eval(null).asBool());
    assertEquals(Type.Bool, expr.getValueType());

    e1 = new ConstEval(DatumFactory.createFloat8(9.3));
    e2 = new ConstEval(DatumFactory.createFloat8(34.2));
    expr = new BinaryEval(EvalType.PLUS, e1, e2);
    assertEquals(Type.Float8, expr.getValueType());
  }
  
  @Test
  public final void testEquals() throws CloneNotSupportedException {
    ConstEval e1;
    ConstEval e2;

    // PLUS
    e1 = new ConstEval(DatumFactory.createInt4(34));
    e2 = new ConstEval(DatumFactory.createInt4(34));
    assertEquals(e1, e2);
    
    BinaryEval plus1 = new BinaryEval(EvalType.PLUS, e1, e2);
    BinaryEval plus2 = new BinaryEval(EvalType.PLUS, e2, e1);
    assertEquals(plus1, plus2);
    
    ConstEval e3 = new ConstEval(DatumFactory.createInt4(29));
    BinaryEval plus3 = new BinaryEval(EvalType.PLUS, e1, e3);
    assertFalse(plus1.equals(plus3));
    
    // LTH
    ConstEval e4 = new ConstEval(DatumFactory.createInt4(9));
    ConstEval e5 = new ConstEval(DatumFactory.createInt4(34));
    BinaryEval compExpr1 = new BinaryEval(EvalType.LTH, e4, e5);
    assertCloneEqual(compExpr1);
    
    ConstEval e6 = new ConstEval(DatumFactory.createInt4(9));
    ConstEval e7 = new ConstEval(DatumFactory.createInt4(34));
    BinaryEval compExpr2 = new BinaryEval(EvalType.LTH, e6, e7);
    assertCloneEqual(compExpr2);
    
    assertTrue(compExpr1.equals(compExpr2));
  }

  @Test
  public final void testBindCheck() {
    ConstEval e1;
    ConstEval e2;
    BinaryEval binEval;

    // Constant
    e1 = new ConstEval(DatumFactory.createInt4(9));
    e2 = new ConstEval(DatumFactory.createInt4(34));
    binEval = new BinaryEval(EvalType.LTH, e1, e2);
    try {
      binEval.eval(null);
      fail("EvalNode is not binded");
    } catch (IllegalStateException e) {
      assertTrue(binEval.bind(null, null).eval(null).asBool());
    }

    CaseWhenEval caseWhenEval = new CaseWhenEval();
    caseWhenEval.addIfCond(new CaseWhenEval.IfThenEval(binEval, new ConstEval(DatumFactory.createInt4(1))));
    try {
      caseWhenEval.eval(null);
      fail("EvalNode is not binded");
    } catch (IllegalStateException e) {
      assertEquals(caseWhenEval.bind(null, null).eval(null).asInt4(), 1);
    }

    Schema schema = SchemaBuilder.builder().addAll(new Column[]{new Column("test", TajoDataTypes.Type.INT4)}).build();
    Tuple tuple = new VTuple(new Datum[]{DatumFactory.createText("aaa")});
    RegexPredicateEval regexEval = new RegexPredicateEval(false, new FieldEval("test",
        CatalogUtil.newSimpleDataType(TajoDataTypes.Type.INT4)), new ConstEval(DatumFactory.createText("a*")), false);
    try {
      regexEval.eval(null);
      fail("EvalNode is not binded");
    } catch (IllegalStateException e) {
      assertEquals(regexEval.bind(null, schema).eval(tuple).asBool(), true);
    }

    RowConstantEval rowConstantEval = new RowConstantEval(new Datum[]{});
    try {
      rowConstantEval.eval(null);
      fail("EvalNode is not binded");
    } catch (IllegalStateException e) {
      assertEquals(rowConstantEval.bind(null, null).eval(null).isNull(), true);
    }
  }
  
  private void assertCloneEqual(EvalNode eval) throws CloneNotSupportedException {
    EvalNode copy = (EvalNode) eval.clone();
    assertEquals(eval, copy);
    assertFalse(eval == copy);
  }

  @Test
  public final void testHashAndEqual() {
    EvalNode e1 = new BinaryEval(EvalType.EQUAL,
        new FieldEval("default.n1.n_nationkey", TypeConverter.convert(INT4)),
        new FieldEval("default.n2.n_nationkey", TypeConverter.convert(INT4))
    );

    EvalNode e2 = new BinaryEval(EvalType.EQUAL,
        new FieldEval("default.n1.n_name", TypeConverter.convert(INT4)),
        new FieldEval("default.n2.n_name", TypeConverter.convert(INT4))
    );

    EvalNode e3 = new BinaryEval(EvalType.EQUAL,
        new FieldEval("default.n1.n_regionkey", TypeConverter.convert(INT4)),
        new FieldEval("default.n2.n_regionkey", TypeConverter.convert(INT4))
    );

    EvalNode e4 = new BinaryEval(EvalType.EQUAL,
        new FieldEval("default.n1.n_comment", TypeConverter.convert(TEXT)),
        new FieldEval("default.n2.n_comment", TypeConverter.convert(TEXT))
    );

    FieldEval f1 = new FieldEval("default.n1.n_nationkey", TypeConverter.convert(INT4));
    System.out.println(f1.hashCode());
    System.out.println(e1.hashCode());

    System.out.println(">>>>" + e1.hashCode() + " >>>>" + e2.hashCode() + " >>>>" + e3.hashCode() + " >>>> " + e4.hashCode());
    System.out.println(">>>>" + e1.hashCode() + " >>>>" + e2.hashCode() + " >>>>" + e3.hashCode() + " >>>> " + e4.hashCode());
  }
}
