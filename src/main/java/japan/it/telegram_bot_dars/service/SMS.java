package japan.it.telegram_bot_dars.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.File;

public class SMS {
    public SendMessage message (Long chatId, String text){
        return new SendMessage(chatId.toString(), text);
    }

    public SendPhoto photo (Long chatId , String caption , String filePath){
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setCaption(caption);

        File file = new File(filePath);
        InputFile inputFile = new InputFile();
        inputFile.setMedia(file);
        sendPhoto.setPhoto(inputFile);
        return sendPhoto;
    }

    public String sksiyaUchunText = "Servis xizmatlari aksiyasi\n" +
            "\n" +
            "Mustaqillik kuni munosabati bilan Hyundai Uzbekistan servis xizmatlari aksiyasini sovg'aga taqdim qiladi, jumladan:\n" +
            "\n" +
            "• kompyuter diagnostikasi\n" +
            "• yurish qismi diagnostikasi\n" +
            "• moy almashtirish xizmati.\n" +
            "\n" +
            "Siz faqat moy va sarf materiallari uchun to‘lov kiritasiz.\n" +
            "Shuningdek, biz 1 500 000 so‘mdan boshlangan buyurtma berilganida brendli suvenirlarni \uD83C\uDF81ham egasi bo'lasiz.\n" +
            "\n" +
            "Aksiya 2024 yilning\n" +
            "31 avgustidan 3 sentabrga qadar Toshkentdagi barcha Hyundai servis markazlarida amal qiladi.\n" +
            "\n" +
            "Ko‘proq ma'lumot\n" +
            "va ro‘yxatdan o‘tish uchun: \uD83D\uDCDE1248";
}
