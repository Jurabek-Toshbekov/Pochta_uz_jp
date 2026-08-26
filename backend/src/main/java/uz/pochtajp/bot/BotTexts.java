package uz.pochtajp.bot;

import java.util.Map;

/**
 * Bot matnlari uch tilda (§8.2, §16.6).
 *
 * <p>Ruh: bot jerkimaydi, kamsitmaydi, emoji bilan g'azablanmaydi (§2, 8-nuqson).
 * Har bir javob nima qilish kerakligini aytadi.
 *
 * <p>Matnlar Telegram HTML {@code parse_mode} bilan yuboriladi — shu sabab
 * ular ichida faqat ishonchli teglar bor, foydalanuvchi matni esa
 * {@link uz.pochtajp.common.TelegramHtml#escape} orqali qo'shiladi (§7.2).
 */
public final class BotTexts {

    private BotTexts() {
    }

    /**
     * @param start  {@code %s} — foydalanuvchi ismi
     */
    public record Pack(
            String start,
            String btnNewPost,
            String btnSearch,
            String btnMyPosts,
            String btnOpenApp,
            String newPostPrompt,
            String searchPrompt,
            String myPostsPrompt,
            String subscriptionsSoon,
            String safety,
            String rules,
            String languageTitle,
            String languageChanged,
            String help,
            String myData,
            String btnExportData,
            String btnDeleteData,
            String btnCancel,
            String dataExportCaption,
            String dataDeleteConfirm,
            String dataDeleted,
            String dataDeleteCancelled,
            String freeText,
            String media,
            String unknownCommand,
            String blocked,
            String miniappNotConfigured,
            String genericError
    ) {
    }

