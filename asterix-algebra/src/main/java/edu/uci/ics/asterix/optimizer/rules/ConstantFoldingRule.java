package edu.uci.ics.asterix.optimizer.rules;

import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.asterix.common.config.GlobalConfig;
import edu.uci.ics.asterix.dataflow.data.common.AqlExpressionTypeComputer;
import edu.uci.ics.asterix.dataflow.data.common.AqlNullableTypeComputer;
import edu.uci.ics.asterix.dataflow.data.nontagged.AqlNullWriterFactory;
import edu.uci.ics.asterix.formats.nontagged.AqlBinaryBooleanInspectorImpl;
import edu.uci.ics.asterix.formats.nontagged.AqlBinaryComparatorFactoryProvider;
import edu.uci.ics.asterix.formats.nontagged.AqlBinaryHashFunctionFactoryProvider;
import edu.uci.ics.asterix.formats.nontagged.AqlBinaryIntegerInspector;
import edu.uci.ics.asterix.formats.nontagged.AqlPrinterFactoryProvider;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.formats.nontagged.AqlTypeTraitProvider;
import edu.uci.ics.asterix.jobgen.AqlLogicalExpressionJobGen;
import edu.uci.ics.asterix.om.base.AString;
import edu.uci.ics.asterix.om.base.IAObject;
import edu.uci.ics.asterix.om.constants.AsterixConstantValue;
import edu.uci.ics.asterix.om.functions.AsterixBuiltinFunctions;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalExpressionTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractLogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AggregateFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ConstantExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ScalarFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.StatefulFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.UnnestingFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.hyracks.algebricks.core.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.hyracks.algebricks.core.algebra.visitors.ILogicalExpressionVisitor;
import edu.uci.ics.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.util.ByteBufferInputStream;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;

public class ConstantFoldingRule implements IAlgebraicRewriteRule {

    private ConstantFoldingVisitor cfv = new ConstantFoldingVisitor();

    private static final IVariableTypeEnvironment _emptyTypeEnv = new IVariableTypeEnvironment() {

        @Override
        public boolean substituteProducedVariable(LogicalVariable v1, LogicalVariable v2) throws AlgebricksException {
            throw new IllegalStateException();
        }

        @Override
        public void setVarType(LogicalVariable var, Object type) {
            throw new IllegalStateException();
        }

        @Override
        public Object getVarType(LogicalVariable var, List<LogicalVariable> nonNullVariables)
                throws AlgebricksException {
            throw new IllegalStateException();
        }

        @Override
        public Object getVarType(LogicalVariable var) throws AlgebricksException {
            throw new IllegalStateException();
        }

        @Override
        public Object getType(ILogicalExpression expr) throws AlgebricksException {
            return AqlExpressionTypeComputer.INSTANCE.getType(expr, null, this);
        }
    };

    private static final JobGenContext _jobGenCtx = new JobGenContext(null, null, null,
            AqlSerializerDeserializerProvider.INSTANCE, AqlBinaryHashFunctionFactoryProvider.INSTANCE,
            AqlBinaryComparatorFactoryProvider.INSTANCE, AqlTypeTraitProvider.INSTANCE,
            AqlBinaryBooleanInspectorImpl.INSTANCE, AqlBinaryIntegerInspector.INSTANCE,
            AqlPrinterFactoryProvider.INSTANCE, AqlNullWriterFactory.INSTANCE, null,
            AqlLogicalExpressionJobGen.INSTANCE, AqlExpressionTypeComputer.INSTANCE, AqlNullableTypeComputer.INSTANCE,
            null, null, null, GlobalConfig.DEFAULT_FRAME_SIZE, null);

