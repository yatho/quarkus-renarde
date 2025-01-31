package io.quarkiverse.renarde.util;

import java.security.SecureRandom;
import java.util.Base64;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.core.multipart.FormData;
import org.jboss.resteasy.reactive.server.core.multipart.FormData.FormValue;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;

@Named("CRSF")
@RequestScoped
public class CRSF {

    @Inject
    HttpServerRequest request;

    private String crsfToken;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final static int CRSF_SIZE = 16;
    private final static String CRSF_COOKIE_NAME = "_renarde_crsf";
    private final static String CRSF_FORM_NAME = "_renarde_crsf_token";

    public void setCRSFCookie() {
        // FIXME: expiry, others?
        // in some cases with exception mappers, it appears the filters get invoked twice
        // FIXME: sometimes we seem to lose the flow and request scope, leading to calls like:
        /*
         * Reading CRSF cookie
         * Existing cookie: MEJBRQDw9Y8FGOmEG1vItA==
         * Saving CRSF cookie: MEJBRQDw9Y8FGOmEG1vItA==
         * Saving CRSF cookie: null
         */
        if (!request.response().headWritten() && crsfToken != null)
            request.response().addCookie(
                    Cookie.cookie(CRSF_COOKIE_NAME, crsfToken).setPath("/"));
    }

    public void readCRSFCookie() {
        Cookie cookie = request.getCookie(CRSF_COOKIE_NAME);
        if (cookie != null) {
            crsfToken = cookie.getValue();
        } else {
            byte[] bytes = new byte[CRSF_SIZE];
            SECURE_RANDOM.nextBytes(bytes);
            crsfToken = Base64.getEncoder().encodeToString(bytes);
        }
    }

    public void checkCRSFToken() {
        CurrentVertxRequest currentVertxRequest = CDI.current().select(CurrentVertxRequest.class).get();
        ResteasyReactiveRequestContext rrContext = (ResteasyReactiveRequestContext) currentVertxRequest
                .getOtherHttpContextObject();
        FormData formData = rrContext.getFormData();
        String formToken = null;
        // FIXME: we could allow checks for query params
        if (formData != null) {
            FormValue value = formData.getFirst(CRSF_FORM_NAME);
            formToken = value != null ? value.getValue() : null;
        }
        if (formToken == null || !formToken.equals(crsfToken)) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("Invalid or missing CRSF Token").build());
        }
    }

    // For views
    public String formName() {
        return CRSF_FORM_NAME;
    }

    // For views
    public String token() {
        return crsfToken;
    }
}
