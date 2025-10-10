package japan.it.telegram_bot_dars.service;


import japan.it.telegram_bot_dars.entity.PostEntity;
import japan.it.telegram_bot_dars.repository.PostRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Component
public class TelegramBotMain extends TelegramLongPollingBot {
    private final PostRepository postRepository;
    KeyboardLocal keyboardLocal = new KeyboardLocal();

    //    GoogleAnalyticsUploader googleAnalyticsUploader = new GoogleAnalyticsUploader();
    public TelegramBotMain(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    public void onUpdateReceived(Update update) {


        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String text = message.getText();

            PostEntity entity = new PostEntity();
            Optional<PostEntity> optional = postRepository.findTopByChatIdOrderByCreatedAtDesc(chatId.toString());
            if (optional.isPresent()) {
                entity = optional.get();
            }

            if (message.hasText()) {
                // boshlanishi
                if (text.equals("/start") || text.equals("Boshlash")) {
                    sendMessage(chatId.toString(), MessageText.START);
                    sendMessage(chatId.toString(), MessageText.START_02);
                    sendMessage(chatId.toString(), MessageText.START_03);
                    sendKeyboard(keyboardLocal.route(chatId));

                    entity.setStepNumber(1);
                    entity.setChatId(chatId.toString());
                    saveEntity(entity);

//                    sendGroup(entity,chatId.toString());
                    return;
                }

                // 2 step // qaysi yonalishda
                if (entity.getStepNumber() == 1) {
                    sendKeyboard(keyboardLocal.routeCountry(chatId));

                    entity.setPostType(text);
                    entity.setStepNumber(2);
                    saveEntity(entity);
                    return;
                }

                //  3 step // airportlar
                if (entity.getStepNumber() == 2) {
                    if (text.equals("Yaponiyadan \uD83C\uDDEF\uD83C\uDDF5  ➡\uFE0F  O`zbekistonga  \uD83C\uDDFA\uD83C\uDDFF")) {
                        sendKeyboard(keyboardLocal.fromJpAirport(chatId));
                    }
                    if (text.equals("O`zbekistondan \uD83C\uDDFA\uD83C\uDDFF ➡\uFE0F  Yaponiyaga \uD83C\uDDEF\uD83C\uDDF5")) {
                        sendKeyboard(keyboardLocal.fromUzbAirport(chatId));
                    }

                    entity.setStepNumber(3);
                    entity.setRoute(text);
                    saveEntity(entity);
                    return;
                }

                //  4 step // vaqt
                if (entity.getStepNumber() == 3) {
                    if (entity.getPostType().equals("Pochta bervoraman")) {
                        sendMessage(chatId.toString(), MessageText.TIME_FROM_BERVORAMAN);
                    }
                    if (entity.getPostType().equals("Pochta olib ketaman")) {
                        sendMessage(chatId.toString(), MessageText.TIME_FROM_OLIB_KETAMAN);
                    }

                    entity.setStepNumber(4);
                    entity.setAirport(text);
                    saveEntity(entity);
                    return;
                }

                //  5 step // vaqt
                if (entity.getStepNumber() == 4) {
                    String userName = message.getFrom().getUserName();
                    if (userName == null) {
                        sendMessage(chatId.toString(), MessageText.CONTACT_NULL);
                    }
                    if (userName != null) {
                        sendMessage(chatId.toString(), "@" + userName + "  Telegramdagi username ingizni oldik rozi bo'lasiz \uD83D\uDE0A \b  \n\n" +
                                "Bundan tashqari Siz bilan bog'lanish uchun telefon raqam ham bering ");
                        entity.setTelegramUserName(userName);
                        String name = nameModification(message.getFrom().getFirstName(), message.getFrom().getLastName());
                        entity.setTelegramName(name);
                    }

                    entity.setDate(text);
                    entity.setStepNumber(5);
                    saveEntity(entity);
                    return;
                }

                //  6 step // baggage
                if (entity.getStepNumber() == 5) {
                    if (entity.getPostType().equals("Pochta bervoraman")) {
                        sendMessage(chatId.toString(), MessageText.NIMA_BERMOQCHI);
                    }
                    if (entity.getPostType().equals("Pochta olib ketaman")) {
                        sendMessage(chatId.toString(), MessageText.NIMA_OLIB_KETADI);
                    }
                    sendKeyboard(keyboardLocal.baggage(chatId));

                    entity.setStepNumber(6);
                    entity.setContact(text);
                    saveEntity(entity);
                    return;
                }

                //  7 step // comment
                if (entity.getStepNumber() == 6) {
                    sendMessage(chatId.toString(), MessageText.COMMENT);

                    entity.setStepNumber(7);
                    entity.setBaggage(text);
                    saveEntity(entity);
                    return;
                }

                //  8 step // price
                if (entity.getStepNumber() == 7) {
                    String named = nameModification(message.getFrom().getFirstName(), message.getFrom().getLastName());
                    if (entity.getPostType().equals("Pochta bervoraman")) {
                        String msg = "Pochtangiz uchun qancha pul tolamoqchisiz ? \n yozing misol uchun \nKilosi 2000 yen yoki 20 usd ";
                        sendMessage(chatId.toString(), msg);
                    }
                    if (entity.getPostType().equals("Pochta olib ketaman")) {
                        String msg = "Ha, aytgancha, " + named + ", pochtani qanchadan olib ketayapsiz? Yozishingiz kerak bo'ladi. \nMisol: 2000 yen yoki 20usd";
                        sendMessage(chatId.toString(), msg);
                    }
                    entity.setStepNumber(8);
                    entity.setComment(text);
                    saveEntity(entity);
                    return;
                }

                //  9 step // review post
                if (entity.getStepNumber() == 8) {
                    sendMessage(chatId.toString(), MessageText.LAST);

                    entity.setStepNumber(9);
                    entity.setPrice(text);
                    saveEntity(entity);
                    preViewPost(entity, chatId.toString());
                    sendKeyboard(keyboardLocal.sendGroup(chatId));
                    return;
                }

                //  10 step // yuborish
                if (entity.getStepNumber() == 9) {
                    if (text.equals("Yuborish ✅")) {
                        boolean sendGroup = sendGroup(entity, "-1001424117981");
                        if (sendGroup) {
                            sendMessage(chatId.toString(), MessageText.LAST_001);
                        }
                        if (!sendGroup) {
                            sendMessage(chatId.toString(), MessageText.LAST_002);
                        }
                    }
                    if (text.equals("Bekor qilish ❌")) {
                        sendMessage(chatId.toString(), MessageText.DELETE);
                    }
//                    googleAnalyticsUploader.sendToGoogleAnalytics(entity);
                    postRepository.delete(entity);
                    return;
                }

            }
            if (message.hasPhoto() || message.hasAudio() || message.hasContact() || message.hasViaBot() || message.hasDocument()) {
                sendMessage(chatId.toString(), nameModification(message.getFrom().getFirstName(), message.getFrom().getLastName()) + "_ " + MessageText.TEXT_ONLY_WARNING);
                sendMessage(chatId.toString(), MessageText.TEXT_ONLY_WARNING_ICON);

            }
        }
    }


