package uz.pochtajp.domain.enums;

/**
 * Admin JWT turi (§11.1). Access qisqa umr ko'radi, refresh esa faqat
 * yangi juftlik olish uchun ishlaydi va API'ga kirita olmaydi.
 */
public enum AdminTokenType {
    ACCESS,
    REFRESH
}
