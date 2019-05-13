package org.iota.qupla.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.iota.qupla.exception.CodeException;

public class Verilog
{
  public static final int B2_BITS_PER_TRIT = 2;
  private static final String[] B2_ENCODING = {
      "00",
      "10",
      "11",
      "01"
  };
  public static final int B3_BITS_PER_TRIT = 3;
  private static final String[] B3_ENCODING = {
      "000",
      "100",
      "010",
      "001"
  };
  private static final int ENC_NEG = 1;
  private static final int ENC_NULL = 0;
  private static final int ENC_POS = 3;
  private static final int ENC_ZERO = 2;
  private static int bitsPerTrit = B2_BITS_PER_TRIT;
  private static String[] encoding = B2_ENCODING;
  public final ArrayList<Integer> addedFuncs = new ArrayList<>();
  public final HashSet<Integer> mergeFuncs = new HashSet<>();
  public final String prefix = "merge__";

  public static void bitEncoding(final int bits)
  {
    switch (bits)
    {
    case B2_BITS_PER_TRIT:
      bitsPerTrit = B2_BITS_PER_TRIT;
      encoding = B2_ENCODING;
      break;

    case B3_BITS_PER_TRIT:
      bitsPerTrit = B3_BITS_PER_TRIT;
      encoding = B3_ENCODING;
      break;
    }
  }

  public String file(final String name)
  {
    final String mergeFile = "Verilog/b" + bitsPerTrit + "/" + name + ".vl";
    try
    {
      return new String(Files.readAllBytes(Paths.get(mergeFile)));
    }
    catch (final IOException e)
    {
      return null;
    }
  }

  private void generateMergeFunc(final BaseContext context, final int[] sizes)
  {
    int totalSize = 0;
    for (final int size : sizes)
    {
      totalSize += size;
    }

    mergeFuncs.remove(totalSize);
    addedFuncs.add(totalSize);

    final String funcName = prefix + totalSize;
    context.newline().append("function ").append(size(totalSize)).append(" ").append(funcName).append("(").newline().indent();
    context.append("  input ").append(size(totalSize)).append(" input1").newline();
    context.append(", input ").append(size(totalSize)).append(" input2").newline();
    context.append(");").newline();

    for (int i = 0; i < sizes.length; i++)
    {
      context.append("reg ").append(size(sizes[i])).append(" p" + i + ";").newline();
    }

    context.append("begin").newline().indent();

    int offset = totalSize;
    for (int i = 0; i < sizes.length; i++)
    {
      final int length = sizes[i];
      context.append("p" + i + " = " + prefix + length);
      final String slice = range(offset, length);
      context.append("(input1" + slice + ", input2" + slice + ");");
      context.newline();
      offset -= length;
    }

    context.append(funcName).append(" = { ");
    boolean first = true;
    for (int i = 0; i < sizes.length; i++)
    {
      context.append(first ? "" : ", ").append("p" + i);
      first = false;
    }

    context.append(" };").newline().undent();
    context.append("end").newline().undent();
    context.append("endfunction").newline();
  }

  public void generateMergeFuncs(final BaseContext context)
  {
    // we have the lut func for size 1
    generateMergeLut(context);
    mergeFuncs.remove(1);
    addedFuncs.add(1);

    int largest = 1;
    for (final Integer value : mergeFuncs)
    {
      if (largest < value)
      {
        largest = value;
      }
    }

    // add all needed powers of 3
    for (int power = 3; power <= largest; power *= 3)
    {
      int size = power / 3;
      generateMergeFunc(context, new int[] {
          size,
          size,
          size
      });
    }

    final int sizes[] = new int[100];

    // go through the remaining ones in sorted order
    final ArrayList<Integer> remaining = new ArrayList<>(mergeFuncs);
    Collections.sort(remaining);
    for (final Integer size : remaining)
    {
      Collections.sort(addedFuncs);

      // compose the next from from existing ones by gathering
      // as many existing ones needed to get to the requires size
      int totalSizes = 0;
      int remain = size;
      while (remain > 0)
      {
        for (int i = addedFuncs.size() - 1; i >= 0; i--)
        {
          int next = addedFuncs.get(i);
          if (next <= remain)
          {
            sizes[totalSizes++] = next;
            remain -= next;
            break;
          }
        }
      }

      generateMergeFunc(context, Arrays.copyOf(sizes, totalSizes));
    }
  }

  private void generateMergeLut(final BaseContext context)
  {
    final String code = file("merge");
    if (code == null)
    {
      throw new CodeException("Cannot open merge code");
    }

    context.newline().append(code);
  }

  public String range(final int from, final int trits)
  {
    final int start = from * bitsPerTrit - 1;
    final int end = start - trits * bitsPerTrit + 1;
    return "[" + start + ":" + end + "]";
  }

  public String size(final int trits)
  {
    return "[" + (trits * bitsPerTrit - 1) + ":0]";
  }

  public String vector(final String trits)
  {
    StringBuilder result = new StringBuilder();
    final int size = trits.length() * bitsPerTrit;
    result.append(size).append("'b");
    for (int i = 0; i < trits.length(); i++)
    {
      switch (trits.charAt(i))
      {
      case '@':
        result.append(encoding[ENC_NULL]);
        break;
      case '-':
        result.append(encoding[ENC_NEG]);
        break;
      case '0':
        result.append(encoding[ENC_ZERO]);
        break;
      case '1':
        result.append(encoding[ENC_POS]);
        break;
      }
    }

    return result.toString();
  }
}
