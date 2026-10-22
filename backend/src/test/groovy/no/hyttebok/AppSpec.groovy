package no.hyttebok

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.testcontainers.postgresql.PostgreSQLContainer
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.sql.DriverManager

/**
 * The whole app over HTTP, against a throwaway Postgres in Docker (one for all specs, started once). Tests act as
 * real users: sign up, log in (the session cookie), and call the API with a cabin, like the app does.
 * Every user gets a unique email, so specs don't need a clean database.
 */
abstract class AppSpec extends Specification implements TestPropertyProvider {

    static final String ADMIN = 'admin@test.no'
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer('postgres:18-alpine')
    static final HttpClient HTTP = HttpClient.newHttpClient()
    static int users = 0

    static {
        POSTGRES.start()
    }

    @Inject
    EmbeddedServer server

    @Override
    Map<String, String> getProperties() {
        return [
            'datasources.default.url'     : POSTGRES.jdbcUrl,
            'datasources.default.username': POSTGRES.username,
            'datasources.default.password': POSTGRES.password,
            'hyttebok.admin-emails'       : ADMIN,
            'hyttebok.upload-dir'         : Files.createTempDirectory('hyttebok-test').toString(),
        ]
    }

    /** Someone logged in: their session cookie goes with every call. */
    static class User {
        String name
        String email
        String cookie
    }

    record Reply(int status, Object body, Map<String, List<String>> headers) {

        String header(String name) {
            return headers.find { it.key.equalsIgnoreCase(name) }?.value?.first()
        }
    }

    /** A new account, logged in. */
    User signUp(String name = 'Kari', String email = null) {
        def address = email ?: "user${++users}-${System.nanoTime()}@test.no"
        assert call('POST', '/api/auth/signup', [json: [name: name, email: address, password: 'passord1']]).status == 201
        return logIn(new User(name: name, email: address), 'passord1')
    }

    /** The system admin (ADMIN_EMAILS, and Google-verified: set in the database, as if they'd used Google). */
    User admin() {
        def existing = sql("SELECT count(*) FROM app_user WHERE email = '${ADMIN}'") as long
        if (!existing) {
            call('POST', '/api/auth/signup', [json: [name: 'Admin', email: ADMIN, password: 'passord1']])
            update("UPDATE app_user SET google_sub = 'google-admin' WHERE email = '${ADMIN}'")
        }
        return logIn(new User(name: 'Admin', email: ADMIN), 'passord1')
    }

    User logIn(User user, String password) {
        def reply = call('POST', '/login', [json: [username: user.email, password: password]])
        def cookie = reply.headers.find { it.key.equalsIgnoreCase('set-cookie') }?.value?.find { it.startsWith('JWT=') }
        user.cookie = cookie?.split(';')?.first()
        return user
    }

    long cabin(User owner, String name = 'Testhytta') {
        return call('POST', '/api/cabins', [user: owner, json: [name: name]]).body.id as long
    }

    /** {@code owner} invites {@code member} into the cabin with a link, and they accept it. */
    void join(User owner, User member, long cabin, String role = 'MEMBER') {
        def token = call('POST', '/api/access/invites', [user: owner, cabin: cabin, json: [role: role]]).body.token
        assert call('POST', "/api/access/invites/${token}/accept", [user: member]).status == 200
    }

    /** Options: user (logged in as), cabin (X-Hytte), json (a body), multipart ([name, type, bytes] of a file). */
    Reply call(String method, String path, Map options = [:]) {
        def builder = HttpRequest.newBuilder(URI.create("http://localhost:${server.port}${path}"))
        if (options.user?.cookie) {
            builder.header('Cookie', options.user.cookie as String)
        }
        if (options.cabin) {
            builder.header('X-Hytte', options.cabin as String)
        }
        def body = HttpRequest.BodyPublishers.noBody()
        if (options.json != null) {
            builder.header('Content-Type', 'application/json')
            body = HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(options.json))
        }
        if (options.multipart) {
            def (String name, String type, byte[] bytes) = options.multipart
            def boundary = "----hyttebok${System.nanoTime()}"
            def out = new ByteArrayOutputStream()
            out << "--${boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${name}\"\r\nContent-Type: ${type}\r\n\r\n".getBytes('UTF-8')
            out << bytes
            out << "\r\n--${boundary}--\r\n".getBytes('UTF-8')
            builder.header('Content-Type', "multipart/form-data; boundary=${boundary}")
            body = HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())
        }
        def response = HTTP.send(builder.method(method, body).build(), HttpResponse.BodyHandlers.ofByteArray())
        def type = response.headers().firstValue('Content-Type').orElse('')
        def parsed = type.contains('json') && response.body().length ? new JsonSlurper().parse(response.body()) : response.body()
        return new Reply(response.statusCode(), parsed, response.headers().map())
    }

    static Object sql(String query) {
        DriverManager.getConnection(POSTGRES.jdbcUrl, POSTGRES.username, POSTGRES.password).withCloseable { connection ->
            def rs = connection.createStatement().executeQuery(query)
            return rs.next() ? rs.getObject(1) : null
        }
    }

    static void update(String statement) {
        DriverManager.getConnection(POSTGRES.jdbcUrl, POSTGRES.username, POSTGRES.password).withCloseable { connection ->
            connection.createStatement().executeUpdate(statement)
        }
    }

    static final byte[] PNG = Base64.decoder.decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
    static final byte[] SVG = '<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'.getBytes('UTF-8')
    static final byte[] PDF = '%PDF-1.4\n%%EOF\n'.getBytes('UTF-8')
}
