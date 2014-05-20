package org.apache.tajo.engine.function.window;

import org.apache.tajo.catalog.Column;
import org.apache.tajo.catalog.proto.CatalogProtos;
import org.apache.tajo.common.TajoDataTypes;
import org.apache.tajo.datum.Datum;
import org.apache.tajo.datum.DatumFactory;
import org.apache.tajo.datum.Int8Datum;
import org.apache.tajo.datum.NullDatum;
import org.apache.tajo.engine.function.FunctionContext;
import org.apache.tajo.engine.function.WindowAggFunction;
import org.apache.tajo.engine.function.annotation.Description;
import org.apache.tajo.engine.function.annotation.ParamTypes;
import org.apache.tajo.storage.Tuple;

@Description(
    functionName = "rank",
    description = " The number of rows for "
        + "which the supplied expressions are unique and non-NULL.",
    example = "> SELECT rank() OVER (ORDER BY x) FROM ...;",
    returnType = TajoDataTypes.Type.INT8,
    paramTypes = {@ParamTypes(paramTypes = {})}
)
public final class Rank extends WindowAggFunction {

  public Rank() {
    super(new Column[] {
        new Column("expr", TajoDataTypes.Type.ANY)
    });
  }

  public static boolean checkEquality(RankContext context, Tuple params) {
    for (int i = 0; i < context.latest.length; i++) {
      if (!context.latest[i].equalsTo(params.get(i)).isTrue()) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void eval(FunctionContext context, Tuple params) {
    RankContext ctx = (RankContext) context;

    if ((ctx.latest == null || checkEquality(ctx, params))) {
      ctx.latest = params.getValues();
      ctx.count++;
    }
  }

  @Override
  public Int8Datum terminate(FunctionContext ctx) {
    return DatumFactory.createInt8(((RankContext) ctx).count);
  }

  @Override
  public FunctionContext newContext() {
    return new RankContext();
  }

  private class RankContext implements FunctionContext {
    long count = 0;
    Datum [] latest = null;
  }

  @Override
  public CatalogProtos.FunctionType getFunctionType() {
    return CatalogProtos.FunctionType.WINDOW;
  }
}
