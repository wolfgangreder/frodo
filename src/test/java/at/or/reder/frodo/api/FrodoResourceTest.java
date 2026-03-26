package at.or.reder.frodo.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class FrodoResourceTest {

    @Test
    void testInfoEndpoint() {
        given()
                .when().get("/api/info")
                .then()
                .statusCode(200)
                .body("name", is("frodo"))
                .body("version", notNullValue())
                .body("description", notNullValue());
    }
}