    private static final Pack UZ = new Pack(
            """
            Assalomu alaykum, %s!

            Bu bot Yaponiya ↔ O'zbekiston yo'nalishida pochta yuborish va
            olib ketish e'lonlari uchun.

            <b>Qanday ishlaydi:</b>
            1. Quyidagi tugmani bosing — ilova ochiladi
            2. 1 daqiqada e'lonni to'ldirasiz
            3. E'lon kanalga chiqadi

            Kanaldan yoki ilova ichidagi qidiruvdan o'zingizga mos odamni topasiz.

            ⚠️ <b>Muhim:</b> pochta olayotganda yopiq qutini qabul qilmang.
            Batafsil: /xavfsizlik""",
            "📝 E'lon berish",
            "🔍 Qidirish",
            "📋 Mening e'lonlarim",
            "📱 Ilovani ochish",
            "E'lon berish uchun quyidagi tugmani bosing. Forma 4 qadamdan iborat, 1 daqiqa vaqt oladi.",
            "Qidiruv ilova ichida ishlaydi. Tugmani bosing va filtrlarni tanlang.",
            "E'lonlaringiz ilovada ko'rinadi — statusi, ko'rilgan soni va bog'lanishlar bilan.",
            """
            🔔 <b>Xabarnoma obunalari</b>

            Bu funksiya hozir tayyorlanmoqda. Ishga tushganda sizga mos e'lon
            chiqishi bilan xabar beramiz.

            Hozircha e'lonlarni kanaldan yoki ilova ichidagi qidiruvdan kuzatib turing.""",
            """
            ⚠️ <b>Xavfsizlik qoidalari</b>

            <b>Uchta qoida — buzmang:</b>
            1. Yopiq yoki o'ralgan qutini qabul qilmang. Yuk egasi oldida ochib ko'ring.
            2. Yukda nima borligini o'z ko'zingiz bilan ko'ring.
            3. Ishonchsiz odam bilan ishlamang — reyting va sharhlarga qarang.

            <b>Olib ketish taqiqlangan:</b>
            • giyohvand va psixotrop moddalar
            • o'q-dori va qurol
            • go'sht va sut mahsulotlari (Yaponiyaga kirish taqiqlangan)
            • retseptli dorilar va psevdoefedrin tarkibli preparatlar
            • ko'p miqdordagi naqd pul
            • o'simlik va urug'lar
            • soxta brend mahsulotlar

            Taqiqlangan buyum olib o'tsangiz javobgarlik <b>sizda</b> bo'ladi —
            yuk egasida emas.

            Shubhali e'lonni ko'rsangiz "Shikoyat qilish" tugmasini bosing.""",
            """
            📋 <b>Qoidalar</b>

            1. Bitta e'lon — bitta yo'nalish. Takroriy e'lon o'chiriladi.
            2. Kuniga 5 tadan ko'p e'lon berilmaydi.
            3. Narxni aniq yozing: summa, valyuta va hisob birligi.
            4. Reklama, savdo, boshqa xizmatlar taqiqlangan.
            5. Haqorat va aldov — hisob bloklanadi.
            6. Taqiqlangan buyumlar ro'yxati: /xavfsizlik

            Bot va ilova faqat e'lonlarni bir joyga to'playdi. Kelishuv,
            to'lov va yukning o'zi — tomonlarning javobgarligida.""",
            "Tilni tanlang:",
            "✅ Til o'zgartirildi.",
            """
            ❓ <b>Ko'p so'raladigan savollar</b>

            <b>E'lon qanday beriladi?</b>
            /elon — ilova ochiladi, 4 qadamli formani to'ldirasiz.

            <b>Kontaktim hammaga ko'rinadimi?</b>
            Yo'q. Kanalda kontakt ko'rsatilmaydi. U faqat kimdir
            "Bog'lanish" tugmasini bosganda ochiladi.

            <b>E'lonim qachon o'chadi?</b>
            Sana o'tgandan bir kun keyin avtomatik yopiladi. Undan oldin
            o'zingiz ham yopa olasiz.

            <b>Xato yozib qo'ydim, tuzatsam bo'ladimi?</b>
            Ha. Ilovada "Mening e'lonlarim" → tahrirlash.

            <b>Ma'lumotlarimni ko'rsam yoki o'chirsam bo'ladimi?</b>
            /mening_malumotlarim

            <b>Boshqa savol?</b>
            Qoidalar: /qoidalar · Xavfsizlik: /xavfsizlik""",
            """
            🗂 <b>Mening ma'lumotlarim</b>

            Siz haqingizda saqlanadigan ma'lumotni yuklab olishingiz yoki
            o'chirishni so'rashingiz mumkin.

            <b>Yuklab olish</b> — profilingiz, e'lonlaringiz va sozlamalaringiz
            JSON fayl ko'rinishida yuboriladi.

            <b>O'chirish</b> — ismingiz, username'ingiz va telefon raqamingiz
            o'chiriladi, faol e'lonlaringiz yopiladi. Bu amalni qaytarib
            bo'lmaydi.""",
            "📥 Ma'lumotlarimni yuklab olish",
            "🗑 Ma'lumotlarimni o'chirish",
            "Bekor qilish",
            "Sizning ma'lumotlaringiz. Faylni saqlab qo'ying — u qayta yuborilmaydi.",
            """
            ⚠️ <b>O'chirishni tasdiqlaysizmi?</b>

            Ismingiz, username'ingiz va telefon raqamingiz o'chiriladi,
            faol e'lonlaringiz yopiladi.

            Bu amalni qaytarib bo'lmaydi.""",
            "✅ Ma'lumotlaringiz o'chirildi. Yana e'lon bermoqchi bo'lsangiz /start dan boshlang.",
            "Bekor qilindi. Hech narsa o'chirilmadi.",
            "Men buyruqlar bilan ishlayman. E'lon berish uchun quyidagi tugmani bosing.",
            "Rasm va fayllarni ilova orqali yuborasiz. Pastdagi tugmani bosing 🙂",
            "Bunday buyruq yo'q. Buyruqlar ro'yxati: /yordam",
            "Hisobingiz bloklangan. Sabab bo'yicha /qoidalar bilan tanishib chiqing.",
            "Ilova hozir sozlanmoqda. Birozdan keyin qayta urinib ko'ring.",
            "Xatolik yuz berdi. Birozdan keyin qayta urinib ko'ring."
    );

