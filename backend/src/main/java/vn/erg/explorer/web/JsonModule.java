package vn.erg.explorer.web;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.jooby.Body;
import io.jooby.Context;
import io.jooby.Extension;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.MessageDecoder;
import io.jooby.MessageEncoder;
import io.jooby.StatusCode;
import io.jooby.output.Output;
import lombok.NonNull;

import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * JSON encoder / decoder for the REST API on the registry's {@link ObjectMapper}. Unlike the stock Jackson
 * module it turns a {@link JsonResult} with an {@code errorCode} into that HTTP status, so controllers return
 * {@code notfound()} instead of throwing.
 */
public class JsonModule implements Extension, MessageDecoder, MessageEncoder {

    private ObjectMapper mapper;

    private TypeFactory typeFactory;

    @Override
    public void install(@NonNull Jooby application) {
        mapper = application.require(ObjectMapper.class);
        typeFactory = mapper.getTypeFactory();
        application.decoder(MediaType.json, this);
        application.encoder(MediaType.json, this);
        application.errorCode(JsonParseException.class, StatusCode.BAD_REQUEST);
    }

    /** After the Guice module, so the ObjectMapper is in the registry. */
    @Override
    public boolean lateinit() {
        return true;
    }

    @Override
    public @NonNull Object decode(@NonNull Context ctx, @NonNull Type type) throws Exception {
        Body body = ctx.body();
        if (body.isInMemory()) {
            return type == JsonNode.class ? mapper.readTree(body.bytes()) : mapper.readValue(body.bytes(), typeFactory.constructType(type));
        }
        try (InputStream stream = body.stream()) {
            return type == JsonNode.class ? mapper.readTree(stream) : mapper.readValue(stream, typeFactory.constructType(type));
        }
    }

    @Override
    public Output encode(@NonNull Context ctx, @NonNull Object value) throws Exception {
        ctx.setDefaultResponseType(MediaType.json);
        if (value instanceof JsonResult result && result.getErrorCode() != null) {
            ctx.setResponseCode(result.getErrorCode());
        }
        // Jackson keeps its own buffer cache, so wrap the bytes instead of streaming into Jooby's
        return ctx.getOutputFactory().wrap(mapper.writeValueAsBytes(value));
    }

}
