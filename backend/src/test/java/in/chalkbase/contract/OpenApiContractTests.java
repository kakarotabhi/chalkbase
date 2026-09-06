package in.chalkbase.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exports the live OpenAPI document to {@code contracts/openapi.json} on every build.
 *
 * <p>This is a test rather than a Maven plugin because the only honest source of the contract is
 * the running application: springdoc reads the actual controllers, the actual validation
 * annotations and {@code OpenApiConfig}'s customizers. A plugin reading the source would be a
 * second implementation of springdoc and would drift from it.
 *
 * <h2>Determinism is the whole point</h2>
 *
 * CI regenerates this file and fails on a diff. If the bytes moved between two runs of the same
 * code, that gate would fire on innocent pull requests, and a gate that cries wolf is switched off
 * within a week. So the document is not written as springdoc hands it over:
 *
 * <ul>
 *   <li>every object's keys are sorted, because springdoc's map ordering follows classpath scan
 *       order and JVM hash order, neither of which is stable across runs or machines;
 *   <li>{@code required} and {@code tags} are sorted, because they are sets that happen to be
 *       serialised as arrays — {@code enum} and {@code parameters} are left alone, because their
 *       order is meaningful and comes from a Java enum's or a method signature's declaration order;
 *   <li>the JSON is written by hand rather than by a Jackson pretty printer, with LF endings and
 *       two-space indentation, so the bytes do not depend on the platform line separator or on
 *       which Jackson version's pretty printer is on the classpath.
 * </ul>
 *
 * <p>{@link #exportIsDeterministic()} asserts the second half of that: rendering the same document
 * twice has to produce the same bytes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Arrays that are semantically sets: their serialised order carries no information. */
    private static final List<String> SET_VALUED_ARRAYS = List.of("required", "tags");

    @Autowired
    MockMvc mockMvc;

    @Test
    void exportsTheContract() throws Exception {
        Path target = contractsDirectory().resolve("openapi.json");
        Files.writeString(target, render(fetchApiDocs()), StandardCharsets.UTF_8);
        assertThat(target).content(StandardCharsets.UTF_8).startsWith("{\n  \"components\"");
    }

    @Test
    void exportIsDeterministic() throws Exception {
        JsonNode document = fetchApiDocs();
        assertThat(render(document)).isEqualTo(render(fetchApiDocs())).isEqualTo(render(document));
    }

    /**
     * Every response property is required unless it was declared nullable.
     *
     * <p>Guards the inversion in {@code OpenApiConfig}: without it every response field generates
     * as optional, because a response record carries no validation annotations for springdoc to
     * read — validating an output is meaningless. See that class for why.
     */
    @Test
    void responseSchemasDeclareTheirRequiredProperties() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");
        JsonNode studentSummary = schemas.path("StudentSummary");

        assertThat(required(studentSummary))
                .as("everything but the one nullable property")
                .containsExactlyInAnyOrder("id", "admissionNumber", "fullName", "gender", "status");
        assertThat(required(schemas.path("PageResponseStudentSummary")))
                .as("the envelope resolves its generics, so the page inside it is described too")
                .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
        assertThat(required(schemas.path("ApiResponsePageResponseStudentSummary")))
                .as("exactly one of data and error is present, so neither is required")
                .containsExactlyInAnyOrder("success", "timestamp");
    }

    /**
     * A nullable field is absent from the JSON, never sent as {@code null}.
     *
     * <p>{@code spring.jackson.default-property-inclusion=non_null} is what makes that true, and
     * this is the assertion that keeps the document saying it. If {@code nullable} — or its OpenAPI
     * 3.1 spelling, {@code "null"} inside a type array — ever reached the file, the generator would
     * start emitting {@code x: T | null}, which the root {@code AGENTS.md} calls "a lie the compiler
     * cannot catch": code reading it with {@code === null} takes the wrong branch in production
     * while passing every mocked spec.
     */
    @Test
    void nullableMeansAbsentRatherThanNull() throws Exception {
        assertThat(render(fetchApiDocs())).doesNotContain("nullable").doesNotContain("\"null\"");
    }

    /**
     * A query-parameter schema keeps its optional fields.
     *
     * <p>{@code Pageable} is the reason the inversion is scoped to response schemas rather than
     * applied to the whole component map: {@code page}, {@code size} and {@code sort} are supplied
     * by Spring when a caller omits them, and a generated type demanding all three would be wrong
     * about every list endpoint.
     */
    @Test
    void parameterSchemasAreLeftAlone() throws Exception {
        assertThat(required(fetchApiDocs().path("components").path("schemas").path("Pageable")))
                .isEmpty();
    }

    /**
     * The inversion must not contradict what Jakarta validation already said about a request.
     *
     * <p>A request schema's {@code required} comes from {@code @NotBlank} / {@code @NotNull} /
     * {@code @Size}. Marking a genuinely optional request field required would make the generated
     * TypeScript demand a value the server does not want.
     */
    @Test
    void optionalRequestFieldsStayOptional() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        assertThat(required(schemas.path("SaveStudentRequest")))
                .contains("admissionNumber", "fullName", "dateOfBirth", "gender", "status")
                .doesNotContain("admittedOn");
        assertThat(required(schemas.path("CreateSchoolRequest")))
                .contains("code", "name", "board")
                .doesNotContain("city", "state");
        assertThat(required(schemas.path("CreateEnrolmentRequest")))
                .contains("academicSessionId", "sectionId")
                .doesNotContain("rollNumber");
    }

    private JsonNode fetchApiDocs() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readTree(json);
    }

    private static List<String> required(JsonNode schema) {
        List<String> names = new ArrayList<>();
        schema.path("required").forEach(name -> names.add(name.stringValue()));
        return names;
    }

    /** Walks up from the working directory until the sibling {@code contracts/} appears. */
    private static Path contractsDirectory() throws IOException {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            Path contracts = candidate.resolve("contracts");
            if (Files.isDirectory(contracts)) {
                return contracts;
            }
        }
        throw new IOException("no contracts/ directory above " + Path.of("").toAbsolutePath());
    }

    private static String render(JsonNode document) {
        StringBuilder out = new StringBuilder(256 * 1024);
        write(document, "", out);
        return out.append('\n').toString();
    }

    private static void write(JsonNode node, String indent, StringBuilder out) {
        if (node instanceof ObjectNode object) {
            writeObject(object, indent, out);
        } else if (node instanceof ArrayNode array) {
            writeArray(array, indent, out);
        } else {
            // Scalars render to a single canonical line, escaping included.
            out.append(MAPPER.writeValueAsString(node));
        }
    }

    private static void writeObject(ObjectNode object, String indent, StringBuilder out) {
        if (object.isEmpty()) {
            out.append("{}");
            return;
        }
        String inner = indent + "  ";
        Map<String, JsonNode> sorted = new TreeMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = object.properties().iterator(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            sorted.put(entry.getKey(), entry.getValue());
        }
        out.append("{\n");
        boolean first = true;
        for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
            if (!first) {
                out.append(",\n");
            }
            first = false;
            out.append(inner).append(MAPPER.writeValueAsString(entry.getKey())).append(": ");
            write(normalise(entry.getKey(), entry.getValue()), inner, out);
        }
        out.append('\n').append(indent).append('}');
    }

    private static void writeArray(ArrayNode array, String indent, StringBuilder out) {
        if (array.isEmpty()) {
            out.append("[]");
            return;
        }
        String inner = indent + "  ";
        out.append("[\n");
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                out.append(",\n");
            }
            out.append(inner);
            write(array.get(i), inner, out);
        }
        out.append('\n').append(indent).append(']');
    }

    /** Sorts the arrays that are sets, so springdoc's insertion order cannot leak into the file. */
    private static JsonNode normalise(String field, JsonNode value) {
        if (!SET_VALUED_ARRAYS.contains(field) || !(value instanceof ArrayNode array)) {
            return value;
        }
        List<JsonNode> elements = new ArrayList<>();
        array.forEach(elements::add);
        if (!elements.stream().allMatch(JsonNode::isString)) {
            return value;
        }
        elements.sort(Comparator.comparing(JsonNode::stringValue));
        ArrayNode result = MAPPER.createArrayNode();
        elements.forEach(result::add);
        return result;
    }
}
