package com.mgmresorts.shopping.cart.entity;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class ProductData {

    public static class ProductDataDeserializer extends JsonDeserializer<ProductData> {
        public ProductData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            if (p.currentTokenId() == JsonTokenId.ID_START_OBJECT) {
                final JsonNode node = ctxt.readTree(p);
                final ProductData product = new ProductData();
                product.node = node;
                return product;
            }
            return null;

        }
    }

    public static class ProductDataSerializer extends JsonSerializer<ProductData> {
        public void serialize(ProductData value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeObject(value.getNode());
        }
    }

    @JsonPropertyDescription("Replace node with product json. Please refer to product specific schema for this portion")
    private JsonNode node;

    public JsonNode getNode() {
        return node;
    }
}
