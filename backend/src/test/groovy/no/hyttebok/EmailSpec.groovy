package no.hyttebok

import groovy.json.JsonSlurper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.testcontainers.containers.GenericContainer

import static io.micronaut.http.HttpStatus.ACCEPTED
import static io.micronaut.http.HttpStatus.OK

/** Email through a real SMTP server (Mailpit in Docker), read back through Mailpit's API. */
@MicronautTest
class EmailSpec extends AppSpec {

    static final GenericContainer MAILPIT = new GenericContainer('axllent/mailpit:v1.27').withExposedPorts(1025, 8025)

    static {
        MAILPIT.start()
    }

    @Override
    Map<String, String> getProperties() {
        return super.properties + [
            'hyttebok.mail.host'  : MAILPIT.host,
            'hyttebok.mail.port'  : MAILPIT.getMappedPort(1025) as String,
            'hyttebok.mail.from'  : 'hyttebok@test.no',
            'hyttebok.public-url' : 'https://hyttebok.test',
        ]
    }

    def 'should say email is on'() {
        expect:
            call('GET', '/api/auth/me').body.emailEnabled
    }

    def 'should email a working new-password link, once per few minutes'() {
        given:
            def kari = signUp('Kari')
        when:
            def reply = call('POST', '/api/auth/forgot', [json: [email: kari.email, lang: 'nb']])
            def mail = waitForMail(kari.email)
        then:
            reply.status == ACCEPTED.code
            mail.Subject == 'Nytt passord til Hyttebok'
            def token = (mail.Text =~ 'https://hyttebok.test/nytt-passord/([A-Za-z0-9_-]+)')[0][1]
        when: 'the link sets a new password'
            call('POST', "/api/auth/password-reset/${token}", [json: [password: 'fra-eposten']])
        then:
            call('GET', '/api/profile', [user: logIn(kari, 'fra-eposten')]).status == OK.code
        when: 'asking again right away'
            call('POST', '/api/auth/forgot', [json: [email: kari.email]])
            sleep(1500)
        then: 'no second email'
            mails(kari.email).size() == 1
    }

    def 'should not reveal whether an account exists'() {
        given:
            def nobody = "ingen-${System.nanoTime()}@test.no"
        when:
            def reply = call('POST', '/api/auth/forgot', [json: [email: nobody]])
            sleep(1500)
        then:
            reply.status == ACCEPTED.code
            mails(nobody).isEmpty()
    }

    def 'should email a single-use invitation'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari, 'Solbakken')
            def guest = "ny-${System.nanoTime()}@test.no"
        when:
            def invite = call('POST', '/api/access/invites', [user: kari, cabin: karis, json: [email: guest, lang: 'nb']]).body
            def mail = waitForMail(guest)
        then:
            invite.singleUse
            mail.Subject == 'Kari har invitert deg til Solbakken'
            mail.Text.contains("https://hyttebok.test/invitasjon/${invite.token}")
    }

    /** The messages to {@code address}, newest first (Mailpit's summary: Subject, ID ...), with Text added. */
    static List<Map> mails(String address) {
        def api = "http://${MAILPIT.host}:${MAILPIT.getMappedPort(8025)}/api/v1"
        def all = new JsonSlurper().parse(new URL("${api}/search?query=${URLEncoder.encode("to:${address}", 'UTF-8')}")).messages ?: []
        return all.collect { it + [Text: new JsonSlurper().parse(new URL("${api}/message/${it.ID}")).Text] }
    }

    /** Sending happens in the background: wait a little for it. */
    static Map waitForMail(String address) {
        for (def i = 0; i < 50; i++) {
            def found = mails(address)
            if (found) {
                return found.first()
            }
            sleep(100)
        }
        throw new AssertionError("No email to ${address}")
    }
}
