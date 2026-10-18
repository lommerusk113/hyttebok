package no.hyttebok.cabin;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import no.hyttebok.access.Membership;
import no.hyttebok.access.MembershipRepository;
import no.hyttebok.booking.Booking;
import no.hyttebok.booking.BookingRepository;
import no.hyttebok.checklist.ChecklistItem;
import no.hyttebok.checklist.ChecklistItemRepository;
import no.hyttebok.forum.ForumPost;
import no.hyttebok.forum.ForumPostRepository;
import no.hyttebok.forum.ForumReaction;
import no.hyttebok.forum.ForumReactionRepository;
import no.hyttebok.forum.ForumReply;
import no.hyttebok.forum.ForumReplyRepository;
import no.hyttebok.log.LogEntry;
import no.hyttebok.log.LogEntryRepository;
import no.hyttebok.maintenance.Maintenance;
import no.hyttebok.maintenance.MaintenanceCompletion;
import no.hyttebok.maintenance.MaintenanceCompletionRepository;
import no.hyttebok.maintenance.MaintenanceRepository;
import no.hyttebok.shopping.ShoppingItem;
import no.hyttebok.shopping.ShoppingItemRepository;
import no.hyttebok.suggestion.Suggestion;
import no.hyttebok.suggestion.SuggestionRepository;
import no.hyttebok.todo.Todo;
import no.hyttebok.todo.TodoRepository;
import no.hyttebok.wiki.WikiPage;
import no.hyttebok.wiki.WikiPageRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * A test cabin for system admins: every field of the cabin filled in, and bookings, cabin book entries, maintenance
 * history, tasks, shopping, suggestions, forum and wiki from the last two years, plus a few bookings ahead. Dates are
 * relative to today, so the cabin always looks lived in. The admin who makes it becomes its owner.
 */
@Singleton
public class DemoCabin {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final CabinInfoRepository cabins;
    private final MembershipRepository memberships;
    private final BookingRepository bookings;
    private final LogEntryRepository log;
    private final MaintenanceRepository maintenance;
    private final MaintenanceCompletionRepository completions;
    private final TodoRepository todos;
    private final ShoppingItemRepository shopping;
    private final ChecklistItemRepository checklist;
    private final SuggestionRepository suggestions;
    private final ForumPostRepository posts;
    private final ForumReplyRepository replies;
    private final ForumReactionRepository reactions;
    private final WikiPageRepository wiki;
    private final JdbcOperations jdbc;

    private LocalDate today;

    public DemoCabin(CabinInfoRepository cabins, MembershipRepository memberships, BookingRepository bookings, LogEntryRepository log,
                     MaintenanceRepository maintenance, MaintenanceCompletionRepository completions, TodoRepository todos,
                     ShoppingItemRepository shopping, ChecklistItemRepository checklist, SuggestionRepository suggestions,
                     ForumPostRepository posts, ForumReplyRepository replies, ForumReactionRepository reactions, WikiPageRepository wiki,
                     JdbcOperations jdbc) {
        this.cabins = cabins;
        this.memberships = memberships;
        this.bookings = bookings;
        this.log = log;
        this.maintenance = maintenance;
        this.completions = completions;
        this.todos = todos;
        this.shopping = shopping;
        this.checklist = checklist;
        this.suggestions = suggestions;
        this.posts = posts;
        this.replies = replies;
        this.reactions = reactions;
        this.wiki = wiki;
        this.jdbc = jdbc;
    }

    @Transactional
    public synchronized CabinInfo create(long ownerId, String ownerName) {

        today = LocalDate.now(OSLO);
        var cabin = cabins.save(new CabinInfo(null, "Testhytta Fjellro", "Familiehytta ved Storevatnet",
            "Tømmerhytte fra 1978 med fire soverom, badstue og utsikt over vatnet. Bilvei helt fram om sommeren; "
                + "om vinteren parkerer du ved bommen og går eller går på ski de siste 800 meterne.",
            "Fjellroveien 12, 3560 Hemsedal", "Fjellro-gjest", "Badstue2024!",
            "Nøkkelen ligger i nøkkelboksen ved inngangsdøra (kode 4512). Slå på hovedbryteren i gangen og åpne vannet under kjøkkenbenken.",
            "Steng vannet, tøm kjøleskapet og ta med søppelet. Sett varmen på 10 grader og lås nøkkelen inn i boksen igjen.",
            null, List.of(), 60.8623, 8.5532, 812.0, true));
        cabins.updateJoinCode(cabin.id(), JoinCodes.generate());
        memberships.save(new Membership(null, ownerId, cabin.id(), Membership.OWNER, Instant.now()));

        CabinContext.runAs(cabin.id(), () -> fill(ownerName.split("\\s+")[0]));

        return cabin;
    }

