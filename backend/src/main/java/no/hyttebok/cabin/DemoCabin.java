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
 * relative to today, so the cabin always looks lived in. The admin who makes it becomes its owner. The content is in
 * the language the app is in (Norwegian or English); every text below is written as {@code t(norwegian, english)}.
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

    // For the cabin being made (create is synchronized).
    private LocalDate today;
    private boolean english;

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

    /** {@code lang}: the app's language, "en" for English; anything else gives Norwegian. */
    @Transactional
    public synchronized CabinInfo create(long ownerId, String ownerName, String lang) {

        today = LocalDate.now(OSLO);
        english = "en".equals(lang);
        var cabin = cabins.save(new CabinInfo(null, t("Testhytta Fjellro", "Test cabin Fjellro"),
            t("Familiehytta ved Storevatnet", "The family cabin by Lake Storevatnet"),
            t("Tømmerhytte fra 1978 med fire soverom, badstue og utsikt over vatnet. Bilvei helt fram om sommeren; "
                    + "om vinteren parkerer du ved bommen og går eller går på ski de siste 800 meterne.",
                "Log cabin from 1978 with four bedrooms, a sauna and a view of the lake. You can drive all the way in summer; "
                    + "in winter, park at the gate and walk or ski the last 800 metres."),
            "Fjellroveien 12, 3560 Hemsedal", t("Fjellro-gjest", "Fjellro-guest"), t("Badstue2024!", "Sauna2024!"),
            t("Nøkkelen ligger i nøkkelboksen ved inngangsdøra (kode 4512). Slå på hovedbryteren i gangen og åpne vannet under kjøkkenbenken.",
                "The key is in the key box by the front door (code 4512). Switch on the main breaker in the hall and open the water under the kitchen counter."),
            t("Steng vannet, tøm kjøleskapet og ta med søppelet. Sett varmen på 10 grader og lås nøkkelen inn i boksen igjen.",
                "Turn off the water, empty the fridge and take the rubbish with you. Set the heating to 10 degrees and lock the key back in the box."),
            null, List.of(), 60.8623, 8.5532, 812.0, true,
            t("- Kari og Ola har hver sin nøkkel\n- Reservenøkkelen henger i boden, på spikeren bak døra\n- Nøkkelboks ved inngangsdøra: kode 4512",
                "- Kari and Ola each have a key\n- The spare key hangs in the shed, on the nail behind the door\n- Key box by the front door: code 4512")));
        memberships.save(new Membership(null, ownerId, cabin.id(), Membership.OWNER, Instant.now()));

        CabinContext.runAs(cabin.id(), () -> fill(ownerName.split("\\s+")[0]));

        return cabin;
    }

    private String t(String norwegian, String english) {
        return this.english ? english : norwegian;
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
        var winter = t("Vinterferie", "Winter break");
        var hunt = t("Elgjakt", "Moose hunt");
        var olaAndIngrid = t("Ola og Ingrid", "Ola and Ingrid");
        var stays = List.of(
            new Stay(700, 7, "Kari", 4, winter),
            new Stay(610, 3, me, 2, null),
            new Stay(540, 10, olaAndIngrid, 5, t("Fellesferie, del 1", "Summer holiday, part 1")),
            new Stay(470, 4, "Lars", 3, hunt),
            new Stay(380, 3, me, 6, t("Juletur med hele gjengen", "Christmas with the whole gang")),
            new Stay(330, 7, "Kari", 4, winter),
            new Stay(250, 4, "Sofie", 2, t("Påske", "Easter")),
            new Stay(175, 10, olaAndIngrid, 5, t("Fellesferie", "Summer holiday")),
            new Stay(105, 4, "Lars", 3, hunt),
            new Stay(30, 3, me, 4, t("Dugnadshelg", "Work weekend")),
            new Stay(-12, 3, "Sofie", 2, null),
            new Stay(-45, 5, me, 6, t("Juletur", "Christmas")),
            new Stay(-120, 7, "Kari", 4, winter));

        return stays.stream().map(s -> {
            var start = today.minusDays(s.daysAgo());
            var saved = bookings.save(new Booking(null, s.who(), start, start.plusDays(s.nights()), s.guests(), s.note(), null, null, null, null));
            return s.daysAgo() > 0 ? saved.id() : null;
        }).filter(id -> id != null).toList();
    }

    private void cabinBook(String me, List<Long> bookingIds) {

        record Entry(int booking, String author, String title, String content, List<String> with) {
        }
        var entries = List.of(
            new Entry(0, "Kari", t("Snøkaos og skiføre", "Snow chaos and great skiing"),
                t("Kom fram i full snøstorm og måtte måke oss inn. Resten av uka var det strålende sol "
                        + "og perfekt føre. Barna lærte å ta heisen alene i bakken.",
                    "Arrived in a full blizzard and had to dig our way in. The rest of the week was "
                        + "glorious sunshine and perfect snow. The kids learned to ride the lift on their own."),
                List.of("Kari", "Per", "Emma", "Jonas")),
            new Entry(2, "Ingrid", t("Fellesferie ved vatnet", "Summer by the lake"),
                t("Badet hver dag selv om vatnet var 16 grader. Fikk tre ørret på garnet, og Ola fikk endelig fikset benken på brygga.",
                    "Swam every day even though the lake was 16 degrees. Caught three trout in the net, and Ola finally fixed the bench on the jetty."),
                List.of("Ola", "Ingrid", "Mia", "Theo", t("Bestemor", "Grandma"))),
            new Entry(3, "Lars", t("Elgjakt", "Moose hunt"),
                t("Ingen elg i år, men mye fin tur. Vedboden er fylt opp igjen.", "No moose this year, but lots of good walks. The woodshed is full again."),
                List.of("Lars", "Henrik", "Tore")),
            new Entry(4, me, t("Jul på hytta", "Christmas at the cabin"),
                t("Ribbe, pinnekjøtt og altfor mye konfekt. Strømmen gikk på julaften, så vi spiste i lys fra peisen. Ingen klaget.",
                    "Roast pork, lamb ribs and far too many sweets. The power went out on Christmas Eve, so we ate by the light of the fireplace. "
                        + "Nobody complained."),
                List.of(me, "Kari", "Ola", "Ingrid", "Lars", "Sofie")),
            new Entry(6, "Sofie", t("Påskefjellet", "Easter in the mountains"),
                t("Lange turer til Storefjell, appelsin og Kvikk Lunsj på toppen. Brannslukkeren er sjekket.",
                    "Long trips up Storefjell, oranges and chocolate at the top. The fire extinguisher is checked."),
                List.of("Sofie", "Marius")),
            new Entry(7, "Ola", t("Sommer igjen", "Summer again"),
                t("Malte nordveggen og byttet to takstein. Myggen var verre enn i fjor.",
                    "Painted the north wall and replaced two roof tiles. The mosquitoes were worse than last year."),
                List.of("Ola", "Ingrid", "Mia", "Theo")),
            new Entry(8, "Lars", t("Jakt og sopp", "Hunting and mushrooms"),
                t("Felte en okse første dag! Fant også masse kantarell bak låven.", "Got a bull on the first day! Also found loads of chanterelles behind the barn."),
                List.of("Lars", "Henrik")),
            new Entry(9, me, t("Dugnad", "Work weekend"),
                t("Ryddet kratt, renset takrennene og byttet batterier i røykvarslerne. Pizza i badstua etterpå.",
                    "Cleared brush, cleaned the gutters and changed the smoke alarm batteries. Pizza in the sauna afterwards."),
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
            new Task(t("Feie pipa", "Sweep the chimney"),
                t("Feieren kommer på forespørsel; ring kommunen i god tid.", "The sweep comes on request; call the council well ahead."), 1, "YEARS", 690, 330),
            new Task(t("Bytte batterier i røykvarslerne", "Change the smoke alarm batteries"),
                t("Fire varslere: gangen, stua og begge soverommene oppe.", "Four alarms: the hall, the living room and both bedrooms upstairs."), 1, "YEARS", 400, 30),
            new Task(t("Rense takrennene", "Clean the gutters"), null, 1, "YEARS", 560, 200),
            new Task(t("Sjekke brannslukkeren", "Check the fire extinguisher"),
                t("Rist den og sjekk at nålen står på grønt.", "Shake it and check that the needle is on green."), 6, "MONTHS", 520, 340, 250),
            new Task(t("Bytte vannfilter", "Change the water filter"),
                t("Filteret står under kjøkkenbenken. Nye filtre ligger i boden.", "The filter is under the kitchen counter. New filters are in the shed."),
                3, "MONTHS", 300, 210),
            new Task(t("Beise terrassen", "Stain the deck"), null, 2, "YEARS", 600),
            new Task(t("Tømme slamavskilleren", "Empty the septic tank"), t("Avtales med kommunen.", "Book it with the council."), 2, "YEARS"));

        String[] who = {me, "Kari", "Ola", "Lars", "Sofie"};
        var n = 0;
        for (var task : tasks) {
            var saved = maintenance.save(new Maintenance(null, task.name(), task.description(), task.every(), task.unit(), null, null, null));
            backdate("maintenance", saved.id(), today.minusDays(720));
            LocalDate last = null;
            for (var daysAgo : task.doneDaysAgo()) {
                var on = today.minusDays(daysAgo);
                var by = who[n++ % who.length];
                var done = completions.save(new MaintenanceCompletion(null, saved.id(), on, by, null, List.of(by),
                    n % 2 == 0 ? t("Alt i orden.", "All good.") : null, null));
                backdate("maintenance_completion", done.id(), on);
                last = on;
            }
            maintenance.update(saved.id(), last);
        }
    }

    private void todos(String me) {

        todo(t("Fikse porten", "Fix the gate"), t("Hengslet nederst har løsnet.", "The bottom hinge has come loose."), "Ola", 90, null, 0, null);
        todo(t("Skifte pakning på kjøkkenkrana", "Replace the washer in the kitchen tap"), t("Den drypper.", "It drips."), "Kari", 40, null, 0, null);
        todo(t("Kjøpe ny grill", "Buy a new grill"), null, me, 12, null, 0, null);
        todo(t("Male utedoen", "Paint the outhouse"), null, "Sofie", 200, null, 0, null);
        todo(t("Bytte lyspære i badstua", "Change the light bulb in the sauna"), null, "Lars", 320, "Lars", 300,
            t("Tok med to ekstra, de ligger i skuffen.", "Brought two spares, they're in the drawer."));
        todo(t("Montere ny vedovn", "Install a new wood stove"), t("Den gamle sprakk i fjor vinter.", "The old one cracked last winter."), me, 420, me, 360,
            t("Ny Jøtul på plass, godkjent av feieren.", "New Jøtul in place, approved by the sweep."));
        todo(t("Fjerne vepsebol under taket", "Remove the wasp nest under the roof"), null, "Ingrid", 190, "Ola", 170, null);
        todo(t("Skru fast gelenderet på trappa", "Tighten the stair railing"), null, "Kari", 60, "Lars", 28,
            t("Nye skruer, sitter som et skudd.", "New screws, solid as a rock."));
    }

    private void todo(String title, String description, String by, int createdDaysAgo, String doneBy, int doneDaysAgo, String note) {

        var done = doneBy != null;
        var saved = todos.save(new Todo(null, title, description, done, doneBy, done ? at(today.minusDays(doneDaysAgo)) : null, by, null,
            done ? List.of(doneBy) : null, note, null, null));
        backdate("todo", saved.id(), today.minusDays(createdDaysAgo));
    }

    private void shopping(String me) {

        var toiletPaper = t("Dopapir", "Toilet paper");
        var coffee = t("Kaffe", "Coffee");
        var binBags = t("Søppelposer", "Bin bags");
        var washingUp = t("Oppvaskmiddel", "Washing-up liquid");
        for (var name : List.of(toiletPaper, coffee, t("Ved", "Firewood"), t("Fyrstikker", "Matches"), binBags, washingUp,
            t("Lyspærer", "Light bulbs"), t("Batterier (AA)", "Batteries (AA)"))) {
            checklist.save(new ChecklistItem(null, name, null, null));
        }
        record Item(String name, String quantity, String by, int daysAgo, String boughtBy) {
        }
        for (var i : List.of(
            new Item(coffee, t("2 pakker", "2 packs"), "Kari", 3, null),
            new Item(toiletPaper, null, me, 5, null),
            new Item(t("Tennbrikett", "Fire lighters"), null, "Lars", 8, null),
            new Item(t("Lyspærer E14", "Light bulbs E14"), t("4 stk", "4"), "Sofie", 20, null),
            new Item(binBags, t("1 rull", "1 roll"), "Ola", 35, "Ola"),
            new Item(washingUp, null, me, 40, "Kari"))) {
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
            new Idea(t("Bygge gapahuk ved vatnet", "Build a lean-to by the lake"),
                t("Kunne vært fint for overnatting ute med barna.", "Would be great for sleeping outside with the kids."),
                "DO", "Lars", List.of("Lars", "Kari", "Sofie"), false, 150),
            new Idea(t("Ny kajakk", "New kayak"), t("Den gamle lekker.", "The old one leaks."), "BUY", "Ola", List.of("Ola", "Ingrid", me), false, 80),
            new Idea(t("Varmepumpe", "Heat pump"), t("Sparer strøm og holder hytta tørr om vinteren.", "Saves power and keeps the cabin dry in winter."),
                "BUY", me, List.of(me, "Kari"), false, 45),
            new Idea(t("Felles dugnadshelg hver høst", "A shared work weekend every autumn"), null, "DO", "Kari", List.of("Kari", "Lars", "Ola", me), true, 400),
            new Idea(t("Brettspill til regnværsdager", "Board games for rainy days"), null, "BUY", "Sofie", List.of("Sofie"), false, 20))) {
            var saved = suggestions.save(new Suggestion(null, s.title(), s.description(), s.category(), s.author(), s.voters(), s.done(), null, null));
            backdate("suggestion", saved.id(), today.minusDays(s.daysAgo()));
        }
    }

    private void forum(String me) {

        var post = post(t("Hvem tar med tilhenger til dugnaden?", "Who's bringing a trailer to the work weekend?"),
            t("Vi må kjøre bort kvist og gammelt materiale. Har noen tilhenger de kan ta med?",
                "We need to haul away branches and old timber. Does anyone have a trailer they can bring?"), "Kari", 45);
        reply(post, t("Jeg tar med min, den er stor nok til det meste.", "I'll bring mine, it's big enough for most of it."), "Lars", 44, "👍");
        reply(post, t("Supert! Jeg tar med pizza til etterpå.", "Great! I'll bring pizza for afterwards."), me, 43, "🎉");
        react(post, null, "👍", "Ola");

        post = post(t("Strømprisene i vinter", "Power prices this winter"),
            t("Skal vi senke grunnvarmen til 8 grader når ingen er der? Det står på 12 nå.",
                "Should we turn the background heat down to 8 degrees when nobody is there? It's on 12 now."), me, 120);
        reply(post, t("Enig, men ikke lavere enn 8, ellers fryser rørene i gangen.", "Agreed, but no lower than 8, or the pipes in the hall will freeze."),
            "Ola", 119, null);
        reply(post, t("Kan vi få en smartplugg så vi kan skru opp før vi kommer?", "Could we get a smart plug so we can turn it up before we arrive?"),
            "Sofie", 117, "❤️");

        post = post(t("Bilder fra jakta", "Photos from the hunt"),
            t("Endelig en okse! Takk for et fint lag i år.", "Finally a bull! Thanks for a great team this year."), "Lars", 100);
        reply(post, t("Gratulerer! Gleder meg til elgstek til jul.", "Congratulations! Looking forward to moose roast at Christmas."), "Ingrid", 99, "😂");
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

        page(t("Vann og strøm", "Water and power"),
            t("Hovedbryteren står i sikringsskapet i gangen. Vannet åpnes med kranen under kjøkkenbenken, og må stenges og tømmes om vinteren "
                    + "hvis det er meldt kuldegrader. Vannpumpa er i kjelleren; trykk på den røde knappen hvis det ikke kommer vann.",
                "The main breaker is in the fuse box in the hall. The water is opened with the tap under the kitchen counter, and must be shut off "
                    + "and drained in winter if frost is forecast. The water pump is in the basement; press the red button if no water comes."), me);
        page(t("Peis og ved", "Fireplace and firewood"),
            t("Ved ligger i vedskjulet bak hytta; ta fra venstre side (den eldste). Spjeldet må være åpent før du fyrer. "
                    + "Aske i metallbøtta på verandaen, aldri i søppelet.",
                "Firewood is in the woodshed behind the cabin; take from the left side (the oldest). The damper must be open before you light "
                    + "the fire. Ash goes in the metal bucket on the porch, never in the rubbish."), "Lars");
        page(t("Søppel og gjenvinning", "Rubbish and recycling"),
            t("Nærmeste returpunkt er ved butikken i sentrum. Matavfall i grønne poser, plast i egen pose. Glass og metall i kassen i boden.",
                "The nearest recycling point is by the shop in the village. Food waste in green bags, plastic in its own bag. "
                    + "Glass and metal in the box in the shed."), "Kari");
        page(t("Badstua", "The sauna"),
            t("Ovnen slås på med bryteren ved døra og bruker ca. 40 minutter. Skru den av når dere er ferdige, og la døra stå på gløtt så den tørker.",
                "The heater is switched on by the door and takes about 40 minutes. Turn it off when you're done, and leave the door ajar so it dries."),
            "Sofie");
        page(t("Båt og fiske", "Boat and fishing"),
            t("Robåten ligger opp ned ved brygga, årene henger i boden. Fiskekort kjøpes på inatur.no. Garn skal røktes hver morgen.",
                "The rowing boat lies upside down by the jetty, the oars hang in the shed. Fishing licences are bought at inatur.no. "
                    + "Nets must be checked every morning."), "Ola");
    }

    private void page(String title, String content, String by) {
        wiki.save(new WikiPage(null, title, content, by, null, null));
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