    private static final IOperatorSchema[] _emptySchemas = new IOperatorSchema[] {};

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        ILogicalOperator op = opRef.getValue();
        if (context.checkIfInDontApplySet(this, op)) {
            return false;
        }
        // context.addToDontApplySet(this, op);
        return op.acceptExpressionTransform(cfv);
    }

    private class ConstantFoldingVisitor implements ILogicalExpressionVisitor<Pair<Boolean, ILogicalExpression>, Void>,
            ILogicalExpressionReferenceTransform {

        private ArrayBackedValueStorage resStore = new ArrayBackedValueStorage();
        private ByteBufferInputStream bbis = new ByteBufferInputStream();
        private DataInputStream dis = new DataInputStream(bbis);

        @Override
        public boolean transform(Mutable<ILogicalExpression> exprRef) throws AlgebricksException {
            AbstractLogicalExpression expr = (AbstractLogicalExpression) exprRef.getValue();
            Pair<Boolean, ILogicalExpression> p = expr.accept(this, null);
            if (p.first) {
                exprRef.setValue(p.second);
            }
            return p.first;
        }

        @Override
        public Pair<Boolean, ILogicalExpression> visitConstantExpression(ConstantExpression expr, Void arg)
                throws AlgebricksException {
            return new Pair<Boolean, ILogicalExpression>(false, expr);
        }

        @Override
        public Pair<Boolean, ILogicalExpression> visitVariableReferenceExpression(VariableReferenceExpression expr,
                Void arg) throws AlgebricksException {
            return new Pair<Boolean, ILogicalExpression>(false, expr);
        }

        @Override
        public Pair<Boolean, ILogicalExpression> visitScalarFunctionCallExpression(ScalarFunctionCallExpression expr,
                Void arg) throws AlgebricksException {
            boolean changed = changeRec(expr, arg);
            if (!checkArgs(expr)) {
                return new Pair<Boolean, ILogicalExpression>(changed, expr);
            }
            // TODO: currently ARecord is always a closed record
            if (expr.getFunctionIdentifier().equals(AsterixBuiltinFunctions.OPEN_RECORD_CONSTRUCTOR)) {
                return new Pair<Boolean, ILogicalExpression>(false, null);
            }
            if (expr.getFunctionIdentifier().equals(AsterixBuiltinFunctions.FIELD_ACCESS_BY_NAME)) {
                ARecordType rt = (ARecordType) _emptyTypeEnv.getType(expr.getArguments().get(0).getValue());
                String str = ((AString) ((AsterixConstantValue) ((ConstantExpression) expr.getArguments().get(1)
                        .getValue()).getValue()).getObject()).getStringValue();
                int k = rt.findFieldPosition(str);
                if (k >= 0) {
                    // wait for the ByNameToByIndex rule to apply
                    return new Pair<Boolean, ILogicalExpression>(changed, expr);
                }
            }
            IEvaluatorFactory fact = _jobGenCtx.getExpressionJobGen().createEvaluatorFactory(expr, _emptyTypeEnv,
                    _emptySchemas, _jobGenCtx);
            IEvaluator eval = fact.createEvaluator(resStore);
            resStore.reset();
            eval.evaluate(null);
            Object t = _emptyTypeEnv.getType(expr);
            ISerializerDeserializer serde = _jobGenCtx.getSerializerDeserializerProvider().getSerializerDeserializer(t);
            bbis.setByteBuffer(ByteBuffer.wrap(resStore.getBytes(), resStore.getStartIndex(), resStore.getLength()), 0);
            IAObject o;
            try {
                o = (IAObject) serde.deserialize(dis);
            } catch (HyracksDataException e) {
                throw new AlgebricksException(e);
            }
            return new Pair<Boolean, ILogicalExpression>(true, new ConstantExpression(new AsterixConstantValue(o)));
        }

        @Override
        public Pair<Boolean, ILogicalExpression> visitAggregateFunctionCallExpression(
                AggregateFunctionCallExpression expr, Void arg) throws AlgebricksException {
            boolean changed = changeRec(expr, arg);
            return new Pair<Boolean, ILogicalExpression>(changed, expr);
        }

        @Override
        public Pair<Boolean, ILogicalExpression> visitStatefulFunctionCallExpression(
                StatefulFunctionCallExpression expr, Void arg) throws AlgebricksException {
            boolean changed = changeRec(expr, arg);
            return new Pair<Boolean, ILogicalExpression>(changed, expr);
        }

        @Override
        public Pair<Boolean, ILogicalExpression> visitUnnestingFunctionCallExpression(
                UnnestingFunctionCallExpression expr, Void arg) throws AlgebricksException {
            boolean changed = changeRec(expr, arg);
            return new Pair<Boolean, ILogicalExpression>(changed, expr);
        }

        private boolean changeRec(AbstractFunctionCallExpression expr, Void arg) throws AlgebricksException {
            boolean changed = false;
            for (Mutable<ILogicalExpression> r : expr.getArguments()) {
                Pair<Boolean, ILogicalExpression> p2 = r.getValue().accept(this, arg);
                if (p2.first) {
                    r.setValue(p2.second);
                    changed = true;
                }
            }
            return changed;
        }

        private boolean checkArgs(AbstractFunctionCallExpression expr) throws AlgebricksException {
            for (Mutable<ILogicalExpression> r : expr.getArguments()) {
                if (r.getValue().getExpressionTag() != LogicalExpressionTag.CONSTANT) {
                    return false;
                }
            }
            return true;
        }
    }
}
