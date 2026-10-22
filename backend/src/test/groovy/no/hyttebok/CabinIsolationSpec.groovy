package no.hyttebok

import io.micronaut.test.extensions.spock.annotation.MicronautTest

import static io.micronaut.http.HttpStatus.FORBIDDEN
import static io.micronaut.http.HttpStatus.OK
import static io.micronaut.http.HttpStatus.UNAUTHORIZED

/** One cabin's data never reaches another cabin's members, whatever ids or cabin they send. */
@MicronautTest
class CabinIsolationSpec extends AppSpec {

    def 'should refuse a cabin the user is not a member of'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def karis = cabin(kari)
        expect:
            call('GET', '/api/todos', [user: ola, cabin: karis]).status == FORBIDDEN.code
            call('POST', '/api/todos', [user: ola, cabin: karis, json: [title: 'Inntrenger', done: false]]).status == FORBIDDEN.code
    }

    def 'should refuse cabin data without a login'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
        expect:
            call('GET', '/api/bookings', [cabin: karis]).status == UNAUTHORIZED.code
    }

    def 'should not let a member change another cabin by its ids'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def karis = cabin(kari)
            def olas = cabin(ola)
            def todo = call('POST', '/api/todos', [user: kari, cabin: karis, json: [title: 'Male', done: false]]).body
        when: 'Ola sends Kari\'s todo id, but with his own cabin'
            call('PUT', '/api/todos', [user: ola, cabin: olas, json: todo + [title: 'Kapret']])
            call('DELETE', "/api/todos/${todo.id}", [user: ola, cabin: olas])
        then: 'Kari\'s todo is untouched'
            def list = call('GET', '/api/todos', [user: kari, cabin: karis]).body
            list*.title == ['Male']
        and: 'and nothing of hers shows up in his cabin'
            !(call('GET', '/api/todos', [user: ola, cabin: olas]).body*.title.contains('Male'))
    }

    def 'should not list another cabin\'s photos or expenses'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def karis = cabin(kari)
            def olas = cabin(ola)
            call('POST', '/api/expenses', [user: kari, cabin: karis, json: [kind: 'EXPENSE', amount: 100, paidBy: 'Kari', splitBetween: [], spentOn: '2026-01-01']])
        expect:
            call('GET', '/api/expenses', [user: kari, cabin: karis]).body.size() == 1
            call('GET', '/api/expenses', [user: ola, cabin: olas]).body.size() == 0
    }

    def 'should let a removed member go at once'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def karis = cabin(kari)
            join(kari, ola, karis)
            def olaId = sql("SELECT id FROM app_user WHERE email = '${ola.email}'")
        when:
            call('DELETE', "/api/access/members/${olaId}", [user: kari, cabin: karis])
        then:
            call('GET', '/api/todos', [user: ola, cabin: karis]).status == FORBIDDEN.code
        and:
            call('GET', '/api/todos', [user: kari, cabin: karis]).status == OK.code
    }
}
