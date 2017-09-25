package session.data_types;

import com.softwaremill.session.SessionSerializer;
import com.softwaremill.session.SingleValueSessionSerializer;
import com.softwaremill.session.javadsl.SessionSerializers;
import scala.compat.java8.JFunction0;
import scala.compat.java8.JFunction1;
import scala.util.Try;

public class CustomType {

    /**
     * This session serializer converts a session type into a value (always a String type). The first two arguments are just conversion functions.
     * The third argument is a serializer responsible for preparing the data to be sent/received over the wire. There are some ready-to-use serializers available
     * in the com.softwaremill.session.SessionSerializer companion object, like stringToString and mapToString, just to name a few.
     */
    private static final SessionSerializer<CustomType, String> customTypeSerializer = new SingleValueSessionSerializer<>(
        // transform CustomType into String ("myString,myInt")
        (JFunction1<CustomType, String>) (session) -> (session.getMyString().concat(",").concat(session.getMyInt().toString()))
        ,
        // transform String into CustomType
        (JFunction1<String, Try<CustomType>>) (body) -> Try.apply((JFunction0<CustomType>) (() ->
            new CustomType(body.split(",")[0], Integer.valueOf(body.split(",")[1]))
        ))
        ,
        SessionSerializers.StringToStringSessionSerializer
    );

    private final String myString;
    private final Integer myInt;

    public CustomType(String myString, Integer myInt) {
        this.myString = myString;
        this.myInt = myInt;
    }

    public static SessionSerializer<CustomType, String> getSerializer() {
        return customTypeSerializer;
    }

    public String getMyString() {
        return myString;
    }

    public Integer getMyInt() {
        return myInt;
    }
}
