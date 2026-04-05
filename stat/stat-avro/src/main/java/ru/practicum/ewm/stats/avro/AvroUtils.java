package ru.practicum.ewm.stats.avro;

import lombok.experimental.UtilityClass;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@UtilityClass
public final class AvroUtils {

    public static byte[] toBytes(SpecificRecord record) {
        DatumWriter<SpecificRecord> writer = new SpecificDatumWriter<>(record.getSchema());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize Avro record", e);
        }
    }

    public static <T extends SpecificRecord> T fromBytes(byte[] data, Schema schema) {
        DatumReader<T> reader = new SpecificDatumReader<>(schema);
        try {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize Avro record", e);
        }
    }
}
