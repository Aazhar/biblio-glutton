package com.scienceminer.lookup.web;


import com.google.common.collect.Lists;
import com.google.inject.Module;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import com.scienceminer.lookup.command.*;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.utils.grobid.GrobidClient;
import com.scienceminer.lookup.web.healthcheck.LookupHealthCheck;
import com.scienceminer.lookup.web.module.LookupServiceModule;
import com.scienceminer.lookup.web.module.NotFoundExceptionMapper;
import com.scienceminer.lookup.web.module.ServiceExceptionMapper;
import com.scienceminer.lookup.web.module.ServiceOverloadedExceptionMapper;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.HttpClient;

import java.util.List;

public final class LookupServiceApplication extends Application<LookupConfiguration> {
    private static final String RESOURCES = "/service";

    // ========== Application ==========
    @Override
    public String getName() {
        return "lookup-service";
    }

    @Override
    public void run(LookupConfiguration configuration, Environment environment) throws Exception {

        environment.jersey().setUrlPattern(RESOURCES + "/*");
        environment.jersey().register(new ServiceExceptionMapper());
        environment.jersey().register(new NotFoundExceptionMapper());
        environment.jersey().register(new ServiceOverloadedExceptionMapper());

        final LookupHealthCheck healthCheck = new LookupHealthCheck(configuration);
        environment.healthChecks().register("HealthCheck", healthCheck);
    }

    private List<? extends Module> getGuiceModules() {
        return Lists.newArrayList(new LookupServiceModule());
    }


    @Override
    public void initialize(Bootstrap<LookupConfiguration> bootstrap) {
        GuiceBundle<LookupConfiguration> guiceBundle = GuiceBundle.defaultBuilder(LookupConfiguration.class)
                .modules(getGuiceModules())
                .build();
        bootstrap.addBundle(new AssetsBundle("/assets/", "/", "index.html", "static"));
        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.addCommand(new LoadUnpayWallCommand());
        bootstrap.addCommand(new LoadIstexIdsCommand());
        bootstrap.addCommand(new LoadPMIDCommand());
        bootstrap.addCommand(new LoadCrossrefCommand());
        bootstrap.addCommand(new IndexCrossrefCommand());
        bootstrap.addCommand(new UpdateCrossrefCommand());
    }

    // ========== static ==========
    public static void main(String... args) throws Exception {
        new LookupServiceApplication().run(args);
    }
}
