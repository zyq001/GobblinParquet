/*
 * Copyright (C) 2014-2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableInput;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.mapred.FsInput;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import com.google.common.primitives.Longs;

import static org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE;
import static org.apache.avro.SchemaCompatibility.checkReaderWriterCompatibility;


/**
 * A Utils class for dealing with Avro objects
 */
public class AvroUtils {

  private static final Logger LOG = LoggerFactory.getLogger(AvroUtils.class);

  private static final String FIELD_LOCATION_DELIMITER = ".";

  private static final String AVRO_SUFFIX = ".avro";

  /**
   * Given a GenericRecord, this method will return the schema of the field specified by the path parameter. The
   * fieldLocation parameter is an ordered string specifying the location of the nested field to retrieve. For example,
   * field1.nestedField1 takes the the schema of the field "field1", and retrieves the schema "nestedField1" from it.
   * @param schema is the record to retrieve the schema from
   * @param fieldLocation is the location of the field
   * @return the schema of the field
   */
  public static Optional<Schema> getFieldSchema(Schema schema, String fieldLocation) {
    Preconditions.checkNotNull(schema);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(fieldLocation));

    Splitter splitter = Splitter.on(FIELD_LOCATION_DELIMITER).omitEmptyStrings().trimResults();
    List<String> pathList = Lists.newArrayList(splitter.split(fieldLocation));

    if (pathList.size() == 0) {
      return Optional.absent();
    }

