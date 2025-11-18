package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Map;

public enum CategoriaServicio {
    PLOMERIA,
    CARPINTERIA,
    ASEO,
    ELECTRICIDAD,
    PINTURA,
    JARDINERIA,
    COSTURA,
    COCINA,
    TECNOLOGIA;

    @JsonCreator
    public static CategoriaServicio fromJson(Object raw) {
        if (raw == null) {
            return null;
        }

        if (raw instanceof CategoriaServicio categoria) {
            return categoria;
        }

        if (raw instanceof String texto) {
            return parse(texto);
        }

        if (raw instanceof Map<?, ?> map) {
            Object value = map.get("value");
            if (value == null) {
                value = map.get("label");
            }
            if (value == null) {
                value = map.get("name");
            }
            if (value instanceof String texto) {
                return parse(texto);
            }
        }

        throw new IllegalArgumentException("Categoria no valida: " + raw);
    }

    private static CategoriaServicio parse(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('-', ' ')
                .trim()
                .toUpperCase()
                .replace(' ', '_');

        if (normalized.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Categoria no valida: " + raw));
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    public String getDisplayName() {
        return switch (this) {
            case PLOMERIA -> "Plomeria";
            case CARPINTERIA -> "Carpinteria";
            case ASEO -> "Aseo";
            case ELECTRICIDAD -> "Electricidad";
            case PINTURA -> "Pintura";
            case JARDINERIA -> "Jardineria";
            case COSTURA -> "Costura";
            case COCINA -> "Cocina";
            case TECNOLOGIA -> "Tecnologia";
        };
    }
}
