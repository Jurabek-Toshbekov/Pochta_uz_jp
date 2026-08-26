/**
 * uz-latn — asosiy lug'at. Boshqa tillar shu tuzilmani takrorlaydi.
 *
 * Matn qoidalari (§9.4): tugma nima qilishini aytadi; xato kechirim so'ramaydi,
 * nima qilish kerakligini aytadi; bo'sh ekran — taklif.
 */
export const uz = {
  common: {
    next: 'Davom etish',
    back: 'Orqaga',
    cancel: 'Bekor qilish',
    retry: 'Qayta urinish',
    loading: 'Yuklanmoqda…',
    close: 'Yopish',
    optional: 'ixtiyoriy',
    errorGeneric: 'Xatolik yuz berdi. Qayta urinib ko‘ring.',
    errorNetwork: 'Internet aloqasi yo‘q. Qayta urinib ko‘ring.',
    errorSession: 'Sessiya yaroqsiz. Ilovani yopib qaytadan oching.',
    kg: 'kg',
  },

  home: {
    greeting: 'Assalomu alaykum',
    subtitle: 'Yaponiya ↔ O‘zbekiston yo‘nalishida pochta e‘lonlari',
    newPost: 'E‘lon berish',
    search: 'Qidirish',
    myPosts: 'Mening e‘lonlarim',
    searchSoon: 'Qidiruv tez orada ishga tushadi.',
  },

  consent: {
    title: 'Boshlashdan oldin',
    text: 'Ilovadan foydalanish shartlari va maxfiylik siyosatiga rozilik bildirasiz. Ma‘lumotlaringiz faqat e‘lon ko‘rsatish va xizmatni yaxshilash uchun ishlatiladi.',
    tos: 'Foydalanish shartlariga roziman',
    privacy: 'Maxfiylik siyosatiga roziman',
    accept: 'Roziman va davom etaman',
  },

  step1: {
    title: 'Nima qilmoqchisiz?',
    send: 'Pochta yubormoqchiman',
    sendHint: 'Yukingiz bor, olib ketadigan odam kerak',
    carry: 'Pochta olib ketaman',
    carryHint: 'Uchayotgan reysingizda joy bor',
    direction: 'Yo‘nalish',
    jpToUz: 'Yaponiyadan O‘zbekistonga',
    uzToJp: 'O‘zbekistondan Yaponiyaga',
  },

  step2: {
    title: 'Yo‘nalish va sana',
    origin: 'Qaysi aeroportdan',
    dest: 'Qaysi aeroportga',
    searchAirport: 'Shahar yoki kod bo‘yicha izlash',
    popular: 'Mashhur',
    other: 'Boshqa',
    otherCityPlaceholder: 'Shahar nomini yozing',
    finalDestination: 'Yakuniy manzil',
    finalDestinationHint: 'Masalan: Samarqand',
    dateCarry: 'Uchish sanasi',
    dateSend: 'Qachongacha kerak',
    flexible: 'Sanaga moslasha olaman',
    flexibleDays: 'kun',
  },

  step3: {
    title: 'Yuk va narx',
    categories: 'Yuk turi',
    categoriesHint: 'Bir nechtasini tanlash mumkin',
    weight: 'Og‘irlik',
    weightFrom: 'dan',
    weightTo: 'gacha',
    price: 'Narx',
    currency: 'Valyuta',
    unit: 'Hisob',
    unitPerKg: 'kg uchun',
    unitTotal: 'jami',
    unitNegotiable: 'Kelishamiz',
    comment: 'Izoh',
    commentPlaceholder: 'Qo‘shimcha ma‘lumot',
    riskWarning: 'Diqqat',
  },

  step4: {
    title: 'Aloqa',
    telegram: 'Telegram',
    phone: 'Telefon',
    phonePlaceholder: '+998 90 123 45 67',
    requestPhone: 'Telegram’dan olish',
    other: 'Boshqa aloqa',
    otherPlaceholder: 'WhatsApp, email…',
    privacyNote:
      'Kontaktingiz kanalda ko‘rinmaydi. Faqat «Bog‘lanish» bosilganda ochiladi.',
  },

  preview: {
    title: 'Ko‘rib chiqing',
    hint: 'E‘lon kanalda aynan shunday ko‘rinadi.',
    checklistTitle: 'Xavfsizlik',
    check1: 'Men yopiq/o‘ralgan qutini olmayman — yuk egasi oldida ochib ko‘raman.',
    check2: 'Yukda taqiqlangan buyum yo‘qligiga ishonch hosil qildim.',
    check3: 'Bu e‘lon uchun to‘liq javobgarlikni o‘zim olaman.',
    prohibitedTitle: 'Taqiqlangan buyumlar ro‘yxati',
    prohibitedList: [
      'giyohvand va psixotrop moddalar',
      'o‘q-dori va qurol',
      'go‘sht va sut mahsulotlari (Yaponiyaga kirish taqiqlangan)',
      'retseptli dorilar va psevdoefedrin tarkibli preparatlar',
      'ko‘p miqdordagi naqd pul',
      'o‘simlik va urug‘lar',
      'soxta brend mahsulotlar',
    ],
    submit: 'Kanalga yuborish',
    submitting: 'Yuborilmoqda…',
  },

  success: {
    title: 'E‘loningiz chiqdi',
    text: 'Kanalda e‘loningiz paydo bo‘ldi. Sizga mos odam topilsa, bog‘lanadi.',
    pendingTitle: 'E‘lon qabul qilindi',
    pendingText:
      'Hozir kanalga yuborib bo‘lmadi. E‘lon saqlandi va tez orada chiqadi — qaytadan to‘ldirish kerak emas.',
    openChannel: 'Kanaldagi e‘lonni ko‘rish',
    share: 'Ulashish',
    newPost: 'Yana e‘lon berish',
    toHome: 'Bosh sahifa',
  },

  my: {
    title: 'Mening e‘lonlarim',
    empty: 'Hali e‘lon bermagansiz. Birinchisini yarataylik.',
    createFirst: 'E‘lon berish',
    active: 'Faol',
    closed: 'Yopilgan',
    views: 'ko‘rildi',
    reveals: 'bog‘lanish',
  },

  status: {
    DRAFT: 'Qoralama',
    PENDING: 'Navbatda',
    PUBLISHED: 'Kanalda',
    REJECTED: 'Rad etilgan',
    EXPIRED: 'Muddati tugagan',
    CLOSED: 'Yopilgan',
    DELETED: 'O‘chirilgan',
  },

  postType: {
    SEND: 'Pochta yuboraman',
    CARRY: 'Pochta olib ketaman',
  },

  language: {
    title: 'Til',
    uz: 'O‘zbekcha',
    'uz-cyrl': 'Ўзбекча',
    ru: 'Русский',
  },
};

export type Dictionary = typeof uz;
