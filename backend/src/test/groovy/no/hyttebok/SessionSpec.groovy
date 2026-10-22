package no.hyttebok

import io.micronaut.test.extensions.spock.annotation.MicronautTest

import static io.micronaut.http.HttpStatus.NOT_FOUND
import static io.micronaut.http.HttpStatus.OK
import static io.micronaut.http.HttpStatus.UNAUTHORIZED
import static io.micronaut.http.HttpStatus.UNPROCESSABLE_ENTITY

/** Logging in, and the ways a password changes: every one of them logs out the other devices. */
@MicronautTest
class SessionSpec extends AppSpec {

    def 'should not log in with a wrong password'() {
        given:
            def kari = signUp('Kari')
        when:
            def again = logIn(new AppSpec.User(name: 'Kari', email: kari.email), 'feil-passord')
        then:
            again.cookie == null
    }

    def 'should log out every device when the password changes'() {
        given:
            def phone = signUp('Kari')
            def laptop = logIn(new AppSpec.User(name: 'Kari', email: phone.email), 'passord1')
        when:
            def reply = call('PUT', '/api/profile/password', [user: phone, json: [current: 'passord1', password: 'nytt-passord']])
        then:
            reply.status == OK.code
            call('GET', '/api/profile', [user: laptop]).status == UNAUTHORIZED.code
            call('GET', '/api/profile', [user: phone]).status == UNAUTHORIZED.code
        and:
            call('GET', '/api/profile', [user: logIn(phone, 'nytt-passord')]).status == OK.code
    }

    def 'should refuse a password change with the wrong current password'() {
        given:
            def kari = signUp('Kari')
        expect:
            call('PUT', '/api/profile/password', [user: kari, json: [current: 'gjetting', password: 'nytt-passord']]).status == UNPROCESSABLE_ENTITY.code
    }

    def 'should let a recovery code work once, for its own account only'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def code = call('POST', '/api/profile/recovery-codes', [user: kari]).body.first()
        expect:
            call('POST', '/api/auth/recover', [json: [email: ola.email, code: code]]).status == UNPROCESSABLE_ENTITY.code
        when:
            def first = call('POST', '/api/auth/recover', [json: [email: kari.email.toUpperCase(), code: code.toUpperCase()]])
        then:
            first.status == OK.code
            call('POST', '/api/auth/recover', [json: [email: kari.email, code: code]]).status == UNPROCESSABLE_ENTITY.code
        when: 'the link it gave sets a new password'
            call('POST', "/api/auth/password-reset/${first.body.token}", [json: [password: 'fra-koden']])
        then:
            call('GET', '/api/profile', [user: kari]).status == UNAUTHORIZED.code
            call('GET', '/api/profile', [user: logIn(kari, 'fra-koden')]).status == OK.code
    }

    def 'should no longer let owners make password links'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def karis = cabin(kari)
            join(kari, ola, karis)
            def olaId = sql("SELECT id FROM app_user WHERE email = '${ola.email}'")
        expect:
            call('POST', "/api/access/users/${olaId}/password-reset", [user: kari, cabin: karis]).status == NOT_FOUND.code
    }
}
