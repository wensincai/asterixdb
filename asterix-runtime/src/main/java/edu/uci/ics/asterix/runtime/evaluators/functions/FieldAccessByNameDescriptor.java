package edu.uci.ics.asterix.runtime.evaluators.functions;

import java.io.DataOutput;
import java.io.IOException;

import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ARecordSerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ANull;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.EnumDeserializer;
import edu.uci.ics.asterix.om.util.NonTaggedFormatUtil;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class FieldAccessByNameDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    private static final FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS,
            "field-access-by-name", 2, true);

    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

    @Override
    public IEvaluatorFactory createEvaluatorFactory(IEvaluatorFactory[] args) {
        return new FieldAccessByNameEvalFactory(args[0], args[1]);
    }

    private static class FieldAccessByNameEvalFactory implements IEvaluatorFactory {

        private static final long serialVersionUID = 1L;

        private IEvaluatorFactory recordEvalFactory;
        private IEvaluatorFactory fldNameEvalFactory;

        private final static byte SER_NULL_TYPE_TAG = ATypeTag.NULL.serialize();
        private final static byte SER_RECORD_TYPE_TAG = ATypeTag.RECORD.serialize();

        public FieldAccessByNameEvalFactory(IEvaluatorFactory recordEvalFactory, IEvaluatorFactory fldNameEvalFactory) {
            this.recordEvalFactory = recordEvalFactory;
            this.fldNameEvalFactory = fldNameEvalFactory;
        }

        @Override
        public IEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {
            return new IEvaluator() {

                private DataOutput out = output.getDataOutput();

                private ArrayBackedValueStorage outInput0 = new ArrayBackedValueStorage();
                private ArrayBackedValueStorage outInput1 = new ArrayBackedValueStorage();
                private IEvaluator eval0 = recordEvalFactory.createEvaluator(outInput0);
                private IEvaluator eval1 = fldNameEvalFactory.createEvaluator(outInput1);
                @SuppressWarnings("unchecked")
                private ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
                        .getSerializerDeserializer(BuiltinType.ANULL);
                private int fieldValueOffset;
                private int fieldValueLength;
                private ATypeTag fieldValueTypeTag = ATypeTag.NULL;

                @Override
                public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {

                    try {
                        outInput0.reset();
                        eval0.evaluate(tuple);
                        outInput1.reset();
                        eval1.evaluate(tuple);
                        byte[] serRecord = outInput0.getBytes();

                        if (serRecord[0] == SER_NULL_TYPE_TAG) {
                            nullSerde.serialize(ANull.NULL, out);
                            return;
                        }

                        if (serRecord[0] != SER_RECORD_TYPE_TAG) {
                            throw new AlgebricksException("Field accessor is not defined for values of type"
                                    + EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(serRecord[0]));
                        }

                        byte[] serFldName = outInput1.getBytes();
                        fieldValueOffset = ARecordSerializerDeserializer.getFieldOffsetByName(serRecord, serFldName);
                        if (fieldValueOffset < 0) {
                            out.writeByte(ATypeTag.NULL.serialize());
                            return;
                        }

                        fieldValueTypeTag = EnumDeserializer.ATYPETAGDESERIALIZER
                                .deserialize(serRecord[fieldValueOffset]);
                        fieldValueLength = NonTaggedFormatUtil.getFieldValueLength(serRecord, fieldValueOffset,
                                fieldValueTypeTag, true) + 1;
                        out.write(serRecord, fieldValueOffset, fieldValueLength);

                    } catch (IOException e) {
                        throw new AlgebricksException(e);
                    } catch (AsterixException e) {
                        throw new AlgebricksException(e);
                    }
                }
            };
        }

    }
}