    return AvroUtils.getFieldSchemaHelper(schema, pathList, 0);
  }

  /**
   * Helper method that does the actual work for {@link #getFieldSchema(Schema, String)}
   * @param schema passed from {@link #getFieldValue(Schema, String)}
   * @param pathList passed from {@link #getFieldValue(Schema, String)}
   * @param field keeps track of the index used to access the list pathList
   * @return the schema of the field
   */
  private static Optional<Schema> getFieldSchemaHelper(Schema schema, List<String> pathList, int field) {
    if (schema.getField(pathList.get(field)) == null) {
      return Optional.absent();
    }
    if ((field + 1) == pathList.size()) {
      return Optional.fromNullable(schema.getField(pathList.get(field)).schema());
    } else {
      return AvroUtils.getFieldSchemaHelper(schema.getField(pathList.get(field)).schema(), pathList, ++field);
    }
  }

  /**
   * Given a GenericRecord, this method will return the field specified by the path parameter. The fieldLocation
   * parameter is an ordered string specifying the location of the nested field to retrieve. For example,
   * field1.nestedField1 takes the the value of the field "field1", and retrieves the field "nestedField1" from it.
   * @param record is the record to retrieve the field from
   * @param fieldLocation is the location of the field
   * @return the value of the field
   */
  public static Optional<Object> getFieldValue(GenericRecord record, String fieldLocation) {
    Preconditions.checkNotNull(record);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(fieldLocation));

    Splitter splitter = Splitter.on(FIELD_LOCATION_DELIMITER).omitEmptyStrings().trimResults();
    List<String> pathList = splitter.splitToList(fieldLocation);

    if (pathList.size() == 0) {
      return Optional.absent();
    }

    return AvroUtils.getFieldHelper(record, pathList, 0);
  }

  /**
   * Helper method that does the actual work for {@link #getFieldValue(GenericRecord, String)}
   * @param data passed from {@link #getFieldValue(GenericRecord, String)}
   * @param pathList passed from {@link #getFieldValue(GenericRecord, String)}
   * @param field keeps track of the index used to access the list pathList
   * @return the value of the field
   */
  private static Optional<Object> getFieldHelper(Object data, List<String> pathList, int field) {
    if (data == null) {
      return Optional.absent();
    }

    if ((field + 1) == pathList.size()) {
      return Optional.fromNullable(((Record) data).get(pathList.get(field)));
    } else {
      return AvroUtils.getFieldHelper(((Record) data).get(pathList.get(field)), pathList, ++field);
    }
  }

  /**
   * Change the schema of an Avro record.
   * @param record The Avro record whose schema is to be changed.
   * @param newSchema The target schema. It must be compatible as reader schema with record.getSchema() as writer schema.
   * @return a new Avro record with the new schema.
   * @throws IOException if conversion failed.
   */
  public static GenericRecord convertRecordSchema(GenericRecord record, Schema newSchema) throws IOException {
    if (checkReaderWriterCompatibility(newSchema, record.getSchema()).getType() != COMPATIBLE) {
      LOG.debug("Record schema not compatible with writer schema. Converting record schema to writer schema may fail.");
    }

    try {
      BinaryDecoder decoder = new DecoderFactory().binaryDecoder(recordToByteArray(record), null);
      DatumReader<GenericRecord> reader = new GenericDatumReader<GenericRecord>(record.getSchema(), newSchema);
      return reader.read(null, decoder);
    } catch (IOException e) {
      throw new IOException(
          String.format("Cannot convert avro record to new schema. Origianl schema = %s, new schema = %s",
              record.getSchema(), newSchema),
          e);
    }
  }

  /**
   * Convert a GenericRecord to a byte array.
   */
  public static byte[] recordToByteArray(GenericRecord record) throws IOException {
    Closer closer = Closer.create();
    try {
      ByteArrayOutputStream out = closer.register(new ByteArrayOutputStream());
      Encoder encoder = EncoderFactory.get().directBinaryEncoder(out, null);
      DatumWriter<GenericRecord> writer = new GenericDatumWriter<GenericRecord>(record.getSchema());
      writer.write(record, encoder);
      byte[] byteArray = out.toByteArray();
      return byteArray;
    } catch (Throwable t) {
      throw closer.rethrow(t);
    } finally {
      closer.close();
    }
  }

  /**
   * Get Avro schema from an Avro data file.
   */
  public static Schema getSchemaFromDataFile(Path dataFile, FileSystem fs) throws IOException {
    Closer closer = Closer.create();
    try {
      SeekableInput sin = closer.register(new FsInput(dataFile, fs.getConf()));
      DataFileReader<GenericRecord> reader =
          closer.register(new DataFileReader<GenericRecord>(sin, new GenericDatumReader<GenericRecord>()));
      return reader.getSchema();
    } finally {
      closer.close();
    }
  }

  /**
   * Parse Avro schema from a schema file.
   */
  public static Schema parseSchemaFromFile(Path filePath, FileSystem fs) throws IOException {
    Closer closer = Closer.create();
    try {
      InputStream in = closer.register(fs.open(filePath));
      return new Schema.Parser().parse(in);
    } finally {
      closer.close();
    }
  }

  /**
   * Get the latest avro schema for a directory
   * @param directory the input dir that contains avro files
   * @param conf configuration
   * @param latest true to return latest schema, false to return oldest schema
   * @return the latest/oldest schema in the directory
   * @throws IOException
   */
  public static Schema getDirectorySchema(Path directory, Configuration conf, boolean latest) throws IOException {
    Schema schema = null;
    Closer closer = Closer.create();
    try {
      List<FileStatus> files = getDirectorySchemaHelper(directory, FileSystem.get(conf));
      if (files == null || files.size() == 0) {
        LOG.warn("There is no previous avro file in the directory: " + directory);
      } else {
        FileStatus file = latest ? files.get(0) : files.get(files.size() - 1);
        LOG.info("Path to get the avro schema: " + file);
        FsInput fi = new FsInput(file.getPath(), conf);
        GenericDatumReader<GenericRecord> genReader = new GenericDatumReader<GenericRecord>();
        schema = closer.register(new DataFileReader<GenericRecord>(fi, genReader)).getSchema();
      }
    } catch (IOException ioe) {
      throw new IOException("Cannot get the schema for directory " + directory, ioe);
    } catch (Throwable t) {
      throw closer.rethrow(t);
    } finally {
      closer.close();
    }
    return schema;
  }

  private static List<FileStatus> getDirectorySchemaHelper(Path directory, FileSystem fs) throws IOException {
    List<FileStatus> files = Lists.newArrayList();
    if (fs.exists(directory)) {
      getAllNestedAvroFiles(fs.getFileStatus(directory), files, fs);
      if (files.size() > 0) {
        Collections.sort(files, new Comparator<FileStatus>() {
          @Override
          public int compare(FileStatus file1, FileStatus file2) {
            return Longs.compare(Long.valueOf(file2.getModificationTime()), Long.valueOf(file1.getModificationTime()));
          }
        });
      }
    }
    return files;
  }

  @SuppressWarnings("deprecation")
  private static void getAllNestedAvroFiles(FileStatus dir, List<FileStatus> files, FileSystem fs) throws IOException {
    if (dir.isDir()) {
      FileStatus[] filesInDir = fs.listStatus(dir.getPath());
      if (filesInDir != null) {
        for (FileStatus f : filesInDir) {
          getAllNestedAvroFiles(f, files, fs);
        }
      }
    } else if (dir.getPath().getName().endsWith(AVRO_SUFFIX)) {
      files.add(dir);
    }
  }

  /**
   * Merge oldSchema and newSchame. Set a field default value to null, if this field exists in the old schema but not in the new schema.
   * @param oldSchema
   * @param newSchema
   * @return schema that contains all the fields in both old and new schema.
   */
  public static Schema nullifyFieldsForSchemaMerge(Schema oldSchema, Schema newSchema) {
    if (oldSchema == null) {
      LOG.warn("No previous schema available, use the new schema instead.");
      return newSchema;
    }

    if (!(oldSchema.getType().equals(Type.RECORD) && newSchema.getType().equals(Type.RECORD))) {
      LOG.warn("Both previous schema and new schema need to be record type. Quit merging schema.");
      return newSchema;
    }

    List<Field> combinedFields = Lists.newArrayList();
    for (Field newFld : newSchema.getFields()) {
      combinedFields.add(new Field(newFld.name(), newFld.schema(), newFld.doc(), newFld.defaultValue()));
    }

    for (Field oldFld : oldSchema.getFields()) {
      if (newSchema.getField(oldFld.name()) == null) {
        List<Schema> union = Lists.newArrayList();
        Schema oldFldSchema = oldFld.schema();
        if (oldFldSchema.getType().equals(Type.UNION)) {
          union.add(Schema.create(Type.NULL));
          for (Schema itemInUion : oldFldSchema.getTypes()) {
            if (!itemInUion.getType().equals(Type.NULL)) {
              union.add(itemInUion);
            }
          }
          Schema newFldSchema = Schema.createUnion(union);
          combinedFields.add(new Field(oldFld.name(), newFldSchema, oldFld.doc(), oldFld.defaultValue()));
        } else {
          union.add(Schema.create(Type.NULL));
          union.add(oldFldSchema);
          Schema newFldSchema = Schema.createUnion(union);
          combinedFields.add(new Field(oldFld.name(), newFldSchema, oldFld.doc(), oldFld.defaultValue()));
        }
      }
    }

    Schema mergedSchema =
        Schema.createRecord(newSchema.getName(), newSchema.getDoc(), newSchema.getNamespace(), newSchema.isError());
    mergedSchema.setFields(combinedFields);
    return mergedSchema;
  }

  /**
   * Remove map, array, enum fields, as well as union fields that contain map, array or enum,
   * from an Avro schema. A schema with these fields cannot be used as Mapper key in a
   * MapReduce job.
   */
  public static Optional<Schema> removeUncomparableFields(Schema schema) {
    switch (schema.getType()) {
      case RECORD:
        return removeUncomparableFieldsFromRecord(schema);
      case UNION:
        return removeUncomparableFieldsFromUnion(schema);
      case MAP:
        return Optional.absent();
      case ARRAY:
        return Optional.absent();
      case ENUM:
        return Optional.absent();
      default:
        return Optional.of(schema);
    }
  }

  private static Optional<Schema> removeUncomparableFieldsFromRecord(Schema record) {
    Preconditions.checkArgument(record.getType() == Schema.Type.RECORD);

    List<Field> fields = Lists.newArrayList();
    for (Field field : record.getFields()) {
      Optional<Schema> newFieldSchema = removeUncomparableFields(field.schema());
      if (newFieldSchema.isPresent()) {
        fields.add(new Field(field.name(), newFieldSchema.get(), field.doc(), field.defaultValue()));
      }
    }

    Schema newSchema = Schema.createRecord(record.getName(), record.getDoc(), record.getName(), false);
    newSchema.setFields(fields);
    return Optional.of(newSchema);
  }

  private static Optional<Schema> removeUncomparableFieldsFromUnion(Schema union) {
    Preconditions.checkArgument(union.getType() == Schema.Type.UNION);

    List<Schema> newUnion = Lists.newArrayList();
    for (Schema unionType : union.getTypes()) {
      Optional<Schema> newType = removeUncomparableFields(unionType);
      if (newType.isPresent()) {
        newUnion.add(newType.get());
      }
    }

    // Discard the union field if one or more types are removed from the union.
    if (newUnion.size() != union.getTypes().size()) {
      return Optional.absent();
    } else {
      return Optional.of(Schema.createUnion(newUnion));
    }
  }

  /**
   * Copies the input {@link org.apache.avro.Schema} but changes the schema name.
   * @param schema {@link org.apache.avro.Schema} to copy.
   * @param newName name for the copied {@link org.apache.avro.Schema}.
   * @return A {@link org.apache.avro.Schema} that is a copy of schema, but has the name newName.
   */
  public static Schema switchName(Schema schema, String newName) {
    if(schema.getName().equals(newName)) {
      return schema;
    }

    Schema newSchema = Schema.createRecord(newName, schema.getDoc(), schema.getNamespace(), schema.isError());

    List<Field> fields = schema.getFields();
    Iterable<Field> fieldsNew = Iterables.transform(fields, new Function<Field, Field>() {
      @Override public Schema.Field apply(Field input) {
        Field field = new Field(input.name(), input.schema(), input.doc(), input.defaultValue(), input.order());
        return field;
      }
    });

    newSchema.setFields(Lists.newArrayList(fieldsNew));
    return newSchema;
  }
}
