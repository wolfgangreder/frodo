package at.or.reder.frodo.api;

import at.or.reder.frodo.FrodoVersion;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Tag(name = "Frodo API", description = "Frodo server endpoints")
public class FrodoResource {

    @Inject
    FrodoVersion frodoVersion;

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get application info", description = "Returns basic application information")
    public ApplicationInfo info() {
        return new ApplicationInfo("frodo", frodoVersion.getVersion(), "Frodo Quarkus Server");
    }

    public record ApplicationInfo(String name, String version, String description) {
    }
}
