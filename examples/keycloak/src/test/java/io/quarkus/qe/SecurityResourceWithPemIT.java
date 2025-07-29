package io.quarkus.qe;

import io.quarkus.test.bootstrap.RestService;
import io.quarkus.test.scenarios.QuarkusScenario;
import io.quarkus.test.services.QuarkusApplication;

@QuarkusScenario
public class SecurityResourceWithPemIT extends BaseSecurityResourceWithPemIT {

    @QuarkusApplication
    static final RestService app = new RestService()
            .withProperty("quarkus.oidc.auth-server-url", keycloak::getRealmUrl)
            .withProperty("quarkus.oidc.client-id", CLIENT_ID_DEFAULT)
            .withProperty("quarkus.oidc.credentials.secret", CLIENT_SECRET_DEFAULT)
            .withProperty("quarkus.oidc.tls.tls-configuration-name", "oidc")
            .withProperty("quarkus.tls.oidc.trust-store.pem.certs", keycloak::getTrustStore);

    @Override
    public RestService getApp() {
        return app;
    }
}
