package io.snowdrop.cartography.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestRegistryResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistryCrudTest {

    private static String createdId;

    @Test
    @Order(1)
    void testListCapabilities() {
        given()
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(containsString("Batch"))
                .body(containsString("REST"))
                .body(containsString("JPA"));
    }

    @Test
    @Order(2)
    void testCreateCapability() {
        String entriesJson = "[{\"framework\":\"Spring\",\"name\":\"spring-security\","
                + "\"type\":\"STARTER\",\"description\":\"Spring Security framework\"}]";

        Response response = given()
            .redirects().follow(false)
            .formParam("category", "Security")
            .formParam("description", "Authentication and authorization")
            .formParam("tags", "security, auth")
            .formParam("entriesJson", entriesJson)
            .when().post("/registry");

        response.then().statusCode(303);

        String location = response.header("Location");
        assertNotNull(location);
        assertTrue(location.matches(".*/registry/\\d+/edit"));
        createdId = location.replaceAll(".*/registry/(\\d+)/edit", "$1");
    }

    @Test
    @Order(3)
    void testEditFormShowsCreatedCapability() {
        given()
            .when().get("/registry/" + createdId + "/edit")
            .then()
                .statusCode(200)
                .body(containsString("Security"))
                .body(containsString("spring-security"));
    }

    @Test
    @Order(4)
    void testUpdateCapability() {
        String entriesJson = "[{\"framework\":\"Spring\",\"name\":\"spring-security\","
                + "\"type\":\"STARTER\",\"description\":\"Updated description\"},"
                + "{\"framework\":\"Quarkus\",\"name\":\"quarkus-security\","
                + "\"type\":\"EXTENSION\",\"description\":\"Quarkus Security\"}]";

        given()
            .redirects().follow(false)
            .formParam("category", "Security")
            .formParam("description", "Updated auth description")
            .formParam("tags", "security, auth, oauth")
            .formParam("reviewBy", "Test User")
            .formParam("reviewDate", "2026-07-17")
            .formParam("quarkusStatus", "in progress")
            .formParam("statusComment", "Work started")
            .formParam("entriesJson", entriesJson)
            .when().post("/registry/" + createdId)
            .then()
                .statusCode(303);

        given()
            .when().get("/registry/" + createdId + "/edit")
            .then()
                .statusCode(200)
                .body(containsString("Updated auth description"))
                .body(containsString("quarkus-security"));
    }

    @Test
    @Order(5)
    void testUpdateQuarkusStatus() {
        given()
            .formParam("status", "done")
            .when().post("/registry/" + createdId + "/quarkus-status")
            .then()
                .statusCode(200)
                .body("quarkusStatus", is("done"));
    }

    @Test
    @Order(6)
    void testDeleteCapability() {
        given()
            .redirects().follow(false)
            .when().post("/registry/" + createdId + "/delete")
            .then()
                .statusCode(303);

        given()
            .when().get("/registry")
            .then()
                .statusCode(200)
                .body(not(containsString(">Security<")));
    }
}