    private void fill(String me) {

        var bookingIds = bookings(me);
        cabinBook(me, bookingIds);
        maintenance(me);
        todos(me);
        shopping(me);
        suggestions(me);
        forum(me);
        wiki(me);
    }

    /** Past and coming stays; returns the ids of the past ones (newest last) for the cabin book. */
    private List<Long> bookings(String me) {

        record Stay(int daysAgo, int nights, String who, int guests, String note) {
        }
        var stays = List.of(
            new Stay(700, 7, "Kari", 4, "Vinterferie"),
            new Stay(610, 3, me, 2, null),
            new Stay(540, 10, "Ola og Ingrid", 5, "Fellesferie, del 1"),
            new Stay(470, 4, "Lars", 3, "Elgjakt"),
            new Stay(380, 3, me, 6, "Juletur med hele gjengen"),
            new Stay(330, 7, "Kari", 4, "Vinterferie"),
            new Stay(250, 4, "Sofie", 2, "Påske"),
            new Stay(175, 10, "Ola og Ingrid", 5, "Fellesferie"),
            new Stay(105, 4, "Lars", 3, "Elgjakt"),
            new Stay(30, 3, me, 4, "Dugnadshelg"),
            new Stay(-12, 3, "Sofie", 2, null),
            new Stay(-45, 5, me, 6, "Juletur"),
            new Stay(-120, 7, "Kari", 4, "Vinterferie"));

        return stays.stream().map(s -> {
            var start = today.minusDays(s.daysAgo());
            var saved = bookings.save(new Booking(null, s.who(), start, start.plusDays(s.nights()), s.guests(), s.note(), null));
            return s.daysAgo() > 0 ? saved.id() : null;
        }).filter(id -> id != null).toList();
    }

    private void cabinBook(String me, List<Long> bookingIds) {

        record Entry(int booking, String author, String title, String content, List<String> with) {
        }
        var entries = List.of(
            new Entry(0, "Kari", "Snøkaos og skiføre", "Kom fram i full snøstorm og måtte måke oss inn. Resten av uka var det strålende sol "
                + "og perfekt føre. Barna lærte å ta heisen alene i bakken.", List.of("Kari", "Per", "Emma", "Jonas")),
            new Entry(2, "Ingrid", "Fellesferie ved vatnet", "Badet hver dag selv om vatnet var 16 grader. Fikk tre ørret på garnet, "
                + "og Ola fikk endelig fikset benken på brygga.", List.of("Ola", "Ingrid", "Mia", "Theo", "Bestemor")),
            new Entry(3, "Lars", "Elgjakt", "Ingen elg i år, men mye fin tur. Vedboden er fylt opp igjen.", List.of("Lars", "Henrik", "Tore")),
            new Entry(4, me, "Jul på hytta", "Ribbe, pinnekjøtt og altfor mye konfekt. Strømmen gikk på julaften, så vi spiste i lys "
                + "fra peisen. Ingen klaget.", List.of(me, "Kari", "Ola", "Ingrid", "Lars", "Sofie")),
            new Entry(6, "Sofie", "Påskefjellet", "Lange turer til Storefjell, appelsin og Kvikk Lunsj på toppen. Brannslukkeren er sjekket.",
                List.of("Sofie", "Marius")),
            new Entry(7, "Ola", "Sommer igjen", "Malte nordveggen og byttet to takstein. Myggen var verre enn i fjor.", List.of("Ola", "Ingrid", "Mia", "Theo")),
            new Entry(8, "Lars", "Jakt og sopp", "Felte en okse første dag! Fant også masse kantarell bak låven.", List.of("Lars", "Henrik")),
            new Entry(9, me, "Dugnad", "Ryddet kratt, renset takrennene og byttet batterier i røykvarslerne. Pizza i badstua etterpå.",
                List.of(me, "Kari", "Lars")));

        for (var e : entries) {
            var booking = bookings.findById(bookingIds.get(e.booking())).orElseThrow();
            var saved = log.save(new LogEntry(null, e.author(), e.title(), e.content(), e.with(), booking.startDate(), booking.endDate(), booking.id(), null, null));
            backdate("log_entry", saved.id(), booking.endDate());
        }
    }

