package org.iota.qupla.qupla.context;

import org.iota.qupla.qupla.context.base.QuplaBaseContext;
import org.iota.qupla.qupla.expression.*;
import org.iota.qupla.qupla.expression.base.BaseExpr;
import org.iota.qupla.qupla.expression.constant.ConstExpr;
import org.iota.qupla.qupla.expression.constant.ConstNumber;
import org.iota.qupla.qupla.expression.constant.ConstTerm;
import org.iota.qupla.qupla.expression.constant.ConstTypeName;
import org.iota.qupla.qupla.parser.QuplaModule;
import org.iota.qupla.qupla.statement.*;
import org.iota.qupla.qupla.statement.helper.LutEntry;
import org.iota.qupla.qupla.statement.helper.TritStructDef;
import org.iota.qupla.qupla.statement.helper.TritVectorDef;

/*
    QuplaToYAMLContext generates YAML representation of Qupla module and saves it to file Qupla.yml
    For more info see https://github.com/lunfardo314/quplayaml
 */

public class QuplaToYAMLContext extends QuplaBaseContext {
    private String fileName;
    private QuplaPrintContext printer;

    public QuplaToYAMLContext(String fileName) {
        this.fileName = fileName;
        printer = new QuplaPrintContext();
    }


    private void appendStringAsComment(String str){
        String[] lines = str.split("\n");
        for (String line : lines) {
            append("# " + line);
            newline();
        }
    }

    private void appendExprAsComment(BaseExpr expr){
        appendStringAsComment(expr.toString());
    }

    private String getExpressionTypeTag(BaseExpr expr){
        return expr.getClass().getSimpleName();
    }

    private void evalExpression(BaseExpr expr){
        if (expr instanceof SubExpr){
            expr = ((SubExpr)expr).expr;
        }
        append(getExpressionTypeTag(expr)+":");
        newline();
        indent();
        expr.eval(this);
        undent();
    }

    @Override
    public void eval(final QuplaModule module) {
        fileOpen(fileName);

        append("module: '" + fileName + "'");
        newline();

        for (final ImportStmt imp : module.imports) {
            append("# import ");
            append(imp.name);
            newline();
        }
        newline();

        append("types: ");
        newline();
        indent();
        for (final TypeStmt type : module.types) {
            evalTypeDefinition(type);
        }
        undent();

        append("luts: ");
        newline().indent();
        for (final LutStmt lut : module.luts) {
            evalLutDefinition(lut);
        }
        undent();

        append("functions: ");
        newline();
        for (final FuncStmt func : module.funcs) {
            evalFuncBody(func);
        }

        append("execs: ");
        newline();
        for (final ExecStmt exec : module.execs)
        {
            indent();
            evalExec(exec);
            undent();
        }

        fileClose();
    }

    @Override
    public void evalTypeDefinition(TypeStmt type) {
        appendExprAsComment(type);

        append(type.name + ":");
        newline();

        indent();
        if (type.struct != null) {
            evalTritStruct(type.struct);
        } else {
            evalTritVector(type.vector);
        }
        undent();
    }

    @Override
    public void evalVector(VectorExpr vectorExpr) {
        indent();
        append("value: " + "'" +vectorExpr.name+ "'");
        newline();
        append("trits: " + "'"+vectorExpr.vector.trits()+"'");
        newline();
        // not necessary
        append("trytes: " + "'"+vectorExpr.vector.toTrytes()+"'");
        newline();
        undent();
    }

    private void evalTritVector(final TritVectorDef vector) {
        append("vector: ");
        append(vector.typeExpr.toString().trim());
        newline();
        append("size: '" + vector.size + "'");
        newline();
    }

    private void evalTritStruct(final TritStructDef struct) {
        append("size: '*'");
        newline();
        append("fields: ");
        newline();
        indent();

        for (final BaseExpr field : struct.fields) {
            indent();
            if (field.name != null) {
                append(field.name).append(": ").newline();
            }
            indent();
            evalTritVector((TritVectorDef) field);
            undent();

            undent();
        }
        undent();
    }

    @Override
    public void evalLutDefinition(final LutStmt lut) {
        appendExprAsComment(lut);

        append(lut.name).append(":");
        newline();
        indent();
        append("lutTable:");
        newline();
        indent();
        for (final LutEntry entry : lut.entries) {
            evalLutEntry(entry);
            newline();
        }
        undent();
        undent();
    }

    @Override
    public void evalLutLookup(LutExpr lookup) {
        append("name: ");
        append(lookup.name);
        newline();
        append("args: ");
        newline();
        indent();
        for (final BaseExpr arg : lookup.args){
            append("- ");
            newline();
            indent();
            evalExpression(arg);
            undent();
        }
        undent();
    }

