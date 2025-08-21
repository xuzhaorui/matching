package io.github.xuzhaorui.matching.messaging;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AvroSerde {

    public byte[] toBytes(GenericRecord record) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(record.getSchema());
            Encoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public GenericRecord fromBytes(byte[] bytes, Schema schema) {
        try {
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            Decoder decoder = DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(bytes), null);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
