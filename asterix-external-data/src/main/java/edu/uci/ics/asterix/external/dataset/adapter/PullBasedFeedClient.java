/*
 * Copyright 2009-2012 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.external.dataset.adapter;

import java.io.DataOutput;
import java.io.IOException;

import edu.uci.ics.asterix.builders.IARecordBuilder;
import edu.uci.ics.asterix.builders.RecordBuilder;
import edu.uci.ics.asterix.builders.UnorderedListBuilder;
import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ARecordSerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ABoolean;
import edu.uci.ics.asterix.om.base.AInt32;
import edu.uci.ics.asterix.om.base.AMutableDateTime;
import edu.uci.ics.asterix.om.base.AMutableInt32;
import edu.uci.ics.asterix.om.base.AMutablePoint;
import edu.uci.ics.asterix.om.base.AMutableRecord;
import edu.uci.ics.asterix.om.base.AMutableString;
import edu.uci.ics.asterix.om.base.AMutableUnorderedList;
import edu.uci.ics.asterix.om.base.AString;
import edu.uci.ics.asterix.om.base.IACursor;
import edu.uci.ics.asterix.om.base.IAObject;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.AUnorderedListType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.data.std.util.ArrayBackedValueStorage;

public abstract class PullBasedFeedClient implements IPullBasedFeedClient {

    protected ARecordSerializerDeserializer recordSerDe;
    protected AMutableRecord mutableRecord;
    protected boolean messageReceived;
    protected boolean continueIngestion = true;
    protected IARecordBuilder recordBuilder = new RecordBuilder();

    protected AMutableString aString = new AMutableString("");
    protected AMutableInt32 aInt32 = new AMutableInt32(0);
    protected AMutablePoint aPoint = new AMutablePoint(0, 0);
    protected AMutableDateTime aDateTime = new AMutableDateTime(0);

    @SuppressWarnings("unchecked")
    protected ISerializerDeserializer<AString> stringSerde = AqlSerializerDeserializerProvider.INSTANCE
            .getSerializerDeserializer(BuiltinType.ASTRING);
    @SuppressWarnings("unchecked")
    protected ISerializerDeserializer<ABoolean> booleanSerde = AqlSerializerDeserializerProvider.INSTANCE
            .getSerializerDeserializer(BuiltinType.ABOOLEAN);
    @SuppressWarnings("unchecked")
    protected ISerializerDeserializer<AInt32> int32Serde = AqlSerializerDeserializerProvider.INSTANCE
            .getSerializerDeserializer(BuiltinType.AINT32);

    public abstract InflowState setNextRecord() throws Exception;

    @Override
    public InflowState nextTuple(DataOutput dataOutput) throws AsterixException {
        try {
            InflowState state = setNextRecord();
            boolean first = true;
            switch (state) {
                case DATA_AVAILABLE:
                    IAType t = mutableRecord.getType();
                    ATypeTag tag = t.getTypeTag();
                    dataOutput.writeByte(tag.serialize());
                    if (first) {
                        recordBuilder.reset(mutableRecord.getType());
                        first = false;
                    }
                    recordBuilder.init();
                    writeRecord(mutableRecord, dataOutput, recordBuilder);
                    break;
 
                case DATA_NOT_AVAILABLE:
                    break;
                case NO_MORE_DATA:
                    break;
            }
            return state;
        } catch (Exception e) {
            throw new AsterixException(e);
        }

    }

    @SuppressWarnings("unchecked")
    private void writeRecord(AMutableRecord record, DataOutput dataOutput, IARecordBuilder recordBuilder)
            throws IOException, AsterixException {
        ArrayBackedValueStorage fieldValue = new ArrayBackedValueStorage();
        int numFields = record.getType().getFieldNames().length;
        for (int pos = 0; pos < numFields; pos++) {
            fieldValue.reset();
            IAObject obj = record.getValueByPos(pos);
            writeObject(obj, fieldValue.getDataOutput());
            recordBuilder.addField(pos, fieldValue);
        }
        recordBuilder.write(dataOutput, false);
    }

    private void writeObject(IAObject obj, DataOutput dataOutput) throws IOException, AsterixException {
        switch (obj.getType().getTypeTag()) {
            case RECORD:
                ATypeTag tag = obj.getType().getTypeTag();
                try {
                    dataOutput.writeByte(tag.serialize());
                } catch (IOException e) {
                    throw new HyracksDataException(e);
                }
                IARecordBuilder recordBuilder = new RecordBuilder();
                recordBuilder.reset((ARecordType) obj.getType());
                recordBuilder.init();
                writeRecord((AMutableRecord) obj, dataOutput, recordBuilder);
                break;
            case UNORDEREDLIST:
                tag = obj.getType().getTypeTag();
                try {
                    dataOutput.writeByte(tag.serialize());
                } catch (IOException e) {
                    throw new HyracksDataException(e);
                }
                UnorderedListBuilder listBuilder = new UnorderedListBuilder();
                listBuilder.reset((AUnorderedListType) ((AMutableUnorderedList) obj).getType());
                IACursor cursor = ((AMutableUnorderedList) obj).getCursor();
                ArrayBackedValueStorage listItemValue = new ArrayBackedValueStorage();
                while (cursor.next()) {
                    listItemValue.reset();
                    IAObject item = cursor.get();
                    writeObject(item, listItemValue.getDataOutput());
                    listBuilder.addItem(listItemValue);
                }
                listBuilder.write(dataOutput, false);
                break;
            default:
                AqlSerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(obj.getType()).serialize(obj,
                        dataOutput);
                break;
        }
    }
}