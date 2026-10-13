package com.mokapotlabs.invoiceassistant.web;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** A browser session for the attended local operator, confined to the loopback host. */
@ApplicationScoped
public class LocalPayloadAuthentication implements HttpAuthenticationMechanism {
    static final String PRINCIPAL = System.getProperty("user.name");
    private static final String COOKIE = "invoice-local-session";
    private final String token;

    public LocalPayloadAuthentication() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager providers) {
        String host = context.request().authority().host();
        if (!(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]"))
                || !context.request().remoteAddress().isInetSocket()
                || !java.util.Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
                        .contains(context.request().remoteAddress().host())) {
            return Uni.createFrom().nullItem();
        }
        String origin = context.request().getHeader("Origin");
        if (origin != null
                && !origin.equals(
                        context.request().scheme() + "://" + context.request().authority())) {
            return Uni.createFrom().nullItem();
        }
        Cookie cookie = context.request().getCookie(COOKIE);
        boolean valid = cookie != null
                && MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.US_ASCII),
                        cookie.getValue().getBytes(StandardCharsets.US_ASCII));
        if (!valid
                && context.request().method() == io.vertx.core.http.HttpMethod.GET
                && java.util.Set.of("/", "/setup", "/settings", "/outlook").contains(context.normalizedPath())
                && !"cross-site".equals(context.request().getHeader("Sec-Fetch-Site"))) {
            context.response()
                    .addCookie(Cookie.cookie(COOKIE, token)
                            .setPath("/")
                            .setHttpOnly(true)
                            .setSameSite(CookieSameSite.STRICT));
            valid = true;
        }
        if (!valid) return Uni.createFrom().nullItem();
        return Uni.createFrom()
                .item(QuarkusSecurityIdentity.builder()
                        .setPrincipal(() -> PRINCIPAL)
                        .addRole("local-operator")
                        .build());
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401));
    }
}
