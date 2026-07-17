package io.snowdrop.cartography.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class RegistryQueryTest {

    @Test
    void testFilterByName() {
        given()
            .queryParam("name", "batch")
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(containsString("Batch"))
                .body(not(containsString(">REST<")))
                .body(not(containsString(">JPA<")));
    }

    @Test
    void testFilterByNameCaseInsensitive() {
        given()
            .queryParam("name", "BATCH")
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(containsString("Batch"));
    }

    @Test
    void testFilterBySpringSupported() {
        given()
            .queryParam("spring", "yes")
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(containsString("Batch"))
                .body(containsString("REST"))
                .body(containsString("JPA"));
    }

    @Test
    void testFilterByQuarkusSupported() {
        given()
            .queryParam("quarkus", "yes")
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(containsString("Batch"))
                .body(containsString("REST"))
                .body(not(containsString(">JPA<")));
    }

    @Test
    void testFilterByQuarkusNotSupported() {
        given()
            .queryParam("quarkus", "no")
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(containsString("JPA"))
                .body(not(containsString(">Batch<")))
                .body(not(containsString(">REST<")));
    }

    @Test
    void testFilterCombinedSpringYesQuarkusNo() {
        given()
            .queryParam("spring", "yes")
            .queryParam("quarkus", "no")
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(containsString("JPA"))
                .body(not(containsString(">Batch<")))
                .body(not(containsString(">REST<")));
    }

    @Test
    void testFilterNoMatch() {
        given()
            .queryParam("name", "nonexistent")
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(not(containsString(">Batch<")))
                .body(not(containsString(">REST<")))
                .body(not(containsString(">JPA<")));
    }
}
