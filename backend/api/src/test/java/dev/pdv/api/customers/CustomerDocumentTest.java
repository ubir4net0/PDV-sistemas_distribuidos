package dev.pdv.api.customers;

import dev.pdv.api.customers.domain.CustomerDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerDocumentTest {
    @ParameterizedTest
    @CsvSource({"PF,123.456.789-09,12345678909", "PF,12345678909,12345678909",
            "PJ,12.ABC.345/01DE-35,12ABC34501DE35", "PJ,12abc34501de35,12ABC34501DE35",
            "PJ,11.222.333/0001-81,11222333000181"})
    void canonicalizesAndValidates(String type, String raw, String expected) {
        assertThat(CustomerDocument.canonical(type, raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PF,00000000000", "PF,11111111111", "PF,12345678900", "PF,abc12345678909",
            "PF,123.456789-09", "PJ,00000000000000", "PJ,12ABC34501DE34", "PJ,12ABC34501DEAA",
            "PJ,12ABC34501DE35!", "PF,12ABC34501DE35", "PJ,12345678909", "UNKNOWN,12345678909",
            "PJ,12ABß4501DE00"})
    void rejectsInvalidDocuments(String type, String value) {
        assertThatThrownBy(() -> CustomerDocument.canonical(type, value)).isInstanceOf(IllegalArgumentException.class);
    }
}
