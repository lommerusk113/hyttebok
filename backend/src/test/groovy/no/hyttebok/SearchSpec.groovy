package no.hyttebok

import io.micronaut.test.extensions.spock.annotation.MicronautTest

/** Search finds things across the cabin, and only in that cabin. */
@MicronautTest
class SearchSpec extends AppSpec {

    def 'should find matches of different kinds, case-insensitively'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
            call('POST', '/api/todos', [user: kari, cabin: karis, json: [title: 'Bytte vannfilter', done: false]])
            call('POST', '/api/wiki', [user: kari, cabin: karis, json: [title: 'Vann', content: 'Hovedkranen til VANNET står i boden.']])
            call('POST', '/api/shopping', [user: kari, cabin: karis, json: [name: 'Kaffe', bought: false]])
        when:
            def hits = call('GET', '/api/search?q=vann', [user: kari, cabin: karis]).body
        then:
            hits*.type.toSet() == ['todo', 'wiki'] as Set
            hits.find { it.type == 'wiki' }.snippet.contains('VANNET')
    }

    def 'should not find anything in another cabin'() {
        given:
            def kari = signUp('Kari')
            def ola = signUp('Ola')
            def karis = cabin(kari)
            def olas = cabin(ola)
            call('POST', '/api/todos', [user: kari, cabin: karis, json: [title: 'Hemmelig plan', done: false]])
        expect:
            call('GET', '/api/search?q=hemmelig', [user: ola, cabin: olas]).body == []
    }

    def 'should treat % and _ as plain characters'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
            call('POST', '/api/todos', [user: kari, cabin: karis, json: [title: 'Male', done: false]])
        expect:
            call('GET', '/api/search?q=%25%25', [user: kari, cabin: karis]).body == []
    }
}
