package japan.it.telegram_bot_dars.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class KeyboardLocal {

    public  SendMessage route(Long chatId) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> rowList = new ArrayList<>();

        KeyboardRow row01 = new KeyboardRow();
        row01.add(new KeyboardButton("Pochta bervoraman"));

        KeyboardRow row02 = new KeyboardRow();
        row02.add(new KeyboardButton("Pochta olib ketaman"));

        rowList.add(row01);
        rowList.add(row02);

        replyKeyboardMarkup.setKeyboard(rowList);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        replyKeyboardMarkup.setResizeKeyboard(true);

        SendMessage message1 = new SendMessage();
        message1.setChatId(chatId);
        message1.setText("Pochta bervorasizmi yoki olib ketasizmi ?");
        message1.setReplyMarkup(replyKeyboardMarkup);
        return message1;
    }

    public  SendMessage routeCountry(Long chatId) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> rowList = new ArrayList<>();

        KeyboardRow row01 = new KeyboardRow();
        row01.add(new KeyboardButton("Yaponiyadan \uD83C\uDDEF\uD83C\uDDF5  ➡\uFE0F  O`zbekistonga  \uD83C\uDDFA\uD83C\uDDFF"));

        KeyboardRow row02 = new KeyboardRow();
        row02.add(new KeyboardButton("O`zbekistondan \uD83C\uDDFA\uD83C\uDDFF ➡\uFE0F  Yaponiyaga \uD83C\uDDEF\uD83C\uDDF5"));

        rowList.add(row01);
        rowList.add(row02);

        replyKeyboardMarkup.setKeyboard(rowList);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        replyKeyboardMarkup.setResizeKeyboard(false);

        SendMessage message1 = new SendMessage();
        message1.setChatId(chatId);
        message1.setText("Qaysi yo'nalishda ?");
        message1.setReplyMarkup(replyKeyboardMarkup);
        return message1;

    }

    public  SendMessage fromUzbAirport(Long chatId) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> rowList = new ArrayList<>();

        KeyboardRow row01 = new KeyboardRow();
        row01.add(new KeyboardButton("Toshkent(TAS) ➡\uFE0F Narita(NRT)"));
        KeyboardRow row02 = new KeyboardRow();
        row02.add(new KeyboardButton("Toshkent(TAS) ➡\uFE0F Haneda(HND)"));
        KeyboardRow row03 = new KeyboardRow();
        row03.add(new KeyboardButton("Toshkent(TAS) ➡\uFE0F Kansai(KIX)"));
        KeyboardRow row04 = new KeyboardRow();
        row04.add(new KeyboardButton("Toshkent(TAS) ➡\uFE0F Chubu(NGO)"));
        KeyboardRow row05 = new KeyboardRow();
        row05.add(new KeyboardButton("Toshkent(TAS) ➡\uFE0F Fukuoka (FUK)"));
        KeyboardRow row06 = new KeyboardRow();
        row06.add(new KeyboardButton("Toshkent(TAS) ➡\uFE0F Boshqa ..."));

        rowList.add(row01);
        rowList.add(row02);
        rowList.add(row03);
        rowList.add(row04);
        rowList.add(row05);
        rowList.add(row06);

        replyKeyboardMarkup.setKeyboard(rowList);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        replyKeyboardMarkup.setResizeKeyboard(true);

        SendMessage message1 = new SendMessage();
        message1.setChatId(chatId);
        message1.setText("Airportni tanlang ... \n\n Agar boshqa yo'nalish bo'lsa qo'lda yozsangiz ham bo'ladi . \n Misol: Toshkent ➡\uFE0F Narita ➡\uFE0F Yokohama");
        message1.setReplyMarkup(replyKeyboardMarkup);
        return message1;

    }

    public  SendMessage fromJpAirport(Long chatId) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> rowList = new ArrayList<>();

        KeyboardRow row01 = new KeyboardRow();
        row01.add(new KeyboardButton("Narita(NRT) ➡️ Toshkent(TAS)"));
        KeyboardRow row02 = new KeyboardRow();
        row02.add(new KeyboardButton("Haneda(HND) ➡️ Toshkent(TAS)"));
        KeyboardRow row03 = new KeyboardRow();
        row03.add(new KeyboardButton("Kansai(KIX) ➡️ Toshkent(TAS)"));
        KeyboardRow row04 = new KeyboardRow();
        row04.add(new KeyboardButton("Chubu(NGO) ➡️ Toshkent(TAS)"));
        KeyboardRow row05 = new KeyboardRow();
        row05.add(new KeyboardButton("Fukuoka(FUK) ➡️ Toshkent(TAS)"));
        KeyboardRow row06 = new KeyboardRow();
        row06.add(new KeyboardButton("Boshqa ... ➡️ Toshkent(TAS)"));

        rowList.add(row01);
        rowList.add(row02);
        rowList.add(row03);
        rowList.add(row04);
        rowList.add(row05);
        rowList.add(row06);

        replyKeyboardMarkup.setKeyboard(rowList);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        replyKeyboardMarkup.setResizeKeyboard(true);

        SendMessage message1 = new SendMessage();
        message1.setChatId(chatId);
        message1.setText("Airportni tanlang ... \n\n Agar boshqa yo'nalish bo'lsa qo'lda yozsangiz ham bo'ladi . \n Misol: Tokyo ➡\uFE0F Toshkent ➡\uFE0F Samarqand");
        message1.setReplyMarkup(replyKeyboardMarkup);
        return message1;
    }


    public  SendMessage baggage(Long chatId) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> rowList = new ArrayList<>();

        KeyboardRow row01 = new KeyboardRow();
        row01.add(new KeyboardButton("Hujjatlar"));
        row01.add(new KeyboardButton("23 kg tayyor yuk"));

        KeyboardRow row02 = new KeyboardRow();
        row02.add(new KeyboardButton("BADlar"));
        row02.add(new KeyboardButton("Vitaminlar"));

        KeyboardRow row03 = new KeyboardRow();
        row03.add(new KeyboardButton("Kiyim-kechak"));

        KeyboardRow row04 = new KeyboardRow();
        row04.add(new KeyboardButton("Maishiy texnika, telefon, noutbuk"));

        KeyboardRow row05 = new KeyboardRow();
        row05.add(new KeyboardButton("Puliga qarab hamma narsa \uD83D\uDE05"));


        rowList.add(row01);
        rowList.add(row02);
        rowList.add(row03);
        rowList.add(row04);
        rowList.add(row05);

        replyKeyboardMarkup.setKeyboard(rowList);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        replyKeyboardMarkup.setResizeKeyboard(true);

        SendMessage message1 = new SendMessage();
        message1.setChatId(chatId);
        message1.setText("Tanlasangiz ham bo'ladi ");
        message1.setReplyMarkup(replyKeyboardMarkup);
        return message1;
    }


    public  SendMessage sendGroup(Long chatId) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> rowList = new ArrayList<>();

        KeyboardRow row01 = new KeyboardRow();
        row01.add(new KeyboardButton("Yuborish ✅"));

        KeyboardRow row02 = new KeyboardRow();
        row02.add(new KeyboardButton("Bekor qilish ❌"));

        rowList.add(row01);
        rowList.add(row02);

        replyKeyboardMarkup.setKeyboard(rowList);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        replyKeyboardMarkup.setResizeKeyboard(true);

        SendMessage message1 = new SendMessage();
        message1.setChatId(chatId);
        message1.setText("Agar ma'lumot to'g'ri bo'lsa, << Yuborish ✅ >> tugmasini bosing va e'loniz guruxga yuboriladi .");
        message1.setReplyMarkup(replyKeyboardMarkup);
        return message1;
    }


}
