//package gobblin.converter.avro;
//
//import com.google.gson.*;
//import gobblin.configuration.WorkUnitState;
//import gobblin.converter.Converter;
//import gobblin.converter.DataConversionException;
//import gobblin.converter.SchemaConversionException;
//import gobblin.converter.SingleRecordIterable;
////import org.apache.avro.Schema;
//import org.apache.avro.generic.GenericRecord;
//import org.apache.avro.util.Utf8;
//
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * Created by zangyq on 2015/9/20.
// */
//public class AvroToParquetConverter extends Converter<String, MessageType, GenericRecord, JsonObject> {
//
//    private Group group;
//
//    @Override
//    public Converter<Schema, Schema, GenericRecord, JsonObject> init(WorkUnitState workUnit) {
//        this.gson = new GsonBuilder().create();
//        return this;
//    }
//
//    @Override
//    public JsonArray convertSchema(String inputSchema, WorkUnitState workUnit)
//            throws SchemaConversionException {
//        return new JsonParser().parse(inputSchema).getAsJsonArray();
//    }
//
//    @Override
//    public Iterable<JsonObject> convertRecord(JsonArray outputSchema, GenericRecord inputRecord, WorkUnitState workUnit)
//            throws DataConversionException {
//        Map<String, Object> record = new HashMap<String, Object>();
//        for (Schema.Field field : inputRecord.getSchema().getFields()) {
//            Object col = inputRecord.get(field.name());
//            if (col != null && col instanceof Utf8) {
//                col = col.toString();
//            }
//            record.put(field.name(), col);
//        }
//
//        return new SingleRecordIterable<JsonObject>(
//                this.gson.fromJson(this.gson.toJson(record), JsonObject.class).getAsJsonObject());
//    }
//
//}
