package no.hyttebok

import io.micronaut.test.extensions.spock.annotation.MicronautTest

import static io.micronaut.http.HttpStatus.BAD_REQUEST
import static io.micronaut.http.HttpStatus.OK

/** Uploads are judged by their bytes, and served so they can't run as a page from our address. */
@MicronautTest
class UploadSpec extends AppSpec {

    def 'should refuse an SVG photo, whatever the browser calls it'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
        expect:
            call('POST', '/api/photos', [user: kari, cabin: karis, multipart: ['bilde.png', 'image/png', SVG]]).status == BAD_REQUEST.code
    }

    def 'should store a photo with the type of its bytes and serve it safely'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
        when:
            def photo = call('POST', '/api/photos', [user: kari, cabin: karis, multipart: ['bilde', 'text/html', PNG]]).body
            def file = call('GET', "/api/photos/${photo.id}/file?hytte=${karis}", [user: kari])
        then:
            photo.contentType == 'image/png'
            file.header('Content-Type') == 'image/png'
            file.header('X-Content-Type-Options') == 'nosniff'
            file.header('Content-Security-Policy').contains('sandbox')
    }

    def 'should attach photos and PDFs to a wiki page, and nothing else'() {
        given:
            def kari = signUp('Kari')
            def karis = cabin(kari)
            def page = call('POST', '/api/wiki', [user: kari, cabin: karis, json: [title: 'Varmepumpe']]).body
        when:
            def pdf = call('POST', "/api/wiki/${page.id}/attachments", [user: kari, cabin: karis, multipart: ['manual.pdf', 'application/pdf', PDF]])
            def svg = call('POST', "/api/wiki/${page.id}/attachments", [user: kari, cabin: karis, multipart: ['tegning.svg', 'image/svg+xml', SVG]])
            def file = call('GET', "/api/wiki/attachments/${pdf.body.id}/file?hytte=${karis}", [user: kari])
        then:
            pdf.status == OK.code
            pdf.body.originalName == 'manual.pdf'
            svg.status == BAD_REQUEST.code
            file.header('Content-Disposition').startsWith('attachment')
            file.header('X-Content-Type-Options') == 'nosniff'
        when: 'the page is deleted'
            call('DELETE', "/api/wiki/${page.id}", [user: kari, cabin: karis])
        then: 'its files go with it'
            call('GET', '/api/wiki/attachments', [user: kari, cabin: karis]).body == []
    }
}
