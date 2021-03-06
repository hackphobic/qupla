package org.iota.qupla.dispatcher.entity;

import java.util.Collection;

import org.iota.qupla.abra.AbraModule;
import org.iota.qupla.abra.block.base.AbraBaseBlock;
import org.iota.qupla.abra.context.AbraEvalContext;
import org.iota.qupla.dispatcher.Dispatcher;
import org.iota.qupla.dispatcher.Entity;
import org.iota.qupla.dispatcher.Environment;
import org.iota.qupla.exception.CodeException;
import org.iota.qupla.helper.TritVector;
import org.iota.qupla.qupla.context.QuplaEvalContext;
import org.iota.qupla.qupla.expression.AffectExpr;
import org.iota.qupla.qupla.expression.JoinExpr;
import org.iota.qupla.qupla.expression.base.BaseExpr;
import org.iota.qupla.qupla.parser.QuplaModule;
import org.iota.qupla.qupla.statement.FuncStmt;
import org.iota.qupla.qupla.statement.TypeStmt;

public class FuncEntity extends Entity
{
  public static AbraModule abraModule;

  public AbraBaseBlock block;
  public FuncStmt func;

  public FuncEntity(final FuncStmt func, final int limit, final Dispatcher dispatcher)
  {
    super(limit);

    this.func = func;

    // determine the list of effects that this entity produces
    for (final BaseExpr envExpr : func.envExprs)
    {
      if (envExpr instanceof AffectExpr)
      {
        final AffectExpr affect = (AffectExpr) envExpr;
        final Environment env = dispatcher.getEnvironment(affect.name, func.typeInfo);
        final int delay = affect.delay == null ? 0 : affect.delay.size;
        affect(env, delay);
      }
    }
  }

  public static void addEntities(final Dispatcher dispatcher, final Collection<QuplaModule> modules)
  {
    // add all functions in all modules that have join statements
    // as entities to their corresponding environment so that they
    // will be invoked when their environment receives an effect
    for (final QuplaModule module : modules)
    {
      for (final FuncStmt func : module.funcs)
      {
        for (final BaseExpr envExpr : func.envExprs)
        {
          if (envExpr instanceof JoinExpr)
          {
            final JoinExpr join = (JoinExpr) envExpr;
            final TypeStmt typeInfo = func.params.get(0).typeInfo;
            final Environment environment = dispatcher.getEnvironment(join.name, typeInfo);
            final int limit = join.limit == null ? 1 : join.limit.size;
            final Entity entity = new FuncEntity(func, limit, dispatcher);
            environment.join(entity);
          }
        }
      }
    }
  }

  public TritVector onEffect(final TritVector effect)
  {
    if (abraModule != null)
    {
      if (block == null)
      {
        block = abraModule.branch(func.name);
        if (block == null)
        {
          throw new CodeException("Cannot find block: " + func.name);
        }
      }

      final AbraEvalContext evalContext = new AbraEvalContext();
      return evalContext.evalEntity(this, effect);
    }

    final QuplaEvalContext evalContext = new QuplaEvalContext();
    return evalContext.evalEntity(this, effect);
  }
}
