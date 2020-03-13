## The what's and when's

### What is `akka-http-session`
It is a set of `akka-http` directives, that are used when building Akka HTTP Routes.
They intercept an HTTP request to search for a particular Cookie or Header to extract data, a.k.a. the session.  

### When do I need `akka-http-session`?
The directives are useful in web applications to send a particular type of data to clients. 
They prevent the request and response bodies to be mixed with session data, happening with every HTTP call.  
 
### What typical use cases does `akka-http-session` solve?
One goal is to login with a stateless protocol (HTTP) and retrieve data through subsequent calls without re-authenticating each one.

The client logs in and receives a token. 
This token is used by the client to make further requests. 
It is the client's responsibility to invalidate this token, once it is no longer needed. 
Typically this is the case, when a user logs out.

An additional feature is the possibility to send data as part of a session. 
To prevent this data to be forged, it is signed by the server. 
The signature is appended to the data what prevents the client, or an attacker, to modify any piece of it. 

### What are the limits?
A session should be kept small and simple. 
`akka-http-session` does not allow for nested objects inside sessions. 
Another hard limit is the size of a session: for Cookies it is restricted to 4kB minus 50B for the server signature appended to the session's data.

## Session Data
### What type of data can be sent in a session?
As long as you provide a serializer for your custom types, you can use them.
Standard types like `String`, `Integer`, `Long`, `Double` and `Map<String, String>` are supported out of the box.

