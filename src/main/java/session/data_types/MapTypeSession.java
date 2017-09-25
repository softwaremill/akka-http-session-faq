package session.data_types;

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
import com.softwaremill.session.BasicSessionEncoder;
import com.softwaremill.session.OneOff;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;
import com.softwaremill.session.SetSessionTransport;
import com.softwaremill.session.javadsl.HttpSessionAwareDirectives;
import com.softwaremill.session.javadsl.SessionSerializers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static com.softwaremill.session.javadsl.SessionTransports.HeaderST;


public class MapTypeSession extends HttpSessionAwareDirectives<Map<String, String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapTypeSession.class);
    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";

    // ***************************************************************** //
    // This is where the Session Data Type is set to Map<String, String> //
    // ***************************************************************** //
    private static final SessionEncoder<Map<String, String>> BASIC_ENCODER = new BasicSessionEncoder<>(SessionSerializers.MapToStringSessionSerializer);

    private OneOff<Map<String, String>> oneOffSession;
    private SetSessionTransport sessionTransport;

    private MapTypeSession() {
        super(new SessionManager<>(
                SessionConfig.defaultConfig(SECRET),
                BASIC_ENCODER
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
        final MapTypeSession app = new MapTypeSession();

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
                                        return setSession(oneOffSession, sessionTransport, stringToMap(body), () ->
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
                        path("current_login", () ->
                            get(() ->
                                requiredSession(oneOffSession, sessionTransport, session ->
                                    extractRequestContext(ctx -> {
                                            LOGGER.info("Current session: " + session);
                                            return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                complete(session.get("key1"))
                                            );
                                        }
                                    )
                                )
                            )
                        )
                    )
                )
            );
    }

    /**
     * This helper method converts a session String into a Map.
     * The format of the string is:
     * key1,value1:key2,value2:key3,value3
     */
    private Map<String, String> stringToMap(String body) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String tuple : body.split(":")) {
            result.put(tuple.split(",")[0], tuple.split(",")[1]);
        }
        return result;
    }
}