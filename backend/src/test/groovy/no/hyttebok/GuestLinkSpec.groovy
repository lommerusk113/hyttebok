package no.hyttebok

import io.micronaut.test.extensions.spock.annotation.MicronautTest

import java.time.LocalDate

import static io.micronaut.http.HttpStatus.BAD_REQUEST
import static io.micronaut.http.HttpStatus.FORBIDDEN
import static io.micronaut.http.HttpStatus.GONE
import static io.micronaut.http.HttpStatus.OK
import static io.micronaut.http.HttpStatus.UNAUTHORIZED

/** Guest links show a guest the arrival info, and nothing else of the cabin. */
@MicronautTest
class GuestLinkSpec extends AppSpec {

    def 'should show a guest the arrival info without a login'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari, 'Fjellro')
            def info = call('GET', '/api/cabin', [user: kari, cabin: karis]).body
            call('PUT', '/api/cabin', [user: kari, cabin: karis, json: info + [arrivalInfo: '- Nøkkel i boksen', wifiName: 'Fjellro', wifiPassword: 'hemmelig',
                                                                              keyInfo: 'Kode 1234', hiddenSections: ['keyInfo']]])
            def link = call('POST', '/api/guest-links', [user: kari, cabin: karis, json: [label: 'Familien Hansen', validUntil: LocalDate.now().plusDays(3).toString()]]).body
        when:
            def view = call('GET', "/api/guest/${link.token}")
        then:
            view.status == OK.code
            view.body.cabinName == 'Fjellro'
            view.body.label == 'Familien Hansen'
            view.body.arrivalInfo == '- Nøkkel i boksen'
            view.body.wifiPassword == 'hemmelig'
        and: 'hidden cards stay hidden'
            view.body.keyInfo == null
        and: 'the link is no way into the rest of the cabin'
            call('GET', '/api/bookings', [cabin: karis]).status == UNAUTHORIZED.code
    }

    def 'should stop working when revoked'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
            def link = call('POST', '/api/guest-links', [user: kari, cabin: karis, json: [label: 'Gjest', validUntil: LocalDate.now().toString()]]).body
        when:
            call('DELETE', "/api/guest-links/${link.id}", [user: kari, cabin: karis])
        then:
            call('GET', "/api/guest/${link.token}").status == GONE.code
    }

    def 'should stop working after the last day'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
            def link = call('POST', '/api/guest-links', [user: kari, cabin: karis, json: [label: 'Gjest', validUntil: LocalDate.now().toString()]]).body
        when:
            update("UPDATE guest_link SET valid_until = current_date - 1 WHERE id = ${link.id}")
        then:
            call('GET', "/api/guest/${link.token}").status == GONE.code
    }

    def 'should refuse a last day in the past'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
        expect:
            call('POST', '/api/guest-links', [user: kari, cabin: karis, json: [label: 'Gjest', validUntil: '2020-01-01']]).status == BAD_REQUEST.code
    }

    def 'should only let owners make them'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def karis = cabin(kari)
            join(kari, ola, karis)
        expect:
            call('POST', '/api/guest-links', [user: ola, cabin: karis, json: [label: 'Gjest', validUntil: LocalDate.now().toString()]]).status == FORBIDDEN.code
    }
}
