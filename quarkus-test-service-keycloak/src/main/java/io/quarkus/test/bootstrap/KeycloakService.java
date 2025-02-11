package io.quarkus.test.bootstrap;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;

import io.smallrye.certs.CertificateFiles;
import io.smallrye.certs.CertificateGenerator;
import io.smallrye.certs.CertificateRequest;
import io.smallrye.certs.Format;
import io.smallrye.certs.JksCertificateFiles;

public class KeycloakService extends BaseService<KeycloakService> {

    public static final String DEFAULT_REALM_BASE_PATH = "/realms";
    public static final String DEFAULT_REALM = "test-realm";
    // The convention for importing realm is to have file name like `<realm-name>-realm.json`
    public static final String DEFAULT_REALM_FILE = "/test-realm-realm.json";
    private static final String REALM_DEST_PATH = "/opt/keycloak/data/import";
    private static final String KEYSTORE_DEST_PATH = "/opt/keycloak/conf/";
    private static final String USER = "admin";
    private static final String PASSWORD = "admin";
    private static final String KEYSTORE_PREFIX = "server";
    private static final Format KEYSTORE_FORMAT = Format.PKCS12;
    private static final String KEYSTORE_PASSWORD = "secret";
    private static final int HTTP_80 = 80;

    private String realmBasePath = "realms";
    private final String realm;

    private boolean runKeycloakInProdMode = false;

    /**
     * KeycloakService constructor, supported since Keycloak 18.
     *
     * @param realmFile for example /test-realm-realm.json
     * @param realmName
     * @param realmBasePath such as "/realms" used by Keycloak 18 or "auth/realms" used by previous versions
     * @param runKeycloakInProdMode the prod mode needs to setup certificate and use https protocol
     */
    public KeycloakService(String realmFile, String realmName, String realmBasePath, boolean runKeycloakInProdMode) {
        this(realmName);
        this.realmBasePath = normalizeRealmBasePath(realmBasePath);
        this.runKeycloakInProdMode = runKeycloakInProdMode;
        withProperty("KEYCLOAK_REALM_IMPORT", "resource_with_destination::" + REALM_DEST_PATH + "|" + realmFile);
        if (runKeycloakInProdMode) {
            String keystoreName;
            try {
                CertificateRequest request = new CertificateRequest()
                        .withName(KEYSTORE_PREFIX)
                        .withPassword(KEYSTORE_PASSWORD)
                        .withFormat(KEYSTORE_FORMAT);
                List<CertificateFiles> certificateFiles = new CertificateGenerator(Path.of("target", "test-classes"),
                        true)
                        .generate(request);
                keystoreName = ((JksCertificateFiles) certificateFiles.get(0)).keyStoreFile().getFileName().toString();

            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            withProperty("KC_HTTPS_CERTIFICATE_FILE", "secret_with_destination::" + KEYSTORE_DEST_PATH + "|" + keystoreName);
            withProperty("KC_HTTPS_CERTIFICATE_KEY_FILE", "secret_with_destination::" + KEYSTORE_DEST_PATH + "|"
                    + keystoreName);
            withProperty("KC_HTTPS_KEY_STORE_PASSWORD", KEYSTORE_PASSWORD);
        }
    }

    /**
     * KeycloakService constructor, supported since Keycloak 18.
     *
     * @param realmFile for example /test-realm-realm.json
     * @param realmName
     * @param realmBasePath such as "/realms" used by Keycloak 18 or "auth/realms" used by previous versions
     */
    public KeycloakService(String realmFile, String realmName, String realmBasePath) {
        this(realmFile, realmName, realmBasePath, false);
    }

    public KeycloakService(String realmName) {
        this.realm = realmName;
        withProperty("KC_BOOTSTRAP_ADMIN_USERNAME", USER);
        withProperty("KC_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD);
        // TODO drop next variables as they were deprecated in KC 26 (possibly when we move to KC 28+)
        withProperty("KEYCLOAK_ADMIN", USER);
        withProperty("KEYCLOAK_ADMIN_PASSWORD", PASSWORD);
    }

    public String getRealmUrl() {
        var host = runKeycloakInProdMode ? getURI(Protocol.HTTPS) : getURI(Protocol.HTTP);

        // SMELL: Keycloak does not validate Token Issuers when URL contains the port 80.
        int port = host.getPort();
        if (port == HTTP_80) {
            port = -1;
        }
        try {
            URI url = new URI(host.getScheme(),
                    host.getUserInfo(),
                    host.getHost(),
                    port,
                    "/" + realmBasePath + "/" + realm,
                    null,
                    null);
            return url.toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public AuthzClient createAuthzClient(String clientId, String clientSecret) {
        SSLConnectionSocketFactory sslConnectionSocketFactory;
        try {
            TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
            sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalStateException("Unable to create SSLConnectionSocketFactory to allow"
                    + " secured connection use self-signed certificates", e);
        }
        return AuthzClient.create(new Configuration(
                StringUtils.substringBefore(getRealmUrl(), "/realms"),
                realm,
                clientId,
                Collections.singletonMap("secret", clientSecret),
                HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build()));
    }

    private String normalizeRealmBasePath(String realmBasePath) {
        if (realmBasePath.startsWith("/")) {
            realmBasePath = realmBasePath.substring(1);
        }

        if (realmBasePath.endsWith("/")) {
            realmBasePath = realmBasePath.substring(0, realmBasePath.length() - 1);
        }

        return realmBasePath;
    }
}