    private static final Pack UZ_CYRL = new Pack(
            """
            Ассалому алайкум, %s!

            Бу бот Япония ↔ Ўзбекистон йўналишида почта юбориш ва
            олиб кетиш эълонлари учун.

            <b>Қандай ишлайди:</b>
            1. Қуйидаги тугмани босинг — илова очилади
            2. 1 дақиқада эълонни тўлдирасиз
            3. Эълон каналга чиқади

            Каналдан ёки илова ичидаги қидирувдан ўзингизга мос одамни топасиз.

            ⚠️ <b>Муҳим:</b> почта олаётганда ёпиқ қутини қабул қилманг.
            Батафсил: /xavfsizlik""",
            "📝 Эълон бериш",
            "🔍 Қидириш",
            "📋 Менинг эълонларим",
            "📱 Иловани очиш",
            "Эълон бериш учун қуйидаги тугмани босинг. Форма 4 қадамдан иборат, 1 дақиқа вақт олади.",
            "Қидирув илова ичида ишлайди. Тугмани босинг ва фильтрларни танланг.",
            "Эълонларингиз иловада кўринади — статуси, кўрилган сони ва боғланишлар билан.",
            """
            🔔 <b>Хабарнома обуналари</b>

            Бу функция ҳозир тайёрланмоқда. Ишга тушганда сизга мос эълон
            чиқиши билан хабар берамиз.

            Ҳозирча эълонларни каналдан ёки илова ичидаги қидирувдан кузатиб туринг.""",
            """
            ⚠️ <b>Хавфсизлик қоидалари</b>

            <b>Учта қоида — бузманг:</b>
            1. Ёпиқ ёки ўралган қутини қабул қилманг. Юк эгаси олдида очиб кўринг.
            2. Юкда нима борлигини ўз кўзингиз билан кўринг.
            3. Ишончсиз одам билан ишламанг — рейтинг ва шарҳларга қаранг.

            <b>Олиб кетиш тақиқланган:</b>
            • гиёҳванд ва психотроп моддалар
            • ўқ-дори ва қурол
            • гўшт ва сут маҳсулотлари (Японияга кириш тақиқланган)
            • рецептли дорилар ва псевдоэфедрин таркибли препаратлар
            • кўп миқдордаги нақд пул
            • ўсимлик ва уруғлар
            • сохта бренд маҳсулотлар

            Тақиқланган буюм олиб ўтсангиз жавобгарлик <b>сизда</b> бўлади —
            юк эгасида эмас.

            Шубҳали эълонни кўрсангиз "Шикоят қилиш" тугмасини босинг.""",
            """
            📋 <b>Қоидалар</b>

            1. Битта эълон — битта йўналиш. Такрорий эълон ўчирилади.
            2. Кунига 5 тадан кўп эълон берилмайди.
            3. Нархни аниқ ёзинг: сумма, валюта ва ҳисоб бирлиги.
            4. Реклама, савдо, бошқа хизматлар тақиқланган.
            5. Ҳақорат ва алдов — ҳисоб блокланади.
            6. Тақиқланган буюмлар рўйхати: /xavfsizlik

            Бот ва илова фақат эълонларни бир жойга тўплайди. Келишув,
            тўлов ва юкнинг ўзи — томонларнинг жавобгарлигида.""",
            "Тилни танланг:",
            "✅ Тил ўзгартирилди.",
            """
            ❓ <b>Кўп сўраладиган саволлар</b>

            <b>Эълон қандай берилади?</b>
            /elon — илова очилади, 4 қадамли формани тўлдирасиз.

            <b>Контактим ҳаммага кўринадими?</b>
            Йўқ. Каналда контакт кўрсатилмайди. У фақат кимдир
            "Боғланиш" тугмасини босганда очилади.

            <b>Эълоним қачон ўчади?</b>
            Сана ўтгандан бир кун кейин автоматик ёпилади. Ундан олдин
            ўзингиз ҳам ёпа оласиз.

            <b>Хато ёзиб қўйдим, тузатсам бўладими?</b>
            Ҳа. Иловада "Менинг эълонларим" → таҳрирлаш.

            <b>Маълумотларимни кўрсам ёки ўчирсам бўладими?</b>
            /mening_malumotlarim

            <b>Бошқа савол?</b>
            Қоидалар: /qoidalar · Хавфсизлик: /xavfsizlik""",
            """
            🗂 <b>Менинг маълумотларим</b>

            Сиз ҳақингизда сақланадиган маълумотни юклаб олишингиз ёки
            ўчиришни сўрашингиз мумкин.

            <b>Юклаб олиш</b> — профилингиз, эълонларингиз ва созламаларингиз
            JSON файл кўринишида юборилади.

            <b>Ўчириш</b> — исмингиз, username'ингиз ва телефон рақамингиз
            ўчирилади, фаол эълонларингиз ёпилади. Бу амални қайтариб
            бўлмайди.""",
            "📥 Маълумотларимни юклаб олиш",
            "🗑 Маълумотларимни ўчириш",
            "Бекор қилиш",
            "Сизнинг маълумотларингиз. Файлни сақлаб қўйинг — у қайта юборилмайди.",
            """
            ⚠️ <b>Ўчиришни тасдиқлайсизми?</b>

            Исмингиз, username'ингиз ва телефон рақамингиз ўчирилади,
            фаол эълонларингиз ёпилади.

            Бу амални қайтариб бўлмайди.""",
            "✅ Маълумотларингиз ўчирилди. Яна эълон бермоқчи бўлсангиз /start дан бошланг.",
            "Бекор қилинди. Ҳеч нарса ўчирилмади.",
            "Мен буйруқлар билан ишлайман. Эълон бериш учун қуйидаги тугмани босинг.",
            "Расм ва файлларни илова орқали юборасиз. Пастдаги тугмани босинг 🙂",
            "Бундай буйруқ йўқ. Буйруқлар рўйхати: /yordam",
            "Ҳисобингиз блокланган. Сабаб бўйича /qoidalar билан танишиб чиқинг.",
            "Илова ҳозир созланмоқда. Бироздан кейин қайта уриниб кўринг.",
            "Хатолик юз берди. Бироздан кейин қайта уриниб кўринг."
    );