    private void maintenance(String me) {

        record Task(String name, String description, int every, String unit, int... doneDaysAgo) {
        }
        var tasks = List.of(
            new Task("Feie pipa", "Feieren kommer på forespørsel; ring kommunen i god tid.", 1, "YEARS", 690, 330),
            new Task("Bytte batterier i røykvarslerne", "Fire varslere: gangen, stua og begge soverommene oppe.", 1, "YEARS", 400, 30),
            new Task("Rense takrennene", null, 1, "YEARS", 560, 200),
            new Task("Sjekke brannslukkeren", "Rist den og sjekk at nålen står på grønt.", 6, "MONTHS", 520, 340, 250),
            new Task("Bytte vannfilter", "Filteret står under kjøkkenbenken. Nye filtre ligger i boden.", 3, "MONTHS", 300, 210),
            new Task("Beise terrassen", null, 2, "YEARS", 600),
            new Task("Tømme slamavskilleren", "Avtales med kommunen.", 2, "YEARS"));

        String[] who = {me, "Kari", "Ola", "Lars", "Sofie"};
        var n = 0;
        for (var task : tasks) {
            var saved = maintenance.save(new Maintenance(null, task.name(), task.description(), task.every(), task.unit(), null, null, null));
            backdate("maintenance", saved.id(), today.minusDays(720));
            LocalDate last = null;
            for (var daysAgo : task.doneDaysAgo()) {
                var on = today.minusDays(daysAgo);
                var by = who[n++ % who.length];
                var done = completions.save(new MaintenanceCompletion(null, saved.id(), on, by, null, List.of(by), n % 2 == 0 ? "Alt i orden." : null, null));
                backdate("maintenance_completion", done.id(), on);
                last = on;
            }
            maintenance.update(saved.id(), last);
        }
    }

    private void todos(String me) {

        todo("Fikse porten", "Hengslet nederst har løsnet.", "Ola", 90, null, 0, null);
        todo("Skifte pakning på kjøkkenkrana", "Den drypper.", "Kari", 40, null, 0, null);
        todo("Kjøpe ny grill", null, me, 12, null, 0, null);
        todo("Male utedoen", null, "Sofie", 200, null, 0, null);
        todo("Bytte lyspære i badstua", null, "Lars", 320, "Lars", 300, "Tok med to ekstra, de ligger i skuffen.");
        todo("Montere ny vedovn", "Den gamle sprakk i fjor vinter.", me, 420, me, 360, "Ny Jøtul på plass, godkjent av feieren.");
        todo("Fjerne vepsebol under taket", null, "Ingrid", 190, "Ola", 170, null);
        todo("Skru fast gelenderet på trappa", null, "Kari", 60, "Lars", 28, "Nye skruer, sitter som et skudd.");
    }

    private void todo(String title, String description, String by, int createdDaysAgo, String doneBy, int doneDaysAgo, String note) {

        var done = doneBy != null;
        var saved = todos.save(new Todo(null, title, description, done, doneBy, done ? at(today.minusDays(doneDaysAgo)) : null, by, null,
            done ? List.of(doneBy) : null, note, null));
        backdate("todo", saved.id(), today.minusDays(createdDaysAgo));
    }

    private void shopping(String me) {

        for (var name : List.of("Dopapir", "Kaffe", "Ved", "Fyrstikker", "Søppelposer", "Oppvaskmiddel", "Lyspærer", "Batterier (AA)")) {
            checklist.save(new ChecklistItem(null, name, null, null));
        }
        record Item(String name, String quantity, String by, int daysAgo, String boughtBy) {
        }
        for (var i : List.of(
            new Item("Kaffe", "2 pakker", "Kari", 3, null),
            new Item("Dopapir", null, me, 5, null),
            new Item("Tennbrikett", null, "Lars", 8, null),
            new Item("Lyspærer E14", "4 stk", "Sofie", 20, null),
            new Item("Søppelposer", "1 rull", "Ola", 35, "Ola"),
            new Item("Oppvaskmiddel", null, me, 40, "Kari"))) {
            var bought = i.boughtBy() != null;
            var saved = shopping.save(new ShoppingItem(null, i.name(), i.quantity(), bought, i.boughtBy(), bought ? at(today.minusDays(i.daysAgo() - 2)) : null,
                i.by(), null, null));
            backdate("shopping_item", saved.id(), today.minusDays(i.daysAgo()));
        }
    }