    @Override
    public void evalSlice(SliceExpr slice) {
        append("source: '" + slice.toString() + "'");
        newline();

        append("var: ");
        append(slice.name);
        newline();
        append("offset: " + slice.start);
        newline();
        append("size: " + slice.size);
        newline();

        if (slice.fields.size() > 0){
            append("fields: ");
            newline();
            indent();
            for (final BaseExpr field : slice.fields) {
                append("- ");
                append(field.name);
                newline();
            }
            undent();
        }

        if (slice.sliceStart != null)
        {
            append("start:");
            newline();
            indent();
            evalExpression(slice.sliceStart);
            undent();

            if (slice.sliceSize != null)
            {
                append("end:");
                newline();
                indent();
                evalExpression(slice.sliceSize);
                undent();
            }
        }
    }

    private void evalLutEntry(final LutEntry entry) {
        append("- '");
        for (int i = 0; i < entry.inputs.length(); i++) {
            append(entry.inputs.substring(i, i + 1));
        }
        append(" = ");
        for (int i = 0; i < entry.outputs.length(); i++) {
            append(entry.outputs.substring(i, i + 1));
        }
        append("'");
    }

    @Override
    public void evalFuncBody(final FuncStmt func){
        // printing func definition as comment
        final String oldString = printer.string;
        printer.string = new String(new char[0]);

        printer.evalFuncBody(func);
        final String ret = printer.string;
        printer.string = oldString;
        appendStringAsComment(ret);

        indent();
        append(func.name + ":");
        newline();

        indent();

        evalFuncBodySignature(func);
        evalFuncBodyEnv(func);
        evalFuncBodyState(func);
        evalFuncBodyAssigns(func);
        evalFuncBodyReturn(func);

        undent();
        undent();
    }

    private void evalFuncBodySignature(final FuncStmt func) {
        append("returnType: ");
        newline();
        indent();
        evalExpression(func.returnType);
        undent();

        if (func.params.size() > 0){
            append("params:");
            newline();
            indent();

            for (BaseExpr p : func.params){
                final NameExpr var = (NameExpr) p;

                append("- ");
                newline();
                indent();

                append("argName: " + var.name);
                newline();
                append("size: " + var.size);
                newline();
                append("type: ");
                newline();

                indent();
                evalExpression(var.type);
                undent();

                undent();
            }
            undent();
        }
    }

    private void evalFuncBodyEnv(final FuncStmt func) {
        if (func.envExprs.size() > 0){
            append("env: ");
            newline();
            indent();
            for (final BaseExpr envExpr : func.envExprs){
                append("- name: " + envExpr.name);
                newline();
                indent();
                append("join: " + (envExpr instanceof JoinExpr));
                newline();
                undent();
            }
            undent();
        }
    }

    private void evalFuncBodyState(final FuncStmt func){
        if (func.stateExprs.size() == 0)
            return;
        append("state: ");
        newline();
        indent();
        for (final BaseExpr se : func.stateExprs) {
            StateExpr stateExpr = (StateExpr)se;
            append(stateExpr.name + ": ");
            newline();

            indent();
            append("size: " + stateExpr.stateType.size);
            newline();

            append("type: ");
            append(stateExpr.stateType.name);
            newline();
            undent();
        }
        undent();
    }

    private void evalFuncBodyAssigns(final FuncStmt func){
        if (func.assignExprs.size() == 0)
            return;
        append("assigns: ");
        newline();
        indent();
        for (final BaseExpr ae : func.assignExprs)
        {
            AssignExpr assignExpr = (AssignExpr)ae;

            append(assignExpr.name + ":");
            newline();
            indent();
            evalExpression(assignExpr.expr);
            undent();
        }
        undent();
    }

    private void evalFuncBodyReturn(final FuncStmt func){
        append("return: ");
        newline();
        appendExprAsComment(func.returnExpr);
        indent();
        evalExpression(func.returnExpr);
        undent();
    }

    @Override
    public void evalFuncCall(FuncExpr call) {
        append("source: '" + call.toString() + "'");
        newline();

        append("name: ");
        append(call.name);
        newline();
        append("args:");
        newline();
        for (final BaseExpr arg : call.args)
        {
            append("- ");
            newline();
            indent();
            evalExpression(arg);
            undent();
        }
    }

    @Override
    public void evalMerge(MergeExpr merge) {
        append("source: '" + merge.toString() + "'");
        newline();

        append("lhs: ");
        newline();
        indent();
        evalExpression(merge.lhs);
        undent();

        append("rhs: ");
        newline();
        indent();
        evalExpression(merge.rhs);
        undent();
    }


