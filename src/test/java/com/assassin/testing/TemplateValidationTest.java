package com.assassin.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TemplateValidationIT {

    private static JsonNode template;

    @BeforeAll
    public static void loadTemplate() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        template = mapper.readTree(new File("template.yaml"));
    }

    @Test
    public void testLogRetentionInDaysParameterTypeIsNumber() {
        JsonNode parameters = template.path("Parameters");
        assertNotNull(parameters, "Parameters section should exist");

        JsonNode logRetentionParam = parameters.path("LogRetentionInDays");
        assertNotNull(logRetentionParam, "LogRetentionInDays parameter should exist");

        JsonNode typeNode = logRetentionParam.path("Type");
        assertEquals("Number", typeNode.asText(), "LogRetentionInDays parameter Type should be Number");
    }
} 