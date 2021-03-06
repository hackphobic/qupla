package org.iota.qupla.abra.optimizers;

import java.util.ArrayList;

import org.iota.qupla.abra.AbraModule;
import org.iota.qupla.abra.block.AbraBlockBranch;
import org.iota.qupla.abra.block.AbraBlockLut;
import org.iota.qupla.abra.block.AbraBlockSpecial;
import org.iota.qupla.abra.block.site.AbraSiteKnot;
import org.iota.qupla.abra.block.site.AbraSiteParam;
import org.iota.qupla.abra.block.site.base.AbraBaseSite;
import org.iota.qupla.abra.optimizers.base.BaseOptimizer;
import org.iota.qupla.helper.TritVector;

// first slice all input vectors into trit-sized slices
// replace all site inputs with those trits
// replace all call knots with the contents of the branch they call
// replace their inputs directly with the actual input parameters
// replace all site inputs with the branch output trits
//TODO how to deal with latches?

public class FpgaConfigurationOptimizer extends BaseOptimizer
{
  private final ArrayList<AbraBlockSpecial> consts = new ArrayList<>();
  private final ArrayList<AbraSiteParam> inputs = new ArrayList<>();
  private final AbraBlockSpecial merger;
  private final AbraBlockLut nullFalse;
  private final AbraBlockLut nullTrue;
  private final ArrayList<AbraBaseSite> outputs = new ArrayList<>();
  private final ArrayList<ArrayList<AbraBaseSite>> siteTrits = new ArrayList<>();
  private final ArrayList<AbraSiteKnot> sites = new ArrayList<>();

  public FpgaConfigurationOptimizer(final AbraModule module, final AbraBlockBranch branch)
  {
    super(module, branch);

    merger = module.specials.get(AbraBlockSpecial.TYPE_MERGE);

    consts.add(new AbraBlockSpecial(AbraBlockSpecial.TYPE_CONST, 1, new TritVector(1, TritVector.TRIT_ZERO)));
    consts.add(new AbraBlockSpecial(AbraBlockSpecial.TYPE_CONST, 1, new TritVector(1, TritVector.TRIT_ONE)));
    consts.add(new AbraBlockSpecial(AbraBlockSpecial.TYPE_CONST, 1, new TritVector(1, TritVector.TRIT_MIN)));

    nullTrue = module.luts.get(3);
    nullFalse = module.luts.get(4);
  }

  private void addInputs(final AbraSiteKnot from, final AbraSiteKnot to)
  {
    for (final AbraBaseSite input : from.inputs)
    {
      final ArrayList<AbraBaseSite> newInputs = siteTrits.get(input.index);
      to.inputs.addAll(newInputs);
      for (final AbraBaseSite newInput : newInputs)
      {
        newInput.references++;
      }
    }
  }

  private void processInput(final AbraSiteParam input)
  {
    final ArrayList<AbraBaseSite> trits = new ArrayList<>(branch.inputs.size());
    for (int i = 0; i < input.size; i++)
    {
      final AbraSiteParam param = new AbraSiteParam();
      param.index = sites.size();
      param.offset = input.offset + i;
      param.size = 1;
      if (input.name != null)
      {
        param.name = input.name + (input.size != 1 ? "_" + i : "");
      }

      inputs.add(param);
      trits.add(param);
    }

    siteTrits.add(trits);
  }

  @Override
  protected void processKnotBranch(final AbraSiteKnot knot, final AbraBlockBranch block)
  {
    if (!block.analyzed)
    {
      new FpgaConfigurationOptimizer(module, block).run();
    }

    final ArrayList<AbraBaseSite> params = new ArrayList<>();
    for (final AbraBaseSite input : knot.inputs)
    {
      final ArrayList<AbraBaseSite> trits = siteTrits.get(input.index);
      params.addAll(trits);
    }

    final AbraBlockBranch call = block.clone();

    for (final AbraSiteKnot site : call.sites)
    {
      for (int i = 0; i < site.inputs.size(); i++)
      {
        final AbraBaseSite in = site.inputs.get(i);
        if (in.index < call.inputs.size())
        {
          final AbraBaseSite param = params.get(in.index);
          in.references--;
          site.inputs.set(i, param);
          param.references++;
        }
      }
    }

    sites.addAll(call.sites);

    final ArrayList<AbraBaseSite> trits = new ArrayList<>();
    for (final AbraBaseSite output : call.outputs)
    {
      output.references--;
      if (output.index >= call.inputs.size())
      {
        trits.add(output);
        continue;
      }

      final AbraBaseSite param = params.get(output.index);
      trits.add(param);
    }

    siteTrits.add(trits);
  }

  private void processKnotConstant(final AbraSiteKnot knot, final AbraBlockSpecial block)
  {
    final ArrayList<AbraBaseSite> trits = new ArrayList<>();
    final byte[] vector = block.constantValue.trits();
    for (final byte trit : vector)
    {
      final int lutId = AbraModule.constLutIndex(trit);
      final AbraSiteKnot lut = new AbraSiteKnot();
      lut.size = 1;
      lut.block = consts.get(lutId);
      trits.add(lut);
      sites.add(lut);
    }

    siteTrits.add(trits);
  }

