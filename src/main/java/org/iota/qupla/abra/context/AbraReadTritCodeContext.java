package org.iota.qupla.abra.context;

import java.util.ArrayList;

import org.iota.qupla.abra.AbraModule;
import org.iota.qupla.abra.block.AbraBlockBranch;
import org.iota.qupla.abra.block.AbraBlockImport;
import org.iota.qupla.abra.block.AbraBlockLut;
import org.iota.qupla.abra.block.base.AbraBaseBlock;
import org.iota.qupla.abra.block.site.AbraSiteKnot;
import org.iota.qupla.abra.block.site.AbraSiteLatch;
import org.iota.qupla.abra.block.site.AbraSiteParam;
import org.iota.qupla.abra.block.site.base.AbraBaseSite;
import org.iota.qupla.abra.context.base.AbraTritCodeBaseContext;
import org.iota.qupla.helper.TritConverter;
import org.iota.qupla.helper.TritVector;

public class AbraReadTritCodeContext extends AbraTritCodeBaseContext
{
  private ArrayList<AbraBaseBlock> blocks = new ArrayList<>();
  private final ArrayList<AbraBaseSite> branchSites = new ArrayList<>();

  @Override
  public void eval(final AbraModule module)
  {
    final int version = getInt();
    check(module.version == version, "Module version should be " + module.version);

    blocks.addAll(module.luts);

    final int luts = getInt();
    for (int i = 0; i < luts; i++)
    {
      final AbraBlockLut lut = new AbraBlockLut();
      module.luts.add(lut);
      blocks.add(lut);
    }

    final int branches = getInt();
    for (int i = 0; i < branches; i++)
    {
      final AbraBlockBranch branch = new AbraBlockBranch();
      module.branches.add(branch);
      blocks.add(branch);
    }

    final int imports = getInt();
    for (int i = 0; i < imports; i++)
    {
      final AbraBlockImport imp = new AbraBlockImport();
      module.imports.add(imp);
      blocks.add(imp);
    }

    module.numberBlocks();

    evalBlocks(module.luts);
    evalBlocks(module.branches);
    evalBlocks(module.imports);
  }

  @Override
  public void evalBranch(final AbraBlockBranch branch)
  {
    final AbraReadTritCodeContext branchTritCode = new AbraReadTritCodeContext();
    branchTritCode.buffer = getTrits(getInt()).toCharArray();
    branchTritCode.blocks = blocks;
    branchTritCode.evalBranchBuffer(branch);
  }

  private void evalBranchBuffer(final AbraBlockBranch branch)
  {
    // zero inputs cannot occur, so we can use that as a special flag
    // to indicate that every input is a single trit input
    // the actual amount of inputs follows immediately
    final int inputs = getInt();
    final boolean singleInputTrits = inputs == 0;
    final int inputSites = singleInputTrits ? getInt() : inputs;

    final int latchSites = getInt();
    final int bodySites = getInt();

    // zero outputs cannot occur, so we can use that as a special flag
    // to indicate that they are a concatenation of the final sites
    // in the body sites list, which is frequently the case anyway
    // the actual amount of outputs follows immediately
    final int outputs = getInt();
    final boolean lastOutputSites = outputs == 0;
    final int outputSites = lastOutputSites ? getInt() : outputs;

    int offset = 0;
    for (int i = 0; i < inputSites; i++)
    {
      final AbraSiteParam input = new AbraSiteParam();
      input.size = singleInputTrits ? 1 : getInt();
      input.offset = offset;
      offset += input.size;
      branch.inputs.add(input);
      branchSites.add(input);
    }

    for (int i = 0; i < latchSites; i++)
    {
      final AbraSiteLatch latch = new AbraSiteLatch();
      branch.latches.add(latch);
      latch.eval(this);
      branchSites.add(latch);
    }

    for (int i = 0; i < bodySites; i++)
    {
      final AbraSiteKnot site = new AbraSiteKnot();
      branch.sites.add(site);
      site.eval(this);
      branchSites.add(site);
    }

    branch.numberSites();

    final int totalSites = branch.totalSites();
    offset = totalSites - outputSites;
    for (int i = 0; i < outputSites; i++)
    {
      final int outputIndex = lastOutputSites ? offset + i : getIndex(totalSites);
      final AbraBaseSite output = branchSites.get(outputIndex);
      branch.outputs.add(output);
      output.references++;
      branch.size += output.size;
    }

    // for each latch find out which site output it should be updated with
    for (int i = 0; i < latchSites; i++)
    {
      final AbraSiteLatch latch = branch.latches.get(i);
      final int latchIndex = getIndex(totalSites);
      if (latchIndex != 0)
      {
        latch.latchSite = branchSites.get(latchIndex);
        latch.latchSite.references++;
      }
    }

    branch.name = "branch" + branch.index;
  }

  @Override
  public void evalImport(final AbraBlockImport imp)
  {
    //    putTrits(imp.hash);
    //    putInt(imp.blocks.size());
    //    for (final AbraBaseBlock block : imp.blocks)
    //    {
    //      putInt(block.index);
    //    }
  }

  @Override
  public void evalKnot(final AbraSiteKnot knot)
  {
    final int blockNr = getInt();
    check(blockNr < blocks.size(), "Invalid block number");
    knot.block = blocks.get(blockNr);

    final int inputSites = getInt();
    for (int i = 0; i < inputSites; i++)
    {
      final int inputIndex = getIndex(branchSites.size());
      final AbraBaseSite input = branchSites.get(inputIndex);
      knot.inputs.add(input);
      input.references++;
    }

    if (blockNr == AbraBaseBlock.TYPE_SLICE)
    {
      final int start = getInt();
      knot.size = getInt();
      knot.slice(start);
      return;
    }

    if (blockNr == AbraBaseBlock.TYPE_CONSTANT)
    {
      knot.size = getInt();
      if (knot.size == 0)
      {
        // all zero trits, get the actual length
        knot.size = getInt();
        final TritVector zeroes = new TritVector(knot.size, '0');
        knot.vector(zeroes);
        return;
      }

      if (knot.size > 5)
      {
        // trailing zeroes were trimmed, get remaining trits and reconstruct by padding zeroes
        final int len = getInt();
        final TritVector remain = new TritVector(getTrits(len));
        final TritVector zeroes = new TritVector(knot.size - len, '0');
        final TritVector vector = TritVector.concat(remain, zeroes);
        knot.vector(vector);
        return;
      }

      // just get the trits
      final TritVector vector = new TritVector(getTrits(knot.size));
      knot.vector(vector);
    }
  }

  @Override
  public void evalLatch(final AbraSiteLatch latch)
  {
    latch.size = getInt();
  }

  @Override
  public void evalLut(final AbraBlockLut lut)
  {
    if (lut.index < AbraModule.SPECIAL_LUTS)
    {
      return;
    }

    // convert 35 trits to 54-bit long value, which encodes 27 bct trits
    final String trits = getTrits(35);
    long value = TritConverter.toLong(trits);
    final char[] buffer = new char[27];
    for (int i = 0; i < 27; i++)
    {
      buffer[i] = "@01-".charAt((int) value & 0x03);
      value >>= 2;
    }

    lut.lookup = new String(buffer);
    lut.name = AbraBlockLut.unnamed(lut.lookup);
  }

  @Override
  public void evalParam(final AbraSiteParam param)
  {
  }

  @Override
  public String toString()
  {
    return toStringRead();
  }
}
