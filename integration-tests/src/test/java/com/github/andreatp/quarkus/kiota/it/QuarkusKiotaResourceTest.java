package com.github.andreatp.quarkus.kiota.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apisdk.ApiClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

@QuarkusTest
public class QuarkusKiotaResourceTest {

    @Test
    public void testHelloEndpoint() {
        given().when().get("/quarkus-kiota").then().statusCode(200).body(is("Hello quarkus-kiota"));
    }

    @Test
    public void testHelloEndpointUsingTheKiotaClient() throws Exception {
        // Arrange
        RequestAdapter adapter = new OkHttpRequestAdapter(new AnonymousAuthenticationProvider());
        adapter.setBaseUrl("http://localhost:8081");
        ApiClient client = new ApiClient(adapter);

        // Act
        String result = client.quarkusKiota().get().get(3, TimeUnit.SECONDS);

        // Assert
        assert result.equals("Hello quarkus-kiota1");
    }
}
