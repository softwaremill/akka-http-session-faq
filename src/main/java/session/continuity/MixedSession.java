package session.continuity;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.softwaremill.session.BasicSessionEncoder;
import com.softwaremill.session.OneOff;
import com.softwaremill.session.RefreshTokenStorage;
import com.softwaremill.session.Refreshable;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;
import com.softwaremill.session.SetSessionTransport;
import com.softwaremill.session.javadsl.HttpSessionAwareDirectives;
import com.softwaremill.session.javadsl.InMemoryRefreshTokenStorage;
import com.softwaremill.session.javadsl.SessionSerializers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static com.softwaremill.session.javadsl.SessionTransports.HeaderST;


public class MixedSession extends HttpSessionAwareDirectives<Map<String, String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MixedSession.class);
    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";
    private static final SessionEncoder<Map<String, String>> BASIC_ENCODER = new BasicSessionEncoder<>(SessionSerializers.MapToStringSessionSerializer);

    // ************************************************************ //
    // This is where the refresh token in-memory storage is defined //
    // ************************************************************ //
    private static final RefreshTokenStorage<Map<String, String>> REFRESH_TOKEN_STORAGE = new InMemoryRefreshTokenStorage<Map<String, String>>() {
        @Override
        public void log(String msg) {
            LOGGER.info(msg);
        }
    };

    private Refreshable<Map<String, String>> refreshableSession;
    private OneOff<Map<String, String>> oneOffSession;
    private SetSessionTransport sessionTransport;

    private MixedSession(MessageDispatcher dispatcher) {
        super(new SessionManager<>(
                SessionConfig.defaultConfig(SECRET),
                BASIC_ENCODER
            )
        );
        // ********************************************************** //
        // TODO This is where the Session continuity is set to Refreshable //
        // ********************************************************** //
        oneOffSession = new OneOff<>(getSessionManager());
        refreshableSession = new Refreshable<>(
            getSessionManager(),
            REFRESH_TOKEN_STORAGE,
            dispatcher
            );
        sessionTransport = HeaderST;
    }

    public static void main(String[] args) throws IOException {

        // ** akka-http boiler plate **
        ActorSystem system = ActorSystem.create("example");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);

        // ** akka-http-session setup **
        final MixedSession app = new MixedSession(system.dispatchers().lookup("akka.actor.default-dispatcher"));

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
                route(
                    pathPrefix("api", () ->
                        route(
                            path("do_login", () ->
                                post(() ->
                                    entity(Unmarshaller.entityToString(), body -> {
                                            LOGGER.info("Logging in {}", body);
                                            return setSession(refreshableSession, sessionTransport, createSessionFrom(body), () ->
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
                                    requiredSession(refreshableSession, sessionTransport, session ->
                                        invalidateSession(refreshableSession, sessionTransport, () ->
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
                                    requiredSession(refreshableSession, sessionTransport, session ->
                                        extractRequestContext(ctx -> {
                                                LOGGER.info("Current session: " + session);
                                                session.put("new", "false");
                                                return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                    complete(session.get("value").join(":", session.get("new")))
                                                );
                                            }
                                        )
                                    )
                                )
                            ),

                            // This should be protected and prevent refreshed sessions from access
                            path("admin_area", () ->
                                session(refreshableSession, sessionTransport, sessionResult -> {
                                    LOGGER.error(sessionResult.toString());
                                return get(() ->
                                    requiredSession(oneOffSession, sessionTransport, session ->
                                        extractRequestContext(ctx -> {
                                                LOGGER.info("Admin area: " + session);
                                                return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                    complete(session.get("value").join(":", session.get("new")))
                                                );
                                            }
                                        )
                                    )
                                );
    })
                            )
                        )
                    )
                )
            );
    }

    private Map<String, String> createSessionFrom(String body) {
        Map<String, String> result = new HashMap<>();
        result.put("new", "true");
        result.put("value", body);
        return result;
    }
}