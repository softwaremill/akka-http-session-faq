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
import java.util.concurrent.CompletionStage;

import static com.softwaremill.session.javadsl.SessionTransports.HeaderST;


public class LongTypeSession extends HttpSessionAwareDirectives<Long> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LongTypeSession.class);
    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";

    // ***************************************************** //
    // This is where the Session Data Type is set to Long //
    // ***************************************************** //
    private static final SessionEncoder<Long> BASIC_ENCODER = new BasicSessionEncoder<>(SessionSerializers.LongToStringSessionSerializer);

    private OneOff<Long> oneOffSession;
    private SetSessionTransport sessionTransport;

    private LongTypeSession() {
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
        final LongTypeSession app = new LongTypeSession();

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
                                        return setSession(oneOffSession, sessionTransport, Long.valueOf(body), () ->
                                            extractRequestContext(ctx ->
                                                onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                    complete("ok")
                                                )
                                            )
                                        );
                                    }
                                )
                            )
                        )
                    )
                )
            );
    }
}