    private static final Pack RU = new Pack(
            """
            Здравствуйте, %s!

            Этот бот — для объявлений о посылках по направлению
            Япония ↔ Узбекистан.

            <b>Как это работает:</b>
            1. Нажмите кнопку ниже — откроется приложение
            2. За минуту заполните объявление
            3. Объявление появится в канале

            Подходящего человека найдёте в канале или через поиск в приложении.

            ⚠️ <b>Важно:</b> не принимайте закрытую коробку.
            Подробнее: /xavfsizlik""",
            "📝 Разместить объявление",
            "🔍 Поиск",
            "📋 Мои объявления",
            "📱 Открыть приложение",
            "Нажмите кнопку ниже. Форма состоит из 4 шагов и занимает около минуты.",
            "Поиск работает в приложении. Нажмите кнопку и выберите фильтры.",
            "Ваши объявления видны в приложении — со статусом, просмотрами и контактами.",
            """
            🔔 <b>Подписки на уведомления</b>

            Функция готовится. Когда она заработает, мы сообщим вам о новом
            подходящем объявлении.

            Пока следите за объявлениями в канале или через поиск в приложении.""",
            """
            ⚠️ <b>Правила безопасности</b>

            <b>Три правила — не нарушайте:</b>
            1. Не принимайте закрытую или запакованную коробку. Откройте при владельце груза.
            2. Своими глазами посмотрите, что внутри.
            3. Не работайте с ненадёжными людьми — смотрите рейтинг и отзывы.

            <b>Запрещено перевозить:</b>
            • наркотические и психотропные вещества
            • боеприпасы и оружие
            • мясные и молочные продукты (запрещён ввоз в Японию)
            • рецептурные лекарства и препараты с псевдоэфедрином
            • крупные суммы наличных
            • растения и семена
            • поддельные брендовые товары

            За запрещённый предмет отвечаете <b>вы</b>, а не владелец груза.

            Увидели подозрительное объявление — нажмите «Пожаловаться».""",
            """
            📋 <b>Правила</b>

            1. Одно объявление — одно направление. Дубли удаляются.
            2. Не более 5 объявлений в день.
            3. Указывайте цену точно: сумму, валюту и единицу расчёта.
            4. Реклама, торговля и сторонние услуги запрещены.
            5. Оскорбления и обман — блокировка аккаунта.
            6. Список запрещённых предметов: /xavfsizlik

            Бот и приложение только собирают объявления в одном месте.
            Договорённости, оплата и сам груз — ответственность сторон.""",
            "Выберите язык:",
            "✅ Язык изменён.",
            """
            ❓ <b>Частые вопросы</b>

            <b>Как разместить объявление?</b>
            /elon — откроется приложение с формой из 4 шагов.

            <b>Мой контакт видят все?</b>
            Нет. В канале контакт не показывается. Он открывается только
            когда кто-то нажимает «Связаться».

            <b>Когда объявление закроется?</b>
            Через день после указанной даты — автоматически. Раньше можно
            закрыть самому.

            <b>Ошибся при заполнении, можно исправить?</b>
            Да. В приложении «Мои объявления» → редактировать.

            <b>Можно посмотреть или удалить мои данные?</b>
            /mening_malumotlarim

            <b>Другой вопрос?</b>
            Правила: /qoidalar · Безопасность: /xavfsizlik""",
            """
            🗂 <b>Мои данные</b>

            Вы можете скачать данные, которые о вас хранятся, или запросить
            их удаление.

            <b>Скачать</b> — профиль, объявления и настройки придут файлом JSON.

            <b>Удалить</b> — имя, username и номер телефона будут удалены,
            активные объявления закрыты. Действие необратимо.""",
            "📥 Скачать мои данные",
            "🗑 Удалить мои данные",
            "Отмена",
            "Ваши данные. Сохраните файл — повторно он не отправляется.",
            """
            ⚠️ <b>Подтверждаете удаление?</b>

            Имя, username и номер телефона будут удалены, активные
            объявления закрыты.

            Действие необратимо.""",
            "✅ Данные удалены. Чтобы разместить объявление снова, начните с /start.",
            "Отменено. Ничего не удалено.",
            "Я работаю по командам. Чтобы разместить объявление, нажмите кнопку ниже.",
            "Фотографии и файлы отправляются через приложение. Нажмите кнопку ниже 🙂",
            "Такой команды нет. Список команд: /yordam",
            "Ваш аккаунт заблокирован. Ознакомьтесь с /qoidalar.",
            "Приложение сейчас настраивается. Попробуйте немного позже.",
            "Произошла ошибка. Попробуйте немного позже."
    );

    private static final Map<String, Pack> PACKS = Map.of(
            "uz", UZ,
            "uz-cyrl", UZ_CYRL,
            "ru", RU
    );

    public static Pack of(String uiLanguage) {
        return PACKS.getOrDefault(uiLanguage == null ? "uz" : uiLanguage, UZ);
    }

    public static Map<String, Pack> all() {
        return PACKS;
    }
}