    @Override
    public String getBotUsername() {
        return "@uzb_jp_elon_bot";
    }

    @Override
    public String getBotToken() {
        return "7090852538:AAEgbZNtNf5WKuFOdTzVtqfiRoTMC0Ub5RQ";
    }

    // Guruhga xabar yuborish uchun metod
    public void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendKeyboard(SendMessage keyboardMessage) {
        try {
            execute(keyboardMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private String nameModification(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null && lastName == null) {
            return firstName;
        }
        if (firstName == null && lastName != null) {
            return lastName;
        } else return "";
    }

    private boolean saveEntity(PostEntity post) {
        try {
            postRepository.save(post);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }


    private void preViewPost(PostEntity entity, String chatId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            // message.setParseMode("HTML");
            message.setText("E'lon turi: " + entity.getPostType() + "\n" +
                    "Yo'nalish: " + entity.getRoute() + "\n" +
                    "Airport: " + entity.getAirport() + "\n" +
                    "Vaqt: " + entity.getDate() + "\n" +
                    "Kontakt: " + entity.getContact() + "\n" +
                    "Bagaj: " + entity.getBaggage() + "\n" +
                    "Narx: " + entity.getPrice() + "\n" +
                    "Comment: " + entity.getComment() + "\n" +
                    "Post yaratishni boshlagan vaqtiz : " + entity.getCreatedAt());
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean sendGroup(PostEntity entity, String chatId) {
        try {
            String postHashTag = "";

            String postType = entity.getPostType();
            String route = entity.getRoute();

// Post turi uchun hashtaglar
            if (postType.equals("Pochta bervoraman")) {
                postHashTag += "#pochta_yuborish ";
            }
            if (postType.equals("Pochta olib ketaman")) {
                postHashTag += "#pochta_olib_ketish ";
            }

// Yo'nalish uchun hashtaglar
            if (route.equals("Yaponiyadan \uD83C\uDDEF\uD83C\uDDF5  ➡\uFE0F  O`zbekistonga  \uD83C\uDDFA\uD83C\uDDFF")) {
                postHashTag += "#yaponiyadan_uzbekistonga ";
            }
            if (route.equals("O`zbekistondan \uD83C\uDDFA\uD83C\uDDFF ➡\uFE0F  Yaponiyaga \uD83C\uDDEF\uD83C\uDDF5")) {
                postHashTag += "#uzbekistondan_yaponiyaga ";
            }

// Bagaj turi uchun hashtaglar
            if (entity.getBaggage() != null && entity.getBaggage().equals("Hujjatlar")) {
                postHashTag += "#hujjat ";
            }
            if (entity.getBaggage() != null && entity.getBaggage().equals("23 kg tayyor yuk")) {
                postHashTag += "#tayyor_yuk ";
            }

// Dastlabki ro'yxatdagi qo'shimcha tovarlar
            if (entity.getBaggage() != null && entity.getBaggage().equals("BADlar")) {
                postHashTag += "#badlar ";
            }
            if (entity.getBaggage() != null && entity.getBaggage().equals("Vitaminlar")) {
                postHashTag += "#vitaminlar ";
            }
            if (entity.getBaggage() != null && entity.getBaggage().equals("Kiyim-kechak")) {
                postHashTag += "#kiyimkechak ";
            }
            if (entity.getBaggage() != null && entity.getBaggage().equals("Maishiy texnika, telefon, noutbuk")) {
                postHashTag += "#maishiy_texnika_telefon_noutbuk ";
            }


            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setParseMode("HTML");

            message.setText(
                    "<b>Yangi e'lon:</b>\n\n" +
                            "<b>E'lon turi:</b> " + entity.getPostType() + "\n" +
                            "<b>Yo'nalish:</b> " + entity.getRoute() + "\n" +
                            "<b>Airport:</b> " + entity.getAirport() + "\n" +
                            "<b>Vaqt:</b> " + entity.getDate() + "\n" +
                            "<b>Kontakt:</b> " + entity.getContact() + "\n" +
                            "<b>Telegram userName:</b> @" + entity.getTelegramUserName() + "\n" +
                            "<b>Telegram name:</b> " + entity.getTelegramName() + "\n" +
                            "<b>Bagaj:</b> " + entity.getBaggage() + "\n" +
                            "<b>Narx:</b> " + entity.getPrice() + "\n" +
                            "<b>Comment:</b> " + entity.getComment() + "\n\n" +
                            postHashTag + "\n\n" +
                            "<b>Guruxga qo'shilish:</b> 1️⃣ @jpuzbpochta \n" +
                            "<b>E'lon berish:</b> 2️⃣ @uzb_jp_elon_bot \n"
            );


            execute(message);

            return true;
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void sendMessage(Long chatId, String text, int topicId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(text);
        sendMessage.setMessageThreadId(topicId);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
