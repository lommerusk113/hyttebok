package no.hyttebok

import io.micronaut.test.extensions.spock.annotation.MicronautTest

import static io.micronaut.http.HttpStatus.FORBIDDEN
import static io.micronaut.http.HttpStatus.OK
import static io.micronaut.http.HttpStatus.UNAUTHORIZED

/** The example cabin: anyone may read it, nobody may change it, and no other cabin opens with it. */
@MicronautTest
class ExampleCabinSpec extends AppSpec {

    def 'should open the example cabin read-only to everyone'() {
        given:
            def admin = admin()
            def example = cabin(admin, 'Eksempel')
            call('PUT', "/api/cabins/${example}/example", [user: admin, json: [on: true]])
            def stranger = signUp('Per')
        expect: 'visitors and logged-in strangers read it'
            call('GET', '/api/cabins/example').body.id == example
            call('GET', '/api/bookings', [cabin: example]).status == OK.code
            call('GET', '/api/todos', [user: stranger, cabin: example]).status == OK.code
        and: 'but change nothing, and see nobody'
            call('POST', '/api/todos', [cabin: example, json: [title: 'x', done: false]]).status == UNAUTHORIZED.code
            call('POST', '/api/todos', [user: stranger, cabin: example, json: [title: 'x', done: false]]).status == FORBIDDEN.code
            call('GET', '/api/access/members', [cabin: example]).status == UNAUTHORIZED.code
            call('GET', '/api/access/members', [user: stranger, cabin: example]).status == FORBIDDEN.code
    }

    def 'should keep other cabins closed'() {
        given:
            def admin = admin()
            def example = cabin(admin, 'Eksempel')
            call('PUT', "/api/cabins/${example}/example", [user: admin, json: [on: true]])
            def kari = signUp('Kari')
            def karis = cabin(kari)
        expect:
            call('GET', '/api/bookings', [cabin: karis]).status == UNAUTHORIZED.code
    }

    def 'should close again when it stops being the example'() {
        given:
            def admin = admin()
            def example = cabin(admin, 'Eksempel')
            call('PUT', "/api/cabins/${example}/example", [user: admin, json: [on: true]])
        when:
            call('PUT', "/api/cabins/${example}/example", [user: admin, json: [on: false]])
        then:
            call('GET', '/api/bookings', [cabin: example]).status == UNAUTHORIZED.code
    }

    def 'should only let system admins pick it'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
        expect:
            call('PUT', "/api/cabins/${karis}/example", [user: kari, json: [on: true]]).status == FORBIDDEN.code
    }
}