    @Override
    public void evalConcat(ConcatExpr concat) {
        append("source: '" + concat.toString() + "'");
        newline();

        append("lhs: ");
        newline();
        indent();
        evalExpression(concat.lhs);
        undent();

        append("rhs: ");
        newline();
        indent();
        evalExpression(concat.rhs);
        undent();
    }

    @Override
    public void evalConditional(CondExpr conditional) {
        append("source: '" + conditional.toString() + "'");
        newline();
        append("if: ");
        newline();
        indent();
        evalExpression(conditional.condition);
        undent();

        append("then: ");
        newline();
        indent();
        if (conditional.trueBranch != null){
            evalExpression(conditional.trueBranch);
        } else {
            append("NullExpr:");
            newline();
        }
        undent();

        append("else:");
        newline();
        indent();
        if (conditional.falseBranch != null){
            evalExpression(conditional.falseBranch);
        } else {
            append("NullExpr: ''");
            newline();
        }
        undent();
    }
    @Override
    public void evalFuncSignature(FuncStmt func) {
        append("'evalFuncSignature not implemented: " + func.toString() + "'");
        newline();
    }

    @Override
    public void evalAssign(AssignExpr assign) {
        append("'evalAssign not implemented: " + assign.toString() + "'");
        newline();
    }

    @Override
    public void evalState(StateExpr state) {
        append("'evalState not implemented: " + state.toString() + "'");
        newline();
    }

    @Override
    public void evalType(TypeExpr type) {
        String src = type.toString().replace("\n", " / ");
        append("source: '" + src + "'");
        newline();

        append("type: ");
        newline();
        indent();
        evalExpression(type.type);
        undent();
        append("fieldValues: ");
        newline();

        indent();
        for (final BaseExpr expr : type.fields) {
            FieldExpr f = (FieldExpr)expr;
            append(f.name + ":");
            newline();
            indent();
            evalExpression(f.expr);
            undent();
        }
        undent();
    }

    private void evalConstTypeName(ConstTypeName constTypeName){
        append("typeName: ");
        append(constTypeName.name);
        newline();
        append("size: ");
        append("" + constTypeName.size);
        newline();
        if (constTypeName.typeInfo.struct != null){
            append("fields:");
            newline();
            int offset = 0;
            for (BaseExpr fe : constTypeName.typeInfo.struct.fields){
                TritVectorDef fld = (TritVectorDef) fe;
                indent();
                append(fld.name + ": ");
                newline();

                indent();
                append("size: '" + fld.size + "'");
                newline();
                append("offset: '" + offset + "'");
                newline();

                undent();
                undent();
                offset += fld.size;
            }
        }
//        appendStringAsComment("typeInfo: '" + constTypeName.typeInfo.toString().replaceAll("\n", "") + "'");
//        newline();
    }

    private void evalConstNumber(ConstNumber constNumber){
        append("value: " + constNumber.name);
        newline();
    }

    private void evalConstTerm(ConstTerm constTerm){
        append("operator: ");
        append("'" + constTerm.operator.text + "'");
        newline();

        append("lhs: ");
        newline();
        indent();
        evalExpression(constTerm.lhs);
        undent();

        append("rhs: ");
        newline();
        indent();
        evalExpression(constTerm.rhs);
        undent();
    }

    private void evalConstExpr(ConstExpr constExpr){
        append("operator: ");
        append("'" + constExpr.operator.text + "'");
        newline();

        append("lhs: ");
        newline();
        indent();
        evalExpression(constExpr.lhs);
        undent();

        append("rhs: ");
        newline();
        indent();
        evalExpression(constExpr.rhs);
        undent();
    }

    @Override
    public void evalBaseExpr(final BaseExpr expr) {
        if (expr instanceof ConstTypeName){
            evalConstTypeName((ConstTypeName)expr);
            return;
        }
        if (expr instanceof ConstNumber){
            evalConstNumber((ConstNumber)expr);
            return;
        }
        if (expr instanceof ConstExpr){
            evalConstExpr((ConstExpr)expr);
            return;
        }
        if (expr instanceof ConstTerm){
            evalConstTerm((ConstTerm)expr);
            return;
        }

        append("'evalBaseExpr not implemented: " + expr.toString() + "'");
        newline();
    }

    private void evalExec(final ExecStmt exec)
    {
        String str = exec.toString();
        appendStringAsComment(str);

        append("-");
        newline();
        indent();

        append("source: '" + str + "'");
        newline();

        if (exec.expected != null){
            append("isFloat: " + exec.typeInfo.isFloat);
            newline();

            append("expected: ");
            newline();
            indent();
            evalExpression(exec.expected);
            undent();
        }

        append("expr: ");
        newline();
        indent();
        evalExpression(exec.expr);
        undent();

        undent();
    }
}



