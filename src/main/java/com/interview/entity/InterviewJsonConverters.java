package com.interview.entity;

import com.interview.dto.InterviewAiModels.Evaluation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import jakarta.persistence.PersistenceException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

public final class InterviewJsonConverters {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private InterviewJsonConverters() {}

    @Converter
    public static class EvaluationConverter implements AttributeConverter<Evaluation, String> {

        @Override
        public String convertToDatabaseColumn(Evaluation evaluation) {
            return encode(evaluation);
        }

        @Override
        public Evaluation convertToEntityAttribute(String value) {
            if (value == null) {
                return null;
            }
            try {
                Evaluation evaluation = JSON.readValue(value, Evaluation.class);
                if (evaluation != null) {
                    return evaluation;
                }
            } catch (JacksonException e) {
                // 파서 예외에는 저장한 면접 데이터가 포함될 수 있다.
            }
            throw new PersistenceException("Invalid stored evaluation JSON");
        }
    }

    @Converter
    public static class CriteriaConverter implements AttributeConverter<List<String>, String> {

        @Override
        public String convertToDatabaseColumn(List<String> criteria) {
            return encode(criteria);
        }

        @Override
        public List<String> convertToEntityAttribute(String value) {
            if (value == null) {
                return null;
            }
            try {
                List<String> criteria = JSON.readValue(value, STRING_LIST);
                if (criteria != null && !criteria.contains(null)) {
                    return List.copyOf(criteria);
                }
            } catch (JacksonException e) {
                // 파서 예외에는 저장한 면접 데이터가 포함될 수 있다.
            }
            throw new PersistenceException("Invalid stored evaluation criteria JSON");
        }
    }

    private static String encode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new PersistenceException("Could not serialize interview data");
        }
    }
}
