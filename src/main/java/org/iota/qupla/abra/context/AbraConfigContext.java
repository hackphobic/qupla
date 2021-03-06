package org.iota.qupla.abra.context;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;

import org.iota.qupla.Qupla;
import org.iota.qupla.abra.AbraModule;
import org.iota.qupla.abra.block.AbraBlockBranch;
import org.iota.qupla.abra.block.AbraBlockImport;
import org.iota.qupla.abra.block.AbraBlockLut;
import org.iota.qupla.abra.block.AbraBlockSpecial;
import org.iota.qupla.abra.block.site.AbraSiteKnot;
import org.iota.qupla.abra.block.site.AbraSiteLatch;
import org.iota.qupla.abra.block.site.AbraSiteParam;
import org.iota.qupla.abra.block.site.base.AbraBaseSite;
import org.iota.qupla.abra.context.base.AbraBaseContext;
import org.iota.qupla.exception.CodeException;
import org.iota.qupla.helper.TritVector;

public class AbraConfigContext extends AbraBaseContext
{
  private AbraBlockBranch funcBranch;
  public String funcName = "add_9";
  private int lutZeroIndex;
  private BufferedOutputStream outputStream;

  public void eval(final AbraModule module)
  {
    module.blockNr = 0;
    module.blocks.clear();

    for (final AbraBlockBranch branch : module.branches)
    {
      if (branch.name.equals(funcName))
      {
        funcBranch = branch;
        break;
      }
    }

    if (funcBranch == null)
    {
      throw new CodeException("Cannot find branch: " + funcName);
    }

    // always include standard luts for consts and nullifies
    lutZeroIndex = module.luts.get(0).index;
    for (int i = 0; i < 5; i++)
    {
      module.blocks.add(module.luts.get(i));
    }

    // find all LUTs this branch uses
    for (final AbraSiteKnot knot : funcBranch.sites)
    {
      if (knot.block instanceof AbraBlockLut && !module.blocks.contains(knot.block))
      {
        module.blocks.add(knot.block);
      }
    }

    module.blockNr = module.specials.size();
    module.numberBlocks(module.blocks);

    try
    {
      outputStream = new BufferedOutputStream(new FileOutputStream(funcName + ".qbc"));

      write(module.blocks.size());
      evalBlocks(module.blocks);
      write(1);
      evalBranch(funcBranch);

      outputStream.close();

      if (funcBranch.sites.size() > 512)
      {
        Qupla.log("WARNING: Requested function is too large to fit on the FPGA!!!");
      }
    }
    catch (final Exception ex)
    {
      throw new CodeException("Cannot write to " + funcName + ".qbc");
    }

    module.numberBlocks();
  }

  @Override
  public void evalBranch(final AbraBlockBranch branch)
  {
    branch.numberSites();
    write(branch.inputs.size());

    write(branch.sites.size());
    for (final AbraSiteKnot site : branch.sites)
    {
      site.eval(this);
    }

    write(branch.outputs.size());
    for (final AbraBaseSite output : branch.outputs)
    {
      write(output.index);
    }
  }

  @Override
  public void evalImport(final AbraBlockImport imp)
  {
    throw new CodeException("Import for config?");
  }

  @Override
  public void evalKnot(final AbraSiteKnot knot)
  {
    if (knot.block.index == AbraBlockSpecial.TYPE_CONST)
    {
      // route constant trit to constant LUT with site 0 as input
      final AbraBlockSpecial block = (AbraBlockSpecial) knot.block;
      final byte trit = block.constantValue.trit(0);
      write(AbraModule.constLutIndex(trit));
      write(1);
      write(0);
      return;
    }

    write(knot.block.index != 0 ? knot.block.index - lutZeroIndex : 0xffff);
    write(knot.inputs.size());
    for (final AbraBaseSite input : knot.inputs)
    {
      write(input.index);
    }
  }

  @Override
  public void evalLatch(final AbraSiteLatch latch)
  {
    throw new CodeException("Latch for config?");
  }

  @Override
  public void evalLut(final AbraBlockLut lut)
  {
    for (int group = 0; group < 32; group += 8)
    {
      int bytes = 0;
      for (int i = 7; i >= 0; i--)
      {
        bytes <<= 2;
        int index = group + i;
        if (index < 27)
        {
          bytes += TritVector.tritToBits(lut.lookup(index));
        }
      }

      write(bytes);
    }
  }

  @Override
  public void evalParam(final AbraSiteParam param)
  {
    throw new CodeException("Param for config?");
  }

  @Override
  public void evalSpecial(final AbraBlockSpecial block)
  {
  }

  private void write(final int value)
  {
    try
    {
      outputStream.write(new byte[] {
          (byte) value,
          (byte) (value >> 8)
      });
    }
    catch (Exception ex)
    {
      throw new CodeException("Config write failed");
    }
  }
}
