package dev.pdv.api.customers;

import java.util.Map;
import dev.pdv.api.support.CustomerHttpSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CustomerCreateHttpIT extends CustomerHttpSupport {
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATOR"})
    void authorizedRolesCreateCanonicalCustomer(String role) throws Exception {
        var response = send("POST", "/customers", token(role), input("12.ABC.345/01DE-35"));
        assertThat(response.statusCode()).isEqualTo(201);
        var body = json.readTree(response.body());
        assertThat(body.get("document").asString()).isEqualTo("12ABC34501DE35");
        assertThat(response.headers().firstValue("Location")).contains("/customers/" + body.get("id").asString());
        assertThat(body.get("active").asBoolean()).isTrue();
    }

    @Test
    void duplicateMaskedAndCanonicalDocumentsConflictSafely(CapturedOutput output) throws Exception {
        var token = token("OPERATOR");
        create(token);
        var response = send("POST", "/customers", token,
                Map.of("personType", "PF", "document", "123.456.789-09", "name", "Synthetic"));
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEqualTo("{\"code\":\"document_conflict\"}");
        assertThat(output.getAll().contains("12345678909")).isFalse();
    }

    @Test
    void requiresAuthenticationAndPermission() throws Exception {
        assertThat(send("POST", "/customers", null, input("12345678909")).statusCode()).isEqualTo(401);
        var token = token("OPERATOR");
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT role_id FROM user_roles WHERE user_id=?)", operator);
        assertThat(send("POST", "/customers", token, input("12345678909")).statusCode()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678900", "00000000000", "bad", "12ABC34501DE34"})
    void invalidDocumentsDoNotPersistOrLeak(String document) throws Exception {
        var response = send("POST", "/customers", token("ADMIN"), input(document));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("{\"code\":\"invalid_request\"}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isZero();
    }
}