    private void suggestions(String me) {

        record Idea(String title, String description, String category, String author, List<String> voters, boolean done, int daysAgo) {
        }
        for (var s : List.of(
            new Idea("Bygge gapahuk ved vatnet", "Kunne vært fint for overnatting ute med barna.", "DO", "Lars", List.of("Lars", "Kari", "Sofie"), false, 150),
            new Idea("Ny kajakk", "Den gamle lekker.", "BUY", "Ola", List.of("Ola", "Ingrid", me), false, 80),
            new Idea("Varmepumpe", "Sparer strøm og holder hytta tørr om vinteren.", "BUY", me, List.of(me, "Kari"), false, 45),
            new Idea("Felles dugnadshelg hver høst", null, "DO", "Kari", List.of("Kari", "Lars", "Ola", me), true, 400),
            new Idea("Brettspill til regnværsdager", null, "BUY", "Sofie", List.of("Sofie"), false, 20))) {
            var saved = suggestions.save(new Suggestion(null, s.title(), s.description(), s.category(), s.author(), s.voters(), s.done(), null, null));
            backdate("suggestion", saved.id(), today.minusDays(s.daysAgo()));
        }
    }

    private void forum(String me) {

        var post = post("Hvem tar med tilhenger til dugnaden?", "Vi må kjøre bort kvist og gammelt materiale. Har noen tilhenger de kan ta med?", "Kari", 45);
        reply(post, "Jeg tar med min, den er stor nok til det meste.", "Lars", 44, "👍");
        reply(post, "Supert! Jeg tar med pizza til etterpå.", me, 43, "🎉");
        react(post, null, "👍", "Ola");

        post = post("Strømprisene i vinter", "Skal vi senke grunnvarmen til 8 grader når ingen er der? Det står på 12 nå.", me, 120);
        reply(post, "Enig, men ikke lavere enn 8, ellers fryser rørene i gangen.", "Ola", 119, null);
        reply(post, "Kan vi få en smartplugg så vi kan skru opp før vi kommer?", "Sofie", 117, "❤️");

        post = post("Bilder fra jakta", "Endelig en okse! Takk for et fint lag i år.", "Lars", 100);
        reply(post, "Gratulerer! Gleder meg til elgstek til jul.", "Ingrid", 99, "😂");
        react(post, null, "🎉", "Kari");
        react(post, null, "🎉", me);
    }

    private long post(String title, String body, String author, int daysAgo) {
        var saved = posts.save(new ForumPost(null, title, body, author, null, null, null));
        backdate("forum_post", saved.id(), today.minusDays(daysAgo));
        return saved.id();
    }

    private void reply(long postId, String body, String author, int daysAgo, String reaction) {
        var saved = replies.save(new ForumReply(null, postId, body, author, null, null, null));
        backdate("forum_reply", saved.id(), today.minusDays(daysAgo));
        if (reaction != null) {
            react(null, saved.id(), reaction, "Kari".equals(author) ? "Ola" : "Kari");
        }
    }

    private void react(Long postId, Long replyId, String emoji, String author) {
        reactions.save(new ForumReaction(null, postId, replyId, emoji, author, null, null));
    }

    private void wiki(String me) {

        wiki.save(new WikiPage(null, "Vann og strøm", "Hovedbryteren står i sikringsskapet i gangen. Vannet åpnes med kranen under kjøkkenbenken, "
            + "og må stenges og tømmes om vinteren hvis det er meldt kuldegrader. Vannpumpa er i kjelleren; trykk på den røde knappen hvis det ikke kommer vann.", me, null, null));
        wiki.save(new WikiPage(null, "Peis og ved", "Ved ligger i vedskjulet bak hytta; ta fra venstre side (den eldste). Spjeldet må være åpent "
            + "før du fyrer. Aske i metallbøtta på verandaen, aldri i søppelet.", "Lars", null, null));
        wiki.save(new WikiPage(null, "Søppel og gjenvinning", "Nærmeste returpunkt er ved butikken i sentrum. Matavfall i grønne poser, "
            + "plast i egen pose. Glass og metall i kassen i boden.", "Kari", null, null));
        wiki.save(new WikiPage(null, "Badstua", "Ovnen slås på med bryteren ved døra og bruker ca. 40 minutter. Skru den av når dere er ferdige, "
            + "og la døra stå på gløtt så den tørker.", "Sofie", null, null));
        wiki.save(new WikiPage(null, "Båt og fiske", "Robåten ligger opp ned ved brygga, årene henger i boden. Fiskekort kjøpes på inatur.no. "
            + "Garn skal røktes hver morgen.", "Ola", null, null));
    }

    /** Created on that day: @DateCreated stamps "now" on save, so the history gets its dates afterwards. */
    private void backdate(String table, long id, LocalDate day) {
        jdbc.prepareStatement("UPDATE " + table + " SET created_at = ? WHERE id = ?", statement -> {
            statement.setTimestamp(1, Timestamp.from(at(day)));
            statement.setLong(2, id);
            return statement.executeUpdate();
        });
    }

    private static Instant at(LocalDate day) {
        return day.atTime(LocalTime.of(18, 0)).atZone(OSLO).toInstant();
    }
}
