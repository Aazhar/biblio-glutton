package web.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import storage.StorageLMDB;
import web.configuration.GCConfiguration;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;


@Path("health")
@Singleton
@Produces("application/json;charset=UTF-8")
public class GCHealthCheck extends HealthCheck {

    @Inject
    private GCConfiguration configuration;

    @Inject
    public GCHealthCheck() {
    }

    @GET
    public Response alive() {
        return Response.ok().build();
    }

    @Override
    protected Result check() throws Exception {
        return Result.healthy();
    }
}