  @Override
  protected void processKnotLut(final AbraSiteKnot knot, final AbraBlockLut block)
  {
    final AbraSiteKnot lut = new AbraSiteKnot();
    lut.size = 1;
    lut.block = knot.block;
    lut.name = knot.name;
    addInputs(knot, lut);

    final ArrayList<AbraBaseSite> trits = new ArrayList<>();
    trits.add(lut);
    sites.add(lut);
    siteTrits.add(trits);
  }

  private void processKnotMerge(final AbraSiteKnot knot)
  {
    final AbraBaseSite input1 = knot.inputs.get(0);
    final AbraBaseSite input2 = knot.inputs.get(1);
    final ArrayList<AbraBaseSite> trits1 = siteTrits.get(input1.index);
    final ArrayList<AbraBaseSite> trits2 = siteTrits.get(input2.index);
    final ArrayList<AbraBaseSite> trits = new ArrayList<>();
    for (int i = 0; i < trits1.size(); i++)
    {
      final AbraBaseSite trit1 = trits1.get(i);
      final AbraBaseSite trit2 = trits2.get(i);
      final AbraSiteKnot merge = new AbraSiteKnot();
      merge.size = 1;
      merge.block = merger;
      merge.inputs.add(trit1);
      trit1.references++;
      merge.inputs.add(trit2);
      trit2.references++;

      trits.add(merge);
      sites.add(merge);
    }

    siteTrits.add(trits);
  }

  private void processKnotNullify(final AbraSiteKnot knot)
  {
    final AbraBaseSite flag = knot.inputs.get(0);
    final AbraBaseSite value = knot.inputs.get(1);
    final ArrayList<AbraBaseSite> flagTrits = siteTrits.get(flag.index);
    final ArrayList<AbraBaseSite> valueTrits = siteTrits.get(value.index);
    final AbraBaseSite flagTrit = flagTrits.get(0);
    final ArrayList<AbraBaseSite> trits = new ArrayList<>();
    final AbraBlockLut nullifier = knot.block.index == AbraBlockSpecial.TYPE_NULLIFY_TRUE ? nullTrue : nullFalse;
    for (int i = 0; i < valueTrits.size(); i++)
    {
      final AbraBaseSite valueTrit = valueTrits.get(i);
      final AbraSiteKnot nullify = new AbraSiteKnot();
      nullify.size = 1;
      nullify.block = nullifier;
      nullify.inputs.add(flagTrit);
      flagTrit.references++;
      nullify.inputs.add(valueTrit);
      valueTrit.references++;

      trits.add(nullify);
      sites.add(nullify);
    }
    siteTrits.add(trits);
  }

  private void processKnotSlice(final AbraSiteKnot knot, final AbraBlockSpecial block)
  {
    final ArrayList<AbraBaseSite> params = new ArrayList<>();
    for (final AbraBaseSite input : knot.inputs)
    {
      final ArrayList<AbraBaseSite> trits = siteTrits.get(input.index);
      params.addAll(trits);
    }

    final ArrayList<AbraBaseSite> trits = new ArrayList<>();
    for (int i = 0; i < block.size; i++)
    {
      final AbraBaseSite param = params.get(block.offset + i);
      trits.add(param);
    }

    siteTrits.add(trits);
  }

  @Override
  protected void processKnotSpecial(final AbraSiteKnot knot, final AbraBlockSpecial block)
  {
    switch (block.index)
    {
    case AbraBlockSpecial.TYPE_MERGE:
      processKnotMerge(knot);
      return;

    case AbraBlockSpecial.TYPE_NULLIFY_FALSE:
    case AbraBlockSpecial.TYPE_NULLIFY_TRUE:
      processKnotNullify(knot);
      return;

    case AbraBlockSpecial.TYPE_CONST:
      processKnotConstant(knot, block);
      return;

    case AbraBlockSpecial.TYPE_CONCAT:
    case AbraBlockSpecial.TYPE_SLICE:
      processKnotSlice(knot, block);
      return;
    }
  }

  @Override
  public void run()
  {
    branch.analyzed = true;

    branch.numberSites();

    for (index = 0; index < branch.inputs.size(); index++)
    {
      processInput(branch.inputs.get(index));
    }

    for (index = 0; index < branch.sites.size(); index++)
    {
      final AbraSiteKnot knot = branch.sites.get(index);
      processKnot(knot);
      if (knot.stmt != null)
      {
        //TODO this will sometimes overwrite
        siteTrits.get(knot.index).get(0).stmt = knot.stmt;
      }
    }

    for (index = 0; index < branch.outputs.size(); index++)
    {
      final AbraBaseSite output = branch.outputs.get(index);
      final ArrayList<AbraBaseSite> trits = siteTrits.get(output.index);
      for (final AbraBaseSite trit : trits)
      {
        outputs.add(trit);
        trit.references++;
      }
    }

    branch.inputs.clear();
    branch.inputs.addAll(inputs);
    branch.sites.clear();
    branch.sites.addAll(sites);
    branch.outputs.clear();
    branch.outputs.addAll(outputs);
    branch.numberSites();
  }
}