[Here's an example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/data_types/StringTypeSession.java) with a `String` data type session:
```
$ curl -i --data "a string type"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: 97674A1F8902A2C6FF18D62D4782508428BE1FD2-1505457571316-xa+string+type
Server: akka-http/10.0.9
Date: Fri, 15 Sep 2017 06:34:31 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
[Here's an example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/data_types/LongTypeSession.java) with a `Long` data type session:
```
$ curl -i --data "12321"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: E387BECF0D28B42ADF67762AAFE31FA554511D48-1505458157589-x12321
Server: akka-http/10.0.9
Date: Fri, 15 Sep 2017 06:44:17 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
[Here's an example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/data_types/MapTypeSession.java) with a `Map<String, String>` data type session:
```
$ curl -i --data "key1,value1:k2,v2"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: D69B68453648EA25DB6A49F459DC7112D766B5B6-1505987538087-xkey1%3Dvalue1%26k2%3Dv2
Server: akka-http/10.0.9
Date: Thu, 21 Sep 2017 09:47:18 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok

$ curl -i -H "Authorization: D69B68453648EA25DB6A49F459DC7112D766B5B6-1505987538087-xkey1%3Dvalue1%26k2%3Dv2" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Thu, 21 Sep 2017 09:47:44 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 6

value1
```

[Here's an example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/data_types/CustomTypeSession.java) with a `CustomType` data type session:
```
$ curl -i --data "my_login,42"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: 1D09A45EDCF4E4060EB88379EB27ABF619FA7C97-1505467994695-xmy_login%2C42
Server: akka-http/10.0.9
Date: Fri, 15 Sep 2017 09:28:14 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```

## Session Transport
### How can I transport the session between server and client?
Two transport types are available: Cookies and Headers.

### <a name="cookies"></a>Why would I use Cookies?
Using Cookies to send session data has the advantage that it is handled automatically by client applications, like a web browser. 
Also Cookies do not require you to implement a storage, since it's built-in into the browser already.

The [Cookie transport example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/transport/CookieTransport.java) shows a typical setup for Cookies. Below is a sample use case:

```
$ curl -i http://localhost:8080/api/current_login

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 07:33:10 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```
As expected, the secured resource is not available. We need to log in first.
``` 
$ curl -i --data "my_login"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Cookie: _sessiondata=625617AD3A82A95149B2DAAA6B4444F633F298E5-1505374699373-xmy_login; Path=/; HttpOnly
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 07:33:19 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
The response tells us to set the `_sessiondata` Cookie.
```
$ curl -i --cookie "_sessiondata=625617AD3A82A95149B2DAAA6B4444F633F298E5-1505374699373-xmy_login" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 07:34:13 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```
### <a name="headers"></a>Why would I use Headers?

Headers are usually used in a non-Cookie world, like mobile. 
You need to come up with a storage for the session data on your client application by yourself. 
This is what browsers do for you when dealing with Cookies.  
Additionally when using refresh tokens, they need to be persisted in that storage as well.

Take a look at [the Header transport example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/transport/HeaderTransport.java) and how it works:

```
$ curl -i http://localhost:8080/api/current_login

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 06:34:49 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```
As expected, the secured resource is not available. Let's login first and get an authorization header:

```
$ curl -i --data "my_login"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: 66B4954117229C7E98710B84384DF0ED075B0AC7-1505371227619-xmy_login
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 06:35:27 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
Now, knowing what value we should set the Authorization header, we can access the secured resource:
```
$ curl -i -H "Authorization: 66B4954117229C7E98710B84384DF0ED075B0AC7-1505371227619-xmy_login" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 06:35:57 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```

### <a name="max-age"></a>How long does a session live?
By default a session expires after 1 week, configurable via the `akka.http.session.max-age` config property.
[In this example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/resources/application.conf) it is set to 5 minutes. 
When running the [HeaderTransport example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/transport/HeaderTransport.java) we get:
```
$ curl -i --data "my_login"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: A9A55BB36713C9CAF020522D88D2FB60F070C5FE-1505470200180-xmy_login
Server: akka-http/10.0.9
Date: Fri, 15 Sep 2017 10:05:00 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
The timestamp attached to the session is `1505470200180` and translates to `Fri Sep 15 2017 10:10:00` which is 5 minutes ahead of the time, when the request was sent `Fri, 15 Sep 2017 10:05:00 GMT`.

## <a name="refreshable"> Session Continuity
### What type of sessions are available
There is the `OneOff` session which when expired is of no use anymore.
An alternative is the `Refreshable` session.
It will provide the client with an additional `_refreshtoken` Cookie or `Set-Refresh-Token` Header, dependent on the transport type.
The [RefreshableSession](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/continuity/RefreshableSession.java) example shows how to use this type of sessions:

```
$ curl -i --data "my_login_"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: 018BB450A1F98C3B6A2EFE147A72F5625B2B6196-1505914981440-xmy_login_
Set-Refresh-Token: h015ti5a3mpdi15g:obv960hi3qic2km08bdnmuq4g6auo9elnhga7igs4p5inm0ep5miaih4sbfkijnq
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 13:38:01 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
This session will expire on `1505914981440` - `Wed Sep 20 2017 13:43:01`:
```
$ curl -i -H "Authorization: 018BB450A1F98C3B6A2EFE147A72F5625B2B6196-1505914981440-xmy_login_" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 13:42:38 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 9

my_login_

$ curl -i -H "Authorization: 018BB450A1F98C3B6A2EFE147A72F5625B2B6196-1505914981440-xmy_login_" http://localhost:8080/api/current_login

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 13:45:09 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```
Now we can use the refresh token to renew the session and receive a new refresh token:
```
$ curl -i -H "Refresh-Token: h015ti5a3mpdi15g:obv960hi3qic2km08bdnmuq4g6auo9elnhga7igs4p5inm0ep5miaih4sbfkijnq" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Set-Authorization: E857CEB406BE7F7C695B0E0822417100C2F6D252-1505915599565-xmy_login_
Set-Refresh-Token: oi74uleiqqcutb7j:r5ebs27dbb3q89h6g2bvcpma9sltpad482tebah8doq7nlh48525rgu8511gbplv
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 13:48:19 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 9

my_login_
```

### When do I need refreshable sessions?
A refreshable session is typically used for "remember-me" functionality. 
This is especially useful in mobile applications, where you log in once, and the session is remembered for a long time. 
Make sure to adjust the `akka.http.session.refresh-token.max-age` config property in [application.conf](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/resources/application.conf) which defaults to 1 month.
Also, these sessions are persisted. 
Although the default implementation stores the refresh tokens in-memory and they won't survive a server restart, 
they still can be thought of a way of storing session details on the server side.

### Can I use an expired session?
No. Once a session is expired, it cannot be used. 
The refresh token instructs the server to create a new session.
Also, when sending a refresh token to renew a session, a new refresh token is issued as well.
Once a refresh token has been submitted to the server it cannot be used a second time:
```
$ curl -i -H "Refresh-Token: oi74uleiqqcutb7j:r5ebs27dbb3q89h6g2bvcpma9sltpad482tebah8doq7nlh48525rgu8511gbplv" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Set-Authorization: 898CFBD951ED19EFE2BBD65DF56436BD5678E75E-1505915676406-xmy_login_
Set-Refresh-Token: qfqipvl8i7ig1lpl:6bacamecnkin5m6f3hd0nd03qh0089isvl9cbb9ra6crgh0lc54sde2p6rrk2p64
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 13:49:36 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 9

my_login_

$ curl -i -H "Refresh-Token: oi74uleiqqcutb7j:r5ebs27dbb3q89h6g2bvcpma9sltpad482tebah8doq7nlh48525rgu8511gbplv" http://localhost:8080/api/current_login

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 13:49:39 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```

### Is the additional refresh token persistent?
Yes. Therefore using refreshable sessions requires you to implement a storage for these tokens.
An in-memory storage is provided as shown in the [RefreshableSession](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/continuity/RefreshableSession.java) example.
However using an in-memory database will invalidate all your refresh tokens when the server restarts.
 
In this example a refresh token is issued and before the second request is sent, the server is restarted:
```
$ curl -i --data "my_login_"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: E4D6AECD0E39B869949155868CB3E75756606AFE-1505916579385-xmy_login_
Set-Refresh-Token: lhl4r4rpf53idp3m:8b5tpiohsa58bcm1ajmiev4ja0p54e6qvqdcgbinqmp81agm95rlileo6s8ptiap
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 14:04:39 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok

$ curl -i -H "Refresh-Token: lhl4r4rpf53idp3m:8b5tpiohsa58bcm1ajmiev4ja0p54e6qvqdcgbinqmp81agm95rlileo6s8ptiap" http://localhost:8080/api/current_login

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Wed, 20 Sep 2017 14:05:04 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```
The refresh token is useless. The server is not able to find the refresh token in the storage:
```
2017-09-20 14:05:04 INFO  RefreshableSession:47 - Looking up token for selector: lhl4r4rpf53idp3m, found: false
```

### How do I enable refreshable sessions?
The `akka-http-session` [directives](#directives) require you to pass a session continuity type.
This can be either `OneOff` or `Refreshable`.

## Security
### Can a Cookie be stolen and be reused by an attacker?
Yes.

### <a name="secure-cookie"></a>How can I use Cookies in a secure way?
1. Use the `invalidateSession` directive when a user logs out or doesn't need that session any longer

This is demonstrated by the `do_logout` route in the [CookieTransport example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/transport/CookieTransport.java).
Its purpose is to send an empty `_sessiondata` Cookie to the client (typically a browser). 
In consequence, the browser should erase that Cookie to prevent an attacker to read the cookie later on.
```
$ curl -i -X POST --cookie "_sessiondata=625617AD3A82A95149B2DAAA6B4444F633F298E5-1505374699373-xmy_login" http://localhost:8080/api/do_logout

HTTP/1.1 200 OK
Set-Cookie: _sessiondata=deleted; Expires=Wed, 01 Jan 1800 00:00:00 GMT; Path=/; HttpOnly
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 07:34:46 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
The Cookie does no longer contain any session data.
It's still possible, that although the browser erased the Cookie, the attacker got it. Therefore:  

2. Use a sensible `max-age` value which defaults to 7 days

For use cases where it make sense, set the `max-age` property to a low value, for example `5 minutes`. 
This is especially true, when your application allows to access sensitive data, like bank accounts, emails, etc.
The `max-age` property is set in `application.conf`, like in [this example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/resources/application.conf).
Using a cookie which expired on `Thu Sep 14 2017 07:38:19` results in a `403` response, when used in a request at `07:39:43`:
``` 
$ curl -i --cookie "_sessiondata=625617AD3A82A95149B2DAAA6B4444F633F298E5-1505374699373-xmy_login" http://localhost:8080/api/current_login

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 07:39:43 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```
3. Secure your transfer protocol - use HTTPS.

4. Enable the `Secure` option for Cookies

The `Secure` attribute is explained in [RFC 6265](https://tools.ietf.org/html/rfc6265#section-4.1.2.5) in more detail. 
This does not prevent the server from sending the Cookie to the client. 
It's just a flag for the client. 
It prevents the browser from sending Cookies, if the request is not transmitted over HTTPS.
There may be 3 types of Cookies being used in `akka-http-session` and all need to have this option enabled explicitly:
`akka.http.session.cookie.secure`, `csrf.cookie.secure` and `refresh-token.cookie.secure`.

### <a name="invalidate-session"></a>Even when a session is invalidated by `invalidateSession`, is it still available?
Yes. 
The invalidation is just a way to inform the client, that it should erase the cookie.
There is no session maintenance going on on the server side.
This means, a Cookie representing an invalidated session can still be used:
``` 
$ curl -i --cookie "_sessiondata=625617AD3A82A95149B2DAAA6B4444F633F298E5-1505374699373-xmy_login" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 07:34:55 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```

### What happens if the Header / Cookie expires

The authorization Header / Cookie contains a timestamp. 
In the example below it is `1505371227619`, which translates to `Thu Sep 14 2017 06:40:27`.
``` 
$ curl -i -H "Authorization: 66B4954117229C7E98710B84384DF0ED075B0AC7-1505371227619-xmy_login" http://localhost:8080/api/current_login
HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 06:39:27 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```
As expected this one passed, since the authorization Header has not expired yet. 
The request below is from `Thu, 14 Sep 2017 06:43:01` and therefore should fail to access the resource: 
```
$ curl -i -H "Authorization: 66B4954117229C7E98710B84384DF0ED075B0AC7-1505371227619-xmy_login" http://localhost:8080/api/current_login
HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 06:43:01 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```

### Is it possible to renew a timed out authorization Header / Cookie?
When using `oneOffSessions`, just setting the timestamp to a future date won't have any effect. 
It is secured by the server by signing the Header / Cookie data. 
Here we set the timestamp to `Mon Sep 25 2017 20:27:07` and executed the request on `Thu, 14 Sep 2017 06:43:10`:
```
$ curl -i -H "Authorization: 66B4954117229C7E98710B84384DF0ED075B0AC7-1506371227619-xmy_login" http://localhost:8080/api/current_login
HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 06:43:10 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource 
```
To renew sessions, use the [Refreshable](#refreshable) transport.

### Is it reasonable to expire sessions after 1 week?
It depends ;)
A cookie may be stolen from a browser, when someone forgets to logout. 
In this case a more sensitive value may make sense. 
On the other hand, a mobile application may play well with a longer expiry value, provided the phone itself is secured from unauthorised access.
In such a use-case however, [Refresh Tokens](#refreshable) which are valid for 1 week by default, may make more sense.

### Is it possible to modify the session data on the client side?
No. The session is signed by the server and changes to the session's content are picked up and rejected.
In other words, it is not possible to login with valid credentials and then having a valid token pretend to be a different user, like in this example:

```
$ curl -i -H "Authorization: EAA15F51D825EFBCC1A2A0D43C65CFCA505F2497-1505383157632-xmy_login" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 09:54:37 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```
Trying to provide the server with a modified session (`my_login` is set to `a_different_login`) results in an error:

```
$ curl -i -H "Authorization: EAA15F51D825EFBCC1A2A0D43C65CFCA505F2497-1505383157632-xa_different_login" http://localhost:8080/api/current_login

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 09:54:41 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```

### What does encryption provide me with?
Enabling session data encryption allows to send data in a format that is not readable by the client.
To enable session data encryption set the `akka.http.session.encrypt-data` config property in `application.conf`, like in [this resource file](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/resources/application.conf).
As seen below, the client received a token, that can be used to access the secured resource, but the data itself is encrypted.

```
$ curl -i --data "my_login"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: 954814B09DA2583BB7AD9E37DFB69436E44111D8-EFDA5F903AA65EFFF7AC7C0DE31A7909A639C54E5332A0C24DDDE09D9B4384A4
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 10:04:13 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```

```
$ curl -i -H "Authorization: 954814B09DA2583BB7AD9E37DFB69436E44111D8-EFDA5F903AA65EFFF7AC7C0DE31A7909A639C54E5332A0C24DDDE09D9B4384A4" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Thu, 14 Sep 2017 10:06:09 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```

### Why would I encrypt session data?
Encryption is used to send data and receive it from the client for further processing but prevent the client from seeing it.
Typically the session contains a user id. 
You may not want to share it for various reasons, like when it is a sequential number revealing how many customers you have at least. 
Another example would be data that is expensive to fetch or require access to a paid API or a 3rd party call.
If such data has to be put in context with a particular user, therefore sent as part of the session data, and the client should not be able to read it, then encryption is the way to go.

## <a name="directives"></a> Session Directives
### What are these session directives exactly for?
Including `akka-http-session` directives into the route chain, you can require an endpoint to be accessible only, if a valid session is provided by the client.
Also there's a directive to initiate or invalidate a session when accessing a particular endpoint. 
This is especially useful when logging in or out.

### What is the `setSession` directive good for?
Adding this directive to a route chain allows you to initialize a session. 
Depending on the transport type, either a `Set-Authorization` header or a `Set-Cookie` header are set with a new session.
It is up to the client to read the appropriate Header and use the session in subsequent calls.
 
### <a name="session-directive"></a> What is the `session` directive good for?
This directive is responsible for extracting the session result to be used further on the server side.
A session result can be `Decoded` (valid), `CreatedFromToken`, `Expired`, `Corrupt` or having no token present `TokenNotFound`
The directive does not require the client to provide a session.
However if a session is present, a result is made available to the server for further processing.
[This example](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/directives/SessionDirective.java) shows two possibilities, either there is no session at all, or the session is present.
```
$ curl -i --data "session_details"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: D3A3AC31E69D96A41DA482198B367364AEFDFEA1-1505735526813-xsession_details
Server: akka-http/10.0.9
Date: Mon, 18 Sep 2017 11:47:06 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
A request to the `do_login` endpoint replies with a session.
If we decide not to use it, the server replies with `no session`, according to the source code.
```
$ curl -i http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Mon, 18 Sep 2017 11:47:17 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 10

no session
```
If however the client decides to provide session details, the reply contains them:
``` 
$ curl -i -H "Authorization: D3A3AC31E69D96A41DA482198B367364AEFDFEA1-1505735526813-xsession_details" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Mon, 18 Sep 2017 11:47:33 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 15

session_details
```
A more detailed example is available in [VariousSessions.java](https://github.com/softwaremill/akka-http-session/tree/master/example/src/main/java/com/softwaremill/example/session/VariousSessionsJava.java).

### What is the `invalidateSession` directive good for?
This directive instructs the client to clean the Cookie or Header. 
In browsers, this is done automatically, but in your custom client application, you need to take care for that by your self.
Take a look at [How can I use Cookies in a secure way?](#secure-cookie) and [Even when a session is invalidated by `invalidateSession`, is it still available?](#invalidate-session) for some important details on this directive and examples on how to use it. 

### What is the `optionalSession` directive good for?
This one is very similar to the `session` directive.
In this case however, we get access to the session details, which is an `Optional`.
Based on that we can decide on the server side, how to proceed.
In the [OptionalSession](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/directives/OptionalSessionDirective.java) example, we either reply with `no session` or with the session details, if present.   

### What is the `requiredSession` directive good for?
This directive is used to secure endpoints. 
Submitting a HTTP request requires the client to provide a valid session.
A session can be requested through an endpoint configured with the `setSession` directive.
The [CookieTransport](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/transport/CookieTransport.java) and [HeaderTransport](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/transport/HeaderTransport.java) examples show how to use this directive.
Also a sample use case of securing an endpoint is shown in [the Cookie](#cookies) and [the Header](#headers) transport example. 

### What is the `touchRequiredSession` directive good for?
Sessions do expire and the max age is configurable, as mentioned in [How long does a session live?](#max-age).
If you want to expose an endpoint that will reset the expiry date, include the `touchRequiredSession` in the route chain.
Besides the expiry date, the whole token will change.
Again when using Cookies, the browser will handle this, and replace the old Cookie, but in your own client application you have to replace the Cookie or Header by yourself.
This directive is demonstrated in the [TouchRequiredSession](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/directives/TouchRequiredSessionDirective.java) example:
```
$ curl -i --data "my_login"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: 170FBA501897D8F338C9680C8198F39B02C6C852-1505741661309-xmy_login
Server: akka-http/10.0.9
Date: Mon, 18 Sep 2017 13:29:21 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
The expiry date of the session is set to `1505741661309`, which is `Mon Sep 18 2017 13:34:21`.
The session life time is set to 5 minutes, as described earlier.
We can now access a resource, that requires a valid session, as usual:
```
$ curl -i -H "Authorization: 170FBA501897D8F338C9680C8198F39B02C6C852-1505741661309-xmy_login" http://localhost:8080/api/current_login
HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Mon, 18 Sep 2017 13:29:45 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```
Touching the session extends the expiry date by another 5 minutes:
```
$ curl -i -X POST -H "Authorization: 170FBA501897D8F338C9680C8198F39B02C6C852-1505741661309-xmy_login" http://localhost:8080/api/touch

HTTP/1.1 200 OK
Set-Authorization: 5B28616D1DF413964CF8D76CD6BB249CBFFB52B6-1505741866892-xmy_login
Server: akka-http/10.0.9
Date: Mon, 18 Sep 2017 13:32:46 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 15

session touched
```   
Now the expiry date is `1505741866892`, which is set to `Mon Sep 18 2017 13:37:46`. 
That's 5 minutes later than the request was issued (`Mon, 18 Sep 2017 13:32:46`).

### What happens if a timed out or invalid session is touched?
The `touchRequiredSession` requires a valid session to be extended. 
If an expired session is passed, the server will reply with an error.
```
$ curl -i -X POST -H "Authorization: 170FBA501897D8F338C9680C8198F39B02C6C852-1505741661309-xmy_login" http://localhost:8080/api/touch

HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Mon, 18 Sep 2017 13:38:11 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```
In this case the session expired on `1505741661309`, which is `Mon Sep 18 2017 13:34:21`, and the touch request was issued on `Mon, 18 Sep 2017 13:38:11`.

## JWT
### How do I use Json Web Tokens
In case you want to use the JWT format for authorization tokens, replace the `BasicSessionEncoder` with the `JwtSessionEncoder` and choose one of the JWT serializers, like `JwtSessionSerializers.StringToJValueSessionSerializer`.
The [JwtEncodedSession](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/jwt/JwtEncodedSession.java) example shows this particular use case:
```
$ curl -i --data "my_login"  http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Authorization: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkYXRhIjoibXlfbG9naW4iLCJleHAiOjE1MDU4Mjg3NjZ9.XCBN+29g11bwE6k/eDHfw0YETiGCEh38n/5tQuFMU/0=
Server: akka-http/10.0.9
Date: Tue, 19 Sep 2017 13:41:06 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok

$ curl -i -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkYXRhIjoibXlfbG9naW4iLCJleHAiOjE1MDU4Mjg3NjZ9.XCBN+29g11bwE6k/eDHfw0YETiGCEh38n/5tQuFMU/0=" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Server: akka-http/10.0.9
Date: Tue, 19 Sep 2017 13:41:46 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```

## CSRF protection
### What is it and (when) do I need it?
A CSRF attack is an attack, which tries to re-use a valid cookie to issue a request on your behalf.
In short, you login to your bank account. A session Cookie is sent back to you.
Now with every request to the bank's site, the Cookie is sent by the browser.
If you open a new tab in your browser and navigate to a malicious web site you may find a prepared link.
Clicking on that link will do a POST request to your bank's site.
Since it is the bank's site, the session Cookie you received from your bank is also sent, hence the request is authorized.
Now the POST request could modify your accounts data as you can do on the bank's site, when logged in.
Typically you won't even find a link to click. 
It could be javascript or an `<img>` html tag which does a GET request. 

CSRF protection is only needed when using Cookie type transport.

The idea behind protecting against CSRF attacks is to use a double-submit cookie.
The server sends an additional XSRF token to the client (browser).
Every request has to include that cookie and additionally an HTTP Header with the same value.
The attacker is able to prepare a link and relies on the browser to send the `_sessiondata` cookie, as well the XSRF token.
But the attacker is not able to read the cookie value. Hence the attacker is not able to set the additional Header to the right value.

### How do I enable CSRF protection
Two directives, `randomTokenCsrfProtection` and `setNewCsrfToken`, are required.
The example [CsrfProtection](https://github.com/softwaremill/akka-http-session-faq/tree/master/src/main/java/session/csrf/CsrfProtection.java) uses both directives.
Finally a `CheckHeader` component is required to intercept the route and lookup the header for the CSRF token.

The first one, `randomTokenCsrfProtection`, sets a new CSRF token on every GET request in form of a `XSRF-TOKEN` Cookie.
```
$ curl -i http://localhost:8080/

HTTP/1.1 200 OK
Set-Cookie: XSRF-TOKEN=mm10u06r81ltjqf7c62c0pn0pc7opssl7gm2ucckom5e4mp0gjsvhn8pa8vr8ula; Path=/
Server: akka-http/10.0.9
Date: Fri, 22 Sep 2017 11:48:42 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 7

Welcome
```
Without that cookie we would be not able to access the `/api/do_login` endpoint:
```
$ curl -i --data "my_login" http://localhost:8080/api/do_login
HTTP/1.1 403 Forbidden
Server: akka-http/10.0.9
Date: Fri, 22 Sep 2017 11:49:01 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 69

The supplied authentication is not authorized to access this resource
```
Let's try again with the `XSRF-TOKEN` Cookie and `X-XSRF-TOKEN` Header:
```
$ curl -i --data "my_login" --cookie "XSRF-TOKEN=mm10u06r81ltjqf7c62c0pn0pc7opssl7gm2ucckom5e4mp0gjsvhn8pa8vr8ula" -H "X-XSRF-TOKEN: mm10u06r81ltjqf7c62c0pn0pc7opssl7gm2ucckom5e4mp0gjsvhn8pa8vr8ula" http://localhost:8080/api/do_login

HTTP/1.1 200 OK
Set-Cookie: _sessiondata=5DEF1181A728E6C1724D263B23A8ABAF859046A8-1506081618995-xmy_login; Path=/; HttpOnly
Set-Cookie: XSRF-TOKEN=isael6lds7q5q4ilm90hnv96s0vqn4o7i7pi4mp8b5q27eeq4ug23bto03vu4fmn; Path=/
Server: akka-http/10.0.9
Date: Fri, 22 Sep 2017 11:50:18 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
Notice we received a new `XSRF-TOKEN` value. 
This is achieved by the `setNewCsrfToken` directive.
This is recommended to prevent a [session fixation attack](https://security.stackexchange.com/questions/22903/why-refresh-csrf-token-per-form-request).
Now we can access the `/api/do_logout` endpoint:
```
$ curl -i -X POST --cookie "_sessiondata=5DEF1181A728E6C1724D263B23A8ABAF859046A8-1506081618995-xmy_login;XSRF-TOKEN=mm10u06r81ltjqf7c62c0pn0pc7opssl7gm2ucckom5e4mp0gjsvhn8pa8vr8ula" -H "X-XSRF-TOKEN: mm10u06r81ltjqf7c62c0pn0pc7opssl7gm2ucckom5e4mp0gjsvhn8pa8vr8ula" http://localhost:8080/api/do_logout

HTTP/1.1 200 OK
Set-Cookie: _sessiondata=deleted; Expires=Wed, 01 Jan 1800 00:00:00 GMT; Path=/; HttpOnly
Server: akka-http/10.0.9
Date: Fri, 22 Sep 2017 11:51:43 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 2

ok
```
Remember that accessing an endpoint via `GET` is not secured by the CSRF protection mechanism.
Therefore endpoints accessible through `GET` should not mutate the server's state in any way:
```
$ curl -i --cookie "_sessiondata=5DEF1181A728E6C1724D263B23A8ABAF859046A8-1506081618995-xmy_login" http://localhost:8080/api/current_login

HTTP/1.1 200 OK
Set-Cookie: XSRF-TOKEN=4ggc4k5ba4i9ovuu4sb32pm9kdvc8fomarqgt0fjih1s5s5ltnboi8e08efo81cp; Path=/
Server: akka-http/10.0.9
Date: Fri, 22 Sep 2017 11:55:53 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 8

my_login
```