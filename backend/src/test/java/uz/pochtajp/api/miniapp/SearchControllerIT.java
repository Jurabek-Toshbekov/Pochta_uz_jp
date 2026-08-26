package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;
import uz.pochtajp.support.PostFixtures;

/**
 * Qidiruv (§10): filtrlar, keyset pagination, matn qidiruvi,
 * {@code search_queries} yozilishi, kontaktni ochish.
 */
class SearchControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/miniapp/posts";
    private static final long SEARCHER_TELEGRAM_ID = 505_000_111L;

    private PostFixtures fixtures;
    private UUID ownerId;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new PostFixtures(jdbcTemplate);
        ownerId = fixtures.insertUser(700_000_001L, "owner1", "NONE", 0);
    }

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, SEARCHER_TELEGRAM_ID);
    }

    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(get(PATH + query)
                .header(HttpHeaders.AUTHORIZATION, auth())
                .header("X-Session-Id", UUID.randomUUID().toString())
                .header("X-Platform", "ios"));
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<String> comments(String json) {
        return JsonPath.read(json, "$.items[*].comment");
    }

    private static String cursorOf(String json) {
        return JsonPath.read(json, "$.nextCursor");
    }

    // ------------------------------------------------------------------
    // Ko'rinish qoidalari
    // ------------------------------------------------------------------

    @Test
    @DisplayName("initData'siz — 401 (§1.4)")
    void requiresAuth() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Faqat PUBLISHED, o'chirilmagan va muddati o'tmagan e'lonlar (§10.2)")
    void showsOnlyVisiblePosts() throws Exception {
        fixtures.post(ownerId).comment("ko-rinadi").insert();
        fixtures.post(ownerId).status("PENDING").comment("navbatda").insert();
        fixtures.post(ownerId).status("CLOSED").comment("yopilgan").insert();
        UUID expired = fixtures.post(ownerId)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .comment("muddati-otgan")
                .insert();
        UUID deleted = fixtures.post(ownerId).comment("ochirilgan").insert();
        jdbcTemplate.update("UPDATE posts SET deleted_at = now() WHERE id = ?", deleted);

        search("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].comment").value("ko-rinadi"));

        // Muddati o'tgan e'lon o'chirilmadi — faqat ko'rinmaydi (§1.1)
        Integer stillThere = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts WHERE id = ?", Integer.class, expired);
        assertThat(stillThere).isEqualTo(1);
    }

    @Test
    @DisplayName("Kontakt qidiruv natijasida YO'Q (§6.4, 2-band)")
    void searchResultsHaveNoContacts() throws Exception {
        fixtures.post(ownerId).insert();

        String body = search("").andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("contactTelegram")
                .doesNotContain("contactPhone")
                .doesNotContain("owner_");
    }

    // ------------------------------------------------------------------
    // Filtrlar (§10.1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Tur va yo'nalish filtri")
    void filtersByTypeAndDirection() throws Exception {
        fixtures.post(ownerId).type("CARRY").direction("JP_UZ").insert();
        fixtures.post(ownerId).type("SEND").direction("JP_UZ").deadlineDate(LocalDate.now().plusDays(5)).insert();
        fixtures.post(ownerId).type("CARRY").direction("UZ_JP").route("TAS", "NRT").insert();

        search("?type=CARRY").andExpect(jsonPath("$.totalCount").value(2));
        search("?type=CARRY&direction=UZ_JP").andExpect(jsonPath("$.totalCount").value(1));
        search("?direction=JP_UZ").andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("Aeroport filtri ko'p tanlovli")
    void filtersByMultipleAirports() throws Exception {
        fixtures.post(ownerId).route("NRT", "TAS").insert();
        fixtures.post(ownerId).route("KIX", "TAS").insert();
        fixtures.post(ownerId).route("FUK", "SKD").insert();

        search("?origin=NRT").andExpect(jsonPath("$.totalCount").value(1));
        search("?origin=NRT,KIX").andExpect(jsonPath("$.totalCount").value(2));
        search("?dest=TAS").andExpect(jsonPath("$.totalCount").value(2));
        search("?origin=nrt").andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("Sana oralig'i — CARRY va SEND uchun bir xil ishlaydi")
    void filtersByDateRange() throws Exception {
        fixtures.post(ownerId).departDate(LocalDate.now().plusDays(3)).insert();
        fixtures.post(ownerId).departDate(LocalDate.now().plusDays(10)).insert();
        fixtures.post(ownerId).type("SEND").deadlineDate(LocalDate.now().plusDays(20)).insert();

        String from = LocalDate.now().plusDays(5).toString();
        String to = LocalDate.now().plusDays(15).toString();

        search("?dateFrom=" + from).andExpect(jsonPath("$.totalCount").value(2));
        search("?dateTo=" + to).andExpect(jsonPath("$.totalCount").value(2));
        search("?dateFrom=" + from + "&dateTo=" + to).andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("Kategoriya filtri")
    void filtersByCategories() throws Exception {
        fixtures.post(ownerId).categories((short) 1).insert();
        fixtures.post(ownerId).categories((short) 2, (short) 3).insert();

        search("?categories=1").andExpect(jsonPath("$.totalCount").value(1));
        search("?categories=2").andExpect(jsonPath("$.totalCount").value(1));
        search("?categories=1,3").andExpect(jsonPath("$.totalCount").value(2));
        search("?categories=7").andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("Maksimal narx: \"kelishamiz\" e'lonlari filtrdan chiqib ketmaydi")
    void priceFilterKeepsNegotiable() throws Exception {
        fixtures.post(ownerId).price("1000", "JPY", "PER_KG").insert();
        fixtures.post(ownerId).price("5000", "JPY", "PER_KG").insert();
        fixtures.post(ownerId).negotiable().insert();

        search("?priceMax=2000")
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("Faqat tasdiqlangan foydalanuvchilar")
    void filtersVerifiedOnly() throws Exception {
        UUID verifiedOwner = fixtures.insertUser(700_000_002L, "verified", "PHONE", 10);
        fixtures.post(ownerId).insert();
        fixtures.post(verifiedOwner).insert();

        search("?verifiedOnly=true")
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].verified").value(true))
                .andExpect(jsonPath("$.items[0].verificationLevel").value("PHONE"));
    }

    // ------------------------------------------------------------------
    // Matn qidiruvi (§10.2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Izoh bo'yicha matn qidiruvi tsvector orqali ishlaydi")
    void findsByCommentText() throws Exception {
        fixtures.post(ownerId).comment("Hujjat va noutbuk olib ketaman").insert();
        fixtures.post(ownerId).comment("Faqat kiyim-kechak").insert();

        search("?q=noutbuk")
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].comment").value(
                        org.hamcrest.Matchers.containsString("noutbuk")));
        search("?q=kiyim").andExpect(jsonPath("$.totalCount").value(1));
        search("?q=televizor").andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("unaccent: apostrofsiz yozilgan so'z ham topiladi")
    void findsWithoutApostrophes() throws Exception {
        fixtures.post(ownerId).finalDestination("Farg‘ona").insert();

        search("?q=Fargona").andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("Erkin kiritilgan shahar nomi ham qidiruvga tushadi")
    void findsByFreeCity() throws Exception {
        fixtures.post(ownerId).freeRoute("Yokohama", "Angren").insert();

        search("?q=Yokohama").andExpect(jsonPath("$.totalCount").value(1));
        search("?q=Angren").andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("Maxsus belgili so'rov xatoga olib kelmaydi")
    void handlesSpecialCharactersInQuery() throws Exception {
        fixtures.post(ownerId).comment("hujjat").insert();

        search("?q=%22hujjat%22").andExpect(status().isOk());
        search("?q=a%20OR%20b%20-c").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Saralash va keyset pagination (§10.2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Default tartib — eng yangi")
    void defaultSortIsNewest() throws Exception {
        fixtures.post(ownerId).comment("eski").publishedAt(Instant.now().minus(3, ChronoUnit.DAYS)).insert();
        fixtures.post(ownerId).comment("yangi").publishedAt(Instant.now()).insert();

        search("")
                .andExpect(jsonPath("$.items[0].comment").value("yangi"))
                .andExpect(jsonPath("$.items[1].comment").value("eski"));
    }

    @Test
    @DisplayName("Uchish sanasi bo'yicha — eng yaqini tepada")
    void sortsByDepartDate() throws Exception {
        fixtures.post(ownerId).comment("keyin").departDate(LocalDate.now().plusDays(20)).insert();
        fixtures.post(ownerId).comment("tezda").departDate(LocalDate.now().plusDays(2)).insert();

        search("?sort=DEPART_DATE")
                .andExpect(jsonPath("$.items[0].comment").value("tezda"));
    }

    @Test
    @DisplayName("Arzon — \"kelishamiz\" oxirida turadi")
    void sortsByPriceWithNegotiableLast() throws Exception {
        fixtures.post(ownerId).comment("qimmat").price("5000", "JPY", "PER_KG").insert();
        fixtures.post(ownerId).comment("arzon").price("500", "JPY", "PER_KG").insert();
        fixtures.post(ownerId).comment("kelishamiz").negotiable().insert();

        search("?sort=CHEAPEST")
                .andExpect(jsonPath("$.items[0].comment").value("arzon"))
                .andExpect(jsonPath("$.items[1].comment").value("qimmat"))
                .andExpect(jsonPath("$.items[2].comment").value("kelishamiz"));
    }

    @Test
    @DisplayName("Reyting bo'yicha — ishonch balli yuqori bo'lgani tepada")
    void sortsByRating() throws Exception {
        UUID trusted = fixtures.insertUser(700_000_003L, "trusted", "PHONE", 50);
        fixtures.post(ownerId).comment("oddiy").insert();
        fixtures.post(trusted).comment("ishonchli").insert();

        search("?sort=RATING")
                .andExpect(jsonPath("$.items[0].comment").value("ishonchli"));
    }

    @Test
    @DisplayName("Keyset pagination: sahifalar takrorlanmaydi va o'tkazib yuborilmaydi")
    void paginatesWithKeyset() throws Exception {
        for (int i = 0; i < 5; i++) {
            fixtures.post(ownerId)
                    .comment("post-" + i)
                    .publishedAt(Instant.now().minus(i, ChronoUnit.HOURS))
                    .insert();
        }

        String firstPage = body(search("?size=2"));
        assertThat(comments(firstPage)).containsExactly("post-0", "post-1");
        assertThat((Integer) JsonPath.read(firstPage, "$.totalCount")).isEqualTo(5);

        String secondPage = body(search("?size=2&cursor=" + cursorOf(firstPage)));
        assertThat(comments(secondPage)).containsExactly("post-2", "post-3");
        // Keyingi sahifalarda umumiy son qayta hisoblanmaydi; null maydon
        // javobga umuman qo'shilmaydi (Jackson non_null).
        assertThat(secondPage).doesNotContain("totalCount");

        String thirdPage = body(search("?size=2&cursor=" + cursorOf(secondPage)));
        assertThat(comments(thirdPage)).containsExactly("post-4");
        assertThat(thirdPage).doesNotContain("nextCursor");
    }

    @Test
    @DisplayName("Buzilgan kursor — 400, tushunarli matn")
    void rejectsBrokenCursor() throws Exception {
        search("?cursor=!!!buzilgan!!!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cursor").exists());
    }

    // ------------------------------------------------------------------
    // Analitika (§10.2, §6.1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Har bir qidiruv search_queries jadvaliga yoziladi")
    void writesSearchQueryRow() throws Exception {
        fixtures.post(ownerId).insert();

        search("?type=CARRY&direction=JP_UZ&origin=NRT&dest=TAS&categories=1&priceMax=3000&currency=JPY&q=test")
                .andExpect(status().isOk());

        var row = jdbcTemplate.queryForMap("SELECT * FROM search_queries");
        assertThat(row.get("post_type")).isEqualTo("CARRY");
        assertThat(row.get("direction")).isEqualTo("JP_UZ");
        assertThat(row.get("origin_airport")).isEqualTo("NRT");
        assertThat(row.get("dest_airport")).isEqualTo("TAS");
        assertThat(row.get("price_currency")).isEqualTo("JPY");
        assertThat(row.get("text_query")).isEqualTo("test");
        assertThat(row.get("result_count")).isEqualTo(0);
        assertThat(row.get("latency_ms")).isNotNull();
        assertThat(row.get("user_id")).isNotNull();
    }

    @Test
    @DisplayName("Keyingi sahifalar search_queries'ga qayta yozilmaydi")
    void paginationDoesNotDuplicateSearchRows() throws Exception {
        for (int i = 0; i < 3; i++) {
            fixtures.post(ownerId).publishedAt(Instant.now().minus(i, ChronoUnit.HOURS)).insert();
        }
        String firstPage = search("?size=1").andReturn().getResponse().getContentAsString();
        search("?size=1&cursor=" + JsonPath.read(firstPage, "$.nextCursor").toString());

        Integer rows = jdbcTemplate.queryForObject("SELECT count(*) FROM search_queries", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Natijasiz qidiruv — search_zero_result eventi (§6.1 oltin ma'lumot)")
    void writesZeroResultEvent() throws Exception {
        search("?origin=FUK&dest=UGC").andExpect(jsonPath("$.totalCount").value(0));

        List<String> names = jdbcTemplate.queryForList("SELECT event_name FROM events", String.class);
        assertThat(names).contains("search_performed", "search_zero_result");

        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'search_zero_result'", String.class);
        assertThat(properties).contains("FUK").contains("UGC");

        Integer zeroRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM search_queries WHERE result_count = 0", Integer.class);
        assertThat(zeroRows).isEqualTo(1);
    }

    @Test
    @DisplayName("Natija bo'lsa zero-result eventi yozilmaydi")
    void noZeroEventWhenResultsExist() throws Exception {
        fixtures.post(ownerId).insert();

        search("").andExpect(jsonPath("$.totalCount").value(1));

        List<String> names = jdbcTemplate.queryForList("SELECT event_name FROM events", String.class);
        assertThat(names).contains("search_performed").doesNotContain("search_zero_result");
    }

    // ------------------------------------------------------------------
    // Tafsilot va kontaktni ochish (§6.4, 2-band)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Tafsilot kontaktsiz keladi, ko'rish hisoblagichi oshadi")
    void detailHidesContactAndCountsView() throws Exception {
        UUID postId = fixtures.post(ownerId).insert();

        mockMvc.perform(get(PATH + "/" + postId).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.id").value(postId.toString()))
                .andExpect(jsonPath("$.own").value(false))
                .andExpect(jsonPath("$.contactRevealed").value(false))
                .andExpect(jsonPath("$.deepLink").value(
                        org.hamcrest.Matchers.containsString("startapp=ch_" + postId)))
                .andExpect(jsonPath("$.post.contactTelegram").doesNotExist());

        Integer views = jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Integer.class, postId);
        assertThat(views).isEqualTo(1);
    }

    @Test
    @DisplayName("Ko'rinmaydigan e'lon tafsiloti — 404")
    void detailOfInvisiblePostIsNotFound() throws Exception {
        UUID pending = fixtures.post(ownerId).status("PENDING").insert();

        mockMvc.perform(get(PATH + "/" + pending).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bog'lanish — kontakt ochiladi, contact_reveals yoziladi, event ketadi")
    void revealContactRecordsIntent() throws Exception {
        UUID postId = fixtures.post(ownerId).insert();

        mockMvc.perform(post(PATH + "/" + postId + "/reveal-contact")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .header("X-Platform", "android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegram").value(
                        org.hamcrest.Matchers.startsWith("owner_")))
                .andExpect(jsonPath("$.alreadyRevealed").value(false));

        Integer reveals = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM contact_reveals WHERE post_id = ?", Integer.class, postId);
        assertThat(reveals).isEqualTo(1);

        Integer counter = jdbcTemplate.queryForObject(
                "SELECT contact_reveal_count FROM posts WHERE id = ?", Integer.class, postId);
        assertThat(counter).isEqualTo(1);

        List<String> names = jdbcTemplate.queryForList("SELECT event_name FROM events", String.class);
        assertThat(names).contains("contact_reveal");
    }

    @Test
    @DisplayName("Takroriy bosish fill rate'ni shishirmaydi")
    void repeatedRevealIsCountedOnce() throws Exception {
        UUID postId = fixtures.post(ownerId).insert();

        mockMvc.perform(post(PATH + "/" + postId + "/reveal-contact")
                .header(HttpHeaders.AUTHORIZATION, auth()));
        mockMvc.perform(post(PATH + "/" + postId + "/reveal-contact")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRevealed").value(true));

        Integer reveals = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM contact_reveals WHERE post_id = ?", Integer.class, postId);
        assertThat(reveals).isEqualTo(1);

        Integer counter = jdbcTemplate.queryForObject(
                "SELECT contact_reveal_count FROM posts WHERE id = ?", Integer.class, postId);
        assertThat(counter).isEqualTo(1);
    }

    @Test
    @DisplayName("O'z e'loni — kontakt ko'rinadi, lekin metrikaga tushmaydi")
    void ownPostRevealIsNotCounted() throws Exception {
        // Qidiruvchining o'zi egasi bo'lsin: avval sessiya ochib user yaratamiz.
        mockMvc.perform(post("/api/miniapp/session")
                .header(HttpHeaders.AUTHORIZATION, auth())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"));
        UUID selfId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE telegram_id = ?", UUID.class, SEARCHER_TELEGRAM_ID);

        UUID postId = fixtures.post(selfId).insert();

        mockMvc.perform(post(PATH + "/" + postId + "/reveal-contact")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRevealed").value(true));

        Integer reveals = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM contact_reveals", Integer.class);
        assertThat(reveals).isZero();
    }

    @Test
    @DisplayName("Yopilgan e'londa bog'lanish — 404")
    void revealOnClosedPostIsNotFound() throws Exception {
        UUID closed = fixtures.post(ownerId).status("CLOSED").insert();

        mockMvc.perform(post(PATH + "/" + closed + "/reveal-contact")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNotFound());
    }
}
