package session.jwt;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.softwaremill.session.JwtSessionEncoder;
import com.softwaremill.session.OneOff;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;
import com.softwaremill.session.SetSessionTransport;
import com.softwaremill.session.javadsl.HttpSessionAwareDirectives;
import com.softwaremill.session.javadsl.JwtSessionSerializers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import static com.softwaremill.session.javadsl.SessionTransports.HeaderST;


public class JwtEncodedSession extends HttpSessionAwareDirectives<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtEncodedSession.class);
    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";

    // ***************************************************** //
    // This is where the Session Encoder is set to JWT //
    // ***************************************************** //
    private static final SessionEncoder<String> JWT_ENCODER = new JwtSessionEncoder<>(JwtSessionSerializers.StringToJValueSessionSerializer, JwtSessionSerializers.DefaultUtcDateFormat);

    private OneOff<String> oneOffSession;
    private SetSessionTransport sessionTransport;

    private JwtEncodedSession() {
        super(new SessionManager<>(
                SessionConfig.defaultConfig(SECRET),
                JWT_ENCODER
            )
        );
        oneOffSession = new OneOff<>(getSessionManager());
        sessionTransport = HeaderST;
    }

    public static void main(String[] args) throws IOException {

        // ** akka-http boiler plate **
        ActorSystem system = ActorSystem.create("example");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);

        // ** akka-http-session setup **
        final JwtEncodedSession app = new JwtEncodedSession();

        // ** akka-http boiler plate continued **
        final Flow<HttpRequest, HttpResponse, NotUsed> routes = app.createRoutes().flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routes, ConnectHttp.toHost("localhost", 8080), materializer);

        System.out.println("Server started, press enter to stop");
        System.in.read();

        binding
            .thenCompose(ServerBinding::unbind)
            .thenAccept(unbound -> system.terminate());
    }

    private Route createRoutes() {
        return
            route(
                pathPrefix("api", () ->
                    route(
                        path("do_login", () ->
                            post(() ->
                                entity(Unmarshaller.entityToString(), body -> {
                                        LOGGER.info("Logging in {}", body);
                                        return setSession(oneOffSession, sessionTransport, body, () ->
                                            extractRequestContext(ctx ->
                                                onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                    complete("ok")
                                                )
                                            )
                                        );
                                    }
                                )
                            )
                        ),

                        // This should be protected and accessible only when logged in
                        path("do_logout", () ->
                            post(() ->
                                requiredSession(oneOffSession, sessionTransport, session ->
                                    invalidateSession(oneOffSession, sessionTransport, () ->
                                        extractRequestContext(ctx -> {
                                                LOGGER.info("Logging out {}", session);
                                                return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                    complete("ok")
                                                );
                                            }
                                        )
                                    )
                                )
                            )
                        ),

                        // This should be protected and accessible only when logged in
                        path("current_login", () ->
                            get(() ->
                                requiredSession(oneOffSession, sessionTransport, session ->
                                    extractRequestContext(ctx -> {
                                            LOGGER.info("Current session: " + session);
                                            return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                complete(session)
                                            );
                                        }
                                    )
                                )
                            )
                        )
                    )
                ),
                pathPrefix("site", () ->
                    getFromResourceDirectory(""))
            );
    }
}