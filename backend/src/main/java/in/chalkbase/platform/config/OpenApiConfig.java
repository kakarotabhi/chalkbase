package in.chalkbase.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    @Bean
    OpenAPI chalkbaseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chalkbase API")
                        .description("School management system for Indian K-12 schools")
                        .version("v1")
                        .license(new License().name("Proprietary")));
    }

    /**
     * Makes every property of a response schema required unless it was declared nullable.
     *
     * <h2>Why the default has to be inverted</h2>
     *
     * springdoc derives {@code required} from Jakarta validation — {@code @NotBlank},
     * {@code @NotNull}, {@code @Size}. That works for a request, which is validated, and it is why
     * the request schemas come out fully specified without any help.
     *
     * <p>A response record carries none of those annotations, because validating an output is
     * meaningless: nothing would ever act on the failure. So springdoc has nothing to read and
     * emits every response property as optional. Generated from that, {@code fullName} — a
     * {@code not null} column that is never absent from any student — becomes
     * {@code fullName?: string}, and every screen that renders it needs a {@code ??} or a
     * {@code !}. Sixty of those would be worse than the hand-written models they replaced, and each
     * one would be noise hiding the handful of fields that genuinely can be missing.
     *
     * <p>Inverting the default puts the annotation where the information is. {@code @Schema(nullable
     * = true)} appears about forty times across the response records, and every one of them records
     * a fact somebody can check: the column is nullable, or the mapper can pass null.
     *
     * <h2>Nullable means absent, and the flag does not survive</h2>
     *
     * {@code spring.jackson.default-property-inclusion=non_null} means a null field is dropped from
     * the JSON rather than written as {@code null}. So the honest contract for a nullable field is
     * "not in {@code required}" — never {@code type: [string, "null"]}, which would generate
     * {@code x: string | null} and describe a wire value this application never sends. The
     * annotation is therefore read as a marker and then cleared, and what reaches
     * {@code contracts/openapi.json} says only that the property may be absent.
     *
     * <h2>Responses only</h2>
     *
     * Scoped by walking the document: the schemas reachable from a response body, minus anything
     * reachable from a request body or a parameter. That subtraction is load-bearing rather than
     * tidiness — {@code Pageable} is a query-parameter schema whose {@code page}, {@code size} and
     * {@code sort} are all genuinely optional, and marking them required would make the generated
     * type demand three values Spring supplies itself. A schema used by both a request and a
     * response is left alone, so this can never contradict what validation already said.
     */
    @Bean
    OpenApiCustomizer requiredUnlessNullable() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) {
                return;
            }
            Map<String, Schema> schemas = components.getSchemas();
            Set<String> responseOnly = new TreeSet<>(reachableFromResponses(openApi, schemas));
            responseOnly.removeAll(reachableFromInputs(openApi, schemas));
            responseOnly.stream().map(schemas::get).forEach(OpenApiConfig::requireEveryNonNullableProperty);
        };
    }

    /**
     * Marks the schema's properties required, minus the nullable ones, and clears the flag.
     *
     * <p>Recurses into inline object schemas — a property declared as a bare {@code object} rather
     * than as a named component — so the rule does not stop at the first level.
     */
    private static void requireEveryNonNullableProperty(Schema<?> schema) {
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        List<String> required = new ArrayList<>();
        schema.getProperties().forEach((name, property) -> {
            if (isNullable(property)) {
                clearNullable(property);
            } else {
                required.add(name);
            }
            requireEveryNonNullableProperty(property);
        });
        // Sorted so the document does not depend on the order Jackson introspected the record.
        required.sort(String::compareTo);
        schema.setRequired(required.isEmpty() ? null : required);
    }

    /**
     * True when the author said this property may be missing.
     *
     * <p>Both spellings are accepted because the document is OpenAPI 3.1, where {@code nullable} is
     * no longer a keyword and swagger-core renders {@code @Schema(nullable = true)} by adding
     * {@code "null"} to the type set instead. Which of the two a given springdoc version leaves on
     * the model is an implementation detail; the annotation in the Java is the contract.
     */
    private static boolean isNullable(Schema<?> property) {
        return Boolean.TRUE.equals(property.getNullable())
                || (property.getTypes() != null && property.getTypes().contains("null"));
    }

    private static void clearNullable(Schema<?> property) {
        property.setNullable(null);
        Set<String> types = property.getTypes();
        if (types != null && types.contains("null")) {
            Set<String> remaining = new LinkedHashSet<>(types);
            remaining.remove("null");
            property.setTypes(remaining.isEmpty() ? null : remaining);
        }
    }

    // ── which schemas are responses ──────────────────────────────────────────────────────────

    private static Set<String> reachableFromResponses(OpenAPI openApi, Map<String, Schema> schemas) {
        Set<String> roots = new LinkedHashSet<>();
        forEachOperation(openApi, operation -> {
            if (operation.getResponses() != null) {
                for (ApiResponse response : operation.getResponses().values()) {
                    collectRefs(contentSchemas(response.getContent()), roots);
                }
            }
        });
        return expand(roots, schemas);
    }

    private static Set<String> reachableFromInputs(OpenAPI openApi, Map<String, Schema> schemas) {
        Set<String> roots = new LinkedHashSet<>();
        forEachOperation(openApi, operation -> {
            RequestBody body = operation.getRequestBody();
            if (body != null) {
                collectRefs(contentSchemas(body.getContent()), roots);
            }
            if (operation.getParameters() != null) {
                for (Parameter parameter : operation.getParameters()) {
                    collectRefs(List.of(parameter.getSchema()), roots);
                    collectRefs(contentSchemas(parameter.getContent()), roots);
                }
            }
        });
        return expand(roots, schemas);
    }

    private static void forEachOperation(OpenAPI openApi, Consumer<Operation> visitor) {
        if (openApi.getPaths() == null) {
            return;
        }
        for (PathItem path : openApi.getPaths().values()) {
            path.readOperations().forEach(visitor);
        }
    }

    private static List<Schema> contentSchemas(Content content) {
        if (content == null) {
            return List.of();
        }
        return content.values().stream().map(MediaType::getSchema).toList();
    }

    /** Follows {@code $ref}s through the component map until nothing new is found. */
    private static Set<String> expand(Set<String> roots, Map<String, Schema> schemas) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (!seen.add(name)) {
                continue;
            }
            Set<String> nested = new LinkedHashSet<>();
            collectRefs(List.of(schemas.getOrDefault(name, new Schema<>())), nested);
            nested.stream().filter(next -> !seen.contains(next)).forEach(pending::addLast);
        }
        return seen;
    }

    /** Every component name mentioned anywhere inside these schemas, however deeply nested. */
    private static void collectRefs(Collection<Schema> schemas, Set<String> into) {
        for (Schema<?> schema : schemas) {
            if (schema == null) {
                continue;
            }
            if (schema.get$ref() != null && schema.get$ref().startsWith(SCHEMA_REF_PREFIX)) {
                into.add(schema.get$ref().substring(SCHEMA_REF_PREFIX.length()));
            }
            if (schema.getProperties() != null) {
                collectRefs(schema.getProperties().values(), into);
            }
            collectRefs(nested(schema), into);
        }
    }

    private static List<Schema> nested(Schema<?> schema) {
        List<Schema> children = new ArrayList<>();
        addIfPresent(children, schema.getItems());
        if (schema.getAdditionalProperties() instanceof Schema<?> additional) {
            children.add(additional);
        }
        addAllIfPresent(children, schema.getAllOf());
        addAllIfPresent(children, schema.getAnyOf());
        addAllIfPresent(children, schema.getOneOf());
        return children;
    }

    private static void addIfPresent(List<Schema> target, Schema<?> schema) {
        if (schema != null) {
            target.add(schema);
        }
    }

    private static void addAllIfPresent(List<Schema> target, List<Schema> schemas) {
        if (schemas != null) {
            target.addAll(schemas);
        }
    }
}
