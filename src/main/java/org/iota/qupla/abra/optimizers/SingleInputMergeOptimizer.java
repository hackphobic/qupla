package org.iota.qupla.abra.optimizers;

import org.iota.qupla.abra.AbraModule;
import org.iota.qupla.abra.block.AbraBlockBranch;
import org.iota.qupla.abra.block.base.AbraBaseBlock;
import org.iota.qupla.abra.block.site.AbraSiteKnot;
import org.iota.qupla.abra.optimizers.base.BaseOptimizer;

public class SingleInputMergeOptimizer extends BaseOptimizer
{
  public SingleInputMergeOptimizer(final AbraModule module, final AbraBlockBranch branch)
  {
    super(module, branch);
  }

  @Override
  protected void processKnot(final AbraSiteKnot knot)
  {
    if (knot.block.specialType == AbraBaseBlock.TYPE_MERGE && knot.inputs.size() == 1)
    {
      // this leaves <knot> unreferenced
      replaceSite(knot, knot.inputs.get(0));
    }
  }
}
